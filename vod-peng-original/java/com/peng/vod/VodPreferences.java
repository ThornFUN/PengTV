package com.peng.vod;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class VodPreferences {
    private static final String PREFERENCES = "vod_sources";
    private static final String SOURCE_URL = "source_url";
    private static final String SOURCE_URLS = "source_urls";
    private static final String SOURCES = "sources_v2";

    private VodPreferences() {
    }

    static List<String> sourceUrls(Context context) {
        List<String> urls = new ArrayList<>();
        for (VodSource source : sources(context)) {
            if (source.enabled) {
                urls.add(source.url);
            }
        }
        return urls;
    }

    static List<VodSource> sources(Context context) {
        SharedPreferences preferences = preferences(context);
        String encoded = preferences.getString(SOURCES, "");
        List<VodSource> result = new ArrayList<>();
        if (encoded.length() > 0) {
            try {
                JSONArray values = new JSONArray(encoded);
                for (int index = 0; index < values.length(); index++) {
                    JSONObject value = values.optJSONObject(index);
                    if (value == null) {
                        continue;
                    }
                    String id = value.optString("id").trim();
                    String name = value.optString("name").trim();
                    String url = value.optString("url").trim();
                    if (id.length() > 0 && name.length() > 0 && isHttpUrl(url)) {
                        result.add(new VodSource(id, name, url,
                                value.optBoolean("enabled", true)));
                    }
                }
            } catch (JSONException ignored) {
            }
            return result;
        }

        Set<String> oldValues = preferences.getStringSet(SOURCE_URLS, null);
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        if (oldValues != null) {
            urls.addAll(oldValues);
        } else {
            String oldUrl = preferences.getString(SOURCE_URL, "");
            if (isHttpUrl(oldUrl)) {
                urls.add(oldUrl);
            }
        }
        for (String url : urls) {
            if (isHttpUrl(url)) {
                result.add(new VodSource(UUID.randomUUID().toString(), "导入的影视源",
                        url.trim(), true));
            }
        }
        if (!result.isEmpty()) {
            saveSources(context, result);
        }
        return result;
    }

    static void upsertSource(Context context, VodSource source) {
        List<VodSource> values = sources(context);
        boolean replaced = false;
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index).id.equals(source.id)) {
                values.set(index, source);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            values.add(source);
        }
        saveSources(context, values);
    }

    static void deleteSource(Context context, String sourceId) {
        List<VodSource> values = sources(context);
        for (int index = values.size() - 1; index >= 0; index--) {
            if (values.get(index).id.equals(sourceId)) {
                values.remove(index);
            }
        }
        saveSources(context, values);
    }

    private static void saveSources(Context context, List<VodSource> values) {
        JSONArray array = new JSONArray();
        for (VodSource source : values) {
            try {
                JSONObject value = new JSONObject();
                value.put("id", source.id);
                value.put("name", source.name);
                value.put("url", source.url);
                value.put("enabled", source.enabled);
                array.put(value);
            } catch (JSONException ignored) {
            }
        }
        preferences(context).edit().putString(SOURCES, array.toString())
                .remove(SOURCE_URL).remove(SOURCE_URLS).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    static boolean isHttpUrl(String value) {
        Uri uri = Uri.parse(value);
        return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null;
    }

    static final class VodSource {
        final String id;
        final String name;
        final String url;
        final boolean enabled;

        VodSource(String id, String name, String url, boolean enabled) {
            this.id = id;
            this.name = name.trim();
            this.url = url.trim();
            this.enabled = enabled;
        }
    }
}
