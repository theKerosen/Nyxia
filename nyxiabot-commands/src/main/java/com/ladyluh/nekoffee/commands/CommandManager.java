package com.ladyluh.nekoffee.commands;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.commands.impl.PingCommand;
import com.ladyluh.nekoffee.commands.impl.TempChannelCommand;
import com.ladyluh.nekoffee.commands.impl.TestCommand;
import com.ladyluh.nekoffee.commands.impl.XPCommand;
import com.ladyluh.nekoffee.config.ConfigManager;
import com.ladyluh.nekoffee.database.DatabaseManager;
import com.ladyluh.nekoffee.model.gateway.MessageCreateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CommandManager {
    public static final Logger LOGGER = LoggerFactory.getLogger(CommandManager.class);
    private final Map<String, Command> commands = new HashMap<>();
    private final NekoffeeClient client;
    private final ConfigManager config;
    private final DatabaseManager dbManager;

    public CommandManager(NekoffeeClient client, ConfigManager config, DatabaseManager dbManager) {
        this.client = client;
        this.config = config;
        this.dbManager = dbManager;
        registerCommands();
    }

    private void registerCommands() {
        addCommand(new PingCommand());
        addCommand(new TestCommand());
        addCommand(new XPCommand(dbManager));
        addCommand(new TempChannelCommand(dbManager));
    }

    private void addCommand(Command command) {
        if (commands.containsKey(command.getName().toLowerCase())) {
            LOGGER.warn("Comando '{}' já registrado. Sobrescrevendo.", command.getName());
        }
        commands.put(command.getName().toLowerCase(), command);
        for (String alias : command.getAliases()) {
            if (commands.containsKey(alias.toLowerCase())) {
                LOGGER.warn("Alias '{}' para comando '{}' já registrado. Sobrescrevendo.", alias, command.getName());
            }
            commands.put(alias.toLowerCase(), command);
        }
        LOGGER.info("Comando '{}' e aliases {} registrados.", command.getName(), command.getAliases());
    }

    public void handleCommand(String commandName, List<String> args, MessageCreateEvent event) {
        Command command = commands.get(commandName.toLowerCase());
        if (command == null) {
            LOGGER.debug("Comando '{}' não reconhecido.", commandName);
            CompletableFuture.completedFuture(null);
            return;
        }

        if (command.isGuildOnly() && event.getMessage().getGuildId() == null) {
            client.sendMessage(event.getChannelId(), "Este comando só pode ser usado em um servidor.").thenAccept(m -> {
            });
            return;
        }

        CommandContext ctx = new CommandContext(client, config, dbManager, event, commandName, args);
        LOGGER.info("Executando comando '{}' para o usuário '{}' com args: {}", command.getName(), ctx.getAuthor().getAsTag(), args);
        command.execute(ctx)
                .exceptionally(ex -> {
                    LOGGER.error("Erro ao executar comando '{}' para o usuário '{}':", command.getName(), ctx.getAuthor().getAsTag(), ex);
                    ctx.reply("Ocorreu um erro ao executar este comando.").exceptionally(e -> {
                        LOGGER.error("Falha ao enviar mensagem de erro de comando:", e);
                        return null;
                    });
                    return null;
                });
    }
}