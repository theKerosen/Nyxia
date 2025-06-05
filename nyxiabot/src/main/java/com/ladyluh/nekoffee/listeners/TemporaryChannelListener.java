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
import com.ladyluh.nekoffee.database.GuildConfig;
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
    private final ConfigManager config;
    private final DatabaseManager dbManager;
    private final VoiceStateCacheManager voiceStateCacheManager;

    public TemporaryChannelListener(ConfigManager config, NekoffeeClient client, DatabaseManager dbManager, VoiceStateCacheManager voiceStateCacheManager) {
        this.client = client;
        this.dbManager = dbManager;
        this.config = config;

        this.voiceStateCacheManager = voiceStateCacheManager;

    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof VoiceStateUpdateEvent vsEvent) {
            handleVoiceStateUpdate(vsEvent);
        }
    }

    private void handleVoiceStateUpdate(VoiceStateUpdateEvent event) {
        String guildId = event.getGuildId();
        if (guildId == null) return; // Ignorar DMs de voz

        dbManager.getGuildConfig(guildId)
                .thenAccept(configOpt -> {
                    GuildConfig guildConfig = configOpt.orElse(new GuildConfig(guildId));

                    String hubChannelId = guildConfig.tempHubChannelId; // Pega da config da guild
                    String tempChannelCategoryId = guildConfig.tempChannelCategoryId; // Pega da config
                    String tempChannelNamePrefix = guildConfig.tempChannelNamePrefix; // Pega da config
                    // Outros padrões como userLimit, defaultLock também devem vir de guildConfig.

                    if (hubChannelId == null || hubChannelId.isEmpty()) {
                        LOGGER.debug("Hub Channel ID não configurado para guild {}. Funcionalidade de canais temporários desabilitada.", guildId);
                        return;
                    }

                    String userId = event.getUserId();
                    String newChannelId = event.getChannelId();

                    dbManager.getTemporaryChannelByOwner(guildId, userId)
                            .thenAccept(existingUserChannelOpt -> {
                                boolean joinedHubChannel = hubChannelId.equals(newChannelId);
                                LOGGER.debug("Usuário {} entrou no Hub Channel ({} == {})? {}. Canal temporário existente no DB? {}",
                                        userId, hubChannelId, newChannelId, joinedHubChannel, existingUserChannelOpt.isPresent());

                                if (joinedHubChannel && existingUserChannelOpt.isEmpty()) {
                                    LOGGER.info("CONDIÇÃO DE CRIAÇÃO ATENDIDA para User ID: {}", userId);
                                    // Passar as configs da guild para o método de criação
                                    createTemporaryChannelForUser(event, guildId, userId, guildConfig);
                                } else if (joinedHubChannel && existingUserChannelOpt.isPresent()) {
                                    LOGGER.info("Usuário {} entrou no Hub Channel, mas já possui um canal temporário ativo: {}. Nenhuma ação de criação.",
                                            userId, existingUserChannelOpt.get().channelId);
                                } else {
                                    LOGGER.debug("Condição de criação NÃO atendida. JoinedHub: {}, HasExistingChannel: {}",
                                            joinedHubChannel, existingUserChannelOpt.isPresent());
                                }

                                // Lógica de Deleção:
                                if (existingUserChannelOpt.isPresent()) {
                                    String ownedTempChannelId = existingUserChannelOpt.get().channelId;
                                    if (!ownedTempChannelId.equals(newChannelId)) {
                                        LOGGER.info("Usuário {} saiu/mudou do seu canal temporário {}. Verificando para deleção.", userId, ownedTempChannelId);
                                        checkAndDeleteChannelIfEmpty(guildId, ownedTempChannelId, userId);
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
                })
                .exceptionally(ex -> {
                    LOGGER.error("Erro ao buscar configurações da guild {} para VoiceStateUpdateEvent:", guildId, ex);
                    return null;
                });
    }

    // Mude a assinatura para aceitar GuildConfig
    private void createTemporaryChannelForUser(VoiceStateUpdateEvent event, String guildId, String userId, GuildConfig guildConfig) {
        event.retrieveMember().thenAcceptAsync(member -> {
            if (member == null || member.getUser() == null) { /* ... */ return; }

            dbManager.getUserChannelPreference(guildId, userId)
                    .thenCompose(userPrefsOpt -> {
                        // Preferências do usuário têm prioridade sobre as da guildConfig.
                        // Usar null-coalescing: preference.orElse(guildConfig.or(ConfigManager.defaults))
                        UserChannelPreference prefs = userPrefsOpt.orElse(new UserChannelPreference(guildId, userId));
                        // Aplicar padrões da guildConfig se a preferência do usuário for null
                        Integer finalUserLimit = prefs.preferredUserLimit != null ? prefs.preferredUserLimit : guildConfig.defaultTempChannelUserLimit;
                        String finalNamePrefix = prefs.preferredName != null && !prefs.preferredName.isEmpty() ? prefs.preferredName : guildConfig.tempChannelNamePrefix;
                        Integer finalDefaultLocked = prefs.defaultLocked != null ? prefs.defaultLocked : guildConfig.defaultTempChannelLock;

                        // Usar o prefixo da guildConfig ou o padrão do ConfigManager se não houver na guildConfig
                        if (finalNamePrefix == null || finalNamePrefix.isEmpty()) {
                            finalNamePrefix = config.getTempChannelNamePrefix(); // Padrão do ConfigManager
                        }
                        if (finalUserLimit == null) {
                            finalUserLimit = config.getTempChannelDefaultLock(); // Padrão do ConfigManager
                        }
                        if (finalDefaultLocked == null) {
                            finalDefaultLocked = config.getTempChannelDefaultLock(); // Padrão do ConfigManager
                        }


                        String tempChannelName;
                        if (finalNamePrefix != null && !finalNamePrefix.isEmpty()) {
                            tempChannelName = finalNamePrefix.replace("%username%", member.getEffectiveName());
                        } else {
                            tempChannelName = "Sala de " + member.getEffectiveName(); // Fallback final
                        }
                        if (tempChannelName.length() > 100) tempChannelName = tempChannelName.substring(0, 100);

                        LOGGER.info("Usuário {} ({}) entrou no Hub. Criando canal: {}", member.getEffectiveName(), userId, tempChannelName);

                        CreateGuildChannelPayload channelPayload = getCreateGuildChannelPayload(guildConfig, tempChannelName, finalUserLimit);

                        String finalTempChannelName = tempChannelName; // Capturar para o lambda
                        Integer finalDefaultLockedVal = finalDefaultLocked; // Capturar para o lambda
                        return client.createGuildChannel(guildId, channelPayload.getName(), ChannelType.GUILD_VOICE, channelPayload.getParentId())
                                .thenCompose(createdChannel -> {
                                    LOGGER.info("Canal temporário {} (ID: {}) criado. Aplicando configurações e movendo membro...",
                                            finalTempChannelName, createdChannel.getId());
                                    return dbManager.addTemporaryChannel(createdChannel.getId(), guildId, userId)
                                            .thenApply(v -> createdChannel);
                                })
                                .thenCompose(createdChannel -> { // Apply lock status
                                    CompletableFuture<Void> permissionsFuture = CompletableFuture.completedFuture(null);
                                    if (finalDefaultLockedVal == 1) { // Usar o valor booleano diretamente
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
                                        // Garantir que @everyone possa conectar se não for trancado por padrão
                                        permissionsFuture = client.editChannelPermissions(
                                                createdChannel.getId(), guildId, TargetType.ROLE,
                                                EnumSet.of(Permission.CONNECT, Permission.VIEW_CHANNEL, Permission.SPEAK), EnumSet.noneOf(Permission.class)
                                        );
                                    }
                                    return permissionsFuture.thenApply(v -> createdChannel);
                                })
                                .thenCompose(createdChannel -> { // Move member
                                    LOGGER.info("Movendo {} para o canal temporário {}", member.getEffectiveName(), createdChannel.getName());
                                    return client.modifyGuildMemberVoiceChannel(guildId, userId, createdChannel.getId())
                                            .thenApply(v -> createdChannel);
                                });
                    })
                    .thenAccept(finalChannel -> {
                        LOGGER.info("Usuário {} movido para seu canal temporário {}.", member.getEffectiveName(), finalChannel.getName());
                    })
                    .exceptionally(ex -> {
                        LOGGER.error("Falha durante a criação/configuração/movimentação do canal temporário para {}:", member.getEffectiveName(), ex);
                        return null;
                    });
        }).exceptionally(ex -> {
            LOGGER.error("Falha ao buscar membro {} para criar canal temporário (exceção externa):", userId, ex);
            return null;
        });
    }

    private static @NotNull CreateGuildChannelPayload getCreateGuildChannelPayload(GuildConfig guildConfig, String tempChannelName, Integer finalUserLimit) {
        CreateGuildChannelPayload channelPayload = new CreateGuildChannelPayload(tempChannelName, ChannelType.GUILD_VOICE);
        if (guildConfig.tempChannelCategoryId != null && !guildConfig.tempChannelCategoryId.isEmpty()) {
            channelPayload.setParentId(guildConfig.tempChannelCategoryId);
        }
        // Aplicar o limite de usuário
        channelPayload.setUserLimit(finalUserLimit == 0 ? null : finalUserLimit);
        return channelPayload;
    }


    private void checkAndDeleteChannelIfEmpty(String guildId, String channelId, String userId) {
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