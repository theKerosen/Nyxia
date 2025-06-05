package com.ladyluh.nekoffee;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.gateway.GatewayIntent;
import com.ladyluh.nekoffee.cache.VoiceStateCacheManager;
import com.ladyluh.nekoffee.commands.CommandManager;
import com.ladyluh.nekoffee.config.ConfigManager;
import com.ladyluh.nekoffee.database.DatabaseManager;
import com.ladyluh.nekoffee.listeners.GuildEventListener;
import com.ladyluh.nekoffee.listeners.LogEventListener;
import com.ladyluh.nekoffee.listeners.MessageEventListener;
import com.ladyluh.nekoffee.listeners.TemporaryChannelListener;
import com.ladyluh.nekoffee.services.XPRoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

public class NyxiaBot {
    private static final Logger LOGGER = LoggerFactory.getLogger(NyxiaBot.class);
    private final ConfigManager config;
    private final NekoffeeClient nekoffeeClient;
    private final DatabaseManager databaseManager;
    private final CommandManager commandManager;
    private final VoiceStateCacheManager voiceStateCacheManager;
    private final XPRoleService xpRoleService;


    public NyxiaBot() throws Exception {
        this.config = new ConfigManager();
        this.nekoffeeClient = Nekoffee.createDefault();
        this.databaseManager = new DatabaseManager("nyxiabot.db");
        this.commandManager = new CommandManager(nekoffeeClient, config, databaseManager); // Passa config
        this.voiceStateCacheManager = new VoiceStateCacheManager();
        this.xpRoleService = new XPRoleService(nekoffeeClient, config); // Passa config
        setupListeners();
    }

    private void setupListeners() {
        nekoffeeClient.addEventListener(new GuildEventListener(config, nekoffeeClient, databaseManager));
        nekoffeeClient.addEventListener(new LogEventListener(config, nekoffeeClient, databaseManager));
        nekoffeeClient.addEventListener(new MessageEventListener(nekoffeeClient, databaseManager, commandManager, xpRoleService));
        nekoffeeClient.addEventListener(new TemporaryChannelListener(config, nekoffeeClient, databaseManager, voiceStateCacheManager));
    }

    public void start() {
        EnumSet<GatewayIntent> intents = EnumSet.of(
                GatewayIntent.GUILDS,
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT,
                GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_VOICE_STATES
        );

        LOGGER.info("Iniciando NyxiaBot...");
        nekoffeeClient.login(config.getBotToken(), intents)
                .thenRun(() -> LOGGER.info("NyxiaBot conectado ao Gateway e PRONTO!"))
                .exceptionally(throwable -> {
                    LOGGER.error("Falha ao iniciar o NyxiaBot:", throwable);
                    return null;
                });


        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Desligando NyxiaBot...");
            nekoffeeClient.shutdown();
            databaseManager.shutdown();
            LOGGER.info("NyxiaBot desligado.");
        }));
    }

    public static void main(String[] args) {
        try {
            NyxiaBot bot = new NyxiaBot();
            bot.start();


        } catch (Exception e) {
            LOGGER.error("Erro fatal ao inicializar o NyxiaBot:", e);
        }
    }
}