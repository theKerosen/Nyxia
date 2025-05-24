package com.ladyluh.nekoffee.cache;

import com.ladyluh.nekoffee.api.event.guild.GuildCreateEvent;
import com.ladyluh.nekoffee.api.event.voice.VoiceStateUpdateEvent;
import com.ladyluh.nekoffee.model.gateway.ReadyEvent;
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
     * Lida com o evento READY.
     * O READY em si não tem voice states, mas sinaliza que GUILD_CREATEs virão.
     */
    public void onReady(ReadyEvent event) {
        LOGGER.info("VoiceStateCacheManager: Recebido ReadyEvent. Cache será populado via GuildCreateEvent.");
    }

    /**
     * Lida com o evento GUILD_CREATE para popular o cache com voice states da guild.
     * Este evento é disparado para cada guild que o bot está conectado na inicialização.
     */
    public void onGuildCreate(GuildCreateEvent event) {
        String guildId = event.getGuild().getId();
        LOGGER.info("VoiceStateCacheManager: Recebido GuildCreateEvent para Guild {}. Populando cache de estados de voz...", guildId);

        ConcurrentHashMap<String, ConcurrentSkipListSet<String>> guildChannelsMap =
                guildVoiceChannelMembers.computeIfAbsent(guildId, k -> new ConcurrentHashMap<>());


        event.getGuild().getVoiceStates().forEach(voiceState -> {
            String channelId = voiceState.getChannelId();
            String userId = voiceState.getUserId();
            if (channelId != null && userId != null) {
                guildChannelsMap.computeIfAbsent(channelId, k -> new ConcurrentSkipListSet<>()).add(userId);
                LOGGER.debug("Cache: Usuário {} adicionado ao canal {} na inicialização.", userId, channelId);
            }
        });
        LOGGER.info("VoiceStateCacheManager: Cache populado para Guild {}. Total de canais de voz rastreados: {}", guildId, guildChannelsMap.size());
    }

    /**
     * Lida com o evento VOICE_STATE_UPDATE para atualizar o cache.
     * Este evento é a fonte principal de atualizações em tempo real.
     */
    public void onVoiceStateUpdate(VoiceStateUpdateEvent event) {
        String guildId = event.getGuildId();
        String userId = event.getUserId();
        String newChannelId = event.getChannelId();

        if (guildId == null) return;

        ConcurrentHashMap<String, ConcurrentSkipListSet<String>> guildChannelsMap =
                guildVoiceChannelMembers.computeIfAbsent(guildId, k -> new ConcurrentHashMap<>());


        guildChannelsMap.forEach((channelIdInMap, usersInChannel) -> {
            if (usersInChannel.remove(userId)) {
                LOGGER.debug("Cache: Usuário {} removido do canal {} (estado anterior).", userId, channelIdInMap);

                if (usersInChannel.isEmpty()) {
                    guildChannelsMap.remove(channelIdInMap);
                    LOGGER.debug("Cache: Canal {} vazio e removido do mapa de canais da guild.", channelIdInMap);
                }
            }
        });


        if (newChannelId != null) {
            guildChannelsMap.computeIfAbsent(newChannelId, k -> new ConcurrentSkipListSet<>()).add(userId);
            LOGGER.debug("Cache: Usuário {} adicionado ao canal {} (novo estado).", userId, newChannelId);
        }

        LOGGER.debug("VoiceStateCacheManager: Voice state atualizado para {} em {}. Novo canal: {}", userId, guildId, newChannelId);
    }

    /**
     * Verifica se um canal de voz está vazio de membros no cache.
     *
     * @param guildId   O ID do servidor.
     * @param channelId O ID do canal de voz.
     * @return true se o canal estiver vazio ou não for encontrado no cache, false caso contrário.
     */
    public boolean isVoiceChannelEmpty(String guildId, String channelId) {
        Set<String> membersInChannel = Optional.ofNullable(guildVoiceChannelMembers.get(guildId))
                .map(channels -> channels.get(channelId))
                .orElse(new ConcurrentSkipListSet<String>());

        LOGGER.debug("VoiceStateCacheManager: Canal {} na guild {} tem {} membros no cache. Vazio? {}",
                channelId, guildId, membersInChannel.size(), membersInChannel.isEmpty());
        return membersInChannel.isEmpty();
    }

    /**
     * Obtém o ID do canal de voz em que um usuário está, a partir do cache.
     *
     * @param guildId O ID do servidor.
     * @param userId  O ID do usuário.
     * @return O ID do canal de voz, ou null se o usuário não estiver em um canal de voz na guild.
     */
    public String getUserVoiceChannelId(String guildId, String userId) {
        return Optional.ofNullable(guildVoiceChannelMembers.get(guildId))
                .map(channels -> {
                    for (Map.Entry<String, ConcurrentSkipListSet<String>> entry : channels.entrySet()) {
                        if (entry.getValue().contains(userId)) {
                            return entry.getKey();
                        }
                    }
                    return null;
                })
                .orElse(null);
    }


    public void clearGuildCache(String guildId) {
        guildVoiceChannelMembers.remove(guildId);
        LOGGER.info("VoiceStateCacheManager: Cache da Guild {} limpo.", guildId);
    }


    public void clearAllCache() {
        guildVoiceChannelMembers.clear();
        LOGGER.info("VoiceStateCacheManager: Todo o cache foi limpo.");
    }
}