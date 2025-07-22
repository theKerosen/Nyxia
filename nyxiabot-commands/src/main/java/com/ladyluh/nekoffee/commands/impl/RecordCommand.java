package com.ladyluh.nekoffee.commands.impl;

import com.ladyluh.nekoffee.api.payload.permission.Permission;
import com.ladyluh.nekoffee.commands.Command;
import com.ladyluh.nekoffee.commands.CommandContext;
import com.ladyluh.nekoffee.services.AudioRecordingService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RecordCommand implements Command {
    private final AudioRecordingService recordingService;

    public RecordCommand(AudioRecordingService recordingService) {
        this.recordingService = recordingService;
    }

    @Override
    public String getName() {
        return "record";
    }

    @Override
    public List<String> getAliases() {
        return List.of("rec", "gravar");
    }

    @Override
    public String getDescription() {
        return "Grava o áudio de um canal de voz.";
    }

    @Override
    public String getUsage() {
        return "record <start/stop>";
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (ctx.getArgs().isEmpty() || ctx.getArgs().getFirst().isBlank()) {
            return ctx.reply("Uso: `" + getUsage() + "`");
        }

        return ctx.getClient().getGuildMember(ctx.getGuildId(), ctx.getAuthor().getId())
                .thenCompose(member -> {
                    if (member == null) {
                        return ctx.reply("Não foi possível encontrar suas informações neste servidor.");
                    }
                    return member.hasPermission(Permission.MANAGE_CHANNELS)
                            .thenCompose(hasPerms -> {
                                if (!hasPerms) {
                                    return ctx.reply("❌ Você precisa da permissão de 'Gerenciar Canais' para usar este comando.");
                                }

                                String voiceChannelId = ctx.getVoiceStateCacheManager().getUserVoiceChannelId(ctx.getGuildId(), member.getId());
                                if (voiceChannelId == null) {
                                    return ctx.reply("❌ Você precisa estar em um canal de voz para usar este comando.");
                                }

                                String subCommand = ctx.getArgs().getFirst().toLowerCase();
                                if ("start".equals(subCommand)) {
                                    return handleStart(ctx, voiceChannelId);
                                } else if ("stop".equals(subCommand)) {
                                    return handleStop(ctx, voiceChannelId);
                                } else {
                                    return ctx.reply("Subcomando inválido. Use `start` ou `stop`.");
                                }
                            });
                });
    }

    private CompletableFuture<Void> handleStart(CommandContext ctx, String voiceChannelId) {
        return ctx.getDbManager().getGuildConfig(ctx.getGuildId())
                .thenCompose(configOpt -> {
                    String recordingsChannelId = configOpt.map(config -> config.recordingsChannelId).orElse(null);
                    if (recordingsChannelId == null || recordingsChannelId.isEmpty()) {
                        return ctx.reply("❌ O canal de gravações não foi configurado. Use `!config set recordings_channel_id #canal`.");
                    }

                    return recordingService.startRecording(ctx.getGuildId(), voiceChannelId, recordingsChannelId)
                            .thenCompose(v -> ctx.reply("▶️ Gravação iniciada no canal <#" + voiceChannelId + ">. Use `!record stop` para parar."))
                            .exceptionally(ex -> {
                                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                                ctx.reply("⚠️ Erro ao iniciar a gravação: " + cause.getMessage());
                                return null;
                            });
                });
    }

    private CompletableFuture<Void> handleStop(CommandContext ctx, String voiceChannelid) {
        ctx.reply("⏹️ Gravação parada. Processando e enviando os arquivos...");
        return recordingService.stopRecording(ctx.getGuildId(), voiceChannelid)
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    ctx.reply("⚠️ Erro ao parar a gravação: " + cause.getMessage());
                    return null;
                });
    }
}