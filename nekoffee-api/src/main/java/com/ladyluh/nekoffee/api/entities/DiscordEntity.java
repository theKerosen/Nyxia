package com.ladyluh.nekoffee.api.entities;

public interface DiscordEntity {
    /**
     * @return O ID único desta entidade no Discord (Snowflake).
     */
    String getId();

    /**
     * @return O ID único desta entidade como um long.
     */
    long getIdLong();
}