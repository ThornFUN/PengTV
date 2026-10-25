package com.peng.video;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;
import java.util.ArrayList;
import java.util.List;

public final class LineSelectionActivity extends Activity {
    private static final String EXTRA_CHANNEL_NAME = "channel_name";
    private static final String EXTRA_URLS = "urls";
    private ExoPlayer previewPlayer;
    private TextView previewStatus;
    private String previewUrl;

    static void open(Context context, String channelName, List<String> urls) {
        Intent intent = new Intent(context, LineSelectionActivity.class);
        intent.putExtra(EXTRA_CHANNEL_NAME, channelName);
        intent.putStringArrayListExtra(EXTRA_URLS, new ArrayList<>(urls));
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String channelName = getIntent().getStringExtra(EXTRA_CHANNEL_NAME);
        ArrayList<String> urls = getIntent().getStringArrayListExtra(EXTRA_URLS);
        if (channelName == null || urls == null || urls.isEmpty()) {
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(40), dp(28), dp(40), dp(28));
        root.setBackgroundColor(Color.rgb(16, 26, 42));

        TextView title = text(channelName + " · 选择默认线路", 28, Color.WHITE);
        root.addView(title);
        TextView description = text("在频道列表选中频道后按菜单键，也可随时修改默认线路。", 16,
                Color.rgb(194, 213, 235));
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(10);
        root.addView(description, descriptionParams);

        FrameLayout preview = new FrameLayout(this);
        preview.setBackgroundColor(Color.BLACK);
        PlayerView playerView = new PlayerView(this);
        playerView.setKeepContentOnPlayerReset(false);
        preview.addView(playerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        previewStatus = text("正在预览线路...", 18, Color.WHITE);
        previewStatus.setGravity(Gravity.CENTER);
        previewStatus.setBackgroundColor(Color.argb(150, 0, 0, 0));
        preview.addView(previewStatus, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(250));
        previewParams.topMargin = dp(18);
        root.addView(preview, previewParams);

        previewPlayer = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(previewPlayer);
        previewPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    previewStatus.setText("当前线路预览正常");
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                previewStatus.setText("该线路无法播放，请尝试其他线路");
            }
        });

        ScrollView scroll = new ScrollView(this);
        LinearLayout lines = new LinearLayout(this);
        lines.setOrientation(LinearLayout.VERTICAL);
        lines.setPadding(0, dp(18), 0, 0);
        scroll.addView(lines);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        String preferredUrl = VideoPreferences.defaultLineUrl(this, channelName);
        if (urls.remove(preferredUrl)) {
            urls.add(0, preferredUrl);
        }
        for (int index = 0; index < urls.size(); index++) {
            final String url = urls.get(index);
            TextView line = text("线路 " + (index + 1)
                    + (url.equals(preferredUrl) ? " · 当前默认" : "") + "\n" + url, 17, Color.WHITE);
            line.setPadding(dp(20), dp(14), dp(20), dp(14));
            line.setBackground(focusBackground());
            line.setClickable(true);
            line.setFocusable(true);
            line.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View view, boolean focused) {
                    if (focused) {
                        previewUrl(url);
                    }
                }
            });
            line.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    VideoPreferences.saveDefaultLineUrl(LineSelectionActivity.this, channelName, url);
                    Toast.makeText(LineSelectionActivity.this, "已设为默认线路", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lineParams.bottomMargin = dp(12);
            lines.addView(line, lineParams);
            if (index == 0) {
                line.requestFocus();
            }
        }
        setContentView(root);
    }

    @Override
    protected void onStop() {
        if (previewPlayer != null) {
            previewPlayer.pause();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (previewPlayer != null) {
            previewPlayer.release();
        }
        super.onDestroy();
    }

    private void previewUrl(String url) {
        if (url.equals(previewUrl)) {
            return;
        }
        previewUrl = url;
        previewStatus.setText("正在预览线路...");
        previewPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        previewPlayer.prepare();
        previewPlayer.play();
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private StateListDrawable focusBackground() {
        StateListDrawable drawable = new StateListDrawable();
        GradientDrawable focused = rounded(Color.rgb(62, 119, 184));
        focused.setStroke(dp(2), Color.WHITE);
        drawable.addState(new int[]{android.R.attr.state_focused}, focused);
        drawable.addState(new int[]{}, rounded(Color.rgb(35, 57, 88)));
        return drawable;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(12));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
