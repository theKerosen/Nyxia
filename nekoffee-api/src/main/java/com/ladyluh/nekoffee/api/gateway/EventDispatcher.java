package com.ladyluh.nekoffee.api.gateway;

import com.ladyluh.nekoffee.api.event.Event;
import org.jetbrains.annotations.Nullable;

public interface EventDispatcher {
    void dispatch(Event event);

    /**
     * Internal-only method to handle leaving a voice channel.
     * Renamed to avoid conflict with the public API method in NekoffeeClient.
     *
     * @param guildId The ID of the guild from which to leave.
     */
    void _internal_leaveVoiceChannel(String guildId); 

    void sendVoiceStateUpdate(String guildId, @Nullable String channelId, boolean selfMute, boolean selfDeaf);
}