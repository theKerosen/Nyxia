package com.ladyluh.nekoffee.listeners;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.User;
import com.ladyluh.nekoffee.api.event.Event;
import com.ladyluh.nekoffee.api.event.EventListener;
import com.ladyluh.nekoffee.builder.EmbedBuilder;
import com.ladyluh.nekoffee.builder.MessageBuilder;
import com.ladyluh.nekoffee.commands.CommandManager;
import com.ladyluh.nekoffee.database.DatabaseManager;
import com.ladyluh.nekoffee.model.gateway.MessageCreateEvent;
import com.ladyluh.nekoffee.services.XPRoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class MessageEventListener implements EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageEventListener.class);
    private static final long XP_COOLDOWN_MILLIS = 60 * 1000;
    private static final int XP_MIN_PER_MESSAGE = 15;
    private static final int XP_MAX_PER_MESSAGE = 30;
    private final NekoffeeClient client;
    private final DatabaseManager dbManager;
    private final String commandPrefix;
    private final CommandManager commandManager;
    private final XPRoleService xpRoleService;

    public MessageEventListener(NekoffeeClient client, DatabaseManager dbManager, CommandManager commandManager, XPRoleService xpRoleService) {

        this.client = client;
        this.dbManager = dbManager;
        this.commandPrefix = "!";
        this.commandManager = commandManager;
        this.xpRoleService = xpRoleService;

    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof MessageCreateEvent mcEvent) {
            handleMessageCreate(mcEvent);
        }
    }

    private void handleMessageCreate(MessageCreateEvent event) {
        User author = event.getAuthor();
        String content = event.getContentRaw();
        String guildId = event.getMessage().getGuildId();
        String channelId = event.getChannelId();

        if (author == null || author.isBot()) {
            return;
        }

        String guildIdInfo = guildId != null ? " (Guild: " + guildId + ")" : " (DM)";
        LOGGER.debug("Mensagem de {}{}: {}", author.getAsTag(), guildIdInfo, content);

        if (guildId != null) {
            dbManager.getUserXP(guildId, author.getId())
                    .thenAccept(currentXP -> {
                        long now = System.currentTimeMillis();

                        if (now - currentXP.getLastMessageTimestamp() > XP_COOLDOWN_MILLIS) {
                            int xpGained = ThreadLocalRandom.current().nextInt(XP_MIN_PER_MESSAGE, XP_MAX_PER_MESSAGE + 1);
                            int oldLevel = currentXP.getLevel();
                            currentXP.setXp(currentXP.getXp() + xpGained);
                            currentXP.setLastMessageTimestamp(now);

                            int xpForNextLevel = calculateXpForLevel(currentXP.getLevel() + 1);
                            boolean leveledUp;

                            if (currentXP.getXp() >= xpForNextLevel) {
                                currentXP.setLevel(currentXP.getLevel() + 1);
                                currentXP.setXp(currentXP.getXp() - xpForNextLevel);
                                leveledUp = true;
                            } else {
                                leveledUp = false;
                            }

                            dbManager.updateUserXP(currentXP.getGuildId(), currentXP.getUserId(), currentXP.getXp(), currentXP.getLevel(), currentXP.getLastMessageTimestamp())
                                    .thenRun(() -> LOGGER.debug("{} ganhou {} XP. Total: {}, Nível: {}", author.getAsTag(), xpGained, currentXP.getXp(), currentXP.getLevel()))
                                    .thenCompose(v -> {
                                        if (leveledUp) {
                                            sendLevelUpMessage(channelId, author, currentXP.getLevel());
                                            return xpRoleService.assignXPRoles(guildId, author.getId(), oldLevel, currentXP.getLevel());
                                        }
                                        return CompletableFuture.completedFuture(null);
                                    })
                                    .exceptionally(ex -> {
                                        LOGGER.error("Erro na lógica de XP para usuário {}:", author.getAsTag(), ex);
                                        return null;
                                    });
                        }
                    })
                    .exceptionally(ex -> {
                        LOGGER.error("Erro ao buscar/processar XP inicial para usuário {}:", author.getAsTag(), ex);
                        return null;
                    });
        }

        if (content.startsWith(commandPrefix)) {
            String commandLine = content.substring(commandPrefix.length()).trim();
            if (commandLine.isEmpty()) return;

            String[] parts = commandLine.split("\\s+", 2);
            String commandName = parts[0].toLowerCase();
            String argsString = parts.length > 1 ? parts[1] : "";
            List<String> argsList = Arrays.asList(argsString.split("\\s+"));

            commandManager.handleCommand(commandName, argsList, event);
        }
    }

    private int calculateXpForLevel(int level) {
        if (level <= 0) return 100;
        return (level * level * 50) + (level * 100) + 100;
    }

    private void sendLevelUpMessage(String channelId, User user, int newLevel) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎉 PARABÉNS, " + escapeMarkdown(user.getGlobalName()))
                .setDescription("Você alcançou o **Nível " + newLevel + "**!")
                .setColor(new Color(0xFFD700))
                .setThumbnail(user.getEffectiveAvatarUrl())
                .addField("Próximo Nível", calculateXpForLevel(newLevel + 1) + " XP", true)
                .setTimestamp(OffsetDateTime.now());

        client.sendMessage(channelId, new MessageBuilder().addEmbed(embed).build())
                .exceptionally(ex -> {
                    LOGGER.error("Erro ao enviar mensagem de level up para {}:", user.getAsTag(), ex);
                    return null;
                });
    }

    private static String escapeMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder escapedText = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '*':   
                case '_':   
                case '~':   
                case '`':   
                case '|':   
                case '[':   
                case ']':   
                case '(':   
                case ')':   
                case '\\':  
                    escapedText.append('\\');
                    escapedText.append(c);
                    break;
                default:
                    escapedText.append(c);
                    break;
            }
        }
        return escapedText.toString();
    }
}