package com.peng.vod;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VodSearchActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private EditText keywordInput;
    private TextView status;
    private LinearLayout results;
    private List<VodCatalog.Item> catalog = new ArrayList<>();
    private boolean catalogReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
        loadCatalog();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(40), dp(28), dp(40), dp(28));
        root.setBackgroundColor(Color.rgb(16, 26, 42));
        root.addView(text("搜索影视", 28, Color.WHITE));
        LinearLayout searchRow = new LinearLayout(this);
        keywordInput = new EditText(this);
        keywordInput.setSingleLine(true);
        keywordInput.setTextColor(Color.WHITE);
        keywordInput.setTextSize(18);
        keywordInput.setHintTextColor(Color.rgb(150, 177, 210));
        keywordInput.setHint("输入片名、类型或简介关键词");
        keywordInput.setBackground(rounded(Color.rgb(35, 57, 88)));
        keywordInput.setPadding(dp(18), 0, dp(18), 0);
        searchRow.addView(keywordInput, new LinearLayout.LayoutParams(0, dp(60), 1.0f));
        TextView search = action("搜索");
        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                search();
            }
        });
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(dp(140), dp(60));
        searchParams.leftMargin = dp(16);
        searchRow.addView(search, searchParams);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(20);
        root.addView(searchRow, rowParams);
        status = text("正在加载全部影视源...", 17, Color.rgb(194, 213, 235));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(16);
        root.addView(status, statusParams);
        ScrollView scroll = new ScrollView(this);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(0, dp(16), 0, 0);
        scroll.addView(results);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
        setContentView(root);
        keywordInput.requestFocus();
    }

    private void loadCatalog() {
        final List<String> sources = VodPreferences.sourceUrls(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final VodCatalog.LoadResult loaded = VodCatalog.loadAll(sources);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        catalog = loaded.items;
                        catalogReady = true;
                        status.setText("已加载 " + catalog.size() + " 个条目，请输入关键词。"
                                + (loaded.failedSources == 0 ? ""
                                : " " + loaded.failedSources + " 个源加载失败。"));
                    }
                });
            }
        }, "vod-search-loader").start();
    }

    private void search() {
        if (!catalogReady) {
            status.setText("影视源仍在加载，请稍后再搜索。");
            return;
        }
        String keyword = keywordInput.getText().toString().trim().toLowerCase(Locale.US);
        if (keyword.length() == 0) {
            keywordInput.setError("请输入关键词");
            keywordInput.requestFocus();
            return;
        }
        results.removeAllViews();
        int matches = 0;
        for (final VodCatalog.Item item : catalog) {
            String searchable = (item.title + "\n" + item.category + "\n"
                    + item.description).toLowerCase(Locale.US);
            if (!searchable.contains(keyword)) {
                continue;
            }
            matches++;
            TextView result = action(item.title + "\n" + item.sourceName
                    + (item.description.length() == 0 ? "" : " · " + item.description));
            result.setGravity(Gravity.CENTER_VERTICAL);
            result.setPadding(dp(20), dp(12), dp(20), dp(12));
            result.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    VodDetailActivity.open(VodSearchActivity.this, item);
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(12);
            results.addView(result, params);
        }
        status.setText("“" + keywordInput.getText().toString().trim() + "”共找到 "
                + matches + " 个结果，已合并全部影视源。");
        if (results.getChildCount() > 0) {
            results.getChildAt(0).requestFocus();
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
        view.setClickable(true);
        view.setFocusable(true);
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
