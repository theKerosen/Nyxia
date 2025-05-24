package com.ladyluh.nekoffee.api.entities;

import com.ladyluh.nekoffee.api.NekoffeeClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface Guild extends DiscordEntity {

    /**
     * @return O nome deste servidor.
     */
    String getName();

    /**
     * @return O ID do ícone do servidor. Pode ser null.
     */
    String getIconId();

    /**
     * @return A URL do ícone do servidor. Retorna null se não houver ícone.
     */
    String getIconUrl();

    /**
     * @return O ID do usuário dono deste servidor.
     */
    String getOwnerId();

    /**
     * Busca o objeto User do dono deste servidor.
     * @param client A instância do NekoffeeClient para fazer a chamada à API.
     * @return Um CompletableFuture contendo o User dono.
     */
    CompletableFuture<User> retrieveOwner(NekoffeeClient client);


    /**
     * @return Uma lista de todos os cargos neste servidor.
     *         Pode retornar uma lista vazia ou necessitar de uma chamada à API para popular.
     *         Para uma busca garantida, use retrieveRoles(NekoffeeClient).
     */
    List<Role> getRoles(); // Este pode vir populado diretamente do GET /guilds/{id}

    /**
     * Busca (ou atualiza) a lista de cargos deste servidor via API.
     * @param client A instância do NekoffeeClient para fazer a chamada à API.
     * @return Um CompletableFuture contendo a lista de Roles.
     */
    CompletableFuture<List<Role>> retrieveRoles(NekoffeeClient client);

    List<VoiceState> getVoiceStates(); // Este método agora deve resolver

}