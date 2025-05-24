package com.ladyluh.nekoffee.model.gateway;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.Message;
import com.ladyluh.nekoffee.api.entities.User;
import com.ladyluh.nekoffee.api.event.AbstractEvent;

public class MessageCreateEvent extends AbstractEvent {
    private final Message message;

    public MessageCreateEvent(NekoffeeClient nekoffeeClient, Message message) {
        super(nekoffeeClient);
        this.message = message;
    }

    public Message getMessage() {
        return message;
    }

    public User getAuthor() {
        return message.getAuthor();
    }

    public String getContentRaw() {
        return message.getContentRaw();
    }

    public String getChannelId() {
        return message.getChannelId();
    }


}