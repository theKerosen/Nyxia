package com.ladyluh.nekoffee.commands.impl;

import com.ladyluh.nekoffee.api.entities.User;
import com.ladyluh.nekoffee.builder.EmbedBuilder;
import com.ladyluh.nekoffee.builder.MessageBuilder;
import com.ladyluh.nekoffee.commands.Command;
import com.ladyluh.nekoffee.commands.CommandContext;
import com.ladyluh.nekoffee.database.DatabaseManager;
import com.ladyluh.nekoffee.database.UserXP;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class XPCommand implements Command {
    private final DatabaseManager dbManager;

    public XPCommand(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public String getName() {
        return "xp";
    }

    @Override
    public List<String> getAliases() {
        return List.of("level");
    }

    @Override
    public String getDescription() {
        return "Mostra seu status de XP ou o de outro membro.";
    }

    @Override
    public String getUsage() {
        return "xp [@membro] / xp top";
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (Objects.equals(ctx.getArgs().getFirst(), "")) {
            return handleXPStatus(ctx, ctx.getAuthor());
        } else if (ctx.getArgs().getFirst().equalsIgnoreCase("top")) {
            return handleXPLeaderboard(ctx);
        } else if (ctx.getArgs().getFirst().matches("<@!?[0-9]+>")) {
            String mentionedUserId = ctx.getArgs().getFirst().replaceAll("[<@!>]", "");
            return ctx.getClient().getUserById(mentionedUserId)
                    .thenCompose(user -> {
                        if (user == null) {
                            return ctx.reply("Não consegui encontrar esse usuário.");
                        }
                        return handleXPStatus(ctx, user);
                    });
        } else {
            return ctx.reply("Uso inválido. Use `!xp [@membro]` ou `!xp top`.");
        }
    }

    private CompletableFuture<Void> handleXPStatus(CommandContext ctx, User targetUser) {
        String guildId = ctx.getGuildId();
        if (guildId == null) return ctx.reply("Este comando requer estar em um servidor.");

        return dbManager.getUserXP(guildId, targetUser.getId()).thenCompose(userXP -> {
            int xpRemainingForNextLevel = calculateXpForLevel(userXP.getLevel() + 1) - userXP.getXp();
            String xpStatus = String.format("Nível **%d** (XP: %d/%d)", userXP.getLevel(), userXP.getXp(), calculateXpForLevel(userXP.getLevel() + 1));

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📊 Status de XP de " + targetUser.getUsername())
                    .setDescription(xpStatus)
                    .setColor(Color.BLUE)
                    .setThumbnail(targetUser.getEffectiveAvatarUrl())
                    .addField("XP Faltando para o Próximo Nível", xpRemainingForNextLevel + " XP", true)
                    .setTimestamp(OffsetDateTime.now());

            return ctx.getClient().sendMessage(ctx.getChannelId(), new MessageBuilder().addEmbed(embed).build()).thenAccept(m -> {
            });
        });
    }

    private CompletableFuture<Void> handleXPLeaderboard(CommandContext ctx) {
        String guildId = ctx.getGuildId();
        if (guildId == null) return ctx.reply("Este comando requer estar em um servidor.");

        return dbManager.getTopXPUsers(guildId, 10).thenCompose(topUsers -> {

            if (topUsers.isEmpty()) {
                return ctx.reply("Ninguém ganhou XP ainda neste servidor!");
            }

            StringBuilder leaderboardDesc = new StringBuilder();

            List<CompletableFuture<String>> userNamesFutures = topUsers.stream()
                    .map(userXP -> ctx.getClient().getUserById(userXP.getUserId())
                            .thenApply(user -> user != null ? user.getAsTag() : "Usuário Desconhecido"))
                    .toList();


            return CompletableFuture.allOf(userNamesFutures.toArray(new CompletableFuture[0]))
                    .thenCompose(v -> {
                        for (int i = 0; i < topUsers.size(); i++) {
                            UserXP userXP = topUsers.get(i);
                            String username = userNamesFutures.get(i).join();
                            leaderboardDesc.append(String.format("**%d. %s** - Nível %d (XP: %d)\n", i + 1, username, userXP.getLevel(), userXP.getXp()));
                        }

                        EmbedBuilder embed = new EmbedBuilder()
                                .setTitle("🏆 Ranking de XP do Servidor 🏆")
                                .setDescription(leaderboardDesc.toString())
                                .setColor(Color.YELLOW)
                                .setFooter("Nekoffee XP Leaderboard", ctx.getClient().getSelfUser().getEffectiveAvatarUrl())
                                .setTimestamp(OffsetDateTime.now());

                        return ctx.getClient().sendMessage(ctx.getChannelId(), new MessageBuilder().addEmbed(embed).build()).thenAccept(m -> {
                        });
                    });
        });
    }


    private int calculateXpForLevel(int level) {
        if (level <= 0) return 100;
        return (level * level * 50) + (level * 100) + 100;
    }
}