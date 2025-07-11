package com.ladyluh.nekoffee;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.*;
import com.ladyluh.nekoffee.api.entities.channel.Channel;
import com.ladyluh.nekoffee.api.entities.channel.ChannelType;
import com.ladyluh.nekoffee.api.event.Event;
import com.ladyluh.nekoffee.api.event.EventListener;
import com.ladyluh.nekoffee.api.event.voice.VoiceServerUpdateEvent;
import com.ladyluh.nekoffee.api.exception.NekoffeeException;
import com.ladyluh.nekoffee.api.gateway.EventDispatcher;
import com.ladyluh.nekoffee.api.gateway.GatewayIntent;
import com.ladyluh.nekoffee.api.payload.channel.ChannelModifyPayload;
import com.ladyluh.nekoffee.api.payload.channel.CreateGuildChannelPayload;
import com.ladyluh.nekoffee.api.payload.member.ModifyMemberPayload;
import com.ladyluh.nekoffee.api.payload.permission.Permission;
import com.ladyluh.nekoffee.api.payload.permission.PermissionOverwritePayload;
import com.ladyluh.nekoffee.api.payload.send.MessageSendPayload;
import com.ladyluh.nekoffee.api.voice.VoiceConnection;
import com.ladyluh.nekoffee.gateway.client.GatewayClient;
import com.ladyluh.nekoffee.gateway.client.RestClient;
import com.ladyluh.nekoffee.gateway.client.impl.OkHttpWebSocketGatewayClientImpl;
import com.ladyluh.nekoffee.json.util.JsonEngine;
import com.ladyluh.nekoffee.model.gateway.ReadyEvent;
import com.ladyluh.nekoffee.model.guild.GuildImpl;
import com.ladyluh.nekoffee.model.member.MemberImpl;
import com.ladyluh.nekoffee.model.message.MessageImpl;
import com.ladyluh.nekoffee.model.role.RoleImpl;
import com.ladyluh.nekoffee.model.user.UserImpl;
import com.ladyluh.nekoffee.voice.VoiceConnectionImpl;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class NekoffeeClientImpl implements NekoffeeClient, EventDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(NekoffeeClientImpl.class);
    private static final String DISCORD_API_BASE_URL = "https://discord.com/api/v10";

    private final RestClient restClient;
    private final JsonEngine jsonEngine;
    private final List<EventListener> eventListeners = new CopyOnWriteArrayList<>();
    private final GatewayClient gatewayClient;
    private final OkHttpClient sharedOkHttpClient;
    private final Map<String, VoiceConnection> voiceConnections = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<VoiceConnection>> pendingVoiceConnections = new ConcurrentHashMap<>();

    private boolean loggedIn = false;
    private User selfUser;
    private String sessionId;

    NekoffeeClientImpl(RestClient restClient, JsonEngine jsonEngine, OkHttpClient sharedOkHttpClient) {
        this.restClient = Objects.requireNonNull(restClient, "RestClient cannot be null");
        this.jsonEngine = Objects.requireNonNull(jsonEngine, "JsonEngine cannot be null");
        this.sharedOkHttpClient = Objects.requireNonNull(sharedOkHttpClient, "Shared OkHttpClient cannot be null");
        this.gatewayClient = new OkHttpWebSocketGatewayClientImpl(this.sharedOkHttpClient, this.jsonEngine, this);
    }


    @Override
    public CompletableFuture<Void> login(String token, Collection<GatewayIntent> intents) {
        if (loggedIn) {
            return CompletableFuture.failedFuture(new NekoffeeException("Already logged in."));
        }
        String botToken = Objects.requireNonNull(token, "Token cannot be null");
        this.restClient.setBotToken(botToken);
        this.gatewayClient.setBotToken(botToken);
        this.gatewayClient.setIntents(intents);

        LOGGER.info("NekoffeeClient: Token and intents set. Connecting to Gateway...");

        return gatewayClient.connect().thenRun(() -> {
            loggedIn = true;
            LOGGER.info("NekoffeeClient successfully connected to Gateway and received READY.");
        }).exceptionally(throwable -> {
            LOGGER.error("NekoffeeClient login failed during Gateway connection.", throwable);

            if (throwable instanceof CompletionException && throwable.getCause() != null) {
                throw new NekoffeeException("Gateway login failed", throwable.getCause());
            }
            throw new NekoffeeException("Gateway login failed", throwable);
        });
    }

    @Override
    public void setActivity(ActivityType type, String name, @Nullable String url) {
        if (!loggedIn) {
            LOGGER.warn("Cannot set activity, client is not logged in.");
            return;
        }

        try {

            OkHttpWebSocketGatewayClientImpl.ActivitySendPayload activity = new OkHttpWebSocketGatewayClientImpl.ActivitySendPayload(name, type.getValue(), url);
            OkHttpWebSocketGatewayClientImpl.PresenceUpdateSendPayload presence = new OkHttpWebSocketGatewayClientImpl.PresenceUpdateSendPayload(
                    List.of(activity),
                    false,
                    null,
                    "online"
            );


            gatewayClient.sendPresenceUpdate(presence);

        } catch (IllegalArgumentException e) {

            LOGGER.error("Failed to set activity due to invalid arguments: {}", e.getMessage());
        }
    }


    @Override
    public void shutdown() {
        if (!loggedIn) return;
        LOGGER.info("NekoffeeClient shutting down...");
        voiceConnections.keySet().forEach(this::leaveVoiceChannel);
        gatewayClient.disconnect();
        restClient.shutdown();
        loggedIn = false;
        LOGGER.info("NekoffeeClient shutdown complete.");
    }

    @Override
    public User getSelfUser() {
        if (this.selfUser == null) {
            throw new IllegalStateException("Self user is not available yet. Bot might not be fully ready.");
        }
        return this.selfUser;
    }

    @Override
    public void dispatch(Event event) {
        if (event instanceof ReadyEvent readyEvent) {
            this.selfUser = readyEvent.getSelfUser();
            this.sessionId = readyEvent.getSessionId();
            LOGGER.info("Client is READY. Session ID set to {}", this.sessionId);
        }

        if (event instanceof VoiceServerUpdateEvent vsu) {
            LOGGER.debug("Received VoiceServerUpdate for guild {}. Checking for pending connections.", vsu.getGuildId());
            CompletableFuture<VoiceConnection> pendingFuture = pendingVoiceConnections.get(vsu.getGuildId());
            VoiceConnection conn = voiceConnections.get(vsu.getGuildId());

            if (pendingFuture != null && conn instanceof VoiceConnectionImpl voiceConn) {
                LOGGER.info("Found pending voice connection for guild {}. Initiating voice server connection.", vsu.getGuildId());
                voiceConn.connect(this.sessionId, vsu.getToken(), vsu.getEndpoint())
                        .whenComplete((aVoid, throwable) -> {
                            pendingVoiceConnections.remove(vsu.getGuildId());
                            if (throwable != null) {
                                LOGGER.error("Failed to establish voice connection for guild {}", vsu.getGuildId(), throwable);
                                pendingFuture.completeExceptionally(throwable);
                                voiceConnections.remove(vsu.getGuildId());
                                conn.disconnect();
                            } else {
                                LOGGER.info("Voice connection for guild {} fully established.", vsu.getGuildId());
                                pendingFuture.complete(conn);
                            }
                        });
            }
        }

        for (EventListener listener : eventListeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                LOGGER.error("Uncaught exception in event listener {} for event {}: {}",
                        listener.getClass().getName(), event.getClass().getName(), e.getMessage(), e);
            }
        }
    }

    @Override
    public CompletableFuture<VoiceConnection> joinVoiceChannel(String guildId, String channelId) {
        if (!loggedIn) {
            return CompletableFuture.failedFuture(new NekoffeeException("Cannot join voice channel, client is not logged in."));
        }
        return pendingVoiceConnections.computeIfAbsent(guildId, gid -> {
            LOGGER.info("Initiating join for guild {}, channel {}.", gid, channelId);

            if (voiceConnections.containsKey(gid)) {
                pendingVoiceConnections.remove(gid);
                return CompletableFuture.completedFuture(voiceConnections.get(gid));
            }

            CompletableFuture<VoiceConnection> connectionFuture = new CompletableFuture<>();
            VoiceConnectionImpl connection = new VoiceConnectionImpl(gid, getSelfUser().getId(), this, jsonEngine, sharedOkHttpClient);
            voiceConnections.put(gid, connection);

            gatewayClient.sendVoiceStateUpdate(gid, channelId, false, false);

            return connectionFuture;
        });
    }

    @Override
    public void _internal_leaveVoiceChannel(String guildId) {

        this.leaveVoiceChannel(guildId);
    }


    @Override
    public CompletableFuture<Void> leaveVoiceChannel(String guildId) {
        if (!loggedIn) {
            LOGGER.warn("Cannot leave voice channel, client is not logged in.");
            return CompletableFuture.failedFuture(new NekoffeeException("Not logged in."));
        }
        LOGGER.info("Leaving voice channel in guild {}.", guildId);
        gatewayClient.sendVoiceStateUpdate(guildId, null, false, false);

        VoiceConnection connection = voiceConnections.remove(guildId);
        if (connection != null) {

            return connection.disconnect();
        }

        CompletableFuture<VoiceConnection> pending = pendingVoiceConnections.remove(guildId);
        if (pending != null && !pending.isDone()) {
            pending.completeExceptionally(new NekoffeeException("Voice connection was cancelled by leaving the channel."));
        }


        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void sendVoiceStateUpdate(String guildId, @Nullable String channelId, boolean selfMute, boolean selfDeaf) {
        if (!loggedIn) {
            LOGGER.warn("Cannot send voice state update, client is not logged in.");
            return;
        }
        gatewayClient.sendVoiceStateUpdate(guildId, channelId, selfMute, selfDeaf);
    }

    @Override
    public CompletableFuture<Channel> modifyChannel(String channelId, ChannelModifyPayload payload) {
        if (!loggedIn) {
            return CompletableFuture.failedFuture(new NekoffeeException("Not logged in. Call login() first."));
        }
        Objects.requireNonNull(channelId, "Channel ID cannot be null");
        Objects.requireNonNull(payload, "Payload cannot be null");

        String url = DISCORD_API_BASE_URL + "/channels/" + channelId;
        String jsonPayload = jsonEngine.toJsonString(payload);
        LOGGER.debug("Modifying channel {}: {}", channelId, jsonPayload);

        return restClient.patch(url, jsonPayload, Collections.emptyMap())
                .thenApply(responseBody -> {
                    LOGGER.debug("Received response for modifyChannel: {}", responseBody);
                    return jsonEngine.fromJsonString(responseBody, Channel.class);
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to modify channel {}: {}", channelId, throwable.getMessage(), throwable);
                    throw new NekoffeeException("Failed to modify channel " + channelId, throwable);
                });
    }


    public CompletableFuture<Void> editChannelPermissions(String channelId, String targetId, TargetType type,
                                                          Collection<Permission> allow, Collection<Permission> deny) {

        long allowBitmask = Permission.calculateBitmask(allow);
        long denyBitmask = Permission.calculateBitmask(deny);
        return editChannelPermissions(channelId, targetId, type, allowBitmask, denyBitmask);
    }


    @Override
    public CompletableFuture<Void> editChannelPermissions(String channelId, String targetId, TargetType type,
                                                          long allowBitmask, long denyBitmask) {
        if (!loggedIn) {
            return CompletableFuture.failedFuture(new NekoffeeException("Not logged in. Call login() first."));
        }
        Objects.requireNonNull(channelId, "Channel ID cannot be null");
        Objects.requireNonNull(targetId, "Target ID cannot be null");
        Objects.requireNonNull(type, "Target type cannot be null");

        String url = DISCORD_API_BASE_URL + "/channels/" + channelId + "/permissions/" + targetId;

        PermissionOverwritePayload payload = new PermissionOverwritePayload(
                type.getValue(),
                String.valueOf(allowBitmask),
                String.valueOf(denyBitmask)
        );
        String jsonPayload = jsonEngine.toJsonString(payload);
        LOGGER.debug("Editing permissions for target {} in channel {}: {}", targetId, channelId, jsonPayload);

        return restClient.put(url, jsonPayload, Collections.emptyMap())
                .thenAccept(responseBody -> LOGGER.info("Permissions for target {} in channel {} edited successfully.", targetId, channelId))
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to edit permissions for target {} in channel {}: {}", targetId, channelId, throwable.getMessage(), throwable);
                    throw new NekoffeeException("Failed to edit channel permissions", throwable);
                });
    }

    @Override
    public CompletableFuture<Channel> createGuildChannel(String guildId, String name, ChannelType type, @Nullable String parentCategoryId) {
        if (!loggedIn) {
            return CompletableFuture.failedFuture(new NekoffeeException("Not logged in. Call login() first."));
        }
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(name, "Channel name cannot be null");
        Objects.requireNonNull(type, "Channel type cannot be null");

        String url = DISCORD_API_BASE_URL + "/guilds/" + guildId + "/channels";
        CreateGuildChannelPayload payload = new CreateGuildChannelPayload(name, type);
        if (parentCategoryId != null) {
            payload.setParentId(parentCategoryId);
        }

        String jsonPayload = jsonEngine.toJsonString(payload);
        LOGGER.debug("Creating guild channel in {}: {}", guildId, jsonPayload);

        return restClient.post(url, jsonPayload, Collections.emptyMap())
                .thenApply(responseBody -> {
                    LOGGER.debug("Received response for createGuildChannel: {}", responseBody);
                    Channel createdChannel = jsonEngine.fromJsonString(responseBody, Channel.class);
                    createdChannel.setNekoffeeClient(this);
                    return createdChannel;
                })
                .exceptionally(throwable -> {
                    throw new NekoffeeException("createChannel failed", throwable);
                });
    }

    @Override
    public CompletableFuture<Channel> deleteChannel(String channelId) {
        if (!loggedIn) {
            return CompletableFuture.failedFuture(new NekoffeeException("Not logged in. Call login() first."));
        }
        Objects.requireNonNull(channelId, "Channel ID cannot be null");

        String url = DISCORD_API_BASE_URL + "/channels/" + channelId;
        LOGGER.debug("Deleting channel: {}", channelId);

        return restClient.delete(url, Collections.emptyMap())
                .thenApply(responseBody -> {
                    LOGGER.debug("Received response for deleteChannel: {}", responseBody);
                    Channel deletedChannel = jsonEngine.fromJsonString(responseBody, Channel.class);
                    deletedChannel.setNekoffeeClient(this);
                    return deletedChannel;
                })
                .exceptionally(throwable -> {
                    throw new NekoffeeException("deleteChannel failed", throwable);
                });
    }


    public CompletableFuture<Void> modifyGuildMemberVoiceChannel(String guildId, String userId, @Nullable String voiceChannelId) {
        if (!loggedIn) {
            return CompletableFuture.failedFuture(new NekoffeeException("Not logged in. Call login() first."));
        }
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(userId, "User ID cannot be null");


        String url = DISCORD_API_BASE_URL + "/guilds/" + guildId + "/members/" + userId;
        ModifyMemberPayload payload = new ModifyMemberPayload(voiceChannelId);
        String jsonPayload = jsonEngine.toJsonString(payload);
        LOGGER.debug("Modifying guild member {} in {}. Setting voice channel to: {}", userId, guildId, voiceChannelId);


        return restClient.patch(url, jsonPayload, Collections.emptyMap())
                .thenAccept(responseBody -> LOGGER.info("Successfully modified voice channel for member {} in guild {}.", userId, guildId))
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to modify voice channel for member {} in guild {}: {}", userId, guildId, throwable.getMessage(), throwable);
                    throw new NekoffeeException("Failed to modify member's voice channel", throwable);
                });
    }

    @Override
    public CompletableFuture<Channel> createGuildChannel(String guildId, CreateGuildChannelPayload payload) {
        if (!loggedIn) {
            return CompletableFuture.failedFuture(new NekoffeeException("Not logged in. Call login() first."));
        }
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(payload, "Payload cannot be null");

        String url = DISCORD_API_BASE_URL + "/guilds/" + guildId + "/channels";
        String jsonPayload = jsonEngine.toJsonString(payload);
        LOGGER.debug("Creating guild channel in {}: {}", guildId, jsonPayload);

        return restClient.post(url, jsonPayload, Collections.emptyMap())
                .thenApply(responseBody -> {
                    LOGGER.debug("Received response for createGuildChannel: {}", responseBody);
                    return jsonEngine.fromJsonString(responseBody, Channel.class);
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to create guild channel in {}: {}", guildId, throwable.getMessage(), throwable);
                    throw new NekoffeeException("Failed to create guild channel", throwable);
                });
    }

    @Override
    public CompletableFuture<Message> sendMessage(String channelId, String content) {
        if (!loggedIn) {
            return CompletableFuture.failedFuture(new NekoffeeException("Not logged in. Call login() first."));
        }
        Objects.requireNonNull(channelId, "Channel ID cannot be null");
        Objects.requireNonNull(content, "Message content cannot be null");
        if (content.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Message content cannot be empty."));
        }
        if (content.length() > 2000) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Message content cannot exceed 2000 characters."));
        }

        String url = DISCORD_API_BASE_URL + "/channels/" + channelId + "/messages";
        Map<String, String> payload = Collections.singletonMap("content", content);
        String jsonPayload = jsonEngine.toJsonString(payload);

        LOGGER.debug("Sending message to channel {}: {}", channelId, jsonPayload);

        return restClient.post(url, jsonPayload, Collections.emptyMap())
                .thenApply(responseBody -> {
                    LOGGER.debug("Received response for sendMessage: {}", responseBody);
                    return (Message) jsonEngine.fromJsonString(responseBody, MessageImpl.class);
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to send message to channel {}: {}", channelId, throwable.getMessage(), throwable);
                    if (throwable instanceof NekoffeeException) {
                        if (throwable.getCause() instanceof NekoffeeException)
                            throw (NekoffeeException) throwable.getCause();
                        throw new NekoffeeException("Failed to send message", throwable);
                    }
                    throw new NekoffeeException("Failed to send message", throwable);
                });
    }

    @Override
    public CompletableFuture<Message> sendMessage(String channelId, MessageSendPayload messageData) {
        if (!loggedIn) {
            return CompletableFuture.failedFuture(new NekoffeeException("Not logged in. Call login() first."));
        }
        Objects.requireNonNull(channelId, "Channel ID cannot be null");
        Objects.requireNonNull(messageData, "MessageData (from API module) cannot be null");

        String url = DISCORD_API_BASE_URL + "/channels/" + channelId + "/messages";
        String jsonPayload = jsonEngine.toJsonString(messageData);

        LOGGER.debug("Sending message (from API payload) to channel {}: {}", channelId, jsonPayload);

        return restClient.post(url, jsonPayload, Collections.emptyMap())
                .thenApply(responseBody -> {
                    LOGGER.debug("Received response for sendMessage (from API payload): {}", responseBody);
                    return (Message) jsonEngine.fromJsonString(responseBody, MessageImpl.class);
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to send message (from API payload) to channel {}: {}", channelId, throwable.getMessage(), throwable);
                    throw new NekoffeeException("Failed to send message from API payload", throwable);
                });
    }

    @Override
    public CompletableFuture<Message> sendMessage(String channelId, MultipartBody body) {
        if (!loggedIn) {
            return CompletableFuture.failedFuture(new NekoffeeException("Not logged in. Call login() first."));
        }
        Objects.requireNonNull(channelId, "Channel ID cannot be null");
        Objects.requireNonNull(body, "MultipartBody cannot be null");

        String url = DISCORD_API_BASE_URL + "/channels/" + channelId + "/messages";

        return restClient.postMultipart(url, body, Collections.emptyMap())
                .thenApply(responseBody -> jsonEngine.fromJsonString(responseBody, Message.class))
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to send multipart message to channel {}: {}", channelId, throwable.getMessage(), throwable);
                    throw new NekoffeeException("Failed to send multipart message", throwable);
                });
    }

    @Override
    public void addEventListener(EventListener listener) {
        eventListeners.add(Objects.requireNonNull(listener, "Listener cannot be null"));
    }

    @Override
    public void removeEventListener(EventListener listener) {
        eventListeners.remove(Objects.requireNonNull(listener, "Listener cannot be null"));
    }


    @Override
    public CompletableFuture<User> getUserById(String userId) {
        if (!loggedIn) {
            return CompletableFuture.failedFuture(new NekoffeeException("Not logged in. Call login() first."));
        }
        Objects.requireNonNull(userId, "User ID cannot be null");

        String url = DISCORD_API_BASE_URL + "/users/" + userId;
        LOGGER.debug("Fetching user by ID: {}", userId);

        return restClient.get(url, Collections.emptyMap())
                .thenApply(responseBody -> {
                    LOGGER.debug("Received response for getUserById ({}): {}", userId, responseBody);
                    return (User) jsonEngine.fromJsonString(responseBody, UserImpl.class);
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to get user by ID {}: {}", userId, throwable.getMessage(), throwable);
                    throw new NekoffeeException("Failed to get user by ID " + userId, throwable);
                });
    }

    @Override
    public CompletableFuture<Channel> getChannelById(String channelId) {
        if (!loggedIn) {
            return CompletableFuture.failedFuture(new NekoffeeException("Not logged in. Call login() first."));
        }
        Objects.requireNonNull(channelId, "Channel ID cannot be null");

        String url = DISCORD_API_BASE_URL + "/channels/" + channelId;
        LOGGER.debug("Fetching channel by ID: {}", channelId);

        return restClient.get(url, Collections.emptyMap())
                .thenApply(responseBody -> {
                    LOGGER.debug("Received response for getChannelById ({}): {}", channelId, responseBody);
                    Channel fetchedChannel = jsonEngine.fromJsonString(responseBody, Channel.class);
                    fetchedChannel.setNekoffeeClient(this);
                    return fetchedChannel;
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to get channel by ID {}: {}", channelId, throwable.getMessage(), throwable);
                    throw new NekoffeeException("Failed to get channel by ID " + channelId, throwable);
                });
    }

    @Override
    public CompletableFuture<Guild> getGuildById(String guildId) {
        if (!loggedIn) {
            return CompletableFuture.failedFuture(new NekoffeeException("Not logged in. Call login() first."));
        }
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String url = DISCORD_API_BASE_URL + "/guilds/" + guildId;
        LOGGER.debug("Fetching guild by ID: {}", guildId);

        return restClient.get(url, Collections.emptyMap())
                .thenApply(responseBody -> {
                    LOGGER.debug("Received response for getGuildById ({}): {}", guildId, responseBody);
                    GuildImpl guild = jsonEngine.fromJsonString(responseBody, GuildImpl.class);

                    if (guild.getRoles() != null) {
                        guild.getRoles().forEach(role -> {
                            if (role != null) {
                                ((RoleImpl) role).setGuildId(guildId);
                            }
                        });
                    }
                    return (Guild) guild;
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to get guild by ID {}: {}", guildId, throwable.getMessage(), throwable);
                    throw new NekoffeeException("Failed to get guild by ID " + guildId, throwable);
                });
    }

    @Override
    public CompletableFuture<List<Role>> getGuildRoles(String guildId) {
        if (!loggedIn) {
            return CompletableFuture.failedFuture(new NekoffeeException("Not logged in. Call login() first."));
        }
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String url = DISCORD_API_BASE_URL + "/guilds/" + guildId + "/roles";
        LOGGER.debug("Fetching roles for guild ID: {}", guildId);

        return restClient.get(url, Collections.emptyMap())
                .thenApply(responseBody -> {
                    LOGGER.debug("Received response for getGuildRoles ({}): {}", guildId, responseBody);
                    List<RoleImpl> roleImpls = jsonEngine.fromJsonString(responseBody, new TypeReference<>() {
                    });
                    roleImpls.forEach(role -> role.setGuildId(guildId));
                    return (List<Role>) new ArrayList<Role>(roleImpls);
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to get roles for guild ID {}: {}", guildId, throwable.getMessage(), throwable);
                    throw new NekoffeeException("Failed to get roles for guild ID " + guildId, throwable);
                });
    }

    @Override
    public CompletableFuture<Member> getGuildMember(String guildId, String userId) {
        if (!loggedIn) {
            return CompletableFuture.failedFuture(new NekoffeeException("Not logged in. Call login() first."));
        }
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(userId, "User ID cannot be null");

        String url = DISCORD_API_BASE_URL + "/guilds/" + guildId + "/members/" + userId;
        LOGGER.debug("Fetching member for guild {} and user {}:", guildId, userId);

        return restClient.get(url, Collections.emptyMap())
                .thenApply(responseBody -> {
                    LOGGER.debug("Received response for getGuildMember (guild: {}, user: {}): {}", guildId, userId, responseBody);
                    MemberImpl member = jsonEngine.fromJsonString(responseBody, MemberImpl.class);
                    member.setGuildId(guildId);
                    member.setNekoffeeClient(this);
                    return (Member) member;
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to get member for guild {} and user {}: {}", guildId, userId, throwable.getMessage(), throwable);
                    throw new NekoffeeException("Failed to get member (guild: " + guildId + ", user: " + userId + ")", throwable);
                });
    }

    @Override
    public CompletableFuture<Void> addRoleToMember(String guildId, String userId, String roleId) {
        if (!loggedIn) {
            return CompletableFuture.failedFuture(new NekoffeeException("Not logged in. Call login() first."));
        }
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        String url = DISCORD_API_BASE_URL + "/guilds/" + guildId + "/members/" + userId + "/roles/" + roleId;
        LOGGER.debug("Adding role {} to member {} in guild {}", roleId, userId, guildId);

        return restClient.put(url, null, Collections.emptyMap())
                .thenAccept(responseBody -> LOGGER.info("Role {} added to member {} in guild {} successfully.", roleId, userId, guildId))
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to add role {} to member {} in guild {}: {}", roleId, userId, guildId, throwable.getMessage(), throwable);
                    throw new NekoffeeException("Failed to add role to member", throwable);
                });
    }

    @Override
    public CompletableFuture<Void> removeRoleFromMember(String guildId, String userId, String roleId) {
        if (!loggedIn) {
            return CompletableFuture.failedFuture(new NekoffeeException("Not logged in. Call login() first."));
        }
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        String url = DISCORD_API_BASE_URL + "/guilds/" + guildId + "/members/" + userId + "/roles/" + roleId;
        LOGGER.debug("Removing role {} from member {} in guild {}", roleId, userId, guildId);

        return restClient.delete(url, Collections.emptyMap())
                .thenAccept(responseBody -> LOGGER.info("Role {} removed from member {} in guild {} successfully.", roleId, userId, guildId))
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to remove role {} from member {} in guild {}: {}", roleId, userId, guildId, throwable.getMessage(), throwable);
                    throw new NekoffeeException("Failed to remove role from member", throwable);
                });
    }
}