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
import com.ladyluh.nekoffee.database.GuildConfig;
import com.ladyluh.nekoffee.model.gateway.ReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.OffsetDateTime;

public class GuildEventListener implements EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildEventListener.class);
    private final NekoffeeClient client;
    private final DatabaseManager dbManager;

    public GuildEventListener(ConfigManager config, NekoffeeClient client, DatabaseManager dbManager) {
        this.client = client;
        this.dbManager = dbManager;

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

        if (guildId == null) {
            LOGGER.warn("GuildMemberAddEvent sem guildId para membro {}. Ignorando.", newMember.getId());
            return;
        }

        // Buscar a configuração da guild
        dbManager.getGuildConfig(guildId)
                .thenAccept(configOpt -> {
                    // Usar orElse para obter a configuração ou um objeto com nulls
                    GuildConfig guildConfig = configOpt.orElse(new GuildConfig(guildId));

                    String autoAssignRoleId = guildConfig.autoAssignRoleId;
                    String welcomeChannelId = guildConfig.welcomeChannelId;

                    LOGGER.info("Novo membro {} (ID: {}) entrou na Guild {}",
                            (newMember.getUser() != null ? newMember.getUser().getAsTag() : newMember.getId()),
                            newMember.getId(),
                            event.getGuild().thenApply(Guild::getName).exceptionally(ex -> "ID: " + guildId).join());

                    if (autoAssignRoleId != null && !autoAssignRoleId.isEmpty()) {
                        LOGGER.info("Tentando adicionar cargo {} a {}", autoAssignRoleId, newMember.getEffectiveName());
                        client.addRoleToMember(guildId, newMember.getId(), autoAssignRoleId)
                                .thenRun(() -> LOGGER.info("Cargo {} atribuído com sucesso a {}!", autoAssignRoleId, newMember.getEffectiveName()))
                                .exceptionally(ex -> {
                                    LOGGER.error("Falha ao atribuir cargo {} a {}:", autoAssignRoleId, newMember.getEffectiveName(), ex);
                                    return null;
                                });
                    } else {
                        LOGGER.debug("Auto-assign role ID não configurado para guild {}.", guildId);
                    }

                    if (welcomeChannelId != null && !welcomeChannelId.isEmpty() && newMember.getUser() != null) {
                        EmbedBuilder welcomeEmbed = new EmbedBuilder()
                                .setTitle("👋Boas vindas!")
                                .setDescription("Bem-vindx ao servidor, **" + newMember.getUser().getAsTag() + "**!")
                                .setColor(new Color(0x4CAF50))
                                .setThumbnail(newMember.getUser().getEffectiveAvatarUrl())
                                .setFooter(client.getSelfUser().getUsername(), client.getSelfUser().getEffectiveAvatarUrl())
                                .setTimestamp(OffsetDateTime.now());

                        MessageBuilder welcomeMsgBuilder = new MessageBuilder().addEmbed(welcomeEmbed);

                        client.sendMessage(welcomeChannelId, welcomeMsgBuilder.build())
                                .thenAccept(ex -> LOGGER.info("Mensagem de boas-vindas (embed) enviada para {}", newMember.getUser().getAsTag()))
                                .exceptionally(ex -> {
                                    LOGGER.error("Falha ao enviar mensagem de boas-vindas (embed):", ex);
                                    return null;
                                });
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.error("Erro ao buscar configurações da guild para GuildMemberAddEvent:");
                    return null;
                });
    }
}