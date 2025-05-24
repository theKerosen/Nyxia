package com.ladyluh.nekoffee.listeners;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.Member;
import com.ladyluh.nekoffee.api.entities.TargetType;
import com.ladyluh.nekoffee.api.entities.channel.ChannelType;
import com.ladyluh.nekoffee.api.event.Event;
import com.ladyluh.nekoffee.api.event.EventListener;
import com.ladyluh.nekoffee.api.event.voice.VoiceStateUpdateEvent;
import com.ladyluh.nekoffee.api.payload.channel.CreateGuildChannelPayload;
import com.ladyluh.nekoffee.api.payload.permission.Permission;
import com.ladyluh.nekoffee.cache.VoiceStateCacheManager;
import com.ladyluh.nekoffee.config.ConfigManager;
import com.ladyluh.nekoffee.database.DatabaseManager;
import com.ladyluh.nekoffee.database.TemporaryChannelRecord;
import com.ladyluh.nekoffee.database.UserChannelPreference;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;


public class TemporaryChannelListener implements EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(TemporaryChannelListener.class);
    private final NekoffeeClient client;
    private final DatabaseManager dbManager;

    private final String hubChannelId;
    private final String temporaryChannelCategoryId;
    private final String temporaryChannelNamePrefix;
    private final VoiceStateCacheManager voiceStateCacheManager;

    public TemporaryChannelListener(ConfigManager config, NekoffeeClient client, DatabaseManager dbManager, VoiceStateCacheManager voiceStateCacheManager) {
        this.client = client;
        this.dbManager = dbManager;

        this.hubChannelId = config.getHubChannelId();
        this.temporaryChannelCategoryId = config.getTempChannelCategoryId();
        this.temporaryChannelNamePrefix = config.getTempChannelNamePrefix() != null ? config.getTempChannelNamePrefix() : "Sala de ";
        this.voiceStateCacheManager = voiceStateCacheManager;

        if (this.hubChannelId == null || this.hubChannelId.isEmpty()) {
            LOGGER.warn("ID do Hub Channel para canais temporários não configurado. Funcionalidade desabilitada.");
        }
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof VoiceStateUpdateEvent vsEvent) {
            handleVoiceStateUpdate(vsEvent);
        }
    }

    private void handleVoiceStateUpdate(VoiceStateUpdateEvent event) {
        if (hubChannelId == null || hubChannelId.isEmpty()) return;

        String userId = event.getUserId();
        String newChannelId = event.getChannelId();
        String guildId = event.getGuildId();

        if (guildId == null) return;

        dbManager.getTemporaryChannelByOwner(guildId, userId)
                .thenAccept(existingUserChannelOpt -> {
                    boolean joinedHubChannel = hubChannelId.equals(newChannelId);
                    LOGGER.debug("Usuário {} entrou no Hub Channel ({} == {})? {}. Canal temporário existente no DB? {}",
                            userId, hubChannelId, newChannelId, joinedHubChannel, existingUserChannelOpt.isPresent());

                    if (joinedHubChannel && existingUserChannelOpt.isEmpty()) {
                        LOGGER.info("CONDIÇÃO DE CRIAÇÃO ATENDIDA para User ID: {}", userId);
                        createTemporaryChannelForUser(event, guildId, userId);
                    } else if (joinedHubChannel) {
                        LOGGER.info("Usuário {} entrou no Hub Channel, mas já possui um canal temporário ativo: {}. Nenhuma ação de criação.",
                                userId, existingUserChannelOpt.get().channelId);
                    } else {
                        LOGGER.debug("Condição de criação NÃO atendida. JoinedHub: {}, HasExistingChannel: {}",
                                false, existingUserChannelOpt.isPresent());
                    }

                    if (existingUserChannelOpt.isPresent()) {
                        String ownedTempChannelId = existingUserChannelOpt.get().channelId;
                        if (!ownedTempChannelId.equals(newChannelId)) {
                            LOGGER.info("Usuário {} saiu/mudou do seu canal temporário {}. Verificando para deleção.", userId, ownedTempChannelId);
                            checkAndDeleteChannelIfEmpty(guildId, ownedTempChannelId);
                        } else {
                            LOGGER.debug("Usuário {} ainda está no seu canal temporário {} ou newChannelId é igual (nenhuma mudança relevante para deleção).", userId, ownedTempChannelId);
                        }
                    } else {
                        LOGGER.debug("Usuário {} não tinha um canal temporário registrado para verificar deleção.", userId);
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.error("Erro ao buscar canal temporário existente para usuário {}:", userId, ex);
                    return null;
                });
    }

    private void createTemporaryChannelForUser(VoiceStateUpdateEvent event, String guildId, String userId) {
        event.retrieveMember().thenAcceptAsync(member -> {
            if (member == null || member.getUser() == null) {
                LOGGER.warn("Não foi possível buscar o membro {} para criar canal temporário.", userId);
                return;
            }

            dbManager.getUserChannelPreference(guildId, userId)
                    .thenCompose(userPrefsOpt -> {
                        UserChannelPreference prefs = userPrefsOpt.orElse(new UserChannelPreference(guildId, userId));

                        String tempChannelName = getString(member);

                        LOGGER.info("Usuário {} ({}) entrou no Hub. Criando canal: {}", member.getEffectiveName(), userId, tempChannelName);

                        CreateGuildChannelPayload channelPayload = getCreateGuildChannelPayload(tempChannelName, prefs);


                        return client.createGuildChannel(guildId, tempChannelName, ChannelType.GUILD_VOICE, channelPayload.getParentId())
                                .thenCompose(createdChannel -> {
                                    LOGGER.info("Canal temporário {} (ID: {}) criado. Aplicando configurações e movendo membro...",
                                            tempChannelName, createdChannel.getId());
                                    return dbManager.addTemporaryChannel(createdChannel.getId(), guildId, userId)
                                            .thenApply(v -> createdChannel);
                                })
                                .thenCompose(createdChannel -> {
                                    CompletableFuture<Void> permissionsFuture;
                                    if (prefs.defaultLocked == 1) {
                                        LOGGER.info("Canal {} será criado como trancado por preferência do usuário.", createdChannel.getId());

                                        CompletableFuture<Void> lockEveryone = client.editChannelPermissions(
                                                createdChannel.getId(), guildId, TargetType.ROLE,
                                                EnumSet.noneOf(Permission.class), EnumSet.of(Permission.CONNECT, Permission.VIEW_CHANNEL, Permission.SPEAK)
                                        );
                                        CompletableFuture<Void> allowOwner = client.editChannelPermissions(
                                                createdChannel.getId(), userId, TargetType.MEMBER,
                                                EnumSet.of(Permission.CONNECT, Permission.VIEW_CHANNEL, Permission.SPEAK), EnumSet.noneOf(Permission.class)
                                        );
                                        permissionsFuture = CompletableFuture.allOf(lockEveryone, allowOwner);
                                    } else {

                                        permissionsFuture = client.editChannelPermissions(
                                                createdChannel.getId(), guildId, TargetType.ROLE,
                                                EnumSet.of(Permission.CONNECT, Permission.VIEW_CHANNEL, Permission.SPEAK), EnumSet.noneOf(Permission.class)
                                        );
                                    }
                                    return permissionsFuture.thenApply(v -> createdChannel);
                                })
                                .thenCompose(createdChannel -> {
                                    LOGGER.info("Movendo {} para o canal temporário {}", member.getEffectiveName(), createdChannel.getName());
                                    return client.modifyGuildMemberVoiceChannel(guildId, userId, createdChannel.getId())
                                            .thenApply(v -> createdChannel);
                                });
                    })
                    .thenAccept(finalChannel -> LOGGER.info("Usuário {} movido para seu canal temporário {}.", member.getEffectiveName(), finalChannel.getName()))
                    .exceptionally(ex -> {
                        LOGGER.error("Falha durante a criação/configuração/movimentação do canal temporário para {}:", member.getEffectiveName(), ex);
                        return null;
                    });
        }).exceptionally(ex -> {
            LOGGER.error("Falha ao buscar membro {} para criar canal temporário (exceção externa):", userId, ex);
            return null;
        });
    }

    private @NotNull CreateGuildChannelPayload getCreateGuildChannelPayload(String tempChannelName, UserChannelPreference prefs) {
        CreateGuildChannelPayload channelPayload = new CreateGuildChannelPayload(tempChannelName, ChannelType.GUILD_VOICE);
        if (temporaryChannelCategoryId != null && !temporaryChannelCategoryId.isEmpty()) {
            channelPayload.setParentId(temporaryChannelCategoryId);
        }
        if (prefs.preferredUserLimit != null) {
            channelPayload.setUserLimit(prefs.preferredUserLimit);
        }
        return channelPayload;
    }

    private @NotNull String getString(Member member) {
        String tempChannelName;

        tempChannelName = (this.temporaryChannelNamePrefix != null ? this.temporaryChannelNamePrefix : "Sala de ") + member.getEffectiveName();

        if (tempChannelName.length() > 100) tempChannelName = tempChannelName.substring(0, 100);
        return tempChannelName;
    }

    private void checkAndDeleteChannelIfEmpty(String guildId, String channelId) {
        dbManager.getTemporaryChannel(channelId)
                .thenAccept(tempChannelOpt -> {
                    if (tempChannelOpt.isEmpty()) {
                        return;
                    }
                    TemporaryChannelRecord tempChannelRecord = tempChannelOpt.get();

                    boolean isChannelActuallyEmpty = voiceStateCacheManager.isVoiceChannelEmpty(guildId, channelId);

                    if (isChannelActuallyEmpty) {
                        LOGGER.info("Canal temporário {} está vazio. Deletando.", channelId);
                        deleteTemporaryChannel(channelId);
                    } else {
                        LOGGER.debug("Canal temporário {} NÃO está vazio. Não deletando por esta ação. (Membros no cache: {})",
                                channelId, voiceStateCacheManager.guildVoiceChannelMembers.get(guildId).get(channelId).size());
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.error("Erro ao buscar canal temporário {} para deleção:", channelId, ex);
                    return null;
                });
    }

    public void deleteTemporaryChannel(String channelId) {
        if (channelId == null) return;

        client.deleteChannel(channelId)
                .thenRun(() -> {
                    LOGGER.info("Canal temporário {} deletado do Discord.", channelId);
                    dbManager.removeTemporaryChannel(channelId);
                })
                .exceptionally(ex -> {
                    if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unknown channel")) {
                        LOGGER.warn("Tentativa de deletar canal temporário {} que já não existe. Removendo do DB.", channelId);
                        dbManager.removeTemporaryChannel(channelId);
                    } else {
                        LOGGER.error("Falha ao deletar canal temporário {}:", channelId, ex);
                    }
                    return null;
                });
    }
}