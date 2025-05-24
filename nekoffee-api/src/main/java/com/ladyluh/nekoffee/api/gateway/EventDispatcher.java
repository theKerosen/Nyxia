package com.ladyluh.nekoffee.api.gateway;

import com.ladyluh.nekoffee.api.event.Event;

@FunctionalInterface
public interface EventDispatcher {
    void dispatch(Event event);
}