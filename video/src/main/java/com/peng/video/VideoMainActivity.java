package com.peng.video;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Xml;
import android.view.Gravity;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.xmlpull.v1.XmlPullParser;

public final class VideoMainActivity extends Activity {
    private static final int MAX_PLAYLIST_CHARACTERS = 2 * 1024 * 1024;
    private static final int CHANNEL_COLUMN_COUNT = 5;
    private static final Pattern ATTRIBUTE_PATTERN =
            Pattern.compile("([\\w-]+)=\"([^\"]*)\"");

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private GridLayout channelList;
    private TextView status;
    private View firstChannel;
    private boolean initialLoad = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadChannels(initialLoad);
        initialLoad = false;
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
        TextView sources = action("源设置");
        sources.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(VideoMainActivity.this, SourceImportActivity.class));
            }
        });
        header.addView(sources, new LinearLayout.LayoutParams(dp(120), dp(52)));
        root.addView(header);

        status = text("", 16, Color.rgb(180, 205, 233));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(12);
        root.addView(status, statusParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        channelList = new GridLayout(this);
        channelList.setColumnCount(CHANNEL_COLUMN_COUNT);
        channelList.setPadding(0, dp(14), 0, dp(18));
        scroll.addView(channelList);
        root.addView(scroll, new LinearLayout.LayoutParams(
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
        channelList.removeAllViews();
        if (channels.isEmpty()) {
            showEmpty("直播源中没有可播放频道。");
            return;
        }
        status.setText("已加载 " + channels.size() + " 个频道（含 "
                + defaultChannelCount + " 个默认频道）"
                + (userSourceFailed ? "；追加播放列表加载失败，仍可播放默认频道。"
                : guide.failed ? "；节目单加载失败，仍可播放频道。"
                : guide.programmes.isEmpty() ? "" : "，已显示节目单预览"));
        PlaybackQueue.setChannels(channels);
        if (autoPlayLastChannel) {
            int lastChannelIndex = findChannelIndex(VideoPreferences.lastChannelUrl(this), channels);
            if (lastChannelIndex >= 0) {
                startPlayback(lastChannelIndex);
                return;
            }
        }
        for (int index = 0; index < channels.size(); index++) {
            final Channel channel = channels.get(index);
            final int channelIndex = index;
            String channelLabel = String.format(Locale.US, "%03d  %s", index + 1, channel.name);
            TextView row = action(channelLabel);
            String programme = guide.programmes.get(channel.id);
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
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(340);
            params.height = programme == null ? dp(72) : dp(88);
            params.setMargins(0, 0, dp(14), dp(14));
            channelList.addView(row, params);
            if (index == 0) {
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

    private void showEmpty(String message) {
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

    static final class Channel {
        final String name;
        final String id;
        final String url;

        Channel(String name, String id, String url) {
            this.name = name;
            this.id = id;
            this.url = url;
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
