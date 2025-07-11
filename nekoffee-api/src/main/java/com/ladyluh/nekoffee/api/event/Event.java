package com.ladyluh.nekoffee.api.event;

import com.ladyluh.nekoffee.api.NekoffeeClient;

public interface Event {
    /**
     * @return A instância do NekoffeeClient que disparou este evento.
     */
    NekoffeeClient getNekoffeeClient();
}