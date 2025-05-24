package com.ladyluh.nekoffee.commands.impl;

import com.ladyluh.nekoffee.builder.EmbedBuilder;
import com.ladyluh.nekoffee.builder.MessageBuilder;
import com.ladyluh.nekoffee.commands.Command;
import com.ladyluh.nekoffee.commands.CommandContext;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TestCommand implements Command {
    @Override
    public String getName() {
        return "test";
    }

    @Override
    public List<String> getAliases() {
        return List.of("teste");
    }

    @Override
    public String getDescription() {
        return "Um comando de teste para a Nekoffee.";
    }

    @Override
    public String getUsage() {
        return "test [args]";
    }

    @Override
    public boolean isGuildOnly() {
        return false;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        String args = String.join(" ", ctx.getArgs());
        EmbedBuilder testEmbed = new EmbedBuilder()
                .setTitle("☕ Comando de Teste da Nekoffee!")
                .setDescription("Você executou o comando de teste, " + ctx.getAuthor().getAsTag() + "!")
                .setColor(Color.MAGENTA)
                .addField("Argumentos Recebidos:", args.isEmpty() ? "*Nenhum*" : args, false)
                .setFooter("Executado por: " + ctx.getClient().getSelfUser().getAsTag(), ctx.getClient().getSelfUser().getEffectiveAvatarUrl())
                .setTimestamp(OffsetDateTime.now());

        MessageBuilder responseBuilder = new MessageBuilder().addEmbed(testEmbed);
        return ctx.getClient().sendMessage(ctx.getChannelId(), responseBuilder.build()).thenAccept(m -> {
        });
    }
}