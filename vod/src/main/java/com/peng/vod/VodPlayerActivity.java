package com.peng.vod;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;
import java.util.ArrayList;
import java.util.List;

public final class VodPlayerActivity extends Activity {
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_URLS = "urls";
    private static final String EXTRA_NAMES = "names";
    private static final String EXTRA_LINE = "line";
    private ExoPlayer player;
    private LinearLayout controls;
    private TextView playPause;
    private TextView message;
    private ArrayList<String> urls;
    private ArrayList<String> names;
    private int lineIndex;

    static void open(Context context, String title, List<VodCatalog.Stream> streams, int selectedLine) {
        ArrayList<String> urls = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        for (VodCatalog.Stream stream : streams) {
            urls.add(stream.url);
            names.add(stream.name);
        }
        Intent intent = new Intent(context, VodPlayerActivity.class);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putStringArrayListExtra(EXTRA_URLS, urls);
        intent.putStringArrayListExtra(EXTRA_NAMES, names);
        intent.putExtra(EXTRA_LINE, selectedLine);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        urls = getIntent().getStringArrayListExtra(EXTRA_URLS);
        names = getIntent().getStringArrayListExtra(EXTRA_NAMES);
        lineIndex = getIntent().getIntExtra(EXTRA_LINE, 0);
        if (urls == null || urls.isEmpty() || lineIndex < 0 || lineIndex >= urls.size()) {
            finish();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        PlayerView playerView = new PlayerView(this);
        playerView.setUseController(false);
        root.addView(playerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        message = new TextView(this);
        message.setTextColor(Color.WHITE);
        message.setTextSize(20);
        message.setGravity(Gravity.CENTER);
        message.setBackgroundColor(Color.BLACK);
        root.addView(message, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        TextView title = new TextView(this);
        title.setText(getIntent().getStringExtra(EXTRA_TITLE));
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setPadding(dp(20), dp(14), dp(20), dp(14));
        title.setBackgroundColor(Color.argb(150, 0, 0, 0));
        root.addView(title, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.LEFT));
        buildControls(root);
        setContentView(root);
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    message.setVisibility(View.GONE);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                message.setText("播放失败，请切换线路或返回详情页");
                message.setVisibility(View.VISIBLE);
            }
        });
        prepareLine();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (controls.getVisibility() == View.VISIBLE) {
                return true;
            }
            showControls();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (controls.getVisibility() == View.VISIBLE) {
            controls.setVisibility(View.GONE);
            return;
        }
        finish();
    }

    @Override
    protected void onStop() {
        if (player != null) {
            player.pause();
        }
        super.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (player != null) {
            player.play();
        }
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            player.release();
        }
        super.onDestroy();
    }

    private void buildControls(FrameLayout root) {
        controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(28), dp(18), dp(28), dp(18));
        controls.setBackgroundColor(Color.argb(210, 16, 26, 42));
        playPause = control("暂停");
        playPause.setOnClickListener(v -> {
            if (player.isPlaying()) {
                player.pause();
                playPause.setText("继续播放");
            } else {
                player.play();
                playPause.setText("暂停");
            }
        });
        controls.addView(playPause, new LinearLayout.LayoutParams(dp(160), dp(56)));
        TextView line = control("线路");
        line.setOnClickListener(v -> {
            lineIndex = (lineIndex + 1) % urls.size();
            prepareLine();
        });
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(dp(160), dp(56));
        lineParams.leftMargin = dp(14);
        controls.addView(line, lineParams);
        TextView exit = control("退出播放");
        exit.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams exitParams = new LinearLayout.LayoutParams(dp(180), dp(56));
        exitParams.leftMargin = dp(14);
        controls.addView(exit, exitParams);
        controls.setVisibility(View.GONE);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = dp(34);
        root.addView(controls, params);
    }

    private TextView control(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(18);
        view.setGravity(Gravity.CENTER);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(62, 119, 184));
        background.setCornerRadius(dp(12));
        view.setBackground(background);
        view.setFocusable(true);
        view.setClickable(true);
        return view;
    }

    private void showControls() {
        controls.setVisibility(View.VISIBLE);
        playPause.setText(player.isPlaying() ? "暂停" : "继续播放");
        playPause.requestFocus();
    }

    private void prepareLine() {
        message.setText("正在加载 " + names.get(lineIndex) + "...");
        message.setVisibility(View.VISIBLE);
        player.setMediaItem(MediaItem.fromUri(urls.get(lineIndex)));
        player.prepare();
        player.play();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
