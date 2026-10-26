package com.peng.vod;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

final class VodPreferences {
    private static final String PREFERENCES = "vod_sources";
    private static final String SOURCE_URL = "source_url";

    private VodPreferences() {
    }

    static String sourceUrl(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(SOURCE_URL, "");
    }

    static void saveSourceUrl(Context context, String value) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(SOURCE_URL, value.trim()).apply();
    }

    static boolean isHttpUrl(String value) {
        Uri uri = Uri.parse(value);
        return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null;
    }
}
