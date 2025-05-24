package com.ladyluh.nekoffee.gateway.client.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.event.guild.GuildCreateEvent;
import com.ladyluh.nekoffee.api.event.guild.member.GuildMemberAddEvent;
import com.ladyluh.nekoffee.api.event.guild.member.GuildMemberRemoveEvent;
import com.ladyluh.nekoffee.api.event.guild.member.GuildMemberUpdateEvent;
import com.ladyluh.nekoffee.api.event.message.MessageDeleteEvent;
import com.ladyluh.nekoffee.api.event.message.MessageUpdateEvent;
import com.ladyluh.nekoffee.api.event.voice.VoiceStateUpdateEvent;
import com.ladyluh.nekoffee.api.exception.NekoffeeException;
import com.ladyluh.nekoffee.api.gateway.EventDispatcher;
import com.ladyluh.nekoffee.api.gateway.GatewayIntent;
import com.ladyluh.nekoffee.gateway.client.GatewayClient;
import com.ladyluh.nekoffee.json.util.JsonEngine;
import com.ladyluh.nekoffee.model.gateway.*;
import com.ladyluh.nekoffee.model.guild.GuildImpl;
import com.ladyluh.nekoffee.model.member.MemberImpl;
import com.ladyluh.nekoffee.model.message.MessageImpl;
import com.ladyluh.nekoffee.model.role.RoleImpl;
import com.ladyluh.nekoffee.model.voice.VoiceStateImpl;
import okhttp3.*;
import okio.ByteString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


public class OkHttpWebSocketGatewayClientImpl implements GatewayClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(OkHttpWebSocketGatewayClientImpl.class);
    private static final String GATEWAY_VERSION = "10";
    private static final String ENCODING = "json";

    private final OkHttpClient httpClient;
    private final JsonEngine jsonEngine;

    private WebSocket webSocket;
    private String botToken;
    private int intentsBitmask;
    private ScheduledExecutorService heartbeatExecutor;
    private ScheduledFuture<?> heartbeatTask;
    private final AtomicInteger sequence = new AtomicInteger(-1);
    private String sessionId;
    private final EventDispatcher eventDispatcher;

    private CompletableFuture<Void> connectionFuture;
    private final AtomicReference<GatewayState> state = new AtomicReference<>(GatewayState.DISCONNECTED);

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    private enum GatewayState {
        DISCONNECTED, CONNECTING, IDENTIFYING, CONNECTED, RESUMING, SHUTTING_DOWN
    }

    public OkHttpWebSocketGatewayClientImpl(OkHttpClient httpClient, JsonEngine jsonEngine, EventDispatcher eventDispatcher) {
        this.httpClient = Objects.requireNonNull(httpClient, "OkHttpClient cannot be null");
        this.jsonEngine = Objects.requireNonNull(jsonEngine, "JsonEngine cannot be null");
        this.eventDispatcher = Objects.requireNonNull(eventDispatcher, "EventDispatcher cannot be null");
    }


    private static class GatewayPayload<T> {
        @JsonProperty("op")
        public int op;
        @JsonProperty("d")
        public T d;
        @JsonProperty("s")
        public Integer s;
        @JsonProperty("t")
        public String t;

        public GatewayPayload(int op, T d) {
            this.op = op;
            this.d = d;
        }
    }


    private static class HelloPayload {
        @JsonProperty("heartbeat_interval")
        public int heartbeatInterval;
    }


    private static class IdentifyProperties {
        @JsonProperty("os")
        public String os = System.getProperty("os.name");
        @JsonProperty("browser")
        public String browser = "Nekoffee";
        @JsonProperty("device")
        public String device = "Nekoffee";
    }

    private static class IdentifyPayload {
        @JsonProperty("token")
        public String token;
        @JsonProperty("intents")
        public int intents;
        @JsonProperty("properties")
        public IdentifyProperties properties;

    }


    @Override
    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    @Override
    public void setIntents(Collection<GatewayIntent> intents) {
        this.intentsBitmask = GatewayIntent.calculateBitmask(intents);
    }

    @Override
    public synchronized CompletableFuture<Void> connect() {
        if (state.get() != GatewayState.DISCONNECTED) {
            LOGGER.warn("Gateway connect() called while not in DISCONNECTED state (current: {})", state.get());
            return connectionFuture != null ? connectionFuture : CompletableFuture.failedFuture(new IllegalStateException("Already connected or connecting."));
        }

        if (botToken == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Bot token not set."));
        }

        state.set(GatewayState.CONNECTING);
        connectionFuture = new CompletableFuture<>();
        LOGGER.info("Attempting to connect to Discord Gateway...");


        CompletableFuture.supplyAsync(this::getGatewayUrlFromRest)
                .thenAccept(this::establishWebSocketConnection)
                .exceptionally(ex -> {
                    LOGGER.error("Failed to obtain Gateway URL or establish connection", ex);
                    state.set(GatewayState.DISCONNECTED);
                    connectionFuture.completeExceptionally(ex);
                    return null;
                });

        return connectionFuture;
    }

    private String getGatewayUrlFromRest() {
        
        
        
        /*
        NekoffeeRestClient rest = ... ; 
        return rest.get("https://discord.com/api/v10/gateway/bot", Collections.emptyMap())
            .thenApply(responseBody -> {
                 Map<String, Object> data = jsonEngine.fromJsonString(responseBody, new TypeReference<Map<String, Object>>(){});
                 return (String) data.get("url");
            }).join(); 
        */
        LOGGER.warn("Using default Gateway URL. Production bots should fetch from /gateway/bot endpoint.");
        return "wss://gateway.discord.gg";
    }


    private void establishWebSocketConnection(String baseUrl) {
        if (state.get() != GatewayState.CONNECTING) {
            LOGGER.warn("Establish WebSocket called in invalid state: {}", state.get());
            if (!connectionFuture.isDone()) {
                connectionFuture.completeExceptionally(new IllegalStateException("Invalid state for WebSocket connection: " + state.get()));
            }
            return;
        }

        String fullGatewayUrl = baseUrl + "/?v=" + GATEWAY_VERSION + "&encoding=" + ENCODING;
        LOGGER.info("Connecting to WebSocket URL: {}", fullGatewayUrl);

        Request request = new Request.Builder().url(fullGatewayUrl).build();
        webSocket = httpClient.newWebSocket(request, new NekoffeeWebSocketListener());

    }


    @Override
    public synchronized void disconnect() {
        if (state.get() == GatewayState.DISCONNECTED || state.get() == GatewayState.SHUTTING_DOWN) {
            return;
        }
        LOGGER.info("Disconnecting from Gateway...");
        state.set(GatewayState.SHUTTING_DOWN);
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
            heartbeatTask = null;
        }
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = null;
        }
        if (webSocket != null) {
            webSocket.close(1000, "Client initiated disconnect");
            webSocket = null;
        }
        sequence.set(-1);
        sessionId = null;
        state.set(GatewayState.DISCONNECTED);
        if (connectionFuture != null && !connectionFuture.isDone()) {
            connectionFuture.completeExceptionally(new NekoffeeException("Connection intentionally closed."));
        }
        LOGGER.info("Gateway disconnected.");
    }

    @Override
    public boolean send(String jsonPayload) {
        if (webSocket != null && (state.get() == GatewayState.CONNECTED || state.get() == GatewayState.IDENTIFYING || state.get() == GatewayState.RESUMING)) {
            LOGGER.debug("GATEWAY SEND -> {}", jsonPayload);
            return webSocket.send(jsonPayload);
        }
        LOGGER.warn("Attempted to send payload while WebSocket is not ready or null. State: {}", state.get());
        return false;
    }

    private void sendIdentify() {
        LOGGER.info("Sending Identify payload...");
        state.set(GatewayState.IDENTIFYING);
        IdentifyProperties props = new IdentifyProperties();
        IdentifyPayload identifyData = new IdentifyPayload();
        identifyData.token = this.botToken;
        identifyData.intents = this.intentsBitmask;
        identifyData.properties = props;

        GatewayPayload<IdentifyPayload> payload = new GatewayPayload<>(2, identifyData);
        send(jsonEngine.toJsonString(payload));
    }

    private void startHeartbeat(int intervalMillis) {
        if (heartbeatExecutor == null || heartbeatExecutor.isShutdown()) {
            heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Nekoffee-Heartbeat-Thread");
                t.setDaemon(true);
                return t;
            });
        }
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        LOGGER.info("Starting heartbeat with interval: {}ms", intervalMillis);
        heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(() -> {


            if (state.get() != GatewayState.CONNECTED && state.get() != GatewayState.RESUMING) {
                LOGGER.warn("Heartbeat: Not in a connected state ({}), skipping heartbeat. Potential connection issue.", state.get());

                return;
            }

            GatewayPayload<Integer> payload = new GatewayPayload<>(1, sequence.get() == -1 ? null : sequence.get());

            send(jsonEngine.toJsonString(payload));
            LOGGER.debug("Heartbeat sent (s: {})", payload.d);
        }, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }


    private class NekoffeeWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(@NotNull WebSocket ws, @NotNull Response response) {
            LOGGER.info("WebSocket Connection Opened!");

        }

        @Override
        public void onMessage(@NotNull WebSocket ws, @NotNull String text) {
            LOGGER.debug("GATEWAY RECV <- {}", text);
            try {
                GatewayPayload<JsonNode> genericPayloadWrapper = jsonEngine.fromJsonString(text, new TypeReference<>() {
                });

                if (genericPayloadWrapper.s != null) {
                    sequence.set(genericPayloadWrapper.s);
                }

                switch (genericPayloadWrapper.op) {
                    case 0:
                        handleDispatch(genericPayloadWrapper.t, genericPayloadWrapper.d);
                        break;

                    case 10:
                        LOGGER.info("Received Hello from Gateway.");

                        HelloPayload helloData = jsonEngine.fromJsonString(genericPayloadWrapper.d.toString(), HelloPayload.class);
                        startHeartbeat(helloData.heartbeatInterval);
                        sendIdentify();
                        break;
                }
            } catch (Exception e) {
                LOGGER.error("Error processing message from Gateway: {}", text, e);
            }
        }


        private void handleDispatch(String eventType, JsonNode eventDataNode) {
            LOGGER.info("Received DISPATCH event: {} (Dispatcher: {})", eventType, eventDispatcher != null);
            if (eventDataNode == null) {
                LOGGER.warn("Received DISPATCH event {} with null data.", eventType);
                return;
            }
            String eventDataJson = eventDataNode.toString();

            NekoffeeClient clientInstance = (NekoffeeClient) eventDispatcher;

            try {
                assert eventDispatcher != null;
                switch (eventType) {
                    case "READY":
                        ReadyPayloadData readyData = jsonEngine.fromJsonString(eventDataJson, ReadyPayloadData.class);
                        LOGGER.info("Gateway READY received! Session ID: {}", readyData.getSessionId());
                        state.set(GatewayState.CONNECTED);
                        sessionId = readyData.getSessionId();

                        if (connectionFuture != null && !connectionFuture.isDone()) {
                            connectionFuture.complete(null);
                        }

                        ReadyEvent readyEvent = new ReadyEvent(clientInstance, readyData.getSelfUser(), readyData.getSessionId(), readyData.getResumeGatewayUrl(), readyData.getGatewayVersion());
                        eventDispatcher.dispatch(readyEvent);
                        break;

                    case "MESSAGE_CREATE":
                        MessageImpl message = jsonEngine.fromJsonString(eventDataJson, MessageImpl.class);

                        MessageCreateEvent mcEvent = new MessageCreateEvent(clientInstance, message);
                        eventDispatcher.dispatch(mcEvent);
                        break;

                    case "GUILD_MEMBER_ADD":
                        MemberImpl memberAdded = jsonEngine.fromJsonString(eventDataJson, MemberImpl.class);

                        GuildMemberAddEvent gmaEvent = new GuildMemberAddEvent(clientInstance, memberAdded);
                        eventDispatcher.dispatch(gmaEvent);
                        LOGGER.info("Dispatched GuildMemberAddEvent for user: {}", memberAdded.getUser() != null ? memberAdded.getUser().getAsTag() : memberAdded.getId());
                        break;

                    case "MESSAGE_UPDATE":

                        MessageImpl updatedMessage = jsonEngine.fromJsonString(eventDataJson, MessageImpl.class);


                        MessageUpdateEvent muEvent = new MessageUpdateEvent(clientInstance, updatedMessage);
                        eventDispatcher.dispatch(muEvent);
                        LOGGER.info("Dispatched MessageUpdateEvent for message ID: {}", updatedMessage.getId());
                        break;


                    case "MESSAGE_DELETE":
                        MessageDeletePayloadData deleteData = jsonEngine.fromJsonString(eventDataJson, MessageDeletePayloadData.class);
                        MessageDeleteEvent mdEvent = new MessageDeleteEvent(clientInstance, deleteData.getId(), deleteData.getChannelId(), deleteData.getGuildId());
                        eventDispatcher.dispatch(mdEvent);
                        LOGGER.info("Dispatched MessageDeleteEvent for message ID: {}", deleteData.getId());
                        break;

                    case "GUILD_MEMBER_REMOVE":
                        GuildMemberRemovePayloadData removeData = jsonEngine.fromJsonString(eventDataJson, GuildMemberRemovePayloadData.class);
                        GuildMemberRemoveEvent gmrEvent = new GuildMemberRemoveEvent(clientInstance, removeData.getGuildId(), removeData.getUser());
                        eventDispatcher.dispatch(gmrEvent);
                        LOGGER.info("Dispatched GuildMemberRemoveEvent for user: {} from guild: {}",
                                removeData.getUser() != null ? removeData.getUser().getAsTag() : "Unknown", removeData.getGuildId());
                        break;

                    case "GUILD_MEMBER_UPDATE":
                        MemberImpl updatedMember = jsonEngine.fromJsonString(eventDataJson, MemberImpl.class);

                        GuildMemberUpdateEvent gmuEvent = new GuildMemberUpdateEvent(clientInstance, updatedMember);
                        eventDispatcher.dispatch(gmuEvent);
                        LOGGER.info("Dispatched GuildMemberUpdateEvent for user: {}", updatedMember.getUser() != null ? updatedMember.getUser().getAsTag() : updatedMember.getId());
                        break;

                    case "VOICE_STATE_UPDATE":
                        VoiceStatePayloadData vsData = jsonEngine.fromJsonString(eventDataJson, VoiceStatePayloadData.class);
                        VoiceStateUpdateEvent vsEvent = new VoiceStateUpdateEvent(clientInstance,
                                vsData.getGuildId(), vsData.getChannelId(), vsData.getUserId(),
                                vsData.isMute() || vsData.isSelfMute(),
                                vsData.isDeaf() || vsData.isSelfDeaf()

                        );
                        eventDispatcher.dispatch(vsEvent);
                        LOGGER.info("Dispatched VoiceStateUpdateEvent for user: {} in guild: {}", vsData.getUserId(), vsData.getGuildId());
                        break;

                    case "GUILD_CREATE":

                        GuildImpl guild = jsonEngine.fromJsonString(eventDataJson, GuildImpl.class);


                        if (guild.getRoles() != null) {
                            guild.getRoles().forEach(role -> {
                                if (role instanceof RoleImpl) {
                                    ((RoleImpl) role).setGuildId(guild.getId());
                                }
                            });
                        }

                        if (guild.getVoiceStates() != null) {
                            guild.getVoiceStates().forEach(vs -> {
                                if (vs instanceof VoiceStateImpl) {
                                    ((VoiceStateImpl) vs).setGuildId(guild.getId());
                                }
                            });
                        }

                        GuildCreateEvent gcEvent = new GuildCreateEvent(clientInstance, guild);
                        eventDispatcher.dispatch(gcEvent);
                        LOGGER.info("Dispatched GuildCreateEvent for guild: {}", guild.getName());
                        break;


                    default:
                        LOGGER.trace("Unhandled DISPATCH event type: {}", eventType);
                }
            } catch (Exception e) {
                LOGGER.error("Error handling DISPATCH event {} with data: {}", eventType, eventDataJson, e);
            }
        }

        @Override
        public void onMessage(@NotNull WebSocket ws, @NotNull ByteString bytes) {
            LOGGER.warn("Received binary message from Gateway, which is not expected for JSON encoding. Ignoring.");
        }

        @Override
        public void onClosing(@NotNull WebSocket ws, int code, @NotNull String reason) {
            LOGGER.warn("Gateway is closing connection: {} - {}", code, reason);

            state.set(GatewayState.SHUTTING_DOWN);
        }

        @Override
        public void onClosed(@NotNull WebSocket ws, int code, @NotNull String reason) {
            LOGGER.warn("Gateway connection closed: {} - {}", code, reason);
            state.set(GatewayState.DISCONNECTED);
            if (heartbeatTask != null) {
                heartbeatTask.cancel(true);

            }
            if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
                heartbeatExecutor.shutdownNow();
            }

            if (connectionFuture != null && !connectionFuture.isDone()) {
                connectionFuture.completeExceptionally(new NekoffeeException("Gateway connection closed unexpectedly: " + code + " " + reason));
            }
            LOGGER.info("Gateway disconnection detected, connecting again...");
            connect();
        }

        @Override
        public void onFailure(@NotNull WebSocket ws, @NotNull Throwable t, @Nullable Response response) {
            LOGGER.error("Gateway connection failure!", t);
            state.set(GatewayState.DISCONNECTED);
            if (heartbeatTask != null) {
                heartbeatTask.cancel(true);
            }
            if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
                heartbeatExecutor.shutdownNow();
            }
            if (connectionFuture != null && !connectionFuture.isDone()) {
                connectionFuture.completeExceptionally(t);
            }
            LOGGER.info("Gateway failure detected, connecting again...");
            connect();
        }
    }
}