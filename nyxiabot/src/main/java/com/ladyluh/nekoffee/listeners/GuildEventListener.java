package com.ladyluh.nekoffee.listeners;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.Guild;
import com.ladyluh.nekoffee.api.entities.Member;
import com.ladyluh.nekoffee.api.event.Event;
import com.ladyluh.nekoffee.api.event.EventListener;
import com.ladyluh.nekoffee.api.event.guild.member.GuildMemberAddEvent;
import com.ladyluh.nekoffee.builder.EmbedBuilder;
import com.ladyluh.nekoffee.builder.MessageBuilder;
import com.ladyluh.nekoffee.config.ConfigManager;
import com.ladyluh.nekoffee.database.DatabaseManager;
import com.ladyluh.nekoffee.model.gateway.ReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.OffsetDateTime;

public class GuildEventListener implements EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildEventListener.class);
    private final ConfigManager config;
    private final NekoffeeClient client;

    public GuildEventListener(ConfigManager config, NekoffeeClient client, DatabaseManager dbManager) {
        this.config = config;
        this.client = client;

    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof ReadyEvent readyEvent) {
            handleReady(readyEvent);
        } else if (event instanceof GuildMemberAddEvent gmaEvent) {
            handleGuildMemberAdd(gmaEvent);
        }

    }

    private void handleReady(ReadyEvent event) {
        LOGGER.info("Bot está PRONTO! Logado como: {} (ID: {})",
                event.getSelfUser().getAsTag(), event.getSelfUser().getId());
        LOGGER.info("Session ID: {}, Resume URL: {}", event.getSessionId(), event.getResumeGatewayUrl());
    }

    private void handleGuildMemberAdd(GuildMemberAddEvent event) {
        Member newMember = event.getMember();
        String guildId = event.getGuildId();
        String autoAssignRoleId = config.getAutoAssignRoleId();
        String welcomeChannelId = config.getWelcomeChannelId();

        LOGGER.info("Novo membro {} (ID: {}) entrou na Guild {}",
                (newMember.getUser() != null ? newMember.getUser().getAsTag() : newMember.getId()),
                newMember.getId(),
                event.getGuild().thenApply(Guild::getName).exceptionally(x -> "ID: " + guildId).join());

        if (autoAssignRoleId != null && !autoAssignRoleId.isEmpty()) {
            LOGGER.info("Tentando adicionar cargo {} a {}", autoAssignRoleId, newMember.getEffectiveName());
            client.addRoleToMember(guildId, newMember.getId(), autoAssignRoleId)
                    .thenRun(() -> LOGGER.info("Cargo {} atribuído com sucesso a {}!", autoAssignRoleId, newMember.getEffectiveName()))
                    .exceptionally(ex -> {
                        LOGGER.error("Falha ao atribuir cargo {} a {}:", autoAssignRoleId, newMember.getEffectiveName(), ex);
                        return null;
                    });
        }

        if (welcomeChannelId != null && !welcomeChannelId.isEmpty() && newMember.getUser() != null) {
            EmbedBuilder welcomeEmbed = new EmbedBuilder()
                    .setTitle("👋 Boas-vindas!")
                    .setDescription("Bem-vindo(a) ao servidor, **" + newMember.getUser().getAsTag() + "**!")
                    .setColor(new Color(0x4CAF50))
                    .setThumbnail(newMember.getUser().getEffectiveAvatarUrl())
                    .setTimestamp(OffsetDateTime.now());

            MessageBuilder welcomeMsgBuilder = new MessageBuilder().addEmbed(welcomeEmbed);

            client.sendMessage(welcomeChannelId, welcomeMsgBuilder.build())
                    .thenAccept(x -> LOGGER.info("Mensagem de boas-vindas (embed) enviada para {}", newMember.getUser().getAsTag()))
                    .exceptionally(ex -> {
                        LOGGER.error("Falha ao enviar mensagem de boas-vindas (embed):", ex);
                        return null;
                    });
        }
    }
}