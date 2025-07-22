package com.ladyluh.nekoffee.cache;

import com.ladyluh.nekoffee.api.event.guild.GuildCreateEvent;
import com.ladyluh.nekoffee.api.event.voice.VoiceStateUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class VoiceStateCacheManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(VoiceStateCacheManager.class);

    public final ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentSkipListSet<String>>> guildVoiceChannelMembers;

    public VoiceStateCacheManager() {
        this.guildVoiceChannelMembers = new ConcurrentHashMap<>();
        LOGGER.info("VoiceStateCacheManager: Cache de estados de voz inicializado.");
    }

    /**
     * Populates the cache with voice states from a GUILD_CREATE event.
     * This is called on bot startup for each guild.
     */
    public void onGuildCreate(GuildCreateEvent event) {
        String guildId = event.getGuild().getId();
        LOGGER.info("VoiceStateCacheManager: Recebido GuildCreateEvent para Guild {}. Populando cache...", guildId);

        ConcurrentHashMap<String, ConcurrentSkipListSet<String>> guildChannelsMap =
                guildVoiceChannelMembers.computeIfAbsent(guildId, k -> new ConcurrentHashMap<>());

        guildChannelsMap.clear();

        event.getGuild().getVoiceStates().forEach(voiceState -> {
            String channelId = voiceState.getChannelId();
            String userId = voiceState.getUserId();
            if (channelId != null && userId != null) {
                guildChannelsMap.computeIfAbsent(channelId, k -> new ConcurrentSkipListSet<>()).add(userId);
                LOGGER.debug("Cache (init): Usuário {} adicionado ao canal {}.", userId, channelId);
            }
        });
        LOGGER.info("VoiceStateCacheManager: Cache populado para Guild {}. Total de canais de voz rastreados: {}", guildId, guildChannelsMap.size());
    }

    /**
     * Updates the cache based on a VOICE_STATE_UPDATE event.
     */
    public void onVoiceStateUpdate(VoiceStateUpdateEvent event) {
        String guildId = event.getGuildId();
        String userId = event.getUserId();
        String newChannelId = event.getChannelId();

        if (guildId == null || userId == null) return;

        ConcurrentHashMap<String, ConcurrentSkipListSet<String>> guildChannelsMap =
                guildVoiceChannelMembers.computeIfAbsent(guildId, k -> new ConcurrentHashMap<>());

        guildChannelsMap.forEach((channelIdInMap, usersInChannel) -> {
            if (usersInChannel.remove(userId)) {
                LOGGER.debug("Cache (update): Usuário {} removido do canal antigo {}.", userId, channelIdInMap);
                if (usersInChannel.isEmpty()) {
                    guildChannelsMap.remove(channelIdInMap);
                    LOGGER.debug("Cache (update): Canal antigo {} agora está vazio e foi removido do cache.", channelIdInMap);
                }
            }
        });

        if (newChannelId != null) {
            guildChannelsMap.computeIfAbsent(newChannelId, k -> new ConcurrentSkipListSet<>()).add(userId);
            LOGGER.debug("Cache (update): Usuário {} adicionado ao novo canal {}.", userId, newChannelId);
        }
    }

    public Set<String> getMembersInVoiceChannel(String guildId, String channelId) {
        return Optional.ofNullable(guildVoiceChannelMembers.get(guildId))
                .map(channels -> channels.get(channelId))
                .orElse(new ConcurrentSkipListSet<>());
    }

    public boolean isVoiceChannelEmpty(String guildId, String channelId) {
        Set<String> membersInChannel = getMembersInVoiceChannel(guildId, channelId);
        LOGGER.debug("isVoiceChannelEmpty check: Canal {} na guild {} tem {} membros no cache. Vazio? {}",
                channelId, guildId, membersInChannel.size(), membersInChannel.isEmpty());
        return membersInChannel.isEmpty();
    }

    public String getUserVoiceChannelId(String guildId, String userId) {
        return Optional.ofNullable(guildVoiceChannelMembers.get(guildId))
                .flatMap(channels -> {
                    for (Map.Entry<String, ConcurrentSkipListSet<String>> entry : channels.entrySet()) {
                        if (entry.getValue().contains(userId)) {
                            return Optional.of(entry.getKey());
                        }
                    }
                    return Optional.empty();
                })
                .orElse(null);
    }
}