package com.peng.vod;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VodMainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private LinearLayout items;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCatalog();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(34), dp(24), dp(34), dp(28));
        root.setBackgroundColor(Color.rgb(16, 26, 42));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text("PENG影视", 30, Color.WHITE), new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        TextView search = action("搜索");
        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(VodMainActivity.this, VodSearchActivity.class));
            }
        });
        header.addView(search, new LinearLayout.LayoutParams(dp(110), dp(54)));
        TextView source = action("影视源");
        source.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(VodMainActivity.this, VodSourceActivity.class));
            }
        });
        LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(dp(140), dp(54));
        sourceParams.leftMargin = dp(14);
        header.addView(source, sourceParams);
        root.addView(header);
        status = text("", 17, Color.rgb(194, 213, 235));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(14);
        root.addView(status, statusParams);
        ScrollView scroll = new ScrollView(this);
        items = new LinearLayout(this);
        items.setOrientation(LinearLayout.VERTICAL);
        items.setPadding(0, dp(18), 0, 0);
        scroll.addView(items);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
        setContentView(root);
    }

    private void loadCatalog() {
        final List<String> sources = VodPreferences.sourceUrls(this);
        items.removeAllViews();
        status.setText("正在加载影视源...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final VodCatalog.LoadResult catalog = VodCatalog.loadAll(sources);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        showCatalog(catalog.items);
                        if (sources.isEmpty()) {
                            status.setText("已加载 2 个内置合法测试资源。请选择“影视源”追加受信任的 version=1 JSON 地址。");
                        } else if (catalog.failedSources > 0) {
                            status.setText("已加载 " + catalog.items.size() + " 个影视条目；"
                                    + catalog.failedSources + " 个源加载失败。");
                        }
                    }
                });
            }
        }, "vod-loader").start();
    }

    private void showCatalog(List<VodCatalog.Item> catalog) {
        status.setText("已加载 " + catalog.size() + " 个影视条目");
        Map<String, List<VodCatalog.Item>> groups = new LinkedHashMap<>();
        for (VodCatalog.Item item : catalog) {
            List<VodCatalog.Item> group = groups.get(item.category);
            if (group == null) {
                group = new java.util.ArrayList<>();
                groups.put(item.category, group);
            }
            group.add(item);
        }
        for (Map.Entry<String, List<VodCatalog.Item>> entry : groups.entrySet()) {
            TextView title = text(entry.getKey(), 22, Color.WHITE);
            items.addView(title);
            for (final VodCatalog.Item item : entry.getValue()) {
                TextView card = action(item.title + "\n" + item.sourceName
                        + (item.description.length() == 0 ? "" : " · " + item.description));
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(dp(20), dp(12), dp(20), dp(12));
                card.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        VodPlayerActivity.open(VodMainActivity.this, item.title, item.url);
                    }
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                params.topMargin = dp(10);
                items.addView(card, params);
            }
        }
        if (items.getChildCount() > 1) {
            items.getChildAt(1).requestFocus();
        }
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        return view;
    }

    private TextView action(String value) {
        TextView view = text(value, 18, Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setFocusable(true);
        view.setClickable(true);
        view.setBackground(focusBackground());
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
