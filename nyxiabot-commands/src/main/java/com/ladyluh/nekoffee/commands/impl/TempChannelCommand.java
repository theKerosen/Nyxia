package com.ladyluh.nekoffee.commands.impl;

import com.ladyluh.nekoffee.api.entities.TargetType;
import com.ladyluh.nekoffee.api.payload.channel.ChannelModifyPayload;
import com.ladyluh.nekoffee.api.payload.permission.Permission;
import com.ladyluh.nekoffee.commands.Command;
import com.ladyluh.nekoffee.commands.CommandContext;
import com.ladyluh.nekoffee.database.DatabaseManager;
import com.ladyluh.nekoffee.database.TemporaryChannelRecord;
import com.ladyluh.nekoffee.database.UserChannelPreference;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class TempChannelCommand implements Command {
    private final DatabaseManager dbManager;

    public TempChannelCommand(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public String getName() {
        return "sala";
    }

    @Override
    public List<String> getAliases() {
        return List.of("minhasala");
    }

    @Override
    public String getDescription() {
        return "Gerencia seu canal de voz temporário.";
    }

    @Override
    public String getUsage() {
        return "sala <limite/nome/trancar/destrancar/permitir/proibir> [args]";
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (ctx.getArgs().isEmpty()) {
            return ctx.reply("Uso: `" + getUsage() + "`.");
        }

        String guildId = ctx.getGuildId();
        String authorId = ctx.getAuthor().getId();
        String subCommand = ctx.getArgs().getFirst().toLowerCase();


        CompletableFuture<Optional<TemporaryChannelRecord>> tempChannelFuture = dbManager.getTemporaryChannelByOwner(guildId, authorId);
        CompletableFuture<Optional<UserChannelPreference>> prefsFuture = dbManager.getUserChannelPreference(guildId, authorId);

        return CompletableFuture.allOf(tempChannelFuture, prefsFuture)
                .thenCompose(v -> {
                    Optional<TemporaryChannelRecord> userTempChannelOpt = tempChannelFuture.join();
                    Optional<UserChannelPreference> userPrefsOpt = prefsFuture.join();


                    if ("limite".equals(subCommand) || "nome".equals(subCommand)) {
                        return handlePreferenceCommand(ctx, userTempChannelOpt, userPrefsOpt, subCommand);
                    }


                    if (userTempChannelOpt.isEmpty()) {
                        return ctx.reply("Você não possui um canal de voz temporário ativo para usar este comando.");
                    }

                    String tempChannelId = userTempChannelOpt.get().channelId;

                    return switch (subCommand) {
                        case "trancar", "destrancar" ->
                                handleLockUnlockCommand(ctx, tempChannelId, subCommand, userPrefsOpt);
                        case "permitir", "proibir" -> handlePermissionCommand(ctx, tempChannelId, subCommand);
                        default ->
                                ctx.reply("Subcomando de sala desconhecido. Use: `limite`, `nome`, `trancar`, `destrancar`, `permitir`, `proibir`.");
                    };
                });
    }

    private CompletableFuture<Void> handlePreferenceCommand(CommandContext ctx, Optional<TemporaryChannelRecord> userTempChannelOpt, Optional<UserChannelPreference> userPrefsOpt, String subCommand) {
        String guildId = ctx.getGuildId();
        String authorId = ctx.getAuthor().getId();
        UserChannelPreference prefs = userPrefsOpt.orElse(new UserChannelPreference(guildId, authorId));

        switch (subCommand) {
            case "limite":
                if (ctx.getArgs().size() < 2) {
                    return ctx.reply("Uso: `!sala limite <numero>` (0 para ilimitado)");
                }
                try {
                    int limit = Integer.parseInt(ctx.getArgs().get(1));
                    if (limit < 0 || limit > 99) {
                        return ctx.reply("O limite deve ser entre 0 (ilimitado) e 99.");
                    }

                    prefs.preferredUserLimit = (limit);
                    return dbManager.updateUserChannelPreference(guildId, authorId, prefs.preferredUserLimit, prefs.preferredName, prefs.defaultLocked)
                            .thenCompose(v -> ctx.reply("Preferência de limite para seus futuros canais definida para " + (limit == 0 ? "ilimitado" : limit) + "."))
                            .thenCompose(v -> {
                                if (userTempChannelOpt.isPresent()) {
                                    String activeChannelId = userTempChannelOpt.get().channelId;
                                    ChannelModifyPayload payload = new ChannelModifyPayload();
                                    payload.setUserLimit(limit == 0 ? null : limit);
                                    return ctx.getClient().modifyChannel(activeChannelId, payload)
                                            .thenCompose(updatedChannel -> ctx.reply("Limite do seu canal ativo <#" + activeChannelId + "> também foi atualizado."))
                                            .thenApply(x -> null);
                                }
                                return CompletableFuture.completedFuture(null);
                            });
                } catch (NumberFormatException e) {
                    return ctx.reply("Por favor, forneça um número válido para o limite.");
                }

            case "nome":
                if (ctx.getArgs().size() < 2) {
                    return ctx.reply("Uso: `!sala nome <template>` (Use %username% para seu nome)");
                }
                String nameTemplate = String.join(" ", ctx.getArgs().subList(1, ctx.getArgs().size())).trim();
                if (nameTemplate.isEmpty() || nameTemplate.length() > 80) {
                    return ctx.reply("O template do nome deve ter entre 1 e 80 caracteres.");
                }

                prefs.preferredName = nameTemplate;
                return dbManager.updateUserChannelPreference(guildId, authorId, prefs.preferredUserLimit, prefs.preferredName, prefs.defaultLocked)
                        .thenCompose(v -> ctx.reply("Template de nome para seus futuros canais temporários definido para: '" + nameTemplate + "'."))
                        .thenCompose(v -> {
                            if (userTempChannelOpt.isPresent()) {
                                String activeChannelId = userTempChannelOpt.get().channelId;
                                String finalName = formatChannelName(nameTemplate, ctx.getAuthor().getUsername());
                                if (finalName.length() > 100) finalName = finalName.substring(0, 100);

                                ChannelModifyPayload namePayload = new ChannelModifyPayload();
                                namePayload.setName(finalName);
                                return ctx.getClient().modifyChannel(activeChannelId, namePayload)
                                        .thenCompose(x -> ctx.reply("Nome do seu canal ativo <#" + activeChannelId + "> também foi atualizado."))
                                        .thenApply(x -> null);
                            }
                            return CompletableFuture.completedFuture(null);
                        });
            default:
                return ctx.reply("Subcomando de preferência desconhecido.");
        }
    }

    private CompletableFuture<Void> handleLockUnlockCommand(CommandContext ctx, String tempChannelId, String subCommand, Optional<UserChannelPreference> userPrefsOpt) {
        String guildId = ctx.getGuildId();
        String authorId = ctx.getAuthor().getId();

        boolean lock = "trancar".equals(subCommand);
        EnumSet<Permission> allowEveryone = lock ? EnumSet.noneOf(Permission.class) : EnumSet.of(Permission.CONNECT);
        EnumSet<Permission> denyEveryone = lock ? EnumSet.of(Permission.CONNECT) : EnumSet.noneOf(Permission.class);


        UserChannelPreference prefs = userPrefsOpt.orElse(new UserChannelPreference(guildId, authorId));
        CompletableFuture<Void> updatePrefsFuture = dbManager.updateUserChannelPreference(guildId, authorId, prefs.preferredUserLimit, prefs.preferredName, prefs.defaultLocked);

        CompletableFuture<Void> modifyEveryonePerms = ctx.getClient().editChannelPermissions(
                tempChannelId, guildId, TargetType.ROLE,
                allowEveryone, denyEveryone
        );

        CompletableFuture<Void> allowOwner = ctx.getClient().editChannelPermissions(
                tempChannelId, authorId, TargetType.MEMBER,
                EnumSet.of(Permission.CONNECT, Permission.SPEAK),
                EnumSet.noneOf(Permission.class)
        );

        return CompletableFuture.allOf(updatePrefsFuture, modifyEveryonePerms, allowOwner)
                .thenCompose(v -> ctx.reply("Canal <#" + tempChannelId + "> " + (lock ? "trancado" : "destrancado") + " com sucesso!"))
                .thenApply(x -> null);
    }

    private CompletableFuture<Void> handlePermissionCommand(CommandContext ctx, String tempChannelId, String subCommand) {
        if (ctx.getArgs().size() < 2) {
            return ctx.reply("Uso: `!sala " + subCommand + " <@menção_do_usuário ou @menção_do_cargo>`");
        }
        String mention = ctx.getArgs().get(1);
        String targetId;
        TargetType targetType;

        if (mention.startsWith("<@&") && mention.endsWith(">")) {
            targetId = mention.substring(3, mention.length() - 1);
            targetType = TargetType.ROLE;
        } else if (mention.startsWith("<@") && mention.endsWith(">")) {
            targetId = mention.replaceAll("<@!?", "").replaceAll(">", "");
            targetType = TargetType.MEMBER;
        } else {
            return ctx.reply("Por favor, mencione um usuário ou cargo válido.");
        }

        EnumSet<Permission> allowPerms = EnumSet.noneOf(Permission.class);
        EnumSet<Permission> denyPerms = EnumSet.noneOf(Permission.class);
        String actionVerb;

        if ("permitir".equals(subCommand)) {
            allowPerms.addAll(EnumSet.of(Permission.CONNECT, Permission.SPEAK, Permission.VIEW_CHANNEL));
            actionVerb = "permitido";
        } else if ("proibir".equals(subCommand)) {
            denyPerms.addAll(EnumSet.of(Permission.CONNECT, Permission.SPEAK, Permission.VIEW_CHANNEL));
            actionVerb = "proibido";
        } else {
            return ctx.reply("Subcomando de permissão desconhecido.");
        }

        if (targetType == TargetType.MEMBER && targetId.equals(ctx.getAuthor().getId())) {
            return ctx.reply("Você não pode " + subCommand + " a si mesmo.");
        }

        return ctx.getClient().editChannelPermissions(
                        tempChannelId,
                        targetId,
                        targetType,
                        allowPerms,
                        denyPerms
                )
                .thenCompose(v -> ctx.reply(mention + " agora tem o acesso " + actionVerb + " ao canal <#" + tempChannelId + ">."))
                .thenApply(x -> null);
    }

    private String formatChannelName(String template, String username) {
        if (template == null || template.isEmpty()) {
            return "Sala de " + username;
        }
        String name = template.replace("%username%", username);
        name = name.replaceAll("[^a-zA-Z0-9-]", "").toLowerCase();
        name = name.replaceAll("\\s+", "-");
        return name;
    }
}