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

    static final class Stream {
        final String name;
        final String url;

        Stream(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    static final class Episode {
        final String season;
        final String title;
        final List<Stream> streams;

        Episode(String season, String title, List<Stream> streams) {
            this.season = season;
            this.title = title;
            this.streams = streams;
        }
    }

    static final class Item {
        final String category;
        final String title;
        final String description;
        final String sourceName;
        final List<Stream> streams;
        final List<Episode> episodes;

        Item(String category, String title, String description, String sourceName,
                List<Stream> streams, List<Episode> episodes) {
            this.category = category;
            this.title = title;
            this.description = description;
            this.sourceName = sourceName;
            this.streams = streams;
            this.episodes = episodes;
        }

        String firstUrl() {
            List<Stream> values = episodes.isEmpty() ? streams : episodes.get(0).streams;
            return values.isEmpty() ? "" : values.get(0).url;
        }

        String toJson() {
            JSONObject root = new JSONObject();
            try {
                root.put("category", category);
                root.put("title", title);
                root.put("description", description);
                root.put("source", sourceName);
                root.put("streams", streamsToJson(streams));
                JSONArray episodeArray = new JSONArray();
                for (Episode episode : episodes) {
                    JSONObject value = new JSONObject();
                    value.put("season", episode.season);
                    value.put("title", episode.title);
                    value.put("streams", streamsToJson(episode.streams));
                    episodeArray.put(value);
                }
                root.put("episodes", episodeArray);
            } catch (JSONException ignored) {
            }
            return root.toString();
        }

        static Item fromJson(String value) {
            try {
                JSONObject root = new JSONObject(value);
                return new Item(root.optString("category", "其他"), root.optString("title"),
                        root.optString("description"), root.optString("source"),
                        streamsFromJson(root.optJSONArray("streams")),
                        episodesFromJson(root.optJSONArray("episodes"), ""));
            } catch (JSONException exception) {
                return null;
            }
        }
    }

    static final class LoadResult {
        final List<Item> items;
        final int failedSources;

        LoadResult(List<Item> items, int failedSources) {
            this.items = items;
            this.failedSources = failedSources;
        }
    }

    private VodCatalog() {
    }

    static List<Item> defaultItems() {
        List<Item> items = new ArrayList<>();
        List<Stream> apple = new ArrayList<>();
        apple.add(new Stream("默认线路", "https://devstreaming-cdn.apple.com/videos/streaming/"
                + "examples/img_bipbop_adv_example_ts/master.m3u8"));
        items.add(new Item("测试资源", "Apple HLS 多码率示例",
                "Apple 官方公开播放器测试流，含多清晰度、音轨和字幕。", "内置资源",
                apple, new ArrayList<Episode>()));
        List<Stream> nasa = new ArrayList<>();
        nasa.add(new Stream("默认线路",
                "https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8"));
        items.add(new Item("公开直播", "NASA TV Public",
                "NASA 公开频道直播流，通过 Akamai CDN 分发。", "内置资源",
                nasa, new ArrayList<Episode>()));
        return items;
    }

    static LoadResult loadAll(List<String> sources) {
        List<Item> result = new ArrayList<>(defaultItems());
        int failures = 0;
        for (String source : sources) {
            try {
                result.addAll(load(source));
            } catch (IOException exception) {
                failures++;
            }
        }
        return new LoadResult(result, failures);
    }

    static List<Item> load(String source) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", "PENGVod/1.0");
        InputStream input = connection.getInputStream();
        try {
            return parse(read(input), source);
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

    private static List<Item> parse(String json, String sourceUrl) throws IOException {
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("version", 0) != 1) {
                throw new IOException("仅支持 version=1 的影视源配置");
            }
            JSONArray values = root.optJSONArray("items");
            if (values == null) {
                throw new IOException("影视源缺少 items 数组");
            }
            String sourceName = root.optString("name").trim();
            if (sourceName.length() == 0) {
                sourceName = new URL(sourceUrl).getHost();
            }
            List<Item> result = new ArrayList<>();
            for (int index = 0; index < values.length(); index++) {
                JSONObject value = values.optJSONObject(index);
                if (value == null) {
                    continue;
                }
                String category = value.optString("category", "其他").trim();
                String title = value.optString("title").trim();
                List<Stream> streams = streamsFromJson(value.optJSONArray("streams"));
                List<Episode> episodes = episodesFromJson(value.optJSONArray("episodes"), "");
                JSONArray seasons = value.optJSONArray("seasons");
                if (seasons != null) {
                    for (int seasonIndex = 0; seasonIndex < seasons.length(); seasonIndex++) {
                        JSONObject season = seasons.optJSONObject(seasonIndex);
                        if (season != null) {
                            episodes.addAll(episodesFromJson(season.optJSONArray("episodes"),
                                    season.optString("title", "第" + (seasonIndex + 1) + "季")));
                        }
                    }
                }
                if (!TextUtils.isEmpty(title) && (!streams.isEmpty() || !episodes.isEmpty())) {
                    result.add(new Item(category.length() == 0 ? "其他" : category, title,
                            value.optString("description").trim(), sourceName, streams, episodes));
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

    private static JSONArray streamsToJson(List<Stream> streams) throws JSONException {
        JSONArray values = new JSONArray();
        for (Stream stream : streams) {
            JSONObject value = new JSONObject();
            value.put("name", stream.name);
            value.put("url", stream.url);
            values.put(value);
        }
        return values;
    }

    private static List<Stream> streamsFromJson(JSONArray values) {
        List<Stream> streams = new ArrayList<>();
        if (values == null) {
            return streams;
        }
        for (int index = 0; index < values.length(); index++) {
            JSONObject value = values.optJSONObject(index);
            String url = value == null ? "" : value.optString("url").trim();
            if (VodPreferences.isHttpUrl(url)) {
                String name = value.optString("name", "线路 " + (streams.size() + 1)).trim();
                streams.add(new Stream(name.length() == 0 ? "线路 " + (streams.size() + 1) : name, url));
            }
        }
        return streams;
    }

    private static List<Episode> episodesFromJson(JSONArray values, String defaultSeason) {
        List<Episode> episodes = new ArrayList<>();
        if (values == null) {
            return episodes;
        }
        for (int index = 0; index < values.length(); index++) {
            JSONObject value = values.optJSONObject(index);
            if (value == null) {
                continue;
            }
            List<Stream> streams = streamsFromJson(value.optJSONArray("streams"));
            if (!streams.isEmpty()) {
                String title = value.optString("title", "第" + (index + 1) + "集").trim();
                String season = value.optString("season", defaultSeason).trim();
                episodes.add(new Episode(season, title.length() == 0 ? "第" + (index + 1) + "集" : title,
                        streams));
            }
        }
        return episodes;
    }
}
