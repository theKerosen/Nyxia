package com.ladyluh.nekoffee.api.entities;

import com.ladyluh.nekoffee.api.NekoffeeClient;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface Member extends DiscordEntity {

    /**
     * @return O objeto User associado a este membro. Contém informações globais do usuário.
     */
    User getUser();

    /**
     * @return O apelido (nickname) deste membro no servidor. Retorna null se não houver apelido.
     */
    String getNickname();

    /**
     * @return O nome de exibição efetivo do membro. É o apelido se existir, caso contrário, o nome de usuário global.
     */
    String getEffectiveName();

    /**
     * @return A lista de IDs dos cargos que este membro possui.
     */
    List<String> getRoleIds();

    /**
     * Busca a lista de objetos Role que este membro possui.
     * Isso pode envolver buscar todos os cargos da guild e filtrar, ou pode ser populado diretamente
     * se a API fornecer os objetos Role no payload do Member (o que geralmente não acontece).
     *
     * @param client A instância do NekoffeeClient para fazer chamadas à API, se necessário.
     * @return Um CompletableFuture contendo a lista de Roles do membro.
     */
    CompletableFuture<List<Role>> getRoles(NekoffeeClient client);

    /**
     * @return A data e hora em que este membro entrou no servidor.
     */
    OffsetDateTime getTimeJoined();

    /**
     * @return A data e hora em que este membro começou a impulsionar o servidor (Boosting). Retorna null se não estiver impulsionando.
     */
    OffsetDateTime getTimeBoosted();

    /**
     * @return {@code true} se este membro estiver surdo (deafened) nos canais de voz do servidor.
     */
    boolean isDeafened();

    /**
     * @return {@code true} se este membro estiver mudo (muted) nos canais de voz do servidor.
     */
    boolean isMuted();

    /**
     * @return O ID da guild à qual este membro pertence.
     */
    String getGuildId();

    /**
     * Busca a Guild à qual este membro pertence.
     *
     * @param client A instância do NekoffeeClient.
     * @return Um CompletableFuture contendo a Guild.
     */
    CompletableFuture<Guild> retrieveGuild(NekoffeeClient client);


}