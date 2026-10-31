package com.peng.cast.receiver;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.peng.cast.protocol.CastProtocol;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public final class ReceiverActivity extends Activity implements SurfaceHolder.Callback,
        ReceiverService.ReceiverListener {
    private ReceiverService receiverService;
    private boolean bound;
    private TextView statusView;
    private FrameLayout root;
    private SurfaceView surfaceView;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            receiverService = ((ReceiverService.LocalBinder) service).getService();
            bound = true;
            receiverService.startReceiver();
            receiverService.setReceiverListener(ReceiverActivity.this);
            receiverService.setSurface(surfaceView.getHolder().getSurface());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            receiverService = null;
            bound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        surfaceView = new SurfaceView(this);
        surfaceView.setId(R.id.receiver_surface);
        surfaceView.getHolder().addCallback(this);
        root.addView(surfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        statusView = new TextView(this);
        statusView.setTextColor(0xFFFFFFFF);
        statusView.setTextSize(60);
        statusView.setGravity(Gravity.CENTER);
        statusView.setBackgroundColor(0x99000000);
        statusView.setPadding(24, 16, 24, 16);
        root.addView(statusView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        setContentView(root);
        updateStatus();

        Intent serviceIntent = new Intent(this, ReceiverService.class);
        startService(serviceIntent);
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        if (bound) {
            receiverService.setReceiverListener(null);
            receiverService.setSurface(null);
            unbindService(connection);
        }
        super.onDestroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (receiverService != null) {
            receiverService.setSurface(holder.getSurface());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (receiverService != null) {
            receiverService.setSurface(null);
        }
    }

    @Override
    public void onSenderConnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusView.setVisibility(View.VISIBLE);
                statusView.setText("正在接收画面…");
            }
        });
    }

    @Override
    public void onFirstFrameRendered() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusView.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onVideoConfigured(final int width, final int height) {
        surfaceView.post(new Runnable() {
            @Override
            public void run() {
                resizeVideoSurface(width, height);
            }
        });
    }

    @Override
    public void onSenderDisconnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateStatus();
                statusView.setVisibility(View.VISIBLE);
                resetVideoSurface();
            }
        });
    }

    private void resizeVideoSurface(int videoWidth, int videoHeight) {
        int containerWidth = root.getWidth();
        int containerHeight = root.getHeight();
        if (containerWidth == 0 || containerHeight == 0 || videoWidth <= 0 || videoHeight <= 0) {
            return;
        }
        float videoAspect = (float) videoWidth / videoHeight;
        float containerAspect = (float) containerWidth / containerHeight;
        int width = containerWidth;
        int height = containerHeight;
        if (videoAspect > containerAspect) {
            width = Math.round(containerHeight * videoAspect);
        } else {
            height = Math.round(containerWidth / videoAspect);
        }
        surfaceView.setLayoutParams(new FrameLayout.LayoutParams(width, height, Gravity.CENTER));
    }

    private void resetVideoSurface() {
        surfaceView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void updateStatus() {
        statusView.setText("等待手机连接\nIP: " + getLocalIp() + ":" + CastProtocol.PORT);
    }

    private String getLocalIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            return "无法读取网络地址";
        }
        return "未连接网络";
    }
}
