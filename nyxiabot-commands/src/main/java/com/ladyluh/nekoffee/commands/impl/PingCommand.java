package com.ladyluh.nekoffee.commands.impl;

import com.ladyluh.nekoffee.commands.Command;
import com.ladyluh.nekoffee.commands.CommandContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PingCommand implements Command {
    @Override
    public String getName() {
        return "ping_nekoffee";
    }

    @Override
    public List<String> getAliases() {
        return List.of("ping");
    }

    @Override
    public String getDescription() {
        return "Responde com Pong da Nekoffee!";
    }

    @Override
    public String getUsage() {
        return "ping";
    }

    @Override
    public boolean isGuildOnly() {
        return false;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        return ctx.getClient().sendMessage(ctx.getChannelId(), "Pong da Nekoffee! ☕️").thenAccept(m -> {
        });
    }
}