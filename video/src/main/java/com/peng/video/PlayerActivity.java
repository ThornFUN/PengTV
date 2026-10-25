package com.peng.video;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
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
    private TextView title;
    private TextView channelNumber;
    private String fallbackName;
    private String fallbackUrl;
    private int currentIndex;

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
        root.addView(playerView, new FrameLayout.LayoutParams(
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
        currentIndex = channel == null ? 0 : index;
        title.setText(name == null ? "PENG影视" : name);
        channelNumber.setText(String.format(Locale.US, "%03d", currentIndex + 1));
        VideoPreferences.saveLastChannelUrl(this, url);
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        player.prepare();
        player.play();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
