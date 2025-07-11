package com.ladyluh.nekoffee.api.entities.channel;

public interface VoiceChannel extends Channel {
    /**
     *
     * @return O limite máximo de usuários que podem entrar no canal de voz (0 para ilimitado).
     */
    Integer getUserLimit();

    /**
     * @return A taxa de bits (bitrate) em kbps.
     */
    Integer getBitrate();

    /**
     * @return A região RTC do canal de voz, ou null se for automática.
     */
    String getRtcRegion();

    /**
     * @return O status do canal de voz (para canais de Stage).
     */
    String getStatus();

    String getParentId();

    
    
}