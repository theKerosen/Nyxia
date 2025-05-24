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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.OffsetDateTime;

public class LogEventListener implements EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogEventListener.class);
    private final NekoffeeClient client;
    private final String logChannelId;

    public LogEventListener(ConfigManager config, NekoffeeClient client) {
        this.client = client;
        this.logChannelId = config.getLogChannelId();
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
        if (logChannelId == null || logChannelId.isEmpty()) return;

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

        String guildId = updatedMessage.getGuildId();

        if (guildId != null) {
            String messageLink = String.format("https://discord.com/channels/%s/%s/%s",
                    guildId,
                    updatedMessage.getChannelId(),
                    updatedMessage.getId());
            logEmbed.addField("Link", "[Pular para Mensagem](" + messageLink + ")", false);
        }


        client.sendMessage(logChannelId, new MessageBuilder().addEmbed(logEmbed).build())
                .exceptionally(ex -> {
                    LOGGER.error("Falha ao enviar log de MessageUpdate:", ex);
                    return null;
                });
        LOGGER.info("Log: Mensagem {} editada no canal {}", updatedMessage.getId(), updatedMessage.getChannelId());
    }

    private void handleMessageDelete(MessageDeleteEvent event) {
        if (logChannelId == null || logChannelId.isEmpty()) return;

        EmbedBuilder logEmbed = new EmbedBuilder()
                .setTitle("🗑️ Mensagem Deletada")
                .setColor(Color.RED)
                .addField("Canal", "<#" + event.getChannelId() + "> (`" + event.getChannelId() + "`)", false)
                .addField("ID da Mensagem", "`" + event.getMessageId() + "`", false)
                .setTimestamp(OffsetDateTime.now());

        if (event.getGuildId() != null) {
            logEmbed.addField("Servidor ID", "`" + event.getGuildId() + "`", true);
        }

        client.sendMessage(logChannelId, new MessageBuilder().addEmbed(logEmbed).build())
                .exceptionally(ex -> {
                    LOGGER.error("Falha ao enviar log de MessageDelete:", ex);
                    return null;
                });
        LOGGER.info("Log: Mensagem {} deletada no canal {}", event.getMessageId(), event.getChannelId());
    }
}