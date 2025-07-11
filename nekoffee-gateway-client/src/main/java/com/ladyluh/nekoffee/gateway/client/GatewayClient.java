package com.ladyluh.nekoffee.gateway.client;

import com.ladyluh.nekoffee.api.gateway.GatewayIntent;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public interface GatewayClient {

    /**
     * Define o token do bot a ser usado para a autenticação do Gateway.
     *
     * @param botToken O token do bot.
     */
    void setBotToken(String botToken);

    /**
     * Define os intents que serão usados na conexão com o Gateway.
     *
     * @param intents Uma coleção de GatewayIntents.
     */
    void setIntents(Collection<GatewayIntent> intents);

    void sendVoiceStateUpdate(String guildId, @Nullable String channelId, boolean selfMute, boolean selfDeaf);

    /**
     * Conecta-se ao Gateway do Discord.
     *
     * @return Um CompletableFuture que é completado quando a conexão é estabelecida e o evento READY é recebido.
     */
    CompletableFuture<Void> connect();

    /**
     * Desconecta do Gateway do Discord.
     */
    void disconnect();

    /**
     * Envia um payload JSON para o Gateway.
     *
     * @param jsonPayload O payload a ser enviado.
     */
    void send(String jsonPayload);

    /**
     * FIX: Add this method to the interface.
     * Envia um payload de atualização de presença (Opcode 3) para o Gateway.
     *
     * @param payloadData O objeto de dados para a atualização de presença.
     */
    void sendPresenceUpdate(Object payloadData);


}