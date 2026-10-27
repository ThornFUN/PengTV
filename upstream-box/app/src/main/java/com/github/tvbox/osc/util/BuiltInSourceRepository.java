package com.github.tvbox.osc.util;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public final class BuiltInSourceRepository {
    private static final String CONFIG_FILE = "default-configs.json";
    private static final String LIVE_SOURCE_FILE = "default-live-sources.json";

    private BuiltInSourceRepository() {
    }

    public static List<Source> getConfigSources(Context context) throws IOException {
        return readSources(context, CONFIG_FILE, "configs");
    }

    public static List<Source> getLiveSources(Context context) throws IOException {
        return readSources(context, LIVE_SOURCE_FILE, "sources");
    }

    private static List<Source> readSources(Context context, String fileName, String arrayName) throws IOException {
        InputStream inputStream = null;
        Reader reader = null;
        try {
            inputStream = context.getAssets().open(fileName);
            reader = new InputStreamReader(inputStream, "UTF-8");
            JsonObject root = new Gson().fromJson(reader, JsonObject.class);
            if (root == null || !root.has(arrayName) || !root.get(arrayName).isJsonArray()) {
                throw new IOException("Missing " + arrayName + " in " + fileName);
            }
            JsonArray sourceArray = root.getAsJsonArray(arrayName);
            List<Source> sources = new ArrayList<>();
            for (JsonElement element : sourceArray) {
                Source source = new Gson().fromJson(element, Source.class);
                if (source != null && !isEmpty(source.name) && !isEmpty(source.url)) {
                    sources.add(source);
                }
            }
            if (sources.isEmpty()) {
                throw new IOException("No valid sources in " + fileName);
            }
            return sources;
        } catch (RuntimeException e) {
            throw new IOException("Invalid " + fileName, e);
        } finally {
            if (reader != null) {
                reader.close();
            } else if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static class Source {
        public String id;
        public String name;
        public String url;
        public String description;
        public String group;
        public List<String> groups;
        @SerializedName("default")
        public boolean isDefault;
    }
}
