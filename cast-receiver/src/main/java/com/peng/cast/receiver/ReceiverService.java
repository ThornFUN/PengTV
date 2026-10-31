package com.peng.cast.receiver;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.view.Surface;

import com.peng.cast.protocol.CastProtocol;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

public final class ReceiverService extends Service {
    private static final String TAG = "CastReceiver";
    private final LocalBinder binder = new LocalBinder();
    private final Object lifecycleLock = new Object();
    private volatile Surface surface;
    private volatile boolean running;
    private Thread serverThread;
    private ServerSocket serverSocket;
    private MediaCodec decoder;
    private MediaCodec audioDecoder;
    private AudioTrack audioTrack;
    private boolean firstFrameLogged;
    private boolean firstFrameRendered;
    private volatile ReceiverListener receiverListener;

    public interface ReceiverListener {
        void onSenderConnected();

        void onFirstFrameRendered();

        void onSenderDisconnected();
    }

    public final class LocalBinder extends Binder {
        ReceiverService getService() {
            return ReceiverService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startReceiver();
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Intent launchIntent = new Intent(this, ReceiverActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setContentTitle(getString(R.string.notification_title))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
        startForeground(1, notification);
    }

    @Override
    public void onDestroy() {
        stopReceiver();
        super.onDestroy();
    }

    public void setSurface(Surface newSurface) {
        surface = newSurface;
    }

    public void setReceiverListener(ReceiverListener listener) {
        receiverListener = listener;
    }

    public void startReceiver() {
        synchronized (lifecycleLock) {
            if (running) {
                return;
            }
            running = true;
            serverThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    acceptConnections();
                }
            }, "cast-receiver");
            serverThread.start();
        }
    }

    private void stopReceiver() {
        running = false;
        closeServerSocket();
        releaseDecoder();
        releaseAudioDecoder();
    }

    private void acceptConnections() {
        try {
            serverSocket = new ServerSocket(CastProtocol.PORT);
            while (running) {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                notifySenderConnected();
                handleConnection(socket);
            }
        } catch (IOException exception) {
            if (running) {
                Log.e(TAG, "Receiver socket failed", exception);
            }
        } finally {
            closeServerSocket();
        }
    }

    private void handleConnection(Socket socket) {
        try {
            DataInputStream input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            CastProtocol.readHeader(input);
            while (running && !socket.isClosed()) {
                CastProtocol.Packet packet = CastProtocol.readPacket(input);
                if (packet.type == CastProtocol.TYPE_CONFIG) {
                    configureDecoder(CastProtocol.decodeVideoConfig(packet.payload));
                } else if (packet.type == CastProtocol.TYPE_FRAME) {
                    queueFrame(packet);
                } else if (packet.type == CastProtocol.TYPE_AUDIO_CONFIG) {
                    configureAudioDecoder(CastProtocol.decodeAudioConfig(packet.payload));
                } else if (packet.type == CastProtocol.TYPE_AUDIO_FRAME) {
                    queueAudio(packet);
                } else if (packet.type == CastProtocol.TYPE_END) {
                    break;
                }
            }
        } catch (IOException exception) {
            Log.w(TAG, "Sender disconnected or sent an invalid stream", exception);
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            releaseDecoder();
            releaseAudioDecoder();
            notifySenderDisconnected();
        }
    }

    private void configureDecoder(CastProtocol.VideoConfig config) throws IOException {
        Surface target = surface;
        if (target == null || !target.isValid()) {
            throw new IOException("Display surface is unavailable");
        }
        Log.i(TAG, "Configuring H.264 decoder for " + config.width + "x" + config.height
                + ", csd-0=" + describeBytes(config.csd0));
        firstFrameLogged = false;
        firstFrameRendered = false;
        releaseDecoder();
        try {
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", config.width, config.height);
            format.setByteBuffer("csd-0", ByteBuffer.wrap(combineCsd(config.csd0, config.csd1)));
            decoder = MediaCodec.createDecoderByType("video/avc");
            decoder.configure(format, target, null, 0);
            decoder.start();
        } catch (RuntimeException exception) {
            releaseDecoder();
            Log.e(TAG, "Unable to start H.264 decoder for " + config.width + "x" + config.height,
                    exception);
            throw new IOException("Unable to start H.264 decoder", exception);
        }
    }

    private void queueFrame(CastProtocol.Packet packet) {
        MediaCodec activeDecoder = decoder;
        if (activeDecoder == null) {
            return;
        }
        try {
            if (!firstFrameLogged) {
                firstFrameLogged = true;
                Log.i(TAG, "First H.264 frame, flags=" + packet.flags
                        + ", bytes=" + describeBytes(packet.payload));
            }
            int inputIndex = activeDecoder.dequeueInputBuffer(10_000);
            if (inputIndex >= 0) {
                ByteBuffer inputBuffer = activeDecoder.getInputBuffers()[inputIndex];
                inputBuffer.clear();
                inputBuffer.put(packet.payload);
                activeDecoder.queueInputBuffer(inputIndex, 0, packet.payload.length,
                        packet.presentationTimeUs, packet.flags);
            }
            drainDecoder(activeDecoder);
        } catch (RuntimeException exception) {
            Log.e(TAG, "H.264 decoder rejected a video frame", exception);
            releaseDecoder();
        }
    }

    private void drainDecoder(MediaCodec activeDecoder) {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        while (true) {
            int outputIndex = activeDecoder.dequeueOutputBuffer(bufferInfo, 0);
            if (outputIndex >= 0) {
                activeDecoder.releaseOutputBuffer(outputIndex, bufferInfo.size > 0);
                if (bufferInfo.size > 0 && !firstFrameRendered) {
                    firstFrameRendered = true;
                    notifyFirstFrameRendered();
                }
            } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                return;
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
                    || outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                continue;
            } else {
                return;
            }
        }
    }

    private void releaseDecoder() {
        MediaCodec activeDecoder = decoder;
        decoder = null;
        if (activeDecoder != null) {
            try {
                activeDecoder.stop();
            } catch (Exception ignored) {
            }
            try {
                activeDecoder.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void configureAudioDecoder(CastProtocol.AudioConfig config) throws IOException {
        releaseAudioDecoder();
        try {
            MediaFormat format = MediaFormat.createAudioFormat("audio/mp4a-latm",
                    config.sampleRate, config.channelCount);
            format.setByteBuffer("csd-0", ByteBuffer.wrap(config.csd0));
            audioDecoder = MediaCodec.createDecoderByType("audio/mp4a-latm");
            audioDecoder.configure(format, null, null, 0);
            int channelConfig = config.channelCount == 1
                    ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
            int minimumBufferSize = AudioTrack.getMinBufferSize(config.sampleRate, channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (minimumBufferSize <= 0) {
                throw new IOException("Unsupported AAC playback format");
            }
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, config.sampleRate, channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT, minimumBufferSize * 2,
                    AudioTrack.MODE_STREAM);
            audioDecoder.start();
            audioTrack.play();
            Log.i(TAG, "Configured AAC audio playback for " + config.sampleRate + " Hz, "
                    + config.channelCount + " channels");
        } catch (RuntimeException exception) {
            releaseAudioDecoder();
            throw new IOException("Unable to start AAC decoder", exception);
        }
    }

    private void queueAudio(CastProtocol.Packet packet) {
        MediaCodec activeDecoder = audioDecoder;
        if (activeDecoder == null) {
            return;
        }
        try {
            int inputIndex = activeDecoder.dequeueInputBuffer(10_000);
            if (inputIndex >= 0) {
                ByteBuffer inputBuffer = activeDecoder.getInputBuffers()[inputIndex];
                inputBuffer.clear();
                inputBuffer.put(packet.payload);
                activeDecoder.queueInputBuffer(inputIndex, 0, packet.payload.length,
                        packet.presentationTimeUs, packet.flags);
            }
            drainAudioDecoder(activeDecoder);
        } catch (RuntimeException exception) {
            Log.e(TAG, "AAC decoder rejected an audio frame", exception);
            releaseAudioDecoder();
        }
    }

    private void drainAudioDecoder(MediaCodec activeDecoder) {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        ByteBuffer[] outputBuffers = activeDecoder.getOutputBuffers();
        while (true) {
            int outputIndex = activeDecoder.dequeueOutputBuffer(bufferInfo, 0);
            if (outputIndex >= 0) {
                try {
                    AudioTrack activeTrack = audioTrack;
                    if (activeTrack != null && bufferInfo.size > 0) {
                        ByteBuffer decoded = outputBuffers[outputIndex].duplicate();
                        decoded.position(bufferInfo.offset);
                        decoded.limit(bufferInfo.offset + bufferInfo.size);
                        byte[] pcm = new byte[bufferInfo.size];
                        decoded.get(pcm);
                        activeTrack.write(pcm, 0, pcm.length);
                    }
                } finally {
                    activeDecoder.releaseOutputBuffer(outputIndex, false);
                }
            } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                return;
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                continue;
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = activeDecoder.getOutputBuffers();
            } else {
                return;
            }
        }
    }

    private void releaseAudioDecoder() {
        AudioTrack activeTrack = audioTrack;
        audioTrack = null;
        if (activeTrack != null) {
            try {
                activeTrack.stop();
            } catch (IllegalStateException ignored) {
            }
            activeTrack.release();
        }
        MediaCodec activeDecoder = audioDecoder;
        audioDecoder = null;
        if (activeDecoder != null) {
            try {
                activeDecoder.stop();
            } catch (Exception ignored) {
            }
            try {
                activeDecoder.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void closeServerSocket() {
        ServerSocket activeSocket = serverSocket;
        serverSocket = null;
        if (activeSocket != null) {
            try {
                activeSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void notifySenderConnected() {
        ReceiverListener listener = receiverListener;
        if (listener != null) {
            listener.onSenderConnected();
        }
    }

    private void notifyFirstFrameRendered() {
        ReceiverListener listener = receiverListener;
        if (listener != null) {
            listener.onFirstFrameRendered();
        }
    }

    private void notifySenderDisconnected() {
        ReceiverListener listener = receiverListener;
        if (listener != null) {
            listener.onSenderDisconnected();
        }
    }

    private static String describeBytes(byte[] bytes) {
        int length = Math.min(bytes.length, 12);
        StringBuilder text = new StringBuilder(length * 2);
        for (int index = 0; index < length; index++) {
            text.append(String.format("%02x", bytes[index] & 0xFF));
        }
        return text.toString();
    }

    private static byte[] combineCsd(byte[] csd0, byte[] csd1) {
        byte[] combined = new byte[csd0.length + csd1.length];
        System.arraycopy(csd0, 0, combined, 0, csd0.length);
        System.arraycopy(csd1, 0, combined, csd0.length, csd1.length);
        return combined;
    }
}
