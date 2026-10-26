package com.peng.vod;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public final class VodDetailActivity extends Activity {
    private static final String EXTRA_ITEM = "item";
    private VodCatalog.Item item;
    private List<VodCatalog.Stream> streams;
    private int selectedLine;
    private TextView selection;

    static void open(Context context, VodCatalog.Item item) {
        Intent intent = new Intent(context, VodDetailActivity.class);
        intent.putExtra(EXTRA_ITEM, item.toJson());
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        item = VodCatalog.Item.fromJson(getIntent().getStringExtra(EXTRA_ITEM));
        if (item == null) {
            finish();
            return;
        }
        streams = item.episodes.isEmpty() ? item.streams : item.episodes.get(0).streams;
        buildScreen();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(40), dp(28), dp(40), dp(28));
        root.setBackgroundColor(Color.rgb(16, 26, 42));
        root.addView(text(item.title, 30, Color.WHITE));
        root.addView(text(item.category + " · " + item.sourceName, 17, Color.rgb(194, 213, 235)));
        if (item.description.length() > 0) {
            TextView description = text(item.description, 18, Color.WHITE);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(18);
            root.addView(description, params);
        }
        selection = text(selectionText(), 17, Color.rgb(194, 213, 235));
        LinearLayout.LayoutParams selectionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        selectionParams.topMargin = dp(20);
        root.addView(selection, selectionParams);
        TextView play = action("播放");
        play.setOnClickListener(v -> VodPlayerActivity.open(this, item.title, streams, selectedLine));
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(dp(220), dp(60));
        playParams.topMargin = dp(16);
        root.addView(play, playParams);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(22), 0, 0);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        if (!item.episodes.isEmpty()) {
            body.addView(text("选集", 22, Color.WHITE));
            for (int index = 0; index < item.episodes.size(); index++) {
                final VodCatalog.Episode episode = item.episodes.get(index);
                TextView button = action((episode.season.length() == 0 ? "" : episode.season + " · ")
                        + episode.title);
                button.setOnClickListener(v -> {
                    streams = episode.streams;
                    selectedLine = 0;
                    buildScreen();
                });
                addRow(body, button);
            }
        }
        body.addView(text("线路", 22, Color.WHITE));
        for (int index = 0; index < streams.size(); index++) {
            final int line = index;
            TextView button = action(streams.get(index).name + (index == selectedLine ? " · 已选择" : ""));
            button.setOnClickListener(v -> {
                selectedLine = line;
                buildScreen();
            });
            addRow(body, button);
        }
        setContentView(root);
        play.requestFocus();
    }

    private String selectionText() {
        if (streams.isEmpty()) {
            return "没有可播放线路";
        }
        return "当前线路：" + streams.get(selectedLine).name;
    }

    private void addRow(LinearLayout parent, View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        parent.addView(view, params);
    }

    private TextView action(String value) {
        TextView view = text(value, 18, Color.WHITE);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(20), dp(14), dp(20), dp(14));
        view.setClickable(true);
        view.setFocusable(true);
        view.setBackground(focusBackground());
        return view;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
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
