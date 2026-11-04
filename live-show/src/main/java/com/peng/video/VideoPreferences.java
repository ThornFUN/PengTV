package com.peng.video;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class VideoPreferences {
    static final String PREFERENCES = "iptv_sources";
    static final String M3U_URL = "m3u_url";
    static final String EPG_URL = "epg_url";
    private static final String LIVE_SOURCES = "live_sources";
    private static final String LAST_CHANNEL_URL = "last_channel_url";
    private static final String DEFAULT_LINE_PREFIX = "default_line_";
    private static final String HIDDEN_CHANNELS = "hidden_channels";
    private static final String PLAYBACK_QUALITY = "playback_quality";
    private static final String LINE_FAILURE_PREFIX = "line_failure_";
    private static final String LINE_READY_COUNT_PREFIX = "line_ready_count_";
    private static final String LINE_STARTUP_TOTAL_PREFIX = "line_startup_total_";
    static final int QUALITY_SMOOTH = 0;
    static final int QUALITY_BALANCED = 1;
    static final int QUALITY_HIGH = 2;

    private VideoPreferences() {
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    static String m3uUrl(Context context) {
        return preferences(context).getString(M3U_URL, "");
    }

    static String epgUrl(Context context) {
        return preferences(context).getString(EPG_URL, "");
    }

    static void save(Context context, String m3uUrl, String epgUrl) {
        if (isHttpUrl(m3uUrl)) {
            upsertLiveSource(context, new LiveSource("legacy-import", "导入的直播源",
                    m3uUrl, epgUrl, true));
        } else {
            deleteLiveSource(context, "legacy-import");
        }
    }

    static List<LiveSource> liveSources(Context context) {
        String value = preferences(context).getString(LIVE_SOURCES, "");
        List<LiveSource> sources = new ArrayList<>();
        if (value.length() > 0) {
            try {
                JSONArray array = new JSONArray(value);
                for (int index = 0; index < array.length(); index++) {
                    JSONObject item = array.optJSONObject(index);
                    if (item == null) {
                        continue;
                    }
                    String id = item.optString("id").trim();
                    String name = item.optString("name").trim();
                    String m3u = item.optString("m3u").trim();
                    String epg = item.optString("epg").trim();
                    if (id.length() > 0 && name.length() > 0 && isHttpUrl(m3u)) {
                        sources.add(new LiveSource(id, name, m3u, epg, item.optBoolean("enabled", true)));
                    }
                }
            } catch (JSONException ignored) {
            }
            return sources;
        }
        String m3u = m3uUrl(context);
        if (isHttpUrl(m3u)) {
            sources.add(new LiveSource("legacy-import", "导入的直播源", m3u,
                    epgUrl(context), true));
        }
        return sources;
    }

    static void upsertLiveSource(Context context, LiveSource source) {
        List<LiveSource> sources = liveSources(context);
        boolean replaced = false;
        for (int index = 0; index < sources.size(); index++) {
            if (sources.get(index).id.equals(source.id)) {
                sources.set(index, source);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            sources.add(source);
        }
        saveLiveSources(context, sources);
    }

    static void deleteLiveSource(Context context, String sourceId) {
        List<LiveSource> sources = liveSources(context);
        for (int index = sources.size() - 1; index >= 0; index--) {
            if (sources.get(index).id.equals(sourceId)) {
                sources.remove(index);
            }
        }
        saveLiveSources(context, sources);
    }

    private static void saveLiveSources(Context context, List<LiveSource> sources) {
        JSONArray array = new JSONArray();
        for (LiveSource source : sources) {
            try {
                JSONObject item = new JSONObject();
                item.put("id", source.id);
                item.put("name", source.name);
                item.put("m3u", source.m3uUrl);
                item.put("epg", source.epgUrl);
                item.put("enabled", source.enabled);
                array.put(item);
            } catch (JSONException ignored) {
            }
        }
        preferences(context).edit().putString(LIVE_SOURCES, array.toString()).apply();
    }

    static final class LiveSource {
        final String id;
        final String name;
        final String m3uUrl;
        final String epgUrl;
        final boolean enabled;

        LiveSource(String id, String name, String m3uUrl, String epgUrl, boolean enabled) {
            this.id = id;
            this.name = name.trim();
            this.m3uUrl = m3uUrl.trim();
            this.epgUrl = epgUrl.trim();
            this.enabled = enabled;
        }
    }

    static String lastChannelUrl(Context context) {
        return preferences(context).getString(LAST_CHANNEL_URL, "");
    }

    static void saveLastChannelUrl(Context context, String url) {
        preferences(context).edit().putString(LAST_CHANNEL_URL, url).apply();
    }

    static String defaultLineUrl(Context context, String channelName) {
        return preferences(context).getString(DEFAULT_LINE_PREFIX + channelName, "");
    }

    static void saveDefaultLineUrl(Context context, String channelName, String url) {
        preferences(context).edit().putString(DEFAULT_LINE_PREFIX + channelName, url).apply();
    }

    static int playbackQuality(Context context) {
        return preferences(context).getInt(PLAYBACK_QUALITY, QUALITY_SMOOTH);
    }

    static void savePlaybackQuality(Context context, int quality) {
        preferences(context).edit().putInt(PLAYBACK_QUALITY, quality).apply();
    }

    static void recordLineReady(Context context, String url, long startupMillis) {
        String key = lineKey(url);
        SharedPreferences preferences = preferences(context);
        long count = preferences.getLong(LINE_READY_COUNT_PREFIX + key, 0);
        long total = preferences.getLong(LINE_STARTUP_TOTAL_PREFIX + key, 0);
        preferences.edit()
                .putLong(LINE_READY_COUNT_PREFIX + key, count + 1)
                .putLong(LINE_STARTUP_TOTAL_PREFIX + key, total + Math.max(0, startupMillis))
                .apply();
    }

    static void recordLineFailure(Context context, String url) {
        String key = lineKey(url);
        SharedPreferences preferences = preferences(context);
        long failures = preferences.getLong(LINE_FAILURE_PREFIX + key, 0);
        preferences.edit().putLong(LINE_FAILURE_PREFIX + key, Math.min(failures + 1, 100)).apply();
    }

    static long lineHealthScore(Context context, String url) {
        String key = lineKey(url);
        SharedPreferences preferences = preferences(context);
        long failures = preferences.getLong(LINE_FAILURE_PREFIX + key, 0);
        long readyCount = preferences.getLong(LINE_READY_COUNT_PREFIX + key, 0);
        long startupTotal = preferences.getLong(LINE_STARTUP_TOTAL_PREFIX + key, 0);
        long averageStartup = readyCount == 0 ? 15_000 : startupTotal / readyCount;
        return failures * 60_000 + averageStartup;
    }

    static void hideChannel(Context context, String channelName) {
        Set<String> hidden = new HashSet<>(hiddenChannels(context));
        hidden.add(channelName);
        preferences(context).edit().putStringSet(HIDDEN_CHANNELS, hidden).apply();
    }

    static void restoreChannel(Context context, String channelName) {
        Set<String> hidden = new HashSet<>(hiddenChannels(context));
        hidden.remove(channelName);
        preferences(context).edit().putStringSet(HIDDEN_CHANNELS, hidden).apply();
    }

    static List<String> hiddenChannels(Context context) {
        Set<String> hidden = preferences(context).getStringSet(HIDDEN_CHANNELS,
                new HashSet<String>());
        return new ArrayList<>(hidden);
    }

    static Set<String> hiddenChannelNameSet(Context context) {
        return new HashSet<>(hiddenChannels(context));
    }

    static boolean isHttpUrl(String value) {
        Uri uri = Uri.parse(value);
        return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null;
    }

    private static String lineKey(String url) {
        return Base64.encodeToString(url.getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE);
    }
}
