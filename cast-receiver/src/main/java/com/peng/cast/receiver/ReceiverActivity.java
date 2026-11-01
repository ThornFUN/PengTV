package com.peng.cast.receiver;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.peng.cast.protocol.CastProtocol;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.HashMap;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Map;
import java.net.SocketException;
import java.util.Enumeration;

public final class ReceiverActivity extends Activity implements SurfaceHolder.Callback,
        ReceiverService.ReceiverListener {
    private static final String TAG = "CastReceiverActivity";
    private ReceiverService receiverService;
    private boolean bound;
    private TextView statusView;
    private ImageView downloadQrCodeView;
    private ImageView castAddressQrCodeView;
    private LinearLayout waitingPanel;
    private AspectRatioSurfaceView surfaceView;

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

        FrameLayout root = new FrameLayout(this);
        surfaceView = new AspectRatioSurfaceView(this);
        surfaceView.setId(R.id.receiver_surface);
        surfaceView.getHolder().addCallback(this);
        root.addView(surfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));

        waitingPanel = new LinearLayout(this);
        waitingPanel.setOrientation(LinearLayout.VERTICAL);
        waitingPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        waitingPanel.setBackgroundColor(0x99000000);
        int panelPadding = dp(24);
        waitingPanel.setPadding(panelPadding, panelPadding, panelPadding, panelPadding);

        statusView = new TextView(this);
        statusView.setTextColor(0xFFFFFFFF);
        statusView.setTextSize(28);
        statusView.setGravity(Gravity.CENTER);
        waitingPanel.addView(statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout qrRow = new LinearLayout(this);
        qrRow.setOrientation(LinearLayout.HORIZONTAL);
        qrRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams qrRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qrRowParams.topMargin = dp(16);
        waitingPanel.addView(qrRow, qrRowParams);

        LinearLayout downloadQrPanel = createQrPanel("扫码下载发送端");
        downloadQrCodeView = new ImageView(this);
        downloadQrCodeView.setBackgroundColor(0xFFFFFFFF);
        downloadQrCodeView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        downloadQrPanel.addView(downloadQrCodeView,
                new LinearLayout.LayoutParams(dp(260), dp(260)));
        qrRow.addView(downloadQrPanel);

        LinearLayout castAddressQrPanel = createQrPanel("扫码获取电视投屏地址");
        castAddressQrCodeView = new ImageView(this);
        castAddressQrCodeView.setBackgroundColor(0xFFFFFFFF);
        castAddressQrCodeView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        castAddressQrPanel.addView(castAddressQrCodeView,
                new LinearLayout.LayoutParams(dp(260), dp(260)));
        LinearLayout.LayoutParams castQrPanelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        castQrPanelParams.leftMargin = dp(32);
        qrRow.addView(castAddressQrPanel, castQrPanelParams);
        root.addView(waitingPanel, new FrameLayout.LayoutParams(
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
                downloadQrCodeView.setVisibility(View.GONE);
                castAddressQrCodeView.setVisibility(View.GONE);
                waitingPanel.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onFirstFrameRendered() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                waitingPanel.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onVideoConfigured(final int width, final int height) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                surfaceView.setAspectRatio(width, height);
            }
        });
    }

    @Override
    public void onSenderDisconnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateStatus();
                waitingPanel.setVisibility(View.VISIBLE);
                surfaceView.setAspectRatio(0, 0);
            }
        });
    }

    private void updateStatus() {
        String localIp = getLocalIp();
        String downloadUrl = "http://" + localIp + ":"
                + ReceiverService.DOWNLOAD_PORT + ReceiverService.SENDER_APK_PATH;
        String castAddress = localIp + ":" + CastProtocol.PORT;
        statusView.setText("扫描左侧二维码下载安装发送端\n"
                + "扫描右侧二维码获取电视投屏地址\n\n"
                + "等待手机连接\nIP: " + castAddress);
        try {
            downloadQrCodeView.setImageBitmap(generateQrCode(downloadUrl, dp(260)));
            castAddressQrCodeView.setImageBitmap(generateQrCode(castAddress, dp(260)));
            downloadQrCodeView.setVisibility(View.VISIBLE);
            castAddressQrCodeView.setVisibility(View.VISIBLE);
        } catch (WriterException exception) {
            Log.e(TAG, "Unable to generate cast QR codes", exception);
            downloadQrCodeView.setVisibility(View.GONE);
            castAddressQrCodeView.setVisibility(View.GONE);
            statusView.setText("无法生成二维码\n下载地址：" + downloadUrl
                    + "\n投屏地址：" + castAddress);
        }
    }

    private LinearLayout createQrPanel(String label) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(0xFFFFFFFF);
        labelView.setTextSize(24);
        labelView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.bottomMargin = dp(8);
        panel.addView(labelView, labelParams);
        return panel;
    }

    private Bitmap generateQrCode(String content, int size) throws WriterException {
        Map<EncodeHintType, Object> hints = new HashMap<EncodeHintType, Object>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size,
                hints);
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pixels[y * size + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
