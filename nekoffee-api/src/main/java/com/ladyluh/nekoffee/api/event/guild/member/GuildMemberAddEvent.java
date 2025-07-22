package com.ladyluh.nekoffee.api.event.guild.member;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.Guild;
import com.ladyluh.nekoffee.api.entities.Member;
import com.ladyluh.nekoffee.api.event.AbstractEvent;

import java.util.concurrent.CompletableFuture;

public class GuildMemberAddEvent extends AbstractEvent {
    private final Member member;

    public GuildMemberAddEvent(NekoffeeClient nekoffeeClient, Member member /*, String guildId */) {
        super(nekoffeeClient);
        this.member = member;

    }

    /**
     * @return O membro que acabou de entrar no servidor.
     */
    public Member getMember() {
        return member;
    }

    /**
     * @return O ID do servidor ao qual o membro entrou.
     */
    public String getGuildId() {
        return member.getGuildId();
    }

    /**
     * Busca o objeto Guild ao qual o membro entrou.
     *
     * @return Um CompletableFuture contendo a Guild.
     */
    public CompletableFuture<Guild> getGuild() {

        return member.retrieveGuild(getNekoffeeClient());
    }
}