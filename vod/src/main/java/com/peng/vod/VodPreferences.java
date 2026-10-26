package com.peng.vod;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class VodPreferences {
    private static final String PREFERENCES = "vod_sources";
    private static final String SOURCE_URL = "source_url";
    private static final String SOURCE_URLS = "source_urls";

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

    static List<String> sourceUrls(Context context) {
        Set<String> saved = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getStringSet(SOURCE_URLS, null);
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        if (saved != null) {
            for (String value : saved) {
                if (isHttpUrl(value)) {
                    urls.add(value.trim());
                }
            }
        } else if (isHttpUrl(sourceUrl(context))) {
            urls.add(sourceUrl(context));
        }
        return new ArrayList<>(urls);
    }

    static void saveSourceUrls(Context context, List<String> values) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        for (String value : values) {
            if (isHttpUrl(value)) {
                urls.add(value.trim());
            }
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putStringSet(SOURCE_URLS, urls)
                .remove(SOURCE_URL)
                .apply();
    }

    static boolean isHttpUrl(String value) {
        Uri uri = Uri.parse(value);
        return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null;
    }
}
