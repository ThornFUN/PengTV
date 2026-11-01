package com.peng.cast.sender;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import com.peng.cast.protocol.CastProtocol;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

public final class SenderService extends Service {
    public static final String ACTION_SESSION_ERROR = "com.peng.cast.sender.SESSION_ERROR";
    public static final String ACTION_RECONNECTING = "com.peng.cast.sender.RECONNECTING";
    public static final String ACTION_STOP = "com.peng.cast.sender.STOP";
    public static final String EXTRA_ERROR_MESSAGE = "error_message";

    private static final String TAG = "CastSender";
    public static final String EXTRA_HOST = "host";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_PROJECTION_DATA = "projection_data";
    public static final String EXTRA_CAPTURE_MICROPHONE = "capture_microphone";

    private static final String CHANNEL_ID = "cast";
    private static final int NOTIFICATION_ID = 10;
    private static final int AUDIO_SAMPLE_RATE = 48_000;
    private static final long RECONNECT_DELAY_MS = 1_000;

    private volatile boolean streaming;
    private volatile boolean audioStreaming;
    private final Object outputLock = new Object();
    private Thread streamThread;
    private Thread audioThread;
    private MediaProjection projection;
    private MediaCodec encoder;
    private MediaCodec audioEncoder;
    private VirtualDisplay virtualDisplay;
    private AudioRecord audioRecord;
    private Socket socket;
    private DataOutputStream output;
    private PowerManager.WakeLock wakeLock;
    private WindowManager windowManager;
    private View overlayView;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopStreaming();
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        promoteToForeground();
        if (intent == null || streaming) {
            return START_STICKY;
        }

        String host = intent.getStringExtra(EXTRA_HOST);
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent projectionData = intent.getParcelableExtra(EXTRA_PROJECTION_DATA);
        boolean captureMicrophone = intent.getBooleanExtra(EXTRA_CAPTURE_MICROPHONE, false);
        if (host == null || host.trim().isEmpty() || projectionData == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        startStreaming(host.trim(), resultCode, projectionData, captureMicrophone);
        return START_STICKY;
    }

    private int[] getCaptureDimensions() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return scaleTo720p(metrics.widthPixels, metrics.heightPixels);
    }

    @Override
    public void onDestroy() {
        stopStreaming();
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.i(TAG, "Sender task removed; retaining active foreground projection service");
        if (streaming) {
            promoteToForeground();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startStreaming(final String host, final int resultCode, final Intent projectionData,
            final boolean captureMicrophone) {
        streaming = true;
        streamThread = new Thread(new Runnable() {
            @Override
            public void run() {
                stream(host, resultCode, projectionData, captureMicrophone);
            }
        }, "cast-sender");
        streamThread.start();
    }

    private void stream(String host, int resultCode, Intent projectionData,
            boolean captureMicrophone) {
        try {
            acquireWakeLock();
            MediaProjectionManager manager = (MediaProjectionManager)
                    getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, projectionData);
            projection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    stopStreaming();
                }
            }, new Handler(Looper.getMainLooper()));

            showOverlay();
            while (streaming) {
                try {
                    connect(host);
                    startEncoder();
                    startAudioCaptureIfAvailable(captureMicrophone);
                    drainEncoder();
                } catch (IOException | RuntimeException exception) {
                    if (!streaming) {
                        break;
                    }
                    Log.w(TAG, "Cast connection lost; reconnecting", exception);
                    resetConnectionForRetry();
                    sendBroadcast(new Intent(ACTION_RECONNECTING).setPackage(getPackageName()));
                    waitBeforeReconnect();
                }
            }
        } catch (RuntimeException exception) {
            Log.e(TAG, "Screen cast session failed", exception);
            sendSessionError(exception.getMessage());
        } finally {
            stopStreaming();
            stopSelf();
        }
    }

    private void resetConnectionForRetry() {
        stopAudioCapture();
        synchronized (outputLock) {
            output = null;
        }
        closeSocket();
        releaseVirtualDisplay();
        releaseEncoder();
    }

    private void waitBeforeReconnect() {
        try {
            Thread.sleep(RECONNECT_DELAY_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            stopStreaming();
        }
    }

    private void connect(String host) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, CastProtocol.PORT), 5_000);
        socket.setTcpNoDelay(true);
        output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        CastProtocol.writeHeader(output);
    }

    private void startEncoder() throws IOException {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int[] dimensions = getCaptureDimensions();
        MediaFormat format = MediaFormat.createVideoFormat("video/avc", dimensions[0], dimensions[1]);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 3_000_000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        format.setInteger(MediaFormat.KEY_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
        format.setInteger(MediaFormat.KEY_LEVEL,
                MediaCodecInfo.CodecProfileLevel.AVCLevel31);
        try {
            encoder = MediaCodec.createEncoderByType("video/avc");
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Surface inputSurface = encoder.createInputSurface();
            encoder.start();
            virtualDisplay = projection.createVirtualDisplay("PENG投屏", dimensions[0], dimensions[1],
                    metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    inputSurface, null, null);
        } catch (RuntimeException exception) {
            releaseEncoder();
            throw new IOException("Unable to start H.264 encoder", exception);
        }
    }

    private void drainEncoder() throws IOException {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        ByteBuffer[] outputBuffers = encoder.getOutputBuffers();
        while (streaming) {
            int outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000);
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                sendVideoConfig(encoder.getOutputFormat());
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = encoder.getOutputBuffers();
            } else if (outputIndex >= 0) {
                try {
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                            && bufferInfo.size > 0) {
                        ByteBuffer encoded = outputBuffers[outputIndex].duplicate();
                        encoded.position(bufferInfo.offset);
                        encoded.limit(bufferInfo.offset + bufferInfo.size);
                        byte[] payload = new byte[bufferInfo.size];
                        encoded.get(payload);
                        writePacket(CastProtocol.TYPE_FRAME,
                                bufferInfo.presentationTimeUs, bufferInfo.flags, payload);
                    }
                } finally {
                    encoder.releaseOutputBuffer(outputIndex, false);
                }
            }
        }
    }

    private void sendVideoConfig(MediaFormat format) throws IOException {
        ByteBuffer csd0 = format.getByteBuffer("csd-0");
        ByteBuffer csd1 = format.getByteBuffer("csd-1");
        if (csd0 == null || csd1 == null) {
            throw new IOException("Encoder did not provide H.264 configuration");
        }
        CastProtocol.VideoConfig config = new CastProtocol.VideoConfig(
                format.getInteger(MediaFormat.KEY_WIDTH),
                format.getInteger(MediaFormat.KEY_HEIGHT),
                copyBuffer(csd0),
                copyBuffer(csd1));
        writePacket(CastProtocol.TYPE_CONFIG, 0, 0,
                CastProtocol.encodeVideoConfig(config));
    }

    private void startAudioCaptureIfAvailable(boolean captureMicrophone) {
        if (audioThread != null || audioEncoder != null) {
            return;
        }
        if (captureMicrophone) {
            startMicrophoneAudioCapture();
        } else {
            startPlaybackAudioCapture();
        }
    }

    private void startPlaybackAudioCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !hasRecordAudioPermission()) {
            Log.i(TAG, "Application audio capture is unavailable on this device or permission was denied");
            return;
        }
        try {
            AudioFormat audioFormat = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(AUDIO_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build();
            int minimumBufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimumBufferSize <= 0) {
                throw new IllegalStateException("Unsupported playback audio format");
            }
            AudioPlaybackCaptureConfiguration captureConfig =
                    new AudioPlaybackCaptureConfiguration.Builder(projection)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .build();
            AudioRecord record = new AudioRecord.Builder()
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(minimumBufferSize * 2)
                    .setAudioPlaybackCaptureConfig(captureConfig)
                    .build();
            startAudioEncoder(record, 2);
        } catch (IOException | RuntimeException exception) {
            Log.w(TAG, "Unable to capture application audio; continuing with video only", exception);
            releaseAudioResources();
        }
    }

    private void startMicrophoneAudioCapture() {
        if (!hasRecordAudioPermission()) {
            Log.i(TAG, "Microphone capture permission was denied");
            return;
        }
        try {
            int minimumBufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimumBufferSize <= 0) {
                throw new IllegalStateException("Unsupported microphone audio format");
            }
            AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.MIC, AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    minimumBufferSize * 2);
            startAudioEncoder(record, 1);
        } catch (IOException | RuntimeException exception) {
            Log.w(TAG, "Unable to capture microphone audio; continuing with video only", exception);
            releaseAudioResources();
        }
    }

    private void startAudioEncoder(AudioRecord record, int channelCount) throws IOException {
        audioRecord = record;
        MediaFormat encoderFormat = MediaFormat.createAudioFormat("audio/mp4a-latm",
                AUDIO_SAMPLE_RATE, channelCount);
        encoderFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128_000);
        audioEncoder = MediaCodec.createEncoderByType("audio/mp4a-latm");
        audioEncoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioEncoder.start();
        audioRecord.startRecording();
        audioStreaming = true;
        audioThread = new Thread(new Runnable() {
            @Override
            public void run() {
                captureAudio();
            }
        }, "cast-audio");
        audioThread.start();
    }

    private boolean hasRecordAudioPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void captureAudio() {
        byte[] pcm = new byte[4_096];
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        ByteBuffer[] outputBuffers = audioEncoder.getOutputBuffers();
        try {
            while (streaming && audioStreaming) {
                int inputIndex = audioEncoder.dequeueInputBuffer(10_000);
                if (inputIndex < 0) {
                    outputBuffers = drainAudioEncoder(bufferInfo, outputBuffers);
                    continue;
                }
                int byteCount = audioRecord.read(pcm, 0, pcm.length);
                if (byteCount <= 0) {
                    continue;
                }
                ByteBuffer inputBuffer = audioEncoder.getInputBuffers()[inputIndex];
                inputBuffer.clear();
                inputBuffer.put(pcm, 0, byteCount);
                audioEncoder.queueInputBuffer(inputIndex, 0, byteCount,
                        System.nanoTime() / 1_000L, 0);
                outputBuffers = drainAudioEncoder(bufferInfo, outputBuffers);
            }
        } catch (RuntimeException | IOException exception) {
            if (streaming && audioStreaming) {
                Log.w(TAG, "Application audio capture stopped; continuing with video only", exception);
            }
        } finally {
            audioStreaming = false;
            releaseAudioResources();
        }
    }

    private ByteBuffer[] drainAudioEncoder(MediaCodec.BufferInfo bufferInfo,
            ByteBuffer[] outputBuffers) throws IOException {
        while (true) {
            int outputIndex = audioEncoder.dequeueOutputBuffer(bufferInfo, 0);
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                sendAudioConfig(audioEncoder.getOutputFormat());
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = audioEncoder.getOutputBuffers();
            } else if (outputIndex >= 0) {
                try {
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                            && bufferInfo.size > 0) {
                        ByteBuffer encoded = outputBuffers[outputIndex].duplicate();
                        encoded.position(bufferInfo.offset);
                        encoded.limit(bufferInfo.offset + bufferInfo.size);
                        byte[] payload = new byte[bufferInfo.size];
                        encoded.get(payload);
                        writePacket(CastProtocol.TYPE_AUDIO_FRAME,
                                bufferInfo.presentationTimeUs, bufferInfo.flags, payload);
                    }
                } finally {
                    audioEncoder.releaseOutputBuffer(outputIndex, false);
                }
            } else {
                return outputBuffers;
            }
        }
    }

    private void sendAudioConfig(MediaFormat format) throws IOException {
        ByteBuffer csd0 = format.getByteBuffer("csd-0");
        if (csd0 == null) {
            throw new IOException("AAC encoder did not provide codec configuration");
        }
        CastProtocol.AudioConfig config = new CastProtocol.AudioConfig(
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                copyBuffer(csd0));
        writePacket(CastProtocol.TYPE_AUDIO_CONFIG, 0, 0,
                CastProtocol.encodeAudioConfig(config));
    }

    private void writePacket(byte type, long presentationTimeUs, int flags, byte[] payload)
            throws IOException {
        synchronized (outputLock) {
            if (output == null) {
                throw new IOException("Cast connection is closed");
            }
            CastProtocol.writePacket(output, type, presentationTimeUs, flags, payload);
            output.flush();
        }
    }

    private static byte[] copyBuffer(ByteBuffer source) {
        ByteBuffer copy = source.duplicate();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }

    private static int[] scaleTo720p(int width, int height) {
        if (height > width) {
            return new int[]{1280, 720};
        }
        int longestEdge = Math.max(width, height);
        float scale = longestEdge > 1280 ? 1280f / longestEdge : 1f;
        int scaledWidth = alignTo16(Math.max(16, Math.round(width * scale)));
        int scaledHeight = alignTo16(Math.max(16, Math.round(height * scale)));
        return new int[]{scaledWidth, scaledHeight};
    }

    private static int alignTo16(int value) {
        return value & ~15;
    }

    private void sendSessionError(String message) {
        sendBroadcast(new Intent(ACTION_SESSION_ERROR)
                .setPackage(getPackageName())
                .putExtra(EXTRA_ERROR_MESSAGE,
                        message == null ? "无法连接电视或启动屏幕编码" : message));
    }

    private void stopStreaming() {
        hideOverlay();
        if (!streaming) {
            return;
        }
        streaming = false;
        stopAudioCapture();
        synchronized (outputLock) {
            try {
                if (output != null) {
                    CastProtocol.writePacket(output, CastProtocol.TYPE_END, 0, 0, new byte[0]);
                    output.flush();
                }
            } catch (IOException ignored) {
            }
            output = null;
        }
        closeSocket();
        releaseVirtualDisplay();
        releaseEncoder();
        releaseProjection();
        releaseWakeLock();
    }

    private void showOverlay() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (overlayView != null) {
                    return;
                }
                View view = new View(SenderService.this);
                view.setBackgroundColor(0x00000000);
                WindowManager.LayoutParams parameters = new WindowManager.LayoutParams(
                        1,
                        1,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        android.graphics.PixelFormat.TRANSLUCENT);
                parameters.gravity = Gravity.TOP | Gravity.END;
                parameters.x = 0;
                parameters.y = 0;
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
                if (addOverlayView(view, parameters)) {
                    overlayView = view;
                } else {
                    windowManager = null;
                }
            }
        });
    }

    private boolean addOverlayView(View view, WindowManager.LayoutParams parameters) {
        try {
            windowManager.addView(view, parameters);
            return true;
        } catch (RuntimeException exception) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                Log.w(TAG, "Unable to show cast overlay", exception);
                return false;
            }
            parameters.type = WindowManager.LayoutParams.TYPE_PHONE;
            try {
                windowManager.addView(view, parameters);
                return true;
            } catch (RuntimeException fallbackException) {
                Log.w(TAG, "Unable to show cast overlay", fallbackException);
                return false;
            }
        }
    }

    private void hideOverlay() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (overlayView == null || windowManager == null) {
                    return;
                }
                windowManager.removeView(overlayView);
                overlayView = null;
                windowManager = null;
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void closeSocket() {
        Socket activeSocket = socket;
        socket = null;
        if (activeSocket != null) {
            try {
                activeSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void releaseVirtualDisplay() {
        VirtualDisplay activeDisplay = virtualDisplay;
        virtualDisplay = null;
        if (activeDisplay != null) {
            activeDisplay.release();
        }
    }

    private void releaseEncoder() {
        MediaCodec activeEncoder = encoder;
        encoder = null;
        if (activeEncoder != null) {
            try {
                activeEncoder.stop();
            } catch (Exception ignored) {
            }
            try {
                activeEncoder.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void stopAudioCapture() {
        audioStreaming = false;
        AudioRecord activeRecord = audioRecord;
        if (activeRecord != null) {
            try {
                activeRecord.stop();
            } catch (IllegalStateException ignored) {
            }
        }
        Thread activeThread = audioThread;
        if (activeThread != null && activeThread != Thread.currentThread()) {
            activeThread.interrupt();
            try {
                activeThread.join(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        if (activeThread == null || !activeThread.isAlive()) {
            releaseAudioResources();
        }
    }

    private void releaseAudioResources() {
        AudioRecord activeRecord = audioRecord;
        audioRecord = null;
        if (activeRecord != null) {
            try {
                activeRecord.stop();
            } catch (IllegalStateException ignored) {
            }
            activeRecord.release();
        }
        MediaCodec activeEncoder = audioEncoder;
        audioEncoder = null;
        if (activeEncoder != null) {
            try {
                activeEncoder.stop();
            } catch (Exception ignored) {
            }
            try {
                activeEncoder.release();
            } catch (Exception ignored) {
            }
        }
        audioThread = null;
    }

    private void releaseProjection() {
        MediaProjection activeProjection = projection;
        projection = null;
        if (activeProjection != null) {
            activeProjection.stop();
        }
    }

    private void promoteToForeground() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int serviceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                serviceTypes |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            }
            startForeground(NOTIFICATION_ID, notification,
                    serviceTypes);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void acquireWakeLock() {
        if (wakeLock != null) {
            return;
        }
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock activeWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "PENG投屏:projection");
        activeWakeLock.setReferenceCounted(false);
        activeWakeLock.acquire();
        wakeLock = activeWakeLock;
    }

    private void releaseWakeLock() {
        PowerManager.WakeLock activeWakeLock = wakeLock;
        wakeLock = null;
        if (activeWakeLock != null && activeWakeLock.isHeld()) {
            activeWakeLock.release();
        }
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
        Intent launchIntent = new Intent(this, SenderActivity.class);
        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, launchIntent,
                pendingIntentFlags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.presence_video_online)
                .setContentTitle(getString(R.string.notification_title))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }
}
