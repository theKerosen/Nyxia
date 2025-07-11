package com.ladyluh.nekoffee.model.channel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ladyluh.nekoffee.api.entities.channel.CategoryChannel;
import com.ladyluh.nekoffee.api.entities.channel.ChannelType;

public class CategoryChannelImpl extends AbstractChannelImpl implements CategoryChannel {
    @JsonProperty("position")
    private Integer position; 

    public CategoryChannelImpl() {}

    @Override
    public ChannelType getType() {
        return ChannelType.fromId(typeId); 
    }

    @Override
    public Integer getPosition() { return position; }

    @Override
    public String toString() {
        return "CategoryChannelImpl{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type=" + getType() +
                ", position=" + position +
                '}';
    }
}