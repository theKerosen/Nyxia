package com.ladyluh.nekoffee.opus;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * JNA interface for the native Opus library functions required for decoding.
 * This class binds to the 'opus' shared library (e.g., libopus.so, opus.dll, opus.dylib).
 */
public interface Opus extends Library {


    Opus INSTANCE = Native.load("opus", Opus.class);


    int OPUS_OK = 0;
    int OPUS_BAD_ARG = -1;
    int OPUS_BUFFER_TOO_SMALL = -2;
    int OPUS_INTERNAL_ERROR = -3;
    int OPUS_INVALID_PACKET = -4;
    int OPUS_UNIMPLEMENTED = -5;
    int OPUS_INVALID_STATE = -6;
    int OPUS_ALLOC_FAIL = -7;

    /**
     * Creates a new Opus decoder state.
     *
     * @param Fs       The sampling rate of the input signal (Hz). This must be one of 8000, 12000, 16000, 24000, or 48000.
     * @param channels Number of channels (1 for mono, 2 for stereo).
     * @param error    (Out) Returns the error code.
     * @return A newly created decoder state or NULL on error.
     */
    Pointer opus_decoder_create(int Fs, int channels, IntBuffer error);

    /**
     * Decodes an Opus packet.
     *
     * @param st         Decoder state.
     * @param in         Input payload. Use NULL to indicate packet loss.
     * @param len        Number of bytes in the payload.
     * @param out        Output signal (interleaved if 2 channels). Length is `frame_size * channels`.
     * @param frame_size Number of samples per channel of the internal Coder.
     * @param decode_fec Flag (0 or 1) to decode the last packet with FEC (if any).
     * @return Number of samples decoded or a negative error code.
     */
    int opus_decode(Pointer st, byte[] in, int len, ShortBuffer out, int frame_size, int decode_fec);

    /**
     * Destroys an Opus decoder state.
     *
     * @param st Decoder state.
     */
    void opus_decoder_destroy(Pointer st);

    /**
     * Converts an Opus error code into a human readable string.
     *
     * @param error The error code to be converted.
     * @return The error string.
     */
    String opus_strerror(int error);
}