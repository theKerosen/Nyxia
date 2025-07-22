package com.ladyluh.nekoffee.services;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AudioReceiver implements NekoffeeClient.AudioReceiveHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioReceiver.class);
    private final TimedAudioMixer mixer;

    public AudioReceiver(TimedAudioMixer mixer) {
        this.mixer = mixer;
    }

    @Override
    public boolean canReceiveUser(User user) {
        return user != null && !user.isBot();
    }

    @Override
    public void handleUserAudio(User user, byte[] pcmData) {
        mixer.queueAudio(user, pcmData);
    }

    @Override
    public void onShutdown() {
        LOGGER.info("Audio receiver shutting down. Delegating to mixer.");
        mixer.close();
    }
}