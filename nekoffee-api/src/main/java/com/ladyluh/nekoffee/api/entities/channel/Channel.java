package com.ladyluh.nekoffee.api.entities.channel;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.DiscordEntity;

public interface Channel extends DiscordEntity {

    /**
     * @return O nome do canal.
     */
    String getName();

    void setNekoffeeClient(NekoffeeClient client);

    /**
     * @return O tipo deste canal.
     * @see com.ladyluh.nekoffee.api.entities.channel.ChannelType
     */
    ChannelType getType();

    /**
     * @return The ID of the guild this channel belongs to. Will be null for DMs.
     */
    String getGuildId();

}