package com.ladyluh.nekoffee.listeners;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.TargetType;
import com.ladyluh.nekoffee.api.entities.channel.Channel;
import com.ladyluh.nekoffee.api.entities.channel.ChannelType;
import com.ladyluh.nekoffee.api.event.Event;
import com.ladyluh.nekoffee.api.event.EventListener;
import com.ladyluh.nekoffee.api.event.guild.GuildCreateEvent;
import com.ladyluh.nekoffee.api.event.voice.VoiceStateUpdateEvent;
import com.ladyluh.nekoffee.api.payload.channel.ChannelModifyPayload;
import com.ladyluh.nekoffee.api.payload.channel.CreateGuildChannelPayload;
import com.ladyluh.nekoffee.api.payload.permission.Permission;
import com.ladyluh.nekoffee.cache.VoiceStateCacheManager;
import com.ladyluh.nekoffee.config.ConfigManager;
import com.ladyluh.nekoffee.database.DatabaseManager;
import com.ladyluh.nekoffee.database.GuildConfig;
import com.ladyluh.nekoffee.database.TemporaryChannelRecord;
import com.ladyluh.nekoffee.database.UserChannelPreference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class TemporaryChannelListener implements EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(TemporaryChannelListener.class);
    private final NekoffeeClient client;
    private final DatabaseManager dbManager;
    private final VoiceStateCacheManager voiceStateCacheManager;

    private final ConcurrentHashMap<String, CompletableFuture<Channel>> userCreationAttempts = new ConcurrentHashMap<>();

    public TemporaryChannelListener(ConfigManager config, NekoffeeClient client, DatabaseManager dbManager, VoiceStateCacheManager voiceStateCacheManager) {
        this.client = client;
        this.dbManager = dbManager;
        this.voiceStateCacheManager = voiceStateCacheManager;
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof GuildCreateEvent gcEvent) {
            
            voiceStateCacheManager.onGuildCreate(gcEvent);
        } else if (event instanceof VoiceStateUpdateEvent vsEvent) {
            handleVoiceStateUpdate(vsEvent);
        }
    }

    private void handleVoiceStateUpdate(VoiceStateUpdateEvent event) {
        String guildId = event.getGuildId();
        if (guildId == null) return;

        String userId = event.getUserId();
        String newChannelId = event.getChannelId();

        String oldChannelId = voiceStateCacheManager.getUserVoiceChannelId(guildId, userId);

        voiceStateCacheManager.onVoiceStateUpdate(event);

        if (Objects.equals(oldChannelId, newChannelId)) {
            return;
        }

        LOGGER.debug("Processing voice channel change for user {}: Old='{}', New='{}'", userId, oldChannelId, newChannelId);

        if (oldChannelId != null) {
            checkChannelOnUserLeave(guildId, oldChannelId, userId);
        }

        if (newChannelId != null) {
            dbManager.getGuildConfig(guildId).thenAccept(configOpt -> {
                String hubId = configOpt.map(cfg -> cfg.tempHubChannelId).orElse(null);
                if (hubId != null && hubId.equals(newChannelId)) {
                    handleHubJoin(event, guildId, userId, configOpt.orElse(new GuildConfig(guildId)));
                }
            });
        }
    }

    private void handleHubJoin(VoiceStateUpdateEvent event, String guildId, String userId, GuildConfig guildConfig) {
        dbManager.getTemporaryChannelByOwner(guildId, userId).thenAccept(existingChannelOpt -> {
            if (existingChannelOpt.isPresent()) {
                LOGGER.info("User {} joined the Hub but already owns channel {}. Moving them.", userId, existingChannelOpt.get().channelId);
                client.modifyGuildMemberVoiceChannel(guildId, userId, existingChannelOpt.get().channelId);
                return;
            }

            
            userCreationAttempts.computeIfAbsent(userId, k -> {
                LOGGER.info("User {} is in the Hub. Initiating temporary channel creation.", userId);
                return createTemporaryChannelForUser(event, guildId, userId, guildConfig)
                        .whenComplete((channel, ex) -> {
                            userCreationAttempts.remove(userId); 
                            if (ex != null) {
                                LOGGER.error("Failed to create/move to temp channel for user {}.", userId, ex);
                                if (channel != null && channel.getId() != null && !channel.getId().isEmpty()) {
                                    LOGGER.warn("Deleting orphaned channel {} due to creation error.", channel.getId());
                                    deleteTemporaryChannel(channel.getId());
                                }
                            } else {
                                LOGGER.info("Temporary channel for user {} created successfully.", userId);
                            }
                        });
            });
        });
    }

    private CompletableFuture<Channel> createTemporaryChannelForUser(VoiceStateUpdateEvent event, String guildId, String userId, GuildConfig guildConfig) {
        return event.retrieveMember().thenComposeAsync(member -> {
            if (member == null || member.getUser() == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("Could not retrieve member " + userId));
            }

            return dbManager.getUserChannelPreference(guildId, userId)
                    .thenCompose(prefsOpt -> {
                        UserChannelPreference prefs = prefsOpt.orElse(new UserChannelPreference(guildId, userId));

                        
                        Integer finalUserLimit = prefs.preferredUserLimit != null ? prefs.preferredUserLimit : guildConfig.defaultTempChannelUserLimit;
                        String finalNameTemplate = prefs.preferredName != null && !prefs.preferredName.isEmpty() ? prefs.preferredName : guildConfig.tempChannelNamePrefix;
                        Integer finalDefaultLocked = prefs.defaultLocked != null ? prefs.defaultLocked : guildConfig.defaultTempChannelLock;

                        String channelName = (finalNameTemplate != null ? finalNameTemplate : "Sala de %username%").replace("%username%", member.getEffectiveName());
                        if (channelName.length() > 100) channelName = channelName.substring(0, 100);

                        LOGGER.info("Preparing to create channel '{}' for {}", channelName, member.getEffectiveName());

                        CreateGuildChannelPayload payload = new CreateGuildChannelPayload(channelName, ChannelType.GUILD_VOICE);
                        if (guildConfig.tempChannelCategoryId != null && !guildConfig.tempChannelCategoryId.isEmpty()) {
                            payload.setParentId(guildConfig.tempChannelCategoryId);
                        }
                        if (finalUserLimit != null) {
                            payload.setUserLimit(finalUserLimit == 0 ? null : finalUserLimit);
                        }

                        
                        return client.createGuildChannel(guildId, payload)
                                .thenCompose(createdChannel -> dbManager.addTemporaryChannel(createdChannel.getId(), guildId, userId)
                                        .thenCompose(v -> applyChannelPermissions(createdChannel, userId, finalDefaultLocked))
                                        .thenCompose(v -> client.modifyGuildMemberVoiceChannel(guildId, userId, createdChannel.getId()))
                                        .thenApply(v -> createdChannel));
                    });
        });
    }

    private CompletableFuture<Void> applyChannelPermissions(Channel channel, String ownerId, Integer defaultLock) {
        boolean isLocked = defaultLock != null && defaultLock == 1;

        EnumSet<Permission> allowEveryone = isLocked ? EnumSet.noneOf(Permission.class) : EnumSet.of(Permission.CONNECT, Permission.SPEAK, Permission.VIEW_CHANNEL);
        EnumSet<Permission> denyEveryone = isLocked ? EnumSet.of(Permission.CONNECT) : EnumSet.noneOf(Permission.class);

        CompletableFuture<Void> everyonePerms = client.editChannelPermissions(
                channel.getId(), channel.getGuildId(), TargetType.ROLE, allowEveryone, denyEveryone
        );

        CompletableFuture<Void> ownerPerms = client.editChannelPermissions(
                channel.getId(), ownerId, TargetType.MEMBER,
                EnumSet.of(Permission.MANAGE_CHANNELS, Permission.CONNECT, Permission.SPEAK, Permission.VIEW_CHANNEL),
                EnumSet.noneOf(Permission.class)
        );

        return CompletableFuture.allOf(everyonePerms, ownerPerms);
    }

    private void checkChannelOnUserLeave(String guildId, String channelId, String userIdWhoLeft) {
        dbManager.getTemporaryChannel(channelId).thenAccept(tempChannelOpt -> {
            if (tempChannelOpt.isEmpty()) {
                return; 
            }

            
            if (voiceStateCacheManager.isVoiceChannelEmpty(guildId, channelId)) {
                LOGGER.info("Temp channel {} is now empty. Deleting.", channelId);
                deleteTemporaryChannel(channelId);
                return;
            }

            
            if (tempChannelOpt.get().ownerUserId.equals(userIdWhoLeft)) {
                handleOwnerLeave(guildId, tempChannelOpt.get());
            }
        });
    }

    private void handleOwnerLeave(String guildId, TemporaryChannelRecord channelRecord) {
        dbManager.getUserChannelPreference(guildId, channelRecord.ownerUserId).thenAccept(ownerPrefsOpt -> {
            boolean autoSwitchEnabled = ownerPrefsOpt
                    .map(prefs -> prefs.autoOwnerSwitching != null && prefs.autoOwnerSwitching == 1)
                    .orElse(false);

            if (autoSwitchEnabled) {
                LOGGER.info("Auto-owner switch is ON for previous owner. Transferring ownership of {}.", channelRecord.channelId);
                transferChannelOwnership(guildId, channelRecord.channelId);
            } else {
                LOGGER.info("Auto-owner switch is OFF. The channel {} will be deleted as owner has left.", channelRecord.channelId);
                deleteTemporaryChannel(channelRecord.channelId);
            }
        });
    }

    private void transferChannelOwnership(String guildId, String channelId) {
        Set<String> membersInChannel = voiceStateCacheManager.getMembersInVoiceChannel(guildId, channelId);
        if (membersInChannel.isEmpty()) {
            LOGGER.warn("Tried to transfer ownership of {}, but it became empty. Deleting instead.", channelId);
            deleteTemporaryChannel(channelId);
            return;
        }

        String newOwnerId = membersInChannel.iterator().next();
        LOGGER.info("Transferring ownership of channel {} to new owner {}", channelId, newOwnerId);

        dbManager.updateTemporaryChannelOwner(channelId, newOwnerId)
                .thenCompose(v -> client.getGuildMember(guildId, newOwnerId))
                .thenCompose(newOwnerMember -> {
                    if (newOwnerMember == null || newOwnerMember.getUser() == null) {
                        throw new IllegalStateException("Could not retrieve new owner member object " + newOwnerId);
                    }
                    return dbManager.getUserChannelPreference(guildId, newOwnerId)
                            .thenCompose(prefsOpt -> {
                                UserChannelPreference prefs = prefsOpt.orElse(new UserChannelPreference(guildId, newOwnerId));
                                return dbManager.getGuildConfig(guildId)
                                        .thenCompose(gcfgOpt -> {
                                            GuildConfig guildConfig = gcfgOpt.orElse(new GuildConfig(guildId));

                                            
                                            ChannelModifyPayload payload = new ChannelModifyPayload();
                                            String nameTemplate = prefs.preferredName != null && !prefs.preferredName.isEmpty() ? prefs.preferredName : guildConfig.tempChannelNamePrefix;
                                            String finalName = (nameTemplate != null ? nameTemplate : "Sala de %username%").replace("%username%", newOwnerMember.getEffectiveName());
                                            if (finalName.length() > 100) finalName = finalName.substring(0, 100);
                                            payload.setName(finalName);

                                            Integer limit = prefs.preferredUserLimit != null ? prefs.preferredUserLimit : guildConfig.defaultTempChannelUserLimit;
                                            if (limit != null) payload.setUserLimit(limit == 0 ? null : limit);

                                            return client.modifyChannel(channelId, payload)
                                                    .thenCompose(v2 -> client.getChannelById(channelId))
                                                    .thenCompose(channel -> applyChannelPermissions(channel, newOwnerId, prefs.defaultLocked)); 
                                        });
                            });
                })
                .thenRun(() -> LOGGER.info("Channel {} successfully updated for new owner {}.", channelId, newOwnerId))
                .exceptionally(ex -> {
                    LOGGER.error("Failed to complete ownership transfer for channel {}. Deleting it to prevent issues.", channelId, ex);
                    deleteTemporaryChannel(channelId);
                    return null;
                });
    }

    public void deleteTemporaryChannel(String channelId) {
        if (channelId == null) {
            LOGGER.warn("deleteTemporaryChannel: Attempt to delete a channel with a null ID.");
            return;
        }

        LOGGER.info("Initiating deletion of Discord channel: {}", channelId);
        client.deleteChannel(channelId)
                .thenRun(() -> {
                    LOGGER.info("Discord channel {} deleted successfully. Removing from DB.", channelId);
                    dbManager.removeTemporaryChannel(channelId);
                })
                .exceptionally(ex -> {
                    if (ex.getCause() instanceof com.ladyluh.nekoffee.api.exception.NekoffeeException nekoffeeEx &&
                            nekoffeeEx.getMessage() != null && nekoffeeEx.getMessage().contains("status 404")) {
                        LOGGER.warn("Temp channel {} was already deleted on Discord (404). Removing from DB.", channelId);
                        dbManager.removeTemporaryChannel(channelId);
                    } else {
                        LOGGER.error("Failed to delete temp channel {}:", channelId, ex);
                    }
                    return null;
                });
    }
}