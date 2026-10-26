package com.peng.vod;

import android.text.TextUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

final class VodCatalog {
    private static final int MAX_CONFIG_BYTES = 1024 * 1024;

    static final class Item {
        final String category;
        final String title;
        final String description;
        final String url;

        Item(String category, String title, String description, String url) {
            this.category = category;
            this.title = title;
            this.description = description;
            this.url = url;
        }
    }

    private VodCatalog() {
    }

    static List<Item> defaultItems() {
        List<Item> items = new ArrayList<>();
        items.add(new Item("测试资源", "Apple HLS 多码率示例",
                "Apple 官方公开播放器测试流，含多清晰度、音轨和字幕。",
                "https://devstreaming-cdn.apple.com/videos/streaming/examples/"
                        + "img_bipbop_adv_example_ts/master.m3u8"));
        items.add(new Item("公开直播", "NASA TV Public",
                "NASA 公开频道直播流，通过 Akamai CDN 分发。",
                "https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8"));
        return items;
    }

    static List<Item> load(String source) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", "PENGVod/1.0");
        InputStream input = connection.getInputStream();
        try {
            String json = read(input);
            return parse(json);
        } finally {
            input.close();
            connection.disconnect();
        }
    }

    private static String read(InputStream input) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            text.append(buffer, 0, read);
            if (text.length() > MAX_CONFIG_BYTES) {
                throw new IOException("配置文件超过 1 MB 限制");
            }
        }
        return text.toString();
    }

    private static List<Item> parse(String json) throws IOException {
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("version", 0) != 1) {
                throw new IOException("仅支持 version=1 的影视源配置");
            }
            JSONArray items = root.optJSONArray("items");
            if (items == null) {
                throw new IOException("影视源缺少 items 数组");
            }
            List<Item> result = new ArrayList<>();
            for (int index = 0; index < items.length(); index++) {
                JSONObject item = items.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                String category = item.optString("category", "其他").trim();
                String title = item.optString("title").trim();
                String description = item.optString("description").trim();
                JSONArray streams = item.optJSONArray("streams");
                String url = "";
                if (streams != null && streams.length() > 0) {
                    JSONObject stream = streams.optJSONObject(0);
                    if (stream != null) {
                        url = stream.optString("url").trim();
                    }
                }
                if (!TextUtils.isEmpty(title) && VodPreferences.isHttpUrl(url)) {
                    result.add(new Item(category.length() == 0 ? "其他" : category, title,
                            description, url));
                }
            }
            if (result.isEmpty()) {
                throw new IOException("影视源没有可播放条目");
            }
            return result;
        } catch (JSONException exception) {
            throw new IOException("影视源不是有效 JSON", exception);
        }
    }
}
