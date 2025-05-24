package com.ladyluh.nekoffee.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ladyluh.nekoffee.api.entities.DiscordEntity;

public abstract class AbstractDiscordEntity implements DiscordEntity {

    @JsonProperty("id")
    protected String id;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public long getIdLong() {
        if (id == null) {
            return 0L;
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {

            System.err.println("Aviso: Falha ao converter ID para long: " + id);
            return 0L;
        }
    }


}