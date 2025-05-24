package com.ladyluh.nekoffee.gateway.client;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface RestClient {

    /**
     * Define o token de autorização do bot a ser usado nas requisições.
     *
     * @param botToken O token do bot.
     */
    void setBotToken(String botToken);

    /**
     * Executa uma requisição HTTP GET.
     *
     * @param url     A URL do endpoint.
     * @param headers Headers adicionais (além da autorização).
     * @return Um CompletableFuture contendo a string da resposta.
     */
    CompletableFuture<String> get(String url, Map<String, String> headers);

    CompletableFuture<String> patch(String url, String jsonPayload, Map<String, String> headers);

    /**
     * Executa uma requisição HTTP POST.
     *
     * @param url         A URL do endpoint.
     * @param jsonPayload O corpo da requisição em formato JSON.
     * @param headers     Headers adicionais (além da autorização e Content-Type).
     * @return Um CompletableFuture contendo a string da resposta.
     */
    CompletableFuture<String> post(String url, String jsonPayload, Map<String, String> headers);


    /**
     * Executa uma requisição HTTP PUT.
     *
     * @param url         A URL do endpoint.
     * @param jsonPayload O corpo da requisição em formato JSON (pode ser null ou vazio se não houver corpo).
     * @param headers     Headers adicionais.
     * @return Um CompletableFuture contendo a string da resposta (pode ser vazia para status 204).
     */
    CompletableFuture<String> put(String url, String jsonPayload, Map<String, String> headers);

    /**
     * Executa uma requisição HTTP DELETE.
     *
     * @param url     A URL do endpoint.
     * @param headers Headers adicionais.
     * @return Um CompletableFuture contendo a string da resposta (pode ser vazia para status 204).
     */
    CompletableFuture<String> delete(String url, Map<String, String> headers);

    /**
     * Encerra o cliente HTTP, liberando recursos.
     */
    void shutdown();
}