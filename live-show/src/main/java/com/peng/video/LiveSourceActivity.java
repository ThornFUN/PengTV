package com.peng.video;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.UUID;

public final class LiveSourceActivity extends Activity {
    private VideoPreferences.LiveSource editingSource;
    private boolean sourceListVisible;
    private boolean hasResumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showSources();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasResumed && sourceListVisible) {
            showSources();
        }
        hasResumed = true;
    }

    private void showSources() {
        sourceListVisible = true;
        editingSource = null;
        LinearLayout root = pageRoot();
        root.addView(title("直播源管理"));
        root.addView(description("内置频道始终保留。你可以添加、修改、停用或删除自己的 M3U / XMLTV 源。"));
        LinearLayout actions = new LinearLayout(this);
        TextView add = action("添加直播源");
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showEditor(null);
            }
        });
        actions.addView(add, new LinearLayout.LayoutParams(0, dp(56), 1.0f));
        TextView phone = action("手机导入");
        phone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(LiveSourceActivity.this, SourceImportActivity.class);
                intent.putExtra(SourceImportActivity.EXTRA_OPEN_PHONE, true);
                startActivity(intent);
            }
        });
        LinearLayout.LayoutParams phoneParams = new LinearLayout.LayoutParams(0, dp(56), 1.0f);
        phoneParams.leftMargin = dp(16);
        actions.addView(phone, phoneParams);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(24);
        root.addView(actions, actionsParams);
        ScrollView scroll = new ScrollView(this);
        LinearLayout sources = new LinearLayout(this);
        sources.setOrientation(LinearLayout.VERTICAL);
        sources.setPadding(0, dp(20), 0, 0);
        scroll.addView(sources);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
        List<VideoPreferences.LiveSource> values = VideoPreferences.liveSources(this);
        if (values.isEmpty()) {
            sources.addView(description("尚未添加用户直播源。"));
        } else {
            for (final VideoPreferences.LiveSource source : values) {
                TextView card = action(source.name + (source.enabled ? "" : " · 已停用")
                        + "\n" + source.m3uUrl);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(dp(20), dp(12), dp(20), dp(12));
                card.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        showEditor(source);
                    }
                });
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                cardParams.bottomMargin = dp(12);
                sources.addView(card, cardParams);
            }
            sources.getChildAt(0).requestFocus();
        }
        setContentView(root);
    }

    private void showEditor(VideoPreferences.LiveSource source) {
        sourceListVisible = false;
        editingSource = source;
        LinearLayout root = pageRoot();
        root.addView(title(source == null ? "添加直播源" : "编辑直播源"));
        final EditText name = input("例如：公司测试源");
        name.setText(source == null ? "" : source.name);
        root.addView(label("名称"));
        root.addView(name, fieldParams());
        final EditText m3u = input("https://example.com/playlist.m3u");
        m3u.setText(source == null ? "" : source.m3uUrl);
        root.addView(label("M3U 地址"));
        root.addView(m3u, fieldParams());
        final EditText epg = input("https://example.com/guide.xml（可选）");
        epg.setText(source == null ? "" : source.epgUrl);
        root.addView(label("XMLTV 地址（可选）"));
        root.addView(epg, fieldParams());
        LinearLayout actions = new LinearLayout(this);
        TextView save = action("保存");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveSource(name, m3u, epg);
            }
        });
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(56), 1.0f));
        if (source != null) {
            TextView toggle = action(source.enabled ? "停用" : "启用");
            toggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    toggleSource();
                }
            });
            LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(0, dp(56), 1.0f);
            toggleParams.leftMargin = dp(16);
            actions.addView(toggle, toggleParams);
            TextView delete = action("删除");
            delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    confirmDelete();
                }
            });
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(0, dp(56), 1.0f);
            deleteParams.leftMargin = dp(16);
            actions.addView(delete, deleteParams);
        }
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = dp(26);
        root.addView(actions, actionParams);
        setContentView(root);
        name.requestFocus();
    }

    private void saveSource(EditText nameInput, EditText m3uInput, EditText epgInput) {
        String name = nameInput.getText().toString().trim();
        String m3u = m3uInput.getText().toString().trim();
        String epg = epgInput.getText().toString().trim();
        if (name.length() == 0) {
            nameInput.setError("请输入名称");
            nameInput.requestFocus();
            return;
        }
        if (!VideoPreferences.isHttpUrl(m3u)) {
            m3uInput.setError("请输入 http:// 或 https:// M3U 地址");
            m3uInput.requestFocus();
            return;
        }
        if (epg.length() > 0 && !VideoPreferences.isHttpUrl(epg)) {
            epgInput.setError("XMLTV 地址必须是 http:// 或 https://");
            epgInput.requestFocus();
            return;
        }
        String id = editingSource == null ? UUID.randomUUID().toString() : editingSource.id;
        boolean enabled = editingSource == null || editingSource.enabled;
        VideoPreferences.upsertLiveSource(this,
                new VideoPreferences.LiveSource(id, name, m3u, epg, enabled));
        Toast.makeText(this, "直播源已保存", Toast.LENGTH_SHORT).show();
        showSources();
    }

    private void toggleSource() {
        if (editingSource == null) {
            return;
        }
        VideoPreferences.upsertLiveSource(this, new VideoPreferences.LiveSource(editingSource.id,
                editingSource.name, editingSource.m3uUrl, editingSource.epgUrl,
                !editingSource.enabled));
        Toast.makeText(this, editingSource.enabled ? "直播源已停用" : "直播源已启用",
                Toast.LENGTH_SHORT).show();
        showSources();
    }

    private void confirmDelete() {
        if (editingSource == null) {
            return;
        }
        final String sourceId = editingSource.id;
        new AlertDialog.Builder(this)
                .setTitle("删除直播源")
                .setMessage("删除“" + editingSource.name + "”后，其频道将不再加载。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    VideoPreferences.deleteLiveSource(LiveSourceActivity.this, sourceId);
                    Toast.makeText(LiveSourceActivity.this, "直播源已删除", Toast.LENGTH_SHORT).show();
                    showSources();
                })
                .show();
    }

    private LinearLayout pageRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(48), dp(30), dp(48), dp(30));
        root.setBackgroundColor(Color.rgb(16, 26, 42));
        return root;
    }

    private TextView title(String value) {
        return text(value, 28, Color.WHITE);
    }

    private TextView label(String value) {
        TextView view = text(value, 17, Color.WHITE);
        view.setPadding(0, dp(16), 0, dp(8));
        return view;
    }

    private TextView description(String value) {
        return text(value, 16, Color.rgb(194, 213, 235));
    }

    private EditText input(String hint) {
        EditText view = new EditText(this);
        view.setSingleLine(true);
        view.setTextColor(Color.WHITE);
        view.setTextSize(17);
        view.setHintTextColor(Color.rgb(150, 177, 210));
        view.setHint(hint);
        view.setPadding(dp(18), 0, dp(18), 0);
        view.setBackground(rounded(Color.rgb(35, 57, 88)));
        return view;
    }

    private LinearLayout.LayoutParams fieldParams() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
    }

    private TextView action(String value) {
        TextView view = text(value, 18, Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setFocusable(true);
        view.setBackground(focusBackground());
        return view;
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
