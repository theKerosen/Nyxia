package com.ladyluh.nekoffee.voice;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.iwebpp.crypto.TweetNaclFast;
import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.User;
import com.ladyluh.nekoffee.json.util.JsonEngine;
import com.ladyluh.nekoffee.opus.OpusDecoder;
import okhttp3.*;
import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;
import org.bitlet.weupnp.PortMappingEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class VoiceConnectionImpl implements com.ladyluh.nekoffee.api.voice.VoiceConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger(VoiceConnectionImpl.class);
    private static final int PCM_FRAME_SIZE = 3840;
    private final String guildId;
    private final String userId;
    private final NekoffeeClient client;
    private final JsonEngine jsonEngine;
    private final OkHttpClient httpClient;
    private final ExecutorService voiceExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Nekoffee-Voice-Thread"));
    private final CountDownLatch secretKeyLatch = new CountDownLatch(1);
    private final Map<Integer, OpusDecoder> opusDecoders = new ConcurrentHashMap<>();
    private final Map<Integer, String> ssrcToUserMap = new ConcurrentHashMap<>();
    private final Map<String, User> userIdToUserCache = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<Integer, Short> lastSequenceMap = new ConcurrentHashMap<>();
    private final Map<Integer, byte[]> pcmUserBuffers = new ConcurrentHashMap<>();
    private String encryptionMode;
    private WebSocket voiceWebSocket;
    private DatagramSocket udpSocket;
    private InetSocketAddress discordUdpAddress;
    private int ssrc;
    private volatile byte[] secretKey;
    private GatewayDevice activeGateway;
    private int mappedPort = -1;
    private NekoffeeClient.AudioReceiveHandler receiveHandler;
    private Thread udpListenerThread;


    public VoiceConnectionImpl(String guildId, String userId, NekoffeeClient client, JsonEngine jsonEngine, OkHttpClient httpClient) {
        this.guildId = guildId;
        this.userId = userId;
        this.client = client;
        this.jsonEngine = jsonEngine;
        this.httpClient = httpClient;
    }

    @Override
    public CompletableFuture<Void> connect(String sessionId, String token, String endpoint) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        voiceExecutor.submit(() -> {
            try {
                if (running.get() || voiceWebSocket != null) {
                    disconnect();
                }
                String url = "wss://" + endpoint.replace(":80", "") + "/?v=4";
                Request request = new Request.Builder().url(url).build();
                voiceWebSocket = httpClient.newWebSocket(request, new VoiceGatewayHandler(sessionId, token, future));
            } catch (Exception e) {
                LOGGER.error("Failed to initiate voice WebSocket connection for guild {}: {}", guildId, e.getMessage(), e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> disconnect() {

        return CompletableFuture.runAsync(() -> {

            running.set(false);


            if (voiceWebSocket != null) {
                voiceWebSocket.close(1000, "Client disconnect");
                voiceWebSocket = null;
            }


            if (udpSocket != null) {
                udpSocket.close();
                udpSocket = null;
            }


            if (udpListenerThread != null) {
                try {
                    LOGGER.debug("Waiting for UDP listener thread to terminate...");
                    udpListenerThread.join(2000);
                    if (udpListenerThread.isAlive()) {
                        LOGGER.warn("UDP listener thread did not terminate in time.");
                    } else {
                        LOGGER.debug("UDP listener thread terminated successfully.");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.error("Interrupted while waiting for UDP listener thread to stop.");
                }
            }


            if (receiveHandler != null) {
                receiveHandler.onShutdown();
            }


            if (activeGateway != null && mappedPort != -1) {
                try {
                    activeGateway.deletePortMapping(mappedPort, "UDP");
                    LOGGER.info("Successfully removed UPnP port mapping for port {}", mappedPort);
                } catch (Exception e) {
                    LOGGER.error("Failed to remove UPnP port mapping: {}", e.getMessage());
                } finally {
                    activeGateway = null;
                    mappedPort = -1;
                }
            }


            opusDecoders.values().forEach(OpusDecoder::close);
            opusDecoders.clear();
            ssrcToUserMap.clear();
            userIdToUserCache.clear();
            pcmUserBuffers.clear();
            lastSequenceMap.clear();

            LOGGER.info("Voice connection for guild {} fully disconnected.", guildId);
        }, voiceExecutor).whenComplete((res, err) -> {

            if (!voiceExecutor.isShutdown()) {
                voiceExecutor.shutdown();
            }
        });
    }

    @Override
    public void setReceivingHandler(NekoffeeClient.AudioReceiveHandler handler) {
        this.receiveHandler = handler;
    }

    private CompletableFuture<DiscoveredInfo> startUdpDiscovery() {
        CompletableFuture<DiscoveredInfo> future = new CompletableFuture<>();
        voiceExecutor.submit(() -> {
            try {

                try {
                    GatewayDiscover discover = new GatewayDiscover();
                    discover.discover();
                    activeGateway = discover.getValidGateway();
                    if (activeGateway != null) LOGGER.info("UPnP gateway found: {}", activeGateway.getModelName());
                    else LOGGER.warn("No valid UPnP gateway found. Proceeding without port mapping.");
                } catch (Exception e) {
                    LOGGER.warn("UPnP discovery failed: {}", e.getMessage());
                    activeGateway = null;
                }

                udpSocket = new DatagramSocket();
                int localPort = udpSocket.getLocalPort();
                this.mappedPort = localPort;


                if (activeGateway != null) {
                    try {
                        InetAddress localAddress = activeGateway.getLocalAddress();
                        LOGGER.info("Requesting UPnP port mapping: external {} -> internal {}:{} (UDP)", localPort, localAddress.getHostAddress(), localPort);
                        if (!activeGateway.getSpecificPortMappingEntry(localPort, "UDP", new PortMappingEntry())) {
                            activeGateway.addPortMapping(localPort, localPort, localAddress.getHostAddress(), "UDP", "Nekoffee Voice");
                            LOGGER.info("Successfully created UPnP port mapping for port {}", localPort);
                        } else {
                            LOGGER.warn("UPnP port mapping for {} already exists.", localPort);
                        }
                    } catch (Exception e) {
                        LOGGER.error("An error occurred during UPnP port mapping.", e);
                    }
                }


                ByteBuffer discoveryBuffer = ByteBuffer.allocate(74);
                discoveryBuffer.order(ByteOrder.BIG_ENDIAN);
                discoveryBuffer.putShort(0, (short) 1);
                discoveryBuffer.putShort(2, (short) 70);
                discoveryBuffer.putInt(4, ssrc);

                LOGGER.debug("Sending UDP discovery packet to {}", discordUdpAddress);
                udpSocket.send(new DatagramPacket(discoveryBuffer.array(), 74, discordUdpAddress));

                DatagramPacket receivedPacket = new DatagramPacket(new byte[74], 74);
                udpSocket.setSoTimeout(5000);
                udpSocket.receive(receivedPacket);

                ByteBuffer responseBuffer = ByteBuffer.wrap(receivedPacket.getData());

                int nullPos = 8;
                while (nullPos < 72 && responseBuffer.get(nullPos) != 0) {
                    nullPos++;
                }
                if (nullPos == 72) throw new IOException("Invalid IP discovery response: IP not null-terminated.");

                String externalIp = new String(receivedPacket.getData(), 8, nullPos - 8, StandardCharsets.UTF_8);
                int externalPort = Short.toUnsignedInt(responseBuffer.getShort(72));

                future.complete(new DiscoveredInfo(externalIp, externalPort));

            } catch (Exception e) {
                LOGGER.error("Failed during UDP IP Discovery.", e);
                future.completeExceptionally(e);
                if (udpSocket != null) udpSocket.close();
            }
        });
        return future;
    }

    private void startUdpListener() {
        running.set(true);

        this.udpListenerThread = new Thread(this::listenUdp, "Nekoffee-UDP-Listener-" + guildId);
        this.udpListenerThread.setDaemon(true);
        this.udpListenerThread.start();
        LOGGER.info("UDP listener started for guild {}.", guildId);
    }

    private void listenUdp() {
        try {
            if (udpSocket != null && !udpSocket.isClosed()) {
                udpSocket.setSoTimeout(0);
            }
            byte[] buf = new byte[4096];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            while (running.get() && udpSocket != null && !udpSocket.isClosed()) {
                udpSocket.receive(packet);
                if (packet.getLength() > 12) {
                    processAudioPacket(ByteBuffer.wrap(packet.getData(), 0, packet.getLength()));
                }
            }
        } catch (SocketException e) {
            if (running.get()) {
                LOGGER.debug("UDP socket closed for guild {}, listener thread exiting.", guildId);
            }
        } catch (Exception e) {
            if (running.get()) LOGGER.error("Error in UDP listener for guild {}", guildId, e);
        }
        LOGGER.debug("UDP listener loop for guild {} has finished.", guildId);
    }

    /**
     * The core audio processing logic. This is now the entry point before data is buffered and framed.
     */
    private void processAudioPacket(ByteBuffer rtpPacket) {
        try {
            if (!secretKeyLatch.await(2, TimeUnit.SECONDS)) {
                LOGGER.warn("Timed out waiting for secret key. Dropping audio packet.");
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        int ssrc = rtpPacket.getInt(8);
        String userId = ssrcToUserMap.get(ssrc);

        if (userId == null || userId.equals(this.userId) || receiveHandler == null) {
            return;
        }

        User user = userIdToUserCache.computeIfAbsent(userId, id -> {
            try {
                return client.getUserById(id).join();
            } catch (Exception e) {
                LOGGER.error("Failed to fetch user object for ID {}", id, e);
                return null;
            }
        });

        if (user == null || !receiveHandler.canReceiveUser(user)) {
            return;
        }

        OpusDecoder decoder = opusDecoders.computeIfAbsent(ssrc, k -> new OpusDecoder());


        short sequence = rtpPacket.getShort(2);
        Short lastSequence = lastSequenceMap.get(ssrc);
        if (lastSequence != null) {
            short expectedSequence = (short) (lastSequence + 1);
            if (sequence > expectedSequence) {
                int packetsLost = sequence - expectedSequence;
                if (packetsLost > 0 && packetsLost < 10) {
                    LOGGER.warn("Detected {} lost packet(s) for SSRC {}. Concealing...", packetsLost, ssrc);
                    for (int i = 0; i < packetsLost; i++) {
                        byte[] concealedPcm = decoder.decodeWithPLC();
                        if (concealedPcm != null) {

                            handlePcmData(user, ssrc, concealedPcm);
                        }
                    }
                }
            }
        }
        lastSequenceMap.put(ssrc, sequence);


        byte[] decryptedPayload = decryptPacket(rtpPacket);
        if (decryptedPayload == null) return;

        final int RTP_HEADER_SIZE = 12;
        if (decryptedPayload.length <= RTP_HEADER_SIZE) {
            return;
        }

        byte[] opusData = Arrays.copyOfRange(decryptedPayload, RTP_HEADER_SIZE, decryptedPayload.length);
        byte[] pcmAudio = decoder.decodePacket(opusData);

        if (pcmAudio != null) {

            handlePcmData(user, ssrc, pcmAudio);
        }
    }

    /**
     * New method to buffer and frame PCM data into fixed-size chunks for the mixer.
     */
    private void handlePcmData(User user, int ssrc, byte[] newPcmData) {
        if (receiveHandler == null) return;


        byte[] leftoverPcm = pcmUserBuffers.get(ssrc);

        byte[] fullBuffer;
        if (leftoverPcm != null && leftoverPcm.length > 0) {

            fullBuffer = new byte[leftoverPcm.length + newPcmData.length];
            System.arraycopy(leftoverPcm, 0, fullBuffer, 0, leftoverPcm.length);
            System.arraycopy(newPcmData, 0, fullBuffer, leftoverPcm.length, newPcmData.length);
        } else {
            fullBuffer = newPcmData;
        }

        int offset = 0;

        while (fullBuffer.length - offset >= PCM_FRAME_SIZE) {
            byte[] frameToSend = new byte[PCM_FRAME_SIZE];
            System.arraycopy(fullBuffer, offset, frameToSend, 0, PCM_FRAME_SIZE);
            receiveHandler.handleUserAudio(user, frameToSend);
            offset += PCM_FRAME_SIZE;
        }


        if (offset < fullBuffer.length) {
            byte[] remainingData = Arrays.copyOfRange(fullBuffer, offset, fullBuffer.length);
            pcmUserBuffers.put(ssrc, remainingData);
        } else {
            pcmUserBuffers.remove(ssrc);
        }
    }

    @Nullable
    private byte[] decryptPacket(ByteBuffer rtpPacket) {
        rtpPacket.position(0);
        int packetLimit = rtpPacket.limit();
        final int NONCE_SIZE = 24;

        if (packetLimit < 12 + NONCE_SIZE) {
            return null;
        }
        final byte[] nonce = new byte[NONCE_SIZE];
        rtpPacket.get(packetLimit - NONCE_SIZE, nonce, 0, NONCE_SIZE);

        int encryptedAudioLength = packetLimit - 12 - NONCE_SIZE;
        if (encryptedAudioLength <= 0) {
            return null;
        }

        final byte[] encryptedAudio = new byte[encryptedAudioLength];
        rtpPacket.position(12);
        rtpPacket.get(encryptedAudio, 0, encryptedAudioLength);

        TweetNaclFast.SecretBox cryptoBox = new TweetNaclFast.SecretBox(this.secretKey);
        return cryptoBox.open(encryptedAudio, nonce);
    }

    public void setEncryptionMode(String encryptionMode) {
        this.encryptionMode = encryptionMode;
    }

    private record HeartbeatPayload(int op, long d) {
    }

    private record IdentifyPayload(int op, IdentifyData d) {
    }

    private record IdentifyData(
            @JsonProperty("server_id") String serverId,
            @JsonProperty("user_id") String userId,
            @JsonProperty("session_id") String sessionId,
            String token
    ) {
    }

    private record SelectProtocolPayload(int op, SelectProtocolData d) {
    }

    private record SelectProtocolData(
            String protocol,
            SelectProtocolConnectionData data
    ) {
    }

    private record SelectProtocolConnectionData(
            String address,
            int port,
            String mode
    ) {
    }

    private record DiscoveredInfo(String ip, int port) {
    }

    private class VoiceGatewayHandler extends WebSocketListener {
        private final String sessionId;
        private final String token;
        private final CompletableFuture<Void> connectionFuture;
        private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "Nekoffee-Voice-Heartbeat"));

        public VoiceGatewayHandler(String sessionId, String token, CompletableFuture<Void> future) {
            this.sessionId = sessionId;
            this.token = token;
            this.connectionFuture = future;
        }

        @Override
        public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
            LOGGER.info("Voice WebSocket opened for guild {}. Sending Identify.", guildId);
            var payload = new IdentifyPayload(0, new IdentifyData(guildId, userId, sessionId, token));
            webSocket.send(jsonEngine.toJsonString(payload));
        }

        @Override
        public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
            JsonNode payload = jsonEngine.fromJsonString(text, JsonNode.class);
            int op = payload.get("op").asInt();
            JsonNode data = payload.get("d");

            switch (op) {
                case 2:
                    LOGGER.info("Voice READY received (Opcode 2). SSRC: {}, Port: {}. Starting UDP discovery.", data.get("ssrc").asInt(), data.get("port").asInt());
                    ssrc = data.get("ssrc").asInt();
                    discordUdpAddress = new InetSocketAddress(data.get("ip").asText(), data.get("port").asInt());

                    startUdpDiscovery().thenAccept(info -> {
                        LOGGER.info("UDP Discovery successful. External IP: {}, Port: {}. Sending Select Protocol.", info.ip, info.port);
                        var connData = new SelectProtocolConnectionData(info.ip, info.port, "xsalsa20_poly1305_suffix");
                        var selectPayload = new SelectProtocolPayload(1, new SelectProtocolData("udp", connData));
                        webSocket.send(jsonEngine.toJsonString(selectPayload));
                    }).exceptionally(e -> {
                        LOGGER.error("UDP IP Discovery failed, aborting connection.", e);
                        connectionFuture.completeExceptionally(e);
                        disconnect();
                        return null;
                    });
                    break;

                case 4:
                    LOGGER.info("Voice Session Description received (Opcode 4). Storing secret key.");
                    try {
                        JsonNode keyNode = data.get("secret_key");
                        if (keyNode == null || !keyNode.isArray()) {
                            throw new IOException("secret_key is missing or not a JSON array in Session Description payload.");
                        }

                        byte[] keyBytes = new byte[keyNode.size()];
                        for (int i = 0; i < keyNode.size(); i++) {
                            keyBytes[i] = (byte) keyNode.get(i).asInt();
                        }
                        VoiceConnectionImpl.this.secretKey = keyBytes;
                        VoiceConnectionImpl.this.encryptionMode = data.get("mode").asText();

                        VoiceConnectionImpl.this.secretKeyLatch.countDown();
                        voiceExecutor.submit(VoiceConnectionImpl.this::startUdpListener);
                        connectionFuture.complete(null);

                    } catch (IOException e) {
                        connectionFuture.completeExceptionally(new RuntimeException("Failed to parse secret_key", e));
                        disconnect();
                    }
                    break;

                case 5:
                    String speakingUserId = data.get("user_id").asText();
                    int speakingSsrc = data.get("ssrc").asInt();
                    ssrcToUserMap.put(speakingSsrc, speakingUserId);
                    LOGGER.trace("Speaking Update received (Opcode 5): User {} mapped to SSRC {}.", speakingUserId, speakingSsrc);
                    break;

                case 8:
                    LOGGER.info("Voice Hello received (Opcode 8). Starting heartbeat.");
                    int interval = data.get("heartbeat_interval").asInt();
                    heartbeatExecutor.scheduleAtFixedRate(() -> {
                        var heartbeat = new HeartbeatPayload(3, System.currentTimeMillis());
                        webSocket.send(jsonEngine.toJsonString(heartbeat));
                    }, 0, interval, TimeUnit.MILLISECONDS);
                    break;

                case 6:
                    LOGGER.trace("Voice Heartbeat ACK received (Opcode 6).");
                    break;

                default:
                    LOGGER.warn("Received unhandled voice opcode: {}", op);
                    break;
            }
        }

        @Override
        public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
            LOGGER.info("Voice WebSocket closing for guild {}. Code: {}, Reason: {}", guildId, code, reason);
            heartbeatExecutor.shutdownNow();
        }

        @Override
        public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
            LOGGER.error("Voice WebSocket failed for guild {}", guildId, t);
            if (!connectionFuture.isDone()) {
                connectionFuture.completeExceptionally(t);
            }
            heartbeatExecutor.shutdownNow();
            disconnect();
        }
    }
}