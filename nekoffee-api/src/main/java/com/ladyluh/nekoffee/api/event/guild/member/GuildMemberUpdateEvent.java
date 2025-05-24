package com.ladyluh.nekoffee.api.event.guild.member;


import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.Guild;
import com.ladyluh.nekoffee.api.entities.Member;
import com.ladyluh.nekoffee.api.event.AbstractEvent;

import java.util.concurrent.CompletableFuture;

public class GuildMemberUpdateEvent extends AbstractEvent {
    private final Member member;


    public GuildMemberUpdateEvent(NekoffeeClient nekoffeeClient, Member member) {
        super(nekoffeeClient);
        this.member = member;
    }

    /**
     * @return O membro com seu estado atualizado.
     */
    public Member getMember() {
        return member;
    }

    public String getGuildId() {
        return member.getGuildId();
    }

    public CompletableFuture<Guild> getGuild() {
        return member.retrieveGuild(getNekoffeeClient());
    }
}