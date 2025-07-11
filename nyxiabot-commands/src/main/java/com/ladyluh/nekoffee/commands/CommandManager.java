package com.ladyluh.nekoffee.commands;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.cache.VoiceStateCacheManager;
import com.ladyluh.nekoffee.commands.impl.*;
import com.ladyluh.nekoffee.config.ConfigManager;
import com.ladyluh.nekoffee.database.DatabaseManager;
import com.ladyluh.nekoffee.model.gateway.MessageCreateEvent;
import com.ladyluh.nekoffee.services.AudioRecordingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandManager {
    public static final Logger LOGGER = LoggerFactory.getLogger(CommandManager.class);
    private final Map<String, Command> commands = new HashMap<>();
    private final NekoffeeClient client;
    private final ConfigManager config;
    private final DatabaseManager dbManager;
    private final AudioRecordingService audioRecordingService;
    private final VoiceStateCacheManager voiceStateCacheManager;

    public CommandManager(NekoffeeClient client, ConfigManager config, DatabaseManager dbManager, AudioRecordingService audioRecordingService, VoiceStateCacheManager voiceStateCacheManager) {
        this.client = client;
        this.config = config;
        this.dbManager = dbManager;
        this.audioRecordingService = audioRecordingService;
        this.voiceStateCacheManager = voiceStateCacheManager;
        registerCommands();
    }

    private void registerCommands() {
        addCommand(new PingCommand());
        addCommand(new TestCommand());
        addCommand(new XPCommand(dbManager));
        addCommand(new TempChannelCommand(dbManager));
        addCommand(new ConfigCommand(dbManager));
        addCommand(new RecordCommand(audioRecordingService));
    }

    private void addCommand(Command command) {
        commands.put(command.getName().toLowerCase(), command);
        for (String alias : command.getAliases()) {
            commands.put(alias.toLowerCase(), command);
        }
        LOGGER.info("Comando '{}' e aliases {} registrados.", command.getName(), command.getAliases());
    }

    public void handleCommand(String commandName, List<String> args, MessageCreateEvent event) {
        Command command = commands.get(commandName.toLowerCase());
        if (command == null) {
            return;
        }

        if (command.isGuildOnly() && event.getMessage().getGuildId() == null) {
            client.sendMessage(event.getChannelId(), "Este comando só pode ser usado em um servidor.");
            return;
        }


        CommandContext ctx = new CommandContext(client, config, dbManager, voiceStateCacheManager, event, commandName, args);
        LOGGER.info("Executando comando '{}' para o usuário '{}' com args: {}", command.getName(), ctx.getAuthor().getAsTag(), args);

        command.execute(ctx).exceptionally(ex -> {
            LOGGER.error("Erro ao executar comando '{}' para o usuário '{}':", command.getName(), ctx.getAuthor().getAsTag(), ex);

            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            ctx.reply("Ocorreu um erro ao executar este comando: " + cause.getMessage()).exceptionally(e -> {
                LOGGER.error("Falha ao enviar mensagem de erro de comando:", e);
                return null;
            });
            return null;
        });
    }
}