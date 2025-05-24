package com.ladyluh.nekoffee.gateway.client;

import com.ladyluh.nekoffee.api.gateway.GatewayIntent;

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
     * @return true se o envio foi bem-sucedido (enfileirado), false caso contrário.
     */
    boolean send(String jsonPayload);
}