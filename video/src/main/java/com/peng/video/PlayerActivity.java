package com.peng.video;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.ui.PlayerView;
import java.util.Locale;

public final class PlayerActivity extends Activity {
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_INDEX = "index";
    private ExoPlayer player;
    private final Handler overlayHandler = new Handler(Looper.getMainLooper());
    private final Handler channelInputHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideOverlay = new Runnable() {
        @Override
        public void run() {
            title.setVisibility(View.INVISIBLE);
            channelNumber.setVisibility(View.INVISIBLE);
        }
    };
    private TextView title;
    private TextView channelNumber;
    private TextView loadingOverlay;
    private String fallbackName;
    private String fallbackUrl;
    private int currentIndex;
    private final StringBuilder channelInput = new StringBuilder();
    private final Runnable submitChannelInput = new Runnable() {
        @Override
        public void run() {
            submitChannelInput();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fallbackName = getIntent().getStringExtra(EXTRA_NAME);
        fallbackUrl = getIntent().getStringExtra(EXTRA_URL);
        if (!VideoPreferences.isHttpUrl(fallbackUrl)) {
            Toast.makeText(this, "频道地址无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        PlayerView playerView = new PlayerView(this);
        playerView.setKeepContentOnPlayerReset(false);
        root.addView(playerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        loadingOverlay = new TextView(this);
        loadingOverlay.setText("正在加载...");
        loadingOverlay.setTextColor(Color.WHITE);
        loadingOverlay.setTextSize(20);
        loadingOverlay.setGravity(Gravity.CENTER);
        loadingOverlay.setBackgroundColor(Color.BLACK);
        root.addView(loadingOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setPadding(dp(20), dp(14), dp(20), dp(14));
        title.setBackgroundColor(Color.argb(150, 0, 0, 0));
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.LEFT);
        root.addView(title, titleParams);
        channelNumber = new TextView(this);
        channelNumber.setTextColor(Color.WHITE);
        channelNumber.setTextSize(34);
        channelNumber.setGravity(Gravity.CENTER);
        channelNumber.setPadding(dp(22), dp(8), dp(22), dp(8));
        channelNumber.setBackgroundColor(Color.argb(150, 0, 0, 0));
        FrameLayout.LayoutParams numberParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.RIGHT);
        root.addView(channelNumber, numberParams);
        setContentView(root);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    loadingOverlay.setVisibility(View.GONE);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (error.getCause() instanceof BehindLiveWindowException) {
                    player.seekToDefaultPosition();
                    player.prepare();
                    player.play();
                    return;
                }
                Toast.makeText(PlayerActivity.this, "播放失败，请检查直播源", Toast.LENGTH_SHORT).show();
            }
        });
        playChannel(getIntent().getIntExtra(EXTRA_INDEX, 0));
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            appendChannelDigit(keyCode - KeyEvent.KEYCODE_0);
            return true;
        }
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
                && channelInput.length() > 0) {
            submitChannelInput();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            int channelCount = PlaybackQueue.size();
            if (channelCount > 1) {
                int offset = keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                playChannel((currentIndex + offset + channelCount) % channelCount);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.play();
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onStop() {
        overlayHandler.removeCallbacks(hideOverlay);
        channelInputHandler.removeCallbacks(submitChannelInput);
        if (player != null) {
            player.pause();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            player.release();
        }
        super.onDestroy();
    }

    private void playChannel(int index) {
        VideoMainActivity.Channel channel = PlaybackQueue.channelAt(index);
        String name = channel == null ? fallbackName : channel.name;
        String url = channel == null ? fallbackUrl : channel.url;
        if (!VideoPreferences.isHttpUrl(url)) {
            Toast.makeText(this, "频道地址无效", Toast.LENGTH_SHORT).show();
            return;
        }
        channelInput.setLength(0);
        channelInputHandler.removeCallbacks(submitChannelInput);
        currentIndex = channel == null ? 0 : index;
        title.setText(name == null ? "PENG影视" : name);
        channelNumber.setText(String.format(Locale.US, "%03d", currentIndex + 1));
        showChannelOverlay();
        VideoPreferences.saveLastChannelUrl(this, url);
        loadingOverlay.setVisibility(View.VISIBLE);
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        player.prepare();
        player.play();
    }

    private void appendChannelDigit(int digit) {
        if (channelInput.length() == 3) {
            channelInput.setLength(0);
        }
        channelInput.append(digit);
        title.setText("输入频道编号后按确认");
        channelNumber.setText(channelInput.toString());
        showChannelOverlay();
        channelInputHandler.removeCallbacks(submitChannelInput);
        channelInputHandler.postDelayed(submitChannelInput, 1500);
    }

    private void submitChannelInput() {
        if (channelInput.length() == 0) {
            return;
        }
        int channelNumberValue;
        try {
            channelNumberValue = Integer.parseInt(channelInput.toString());
        } catch (NumberFormatException exception) {
            channelInput.setLength(0);
            return;
        }
        channelInput.setLength(0);
        channelInputHandler.removeCallbacks(submitChannelInput);
        int channelCount = PlaybackQueue.size();
        if (channelNumberValue < 1 || channelNumberValue > channelCount) {
            Toast.makeText(this, "没有编号 " + channelNumberValue + " 的频道", Toast.LENGTH_SHORT).show();
            return;
        }
        playChannel(channelNumberValue - 1);
    }

    private void showChannelOverlay() {
        title.setVisibility(View.VISIBLE);
        channelNumber.setVisibility(View.VISIBLE);
        overlayHandler.removeCallbacks(hideOverlay);
        overlayHandler.postDelayed(hideOverlay, 5000);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
