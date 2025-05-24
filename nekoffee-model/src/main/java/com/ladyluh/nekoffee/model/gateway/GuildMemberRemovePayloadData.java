package com.ladyluh.nekoffee.model.gateway;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ladyluh.nekoffee.model.user.UserImpl;

public class GuildMemberRemovePayloadData {
    @JsonProperty("guild_id")
    private String guildId;

    @JsonProperty("user")
    private UserImpl user;


    public String getGuildId() {
        return guildId;
    }

    public UserImpl getUser() {
        return user;
    }
}