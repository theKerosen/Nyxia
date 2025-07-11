package com.ladyluh.nekoffee.gateway.client.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.Nullable;


public class VoiceStateUpdatePayload {

    @JsonProperty("guild_id")
    private final String guildId;

    @JsonProperty("channel_id")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private final String channelId;

    @JsonProperty("self_mute")
    private final boolean selfMute;

    @JsonProperty("self_deaf")
    private final boolean selfDeaf;

    public VoiceStateUpdatePayload(String guildId, @Nullable String channelId, boolean selfMute, boolean selfDeaf) {
        this.guildId = guildId;
        this.channelId = channelId;
        this.selfMute = selfMute;
        this.selfDeaf = selfDeaf;
    }
}