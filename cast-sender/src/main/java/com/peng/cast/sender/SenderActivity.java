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
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class SenderActivity extends Activity {
    private static final int REQUEST_CAPTURE = 100;
    private static final int REQUEST_RECORD_AUDIO = 101;
    private static final int MAX_RECEIVER_HISTORY = 10;
    private static final String PREFERENCES_NAME = "cast";
    private static final String RECEIVER_HISTORY_KEY = "receiver_history";
    private static final String LEGACY_RECEIVER_HOST_KEY = "receiver_host";
    private static final String MICROPHONE_CAPTURE_KEY = "microphone_capture";

    private AutoCompleteTextView receiverAddress;
    private ArrayAdapter<String> receiverAddressAdapter;
    private CheckBox microphoneCapture;
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
                + "\n\n开启卓易通兼容模式时，手机会通过麦克风采集外放声音。");
        instructions.setTextSize(18);
        root.addView(instructions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        receiverAddress = new AutoCompleteTextView(this);
        receiverAddress.setHint("电视 IP，例如 192.168.31.239");
        receiverAddress.setInputType(InputType.TYPE_CLASS_TEXT);
        receiverAddress.setSingleLine(true);
        receiverAddressAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_dropdown_item_1line, loadReceiverHistory());
        receiverAddress.setAdapter(receiverAddressAdapter);
        receiverAddress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                receiverAddress.showDropDown();
            }
        });
        List<String> receiverHistory = loadReceiverHistory();
        if (!receiverHistory.isEmpty()) {
            receiverAddress.setText(receiverHistory.get(0));
            receiverAddress.setSelection(receiverAddress.length());
        }
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        addressParams.topMargin = dp(24);
        root.addView(receiverAddress, addressParams);

        microphoneCapture = new CheckBox(this);
        microphoneCapture.setText("卓易通兼容：通过麦克风采集声音");
        microphoneCapture.setChecked(getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
                .getBoolean(MICROPHONE_CAPTURE_KEY, true));
        root.addView(microphoneCapture, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

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

        Button stopButton = new Button(this);
        stopButton.setText("停止投屏");
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopCasting();
            }
        });
        LinearLayout.LayoutParams stopButtonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stopButtonParams.topMargin = dp(8);
        root.addView(stopButton, stopButtonParams);

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
        rememberReceiverHost(host);
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE).edit()
                .putBoolean(MICROPHONE_CAPTURE_KEY, microphoneCapture.isChecked())
                .apply();
        boolean needsAudioPermission = microphoneCapture.isChecked()
                || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
        if (needsAudioPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
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
                .putExtra(SenderService.EXTRA_PROJECTION_DATA, data)
                .putExtra(SenderService.EXTRA_CAPTURE_MICROPHONE, microphoneCapture.isChecked());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "正在连接电视", Toast.LENGTH_SHORT).show();
        statusView.setText("正在连接电视…");
    }

    private void stopCasting() {
        Intent serviceIntent = new Intent(this, SenderService.class)
                .setAction(SenderService.ACTION_STOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        statusView.setText("已停止投屏");
    }

    private List<String> loadReceiverHistory() {
        android.content.SharedPreferences preferences =
                getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);
        String stored = preferences.getString(RECEIVER_HISTORY_KEY, "");
        if (stored.isEmpty()) {
            stored = preferences.getString(LEGACY_RECEIVER_HOST_KEY, "");
        }
        List<String> hosts = new ArrayList<String>();
        if (stored.isEmpty()) {
            return hosts;
        }
        for (String host : stored.split("\n")) {
            if (!host.isEmpty() && !hosts.contains(host)) {
                hosts.add(host);
            }
        }
        return hosts;
    }

    private void rememberReceiverHost(String host) {
        List<String> hosts = loadReceiverHistory();
        hosts.remove(host);
        hosts.add(0, host);
        if (hosts.size() > MAX_RECEIVER_HISTORY) {
            hosts = new ArrayList<String>(hosts.subList(0, MAX_RECEIVER_HISTORY));
        }
        StringBuilder stored = new StringBuilder();
        for (String item : hosts) {
            if (stored.length() > 0) {
                stored.append('\n');
            }
            stored.append(item);
        }
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE).edit()
                .putString(RECEIVER_HISTORY_KEY, stored.toString())
                .apply();
        receiverAddressAdapter.clear();
        receiverAddressAdapter.addAll(hosts);
        receiverAddressAdapter.notifyDataSetChanged();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
