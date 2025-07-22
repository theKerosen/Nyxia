package com.ladyluh.nekoffee;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.gateway.GatewayIntent;
import com.ladyluh.nekoffee.cache.VoiceStateCacheManager;
import com.ladyluh.nekoffee.commands.CommandManager;
import com.ladyluh.nekoffee.config.ConfigManager;
import com.ladyluh.nekoffee.database.DatabaseManager;
import com.ladyluh.nekoffee.json.util.JsonEngine;
import com.ladyluh.nekoffee.json.util.impl.JacksonJsonEngineImpl;
import com.ladyluh.nekoffee.listeners.GuildEventListener;
import com.ladyluh.nekoffee.listeners.LogEventListener;
import com.ladyluh.nekoffee.listeners.MessageEventListener;
import com.ladyluh.nekoffee.listeners.TemporaryChannelListener;
import com.ladyluh.nekoffee.services.AudioRecordingService;
import com.ladyluh.nekoffee.services.XPRoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class NyxiaBot {
    private static final Logger LOGGER = LoggerFactory.getLogger(NyxiaBot.class);
    private final ConfigManager config;
    private final NekoffeeClient nekoffeeClient;
    private final DatabaseManager databaseManager;
    private final CommandManager commandManager;
    private final VoiceStateCacheManager voiceStateCacheManager;
    private final XPRoleService xpRoleService;
    private final ScheduledExecutorService statusRotator;

    public NyxiaBot() throws Exception {
        this.config = new ConfigManager();
        this.nekoffeeClient = Nekoffee.createDefault();
        this.databaseManager = new DatabaseManager("nyxiabot.db");
        this.voiceStateCacheManager = new VoiceStateCacheManager();
        JsonEngine jsonEngine = new JacksonJsonEngineImpl();

        this.xpRoleService = new XPRoleService(nekoffeeClient, config);
        AudioRecordingService audioRecordingService = new AudioRecordingService(nekoffeeClient, jsonEngine, this.voiceStateCacheManager);

        this.commandManager = new CommandManager(nekoffeeClient, config, databaseManager, audioRecordingService, voiceStateCacheManager);
        this.statusRotator = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Nekoffee-Status-Rotator");
            t.setDaemon(true);
            return t;
        });
        setupListeners();
    }

    public static void main(String[] args) {
        try {
            NyxiaBot bot = new NyxiaBot();
            bot.start();
        } catch (Exception e) {
            LOGGER.error("Erro fatal ao inicializar o NyxiaBot:", e);
        }
    }

    private void setupListeners() {
        nekoffeeClient.addEventListener(new GuildEventListener(nekoffeeClient, databaseManager));
        nekoffeeClient.addEventListener(new LogEventListener(config, nekoffeeClient, databaseManager));
        nekoffeeClient.addEventListener(new MessageEventListener(nekoffeeClient, databaseManager, commandManager, xpRoleService));

        TemporaryChannelListener tempListener = new TemporaryChannelListener(config, nekoffeeClient, databaseManager, voiceStateCacheManager);
        nekoffeeClient.addEventListener(tempListener);
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
                .thenRun(() -> {
                    LOGGER.info("NyxiaBot conectado ao Gateway e PRONTO!");
                    startStatusRotation();
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Falha ao iniciar o NyxiaBot:", throwable);
                    return null;
                });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Desligando NyxiaBot...");
            if (statusRotator != null && !statusRotator.isShutdown()) {
                statusRotator.shutdownNow();
            }
            nekoffeeClient.shutdown();
            databaseManager.shutdown();
            LOGGER.info("NyxiaBot desligado.");
        }));
    }

    private void startStatusRotation() {
        final List<String> statuses = config.getBotStatuses();
        if (statuses.isEmpty()) {
            LOGGER.warn("Nenhum status de bot configurado em BOT_STATUSES. Rotação de status desativada.");
            return;
        }
        final AtomicInteger index = new AtomicInteger(0);

        statusRotator.scheduleAtFixedRate(() -> {
            try {
                String statusText = statuses.get(index.getAndIncrement() % statuses.size());
                nekoffeeClient.setActivity(NekoffeeClient.ActivityType.PLAYING, statusText);
                LOGGER.debug("Status do bot alterado para: Playing {}", statusText);
            } catch (Exception e) {
                LOGGER.error("Falha ao rotacionar status do bot", e);
            }
        }, 5, 25, TimeUnit.SECONDS);
    }
}