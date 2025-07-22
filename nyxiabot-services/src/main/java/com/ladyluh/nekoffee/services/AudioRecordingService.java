package com.ladyluh.nekoffee.services;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.payload.send.MessageSendPayload;
import com.ladyluh.nekoffee.builder.EmbedBuilder;
import com.ladyluh.nekoffee.builder.MessageBuilder;
import com.ladyluh.nekoffee.cache.VoiceStateCacheManager;
import com.ladyluh.nekoffee.json.util.JsonEngine;
import com.ladyluh.nekoffee.services.RecordingAudioHandler.SpeakingSegment;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.info.MultimediaInfo;
import ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AudioRecordingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioRecordingService.class);
    private static final long MAX_FILE_SIZE = 24 * 1024 * 1024;
    private final NekoffeeClient client;
    private final JsonEngine jsonEngine;
    private final VoiceStateCacheManager voiceStateCacheManager;
    private final ConcurrentHashMap<String, RecordingSession> activeSessions = new ConcurrentHashMap<>();

    public AudioRecordingService(NekoffeeClient client, JsonEngine jsonEngine, VoiceStateCacheManager voiceStateCacheManager) {
        this.client = client;
        this.jsonEngine = jsonEngine;
        this.voiceStateCacheManager = voiceStateCacheManager;
        System.setProperty("java.awt.headless", "true");
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            LOGGER.info("Application is running in headless mode. AWT operations are configured.");
        } else {
            LOGGER.warn("Application is not running in headless mode. GUI components might cause issues on a server.");
        }
    }

    public CompletableFuture<Void> startRecording(String guildId, String channelId, String recordingsChannelId) {
        if (activeSessions.containsKey(guildId)) {
            return CompletableFuture.failedFuture(new IllegalStateException("A recording is already in progress for this guild."));
        }
        try {
            RecordingSession session = new RecordingSession(guildId, channelId, recordingsChannelId, client);
            Set<String> initialUserIds = voiceStateCacheManager.getMembersInVoiceChannel(guildId, channelId);
            session.handler.initializeParticipants(initialUserIds);

            return client.joinVoiceChannel(guildId, channelId)
                    .thenAccept(connection -> {
                        connection.setReceivingHandler(session.handler);
                        session.handler.start();
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
        LOGGER.info("Stopping recording for guild {}. Freezing audio streams...", guildId);
        session.handler.close();

        return client.leaveVoiceChannel(guildId).thenComposeAsync(v -> {
            LOGGER.info("Voice disconnect complete. Finalizing video for guild {}.", guildId);

            CompletableFuture<Void> processingFuture = new CompletableFuture<>();
            try {
                Set<String> participants = session.handler.getUserAudioFiles().keySet();
                if (participants.isEmpty() || participants.stream().allMatch(id -> id.equals(client.getSelfUser().getId()))) {
                    LOGGER.warn("No actual user audio was recorded. Nothing to process.");
                    client.sendMessage(session.recordingsChannelId, "⏹️ Gravação parada. Nenhum áudio de usuário foi capturado.");
                    processingFuture.complete(null);
                    return processingFuture;
                }

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                client.getChannelById(channelId).thenAccept(ch -> {
                    String channelName = ch != null ? ch.getName() : "canal desconhecido";
                    client.sendMessage(session.recordingsChannelId, "⏹️ Gravação de `" + session.recordingStartTime.format(formatter) + "` no canal `#" + channelName + "` finalizada. Processando vídeo...");
                });

                File finalMp4File = session.generateVideo();
                if (finalMp4File != null && finalMp4File.exists() && finalMp4File.length() > 0) {
                    long durationMs = session.handler.getFinalDurationMs();
                    Set<String> participantIds = session.handler.getUserAudioFiles().keySet().stream()
                            .filter(id -> !id.equals(client.getSelfUser().getId()))
                            .collect(Collectors.toSet());

                    CompletableFuture<Void> uploadCompletableFuture;
                    if (finalMp4File.length() > MAX_FILE_SIZE) {
                        client.sendMessage(session.recordingsChannelId, "⚠️ O vídeo é muito grande. Dividindo em várias partes para envio...");
                        List<File> fragments = session.splitVideoFile(finalMp4File);
                        CompletableFuture<Void> allUploads = CompletableFuture.completedFuture(null);
                        for (int i = 0; i < fragments.size(); i++) {
                            File fragment = fragments.get(i);
                            int part = i + 1;
                            int totalParts = fragments.size();
                            allUploads = allUploads.thenCompose(ignored -> uploadFile(session.recordingsChannelId, fragment, fragment.getName(), part, totalParts, durationMs, participantIds));
                        }
                        uploadCompletableFuture = allUploads;
                    } else {
                        uploadCompletableFuture = uploadFile(session.recordingsChannelId, finalMp4File, finalMp4File.getName(), 1, 1, durationMs, participantIds);
                    }
                    uploadCompletableFuture.whenComplete((res, err) -> processingFuture.complete(null));
                } else {
                    LOGGER.warn("Final MP4 file is null, empty, or does not exist. Skipping upload.");
                    client.sendMessage(session.recordingsChannelId, "❌ Ocorreu um erro e o vídeo final não pôde ser gerado.");
                    processingFuture.complete(null);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to process recording into video for guild {}", guildId, e);
                client.sendMessage(session.recordingsChannelId, "❌ Ocorreu um erro crítico ao processar o vídeo. Verifique os logs.");
                processingFuture.completeExceptionally(e);
            }

            return processingFuture.whenComplete((res, err) -> session.cleanup());
        });
    }

    private CompletableFuture<Void> uploadFile(String channelId, File file, String filename, int part, int totalParts, long durationMs, Set<String> participantIds) {
        String title = "🎥 Gravação de Voz em Vídeo";
        if (totalParts > 1) {
            title += " (Parte " + part + "/" + totalParts + ")";
        }

        List<CompletableFuture<String>> nameFutures = participantIds.stream()
                .map(id -> client.getUserById(id).thenApply(user -> {
                    if (user == null) return "Desconhecido";
                    return user.getGlobalName() != null && !user.getGlobalName().isEmpty() ? user.getGlobalName() : user.getUsername();
                }))
                .collect(Collectors.toList());

        String finalTitle = title;
        return CompletableFuture.allOf(nameFutures.toArray(new CompletableFuture[0])).thenCompose(v -> {
            String participants = nameFutures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.joining(", "));

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(finalTitle)
                    .setColor(Color.CYAN)
                    .addField("Duração", formatDuration(durationMs), true)
                    .addField("Participantes", participants.isEmpty() ? "Ninguém" : participants, false)
                    .setFooter("Arquivo: " + filename)
                    .setTimestamp(java.time.OffsetDateTime.now());
            try {
                LOGGER.info("Uploading file: {} (Part {}/{})", filename, part, totalParts);
                MessageSendPayload messagePayload = new MessageBuilder().addEmbed(embed).build();
                String payloadJsonString = jsonEngine.toJsonString(messagePayload);
                MultipartBody body = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("payload_json", payloadJsonString)
                        .addFormDataPart("files[0]", filename, RequestBody.create(file, MediaType.parse("video/mp4")))
                        .build();
                return client.sendMessage(channelId, body).thenAccept(message -> {
                });
            } catch (Exception e) {
                LOGGER.error("Failed to prepare upload for file {}", filename, e);
                client.sendMessage(channelId, "❌ Falha ao preparar o envio do arquivo: `" + filename + "`");
                return CompletableFuture.failedFuture(e);
            }
        });
    }

    private String formatDuration(long durationMs) {
        long seconds = (durationMs / 1000) % 60;
        long minutes = (durationMs / (1000 * 60)) % 60;
        long hours = (durationMs / (1000 * 60 * 60));
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    private static class RecordingSession {
        final String guildId;
        final String channelId;
        final String recordingsChannelId;
        final Path recordingDir;
        final String recordingName;
        final RecordingAudioHandler handler;
        final NekoffeeClient client;
        final ZonedDateTime recordingStartTime = ZonedDateTime.now();

        RecordingSession(String guildId, String channelId, String recordingsChannelId, NekoffeeClient client) throws IOException {
            this.guildId = guildId;
            this.channelId = channelId;
            this.recordingsChannelId = recordingsChannelId;
            this.client = client;
            this.recordingName = "rec-" + recordingStartTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            this.recordingDir = Path.of("recordings", guildId, recordingName);
            Files.createDirectories(recordingDir);
            this.handler = new RecordingAudioHandler(this.recordingDir);
        }

        
        private File extractResource(String resourcePath, String outputFileName) throws IOException {
            File outputFile = recordingDir.resolve(outputFileName).toFile();
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (stream == null) throw new IOException("Resource not found: " + resourcePath);
                Files.copy(stream, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return outputFile;
        }

        private String escapeTextForFFmpeg(String text) {
            
            return text.replace("'", "'\\''").replace(":", "\\:").replace("%", "\\%").replace(",", "\\,").replace("[", "\\[").replace("]", "\\]");
        }

        
        
        
        private File generateConcatFile(String userId, double totalDuration, File indicatorImage, File silentImage) throws IOException {
            Map<String, List<SpeakingSegment>> timeline = handler.getMergedSpeakingTimeline();
            List<SpeakingSegment> segments = timeline.getOrDefault(userId, Collections.emptyList());
            File concatFile = recordingDir.resolve("timeline_" + userId + ".txt").toFile();

            try (PrintWriter writer = new PrintWriter(new FileWriter(concatFile))) {
                double lastEndTime = 0.0;
                
                writer.println("ffconcat version 1.0");

                for (SpeakingSegment segment : segments) {
                    double startTime = segment.startTimeMs / 1000.0;
                    double endTime = segment.endTimeMs / 1000.0;

                    
                    if (startTime > lastEndTime) {
                        writer.println("file '" + silentImage.getAbsolutePath().replace("\\", "/") + "'");
                        writer.printf(Locale.US, "duration %.3f%n", startTime - lastEndTime);
                    }

                    
                    writer.println("file '" + indicatorImage.getAbsolutePath().replace("\\", "/") + "'");
                    writer.printf(Locale.US, "duration %.3f%n", endTime - startTime);
                    lastEndTime = endTime;
                }

                
                if (totalDuration > lastEndTime) {
                    writer.println("file '" + silentImage.getAbsolutePath().replace("\\", "/") + "'");
                    writer.printf(Locale.US, "duration %.3f%n", totalDuration - lastEndTime);
                }
            }
            return concatFile;
        }

        
        private File createTransparentPixel() throws IOException {
            File transparentFile = recordingDir.resolve("transparent.png").toFile();
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, 0x00FFFFFF); 
            ImageIO.write(image, "PNG", transparentFile);
            return transparentFile;
        }

        
        
        private File generateCircularMask(int size) throws IOException {
            File maskFile = recordingDir.resolve("mask_" + size + ".png").toFile();
            BufferedImage mask = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = mask.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(Color.BLACK);
            g2d.fillRect(0, 0, size, size);
            g2d.setColor(Color.WHITE);
            g2d.fillOval(0, 0, size, size);
            g2d.dispose();
            ImageIO.write(mask, "PNG", maskFile);
            return maskFile;
        }



        public File generateVideo() throws Exception {
            Set<String> participantIds = handler.getUserAudioFiles().keySet().stream()
                    .filter(id -> !id.equals(client.getSelfUser().getId()))
                    .collect(Collectors.toSet());

            if (participantIds.isEmpty()) {
                LOGGER.warn("No user participants found. Aborting video generation.");
                return null;
            }
            LOGGER.info("Starting definitive, log-corrected video generation for {} participants.", participantIds.size());

            
            CompletableFuture<String> channelNameFuture = client.getChannelById(this.channelId)
                    .thenApply(channel -> channel != null ? "# " + channel.getName() : "Unknown Channel");
            Map<String, ParticipantInfo> participants = downloadParticipantInfo(participantIds);
            if (participants.isEmpty()) return null;
            long durationMs = handler.getFinalDurationMs();
            if (durationMs <= 0) return null;
            double durationSeconds = durationMs / 1000.0;
            LOGGER.info("Total recording duration: {} seconds.", String.format(Locale.US, "%.3f", durationSeconds));

            final int avatarSize = (participantIds.size() == 1) ? 300 : 150;
            File fontFile = extractResource("fonts/DejaVuSans.ttf", "DejaVuSans.ttf");
            File indicatorTemplate = generateSpeakingIndicator(avatarSize);
            File silentPixel = createTransparentPixel();
            File circularMask = generateCircularMask(avatarSize);

            
            DefaultFFMPEGLocator locator = new DefaultFFMPEGLocator();
            String ffmpegPath = locator.getExecutablePath();
            List<String> command = new ArrayList<>();
            command.addAll(Arrays.asList(ffmpegPath, "-hide_banner", "-loglevel", "warning")); 

            
            int inputIndex = 0;
            List<String> pcmInputLabels = new ArrayList<>();
            for (String userId : participants.keySet()) {
                File pcmFile = recordingDir.resolve("user_" + userId + ".pcm").toFile();
                if (pcmFile.exists() && pcmFile.length() > 0) {
                    command.addAll(Arrays.asList("-f", "s16le", "-ar", "48000", "-ac", "2", "-i", pcmFile.getAbsolutePath()));
                    pcmInputLabels.add("[" + inputIndex++ + ":a]");
                }
            }
            Map<String, String> avatarInputLabels = new HashMap<>();
            List<String> userIdsWithAvatars = new ArrayList<>(participants.keySet());
            for (String userId : userIdsWithAvatars) {
                command.addAll(Arrays.asList("-loop", "1", "-i", participants.get(userId).rawAvatarFile.getAbsolutePath()));
                avatarInputLabels.put(userId, "[" + inputIndex++ + ":v]");
            }
            command.addAll(Arrays.asList("-loop", "1", "-i", circularMask.getAbsolutePath()));
            String maskInputLabel = "[" + inputIndex++ + ":v]";
            Map<String, String> timelineInputLabels = new HashMap<>();
            for (String userId : userIdsWithAvatars) {
                File concatFile = generateConcatFile(userId, durationSeconds, indicatorTemplate, silentPixel);
                command.addAll(Arrays.asList("-f", "concat", "-safe", "0", "-i", concatFile.getAbsolutePath()));
                timelineInputLabels.put(userId, "[" + inputIndex++ + ":v]");
            }

            
            
            StringBuilder filterComplex = new StringBuilder();

            
            List<String> compositeLabels = new ArrayList<>();
            for (int i = 0; i < userIdsWithAvatars.size(); i++) {
                String userId = userIdsWithAvatars.get(i);
                String avatarInput = avatarInputLabels.get(userId);
                String timelineInput = timelineInputLabels.get(userId);

                String scaledAvatar = "[scaled_av" + i + "]";
                String circularAvatar = "[circ_av" + i + "]";
                String indicator = "[indicator" + i + "]";
                String compositeLabel = "[comp" + i + "]"; 
                compositeLabels.add(compositeLabel);

                filterComplex
                        .append(avatarInput).append("scale=").append(avatarSize).append(":").append(avatarSize).append(scaledAvatar).append(";")
                        .append(scaledAvatar).append(maskInputLabel).append("alphamerge").append(circularAvatar).append(";")
                        .append(timelineInput).append("scale=").append(avatarSize).append(":").append(avatarSize).append(indicator).append(";")
                        .append(circularAvatar).append(indicator).append("overlay").append(compositeLabel).append(";");
            }

            
            int canvasWidth = 1920;
            int canvasHeight = 1080;
            String bgColor = "#36393f";
            String textColor = "#dcddde";
            String channelName = channelNameFuture.join();
            Locale brazilLocale = new Locale("pt", "BR");
            String dateTimeText = recordingStartTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", brazilLocale));
            String fontPath = fontFile.getAbsolutePath().replace("\\", "/");

            
            filterComplex.append("color=s=").append(canvasWidth).append("x").append(canvasHeight).append(":c=").append(bgColor).append(":d=").append(durationSeconds).append("[base];")
                    .append("[base]geq=lum='p(X,Y)':a='(255-min(255,sqrt(pow(X-W/2,2)+pow(Y-H/2,2))*0.4))'[base_gradient];")
                    .append("[base_gradient]drawbox=y=0:h=60:c=black@0.4:t=fill[header];")
                    .append("[header]drawtext=fontfile='").append(fontPath).append("':text='").append(escapeTextForFFmpeg(dateTimeText)).append("':fontcolor=").append(textColor).append("@0.8:fontsize=28:x=w-text_w-25:y=(60-text_h)/2[base_with_date];")
                    .append("[base_with_date]drawtext=fontfile='").append(fontPath).append("':text='").append(escapeTextForFFmpeg(channelName)).append("':fontcolor=").append(textColor).append(":fontsize=28:x=25:y=(60-text_h)/2[base_with_header];");

            
            String lastStage = "[base_with_header]";

            int nameFontSize = (participantIds.size() == 1) ? 48 : 28;
            int tagFontSize = 28;
            int cols = (participantIds.size() == 1) ? 1 : Math.min(participantIds.size(), 5);
            int rows = (int) Math.ceil((double) participantIds.size() / cols);
            int padding = 30;
            int nameHeight = 40;
            int totalCellHeight = avatarSize + nameHeight + ((participantIds.size() == 1) ? 80 : 0);
            int gridWidth = (cols * avatarSize) + ((cols - 1) * padding);
            int gridHeight = (rows * totalCellHeight) + ((rows - 1) * padding);
            int startX = (canvasWidth - gridWidth) / 2;
            int startY = (canvasHeight - gridHeight) / 2;

            for (int i = 0; i < userIdsWithAvatars.size(); i++) {
                String compositeLabel = compositeLabels.get(i);
                ParticipantInfo info = participants.get(userIdsWithAvatars.get(i));

                int row = i / cols;
                int col = i % cols;
                int xPos = startX + col * (avatarSize + padding);
                int yPos = startY + row * (totalCellHeight + padding);

                String avatarStage = "[av_placed" + i + "]";
                filterComplex.append(lastStage).append(compositeLabel).append("overlay=x=").append(xPos).append(":y=").append(yPos).append(avatarStage).append(";");
                lastStage = avatarStage;

                String nameStage = "[name_placed" + i + "]";
                String textXpos = String.format(Locale.US, "(%d+(%d-text_w)/2)", xPos, avatarSize);
                filterComplex.append(lastStage)
                        .append("drawtext=fontfile='").append(fontPath)
                        .append("':text='").append(escapeTextForFFmpeg(info.displayName))
                        .append("':fontcolor=").append(textColor).append(":fontsize=").append(nameFontSize)
                        .append(":x=").append(textXpos).append(":y=").append(yPos + avatarSize + 15).append(nameStage).append(";");
                lastStage = nameStage;

                if (participantIds.size() == 1) {
                    String tagStage = "[tag_placed" + i + "]";
                    String tagXpos = String.format(Locale.US, "(%d+(%d-text_w)/2)", xPos, avatarSize);
                    filterComplex.append(lastStage)
                            .append("drawtext=fontfile='").append(fontPath)
                            .append("':text='").append(escapeTextForFFmpeg(info.userTag))
                            .append("':fontcolor=").append(textColor).append("@0.6:fontsize=").append(tagFontSize)
                            .append(":x=").append(tagXpos).append(":y=").append(yPos + avatarSize + 80).append(tagStage).append(";");
                    lastStage = tagStage;
                }
            }
            
            String botName = escapeTextForFFmpeg("Recorded by " + client.getSelfUser().getUsername());
            filterComplex.setLength(filterComplex.length() - 1); 
            filterComplex.append(lastStage)
                    .append("drawtext=fontfile='").append(fontPath).append("':text='").append(botName).append("':fontcolor=white@0.5:fontsize=24:x=w-text_w-15:y=h-text_h-15,format=yuv420p[video_out]");
            
            if (!pcmInputLabels.isEmpty()) {
                filterComplex.append(";").append(String.join("", pcmInputLabels))
                        .append("amix=inputs=").append(pcmInputLabels.size()).append(":dropout_transition=0[audio_out]");
            }

            command.addAll(Arrays.asList("-filter_complex", filterComplex.toString()));
            
            command.addAll(Arrays.asList("-map", "[video_out]"));
            if (!pcmInputLabels.isEmpty()) {
                command.addAll(Arrays.asList("-map", "[audio_out]"));
                command.addAll(Arrays.asList("-c:a", "aac", "-b:a", "192k"));
            }
            command.addAll(Arrays.asList("-c:v", "libx264", "-preset", "veryfast", "-crf", "26"));
            command.addAll(Arrays.asList("-threads", "0", "-pix_fmt", "yuv420p", "-y")); 

            File outputFile = recordingDir.resolve(recordingName + ".mp4").toFile();
            command.add(outputFile.getAbsolutePath());

            
            LOGGER.info("Executing FINAL, log-corrected FFmpeg command. Filter complex length: {} chars", filterComplex.length());
            
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();

            
            new Thread(() -> new BufferedReader(new InputStreamReader(process.getErrorStream())).lines().forEach(line -> {
                if (line.contains("frame=") && line.contains("fps=") && line.contains("speed=")) {
                    LOGGER.info("FFMPEG_PROGRESS: {}", line);
                } else {
                    
                    LOGGER.warn("FFMPEG_STDERR: {}", line);
                }
            })).start();
            new Thread(() -> new BufferedReader(new InputStreamReader(process.getInputStream())).lines().forEach(line -> LOGGER.debug("FFMPEG_STDOUT: {}", line))).start();

            boolean finished = process.waitFor(20, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("FFmpeg process timed out after 20 minutes.");
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                
                LOGGER.error("FFmpeg process FAILED with exit code: {}. Command was: {}", exitCode, String.join(" ", command));
                throw new RuntimeException("FFmpeg process failed with exit code: " + exitCode);
            }
            LOGGER.info("Video generation complete: {}", outputFile.getAbsolutePath());
            return outputFile;
        }

        private File generateSpeakingIndicator(int size) throws IOException {
            
            int strokeWidth = (size == 300) ? 12 : 10;
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(new Color(67, 181, 129)); 
            g2d.setStroke(new BasicStroke(strokeWidth));
            g2d.drawOval(strokeWidth / 2, strokeWidth / 2, size - strokeWidth, size - strokeWidth);
            g2d.dispose();
            File file = recordingDir.resolve("speaking_indicator_" + size + ".png").toFile();
            ImageIO.write(image, "PNG", file);
            return file;
        }
        
        private Map<String, ParticipantInfo> downloadParticipantInfo(Set<String> userIds) {
            Map<String, ParticipantInfo> participantInfoMap = new ConcurrentHashMap<>();
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (String userId : userIds) {
                futures.add(
                        client.getUserById(userId).thenAcceptAsync(user -> {
                            if (user == null) return;
                            try {
                                String avatarUrl = user.getEffectiveAvatarUrl().replace("?size=128", "?size=512");
                                File avatarFile = recordingDir.resolve("avatar_raw_" + userId + ".png").toFile();

                                try (InputStream in = new URL(avatarUrl).openStream()) {
                                    Files.copy(in, avatarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                }

                                String name = user.getGlobalName() != null && !user.getGlobalName().isEmpty() ? user.getGlobalName() : user.getUsername();
                                participantInfoMap.put(userId, new ParticipantInfo(avatarFile, name, user.getAsTag()));
                            } catch (IOException e) {
                                LOGGER.error("Failed to download avatar for user {}", userId, e);
                            }
                        })
                );
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            return participantInfoMap;
        }

        public List<File> splitVideoFile(File sourceFile) throws Exception {
            MultimediaObject media = new MultimediaObject(sourceFile);
            MultimediaInfo info = media.getInfo();
            double totalDuration = info.getDuration() / 1000.0;
            long totalSize = sourceFile.length();
            int numFragments = (int) Math.ceil((double) totalSize / MAX_FILE_SIZE);
            double fragmentDuration = totalDuration / numFragments;

            LOGGER.info("Splitting video of duration {}s into {} fragments of ~{}s each.", totalDuration, numFragments, fragmentDuration);

            List<File> fragments = new ArrayList<>();
            DefaultFFMPEGLocator locator = new DefaultFFMPEGLocator();
            String ffmpegPath = locator.getExecutablePath();
            String baseName = sourceFile.getName().substring(0, sourceFile.getName().lastIndexOf('.'));

            for (int i = 0; i < numFragments; i++) {
                double startTime = i * fragmentDuration;
                File fragmentFile = new File(sourceFile.getParent(), String.format("%s-part-%d-of-%d.mp4", baseName, i + 1, numFragments));

                List<String> command = List.of(
                        ffmpegPath,
                        "-i", sourceFile.getAbsolutePath(),
                        "-ss", String.format(Locale.US, "%.3f", startTime),
                        "-t", String.format(Locale.US, "%.3f", fragmentDuration),
                        "-c", "copy", 
                        "-y",
                        fragmentFile.getAbsolutePath()
                );

                LOGGER.info("Executing split command: {}", String.join(" ", command));
                ProcessBuilder pb = new ProcessBuilder(command);
                Process process = pb.start();
                if (process.waitFor() != 0) {
                    
                    String error = new BufferedReader(new InputStreamReader(process.getErrorStream()))
                            .lines().collect(Collectors.joining("\n"));
                    LOGGER.error("FFmpeg split failed for fragment {}. Error: {}", i + 1, error);
                    throw new IOException("FFmpeg split failed for fragment " + (i + 1));
                }
                fragments.add(fragmentFile);
            }
            return fragments;
        }

        public void cleanup() {
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

        
        private static class ParticipantInfo {
            final File rawAvatarFile;
            final String displayName;
            final String userTag;

            ParticipantInfo(File rawAvatarFile, String displayName, String userTag) {
                this.rawAvatarFile = rawAvatarFile;
                this.displayName = displayName;
                this.userTag = userTag;
            }
        }
    }
}