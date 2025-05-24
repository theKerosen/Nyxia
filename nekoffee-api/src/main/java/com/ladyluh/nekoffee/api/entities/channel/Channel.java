package com.ladyluh.nekoffee.api.entities.channel;

import com.ladyluh.nekoffee.api.entities.DiscordEntity;

public interface Channel extends DiscordEntity {

    /**
     * @return O nome do canal.
     */
    String getName();

    /**
     * @return O tipo deste canal.
     * @see com.nekoffee.api.entities.channel.ChannelType
     */
    ChannelType getType();

}