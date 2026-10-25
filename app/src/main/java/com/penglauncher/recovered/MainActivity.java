package com.penglauncher.recovered;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;
import java.text.SimpleDateFormat;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends BasePageActivity {
    private static final int COLUMN_COUNT = 5;
    private static final int INITIAL_CARD_COUNT = 8;
    private static final int DESKTOP_CARD_COUNT = 10;
    private LinearLayout cardGrid;
    private LinearLayout allAppsGrid;
    private ViewFlipper pageSwitcher;
    private View firstHomeCard;
    private View firstAllAppsCard;
    private int lastHomeFocusIndex;
    private ImageView homeBackground;
    private int currentPage;
    private List<AppChoice> availableApps;
    private final Handler clockHandler = new Handler();
    private TextView timeView;
    private TextView dateView;
    private final Runnable updateClock = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            timeView.setText(new SimpleDateFormat("HH:mm", Locale.CHINA).format(now));
            dateView.setText(new SimpleDateFormat("MM月dd日  EEEE", Locale.CHINA).format(now));
            clockHandler.postDelayed(this, 30000L);
        }
    };
    private static final int MAX_RELOAD_ATTEMPTS = 3;
    private final Handler reloadHandler = new Handler();
    private int reloadAttempts;
    private final Runnable reloadCardsRunnable = new Runnable() {
        @Override
        public void run() {
            if (reloadAttempts >= MAX_RELOAD_ATTEMPTS) {
                return;
            }
            reloadAttempts++;
            availableApps = loadAvailableApps();
            refreshCards();
            scheduleReloadIfMissing();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        android.util.Log.e("PENG", "== onCreate called, task=" + getTaskId());
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        availableApps = loadAvailableApps();
        createDefaultCardsIfNeeded();
        buildHome();
        scheduleReloadIfMissing();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        android.util.Log.e("PENG", "== onNewIntent called, action=" + intent.getAction());
    }

    @Override
    protected void onResume() {
        super.onResume();
        android.util.Log.e("PENG", "== onResume called");
        clockHandler.removeCallbacks(updateClock);
        clockHandler.post(updateClock);
        if (homeBackground != null) {
            availableApps = loadAvailableApps();
            applyHomeWallpaper();
            refreshCards();
            scheduleReloadIfMissing();
        }
    }

    @Override
    protected void onPause() {
        clockHandler.removeCallbacks(updateClock);
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && pageSwitcher != null) {
            pageSwitcher.post(new Runnable() {
                @Override
                public void run() {
                    if (currentPage == 0) {
                        focusHomeCard(lastHomeFocusIndex);
                    } else if (currentPage == 1) {
                        if (firstAllAppsCard != null) {
                            firstAllAppsCard.requestFocus();
                        }
                    }
                }
            });
        }
    }

    private void buildHome() {
        FrameLayout screen = new FrameLayout(this);
        homeBackground = new ImageView(this);
        homeBackground.setScaleType(ImageView.ScaleType.CENTER_CROP);
        screen.addView(homeBackground, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        pageSwitcher = new ViewFlipper(this);
        pageSwitcher.addView(createHomePage());
        pageSwitcher.addView(createAllAppsPage());
        LinearLayout.LayoutParams pageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        root.addView(pageSwitcher, pageParams);
        screen.addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(screen);
        applyHomeWallpaper();
        refreshCards();
    }

    private void applyHomeWallpaper() {
        String wallpaperPath = getSharedPreferences(
                FeatureActivity.WALLPAPER_PREFERENCES, MODE_PRIVATE)
                .getString(FeatureActivity.WALLPAPER_PATH_KEY, null);
        if (wallpaperPath != null && new File(wallpaperPath).isFile()) {
            Bitmap wallpaper = decodeWallpaper(new File(wallpaperPath));
            if (wallpaper != null) {
                homeBackground.setImageBitmap(wallpaper);
                return;
            }
        }
        homeBackground.setImageResource(R.drawable.bg_launcher_default_new);
    }

    private Bitmap decodeWallpaper(File wallpaperFile) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(wallpaperFile.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int targetWidth = getResources().getDisplayMetrics().widthPixels;
        int targetHeight = getResources().getDisplayMetrics().heightPixels;
        int sample = 1;
        while (bounds.outWidth / sample > targetWidth * 2
                || bounds.outHeight / sample > targetHeight * 2) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(wallpaperFile.getAbsolutePath(), options);
    }

    private View createHomePage() {
        LinearLayout homePage = new LinearLayout(this);
        homePage.setOrientation(LinearLayout.VERTICAL);
        homePage.setPadding(dp(34), dp(20), dp(34), dp(30));
        allowFocusOverflow(homePage);
        homePage.addView(createWelcomePanel());
        cardGrid = new LinearLayout(this);
        cardGrid.setOrientation(LinearLayout.VERTICAL);
        allowFocusOverflow(cardGrid);
        FrameLayout cardArea = new FrameLayout(this);
        allowFocusOverflow(cardArea);
        LinearLayout.LayoutParams cardAreaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        cardAreaParams.topMargin = dp(22);
        homePage.addView(cardArea, cardAreaParams);
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        cardArea.addView(cardGrid, cardParams);
        return homePage;
    }

    private View createAllAppsPage() {
        ScrollView allAppsScroll = new ScrollView(this);
        allAppsScroll.setFillViewport(true);
        allowFocusOverflow(allAppsScroll);
        allAppsScroll.setPadding(dp(34), dp(20), dp(34), dp(30));
        allAppsScroll.setBackgroundColor(Color.rgb(15, 29, 49));
        LinearLayout allAppsContent = new LinearLayout(this);
        allAppsContent.setOrientation(LinearLayout.VERTICAL);
        allowFocusOverflow(allAppsContent);
        allAppsGrid = new LinearLayout(this);
        allAppsGrid.setOrientation(LinearLayout.VERTICAL);
        allowFocusOverflow(allAppsGrid);
        allAppsContent.addView(allAppsGrid, new LinearLayout.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        allAppsScroll.addView(allAppsContent, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        return allAppsScroll;
    }

    private View createWelcomePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(26), dp(14), dp(26), dp(16));
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
                dp(380), LinearLayout.LayoutParams.WRAP_CONTENT);
        panelParams.topMargin = dp(26);
        panel.setLayoutParams(panelParams);

        timeView = new TextView(this);
        timeView.setTextColor(Color.WHITE);
        timeView.setTextSize(38);
        panel.addView(timeView);

        dateView = new TextView(this);
        dateView.setTextColor(Color.rgb(213, 225, 244));
        dateView.setTextSize(15);
        panel.addView(dateView);

        return panel;
    }

    private void refreshCards() {
        cardGrid.removeAllViews();
        LinearLayout row = null;
        for (int index = 0; index < DESKTOP_CARD_COUNT; index++) {
            if (index % COLUMN_COUNT == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                allowFocusOverflow(row);
                cardGrid.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            AppChoice app = choiceAt(index);
            View card;
            if (app != null) {
                card = createAppCard(app, index);
            } else {
                card = createAddCard(index);
            }
            if (index == 0) {
                firstHomeCard = card;
            }
            card.setTag(index);
            row.addView(card, gridParams());
        }
        refreshAllApps();
    }

    private void refreshAllApps() {
        allAppsGrid.removeAllViews();
        LinearLayout row = null;
        for (int index = 0; index < availableApps.size(); index++) {
            if (index % 6 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                allowFocusOverflow(row);
                allAppsGrid.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            View card = createAllAppsCard(availableApps.get(index), index);
            if (index == 0) {
                firstAllAppsCard = card;
            }
            row.addView(card, allAppsGridParams());
        }
    }

    private View createAppCard(final AppChoice app, final int index) {
        LinearLayout card = createCardContainer();
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.resolveInfo.loadIcon(getPackageManager()));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        card.addView(icon, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        TextView title = new TextView(this);
        title.setText(app.label);
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setPadding(dp(8), 0, dp(8), dp(9));
        card.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launch(app);
            }
        });
        card.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }
                if (keyCode == KeyEvent.KEYCODE_MENU) {
                    showCardMenu(index, app.label);
                    return true;
                }
                if (index >= COLUMN_COUNT && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    showAllAppsPage();
                    return true;
                }
                return false;
            }
        });
        return card;
    }

    private View createAllAppsCard(final AppChoice app, final int index) {
        LinearLayout card = createCardContainer();
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.resolveInfo.loadIcon(getPackageManager()));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        card.addView(icon, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
        TextView title = new TextView(this);
        title.setText(app.label);
        title.setTextColor(Color.WHITE);
        title.setTextSize(13);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        card.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(28)));
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launch(app);
            }
        });
        card.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }
                if (keyCode == KeyEvent.KEYCODE_MENU) {
                    showAllAppsMenu(app);
                    return true;
                }
                if (index < 6 && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    showHomePage();
                    return true;
                }
                return false;
            }
        });
        return card;
    }

    private View createAddCard(final int index) {
        LinearLayout card = createCardContainer();
        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextSize(58);
        plus.setTextColor(Color.rgb(218, 229, 244));
        plus.setGravity(Gravity.CENTER);
        card.addView(plus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
        TextView label = new TextView(this);
        label.setText("添加应用");
        label.setTextColor(Color.WHITE);
        label.setTextSize(15);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, 0, 0, dp(9));
        card.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectAppForCard(index);
            }
        });
        if (index >= COLUMN_COUNT) {
            card.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View view, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                            && event.getAction() == KeyEvent.ACTION_DOWN) {
                        showAllAppsPage();
                        return true;
                    }
                    return false;
                }
            });
        }
        return card;
    }

    private void showAllAppsPage() {
        if (currentPage == 1) {
            return;
        }
        pageSwitcher.setInAnimation(slideAnimation(1.0f, 0.0f));
        pageSwitcher.setOutAnimation(slideAnimation(0.0f, -1.0f));
        pageSwitcher.setDisplayedChild(1);
        currentPage = 1;
        pageSwitcher.post(new Runnable() {
            @Override
            public void run() {
                if (firstAllAppsCard != null) {
                    firstAllAppsCard.requestFocus();
                }
            }
        });
    }

    private View getHomeCard(int index) {
        if (cardGrid == null) {
            return null;
        }
        int row = index / COLUMN_COUNT;
        int col = index % COLUMN_COUNT;
        if (row >= cardGrid.getChildCount()) {
            return null;
        }
        View rowView = cardGrid.getChildAt(row);
        if (rowView instanceof ViewGroup && col < ((ViewGroup) rowView).getChildCount()) {
            return ((ViewGroup) rowView).getChildAt(col);
        }
        return null;
    }

    private void focusHomeCard(int index) {
        View card = getHomeCard(index);
        if (card != null) {
            card.requestFocus();
        } else if (firstHomeCard != null) {
            firstHomeCard.requestFocus();
        }
    }

    private void showHomePage() {
        if (currentPage == 0) {
            return;
        }
        pageSwitcher.setInAnimation(slideAnimation(-1.0f, 0.0f));
        pageSwitcher.setOutAnimation(slideAnimation(0.0f, 1.0f));
        pageSwitcher.setDisplayedChild(0);
        currentPage = 0;
        pageSwitcher.post(new Runnable() {
            @Override
            public void run() {
                focusHomeCard(lastHomeFocusIndex);
            }
        });
    }

    private Animation slideAnimation(float fromY, float toY) {
        TranslateAnimation animation = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, fromY,
                Animation.RELATIVE_TO_SELF, toY);
        animation.setDuration(240L);
        return animation;
    }

    private LinearLayout createCardContainer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), 0);
        card.setGravity(Gravity.CENTER);
        card.setClickable(true);
        card.setFocusable(true);
        setBackgroundCompat(card, cardBackground());
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean focused) {
                view.animate().cancel();
                view.animate().scaleX(focused ? 1.10f : 1.0f)
                        .scaleY(focused ? 1.10f : 1.0f).setDuration(160).start();
                if (focused) {
                    Object tag = view.getTag();
                    if (tag instanceof Integer) {
                        lastHomeFocusIndex = (Integer) tag;
                    }
                }
            }
        });
        return card;
    }

    private void allowFocusOverflow(ViewGroup container) {
        container.setClipChildren(false);
        container.setClipToPadding(false);
    }

    private LinearLayout.LayoutParams gridParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, cardHeight(), 1.0f);
        params.setMargins(dp(8), dp(8), dp(8), dp(8));
        return params;
    }

    private LinearLayout.LayoutParams allAppsGridParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, Math.max(dp(68), Math.round(cardHeight() * 0.58f)), 1.0f);
        params.setMargins(dp(7), dp(7), dp(7), dp(7));
        return params;
    }

    private int cardHeight() {
        int availableWidth = getResources().getDisplayMetrics().widthPixels - dp(68);
        int cardWidth = availableWidth / COLUMN_COUNT;
        return Math.max(dp(96), Math.round(cardWidth * 0.58f));
    }

    private void showCardMenu(final int index, String label) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(label)
                .setItems(new String[]{"替换应用", "移除卡片"}, (ignoredDialog, which) -> {
                    if (which == 0) {
                        selectAppForCard(index);
                    } else {
                        removeCard(index);
                    }
                })
                .create();
        applyDialogFocusStyle(dialog);
        dialog.show();
    }

    private void showAllAppsMenu(final AppChoice app) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(new String[]{"应用详情", "卸载"}, (ignoredDialog, which) -> {
                    if (which == 0) {
                        Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        details.setData(Uri.parse("package:" + app.resolveInfo.activityInfo.packageName));
                        startAfterDialogDismiss((AlertDialog) ignoredDialog, details);
                    } else {
                        Intent uninstall = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
                        uninstall.setData(Uri.parse("package:" + app.resolveInfo.activityInfo.packageName));
                        startAfterDialogDismiss((AlertDialog) ignoredDialog, uninstall);
                    }
                })
                .create();
        applyDialogFocusStyle(dialog);
        dialog.show();
    }

    private void startAfterDialogDismiss(final AlertDialog dialog, final Intent intent) {
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface ignoredDialog) {
                startActivity(intent);
            }
        });
        dialog.dismiss();
    }

    private void selectAppForCard(final int index) {
        if (availableApps.isEmpty()) {
            Toast.makeText(this, "未找到可启动应用", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[availableApps.size()];
        for (int i = 0; i < availableApps.size(); i++) {
            labels[i] = availableApps.get(i).label;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(choiceAt(index) == null ? "添加应用" : "替换应用")
                .setItems(labels, (ignoredDialog, which) -> {
                    saveChoice(index, availableApps.get(which));
                    refreshCards();
                })
                .create();
        applyDialogFocusStyle(dialog);
        dialog.show();
    }

    private void launch(AppChoice app) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setClassName(app.resolveInfo.activityInfo.packageName, app.resolveInfo.activityInfo.name);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException exception) {
            Toast.makeText(this, "无法启动 " + app.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void createDefaultCardsIfNeeded() {
        if (preferences().getBoolean("cards_initialized", false)) {
            return;
        }
        int count = Math.min(INITIAL_CARD_COUNT, availableApps.size());
        for (int index = 0; index < count; index++) {
            saveChoice(index, availableApps.get(index));
        }
        preferences().edit().putBoolean("cards_initialized", true).apply();
    }

    private void saveChoice(int index, AppChoice app) {
        SharedPreferences.Editor editor = preferences().edit();
        editor.putString("card_package_" + index, app.resolveInfo.activityInfo.packageName);
        editor.putString("card_activity_" + index, app.resolveInfo.activityInfo.name);
        editor.apply();
    }

    private void removeCard(int index) {
        SharedPreferences.Editor editor = preferences().edit();
        editor.remove("card_package_" + index);
        editor.remove("card_activity_" + index);
        editor.apply();
        refreshCards();
    }

    private AppChoice choiceAt(int index) {
        String packageName = preferences().getString("card_package_" + index, null);
        String activityName = preferences().getString("card_activity_" + index, null);
        for (AppChoice app : availableApps) {
            if (app.resolveInfo.activityInfo.packageName.equals(packageName)
                    && app.resolveInfo.activityInfo.name.equals(activityName)) {
                return app;
            }
        }
        return null;
    }

    private List<AppChoice> loadAvailableApps() {
        PackageManager packageManager = getPackageManager();
        List<AppChoice> apps = new ArrayList<>();
        Set<ComponentName> seen = new HashSet<>();
        addLaunchableApps(packageManager, Intent.CATEGORY_LEANBACK_LAUNCHER, seen, apps);
        addLaunchableApps(packageManager, Intent.CATEGORY_LAUNCHER, seen, apps);
        Collections.sort(apps, new Comparator<AppChoice>() {
            @Override
            public int compare(AppChoice left, AppChoice right) {
                return left.label.compareToIgnoreCase(right.label);
            }
        });
        return apps;
    }

    private void addLaunchableApps(PackageManager packageManager, String category,
            Set<ComponentName> seen, List<AppChoice> apps) {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(category);
        for (ResolveInfo app : packageManager.queryIntentActivities(query, 0)) {
            if (app.activityInfo == null || getPackageName().equals(app.activityInfo.packageName)) {
                continue;
            }
            ComponentName component = new ComponentName(
                    app.activityInfo.packageName, app.activityInfo.name);
            if (!seen.add(component)) {
                continue;
            }
            CharSequence label = app.loadLabel(packageManager);
            apps.add(new AppChoice(app, label == null ? app.activityInfo.packageName : label.toString()));
        }
    }

    private boolean containsPackage(String packageName) {
        for (AppChoice app : availableApps) {
            if (app.resolveInfo.activityInfo.packageName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private void scheduleReloadIfMissing() {
        for (int index = 0; index < DESKTOP_CARD_COUNT; index++) {
            String pkg = preferences().getString("card_package_" + index, null);
            if (pkg != null && !containsPackage(pkg)) {
                reloadHandler.removeCallbacks(reloadCardsRunnable);
                reloadHandler.postDelayed(reloadCardsRunnable, 1000L);
                return;
            }
        }
    }

    private SharedPreferences preferences() {
        return getSharedPreferences("launcher_cards", MODE_PRIVATE);
    }

    private StateListDrawable cardBackground() {
        StateListDrawable drawable = new StateListDrawable();
        GradientDrawable focused = roundRect(Color.rgb(67, 112, 177), dp(20));
        focused.setStroke(dp(2), Color.WHITE);
        drawable.addState(new int[]{android.R.attr.state_focused}, focused);
        drawable.addState(new int[]{android.R.attr.state_pressed}, focused);
        drawable.addState(new int[]{}, roundRect(Color.argb(215, 45, 66, 104), dp(20)));
        return drawable;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static final class AppChoice {
        private final ResolveInfo resolveInfo;
        private final String label;

        private AppChoice(ResolveInfo resolveInfo, String label) {
            this.resolveInfo = resolveInfo;
            this.label = label;
        }
    }
}
