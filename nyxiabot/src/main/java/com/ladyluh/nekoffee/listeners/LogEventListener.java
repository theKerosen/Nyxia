package com.ladyluh.nekoffee.listeners;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.Message;
import com.ladyluh.nekoffee.api.entities.User;
import com.ladyluh.nekoffee.api.event.Event;
import com.ladyluh.nekoffee.api.event.EventListener;
import com.ladyluh.nekoffee.api.event.message.MessageDeleteEvent;
import com.ladyluh.nekoffee.api.event.message.MessageUpdateEvent;
import com.ladyluh.nekoffee.builder.EmbedBuilder;
import com.ladyluh.nekoffee.builder.MessageBuilder;
import com.ladyluh.nekoffee.config.ConfigManager;
import com.ladyluh.nekoffee.database.DatabaseManager;
import com.ladyluh.nekoffee.database.GuildConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.OffsetDateTime;

public class LogEventListener implements EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogEventListener.class);
    private final NekoffeeClient client;
    private final DatabaseManager dbManager;

    public LogEventListener(ConfigManager config, NekoffeeClient client, DatabaseManager dbManager) {
        this.client = client;
        this.dbManager = dbManager;
    }
    @Override
    public void onEvent(Event event) {
        if (event instanceof MessageUpdateEvent muEvent) {
            handleMessageUpdate(muEvent);
        } else if (event instanceof MessageDeleteEvent mdEvent) {
            handleMessageDelete(mdEvent);
        }
    }

    private void handleMessageUpdate(MessageUpdateEvent event) {
        String guildId = event.getMessage().getGuildId();
        if (guildId == null) return; 

        dbManager.getGuildConfig(guildId)
                .thenAccept(configOpt -> {
                    GuildConfig guildConfig = configOpt.orElse(new GuildConfig(guildId));
                    String logChannelId = guildConfig.logChannelId; 

                    if (logChannelId == null || logChannelId.isEmpty()) {
                        LOGGER.trace("Log channel ID não configurado para guild {}. Log de MessageUpdate pulado.", guildId);
                        return;
                    }

                    Message updatedMessage = event.getMessage();
                    User author = updatedMessage.getAuthor();

                    EmbedBuilder logEmbed = new EmbedBuilder()
                            .setTitle("📝 Mensagem Editada")
                            .setColor(Color.ORANGE)
                            .addField("Canal", "<#" + updatedMessage.getChannelId() + "> (`" + updatedMessage.getChannelId() + "`)", true);

                    if (author != null) {
                        logEmbed.addField("Autor", author.getAsTag() + " (`" + author.getId() + "`)", true);
                    } else if (updatedMessage.getAuthor() != null && updatedMessage.getAuthor().getId() != null) {
                        logEmbed.addField("Autor ID", "`" + updatedMessage.getAuthor().getId() + "`", true);
                    } else {
                        logEmbed.addField("Autor", "Desconhecido", true);
                    }

                    logEmbed.addField("ID da Mensagem", "`" + updatedMessage.getId() + "`", false)
                            .addField("Novo Conteúdo", updatedMessage.getContentRaw() != null && !updatedMessage.getContentRaw().isEmpty() ? updatedMessage.getContentRaw() : "*Conteúdo não presente ou embed editado*", false)
                            .setTimestamp(OffsetDateTime.now());

                    String messageLink = String.format("https://discord.com/channels/%s/%s/%s",
                            guildId,
                            updatedMessage.getChannelId(),
                            updatedMessage.getId());
                    logEmbed.addField("Link", "[Pular para Mensagem](" + messageLink + ")", false);

                    client.sendMessage(logChannelId, new MessageBuilder().addEmbed(logEmbed).build())
                            .exceptionally(ex -> {
                                LOGGER.error("Falha ao enviar log de MessageUpdate para guild {}:", guildId, ex);
                                return null;
                            });
                    LOGGER.info("Log: Mensagem {} editada no canal {} da guild {}", updatedMessage.getId(), updatedMessage.getChannelId(), guildId);
                })
                .exceptionally(ex -> {
                    LOGGER.error("Erro ao buscar configurações da guild para MessageUpdateEvent");
                    return null;
                });
    }

    private void handleMessageDelete(MessageDeleteEvent event) {
        String guildId = event.getGuildId();
        if (guildId == null) return; 

        dbManager.getGuildConfig(guildId)
                .thenAccept(configOpt -> {
                    GuildConfig guildConfig = configOpt.orElse(new GuildConfig(guildId));
                    String logChannelId = guildConfig.logChannelId;

                    if (logChannelId == null || logChannelId.isEmpty()) {
                        LOGGER.trace("Log channel ID não configurado para guild {}. Log de MessageDelete pulado.", guildId);
                        return;
                    }

                    EmbedBuilder logEmbed = new EmbedBuilder()
                            .setTitle("🗑️ Mensagem Deletada")
                            .setColor(Color.RED)
                            .addField("Canal", "<#" + event.getChannelId() + "> (`" + event.getChannelId() + "`)", false)
                            .addField("ID da Mensagem", "`" + event.getMessageId() + "`", false)
                            .setTimestamp(OffsetDateTime.now());

                    logEmbed.addField("Servidor ID", "`" + guildId + "`", true); 

                    client.sendMessage(logChannelId, new MessageBuilder().addEmbed(logEmbed).build())
                            .exceptionally(ex -> {
                                LOGGER.error("Falha ao enviar log de MessageDelete para guild {}:", guildId, ex);
                                return null;
                            });
                    LOGGER.info("Log: Mensagem {} deletada no canal {} da guild {}", event.getMessageId(), event.getChannelId(), guildId);
                })
                .exceptionally(ex -> {
                    LOGGER.error("Erro ao buscar configurações da guild para MessageDeleteEvent");
                    return null;
                });
    }
}