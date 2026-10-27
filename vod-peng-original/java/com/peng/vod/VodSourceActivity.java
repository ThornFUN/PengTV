package com.peng.vod;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.Toast;
import java.util.UUID;

public final class VodSourceActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private VodPreferences.VodSource editing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showSources();
    }

    private void showSources() {
        editing = null;
        LinearLayout root = root();
        root.addView(text("影视源管理", 28, Color.WHITE));
        root.addView(description("只加载你控制或明确授权的 version=1 JSON 目录；不执行 JAR、"
                + "JavaScript、爬虫规则或远程解析代码。"));
        TextView add = action("添加影视源");
        add.setOnClickListener(v -> showEditor(null));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(220), dp(56));
        addParams.topMargin = dp(24);
        root.addView(add, addParams);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(18), 0, 0);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
        for (final VodPreferences.VodSource source : VodPreferences.sources(this)) {
            TextView card = action(source.name + (source.enabled ? "" : " · 已停用")
                    + "\n" + source.url);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(18), dp(12), dp(18), dp(12));
            card.setOnClickListener(v -> showEditor(source));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(12);
            list.addView(card, params);
        }
        if (list.getChildCount() == 0) {
            list.addView(description("尚未添加用户影视源；内置合法测试资源始终可用。"));
        } else {
            list.getChildAt(0).requestFocus();
        }
        setContentView(root);
    }

    private void showEditor(VodPreferences.VodSource source) {
        editing = source;
        LinearLayout root = root();
        root.addView(text(source == null ? "添加影视源" : "编辑影视源", 28, Color.WHITE));
        final EditText name = input("例如：公司授权影视库");
        name.setText(source == null ? "" : source.name);
        root.addView(label("名称"));
        root.addView(name, fieldParams());
        final EditText url = input("https://example.com/peng-vod.json");
        url.setText(source == null ? "" : source.url);
        root.addView(label("version=1 JSON 地址"));
        root.addView(url, fieldParams());
        LinearLayout actions = new LinearLayout(this);
        TextView test = action("测试连接");
        test.setOnClickListener(v -> test(url));
        actions.addView(test, new LinearLayout.LayoutParams(0, dp(56), 1f));
        TextView save = action("保存");
        save.setOnClickListener(v -> save(name, url));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(56), 1f);
        saveParams.leftMargin = dp(14);
        actions.addView(save, saveParams);
        root.addView(actions, margins(dp(24)));
        if (source != null) {
            LinearLayout secondary = new LinearLayout(this);
            TextView enabled = action(source.enabled ? "停用" : "启用");
            enabled.setOnClickListener(v -> {
                VodPreferences.upsertSource(this, new VodPreferences.VodSource(source.id, source.name,
                        source.url, !source.enabled));
                showSources();
            });
            secondary.addView(enabled, new LinearLayout.LayoutParams(0, dp(56), 1f));
            TextView delete = action("删除");
            delete.setOnClickListener(v -> confirmDelete(source));
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(0, dp(56), 1f);
            deleteParams.leftMargin = dp(14);
            secondary.addView(delete, deleteParams);
            root.addView(secondary, margins(dp(14)));
        }
        setContentView(root);
        name.requestFocus();
    }

    private void test(EditText input) {
        final String url = input.getText().toString().trim();
        if (!VodPreferences.isHttpUrl(url)) {
            input.setError("请输入 http:// 或 https:// JSON 地址");
            return;
        }
        Toast.makeText(this, "正在测试连接...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                int count = VodCatalog.load(url).size();
                handler.post(() -> Toast.makeText(this, "连接成功，发现 " + count + " 个条目",
                        Toast.LENGTH_LONG).show());
            } catch (Exception exception) {
                handler.post(() -> Toast.makeText(this, "连接失败：请检查地址和 version=1 格式",
                        Toast.LENGTH_LONG).show());
            }
        }, "vod-source-test").start();
    }

    private void save(EditText nameInput, EditText urlInput) {
        String name = nameInput.getText().toString().trim();
        String url = urlInput.getText().toString().trim();
        if (name.length() == 0) {
            nameInput.setError("请输入名称");
            return;
        }
        if (!VodPreferences.isHttpUrl(url)) {
            urlInput.setError("请输入 http:// 或 https:// JSON 地址");
            return;
        }
        VodPreferences.upsertSource(this, new VodPreferences.VodSource(
                editing == null ? UUID.randomUUID().toString() : editing.id, name, url,
                editing == null || editing.enabled));
        showSources();
    }

    private void confirmDelete(final VodPreferences.VodSource source) {
        new AlertDialog.Builder(this).setTitle("删除影视源")
                .setMessage("删除“" + source.name + "”后，其条目不再加载。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    VodPreferences.deleteSource(this, source.id);
                    showSources();
                }).show();
    }

    private LinearLayout root() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(40), dp(28), dp(40), dp(28));
        root.setBackgroundColor(Color.rgb(16, 26, 42));
        return root;
    }

    private LinearLayout.LayoutParams margins(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = top;
        return params;
    }

    private LinearLayout.LayoutParams fieldParams() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
    }

    private EditText input(String hint) {
        EditText view = new EditText(this);
        view.setSingleLine(true);
        view.setHint(hint);
        view.setTextColor(Color.WHITE);
        view.setHintTextColor(Color.rgb(150, 177, 210));
        view.setTextSize(17);
        view.setPadding(dp(18), 0, dp(18), 0);
        view.setBackground(rounded(Color.rgb(35, 57, 88)));
        return view;
    }

    private TextView label(String value) {
        TextView label = text(value, 17, Color.WHITE);
        label.setPadding(0, dp(18), 0, dp(8));
        return label;
    }

    private TextView description(String value) {
        return text(value, 16, Color.rgb(194, 213, 235));
    }

    private TextView action(String value) {
        TextView view = text(value, 18, Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setFocusable(true);
        view.setClickable(true);
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
