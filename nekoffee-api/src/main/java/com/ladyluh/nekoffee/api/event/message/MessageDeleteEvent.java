package com.ladyluh.nekoffee.api.event.message;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.event.AbstractEvent;

public class MessageDeleteEvent extends AbstractEvent {
    private final String messageId;
    private final String channelId;
    private final String guildId;

    public MessageDeleteEvent(NekoffeeClient nekoffeeClient, String messageId, String channelId, String guildId) {
        super(nekoffeeClient);
        this.messageId = messageId;
        this.channelId = channelId;
        this.guildId = guildId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getGuildId() {
        return guildId;
    }
}