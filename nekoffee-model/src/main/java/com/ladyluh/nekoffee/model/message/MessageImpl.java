package com.ladyluh.nekoffee.model.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ladyluh.nekoffee.api.entities.Message;
import com.ladyluh.nekoffee.api.entities.User;
import com.ladyluh.nekoffee.model.AbstractDiscordEntity;
import com.ladyluh.nekoffee.model.user.UserImpl;

public class MessageImpl extends AbstractDiscordEntity implements Message {

    @JsonProperty("content")
    private String contentRaw;

    @JsonProperty("author")
    private UserImpl author;

    @JsonProperty("channel_id")
    private String channelId;

    @JsonProperty("guild_id")
    private String guildId;

    public MessageImpl() {
    }

    @Override
    public String getGuildId() {
        return guildId;
    }

    @Override
    public String getContentRaw() {
        return contentRaw;
    }

    @Override
    public User getAuthor() {
        return author;
    }

    @Override
    public String getChannelId() {
        return channelId;
    }

    @Override
    public String toString() {
        return "MessageImpl{" +
                "id='" + id + '\'' +
                ", contentRaw='" + contentRaw + '\'' +
                ", author=" + (author != null ? author.getAsTag() : "null") +
                ", channelId='" + channelId + '\'' +
                '}';
    }
}