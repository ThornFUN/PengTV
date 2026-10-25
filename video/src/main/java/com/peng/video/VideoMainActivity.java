package com.peng.video;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Xml;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.xmlpull.v1.XmlPullParser;

public final class VideoMainActivity extends Activity {
    public static final String EXTRA_CHANNEL_LIST_ONLY = "channel_list_only";
    private static final int MAX_PLAYLIST_CHARACTERS = 2 * 1024 * 1024;
    private static final int CHANNEL_COLUMN_COUNT = 3;
    private static final int CHANNELS_PER_PAGE = 60;
    private static final Pattern ATTRIBUTE_PATTERN =
            Pattern.compile("([\\w-]+)=\"([^\"]*)\"");

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private GridLayout channelList;
    private ScrollView channelScroll;
    private TextView status;
    private View firstChannel;
    private View sourceSettings;
    private List<Channel> allChannels = Collections.emptyList();
    private GuideResult channelGuide;
    private int currentPage;
    private int defaultChannelCount;
    private boolean userSourceFailed;
    private final Map<String, List<Integer>> categoryChannels = new LinkedHashMap<>();
    private List<Integer> visibleChannelIndexes = Collections.emptyList();
    private String selectedCategory;
    private boolean initialLoad = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initialLoad = !getIntent().getBooleanExtra(EXTRA_CHANNEL_LIST_ONLY, false);
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadChannels(initialLoad);
        initialLoad = false;
    }

    @Override
    public void onBackPressed() {
        if (selectedCategory != null) {
            showCategoryList();
            return;
        }
        super.onBackPressed();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(34), dp(24), dp(34), dp(28));
        root.setBackgroundColor(Color.rgb(16, 26, 42));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("PENG影视", 30, Color.WHITE);
        header.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        sourceSettings = action("源设置");
        sourceSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(VideoMainActivity.this, SourceImportActivity.class));
            }
        });
        sourceSettings.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                        && event.getAction() == KeyEvent.ACTION_UP
                        && firstChannel != null) {
                    firstChannel.requestFocus();
                    return true;
                }
                return false;
            }
        });
        header.addView(sourceSettings, new LinearLayout.LayoutParams(dp(120), dp(52)));
        root.addView(header);

        status = text("", 16, Color.rgb(180, 205, 233));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(12);
        root.addView(status, statusParams);

        channelScroll = new ScrollView(this);
        channelScroll.setClipToPadding(false);
        channelList = new GridLayout(this);
        channelList.setColumnCount(CHANNEL_COLUMN_COUNT);
        channelList.setPadding(0, dp(14), 0, dp(18));
        channelScroll.addView(channelList);
        root.addView(channelScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
        setContentView(root);
    }

    private void reloadChannels(final boolean autoPlayLastChannel) {
        status.setText("正在加载频道和节目单...");
        channelList.removeAllViews();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Channel> channels;
                try {
                    channels = DefaultChannels.create(VideoMainActivity.this);
                } catch (final IOException exception) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            showEmpty("内置频道配置读取失败。");
                        }
                    });
                    return;
                }
                final int defaultChannelCount = channels.size();
                try {
                    String source = VideoPreferences.m3uUrl(VideoMainActivity.this);
                    if (VideoPreferences.isHttpUrl(source)) {
                        channels.addAll(loadChannels(source));
                    }
                    final GuideResult guide = loadGuide(VideoPreferences.epgUrl(VideoMainActivity.this));
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            showChannels(channels, guide, false, defaultChannelCount, autoPlayLastChannel);
                        }
                    });
                } catch (final IOException exception) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            showChannels(channels, new GuideResult(new HashMap<String, String>(), false),
                                    true, defaultChannelCount, autoPlayLastChannel);
                        }
                    });
                }
            }
        }, "iptv-loader").start();
    }

    private void showChannels(List<Channel> channels, GuideResult guide, boolean userSourceFailed,
            int defaultChannelCount, boolean autoPlayLastChannel) {
        if (channels.isEmpty()) {
            showEmpty("直播源中没有可播放频道。");
            return;
        }
        allChannels = channels;
        channelGuide = guide;
        this.userSourceFailed = userSourceFailed;
        this.defaultChannelCount = defaultChannelCount;
        PlaybackQueue.setChannels(channels);
        buildCategoryChannels();
        if (autoPlayLastChannel) {
            int lastChannelIndex = findChannelIndex(VideoPreferences.lastChannelUrl(this), channels);
            if (lastChannelIndex >= 0) {
                startPlayback(lastChannelIndex);
                return;
            }
        }
        showCategoryList();
    }

    private void buildCategoryChannels() {
        categoryChannels.clear();
        for (int index = 0; index < allChannels.size(); index++) {
            String category = channelCategory(allChannels.get(index));
            List<Integer> indexes = categoryChannels.get(category);
            if (indexes == null) {
                indexes = new ArrayList<>();
                categoryChannels.put(category, indexes);
            }
            indexes.add(index);
        }
    }

    private void showCategoryList() {
        selectedCategory = null;
        visibleChannelIndexes = Collections.emptyList();
        channelList.removeAllViews();
        channelScroll.scrollTo(0, 0);
        status.setText("已加载 " + allChannels.size() + " 个频道，请先选择分类"
                + (userSourceFailed ? "；追加播放列表加载失败，仍可播放默认频道。"
                : channelGuide.failed ? "；节目单加载失败，仍可播放频道。" : ""));
        int index = 0;
        for (final Map.Entry<String, List<Integer>> entry : categoryChannels.entrySet()) {
            final int categoryIndex = index++;
            TextView row = action(entry.getKey() + "\n" + entry.getValue().size() + " 个频道");
            row.setTextSize(20);
            row.setGravity(Gravity.CENTER);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showCategoryChannels(entry.getKey());
                }
            });
            row.setOnKeyListener(new View.OnKeyListener() {
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
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        if (categoryIndex < CHANNEL_COLUMN_COUNT && sourceSettings != null) {
                            sourceSettings.requestFocus();
                            return true;
                        }
                        return moveCategoryFocus(categoryIndex - CHANNEL_COLUMN_COUNT);
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        return moveCategoryFocus(categoryIndex + CHANNEL_COLUMN_COUNT);
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                            && categoryIndex % CHANNEL_COLUMN_COUNT > 0) {
                        return moveCategoryFocus(categoryIndex - 1);
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                            && categoryIndex % CHANNEL_COLUMN_COUNT < CHANNEL_COLUMN_COUNT - 1) {
                        return moveCategoryFocus(categoryIndex + 1);
                    }
                    return false;
                }
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = channelCardWidth();
            params.height = dp(100);
            params.setMargins(0, 0,
                    categoryIndex % CHANNEL_COLUMN_COUNT == CHANNEL_COLUMN_COUNT - 1 ? 0 : dp(14),
                    dp(14));
            channelList.addView(row, params);
            if (categoryIndex == 0) {
                firstChannel = row;
            }
        }
        if (firstChannel != null) {
            firstChannel.requestFocus();
        }
    }

    private void showCategoryChannels(String category) {
        selectedCategory = category;
        List<Integer> indexes = categoryChannels.get(category);
        visibleChannelIndexes = indexes == null ? Collections.<Integer>emptyList() : indexes;
        showChannelPage(0);
    }

    private void showChannelPage(int page) {
        int pageCount = (visibleChannelIndexes.size() + CHANNELS_PER_PAGE - 1)
                / CHANNELS_PER_PAGE;
        if (page < 0 || page >= pageCount) {
            return;
        }
        currentPage = page;
        channelList.removeAllViews();
        channelScroll.scrollTo(0, 0);
        firstChannel = null;
        int startIndex = page * CHANNELS_PER_PAGE;
        int endIndex = Math.min(startIndex + CHANNELS_PER_PAGE, visibleChannelIndexes.size());
        status.setText(selectedCategory + " · " + visibleChannelIndexes.size() + " 个频道，第 "
                + (page + 1) + "/" + pageCount + " 页"
                + (userSourceFailed ? "；追加播放列表加载失败，仍可播放默认频道。"
                : channelGuide.failed ? "；节目单加载失败，仍可播放频道。"
                : channelGuide.programmes.isEmpty() ? "" : "，已显示节目单预览"));
        for (int position = startIndex; position < endIndex; position++) {
            final int channelIndex = visibleChannelIndexes.get(position);
            final int channelPosition = position;
            final Channel channel = allChannels.get(channelIndex);
            String channelLabel = String.format(Locale.US, "%03d  %s", channelIndex + 1,
                    channelDisplayName(channel));
            TextView row = action(channelLabel);
            String programme = channelGuide.programmes.get(channel.id);
            if (programme != null && programme.length() > 0) {
                row.setText(channelLabel + "\n" + programme);
                row.setTextSize(16);
                row.setGravity(Gravity.CENTER_VERTICAL);
            }
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    startPlayback(channelIndex);
                }
            });
            row.setOnKeyListener(new View.OnKeyListener() {
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
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        if (channelPosition < CHANNEL_COLUMN_COUNT && sourceSettings != null) {
                            sourceSettings.requestFocus();
                            return true;
                        }
                        return moveChannelFocus(channelPosition - CHANNEL_COLUMN_COUNT);
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        return moveChannelFocus(channelPosition + CHANNEL_COLUMN_COUNT);
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                            && channelPosition % CHANNEL_COLUMN_COUNT > 0) {
                        return moveChannelFocus(channelPosition - 1);
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                            && channelPosition % CHANNEL_COLUMN_COUNT < CHANNEL_COLUMN_COUNT - 1) {
                        return moveChannelFocus(channelPosition + 1);
                    }
                    return false;
                }
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = channelCardWidth();
            params.height = programme == null ? dp(72) : dp(88);
            params.setMargins(0, 0,
                    channelPosition % CHANNEL_COLUMN_COUNT == CHANNEL_COLUMN_COUNT - 1 ? 0 : dp(14),
                    dp(14));
            channelList.addView(row, params);
            if (position == startIndex) {
                firstChannel = row;
            }
        }
        if (firstChannel != null) {
            firstChannel.requestFocus();
        }
    }

    private int findChannelIndex(String url, List<Channel> channels) {
        if (url.length() == 0) {
            return -1;
        }
        for (int index = 0; index < channels.size(); index++) {
            if (url.equals(channels.get(index).url)) {
                return index;
            }
        }
        return -1;
    }

    private void startPlayback(int index) {
        Channel channel = PlaybackQueue.channelAt(index);
        if (channel == null) {
            return;
        }
        VideoPreferences.saveLastChannelUrl(this, channel.url);
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_INDEX, index);
        intent.putExtra(PlayerActivity.EXTRA_NAME, channel.name);
        intent.putExtra(PlayerActivity.EXTRA_URL, channel.url);
        startActivity(intent);
    }

    private boolean moveChannelFocus(int index) {
        if (index < 0 || index >= visibleChannelIndexes.size()) {
            return true;
        }
        int targetPage = index / CHANNELS_PER_PAGE;
        if (targetPage != currentPage) {
            showChannelPage(targetPage);
            final int targetIndex = index;
            channelList.post(new Runnable() {
                @Override
                public void run() {
                    focusChannel(targetIndex);
                }
            });
            return true;
        }
        focusChannel(index);
        return true;
    }

    private void focusChannel(int index) {
        View target = channelList.getChildAt(index - currentPage * CHANNELS_PER_PAGE);
        if (target == null) {
            return;
        }
        target.requestFocus();
        Rect targetBounds = new Rect();
        target.getDrawingRect(targetBounds);
        targetBounds.offset(target.getLeft(), target.getTop());
        channelScroll.requestChildRectangleOnScreen(channelList, targetBounds, true);
    }

    private boolean moveCategoryFocus(int index) {
        if (index < 0 || index >= channelList.getChildCount()) {
            return true;
        }
        View target = channelList.getChildAt(index);
        target.requestFocus();
        return true;
    }

    private String channelCategory(Channel channel) {
        int separator = channel.name.indexOf(" · ");
        return separator > 0 ? channel.name.substring(0, separator) : "自定义频道";
    }

    private String channelDisplayName(Channel channel) {
        int separator = channel.name.indexOf(" · ");
        return separator > 0 ? channel.name.substring(separator + 3) : channel.name;
    }

    private void showEmpty(String message) {
        allChannels = Collections.emptyList();
        categoryChannels.clear();
        visibleChannelIndexes = Collections.emptyList();
        selectedCategory = null;
        channelList.removeAllViews();
        status.setText(message);
        TextView configure = action("配置直播源");
        configure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(VideoMainActivity.this, SourceImportActivity.class));
            }
        });
        channelList.addView(configure, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64)));
        configure.requestFocus();
    }

    private List<Channel> loadChannels(String source) throws IOException {
        List<Channel> channels = new ArrayList<>();
        BufferedReader reader = openText(source);
        String name = null;
        String id = "";
        int charactersRead = 0;
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                charactersRead += line.length();
                if (charactersRead > MAX_PLAYLIST_CHARACTERS) {
                    throw new IOException("playlist is too large");
                }
                if (line.startsWith("#EXTINF:")) {
                    name = channelName(line);
                    id = attribute(line, "tvg-id");
                } else if (name != null && line.length() > 0 && !line.startsWith("#")) {
                    channels.add(new Channel(name, id, line.trim()));
                    name = null;
                    id = "";
                }
            }
        } finally {
            reader.close();
        }
        return channels;
    }

    private GuideResult loadGuide(String source) {
        Map<String, String> guide = new HashMap<>();
        if (!VideoPreferences.isHttpUrl(source)) {
            return new GuideResult(guide, false);
        }
        try {
            InputStream input = openConnection(source).getInputStream();
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(new InputStreamReader(input, "UTF-8"));
                String channelId = null;
                String title = null;
                while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                    if (parser.getEventType() == XmlPullParser.START_TAG
                            && "programme".equals(parser.getName())) {
                        channelId = parser.getAttributeValue(null, "channel");
                        title = null;
                    } else if (parser.getEventType() == XmlPullParser.START_TAG
                            && "title".equals(parser.getName()) && channelId != null) {
                        title = parser.nextText();
                    } else if (parser.getEventType() == XmlPullParser.END_TAG
                            && "programme".equals(parser.getName())
                            && channelId != null && title != null && !guide.containsKey(channelId)) {
                        guide.put(channelId, title);
                        channelId = null;
                    }
                    parser.next();
                }
            } finally {
                input.close();
            }
        } catch (Exception exception) {
            return new GuideResult(guide, true);
        }
        return new GuideResult(guide, false);
    }

    private BufferedReader openText(String source) throws IOException {
        return new BufferedReader(new InputStreamReader(openConnection(source).getInputStream(), "UTF-8"));
    }

    private HttpURLConnection openConnection(String source) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", "PENGVideo/1.0");
        return connection;
    }

    private String channelName(String line) {
        int comma = line.indexOf(',');
        String name = comma >= 0 ? line.substring(comma + 1).trim() : "";
        return name.length() == 0 ? "未命名频道" : name;
    }

    private String attribute(String line, String name) {
        Matcher matcher = ATTRIBUTE_PATTERN.matcher(line);
        while (matcher.find()) {
            if (name.equals(matcher.group(1))) {
                return matcher.group(2);
            }
        }
        return "";
    }

    private TextView action(String value) {
        TextView view = text(value, 18, Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(20), dp(8), dp(20), dp(8));
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

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int channelCardWidth() {
        int horizontalPadding = dp(34) * 2;
        int horizontalSpacing = dp(14) * (CHANNEL_COLUMN_COUNT - 1);
        return (getResources().getDisplayMetrics().widthPixels - horizontalPadding - horizontalSpacing)
                / CHANNEL_COLUMN_COUNT;
    }

    static final class Channel {
        final String name;
        final String id;
        final String url;
        private final List<String> urls = new ArrayList<>();

        Channel(String name, String id, String url) {
            this.name = name;
            this.id = id;
            this.url = url;
            urls.add(url);
        }

        void addFallbackUrl(String fallbackUrl) {
            if (!urls.contains(fallbackUrl)) {
                urls.add(fallbackUrl);
            }
        }

        List<String> urls() {
            return new ArrayList<>(urls);
        }
    }

    private static final class GuideResult {
        private final Map<String, String> programmes;
        private final boolean failed;

        private GuideResult(Map<String, String> programmes, boolean failed) {
            this.programmes = programmes;
            this.failed = failed;
        }
    }
}
