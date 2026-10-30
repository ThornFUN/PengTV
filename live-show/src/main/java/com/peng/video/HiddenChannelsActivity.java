package com.peng.video;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Collections;
import java.util.List;

public final class HiddenChannelsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showChannels();
    }

    private void showChannels() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(40), dp(28), dp(40), dp(28));
        root.setBackgroundColor(Color.rgb(16, 26, 42));

        TextView title = text("已隐藏频道", 28, Color.WHITE);
        root.addView(title);
        TextView description = text("选中频道后按确认恢复到频道列表。", 16,
                Color.rgb(194, 213, 235));
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(10);
        root.addView(description, descriptionParams);

        ScrollView scroll = new ScrollView(this);
        LinearLayout channels = new LinearLayout(this);
        channels.setOrientation(LinearLayout.VERTICAL);
        channels.setPadding(0, dp(18), 0, 0);
        scroll.addView(channels);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        List<String> hidden = VideoPreferences.hiddenChannels(this);
        Collections.sort(hidden);
        if (hidden.isEmpty()) {
            channels.addView(text("没有已隐藏频道。", 18, Color.rgb(194, 213, 235)));
        } else {
            for (final String channelName : hidden) {
                TextView channel = text(channelName + "\n按确认恢复", 18, Color.WHITE);
                channel.setGravity(Gravity.CENTER_VERTICAL);
                channel.setPadding(dp(20), dp(14), dp(20), dp(14));
                channel.setFocusable(true);
                channel.setClickable(true);
                channel.setBackground(focusBackground());
                channel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        VideoPreferences.restoreChannel(HiddenChannelsActivity.this, channelName);
                        Toast.makeText(HiddenChannelsActivity.this, "频道已恢复", Toast.LENGTH_SHORT).show();
                        showChannels();
                    }
                });
                LinearLayout.LayoutParams channelParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                channelParams.bottomMargin = dp(12);
                channels.addView(channel, channelParams);
            }
            channels.getChildAt(0).requestFocus();
        }
        setContentView(root);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
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
