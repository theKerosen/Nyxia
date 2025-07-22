package com.ladyluh.nekoffee.api.event.guild.member;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.Guild;
import com.ladyluh.nekoffee.api.entities.User;
import com.ladyluh.nekoffee.api.event.AbstractEvent;

import java.util.concurrent.CompletableFuture;

public class GuildMemberRemoveEvent extends AbstractEvent {
    private final String guildId;
    private final User user;

    public GuildMemberRemoveEvent(NekoffeeClient nekoffeeClient, String guildId, User user) {
        super(nekoffeeClient);
        this.guildId = guildId;
        this.user = user;
    }

    public String getGuildId() {
        return guildId;
    }

    public User getUser() {
        return user;
    }

    public CompletableFuture<Guild> getGuild() {
        return nekoffeeClient.getGuildById(guildId);
    }
}