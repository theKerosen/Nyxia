package com.ladyluh.nekoffee.services;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;

public class RecordingAudioHandler implements NekoffeeClient.AudioReceiveHandler, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecordingAudioHandler.class);
    private static final int FRAME_DURATION_MS = 20;
    private static final int PCM_FRAME_SIZE = 3840;

    private final Map<String, ConcurrentLinkedQueue<byte[]>> userPcmQueues = new ConcurrentHashMap<>();
    private final Map<String, byte[]> userPcmBuffers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService mixerScheduler;
    private final RandomAccessFile mixedOutputFile;
    private volatile boolean isRunning = true;

    public RecordingAudioHandler(File outputFile) throws IOException {
        this.mixedOutputFile = new RandomAccessFile(outputFile, "rw");
        this.mixerScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Nekoffee-Recording-Mixer");
            t.setDaemon(true);
            return t;
        });
        this.mixerScheduler.scheduleAtFixedRate(this::mixAndWriteFrame, 0, FRAME_DURATION_MS, TimeUnit.MILLISECONDS);
        LOGGER.info("RecordingAudioHandler started for output file: {}", outputFile.getAbsolutePath());
    }

    @Override
    public boolean canReceiveUser(User user) {
        return user != null && !user.isBot();
    }

    @Override
    public void handleUserAudio(User user, byte[] pcmData) {
        if (!isRunning || pcmData == null) {
            return;
        }


        String userId = user.getId();
        byte[] buffer = userPcmBuffers.get(userId);
        byte[] fullPcm = new byte[pcmData.length + (buffer != null ? buffer.length : 0)];

        if (buffer != null) {
            System.arraycopy(buffer, 0, fullPcm, 0, buffer.length);
        }
        System.arraycopy(pcmData, 0, fullPcm, (buffer != null ? buffer.length : 0), pcmData.length);

        int offset = 0;
        ConcurrentLinkedQueue<byte[]> userQueue = userPcmQueues.computeIfAbsent(userId, k -> new ConcurrentLinkedQueue<>());

        while (fullPcm.length - offset >= PCM_FRAME_SIZE) {
            byte[] frame = Arrays.copyOfRange(fullPcm, offset, offset + PCM_FRAME_SIZE);
            userQueue.offer(frame);
            offset += PCM_FRAME_SIZE;
        }

        if (offset < fullPcm.length) {
            userPcmBuffers.put(userId, Arrays.copyOfRange(fullPcm, offset, fullPcm.length));
        } else {
            userPcmBuffers.remove(userId);
        }
    }

    @Override
    public void onShutdown() {

        close();
    }

    private void mixAndWriteFrame() {
        if (!isRunning) {
            return;
        }

        byte[] mixedFrame = new byte[PCM_FRAME_SIZE];
        ByteBuffer mixedBuffer = ByteBuffer.wrap(mixedFrame).order(ByteOrder.LITTLE_ENDIAN);
        boolean hasAudio = false;

        for (ConcurrentLinkedQueue<byte[]> userQueue : userPcmQueues.values()) {
            byte[] pcmFrame = userQueue.poll();
            if (pcmFrame != null) {
                hasAudio = true;
                ByteBuffer userBuffer = ByteBuffer.wrap(pcmFrame).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < PCM_FRAME_SIZE / 2; i++) {
                    int sampleIndex = i * 2;
                    int mixedSample = mixedBuffer.getShort(sampleIndex) + userBuffer.getShort(sampleIndex);

                    if (mixedSample > Short.MAX_VALUE) mixedSample = Short.MAX_VALUE;
                    if (mixedSample < Short.MIN_VALUE) mixedSample = Short.MIN_VALUE;
                    mixedBuffer.putShort(sampleIndex, (short) mixedSample);
                }
            }
        }

        try {


            if (hasAudio) {
                mixedOutputFile.write(mixedFrame);
            }
        } catch (IOException e) {
            LOGGER.error("Error writing mixed audio frame to file, stopping handler.", e);
            close();
        }
    }

    @Override
    public void close() {
        if (!isRunning) {
            return;
        }
        isRunning = false;
        LOGGER.info("Closing RecordingAudioHandler...");
        mixerScheduler.shutdown();
        try {
            if (!mixerScheduler.awaitTermination(200, TimeUnit.MILLISECONDS)) {
                mixerScheduler.shutdownNow();
            }
            mixedOutputFile.close();
            LOGGER.info("RecordingAudioHandler closed successfully.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Interrupted while shutting down recording scheduler.", e);
        } catch (IOException e) {
            LOGGER.error("Failed to close recording output file.", e);
        }
    }
}