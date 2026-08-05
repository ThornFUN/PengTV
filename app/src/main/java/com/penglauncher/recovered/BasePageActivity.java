package com.penglauncher.recovered;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Typeface;

abstract class BasePageActivity extends Activity {
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.rgb(16, 25, 42));
        }
    }

    protected void showPage(String title, String subtitle) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(20), dp(28), dp(28));
        root.setBackgroundColor(Color.rgb(20, 30, 49));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text(title, 28, Color.WHITE);
        header.addView(heading);
        root.addView(header);

        if (subtitle != null) {
            TextView detail = text(subtitle, 15, Color.rgb(185, 203, 225));
            LinearLayout.LayoutParams detailParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
            detailParams.topMargin = dp(8);
            root.addView(detail, detailParams);
        }

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(18), 0, 0);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
        setContentView(root);
    }

    protected void addAction(String title, String detail, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(10), dp(18), dp(10));
        row.setBackground(interactiveBackground());
        applyTvFocusFeedback(row);
        row.setOnClickListener(listener);

        LinearLayout textBlock = new LinearLayout(this);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        textBlock.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(textBlock, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView name = text(title, 17, Color.WHITE);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        textBlock.addView(name);

        TextView description = text(detail == null ? "" : detail, 14, Color.rgb(174, 188, 208));
        description.setSingleLine(true);
        description.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(3);
        textBlock.addView(description, descriptionParams);

        TextView arrow = text("›", 34, Color.rgb(205, 220, 241));
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(52)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        content.addView(row, params);
    }

    protected void addInfo(String value) {
        TextView view = text(value, 16, Color.rgb(217, 228, 243));
        view.setPadding(dp(12), dp(12), dp(12), dp(12));
        content.addView(view);
    }

    protected EditText addInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(160, 180, 205));
        input.setSingleLine(true);
        input.setBackground(inputBackground());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        content.addView(input, params);
        return input;
    }

    protected TextView text(String value, int sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sizeSp);
        view.setLineSpacing(0.0f, 1.2f);
        return view;
    }

    protected int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    protected StateListDrawable interactiveBackground() {
        StateListDrawable drawable = new StateListDrawable();
        GradientDrawable focused = roundedRect(Color.rgb(73, 126, 193), dp(12));
        focused.setStroke(dp(2), Color.WHITE);
        drawable.addState(new int[]{android.R.attr.state_focused}, focused);
        drawable.addState(new int[]{android.R.attr.state_selected}, focused);
        drawable.addState(new int[]{android.R.attr.state_pressed},
                roundedRect(Color.rgb(56, 101, 162), dp(12)));
        drawable.addState(new int[]{}, roundedRect(Color.rgb(40, 57, 86), dp(12)));
        return drawable;
    }

    protected void applyTvFocusFeedback(View view) {
        view.setFocusable(true);
        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View focusedView, boolean focused) {
                focusedView.animate().cancel();
                focusedView.animate()
                        .scaleX(focused ? 1.04f : 1.0f)
                        .scaleY(focused ? 1.04f : 1.0f)
                        .setDuration(140)
                        .start();
            }
        });
    }

    protected void applyDialogFocusStyle(final AlertDialog dialog) {
        dialog.setOnShowListener(dialogInterface -> {
            ListView list = dialog.getListView();
            if (list != null) {
                list.setSelector(interactiveBackground());
                if (list.getAdapter() != null && list.getAdapter().getCount() > 0) {
                    list.setSelection(0);
                    list.requestFocus();
                }
            }
        });
    }

    private StateListDrawable inputBackground() {
        StateListDrawable drawable = new StateListDrawable();
        GradientDrawable focused = roundedRect(Color.rgb(33, 67, 108), dp(8));
        focused.setStroke(dp(2), Color.rgb(128, 194, 255));
        drawable.addState(new int[]{android.R.attr.state_focused}, focused);
        drawable.addState(new int[]{}, roundedRect(Color.rgb(28, 43, 68), dp(8)));
        return drawable;
    }

    private GradientDrawable roundedRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }
}
