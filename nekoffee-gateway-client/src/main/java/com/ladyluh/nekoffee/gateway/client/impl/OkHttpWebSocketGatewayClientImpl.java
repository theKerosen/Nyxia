package com.ladyluh.nekoffee.gateway.client.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.event.Event;
import com.ladyluh.nekoffee.api.event.guild.GuildCreateEvent;
import com.ladyluh.nekoffee.api.event.guild.member.GuildMemberAddEvent;
import com.ladyluh.nekoffee.api.event.guild.member.GuildMemberRemoveEvent;
import com.ladyluh.nekoffee.api.event.guild.member.GuildMemberUpdateEvent;
import com.ladyluh.nekoffee.api.event.message.MessageDeleteEvent;
import com.ladyluh.nekoffee.api.event.message.MessageUpdateEvent;
import com.ladyluh.nekoffee.api.event.voice.VoiceServerUpdateEvent;
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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class OkHttpWebSocketGatewayClientImpl implements GatewayClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(OkHttpWebSocketGatewayClientImpl.class);
    private static final String GATEWAY_VERSION = "10";
    private static final String ENCODING = "json";

    private final OkHttpClient httpClient;
    private final JsonEngine jsonEngine;
    private final AtomicInteger sequence = new AtomicInteger(-1);
    private final AtomicBoolean receivedHeartbeatAck = new AtomicBoolean(true);
    private final EventDispatcher eventDispatcher;
    private final AtomicReference<GatewayState> state = new AtomicReference<>(GatewayState.DISCONNECTED);
    private WebSocket webSocket;
    private String botToken;
    private int intentsBitmask;
    private ScheduledExecutorService heartbeatExecutor;
    private ScheduledFuture<?> heartbeatTask;
    private String sessionId;
    private CompletableFuture<Void> connectionFuture;


    public OkHttpWebSocketGatewayClientImpl(OkHttpClient httpClient, JsonEngine jsonEngine, EventDispatcher eventDispatcher) {
        this.httpClient = Objects.requireNonNull(httpClient, "OkHttpClient cannot be null");
        this.jsonEngine = Objects.requireNonNull(jsonEngine, "JsonEngine cannot be null");
        this.eventDispatcher = Objects.requireNonNull(eventDispatcher, "EventDispatcher cannot be null");
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
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
        if (botToken == null) return CompletableFuture.failedFuture(new IllegalStateException("Bot token not set."));
        state.set(GatewayState.CONNECTING);
        connectionFuture = new CompletableFuture<>();
        LOGGER.info("Attempting to connect to Discord Gateway...");
        CompletableFuture.supplyAsync(this::getGatewayUrlFromRest)
                .thenAccept(this::establishWebSocketConnection)
                .exceptionally(ex -> {
                    LOGGER.error("Failed to obtain Gateway URL or establish connection", ex);
                    state.set(GatewayState.DISCONNECTED);
                    if (connectionFuture != null && !connectionFuture.isDone()) {
                        connectionFuture.completeExceptionally(ex);
                    }
                    return null;
                });
        return connectionFuture;
    }

    private String getGatewayUrlFromRest() {
        LOGGER.warn("Using default Gateway URL. Production bots should fetch from /gateway/bot endpoint.");
        return "wss://gateway.discord.gg";
    }


    private void establishWebSocketConnection(String baseUrl) {
        if (state.get() != GatewayState.CONNECTING) {
            LOGGER.warn("Establish WebSocket called in invalid state: {}", state.get());
            if (connectionFuture != null && !connectionFuture.isDone()) {
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
        if (state.get() == GatewayState.DISCONNECTED || state.get() == GatewayState.SHUTTING_DOWN) return;
        LOGGER.info("Disconnecting from Gateway...");
        state.set(GatewayState.SHUTTING_DOWN);
        stopHeartbeat();
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

    private void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
            heartbeatTask = null;
        }
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = null;
        }
    }

    @Override
    public void send(String jsonPayload) {
        if (webSocket != null && (state.get() == GatewayState.CONNECTED || state.get() == GatewayState.IDENTIFYING || state.get() == GatewayState.RESUMING)) {
            webSocket.send(jsonPayload);
            return;
        }
        LOGGER.warn("Attempted to send payload while WebSocket is not ready or null. State: {}", state.get());
    }

    @Override
    public void sendPresenceUpdate(Object payloadData) {
        GatewaySendPayload gatewayPayload = new GatewaySendPayload(3, payloadData);
        send(jsonEngine.toJsonString(gatewayPayload));
    }

    private void sendIdentify() {
        LOGGER.info("Sending Identify payload...");
        state.set(GatewayState.IDENTIFYING);
        IdentifyPayload identifyData = new IdentifyPayload();
        identifyData.token = this.botToken;
        identifyData.intents = this.intentsBitmask;
        identifyData.properties = new IdentifyProperties();
        GatewaySendPayload payload = new GatewaySendPayload(2, identifyData);
        send(jsonEngine.toJsonString(payload));
    }

    private void sendHeartbeat() {
        if (!receivedHeartbeatAck.get()) {
            LOGGER.warn("Did not receive Heartbeat ACK since last heartbeat. Reconnecting (Zombied Connection)...");
            if (webSocket != null) webSocket.close(4000, "Zombied connection");
            return;
        }
        receivedHeartbeatAck.set(false);
        int sequenceValue = sequence.get();
        GatewaySendPayload payload = new GatewaySendPayload(1, sequenceValue == -1 ? null : sequenceValue);
        send(jsonEngine.toJsonString(payload));
        LOGGER.trace("Heartbeat sent (s: {})", sequenceValue);
    }

    private void startHeartbeat(int intervalMillis) {
        if (heartbeatExecutor == null || heartbeatExecutor.isShutdown()) {
            heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Nekoffee-Heartbeat-Thread");
                t.setDaemon(true);
                return t;
            });
        }
        if (heartbeatTask != null) heartbeatTask.cancel(false);
        LOGGER.info("Starting heartbeat with interval: {}ms", intervalMillis);
        long initialDelay = (long) (intervalMillis * Math.random());
        heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeat, initialDelay, intervalMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void sendVoiceStateUpdate(String guildId, @Nullable String channelId, boolean selfMute, boolean selfDeaf) {
        VoiceStateUpdatePayload data = new VoiceStateUpdatePayload(guildId, channelId, selfMute, selfDeaf);
        GatewaySendPayload payload = new GatewaySendPayload(4, data);
        send(jsonEngine.toJsonString(payload));
    }


    private enum GatewayState {DISCONNECTED, CONNECTING, IDENTIFYING, CONNECTED, RESUMING, SHUTTING_DOWN}

    public static class GatewayReceivePayload {
        @JsonProperty("op")
        public int op;
        @JsonProperty("d")
        public JsonNode d;
        @JsonProperty("s")
        public Integer s;
        @JsonProperty("t")
        public String t;
    }

    public static class GatewaySendPayload {
        @JsonProperty("op")
        public final int op;
        @JsonProperty("d")
        public final Object d;

        public GatewaySendPayload(int op, Object d) {
            this.op = op;
            this.d = d;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ActivitySendPayload {
        @JsonProperty("name")
        public final String name;
        @JsonProperty("type")
        public final int type;
        @JsonProperty("url")
        public final String url;

        public ActivitySendPayload(String name, int type) {
            this(name, type, null);
        }

        public ActivitySendPayload(String name, int type, @Nullable String url) {
            if (name == null || name.trim().isEmpty())
                throw new IllegalArgumentException("Activity name cannot be null or empty.");
            this.name = name;
            this.type = type;
            if (type == 1 && (url == null || url.trim().isEmpty()))
                throw new IllegalArgumentException("Activity URL cannot be null or empty when type is STREAMING (1).");
            this.url = url;
        }
    }

    /**
     * @param since **THE FINAL FIX IS HERE:** This annotation forces Jackson to always include the 'since' field in the JSON, even if its value is null. This is what Discord's parser requires.
     */
    public record PresenceUpdateSendPayload(@JsonProperty("activities") List<ActivitySendPayload> activities,
                                            @JsonProperty("afk") boolean afk,
                                            @JsonProperty("since") @JsonInclude(JsonInclude.Include.ALWAYS) Long since,
                                            @JsonProperty("status") String status) {
        public PresenceUpdateSendPayload(List<ActivitySendPayload> activities, boolean afk, Long since, String status) {
            this.activities = activities;
            this.afk = afk;
            this.since = since;
            this.status = status;
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

    private class NekoffeeWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(@NotNull WebSocket ws, @NotNull Response response) {
            LOGGER.info("WebSocket Connection Opened!");
        }

        @Override
        public void onMessage(@NotNull WebSocket ws, @NotNull String text) {
            LOGGER.trace("GATEWAY RECV <- {}", text);
            try {
                GatewayReceivePayload genericPayloadWrapper = jsonEngine.fromJsonString(text, GatewayReceivePayload.class);
                if (genericPayloadWrapper.s != null) sequence.set(genericPayloadWrapper.s);

                switch (genericPayloadWrapper.op) {
                    case 0 -> handleDispatch(genericPayloadWrapper.t, genericPayloadWrapper.d);
                    case 1 -> {
                        LOGGER.debug("Gateway requested a heartbeat. Sending one now.");
                        sendHeartbeat();
                    }
                    case 10 -> {
                        LOGGER.info("Received Hello from Gateway.");
                        receivedHeartbeatAck.set(true);
                        HelloPayload helloData = jsonEngine.fromJsonString(genericPayloadWrapper.d.toString(), HelloPayload.class);
                        startHeartbeat(helloData.heartbeatInterval);
                        sendIdentify();
                    }
                    case 11 -> {
                        LOGGER.trace("Heartbeat ACK received.");
                        receivedHeartbeatAck.set(true);
                    }
                    default -> LOGGER.warn("Received unhandled opcode: {}", genericPayloadWrapper.op);
                }
            } catch (Exception e) {
                LOGGER.error("Error processing message from Gateway: {}", text, e);
            }
        }

        private void handleDispatch(String eventType, JsonNode eventDataNode) {
            if (eventDataNode == null) {
                LOGGER.warn("Received DISPATCH event {} with null data.", eventType);
                return;
            }
            String eventDataJson = eventDataNode.toString();
            NekoffeeClient clientInstance = (NekoffeeClient) eventDispatcher;

            try {
                Event event = null;
                switch (eventType) {
                    case "READY" -> {
                        ReadyPayloadData readyData = jsonEngine.fromJsonString(eventDataJson, ReadyPayloadData.class);
                        LOGGER.info("Gateway READY received! Session ID: {}", readyData.getSessionId());
                        state.set(GatewayState.CONNECTED);
                        sessionId = readyData.getSessionId();
                        if (connectionFuture != null && !connectionFuture.isDone()) {
                            connectionFuture.complete(null);
                        }
                        event = new ReadyEvent(clientInstance, readyData.getSelfUser(), readyData.getSessionId(), readyData.getResumeGatewayUrl(), readyData.getGatewayVersion());
                    }
                    case "MESSAGE_CREATE" -> {
                        MessageImpl message = jsonEngine.fromJsonString(eventDataJson, MessageImpl.class);
                        event = new MessageCreateEvent(clientInstance, message);
                    }
                    case "MESSAGE_UPDATE" -> {
                        MessageImpl updatedMessage = jsonEngine.fromJsonString(eventDataJson, MessageImpl.class);
                        event = new MessageUpdateEvent(clientInstance, updatedMessage);
                    }
                    case "MESSAGE_DELETE" -> {
                        MessageDeletePayloadData deleteData = jsonEngine.fromJsonString(eventDataJson, MessageDeletePayloadData.class);
                        event = new MessageDeleteEvent(clientInstance, deleteData.getId(), deleteData.getChannelId(), deleteData.getGuildId());
                    }
                    case "GUILD_CREATE" -> {
                        GuildImpl guild = jsonEngine.fromJsonString(eventDataJson, GuildImpl.class);
                        guild.getRoles().forEach(role -> {
                            if (role instanceof RoleImpl r) r.setGuildId(guild.getId());
                        });
                        guild.getVoiceStates().forEach(vs -> {
                            if (vs instanceof VoiceStateImpl v) v.setGuildId(guild.getId());
                        });
                        event = new GuildCreateEvent(clientInstance, guild);
                        LOGGER.info("Dispatched GuildCreateEvent for guild: {}", guild.getName());
                    }
                    case "GUILD_MEMBER_ADD" -> {
                        MemberImpl memberAdded = jsonEngine.fromJsonString(eventDataJson, MemberImpl.class);
                        memberAdded.setNekoffeeClient(clientInstance);
                        event = new GuildMemberAddEvent(clientInstance, memberAdded);
                        LOGGER.info("Dispatched GuildMemberAddEvent for user: {}", memberAdded.getUser() != null ? memberAdded.getUser().getAsTag() : memberAdded.getId());
                    }
                    case "GUILD_MEMBER_UPDATE" -> {
                        MemberImpl updatedMember = jsonEngine.fromJsonString(eventDataJson, MemberImpl.class);
                        updatedMember.setNekoffeeClient(clientInstance);
                        event = new GuildMemberUpdateEvent(clientInstance, updatedMember);
                    }
                    case "GUILD_MEMBER_REMOVE" -> {
                        GuildMemberRemovePayloadData removeData = jsonEngine.fromJsonString(eventDataJson, GuildMemberRemovePayloadData.class);
                        event = new GuildMemberRemoveEvent(clientInstance, removeData.getGuildId(), removeData.getUser());
                        LOGGER.info("Dispatched GuildMemberRemoveEvent for user: {}", removeData.getUser() != null ? removeData.getUser().getAsTag() : "Unknown");
                    }
                    case "VOICE_STATE_UPDATE" -> {
                        VoiceStatePayloadData vsData = jsonEngine.fromJsonString(eventDataJson, VoiceStatePayloadData.class);
                        event = new VoiceStateUpdateEvent(clientInstance, vsData.getGuildId(), vsData.getChannelId(), vsData.getUserId(), vsData.isMute() || vsData.isSelfMute(), vsData.isDeaf() || vsData.isSelfDeaf());
                    }

                    case "VOICE_SERVER_UPDATE" -> {
                        JsonNode guildIdNode = eventDataNode.get("guild_id");
                        JsonNode tokenNode = eventDataNode.get("token");
                        JsonNode endpointNode = eventDataNode.get("endpoint");

                        if (guildIdNode != null && tokenNode != null && endpointNode != null) {
                            event = new VoiceServerUpdateEvent(clientInstance, guildIdNode.asText(), tokenNode.asText(), endpointNode.asText());
                        } else {
                            LOGGER.warn("Received VOICE_SERVER_UPDATE with incomplete data.");
                        }
                    }
                    default -> LOGGER.trace("Unhandled DISPATCH event type: {}", eventType);
                }
                if (event != null) {
                    eventDispatcher.dispatch(event);
                }
            } catch (Exception e) {
                LOGGER.error("Error handling DISPATCH event {} with data: {}", eventType, eventDataJson, e);
            }
        }

        @Override
        public void onMessage(@NotNull WebSocket ws, @NotNull ByteString bytes) {
            LOGGER.warn("Received binary message, which is not expected.");
        }

        @Override
        public void onClosing(@NotNull WebSocket ws, int code, @NotNull String reason) {
            LOGGER.warn("Gateway is closing connection: {} - {}", code, reason);
        }

        @Override
        public void onClosed(@NotNull WebSocket ws, int code, @NotNull String reason) {
            LOGGER.warn("Gateway connection closed: {} - {}", code, reason);
            boolean wasShuttingDown = state.getAndSet(GatewayState.DISCONNECTED) == GatewayState.SHUTTING_DOWN;
            stopHeartbeat();
            if (connectionFuture != null && !connectionFuture.isDone()) {
                connectionFuture.completeExceptionally(new NekoffeeException("Gateway connection closed unexpectedly: " + code + " " + reason));
            }
            if (!wasShuttingDown) {
                LOGGER.info("Gateway disconnection detected, attempting to reconnect...");
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                connect();
            }
        }

        @Override
        public void onFailure(@NotNull WebSocket ws, @NotNull Throwable t, @Nullable Response response) {
            LOGGER.error("Gateway connection failure!", t);
            boolean wasShuttingDown = state.getAndSet(GatewayState.DISCONNECTED) == GatewayState.SHUTTING_DOWN;
            stopHeartbeat();
            if (connectionFuture != null && !connectionFuture.isDone()) {
                connectionFuture.completeExceptionally(t);
            }
            if (!wasShuttingDown) {
                LOGGER.info("Gateway failure detected, attempting to reconnect...");
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                connect();
            }
        }
    }
}