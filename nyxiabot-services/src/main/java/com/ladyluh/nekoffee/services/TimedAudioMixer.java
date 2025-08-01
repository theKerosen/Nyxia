package com.ladyluh.nekoffee.services;
import com.ladyluh.nekoffee.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.*;
public class TimedAudioMixer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimedAudioMixer.class);
    
    private static final int FRAME_DURATION_MS = 20;
    private static final int FRAME_SIZE_BYTES = 3840;
    private final RandomAccessFile mixedOutputFile;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentLinkedQueue<AudioPacket> audioQueue = new ConcurrentLinkedQueue<>();
    private final byte[] silenceFrame = new byte[FRAME_SIZE_BYTES];
    private static class AudioPacket {
        final User user;
        final byte[] pcmData;
        AudioPacket(User user, byte[] pcmData) {
            this.user = user;
            this.pcmData = pcmData;
        }
    }

    public TimedAudioMixer(File outputFile) throws IOException {
        this.mixedOutputFile = new RandomAccessFile(outputFile, "rw");
        
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Nekoffee-Audio-Mixer-Clock");
            t.setDaemon(true);
            return t;
        });
        LOGGER.info("TimedAudioMixer initialized for output file: {}", outputFile.getAbsolutePath());
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::processNextFrame, 0, FRAME_DURATION_MS, TimeUnit.MILLISECONDS);
        LOGGER.info("Audio mixer clock started.");
    }

    public void queueAudio(User user, byte[] pcmData) {
        if (pcmData.length == FRAME_SIZE_BYTES) {
            audioQueue.offer(new AudioPacket(user, pcmData));
        } else {
            LOGGER.warn("Received audio packet with incorrect size for user {}. Expected {}, got {}. Discarding.",
                    user.getId(), FRAME_SIZE_BYTES, pcmData.length);
        }
    }

    private void processNextFrame() {
        try {
            
            byte[] mixedFrame = Arrays.copyOf(silenceFrame, FRAME_SIZE_BYTES);
            ByteBuffer mixedFrameBuffer = ByteBuffer.wrap(mixedFrame).order(ByteOrder.LITTLE_ENDIAN);
            
            AudioPacket packet;
            while ((packet = audioQueue.poll()) != null) {
                ByteBuffer packetBuffer = ByteBuffer.wrap(packet.pcmData).order(ByteOrder.LITTLE_ENDIAN);
                
                mixedFrameBuffer.position(0);
                packetBuffer.position(0);
                
                for (int i = 0; i < FRAME_SIZE_BYTES / 2; i++) {
                    int currentSample = mixedFrameBuffer.getShort(i * 2);
                    int newSample = packetBuffer.getShort(i * 2);
                    int mixedSample = currentSample + newSample;
                    
                    if (mixedSample > Short.MAX_VALUE) {
                        mixedSample = Short.MAX_VALUE;
                    } else if (mixedSample < Short.MIN_VALUE) {
                        mixedSample = Short.MIN_VALUE;
                    }
                    mixedFrameBuffer.putShort(i * 2, (short) mixedSample);
                }
            }
            
            mixedOutputFile.write(mixedFrame);
        } catch (IOException e) {
            LOGGER.error("Error processing audio frame, stopping mixer.", e);
            close();
        }
    }

    @Override
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            mixedOutputFile.close();
            LOGGER.info("TimedAudioMixer shut down and file closed.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Interrupted while shutting down audio mixer.", e);
        } catch (IOException e) {
            LOGGER.error("Failed to close mixed audio output file.", e);
        }
    }
}