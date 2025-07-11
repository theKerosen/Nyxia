package com.ladyluh.nekoffee.services;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.payload.send.MessageSendPayload;
import com.ladyluh.nekoffee.builder.EmbedBuilder;
import com.ladyluh.nekoffee.builder.MessageBuilder;
import com.ladyluh.nekoffee.json.util.JsonEngine;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator;

import java.awt.Color;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AudioRecordingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioRecordingService.class);
    private final NekoffeeClient client;
    private final JsonEngine jsonEngine;
    private final ConcurrentHashMap<String, RecordingSession> activeSessions = new ConcurrentHashMap<>();
    private static final long TARGET_FRAGMENT_SIZE = (long) (8 * 1024 * 1024 * 0.95);

    public AudioRecordingService(NekoffeeClient client, JsonEngine jsonEngine) {
        this.client = client;
        this.jsonEngine = jsonEngine;
    }

    public CompletableFuture<Void> startRecording(String guildId, String channelId, String recordingsChannelId) {
        if (activeSessions.containsKey(guildId)) {
            return CompletableFuture.failedFuture(new IllegalStateException("A recording is already in progress for this guild."));
        }

        try {
            RecordingSession session = new RecordingSession(guildId, recordingsChannelId);
            AudioReceiver receiver = new AudioReceiver(session.mixer);
            session.mixer.start();

            return client.joinVoiceChannel(guildId, channelId)
                    .thenAccept(connection -> {
                        connection.setReceivingHandler(receiver);
                        activeSessions.put(guildId, session);
                        LOGGER.info("Successfully joined channel {} and started recording for guild {}.", channelId, guildId);
                    })
                    .exceptionally(ex -> {
                        LOGGER.error("Failed to establish full voice connection for guild {}: {}", guildId, ex.getMessage(), ex);
                        session.cleanup();
                        throw new RuntimeException("Failed to establish voice connection: " + ex.getMessage(), ex);
                    });
        } catch (IOException e) {
            LOGGER.error("Error creating recording session for guild {}: {}", guildId, e.getMessage(), e);
            return CompletableFuture.failedFuture(new RuntimeException("Failed to prepare recording directory.", e));
        }
    }

    public CompletableFuture<Void> stopRecording(String guildId, String channelId) {
        RecordingSession session = activeSessions.remove(guildId);
        if (session == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("No active recording session found for this guild."));
        }

        LOGGER.info("Stopping recording for guild {}. Disconnecting from voice channel...", guildId);

        return client.leaveVoiceChannel(guildId).thenRunAsync(() -> {
            LOGGER.info("Voice disconnect complete. Finalizing files for guild {}.", guildId);
            try {
                File mixedPcmFile = session.getMixedPcmFile();
                if (!mixedPcmFile.exists() || mixedPcmFile.length() == 0) {
                    LOGGER.warn("No audio was recorded for session {}. Nothing to upload.", session.recordingName);
                    client.sendMessage(session.recordingsChannelId, "⏹️ Gravação parada. Nenhum áudio foi capturado.");
                    return;
                }

                LocalDateTime dataHoje = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                client.sendMessage(session.recordingsChannelId, "⏹️ Gravação do dia `" + dataHoje.format(formatter) + "` no canal `#" + client.getChannelById(channelId).get().getName() + "`");

                File finalOggFile = session.encodeToOgg(mixedPcmFile);

                if (finalOggFile.exists() && finalOggFile.length() > 0) {
                    if (finalOggFile.length() > TARGET_FRAGMENT_SIZE) {
                        // File is too large, fragment it.
                        client.sendMessage(session.recordingsChannelId, "⚠️ O arquivo de áudio é muito grande. Dividindo em várias partes...");
                        try {
                            List<File> fragments = session.splitAudioFile(finalOggFile);
                            for (File fragment : fragments) {
                                uploadFile(session.recordingsChannelId, fragment, fragment.getName());
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to fragment large audio file for guild {}", guildId, e);
                            client.sendMessage(session.recordingsChannelId, "❌ Ocorreu um erro ao dividir o arquivo de áudio.");
                        }
                    } else {
                        // File is small enough, upload directly.
                        uploadFile(session.recordingsChannelId, finalOggFile, finalOggFile.getName());
                    }
                } else {
                    LOGGER.warn("Final OGG file for guild {} is empty or does not exist. Skipping upload.", guildId);
                }

            } catch (Exception e) {
                LOGGER.error("Failed to process recording for guild {}", guildId, e);
                client.sendMessage(session.recordingsChannelId, "❌ Ocorreu um erro crítico ao processar o áudio. Verifique os logs.");
            } finally {
                session.cleanup();
            }
        });
    }

    private void uploadFile(String channelId, File file, String filename) {
        String title = filename.contains("-part-")
                ? "🎙️ Gravação (Parte " + filename.replaceAll(".*-part-([0-9]+)-of-.*", "$1") + ")"
                : "🎙️ Gravação Concluída";

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription("Arquivo: `" + filename + "`")
                .setColor(Color.CYAN)
                .setTimestamp(java.time.OffsetDateTime.now());

        try {
            LOGGER.info("Uploading file: {}", filename);
            MessageSendPayload messagePayload = new MessageBuilder().addEmbed(embed).build();
            String payloadJsonString = jsonEngine.toJsonString(messagePayload);

            MultipartBody body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("payload_json", payloadJsonString)
                    .addFormDataPart("files[0]", filename, RequestBody.create(file, MediaType.parse("audio/ogg")))
                    .build();
            client.sendMessage(channelId, body).join();
            LOGGER.info("Successfully uploaded file {}", filename);
        } catch (Exception e) {
            LOGGER.error("Failed to upload file {}", filename, e);
            client.sendMessage(channelId, "❌ Falha ao enviar o arquivo final: `" + filename + "`");
        }
    }


    private static class RecordingSession {
        final String guildId;
        final String recordingsChannelId;
        final Path recordingDir;
        final String recordingName;
        final TimedAudioMixer mixer;
        final File mixedPcmFile;

        RecordingSession(String guildId, String recordingsChannelId) throws IOException {
            this.guildId = guildId;
            this.recordingsChannelId = recordingsChannelId;
            this.recordingName = "rec-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            this.recordingDir = Path.of("recordings", guildId, recordingName);
            Files.createDirectories(recordingDir);

            this.mixedPcmFile = recordingDir.resolve("mixed_audio.pcm").toFile();
            this.mixer = new TimedAudioMixer(mixedPcmFile);
        }

        public File getMixedPcmFile() {
            return mixedPcmFile;
        }

        public File encodeToOgg(File sourcePcm) throws Exception {
            File targetOgg = recordingDir.resolve(recordingName + ".ogg").toFile();
            LOGGER.info("Encoding {} to {}...", sourcePcm.getName(), targetOgg.getName());

            DefaultFFMPEGLocator locator = new DefaultFFMPEGLocator();
            String ffmpegPath = locator.getExecutablePath();

            List<String> command = List.of(
                    ffmpegPath,
                    "-f", "s16le",
                    "-ar", "48000",
                    "-ac", "2",
                    "-i", sourcePcm.getAbsolutePath(),
                    "-c:a", "libvorbis",
                    "-b:a", "128k",
                    "-y",
                    targetOgg.getAbsolutePath()
            );

            LOGGER.info("Executing FFmpeg command: {}", String.join(" ", command));
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new RuntimeException("FFmpeg process failed with exit code: " + exitCode);
            }

            LOGGER.info("Encoding complete.");
            return targetOgg;
        }

        private double getAudioDuration(File audioFile) throws IOException, InterruptedException {
            DefaultFFMPEGLocator locator = new DefaultFFMPEGLocator();
            String ffprobePath = locator.getExecutablePath().replace("ffmpeg", "ffprobe");

            List<String> command = List.of(
                    ffprobePath,
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    audioFile.getAbsolutePath()
            );

            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String durationStr = reader.readLine();
                int exitCode = process.waitFor();
                if (exitCode != 0 || durationStr == null) {
                    throw new IOException("ffprobe failed to get duration for " + audioFile.getName());
                }
                return Double.parseDouble(durationStr);
            }
        }

        public List<File> splitAudioFile(File sourceFile) throws Exception {
            double totalDuration = getAudioDuration(sourceFile);
            int numFragments = (int) Math.ceil((double) sourceFile.length() / TARGET_FRAGMENT_SIZE);
            double fragmentDuration = totalDuration / numFragments;

            LOGGER.info("Splitting audio of duration {}s into {} fragments of ~{}s each.", totalDuration, numFragments, fragmentDuration);

            List<File> fragments = new ArrayList<>();
            DefaultFFMPEGLocator locator = new DefaultFFMPEGLocator();
            String ffmpegPath = locator.getExecutablePath();
            String baseName = sourceFile.getName().substring(0, sourceFile.getName().lastIndexOf('.'));

            for (int i = 0; i < numFragments; i++) {
                double startTime = i * fragmentDuration;
                File fragmentFile = new File(sourceFile.getParent(), String.format("%s-part-%d-of-%d.ogg", baseName, i + 1, numFragments));

                List<String> command = List.of(
                        ffmpegPath,
                        "-i", sourceFile.getAbsolutePath(),
                        "-ss", String.valueOf(startTime),
                        "-t", String.valueOf(fragmentDuration),
                        "-c", "copy", // Use stream copy for speed, no re-encoding
                        fragmentFile.getAbsolutePath()
                );

                LOGGER.info("Executing split command: {}", String.join(" ", command));
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.inheritIO();
                Process process = pb.start();
                if (process.waitFor() != 0) {
                    throw new IOException("FFmpeg split failed for fragment " + (i + 1));
                }
                fragments.add(fragmentFile);
            }
            return fragments;
        }

        public void cleanup() {
            mixer.close();
            try {
                if (Files.exists(recordingDir)) {
                    Files.walk(recordingDir)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                    LOGGER.info("Cleaned up and deleted recording directory: {}", recordingDir);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to cleanup recording directory: {}", recordingDir, e);
            }
        }
    }
}