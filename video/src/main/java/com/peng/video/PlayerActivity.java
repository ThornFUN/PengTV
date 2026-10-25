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

public final class PlayerActivity extends Activity {
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_URL = "url";
    private ExoPlayer player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        String url = getIntent().getStringExtra(EXTRA_URL);
        if (!VideoPreferences.isHttpUrl(url)) {
            Toast.makeText(this, "频道地址无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        PlayerView playerView = new PlayerView(this);
        root.addView(playerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        TextView title = new TextView(this);
        title.setText(name == null ? "PENG影视" : name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setPadding(dp(20), dp(14), dp(20), dp(14));
        title.setBackgroundColor(Color.argb(150, 0, 0, 0));
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.LEFT);
        root.addView(title, titleParams);
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
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        player.prepare();
        player.play();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
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
        player.pause();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        player.release();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
