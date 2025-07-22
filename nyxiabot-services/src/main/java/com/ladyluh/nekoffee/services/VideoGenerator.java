package com.ladyluh.nekoffee.services;

import com.ladyluh.nekoffee.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class VideoGenerator implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoGenerator.class);
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int FRAME_RATE = 50;
    private static final int AVATAR_SIZE = 128;
    private static final int PADDING = 20;

    private final Process ffmpegProcess;
    private final OutputStream audioStream;
    private final Path tempFrameDir;
    private final AtomicLong frameCounter = new AtomicLong(0);

    private final Map<String, BufferedImage> avatarCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> speakingState = new ConcurrentHashMap<>();
    private final Map<String, User> userCache = new ConcurrentHashMap<>();
    private final ExecutorService avatarDownloader = Executors.newCachedThreadPool();

    public VideoGenerator(File outputFile) throws IOException {
        this.tempFrameDir = outputFile.getParentFile().toPath().resolve("frames");
        Files.createDirectories(tempFrameDir);
        renderFrameToFile();

        String ffmpegPath = new DefaultFFMPEGLocator().getExecutablePath();

        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath, "-y",
                "-f", "s16le",
                "-ar", "48000",
                "-ac", "2",
                "-thread_queue_size", "1024",
                "-i", "pipe:0",
                "-framerate", String.valueOf(FRAME_RATE),
                "-thread_queue_size", "1024",
                "-i", tempFrameDir.resolve("frame_%06d.png").toAbsolutePath().toString(),
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                "-preset", "veryfast",
                "-c:a", "aac",
                "-b:a", "192k",
                "-movflags", "+faststart",
                "-map", "1:v:0",
                "-map", "0:a:0",
                outputFile.getAbsolutePath()
        );

        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        ffmpegProcess = pb.start();
        audioStream = ffmpegProcess.getOutputStream();
    }

    public boolean isAlive() {
        return ffmpegProcess != null && ffmpegProcess.isAlive();
    }

    public void initializeUsers(Collection<User> users) {
        for (User user : users) {
            userCache.put(user.getId(), user);
            updateUserAvatar(user);
        }
    }

    private void updateUserAvatar(User user) {
        if (user != null && !avatarCache.containsKey(user.getId())) {
            avatarDownloader.submit(() -> {
                try {
                    URL url = new URL(user.getEffectiveAvatarUrl());
                    BufferedImage avatar = ImageIO.read(url);
                    if (avatar != null) {
                        avatarCache.put(user.getId(), avatar);
                    }
                } catch (IOException e) {
                    LOGGER.error("Failed to download avatar for user {}", user.getId(), e);
                }
            });
        }
    }

    public void addAudioFrame(byte[] pcmData) {
        try {
            audioStream.write(pcmData);
        } catch (IOException e) {
            if (!e.getMessage().contains("Broken pipe") && !e.getMessage().contains("Stream closed")) {
                LOGGER.error("Failed to write audio frame to FFmpeg", e);
            }
        }
    }

    public void updateSpeakingState(Map<String, Boolean> currentSpeakers) {
        this.speakingState.clear();
        this.speakingState.putAll(currentSpeakers);
    }

    public void renderNextFrame() {
        renderFrameToFile();
    }

    private void renderFrameToFile() {
        try {
            BufferedImage frame = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_3BYTE_BGR);
            Graphics2D g2d = frame.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(new Color(49, 51, 56));
            g2d.fillRect(0, 0, WIDTH, HEIGHT);
            int x = PADDING;
            int y = PADDING;
            for (User user : userCache.values()) {
                BufferedImage avatar = avatarCache.get(user.getId());
                if (avatar == null) continue;
                g2d.setClip(new Ellipse2D.Float(x, y, AVATAR_SIZE, AVATAR_SIZE));
                g2d.drawImage(avatar, x, y, AVATAR_SIZE, AVATAR_SIZE, null);
                g2d.setClip(null);
                if (speakingState.getOrDefault(user.getId(), false)) {
                    g2d.setColor(new Color(59, 165, 93));
                    g2d.setStroke(new java.awt.BasicStroke(6));
                    g2d.drawOval(x - 3, y - 3, AVATAR_SIZE + 6, AVATAR_SIZE + 6);
                }
                x += AVATAR_SIZE + PADDING;
                if (x + AVATAR_SIZE > WIDTH) {
                    x = PADDING;
                    y += AVATAR_SIZE + PADDING;
                }
            }
            g2d.dispose();
            File frameFile = tempFrameDir.resolve(String.format("frame_%06d.png", frameCounter.getAndIncrement())).toFile();
            ImageIO.write(frame, "PNG", frameFile);
        } catch (IOException e) {
            LOGGER.error("Failed to write video frame to disk", e);
        }
    }

    @Override
    public void close() {
        avatarDownloader.shutdown();
        try {
            if (!avatarDownloader.awaitTermination(5, TimeUnit.SECONDS)) {
                avatarDownloader.shutdownNow();
            }
            audioStream.flush();
            audioStream.close();
            if (ffmpegProcess.waitFor(20, TimeUnit.SECONDS)) {
                LOGGER.info("FFmpeg process exited with code {}.", ffmpegProcess.exitValue());
                if(ffmpegProcess.exitValue() != 0) {
                    LOGGER.error("FFmpeg exited with a non-zero status code, indicating an error during encoding.");
                }
            } else {
                LOGGER.warn("FFmpeg process did not exit in time. Forcing termination.");
                ffmpegProcess.destroyForcibly();
            }
        } catch (Exception e) {
            LOGGER.error("Error during VideoGenerator cleanup.", e);
            ffmpegProcess.destroyForcibly();
        } finally {
            try {
                if (Files.exists(tempFrameDir)) {
                    Files.walk(tempFrameDir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to delete temporary frame directory: {}", tempFrameDir, e);
            }
        }
    }
}