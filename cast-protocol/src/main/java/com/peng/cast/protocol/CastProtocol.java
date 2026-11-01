package com.peng.cast.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class CastProtocol {
    public static final int MAGIC = 0x50434153;
    public static final short VERSION = 1;
    public static final int PORT = 48999;
    public static final int VIDEO_UDP_PORT = 49000;
    public static final int MAX_PACKET_BYTES = 2 * 1024 * 1024;
    public static final int UDP_MAGIC = 0x50435544;
    public static final byte UDP_VERSION = 1;
    public static final int UDP_HEADER_BYTES = 29;
    public static final int UDP_MAX_PAYLOAD_BYTES = 1_200;
    public static final int UDP_MAX_FRAME_PACKETS = 1_024;
    public static final int FRAME_FLAG_KEY = 1;

    public static final byte TYPE_CONFIG = 1;
    public static final byte TYPE_FRAME = 2;
    public static final byte TYPE_END = 3;
    public static final byte TYPE_AUDIO_CONFIG = 4;
    public static final byte TYPE_AUDIO_FRAME = 5;

    private CastProtocol() {
    }

    public static void writeHeader(DataOutputStream output) throws IOException {
        output.writeInt(MAGIC);
        output.writeShort(VERSION);
        output.flush();
    }

    public static void readHeader(DataInputStream input) throws IOException {
        if (input.readInt() != MAGIC || input.readShort() != VERSION) {
            throw new IOException("Unsupported cast protocol");
        }
    }

    public static void writePacket(DataOutputStream output, byte type, long presentationTimeUs,
            int flags, byte[] payload) throws IOException {
        if (payload.length > MAX_PACKET_BYTES) {
            throw new IOException("Packet exceeds size limit");
        }
        output.writeByte(type);
        output.writeLong(presentationTimeUs);
        output.writeInt(flags);
        output.writeInt(payload.length);
        output.write(payload);
    }

    public static Packet readPacket(DataInputStream input) throws IOException {
        byte type = input.readByte();
        long presentationTimeUs = input.readLong();
        int flags = input.readInt();
        int length = input.readInt();
        if (length < 0 || length > MAX_PACKET_BYTES) {
            throw new IOException("Invalid packet size");
        }
        byte[] payload = new byte[length];
        input.readFully(payload);
        return new Packet(type, presentationTimeUs, flags, payload);
    }

    public static byte[] encodeVideoConfig(VideoConfig config) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(config.width);
        output.writeInt(config.height);
        output.writeInt(config.csd0.length);
        output.write(config.csd0);
        output.writeInt(config.csd1.length);
        output.write(config.csd1);
        output.flush();
        return bytes.toByteArray();
    }

    public static VideoConfig decodeVideoConfig(byte[] payload) throws IOException {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
        int width = input.readInt();
        int height = input.readInt();
        byte[] csd0 = readBuffer(input);
        byte[] csd1 = readBuffer(input);
        if (width <= 0 || height <= 0 || input.available() != 0) {
            throw new IOException("Invalid video configuration");
        }
        return new VideoConfig(width, height, csd0, csd1);
    }

    public static byte[] encodeAudioConfig(AudioConfig config) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(config.sampleRate);
        output.writeInt(config.channelCount);
        output.writeInt(config.csd0.length);
        output.write(config.csd0);
        output.flush();
        return bytes.toByteArray();
    }

    public static AudioConfig decodeAudioConfig(byte[] payload) throws IOException {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
        int sampleRate = input.readInt();
        int channelCount = input.readInt();
        byte[] csd0 = readBuffer(input);
        if (sampleRate <= 0 || channelCount < 1 || channelCount > 2 || input.available() != 0) {
            throw new IOException("Invalid audio configuration");
        }
        return new AudioConfig(sampleRate, channelCount, csd0);
    }

    private static byte[] readBuffer(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > MAX_PACKET_BYTES) {
            throw new IOException("Invalid codec configuration");
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return bytes;
    }

    public static final class Packet {
        public final byte type;
        public final long presentationTimeUs;
        public final int flags;
        public final byte[] payload;

        private Packet(byte type, long presentationTimeUs, int flags, byte[] payload) {
            this.type = type;
            this.presentationTimeUs = presentationTimeUs;
            this.flags = flags;
            this.payload = payload;
        }

    }

    public static final class VideoConfig {
        public final int width;
        public final int height;
        public final byte[] csd0;
        public final byte[] csd1;

        public VideoConfig(int width, int height, byte[] csd0, byte[] csd1) {
            this.width = width;
            this.height = height;
            this.csd0 = csd0;
            this.csd1 = csd1;
        }
    }

    public static final class AudioConfig {
        public final int sampleRate;
        public final int channelCount;
        public final byte[] csd0;

        public AudioConfig(int sampleRate, int channelCount, byte[] csd0) {
            this.sampleRate = sampleRate;
            this.channelCount = channelCount;
            this.csd0 = csd0;
        }
    }
}
