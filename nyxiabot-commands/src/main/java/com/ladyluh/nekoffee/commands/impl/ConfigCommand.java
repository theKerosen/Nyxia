package com.ladyluh.nekoffee.commands.impl;

import com.ladyluh.nekoffee.api.payload.permission.Permission;
import com.ladyluh.nekoffee.builder.EmbedBuilder;
import com.ladyluh.nekoffee.builder.MessageBuilder;
import com.ladyluh.nekoffee.commands.Command;
import com.ladyluh.nekoffee.commands.CommandContext;
import com.ladyluh.nekoffee.database.DatabaseManager;
import com.ladyluh.nekoffee.database.GuildConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigCommand implements Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigCommand.class);
    private final DatabaseManager dbManager;

    public ConfigCommand(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public String getName() {
        return "config";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("cfg", "settings");
    }

    @Override
    public String getDescription() {
        return "Define configurações para este servidor.";
    }

    @Override
    public String getUsage() {
        return "config <set/show> <key> [value]";
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }

    public CompletableFuture<Void> execute(CommandContext ctx) {
        String guildId = ctx.getGuildId();
        if (guildId == null) {
            return ctx.reply("Este comando só pode ser usado em um servidor.");
        }

        return ctx.getClient().getGuildMember(guildId, ctx.getAuthor().getId())
                .thenCompose(member -> {
                    if (member == null) {
                        return ctx.reply("Não consegui verificar suas permissões neste servidor.");
                    }
                    return member.hasPermission(Permission.ADMINISTRATOR)
                            .thenCompose(hasAdminPerm -> { 
                                if (!hasAdminPerm) {
                                    return ctx.reply("Você não tem permissão para usar este comando. (Requer permissão de ADMINISTRADOR)");
                                }

                                if (ctx.getArgs().isEmpty()) {
                                    return showHelp(ctx);
                                }
                                String subCommand = ctx.getArgs().getFirst().toLowerCase();
                                List<String> cmdArgs = ctx.getArgs().subList(1, ctx.getArgs().size());

                                return switch (subCommand) {
                                    case "show" -> showConfig(ctx, cmdArgs);
                                    case "set" -> setConfig(ctx, cmdArgs);
                                    default -> showHelp(ctx);
                                };
                            });
                });
    }

    private CompletableFuture<Void> showHelp(CommandContext ctx) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("⚙️ Ajuda do Comando Config")
                .setDescription("Define configurações específicas para este servidor.")
                .setColor(Color.LIGHT_GRAY)
                .addField("Uso:", "`!config <set/show> <key> [value]`", false)
                .addField("Chaves Disponíveis:",
                        """
                                `log_channel`: Canal para logs de mensagens/membros.
                                `welcome_channel`: Canal para mensagens de boas-vindas.
                                `auto_assign_role`: Cargo para novos membros.
                                `temp_hub_channel`: Canal de voz para criar salas temporárias.
                                `temp_channel_category`: Categoria para salas temporárias.
                                `temp_channel_name_prefix`: Prefixo do nome de salas temporárias.
                                `default_temp_channel_user_limit`: Limite padrão de usuários (0 para ilimitado).
                                `default_temp_channel_lock`: Trancar salas temporárias por padrão (true/false).
                                `join_sound_id`: ID do som da soundboard para tocar ao entrar em call.
                                """, false)
                .setTimestamp(OffsetDateTime.now());
        return ctx.getClient().sendMessage(ctx.getChannelId(), new MessageBuilder().addEmbed(embed).build()).thenAccept(v -> {
        });
    }

    private CompletableFuture<Void> showConfig(CommandContext ctx, List<String> args) {
        String guildId = ctx.getGuildId();
        return dbManager.getGuildConfig(guildId)
                .thenCompose(configOpt -> { 
                    GuildConfig guildConfig = configOpt.orElse(new GuildConfig(guildId));

                    StringBuilder configText = new StringBuilder();
                    configText.append("```properties\n");

                    for (Field field : GuildConfig.class.getDeclaredFields()) {
                        if (field.isSynthetic() || field.getName().startsWith("this$")) continue;

                        try {
                            field.setAccessible(true);
                            Object value = field.get(guildConfig);
                            String displayKey = field.getName().replaceAll("([A-Z])", "_$1").toLowerCase();
                            configText.append(displayKey).append("=").append(configFormat(value)).append("\n");
                        } catch (IllegalAccessException e) {
                            LOGGER.error("Erro ao acessar campo por reflection em showConfig: {}", field.getName(), e);
                        }
                    }
                    configText.append("```");

                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle("⚙️ Configurações do Servidor: " + ctx.getGuildId())
                            .setDescription(configText.toString())
                            .setColor(Color.LIGHT_GRAY)
                            .setFooter("Configure com !config set <key> <value>", ctx.getClient().getSelfUser().getEffectiveAvatarUrl())
                            .setTimestamp(OffsetDateTime.now());

                    return ctx.getClient().sendMessage(ctx.getChannelId(), new MessageBuilder().addEmbed(embed).build()).thenApply(v -> null); 
                });
    }

    private String configFormat(Object value) {
        return value == null ? "Não definido" : String.valueOf(value);
    }

    private CompletableFuture<Void> setConfig(CommandContext ctx, List<String> args) {
        if (args.size() < 2) {
            return ctx.reply("Uso: `!config set <key> <value>`");
        }

        String key = args.getFirst().toLowerCase();
        String rawValue = String.join(" ", args.subList(1, args.size())).trim();
        String guildId = ctx.getGuildId();

        return dbManager.getGuildConfig(guildId)
                .thenCompose(configOpt -> { 
                    GuildConfig guildConfig = configOpt.orElse(new GuildConfig(guildId));
                    Field targetField;

                    
                    Pattern pattern = Pattern.compile("_([a-z])");
                    Matcher matcher = pattern.matcher(key);
                    StringBuilder sb = new StringBuilder();
                    while (matcher.find()) {
                        matcher.appendReplacement(sb, matcher.group(1).toUpperCase());
                    }
                    matcher.appendTail(sb);
                    String fieldName = sb.toString();

                    LOGGER.info("fieldName gerado: {}", fieldName);
                    LOGGER.info("key original: {}", key);

                    String finalReplyMessage; 

                    try {
                        targetField = GuildConfig.class.getDeclaredField(fieldName);
                        targetField.setAccessible(true);

                        Object valueToSet = getObject(targetField, rawValue, key);


                        targetField.set(guildConfig, valueToSet); 

                        finalReplyMessage = "Configuração `" + key + "` definida para: `" + configFormat(valueToSet) + "`";

                    } catch (NoSuchFieldException e) {
                        LOGGER.warn("Chave de configuração '{}' não encontrada como campo no GuildConfig.", key, e);
                        return ctx.reply("Chave de configuração desconhecida: '" + key + "'.");
                    } catch (NumberFormatException e) {
                        return ctx.reply("Valor inválido para '" + key + "'. Esperado um número.");
                    } catch (IllegalArgumentException e) {
                        return ctx.reply("Valor inválido para '" + key + "'. " + e.getMessage());
                    } catch (IllegalAccessException e) {
                        LOGGER.error("Erro de acesso ao campo por reflection: {}", key, e);
                        return ctx.reply("Erro interno ao tentar configurar. Verifique os logs.");
                    }

                    return dbManager.updateGuildConfig(guildConfig)
                            .thenCompose(v -> ctx.reply(finalReplyMessage)) 
                            .exceptionally(ex -> {
                                LOGGER.error("Erro ao salvar configuração {}:{} para guild {}:", key, rawValue, guildId, ex);
                                ctx.reply("Erro ao salvar a configuração. Verifique os logs.");

                                return null;
                            });
                });
    }

    private static @Nullable Object getObject(Field targetField, String rawValue, String key) {
        Object valueToSet;
        Class<?> fieldType = targetField.getType();


        String processedValue = rawValue;
        if (rawValue.matches("<#[0-9]+>")) {
            processedValue = rawValue.substring(2, rawValue.length() - 1);
        } else if (rawValue.matches("<@&[0-9]+>")) {
            processedValue = rawValue.substring(3, rawValue.length() - 1);
        } else if (rawValue.matches("<@!?[0-9]+>")) {
            processedValue = rawValue.replaceAll("[<@!>]", "").replaceAll(">", "");
        }


        if (processedValue.isEmpty() || processedValue.equalsIgnoreCase("null")) {
            valueToSet = null;
        } else if (fieldType == String.class) {
            valueToSet = processedValue;
        } else if (fieldType == Integer.class || fieldType == int.class) {
            int intValue = Integer.parseInt(processedValue);
            if (key.equals("default_temp_channel_user_limit") && (intValue < 0 || intValue > 99)) {
                throw new IllegalArgumentException("Limite de usuários deve ser entre 0 e 99.");
            }
            valueToSet = intValue;
        } else if (fieldType == Boolean.class || fieldType == boolean.class) {

            if (!processedValue.equalsIgnoreCase("true") && !processedValue.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("Valor deve ser 'true' ou 'false'.");
            }
            valueToSet = Boolean.parseBoolean(processedValue);
        } else {
            throw new IllegalArgumentException("Tipo de valor para '" + key + "' não suportado.");
        }
        return valueToSet;
    }
}