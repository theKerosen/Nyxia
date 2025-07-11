package com.ladyluh.nekoffee.api.entities;


public interface Message extends DiscordEntity {
    String getGuildId();

    /**
     * @return O conteúdo textual desta mensagem.
     */
    String getContentRaw();

    /**
     * @return O autor desta mensagem.
     */
    User getAuthor();

    /**
     * @return O ID do canal onde esta mensagem foi enviada.
     */
    String getChannelId();


}