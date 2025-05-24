package com.ladyluh.nekoffee.api.event;

import com.ladyluh.nekoffee.api.event.Event;

@FunctionalInterface // Bom para lambdas
public interface EventListener {
    /**
     * Chamado quando um evento do Discord é recebido.
     * @param event O evento que ocorreu.
     */
    void onEvent(Event event);
}