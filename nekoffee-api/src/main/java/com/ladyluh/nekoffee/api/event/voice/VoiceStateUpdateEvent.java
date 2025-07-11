package com.ladyluh.nekoffee.api.event.voice;


import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.Guild;
import com.ladyluh.nekoffee.api.entities.Member;
import com.ladyluh.nekoffee.api.entities.User;
import com.ladyluh.nekoffee.api.entities.channel.Channel;
import com.ladyluh.nekoffee.api.event.AbstractEvent;

import java.util.concurrent.CompletableFuture;


public class VoiceStateUpdateEvent extends AbstractEvent {


    private final String guildId;
    private final String channelId;
    private final String userId;
    private final boolean isMuted;
    private final boolean isDeafened;


    public VoiceStateUpdateEvent(NekoffeeClient nekoffeeClient,
                                 String guildId, String channelId, String userId,
                                 boolean isMuted, boolean isDeafened) {
        super(nekoffeeClient);
        this.guildId = guildId;
        this.channelId = channelId;
        this.userId = userId;
        this.isMuted = isMuted;
        this.isDeafened = isDeafened;

    }

    public String getGuildId() {
        return guildId;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getUserId() {
        return userId;
    }

    public boolean isMuted() {
        return isMuted;
    }

    public boolean isDeafened() {
        return isDeafened;
    }


    public CompletableFuture<User> retrieveUser() {
        return nekoffeeClient.getUserById(userId);
    }

    public CompletableFuture<Member> retrieveMember() {
        if (guildId == null) return CompletableFuture.failedFuture(new IllegalStateException("Not in a guild."));
        return nekoffeeClient.getGuildMember(guildId, userId);
    }

    public CompletableFuture<Guild> retrieveGuild() {
        if (guildId == null) return CompletableFuture.failedFuture(new IllegalStateException("Not in a guild."));
        return nekoffeeClient.getGuildById(guildId);
    }

    public CompletableFuture<Channel> retrieveChannel() {
        if (channelId == null) return CompletableFuture.completedFuture(null);
        return nekoffeeClient.getChannelById(channelId);
    }

    public boolean hasJoinedChannel() {
        return channelId != null;
    }

    public boolean hasLeftChannel() {


        return channelId == null;
    }
}