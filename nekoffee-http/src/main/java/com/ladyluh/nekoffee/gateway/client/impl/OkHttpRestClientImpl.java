package com.ladyluh.nekoffee.gateway.client.impl;

import com.ladyluh.nekoffee.api.exception.NekoffeeException;
import com.ladyluh.nekoffee.gateway.client.RestClient;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class OkHttpRestClientImpl implements RestClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient httpClient;
    private String botToken;

    private final ExecutorService callbackExecutor = Executors.newCachedThreadPool(
            runnable -> {
                Thread t = Executors.defaultThreadFactory().newThread(runnable);
                t.setName("Nekoffee-OkHttp-Callback-" + t.getId());
                t.setDaemon(true);
                return t;
            });


    public OkHttpRestClientImpl() {
        this.httpClient = new OkHttpClient.Builder()

                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)

                .build();
    }

    @Override
    public void setBotToken(String botToken) {
        this.botToken = Objects.requireNonNull(botToken, "Bot token cannot be null");
    }

    private Headers buildHeaders(Map<String, String> additionalHeaders, boolean includeContentType) {
        Headers.Builder builder = new Headers.Builder();
        if (this.botToken == null) {
            throw new IllegalStateException("Bot token has not been set. Call setBotToken() first.");
        }
        builder.add("Authorization", "Bot " + this.botToken);
        builder.add("User-Agent", "Nekoffee Discord Bot (https://github.com/yourusername/nekoffee, 0.1.0)");

        if (includeContentType) {
            builder.add("Content-Type", JSON.toString());
        }

        if (additionalHeaders != null) {
            additionalHeaders.forEach(builder::add);
        }
        return builder.build();
    }

    @Override
    public CompletableFuture<String> patch(String url, String jsonPayload, Map<String, String> headers) {
        RequestBody body = RequestBody.create(jsonPayload, JSON);
        Request request = new Request.Builder()
                .url(url)
                .headers(buildHeaders(headers, true))
                .patch(body)
                .build();
        return executeAsync(request);
    }

    @Override
    public CompletableFuture<String> get(String url, Map<String, String> headers) {
        Request request = new Request.Builder()
                .url(url)
                .headers(buildHeaders(headers, false))
                .get()
                .build();
        return executeAsync(request);
    }

    @Override
    public CompletableFuture<String> post(String url, String jsonPayload, Map<String, String> headers) {
        RequestBody body = RequestBody.create(jsonPayload, JSON);
        Request request = new Request.Builder()
                .url(url)
                .headers(buildHeaders(headers, true))
                .post(body)
                .build();
        return executeAsync(request);
    }

    private CompletableFuture<String> executeAsync(Request request) {
        CompletableFuture<String> future = new CompletableFuture<>();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {

                callbackExecutor.submit(() -> future.completeExceptionally(new NekoffeeException("Request failed: " + request.method() + " " + request.url(), e)));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {

                try (ResponseBody responseBody = response.body()) {
                    final String bodyString = responseBody != null ? responseBody.string() : null;
                    callbackExecutor.submit(() -> {
                        if (response.isSuccessful()) {
                            future.complete(bodyString);
                        } else {


                            String errorMessage = "Request to " + request.url() + " failed with status " + response.code();
                            if (bodyString != null && !bodyString.isEmpty()) {
                                errorMessage += "\nResponse: " + bodyString;
                            }
                            future.completeExceptionally(new NekoffeeException(errorMessage));
                        }
                    });
                } catch (Exception e) {
                    callbackExecutor.submit(() -> future.completeExceptionally(new NekoffeeException("Error processing response from " + request.url(), e)));
                }
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<String> put(String url, String jsonPayload, Map<String, String> headers) {
        RequestBody body;
        if (jsonPayload != null && !jsonPayload.isEmpty()) {
            body = RequestBody.create(jsonPayload, JSON);
        } else {

            body = RequestBody.create(new byte[0], null);
        }

        Request request = new Request.Builder()
                .url(url)
                .headers(buildHeaders(headers, jsonPayload != null && !jsonPayload.isEmpty()))
                .put(body)
                .build();
        return executeAsync(request);
    }

    @Override
    public CompletableFuture<String> delete(String url, Map<String, String> headers) {
        Request request = new Request.Builder()
                .url(url)
                .headers(buildHeaders(headers, false))
                .delete()
                .build();
        return executeAsync(request);
    }

    @Override
    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        callbackExecutor.shutdown();
        try {
            if (!httpClient.dispatcher().executorService().awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("OkHttp dispatcher did not terminate in time.");
            }
            if (!callbackExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Nekoffee callback executor did not terminate in time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Shutdown interrupted.");
        }
    }
}