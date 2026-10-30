package com.penglauncher.recovered;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.util.LruCache;
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

public final class MainActivity extends FeatureActivity {
    private static final int COLUMN_COUNT = 5;
    private static final int INITIAL_CARD_COUNT = 5;
    private static final int DESKTOP_CARD_COUNT = 5;
    private static final int ICON_CANVAS_SIZE_DP = 96;
    private static final int ICON_CONTENT_SIZE_DP = 66;
    private static final String CARD_STATE_AUTO = "auto";
    private static final String CARD_STATE_USER = "user";
    private static final String CARD_STATE_EMPTY = "empty";
    private LinearLayout cardGrid;
    private LinearLayout allAppsGrid;
    private ScrollView allAppsScroll;
    private ViewFlipper pageSwitcher;
    private View firstHomeCard;
    private View firstAllAppsCard;
    private TextView settingsButton;
    private int lastHomeFocusIndex;
    private ComponentName lastHomeFocusComponent;
    private ImageView homeBackground;
    private int currentPage;
    private boolean featurePageShown;
    private boolean returnToSettings;
    private List<AppChoice> availableApps;
    private final LruCache<String, Bitmap> normalizedIconCache =
            new LruCache<String, Bitmap>(1536 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    return bitmap.getByteCount();
                }
            };
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
    private boolean packageReceiverRegistered;
    private final BroadcastReceiver packageChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            normalizedIconCache.evictAll();
            refreshAvailableApps();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        availableApps = loadAvailableApps();
        initializeCardStates();
        fillAutomaticCards();
        buildHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerPackageChangeReceiver();
        clockHandler.removeCallbacks(updateClock);
        clockHandler.post(updateClock);
        if (!featurePageShown && homeBackground != null) {
            refreshAvailableApps();
            applyHomeWallpaper();
        }
    }

    private void showSettingsButton() {
        if (settingsButton == null) {
            return;
        }
        settingsButton.setVisibility(View.VISIBLE);
        settingsButton.postDelayed(new Runnable() {
            @Override
            public void run() {
                settingsButton.requestFocus();
            }
        }, 100L);
    }

    private void hideSettingsButton() {
        if (settingsButton != null) {
            settingsButton.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onPause() {
        clockHandler.removeCallbacks(updateClock);
        unregisterPackageChangeReceiver();
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
                        restoreHomeFocus();
                    } else if (currentPage == 1) {
                        focusFirstAllAppsCard();
                    }
                }
            });
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if ((event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP)
                && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN
                && currentPage == 0
                && settingsButton != null
                && settingsButton.hasFocus()) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                hideSettingsButton();
                restoreHomeFocus();
            }
            return true;
        }
        if ((event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP)
                && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                && currentPage == 0) {
            showSettingsButton();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (featurePageShown) {
            closeFeaturePage();
        } else if (currentPage == 1) {
            showHomePage();
        }
        // The home page is the root of PENG 桌面. Back must never close the launcher.
    }

    private void showFeaturePage(String page) {
        stopUploadServer();
        featurePageShown = true;
        currentPage = -1;
        if (SETTINGS.equals(page)) {
            returnToSettings = false;
        }
        if (ABOUT.equals(page)) {
            about();
        } else if (WALLPAPER_UPLOAD.equals(page)) {
            showWallpaperUpload();
        } else if (FILE_MANAGER.equals(page)) {
            showFileManager();
        } else {
            settings();
        }
    }

    @Override
    protected void openFeaturePage(String page) {
        returnToSettings = featurePageShown;
        showFeaturePage(page);
    }

    @Override
    protected void closeFeaturePage() {
        if (!featurePageShown) {
            return;
        }
        stopUploadServer();
        if (returnToSettings) {
            returnToSettings = false;
            settings();
            return;
        }
        featurePageShown = false;
        currentPage = 0;
        buildHome();
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
        pageSwitcher.post(new Runnable() {
            @Override
            public void run() {
                restoreHomeFocus();
            }
        });
    }

    private void applyHomeWallpaper() {
        String wallpaperPath = getSharedPreferences(
                WALLPAPER_PREFERENCES, MODE_PRIVATE).getString(WALLPAPER_PATH_KEY, null);
        if (wallpaperPath != null && new File(wallpaperPath).isFile()) {
            Bitmap wallpaper = decodeWallpaper(new File(wallpaperPath));
            if (wallpaper != null) {
                homeBackground.setImageBitmap(wallpaper);
                return;
            }
        }
        homeBackground.setImageDrawable(null);
        homeBackground.setBackgroundColor(Color.rgb(15, 29, 49));
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
        homePage.addView(createHomeHeader());
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
        allAppsScroll = new ScrollView(this);
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
        timeView.setTextSize(50);
        panel.addView(timeView);

        dateView = new TextView(this);
        dateView.setTextColor(Color.rgb(213, 225, 244));
        dateView.setTextSize(19);
        panel.addView(dateView);

        return panel;
    }

    private View createHomeHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams welcomeParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        welcomeParams.topMargin = dp(26);
        header.addView(createWelcomePanel(), welcomeParams);

        settingsButton = text("设置", 17, Color.WHITE);
        settingsButton.setContentDescription("设置");
        settingsButton.setGravity(Gravity.CENTER);
        settingsButton.setPadding(dp(16), 0, dp(16), 0);
        settingsButton.setClickable(true);
        setBackgroundCompat(settingsButton, interactiveBackground());
        settingsButton.setFocusable(true);
        settingsButton.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(final View view, boolean focused) {
                view.animate().cancel();
                view.animate()
                        .scaleX(focused ? 1.02f : 1.0f)
                        .scaleY(focused ? 1.02f : 1.0f)
                        .setDuration(140)
                        .start();
                if (!focused) {
                    view.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!view.hasFocus()) {
                                view.setVisibility(View.GONE);
                            }
                        }
                    });
                }
            }
        });
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showFeaturePage(SETTINGS);
            }
        });
        settingsButton.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN
                        || event.getAction() == KeyEvent.ACTION_UP)
                        && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusHomeCard(lastHomeFocusIndex);
                    return true;
                }
                return false;
            }
        });
        header.addView(settingsButton, new LinearLayout.LayoutParams(dp(96), dp(52)));
        settingsButton.setVisibility(View.GONE);
        return header;
    }

    private void refreshCards() {
        final ComponentName returnComponent = lastHomeFocusComponent;
        final int returnIndex = lastHomeFocusIndex;
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
            card.setTag(new HomeCardFocus(index, app == null ? null
                    : new ComponentName(app.resolveInfo.activityInfo.packageName,
                    app.resolveInfo.activityInfo.name)));
            row.addView(card, gridParams());
        }
        refreshAllApps();
        if (currentPage == 0) {
            cardGrid.post(new Runnable() {
                @Override
                public void run() {
                    focusHomeCard(returnComponent, returnIndex);
                }
            });
        }
    }

    private void refreshAllApps() {
        allAppsGrid.removeAllViews();
        LinearLayout row = null;
        for (int index = 0; index < availableApps.size(); index++) {
            if (index % COLUMN_COUNT == 0) {
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
            row.addView(card, gridParams());
        }
        if (row != null) {
            int emptySlots = (COLUMN_COUNT - availableApps.size() % COLUMN_COUNT) % COLUMN_COUNT;
            for (int index = 0; index < emptySlots; index++) {
                View spacer = new View(this);
                spacer.setVisibility(View.INVISIBLE);
                row.addView(spacer, gridParams());
            }
        }
    }

    private View createAppCard(final AppChoice app, final int index) {
        LinearLayout card = createCardContainer();
        ImageView icon = createAppIcon(app);
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
                if (keyCode == KeyEvent.KEYCODE_MENU) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        showCardMenu(index, app.label);
                        return true;
                    }
                    return false;
                }
                if (event.getAction() != KeyEvent.ACTION_DOWN
                        && event.getAction() != KeyEvent.ACTION_UP) {
                    return false;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
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
        ImageView icon = createAppIcon(app);
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
                if (keyCode == KeyEvent.KEYCODE_MENU) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        showAllAppsMenu(app);
                        return true;
                    }
                    return false;
                }
                if (event.getAction() != KeyEvent.ACTION_DOWN
                        && event.getAction() != KeyEvent.ACTION_UP) {
                    return false;
                }
                if (index < COLUMN_COUNT
                        && keyCode == KeyEvent.KEYCODE_DPAD_UP
                        && event.getAction() == KeyEvent.ACTION_UP) {
                    showHomePage();
                    return true;
                }
                return false;
            }
        });
        return card;
    }

    private ImageView createAppIcon(AppChoice app) {
        ImageView icon = new ImageView(this);
        Bitmap normalizedIcon = normalizedIcon(app);
        if (normalizedIcon == null) {
            icon.setImageDrawable(app.resolveInfo.loadIcon(getPackageManager()));
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        } else {
            icon.setImageBitmap(normalizedIcon);
            icon.setScaleType(ImageView.ScaleType.CENTER);
        }
        return icon;
    }

    private Bitmap normalizedIcon(AppChoice app) {
        String cacheKey = app.resolveInfo.activityInfo.packageName + "/"
                + app.resolveInfo.activityInfo.name;
        Bitmap cachedIcon = normalizedIconCache.get(cacheKey);
        if (cachedIcon != null) {
            return cachedIcon;
        }

        Drawable drawable = app.resolveInfo.loadIcon(getPackageManager());
        if (drawable == null) {
            return null;
        }
        int canvasSize = dp(ICON_CANVAS_SIZE_DP);
        Bitmap source = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888);
        Canvas sourceCanvas = new Canvas(source);
        Rect originalBounds = drawable.copyBounds();
        drawable.setBounds(0, 0, canvasSize, canvasSize);
        drawable.draw(sourceCanvas);
        drawable.setBounds(originalBounds);

        Rect visibleBounds = visibleBounds(source);
        if (visibleBounds == null) {
            source.recycle();
            return null;
        }

        Bitmap result = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888);
        int contentSize = dp(ICON_CONTENT_SIZE_DP);
        float scale = Math.min((float) contentSize / visibleBounds.width(),
                (float) contentSize / visibleBounds.height());
        int width = Math.max(1, Math.round(visibleBounds.width() * scale));
        int height = Math.max(1, Math.round(visibleBounds.height() * scale));
        int left = (canvasSize - width) / 2;
        int top = (canvasSize - height) / 2;
        new Canvas(result).drawBitmap(source, visibleBounds,
                new Rect(left, top, left + width, top + height),
                new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        source.recycle();
        normalizedIconCache.put(cacheKey, result);
        return result;
    }

    private Rect visibleBounds(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        int left = width;
        int top = height;
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((pixels[y * width + x] >>> 24) == 0) {
                    continue;
                }
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        }
        return right < left || bottom < top ? null : new Rect(left, top, right + 1, bottom + 1);
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
        card.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN
                        && event.getAction() != KeyEvent.ACTION_UP) {
                    return false;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    showAllAppsPage();
                    return true;
                }
                return false;
            }
        });
        return card;
    }

    private void showAllAppsPage() {
        if (currentPage == 1) {
            return;
        }
        hideSettingsButton();
        pageSwitcher.setInAnimation(slideAnimation(1.0f, 0.0f));
        pageSwitcher.setOutAnimation(slideAnimation(0.0f, -1.0f));
        pageSwitcher.setDisplayedChild(1);
        currentPage = 1;
        pageSwitcher.post(new Runnable() {
            @Override
            public void run() {
                focusFirstAllAppsCard();
            }
        });
    }

    private void focusFirstAllAppsCard() {
        if (firstAllAppsCard != null) {
            firstAllAppsCard.requestFocus();
        }
        if (allAppsScroll != null) {
            allAppsScroll.scrollTo(0, 0);
        }
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
        focusHomeCard(null, index);
    }

    private void restoreHomeFocus() {
        focusHomeCard(lastHomeFocusComponent, lastHomeFocusIndex);
    }

    private void focusHomeCard(ComponentName component, int fallbackIndex) {
        if (component != null) {
            for (int index = 0; index < DESKTOP_CARD_COUNT; index++) {
                AppChoice choice = choiceAt(index);
                if (choice != null && component.getPackageName().equals(
                        choice.resolveInfo.activityInfo.packageName)
                        && component.getClassName().equals(choice.resolveInfo.activityInfo.name)) {
                    View matchingCard = getHomeCard(index);
                    if (matchingCard != null) {
                        matchingCard.requestFocus();
                        return;
                    }
                }
            }
        }
        View card = getHomeCard(Math.max(0, Math.min(fallbackIndex, DESKTOP_CARD_COUNT - 1)));
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
        hideSettingsButton();
        pageSwitcher.setInAnimation(slideAnimation(-1.0f, 0.0f));
        pageSwitcher.setOutAnimation(slideAnimation(0.0f, 1.0f));
        pageSwitcher.setDisplayedChild(0);
        currentPage = 0;
        pageSwitcher.post(new Runnable() {
            @Override
            public void run() {
                hideSettingsButton();
                restoreHomeFocus();
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
                    if (tag instanceof HomeCardFocus) {
                        HomeCardFocus cardFocus = (HomeCardFocus) tag;
                        lastHomeFocusIndex = cardFocus.index;
                        lastHomeFocusComponent = cardFocus.component;
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
                .setItems(new String[]{"卸载"}, (ignoredDialog, which) -> {
                    startAfterDialogDismiss((AlertDialog) ignoredDialog, uninstallIntent(app));
                })
                .create();
        applyDialogFocusStyle(dialog);
        dialog.show();
    }

    private Intent uninstallIntent(AppChoice app) {
        Intent uninstall = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
        uninstall.setData(Uri.parse("package:" + app.resolveInfo.activityInfo.packageName));
        return uninstall;
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
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException exception) {
            Toast.makeText(this, "无法启动 " + app.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeCardStates() {
        if (preferences().getBoolean("card_state_migrated", false)) {
            return;
        }
        SharedPreferences.Editor editor = preferences().edit();
        for (int index = 0; index < DESKTOP_CARD_COUNT; index++) {
            if (cardState(index) != null) {
                continue;
            }
            if (hasSavedChoice(index)) {
                editor.putString(cardStateKey(index), CARD_STATE_USER);
            } else if (index < INITIAL_CARD_COUNT) {
                editor.putString(cardStateKey(index), CARD_STATE_AUTO);
            }
        }
        editor.putBoolean("card_state_migrated", true);
        editor.apply();
    }

    private void fillAutomaticCards() {
        Set<ComponentName> pinnedComponents = savedComponents();
        SharedPreferences.Editor editor = preferences().edit();
        boolean changed = false;
        for (int index = 0; index < INITIAL_CARD_COUNT; index++) {
            if (!CARD_STATE_AUTO.equals(cardState(index)) || hasSavedChoice(index)) {
                continue;
            }
            AppChoice candidate = firstUnpinnedApp(pinnedComponents);
            if (candidate == null) {
                break;
            }
            editor.putString("card_package_" + index, candidate.resolveInfo.activityInfo.packageName);
            editor.putString("card_activity_" + index, candidate.resolveInfo.activityInfo.name);
            pinnedComponents.add(new ComponentName(
                    candidate.resolveInfo.activityInfo.packageName,
                    candidate.resolveInfo.activityInfo.name));
            changed = true;
        }
        if (changed) {
            editor.apply();
        }
    }

    private void saveChoice(int index, AppChoice app) {
        SharedPreferences.Editor editor = preferences().edit();
        editor.putString("card_package_" + index, app.resolveInfo.activityInfo.packageName);
        editor.putString("card_activity_" + index, app.resolveInfo.activityInfo.name);
        editor.putString(cardStateKey(index), CARD_STATE_USER);
        editor.apply();
    }

    private void removeCard(int index) {
        SharedPreferences.Editor editor = preferences().edit();
        editor.remove("card_package_" + index);
        editor.remove("card_activity_" + index);
        editor.putString(cardStateKey(index), CARD_STATE_EMPTY);
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

    private void refreshAvailableApps() {
        availableApps = loadAvailableApps();
        fillAutomaticCards();
        if (cardGrid != null) {
            refreshCards();
        }
    }

    private Set<ComponentName> savedComponents() {
        Set<ComponentName> components = new HashSet<>();
        for (int index = 0; index < DESKTOP_CARD_COUNT; index++) {
            String packageName = preferences().getString("card_package_" + index, null);
            String activityName = preferences().getString("card_activity_" + index, null);
            if (packageName != null && activityName != null) {
                components.add(new ComponentName(packageName, activityName));
            }
        }
        return components;
    }

    private AppChoice firstUnpinnedApp(Set<ComponentName> pinnedComponents) {
        for (AppChoice app : availableApps) {
            ComponentName component = new ComponentName(
                    app.resolveInfo.activityInfo.packageName, app.resolveInfo.activityInfo.name);
            if (!pinnedComponents.contains(component)) {
                return app;
            }
        }
        return null;
    }

    private boolean hasSavedChoice(int index) {
        return preferences().contains("card_package_" + index)
                && preferences().contains("card_activity_" + index);
    }

    private String cardState(int index) {
        return preferences().getString(cardStateKey(index), null);
    }

    private String cardStateKey(int index) {
        return "card_state_" + index;
    }

    private void registerPackageChangeReceiver() {
        if (packageReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        registerReceiver(packageChangedReceiver, filter);
        packageReceiverRegistered = true;
    }

    private void unregisterPackageChangeReceiver() {
        if (!packageReceiverRegistered) {
            return;
        }
        unregisterReceiver(packageChangedReceiver);
        packageReceiverRegistered = false;
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

    private static final class HomeCardFocus {
        private final int index;
        private final ComponentName component;

        private HomeCardFocus(int index, ComponentName component) {
            this.index = index;
            this.component = component;
        }
    }
}
