package com.ladyluh.nekoffee.commands;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface Command {
    String getName();

    List<String> getAliases();

    String getDescription();

    String getUsage();

    boolean isGuildOnly();


    CompletableFuture<Void> execute(CommandContext ctx);
}