package com.ladyluh.nekoffee.api.entities;

import com.ladyluh.nekoffee.api.NekoffeeClient;

import java.awt.*;
import java.util.concurrent.CompletableFuture;

public interface Role extends DiscordEntity, Comparable<Role> {

    /**
     * @return O nome deste cargo.
     */
    String getName();

    /**
     * @return A cor RGB deste cargo. Retorna null se a cor for padrão (0).
     */
    Color getColor();

    /**
     * @return O valor RGB inteiro da cor.
     */
    int getColorRaw();

    /**
     * @return {@code true} se este cargo é separado dos membros online na lista de membros.
     */
    boolean isHoisted();

    /**
     * @return A posição deste cargo na hierarquia. Cargos com posição mais alta são exibidos acima.
     */
    int getPosition();

    /**
     * @return As permissões (bitwise) deste cargo.
     * Use com a enumeração Permission.
     */
    long getPermissionsRaw();

    /**
     * @return {@code true} se este cargo é gerenciado por uma integração (ex: bot).
     */
    boolean isManaged();

    /**
     * @return {@code true} se este cargo pode ser mencionado.
     */
    boolean isMentionable();

    /**
     * @return A String para mencionar este cargo (ex: "<@&ID_DO_CARGO>").
     */
    String getAsMention();

    /**
     * @return O ID do servidor ao qual este cargo pertence.
     * Pode ser útil se o objeto Role for obtido fora do contexto de um Guild.
     */
    String getGuildId();


    /**
     * Retorna a Guild à qual este cargo pertence.
     *
     * @param client A instância do NekoffeeClient para fazer a chamada à API, se necessário.
     * @return Um CompletableFuture contendo a Guild.
     */
    CompletableFuture<Guild> retrieveGuild(NekoffeeClient client);


}