package com.ladyluh.nekoffee.model.channel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ladyluh.nekoffee.api.entities.channel.Channel;
import com.ladyluh.nekoffee.api.entities.channel.ChannelType;
import com.ladyluh.nekoffee.model.AbstractDiscordEntity;

public abstract class AbstractChannelImpl extends AbstractDiscordEntity implements Channel {

    @JsonProperty("name")
    protected String name;

    @JsonProperty("type")
    protected int typeId;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ChannelType getType() {
        return ChannelType.fromId(typeId);
    }
}