package com.ladyluh.nekoffee.api;

import com.ladyluh.nekoffee.api.entities.*;
import com.ladyluh.nekoffee.api.entities.channel.Channel;
import com.ladyluh.nekoffee.api.entities.channel.ChannelType;
import com.ladyluh.nekoffee.api.event.EventListener;
import com.ladyluh.nekoffee.api.gateway.GatewayIntent;
import com.ladyluh.nekoffee.api.payload.channel.ChannelModifyPayload;
import com.ladyluh.nekoffee.api.payload.channel.CreateGuildChannelPayload;
import com.ladyluh.nekoffee.api.payload.permission.Permission;
import com.ladyluh.nekoffee.api.payload.send.MessageSendPayload;
import com.ladyluh.nekoffee.api.voice.VoiceConnection;
import okhttp3.MultipartBody;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface NekoffeeClient {

    User getSelfUser();

    CompletableFuture<Void> login(String token, Collection<GatewayIntent> intents);

    void shutdown();

    CompletableFuture<Message> sendMessage(String channelId, String content);

    void addEventListener(EventListener listener);

    void removeEventListener(EventListener listener);

    CompletableFuture<User> getUserById(String userId);

    CompletableFuture<Channel> getChannelById(String channelId);

    CompletableFuture<Guild> getGuildById(String guildId);

    CompletableFuture<List<Role>> getGuildRoles(String guildId);

    CompletableFuture<Member> getGuildMember(String guildId, String userId);

    CompletableFuture<Void> addRoleToMember(String guildId, String userId, String roleId);

    CompletableFuture<Void> removeRoleFromMember(String guildId, String userId, String roleId);

    CompletableFuture<Message> sendMessage(String channelId, MessageSendPayload messageData);

    CompletableFuture<Channel> createGuildChannel(String guildId, CreateGuildChannelPayload payload);

    CompletableFuture<Channel> createGuildChannel(String guildId, String name, ChannelType type, @Nullable String parentCategoryId);

    CompletableFuture<Channel> deleteChannel(String channelId);

    CompletableFuture<Void> modifyGuildMemberVoiceChannel(String guildId, String userId, @Nullable String voiceChannelId);

    CompletableFuture<Channel> modifyChannel(String channelId, ChannelModifyPayload payload);

    CompletableFuture<Void> editChannelPermissions(String channelId, String targetId, TargetType type, Collection<Permission> allow, Collection<Permission> deny);

    CompletableFuture<Void> editChannelPermissions(String channelId, String targetId, TargetType type, long allowBitmask, long denyBitmask);

    /**
     * Sends a message with a multipart body, typically used for file uploads.
     *
     * @param channelId The ID of the channel to send the message to.
     * @param body      The MultipartBody containing the message data and files.
     * @return A CompletableFuture containing the sent Message.
     */
    CompletableFuture<Message> sendMessage(String channelId, MultipartBody body);

    /**
     * Plays a sound from the server's soundboard in the specified voice channel.
     * The bot must already be in this voice channel.
     *
     * @param guildId   The ID of the guild.
     * @param channelId The ID of the voice channel the bot is currently in.
     * @param soundId   The ID of the soundboard sound to play.
     */
    void playSoundboardSound(String guildId, String channelId, String soundId);

    /**
     * Attempts to join a voice channel.
     *
     * @param guildId   The ID of the guild where the voice channel is located.
     * @param channelId The ID of the voice channel to join.
     * @return A CompletableFuture that completes with the VoiceConnection when established.
     * It will complete exceptionally if the client is not logged in or an error occurs during connection.
     */
    CompletableFuture<VoiceConnection> joinVoiceChannel(String guildId, String channelId);

    /**
     * Attempts to leave a voice channel in a specific guild.
     *
     * @param guildId The ID of the guild from which to leave the voice channel.
     */
    CompletableFuture<Void> leaveVoiceChannel(String guildId);

    /**
     * FIX: Overload setActivity to support all activity types, including streaming with a URL.
     *
     * @param type The type of activity (Playing, Streaming, etc.).
     * @param name The text to display for the activity.
     * @param url  The URL for streaming status (required if type is STREAMING, otherwise ignored).
     */
    void setActivity(ActivityType type, String name, @Nullable String url);

    default void setActivity(ActivityType type, String name) {
        if (type == ActivityType.STREAMING) {
            throw new IllegalArgumentException("STREAMING activity type requires a URL. Use the setActivity(type, name, url) method.");
        }
        setActivity(type, name, null);
    }

    /**
     * FIX: Add an ActivityType enum to make setting statuses type-safe.
     * Corresponds to Discord's activity type integers.
     */
    enum ActivityType {
        PLAYING(0),
        STREAMING(1),
        LISTENING(2),
        WATCHING(3),
        CUSTOM(4),
        COMPETING(5);

        private final int value;

        ActivityType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * Interface for handling audio data received from a voice channel.
     * Implementations of this are given to a VoiceConnection.
     */
    interface AudioReceiveHandler {
        /**
         * Checks if audio from a specific user should be processed.
         *
         * @param user The User whose audio is about to be received.
         * @return true if the audio should be processed, false to discard.
         */
        boolean canReceiveUser(User user);

        /**
         * Handles a 20ms packet of decoded PCM, stereo, 48kHz audio from a specific user.
         *
         * @param user    The User who spoke. This is always non-null.
         * @param pcmData The raw PCM audio data.
         */
        void handleUserAudio(User user, byte[] pcmData);

        /**
         * Called when the voice connection is terminated and the handler is shut down.
         */
        void onShutdown();
    }

}