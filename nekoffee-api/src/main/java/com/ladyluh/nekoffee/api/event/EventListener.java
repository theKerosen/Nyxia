package com.ladyluh.nekoffee.api.event;

@FunctionalInterface
public interface EventListener {
    /**
     * Chamado quando um evento do Discord é recebido.
     * @param event O evento que ocorreu.
     */
    void onEvent(Event event);
}