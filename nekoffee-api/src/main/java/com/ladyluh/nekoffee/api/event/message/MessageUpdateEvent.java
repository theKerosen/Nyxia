package com.ladyluh.nekoffee.api.event.message;


import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.Message;
import com.ladyluh.nekoffee.api.event.AbstractEvent;

public class MessageUpdateEvent extends AbstractEvent {
    private final Message message;


    public MessageUpdateEvent(NekoffeeClient nekoffeeClient, Message message) {
        super(nekoffeeClient);
        this.message = message;
    }

    /**
     * @return A mensagem atualizada. Note que alguns campos podem ser nulos se não
     * foram incluídos no payload do evento (ex: se apenas o embed foi atualizado).
     */
    public Message getMessage() {
        return message;
    }


    public String getChannelId() {
        return message.getChannelId();
    }
}