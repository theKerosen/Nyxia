package com.ladyluh.nekoffee.api.event;

import com.ladyluh.nekoffee.api.NekoffeeClient;

public abstract class AbstractEvent implements Event {
    protected final NekoffeeClient nekoffeeClient;

    public AbstractEvent(NekoffeeClient nekoffeeClient) {
        this.nekoffeeClient = nekoffeeClient;
    }

    @Override
    public NekoffeeClient getNekoffeeClient() {
        return nekoffeeClient;
    }
}