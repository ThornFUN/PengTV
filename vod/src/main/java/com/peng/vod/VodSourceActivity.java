package com.peng.vod;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class VodSourceActivity extends Activity {
    private EditText sourceInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showForm();
    }

    private void showForm() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(40), dp(28), dp(40), dp(28));
        root.setBackgroundColor(Color.rgb(16, 26, 42));
        root.addView(text("影视源设置", 28, Color.WHITE));
        TextView description = text("仅加载你控制或明确授权的 version=1 JSON 配置。"
                + "不支持 JAR、JavaScript、爬虫规则或来源不明的执行代码。", 17,
                Color.rgb(194, 213, 235));
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(14);
        root.addView(description, descriptionParams);
        TextView label = text("影视源 JSON 地址（每行一个）", 18, Color.WHITE);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(28);
        root.addView(label, labelParams);
        sourceInput = new EditText(this);
        sourceInput.setSingleLine(false);
        sourceInput.setTextColor(Color.WHITE);
        sourceInput.setTextSize(17);
        sourceInput.setHintTextColor(Color.rgb(150, 177, 210));
        sourceInput.setHint("https://example.com/peng-vod.json\nhttps://example.com/company-vod.json");
        sourceInput.setText(joinSources(VodPreferences.sourceUrls(this)));
        sourceInput.setBackground(inputBackground());
        sourceInput.setPadding(dp(18), 0, dp(18), 0);
        root.addView(sourceInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(150)));
        TextView schema = text("格式：{\"version\":1,\"items\":[{\"category\":\"电影\","
                + "\"title\":\"示例\",\"description\":\"简介\",\"streams\":[{\"url\":\"https://...m3u8\"}]}]}",
                15, Color.rgb(194, 213, 235));
        LinearLayout.LayoutParams schemaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        schemaParams.topMargin = dp(20);
        root.addView(schema, schemaParams);
        TextView save = action("保存影视源");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                save();
            }
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(dp(220), dp(58));
        saveParams.topMargin = dp(30);
        root.addView(save, saveParams);
        setContentView(root);
        sourceInput.requestFocus();
    }

    private void save() {
        String[] lines = sourceInput.getText().toString().split("\\n");
        java.util.List<String> sources = new java.util.ArrayList<>();
        for (String line : lines) {
            String source = line.trim();
            if (source.length() == 0) {
                continue;
            }
            if (!VodPreferences.isHttpUrl(source)) {
                sourceInput.setError("每行都必须是 http:// 或 https:// JSON 地址");
                sourceInput.requestFocus();
                return;
            }
            sources.add(source);
        }
        VodPreferences.saveSourceUrls(this, sources);
        Toast.makeText(this, sources.isEmpty() ? "已清空用户影视源" : "已保存 "
                + sources.size() + " 个影视源", Toast.LENGTH_SHORT).show();
        finish();
    }

    private String joinSources(java.util.List<String> sources) {
        StringBuilder text = new StringBuilder();
        for (String source : sources) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(source);
        }
        return text.toString();
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

    private GradientDrawable inputBackground() {
        GradientDrawable drawable = rounded(Color.rgb(35, 57, 88));
        drawable.setStroke(dp(1), Color.rgb(103, 140, 185));
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
