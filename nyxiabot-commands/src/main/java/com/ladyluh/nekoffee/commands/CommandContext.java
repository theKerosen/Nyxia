package com.ladyluh.nekoffee.commands;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.User;
import com.ladyluh.nekoffee.cache.VoiceStateCacheManager;
import com.ladyluh.nekoffee.config.ConfigManager;
import com.ladyluh.nekoffee.database.DatabaseManager;
import com.ladyluh.nekoffee.model.gateway.MessageCreateEvent;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CommandContext {
    private final NekoffeeClient client;
    private final ConfigManager config;
    private final DatabaseManager dbManager;
    private final VoiceStateCacheManager voiceStateCacheManager;
    private final MessageCreateEvent event;
    private final List<String> args;
    private final String commandName;

    public CommandContext(NekoffeeClient client, ConfigManager config, DatabaseManager dbManager, VoiceStateCacheManager voiceStateCacheManager, MessageCreateEvent event, String commandName, List<String> args) {
        this.client = client;
        this.config = config;
        this.dbManager = dbManager;
        this.voiceStateCacheManager = voiceStateCacheManager;
        this.event = event;
        this.commandName = commandName;
        this.args = args;
    }

    public NekoffeeClient getClient() {
        return client;
    }

    public ConfigManager getConfig() {
        return config;
    }

    public DatabaseManager getDbManager() {
        return dbManager;
    }

    public VoiceStateCacheManager getVoiceStateCacheManager() {
        return voiceStateCacheManager;
    }

    public MessageCreateEvent getEvent() {
        return event;
    }

    public List<String> getArgs() {
        return args;
    }

    public String getCommandName() {
        return commandName;
    }

    public User getAuthor() {
        return event.getAuthor();
    }

    public String getChannelId() {
        return event.getChannelId();
    }

    public String getGuildId() {
        return event.getMessage().getGuildId();
    }

    public String getMessageId() {
        return event.getMessage().getId();
    }

    public String getRawContent() {
        return event.getContentRaw();
    }

    public CompletableFuture<Void> reply(String message) {
        return client.sendMessage(getChannelId(), message).thenAccept(m -> {
        });
    }
}