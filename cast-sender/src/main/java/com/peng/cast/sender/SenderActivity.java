package com.peng.cast.sender;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class SenderActivity extends Activity {
    private static final int REQUEST_CAPTURE = 100;
    private static final int REQUEST_RECORD_AUDIO = 101;
    private EditText receiverAddress;
    private TextView statusView;

    private final BroadcastReceiver sessionErrorReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            statusView.setText("投屏已停止：" + intent.getStringExtra(SenderService.EXTRA_ERROR_MESSAGE));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int padding = dp(24);
        root.setPadding(padding, padding, padding, padding);

        TextView instructions = new TextView(this);
        instructions.setText("在电视打开 PENG投屏接收器，输入电视显示的 IP 地址后开始投屏。"
                + "\n\nAndroid 10 及以上会同时投送允许采集的应用声音。");
        instructions.setTextSize(18);
        root.addView(instructions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        receiverAddress = new EditText(this);
        receiverAddress.setHint("电视 IP，例如 192.168.31.239");
        receiverAddress.setInputType(InputType.TYPE_CLASS_TEXT);
        receiverAddress.setSingleLine(true);
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        addressParams.topMargin = dp(24);
        root.addView(receiverAddress, addressParams);

        Button startButton = new Button(this);
        startButton.setText("开始投屏");
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestProjectionWithAudioPermission();
            }
        });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = dp(16);
        root.addView(startButton, buttonParams);

        statusView = new TextView(this);
        statusView.setTextSize(14);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(12);
        root.addView(statusView, statusParams);
        setContentView(root);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(SenderService.ACTION_SESSION_ERROR);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sessionErrorReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(sessionErrorReceiver, filter);
        }
    }

    @Override
    protected void onStop() {
        unregisterReceiver(sessionErrorReceiver);
        super.onStop();
    }

    private void requestProjectionWithAudioPermission() {
        String host = receiverAddress.getText().toString().trim();
        if (host.isEmpty()) {
            receiverAddress.setError("请输入电视 IP 地址");
            return;
        }
        getSharedPreferences("cast", MODE_PRIVATE).edit().putString("receiver_host", host).apply();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }
        requestProjection();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RECORD_AUDIO) {
            return;
        }
        if (grantResults.length == 0
                || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "未授予录音权限，将只投送画面", Toast.LENGTH_LONG).show();
        }
        requestProjection();
    }

    private void requestProjection() {
        MediaProjectionManager manager = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CAPTURE || resultCode != RESULT_OK || data == null) {
            return;
        }
        Intent serviceIntent = new Intent(this, SenderService.class)
                .putExtra(SenderService.EXTRA_HOST, receiverAddress.getText().toString().trim())
                .putExtra(SenderService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(SenderService.EXTRA_PROJECTION_DATA, data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "正在连接电视", Toast.LENGTH_SHORT).show();
        statusView.setText("正在连接电视…");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
