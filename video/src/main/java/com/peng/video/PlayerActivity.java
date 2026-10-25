package com.peng.video;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.ui.PlayerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PlayerActivity extends Activity {
    private static final String TAG = "PENG_VIDEO";
    private static final int DRAWER_COLUMN_COUNT = 3;
    private static final int DRAWER_CHANNELS_PER_PAGE = 30;
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_INDEX = "index";
    private ExoPlayer player;
    private final Handler overlayHandler = new Handler(Looper.getMainLooper());
    private final Handler channelInputHandler = new Handler(Looper.getMainLooper());
    private final Handler retryHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideOverlay = new Runnable() {
        @Override
        public void run() {
            title.setVisibility(View.INVISIBLE);
            channelNumber.setVisibility(View.INVISIBLE);
        }
    };
    private TextView title;
    private TextView channelNumber;
    private TextView loadingOverlay;
    private String fallbackName;
    private String fallbackUrl;
    private int currentIndex;
    private long lastBackPressedAt;
    private final StringBuilder channelInput = new StringBuilder();
    private final Runnable submitChannelInput = new Runnable() {
        @Override
        public void run() {
            submitChannelInput();
        }
    };
    private final Runnable retryPlayback = new Runnable() {
        @Override
        public void run() {
            prepareCurrentChannel();
        }
    };
    private String currentUrl;
    private int retryCount;
    private List<String> currentUrls = Collections.emptyList();
    private int currentUrlIndex;
    private TextView lineControl;
    private LinearLayout channelDrawer;
    private LinearLayout drawerCategories;
    private GridLayout drawerChannels;
    private ScrollView drawerChannelScroll;
    private TextView drawerTitle;
    private final Map<String, List<Integer>> drawerCategoryIndexes = new LinkedHashMap<>();
    private final Map<String, View> drawerCategoryViews = new LinkedHashMap<>();
    private final List<String> drawerCategoryNames = new ArrayList<>();
    private List<Integer> visibleDrawerIndexes = Collections.emptyList();
    private String selectedDrawerCategory;
    private int drawerPage;
    private boolean linePreviewing;
    private int linePreviewStartIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        fallbackName = getIntent().getStringExtra(EXTRA_NAME);
        fallbackUrl = getIntent().getStringExtra(EXTRA_URL);
        if (!VideoPreferences.isHttpUrl(fallbackUrl)) {
            Toast.makeText(this, "频道地址无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        PlayerView playerView = new PlayerView(this);
        playerView.setKeepContentOnPlayerReset(false);
        root.addView(playerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        loadingOverlay = new TextView(this);
        loadingOverlay.setText("正在加载...");
        loadingOverlay.setTextColor(Color.WHITE);
        loadingOverlay.setTextSize(20);
        loadingOverlay.setGravity(Gravity.CENTER);
        loadingOverlay.setBackgroundColor(Color.BLACK);
        root.addView(loadingOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setPadding(dp(20), dp(14), dp(20), dp(14));
        title.setBackgroundColor(Color.argb(150, 0, 0, 0));
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.LEFT);
        root.addView(title, titleParams);
        channelNumber = new TextView(this);
        channelNumber.setTextColor(Color.WHITE);
        channelNumber.setTextSize(34);
        channelNumber.setGravity(Gravity.CENTER);
        channelNumber.setPadding(dp(22), dp(8), dp(22), dp(8));
        channelNumber.setBackgroundColor(Color.argb(150, 0, 0, 0));
        FrameLayout.LayoutParams numberParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.RIGHT);
        root.addView(channelNumber, numberParams);
        lineControl = new TextView(this);
        lineControl.setTextColor(Color.WHITE);
        lineControl.setTextSize(18);
        lineControl.setGravity(Gravity.CENTER);
        lineControl.setPadding(dp(28), dp(16), dp(28), dp(16));
        lineControl.setBackground(focusBackground());
        lineControl.setVisibility(View.GONE);
        FrameLayout.LayoutParams lineControlParams = new FrameLayout.LayoutParams(
                dp(760), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lineControlParams.bottomMargin = dp(34);
        root.addView(lineControl, lineControlParams);
        buildChannelDrawer(root);
        setContentView(root);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    retryCount = 0;
                    loadingOverlay.setVisibility(View.GONE);
                    if (linePreviewing) {
                        updateLineControl("预览正常，按确认设为默认");
                    }
                    Log.i(TAG, "Playback ready: " + currentUrl);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Log.e(TAG, "Playback error: " + currentUrl, error);
                if (linePreviewing) {
                    updateLineControl("该线路无法播放，可继续左右切换");
                    return;
                }
                if (retryCount < 3) {
                    retryCount++;
                    loadingOverlay.setText("播放中断，正在重试（" + retryCount + "/3）...");
                    loadingOverlay.setVisibility(View.VISIBLE);
                    retryHandler.removeCallbacks(retryPlayback);
                    retryHandler.postDelayed(retryPlayback,
                            error.getCause() instanceof BehindLiveWindowException ? 0 : 2000);
                    return;
                }
                if (playFallbackUrl()) {
                    return;
                }
                loadingOverlay.setText("播放失败，请切换频道或稍后重试");
                loadingOverlay.setVisibility(View.VISIBLE);
                Toast.makeText(PlayerActivity.this, "播放失败，请切换频道或稍后重试", Toast.LENGTH_SHORT).show();
            }
        });
        playChannel(getIntent().getIntExtra(EXTRA_INDEX, 0));
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            toggleChannelDrawer();
            return true;
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            appendChannelDigit(keyCode - KeyEvent.KEYCODE_0);
            return true;
        }
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
                && channelInput.length() > 0) {
            submitChannelInput();
            return true;
        }
        if (linePreviewing && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            previewLine(keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            int channelCount = PlaybackQueue.size();
            if (channelCount > 1) {
                int offset = keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                playChannel((currentIndex + offset + channelCount) % channelCount);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (linePreviewing) {
                savePreviewLine();
            } else {
                showLineControl();
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (channelDrawer.getVisibility() == View.VISIBLE) {
            channelDrawer.setVisibility(View.GONE);
            return;
        }
        if (linePreviewing) {
            cancelLinePreview();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackPressedAt <= 2000) {
            finishAffinity();
            return;
        }
        lastBackPressedAt = now;
        Toast.makeText(this, "再按一次返回退出 PENG影视", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onStop() {
        overlayHandler.removeCallbacks(hideOverlay);
        channelInputHandler.removeCallbacks(submitChannelInput);
        retryHandler.removeCallbacks(retryPlayback);
        if (player != null) {
            player.pause();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            player.release();
        }
        super.onDestroy();
    }

    private void playChannel(int index) {
        VideoMainActivity.Channel channel = PlaybackQueue.channelAt(index);
        String name = channel == null ? fallbackName : channel.name;
        String url = channel == null ? fallbackUrl : channel.url;
        if (!VideoPreferences.isHttpUrl(url)) {
            Toast.makeText(this, "频道地址无效", Toast.LENGTH_SHORT).show();
            return;
        }
        channelInput.setLength(0);
        channelInputHandler.removeCallbacks(submitChannelInput);
        retryHandler.removeCallbacks(retryPlayback);
        retryCount = 0;
        linePreviewing = false;
        lineControl.setVisibility(View.GONE);
        currentIndex = channel == null ? 0 : index;
        currentUrls = channel == null ? Collections.singletonList(url) : preferredUrls(channel);
        currentUrlIndex = 0;
        currentUrl = currentUrls.get(currentUrlIndex);
        title.setText(name == null ? "PENG影视" : name);
        channelNumber.setText(String.format(Locale.US, "%03d", currentIndex + 1));
        showChannelOverlay();
        VideoPreferences.saveLastChannelUrl(this, url);
        prepareCurrentChannel();
    }

    private List<String> preferredUrls(VideoMainActivity.Channel channel) {
        List<String> urls = new ArrayList<>(channel.urls());
        String preferredUrl = VideoPreferences.defaultLineUrl(this, channel.name);
        if (urls.remove(preferredUrl)) {
            urls.add(0, preferredUrl);
        }
        return urls;
    }

    private boolean playFallbackUrl() {
        if (currentUrlIndex + 1 >= currentUrls.size()) {
            return false;
        }
        currentUrl = currentUrls.get(++currentUrlIndex);
        retryCount = 0;
        loadingOverlay.setText("主线路失败，正在切换备用线路...");
        loadingOverlay.setVisibility(View.VISIBLE);
        retryHandler.removeCallbacks(retryPlayback);
        retryHandler.post(retryPlayback);
        return true;
    }

    private void prepareCurrentChannel() {
        if (!VideoPreferences.isHttpUrl(currentUrl)) {
            return;
        }
        loadingOverlay.setText("正在加载...");
        loadingOverlay.setVisibility(View.VISIBLE);
        player.setMediaItem(MediaItem.fromUri(Uri.parse(currentUrl)));
        player.prepare();
        player.play();
    }

    private void buildChannelDrawer(FrameLayout root) {
        channelDrawer = new LinearLayout(this);
        channelDrawer.setOrientation(LinearLayout.HORIZONTAL);
        channelDrawer.setPadding(dp(20), dp(22), dp(20), dp(22));
        channelDrawer.setBackgroundColor(Color.argb(242, 16, 26, 42));
        channelDrawer.setVisibility(View.GONE);

        ScrollView categoryScroll = new ScrollView(this);
        drawerCategories = new LinearLayout(this);
        drawerCategories.setOrientation(LinearLayout.VERTICAL);
        categoryScroll.addView(drawerCategories);
        channelDrawer.addView(categoryScroll, new LinearLayout.LayoutParams(dp(250),
                LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout channelArea = new LinearLayout(this);
        channelArea.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams channelAreaParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
        channelAreaParams.leftMargin = dp(18);
        channelDrawer.addView(channelArea, channelAreaParams);

        drawerTitle = new TextView(this);
        drawerTitle.setTextColor(Color.WHITE);
        drawerTitle.setTextSize(22);
        channelArea.addView(drawerTitle);
        drawerChannelScroll = new ScrollView(this);
        drawerChannelScroll.setClipToPadding(false);
        drawerChannels = new GridLayout(this);
        drawerChannels.setColumnCount(DRAWER_COLUMN_COUNT);
        drawerChannels.setPadding(0, dp(16), 0, dp(16));
        drawerChannelScroll.addView(drawerChannels);
        channelArea.addView(drawerChannelScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        FrameLayout.LayoutParams drawerParams = new FrameLayout.LayoutParams(
                getResources().getDisplayMetrics().widthPixels * 3 / 4,
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.LEFT);
        root.addView(channelDrawer, drawerParams);
    }

    private void toggleChannelDrawer() {
        if (channelDrawer.getVisibility() == View.VISIBLE) {
            channelDrawer.setVisibility(View.GONE);
            return;
        }
        buildDrawerCategories();
        String category = channelCategory(PlaybackQueue.channelAt(currentIndex));
        if (!drawerCategoryIndexes.containsKey(category) && !drawerCategoryNames.isEmpty()) {
            category = drawerCategoryNames.get(0);
        }
        showDrawerCategory(category);
        channelDrawer.setVisibility(View.VISIBLE);
        View categoryView = drawerCategoryViews.get(category);
        if (categoryView != null) {
            categoryView.requestFocus();
        }
    }

    private void buildDrawerCategories() {
        drawerCategoryIndexes.clear();
        drawerCategoryViews.clear();
        drawerCategoryNames.clear();
        List<VideoMainActivity.Channel> channels = PlaybackQueue.channels();
        for (int index = 0; index < channels.size(); index++) {
            String category = channelCategory(channels.get(index));
            List<Integer> indexes = drawerCategoryIndexes.get(category);
            if (indexes == null) {
                indexes = new ArrayList<>();
                drawerCategoryIndexes.put(category, indexes);
                drawerCategoryNames.add(category);
            }
            indexes.add(index);
        }
        drawerCategories.removeAllViews();
        for (int index = 0; index < drawerCategoryNames.size(); index++) {
            final int categoryIndex = index;
            final String category = drawerCategoryNames.get(index);
            TextView item = drawerAction(category + "\n"
                    + drawerCategoryIndexes.get(category).size() + " 个频道");
            item.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View view, boolean focused) {
                    if (focused) {
                        showDrawerCategory(category);
                    }
                }
            });
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    focusDrawerChannel(0);
                }
            });
            item.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View view, int keyCode, KeyEvent event) {
                    if (keyCode != KeyEvent.KEYCODE_DPAD_UP
                            && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                            && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) {
                        return false;
                    }
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        return true;
                    }
                    if (event.getAction() != KeyEvent.ACTION_UP) {
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        focusDrawerChannel(0);
                    } else {
                        int target = categoryIndex
                                + (keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                        if (target >= 0 && target < drawerCategoryNames.size()) {
                            drawerCategoryViews.get(drawerCategoryNames.get(target)).requestFocus();
                        }
                    }
                    return true;
                }
            });
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(72));
            itemParams.bottomMargin = dp(12);
            drawerCategories.addView(item, itemParams);
            drawerCategoryViews.put(category, item);
        }
    }

    private void showDrawerCategory(String category) {
        if (category == null || !category.equals(selectedDrawerCategory)) {
            selectedDrawerCategory = category;
            visibleDrawerIndexes = drawerCategoryIndexes.get(category);
            drawerPage = 0;
        }
        showDrawerPage(drawerPage);
    }

    private void showDrawerPage(int page) {
        if (visibleDrawerIndexes == null || visibleDrawerIndexes.isEmpty()) {
            return;
        }
        int pageCount = (visibleDrawerIndexes.size() + DRAWER_CHANNELS_PER_PAGE - 1)
                / DRAWER_CHANNELS_PER_PAGE;
        if (page < 0 || page >= pageCount) {
            return;
        }
        drawerPage = page;
        drawerTitle.setText(selectedDrawerCategory + " · " + visibleDrawerIndexes.size()
                + " 个频道 · " + (page + 1) + "/" + pageCount + " 页");
        drawerChannels.removeAllViews();
        drawerChannelScroll.scrollTo(0, 0);
        int start = page * DRAWER_CHANNELS_PER_PAGE;
        int end = Math.min(start + DRAWER_CHANNELS_PER_PAGE, visibleDrawerIndexes.size());
        for (int position = start; position < end; position++) {
            final int channelPosition = position;
            final int channelIndex = visibleDrawerIndexes.get(position);
            VideoMainActivity.Channel channel = PlaybackQueue.channelAt(channelIndex);
            TextView item = drawerAction(String.format(Locale.US, "%03d  %s", channelIndex + 1,
                    channelDisplayName(channel)));
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    channelDrawer.setVisibility(View.GONE);
                    playChannel(channelIndex);
                }
            });
            item.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View view, int keyCode, KeyEvent event) {
                    if (keyCode != KeyEvent.KEYCODE_DPAD_UP
                            && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                            && keyCode != KeyEvent.KEYCODE_DPAD_LEFT
                            && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) {
                        return false;
                    }
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        return true;
                    }
                    if (event.getAction() != KeyEvent.ACTION_UP) {
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                            && channelPosition % DRAWER_COLUMN_COUNT == 0) {
                        drawerCategoryViews.get(selectedDrawerCategory).requestFocus();
                        return true;
                    }
                    int target = channelPosition;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        target -= DRAWER_COLUMN_COUNT;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        target += DRAWER_COLUMN_COUNT;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        target--;
                    } else if (channelPosition % DRAWER_COLUMN_COUNT < DRAWER_COLUMN_COUNT - 1) {
                        target++;
                    }
                    return moveDrawerChannelFocus(target);
                }
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = drawerChannelWidth();
            params.height = dp(68);
            params.setMargins(0, 0,
                    channelPosition % DRAWER_COLUMN_COUNT == DRAWER_COLUMN_COUNT - 1 ? 0 : dp(12),
                    dp(12));
            drawerChannels.addView(item, params);
        }
    }

    private boolean moveDrawerChannelFocus(int position) {
        if (position < 0 || position >= visibleDrawerIndexes.size()) {
            return true;
        }
        int targetPage = position / DRAWER_CHANNELS_PER_PAGE;
        if (targetPage != drawerPage) {
            showDrawerPage(targetPage);
            final int targetPosition = position;
            drawerChannels.post(new Runnable() {
                @Override
                public void run() {
                    focusDrawerChannel(targetPosition);
                }
            });
            return true;
        }
        focusDrawerChannel(position);
        return true;
    }

    private void focusDrawerChannel(int position) {
        if (position < 0 || position >= visibleDrawerIndexes.size()) {
            return;
        }
        View target = drawerChannels.getChildAt(position - drawerPage * DRAWER_CHANNELS_PER_PAGE);
        if (target == null) {
            return;
        }
        target.requestFocus();
        Rect bounds = new Rect();
        target.getDrawingRect(bounds);
        bounds.offset(target.getLeft(), target.getTop());
        drawerChannelScroll.requestChildRectangleOnScreen(drawerChannels, bounds, true);
    }

    private String channelCategory(VideoMainActivity.Channel channel) {
        if (channel == null) {
            return "";
        }
        int separator = channel.name.indexOf(" · ");
        return separator > 0 ? channel.name.substring(0, separator) : "自定义频道";
    }

    private String channelDisplayName(VideoMainActivity.Channel channel) {
        int separator = channel.name.indexOf(" · ");
        return separator > 0 ? channel.name.substring(separator + 3) : channel.name;
    }

    private TextView drawerAction(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(17);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(14), dp(6), dp(14), dp(6));
        view.setFocusable(true);
        view.setClickable(true);
        view.setBackground(focusBackground());
        return view;
    }

    private int drawerChannelWidth() {
        int drawerWidth = getResources().getDisplayMetrics().widthPixels * 3 / 4;
        int spacing = dp(12) * (DRAWER_COLUMN_COUNT - 1);
        return (drawerWidth - dp(250) - dp(58) - spacing) / DRAWER_COLUMN_COUNT;
    }

    private StateListDrawable focusBackground() {
        StateListDrawable drawable = new StateListDrawable();
        GradientDrawable focused = rounded(Color.rgb(62, 119, 184));
        focused.setStroke(dp(2), Color.WHITE);
        drawable.addState(new int[]{android.R.attr.state_focused}, focused);
        drawable.addState(new int[]{}, rounded(Color.argb(220, 35, 57, 88)));
        return drawable;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(12));
        return drawable;
    }

    private void showLineControl() {
        if (currentUrls.size() < 2) {
            Toast.makeText(this, "当前频道没有备用线路", Toast.LENGTH_SHORT).show();
            return;
        }
        linePreviewing = true;
        linePreviewStartIndex = currentUrlIndex;
        lineControl.setVisibility(View.VISIBLE);
        updateLineControl("左右切换线路，确认设为默认，返回取消");
    }

    private void previewLine(int offset) {
        currentUrlIndex = (currentUrlIndex + offset + currentUrls.size()) % currentUrls.size();
        currentUrl = currentUrls.get(currentUrlIndex);
        retryHandler.removeCallbacks(retryPlayback);
        retryCount = 0;
        updateLineControl("正在预览...");
        prepareCurrentChannel();
    }

    private void savePreviewLine() {
        VideoMainActivity.Channel channel = PlaybackQueue.channelAt(currentIndex);
        if (channel != null) {
            VideoPreferences.saveDefaultLineUrl(this, channel.name, currentUrl);
        }
        linePreviewing = false;
        lineControl.setVisibility(View.GONE);
        Toast.makeText(this, "已设为默认线路", Toast.LENGTH_SHORT).show();
    }

    private void cancelLinePreview() {
        currentUrlIndex = linePreviewStartIndex;
        currentUrl = currentUrls.get(currentUrlIndex);
        linePreviewing = false;
        lineControl.setVisibility(View.GONE);
        prepareCurrentChannel();
    }

    private void updateLineControl(String message) {
        lineControl.setText("线路 " + (currentUrlIndex + 1) + "/" + currentUrls.size() + " · " + message);
    }

    private void appendChannelDigit(int digit) {
        if (channelInput.length() >= channelNumberDigitLimit()) {
            channelInput.setLength(0);
        }
        channelInput.append(digit);
        title.setText("输入频道编号后按确认");
        channelNumber.setText(channelInput.toString());
        showChannelOverlay();
        channelInputHandler.removeCallbacks(submitChannelInput);
        channelInputHandler.postDelayed(submitChannelInput, 1500);
    }

    private int channelNumberDigitLimit() {
        return Math.max(3, String.valueOf(Math.max(1, PlaybackQueue.size())).length());
    }

    private void submitChannelInput() {
        if (channelInput.length() == 0) {
            return;
        }
        int channelNumberValue;
        try {
            channelNumberValue = Integer.parseInt(channelInput.toString());
        } catch (NumberFormatException exception) {
            channelInput.setLength(0);
            return;
        }
        channelInput.setLength(0);
        channelInputHandler.removeCallbacks(submitChannelInput);
        int channelCount = PlaybackQueue.size();
        if (channelNumberValue < 1 || channelNumberValue > channelCount) {
            Toast.makeText(this, "没有编号 " + channelNumberValue + " 的频道", Toast.LENGTH_SHORT).show();
            return;
        }
        playChannel(channelNumberValue - 1);
    }

    private void showChannelOverlay() {
        title.setVisibility(View.VISIBLE);
        channelNumber.setVisibility(View.VISIBLE);
        overlayHandler.removeCallbacks(hideOverlay);
        overlayHandler.postDelayed(hideOverlay, 5000);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
