package com.peng.video;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

final class VideoPreferences {
    static final String PREFERENCES = "iptv_sources";
    static final String M3U_URL = "m3u_url";
    static final String EPG_URL = "epg_url";
    private static final String LAST_CHANNEL_URL = "last_channel_url";
    private static final String DEFAULT_LINE_PREFIX = "default_line_";

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
        preferences(context).edit()
                .putString(M3U_URL, m3uUrl.trim())
                .putString(EPG_URL, epgUrl.trim())
                .apply();
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

    static boolean isHttpUrl(String value) {
        Uri uri = Uri.parse(value);
        return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null;
    }
}
