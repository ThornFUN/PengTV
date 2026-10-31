package com.peng.cast.sender;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.Surface;

import com.peng.cast.protocol.CastProtocol;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

public final class SenderService extends Service {
    public static final String ACTION_SESSION_ERROR = "com.peng.cast.sender.SESSION_ERROR";
    public static final String EXTRA_ERROR_MESSAGE = "error_message";

    private static final String TAG = "CastSender";
    public static final String EXTRA_HOST = "host";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_PROJECTION_DATA = "projection_data";

    private static final String CHANNEL_ID = "cast";
    private static final int NOTIFICATION_ID = 10;

    private volatile boolean streaming;
    private Thread streamThread;
    private MediaProjection projection;
    private MediaCodec encoder;
    private VirtualDisplay virtualDisplay;
    private Socket socket;
    private DataOutputStream output;
    private PowerManager.WakeLock wakeLock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        promoteToForeground();
        if (intent == null || streaming) {
            return START_NOT_STICKY;
        }
        String host = intent.getStringExtra(EXTRA_HOST);
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent projectionData = intent.getParcelableExtra(EXTRA_PROJECTION_DATA);
        if (host == null || host.trim().isEmpty() || projectionData == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        startStreaming(host.trim(), resultCode, projectionData);
        return START_NOT_STICKY;
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

    private void startStreaming(final String host, final int resultCode, final Intent projectionData) {
        streaming = true;
        streamThread = new Thread(new Runnable() {
            @Override
            public void run() {
                stream(host, resultCode, projectionData);
            }
        }, "cast-sender");
        streamThread.start();
    }

    private void stream(String host, int resultCode, Intent projectionData) {
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

            connect(host);
            startEncoder();
            drainEncoder();
        } catch (IOException | RuntimeException exception) {
            Log.e(TAG, "Screen cast session failed", exception);
            sendSessionError(exception.getMessage());
        } finally {
            stopStreaming();
            stopSelf();
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
        int[] dimensions = scaleTo720p(metrics.widthPixels, metrics.heightPixels);
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
                    metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
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
                        CastProtocol.writePacket(output, CastProtocol.TYPE_FRAME,
                                bufferInfo.presentationTimeUs, bufferInfo.flags, payload);
                        output.flush();
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
        CastProtocol.writePacket(output, CastProtocol.TYPE_CONFIG, 0, 0,
                CastProtocol.encodeVideoConfig(config));
        output.flush();
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
        if (!streaming) {
            return;
        }
        streaming = false;
        DataOutputStream activeOutput = output;
        if (activeOutput != null) {
            try {
                CastProtocol.writePacket(activeOutput, CastProtocol.TYPE_END, 0, 0, new byte[0]);
                activeOutput.flush();
            } catch (IOException ignored) {
            }
        }
        output = null;
        closeSocket();
        releaseVirtualDisplay();
        releaseEncoder();
        releaseProjection();
        releaseWakeLock();
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
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
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
