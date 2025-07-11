package com.ladyluh.nekoffee.voice;

import com.ladyluh.nekoffee.api.NekoffeeClient;

import java.util.concurrent.CompletableFuture;

/**
 * Represents a connection to a Discord voice channel.
 */
public interface VoiceConnection {
    /**
     * Connects to the voice channel using the provided server information.
     *
     * @param sessionId The session ID from the main gateway.
     * @param token     The voice token from the VOICE_SERVER_UPDATE event.
     * @param endpoint  The voice server endpoint from the VOICE_SERVER_UPDATE event.
     * @return A CompletableFuture that completes when the voice connection is fully established.
     */
    CompletableFuture<Void> connect(String sessionId, String token, String endpoint);


    /**
     * Disconnects from the voice channel and returns a CompletableFuture that
     * completes when the disconnection process is fully finished.
     *
     * @return A CompletableFuture that completes upon full disconnection.
     */
    CompletableFuture<Void> disconnect();

    void setReceivingHandler(NekoffeeClient.AudioReceiveHandler handler);
}