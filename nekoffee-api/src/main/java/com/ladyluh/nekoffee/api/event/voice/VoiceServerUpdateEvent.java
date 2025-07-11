package com.ladyluh.nekoffee.api.event.voice;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.event.AbstractEvent;

public class VoiceServerUpdateEvent extends AbstractEvent {
    private final String guildId;
    private final String token;
    private final String endpoint;

    public VoiceServerUpdateEvent(NekoffeeClient nekoffeeClient, String guildId, String token, String endpoint) {
        super(nekoffeeClient);
        this.guildId = guildId;
        this.token = token;
        this.endpoint = endpoint;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getToken() {
        return token;
    }

    public String getEndpoint() {
        return endpoint;
    }
}