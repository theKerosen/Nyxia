package com.ladyluh.nekoffee.api.event.guild;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.Guild;
import com.ladyluh.nekoffee.api.event.AbstractEvent;

public class GuildCreateEvent extends AbstractEvent {
    private final Guild guild;

    public GuildCreateEvent(NekoffeeClient nekoffeeClient, Guild guild) {
        super(nekoffeeClient);
        this.guild = guild;
    }

    /**
     * @return O objeto Guild que acaba de ser criado ou se tornou disponível.
     *         Este objeto Guild deve conter o estado completo da guild na inicialização,
     *         incluindo canais, cargos e voice_states (se os intents permitirem).
     */
    public Guild getGuild() {
        return guild;
    }
}