package com.ladyluh.nekoffee.model.channel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.channel.Channel;
import com.ladyluh.nekoffee.api.entities.channel.ChannelType;
import com.ladyluh.nekoffee.model.AbstractDiscordEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public abstract class AbstractChannelImpl extends AbstractDiscordEntity implements Channel {

    @JsonProperty("name")
    protected String name;

    @JsonProperty("type")
    protected int typeId;

    @JsonProperty("guild_id") 
    protected String guildId;

    @JsonIgnore 
    protected NekoffeeClient nekoffeeClient; 

    @Override
    public String getName() { return name; }

    @Override
    public ChannelType getType() { return ChannelType.fromId(typeId); }

    public String getGuildId() { return guildId; } 

    
    @Override
    public void setNekoffeeClient(NekoffeeClient client) {
        this.nekoffeeClient = Objects.requireNonNull(client, "NekoffeeClient cannot be null for ChannelImpl.");
    }
}