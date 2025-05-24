package com.ladyluh.nekoffee.api.entities.channel;


public interface TextChannel extends Channel {

    /**
     * @return O tópico do canal de texto. Pode ser null ou vazio.
     */
    String getTopic();

    /**
     * @return {@code true} se este canal estiver marcado como NSFW (Not Safe For Work).
     */
    boolean isNsfw();


}