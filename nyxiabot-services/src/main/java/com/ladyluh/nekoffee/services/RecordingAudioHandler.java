package com.ladyluh.nekoffee.services;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class RecordingAudioHandler implements NekoffeeClient.AudioReceiveHandler, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecordingAudioHandler.class);
    private static final int FRAME_DURATION_MS = 20;
    private static final int PCM_FRAME_SIZE = 3840;

    public static class SpeakingSegment {
        public final String userId;
        public final long startTimeMs;
        public final long endTimeMs;

        public SpeakingSegment(String userId, long startTimeMs, long endTimeMs) {
            this.userId = userId;
            this.startTimeMs = startTimeMs;
            this.endTimeMs = endTimeMs;
        }
    }

    private final Path recordingDir;
    private final Map<String, RandomAccessFile> userAudioFiles = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentLinkedQueue<byte[]>> userPcmQueues = new ConcurrentHashMap<>();
    private final List<SpeakingSegment> speakingTimeline = Collections.synchronizedList(new ArrayList<>());
    private final ScheduledExecutorService mixerScheduler;
    private final byte[] silenceFrame = new byte[PCM_FRAME_SIZE];
    private volatile boolean isRunning = true;
    private final AtomicLong frameCounter = new AtomicLong(0);

    public RecordingAudioHandler(Path recordingDir) {
        this.recordingDir = recordingDir;
        this.mixerScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Nekoffee-Recording-Clock");
            t.setDaemon(true);
            return t;
        });
        LOGGER.info("RecordingAudioHandler initialized for output directory: {}", recordingDir);
    }

    public void start() {
        if (isRunning) {
            LOGGER.info("Starting RecordingAudioHandler master clock.");
            this.mixerScheduler.scheduleAtFixedRate(this::processNextFrame, 0, FRAME_DURATION_MS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Proactively creates files for all users present at the start of the recording.
     * @param userIds A set of user IDs to initialize.
     */
    public void initializeParticipants(Set<String> userIds) {
        LOGGER.info("Pre-initializing PCM files for {} participants.", userIds.size());
        for (String userId : userIds) {
            userPcmQueues.computeIfAbsent(userId, k -> new ConcurrentLinkedQueue<>());
            userAudioFiles.computeIfAbsent(userId, id -> {
                try {
                    File file = recordingDir.resolve("user_" + id + ".pcm").toFile();
                    return new RandomAccessFile(file, "rw");
                } catch (IOException e) {
                    throw new RuntimeException("Failed to create initial PCM file for user " + id, e);
                }
            });
        }
    }

    @Override
    public boolean canReceiveUser(User user) {
        if (user == null || user.isBot()) {
            return false;
        }
        userPcmQueues.computeIfAbsent(user.getId(), k -> new ConcurrentLinkedQueue<>());
        userAudioFiles.computeIfAbsent(user.getId(), id -> {
            long framesToPad = frameCounter.get();
            try {
                File fileHandle = recordingDir.resolve("user_" + id + ".pcm").toFile();
                RandomAccessFile file = new RandomAccessFile(fileHandle, "rw");
                if (framesToPad > 0) {
                    LOGGER.info("New user {} joined mid-recording. Padding their audio file with {} silent frames to synchronize.", id, framesToPad);
                    byte[] paddingBuffer = new byte[PCM_FRAME_SIZE];
                    for (int i = 0; i < framesToPad; i++) {
                        file.write(paddingBuffer);
                    }
                }
                return file;
            } catch (IOException e) {
                LOGGER.error("Fatal: Failed to create or pad PCM file for new user {}", id, e);
                throw new RuntimeException("Failed to create/pad PCM file for user " + id, e);
            }
        });
        return true;
    }

    @Override
    public void handleUserAudio(User user, byte[] pcmData) {
        if (!isRunning || pcmData == null || user == null) {
            return;
        }
        ConcurrentLinkedQueue<byte[]> queue = userPcmQueues.get(user.getId());
        if (queue != null) {
            queue.offer(pcmData);
        }
    }

    private void processNextFrame() {
        if (!isRunning) return;

        long currentFrame = frameCounter.getAndIncrement();

        for (String userId : userAudioFiles.keySet()) {
            RandomAccessFile userFile = userAudioFiles.get(userId);
            ConcurrentLinkedQueue<byte[]> userQueue = userPcmQueues.get(userId);
            if (userFile == null || userQueue == null) continue;

            byte[] pcmFrame = userQueue.poll();
            byte[] frameToWrite;

            if (pcmFrame != null) {
                frameToWrite = pcmFrame;
                long startTime = currentFrame * FRAME_DURATION_MS;
                speakingTimeline.add(new SpeakingSegment(userId, startTime, startTime + FRAME_DURATION_MS));
            } else {
                frameToWrite = silenceFrame;
            }

            try {
                userFile.write(frameToWrite);
            } catch (IOException e) {
                LOGGER.error("Error writing audio frame for user {}, stopping handler.", userId, e);
                close();
                return;
            }
        }
    }

    public Map<String, List<SpeakingSegment>> getMergedSpeakingTimeline() {
        if (speakingTimeline.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<SpeakingSegment>> segmentsByUser = this.speakingTimeline.stream()
                .collect(Collectors.groupingBy(s -> s.userId));
        Map<String, List<SpeakingSegment>> mergedSegmentsByUser = new ConcurrentHashMap<>();
        segmentsByUser.forEach((userId, segments) -> {
            if (segments.isEmpty()) return;
            segments.sort((s1, s2) -> Long.compare(s1.startTimeMs, s2.startTimeMs));
            List<SpeakingSegment> merged = new ArrayList<>();
            SpeakingSegment currentMerge = new SpeakingSegment(userId, segments.get(0).startTimeMs, segments.get(0).endTimeMs);
            for (int i = 1; i < segments.size(); i++) {
                SpeakingSegment next = segments.get(i);
                if (next.startTimeMs - currentMerge.endTimeMs <= FRAME_DURATION_MS) {
                    currentMerge = new SpeakingSegment(userId, currentMerge.startTimeMs, next.endTimeMs);
                } else {
                    merged.add(currentMerge);
                    currentMerge = new SpeakingSegment(userId, next.startTimeMs, next.endTimeMs);
                }
            }
            merged.add(currentMerge);
            mergedSegmentsByUser.put(userId, merged);
        });
        return mergedSegmentsByUser;
    }

    public Map<String, RandomAccessFile> getUserAudioFiles() {
        return userAudioFiles;
    }

    @Override
    public void onShutdown() {
        close();
    }

    @Override
    public void close() {
        if (!isRunning) return;
        isRunning = false;
        LOGGER.info("Closing RecordingAudioHandler. Finalizing audio streams...");
        mixerScheduler.shutdown();
        try {
            if (!mixerScheduler.awaitTermination(200, TimeUnit.MILLISECONDS)) {
                mixerScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Interrupted while shutting down recording scheduler.", e);
        }
        for (RandomAccessFile file : userAudioFiles.values()) {
            try {
                file.close();
            } catch (IOException e) {
                LOGGER.error("Failed to close a user PCM file.", e);
            }
        }
        LOGGER.info("RecordingAudioHandler streams finalized. {} participants recorded across {} frames.", userAudioFiles.size(), frameCounter.get());
    }

    public long getFinalDurationMs() {
        return frameCounter.get() * FRAME_DURATION_MS;
    }
}