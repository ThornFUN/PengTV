package com.peng.video;

import android.content.Context;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DefaultChannels {
    private static final String ASSET_NAME = "default_channels.tsv";
    private static final String[] QUALITY_SUFFIXES = {
            "高清", "超清", "蓝光", "FHD", "UHD", "4K", "HD"
    };
    private static final String[] CCTV_NAME_SUFFIXES = {
            "综合", "财经", "综艺", "中文国际", "体育", "电影", "国防军事", "电视剧",
            "纪录", "科教", "戏曲", "社会与法", "新闻", "少儿", "音乐", "奥林匹克", "农业农村"
    };
    private static List<VideoMainActivity.Channel> cachedChannels;

    private DefaultChannels() {
    }

    static synchronized List<VideoMainActivity.Channel> create(Context context) throws IOException {
        if (cachedChannels != null) {
            return new ArrayList<>(cachedChannels);
        }

        Map<String, VideoMainActivity.Channel> channelsByName = new LinkedHashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(ASSET_NAME), "UTF-8"));
        try {
            String line;
            int index = 0;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split("\\t", 3);
                if (values.length != 3) {
                    continue;
                }
                String category = values[0].trim();
                String name = values[1].trim();
                String url = values[2].trim();
                if (category.length() == 0 || name.length() == 0 || !VideoPreferences.isHttpUrl(url)) {
                    continue;
                }
                String displayName = category + " · " + name;
                String groupName = category + " · " + canonicalName(name);
                VideoMainActivity.Channel channel = channelsByName.get(groupName);
                if (channel == null) {
                    channel = new VideoMainActivity.Channel(displayName, "default-" + index++, url);
                    channelsByName.put(groupName, channel);
                } else {
                    channel.addFallbackUrl(url);
                }
            }
        } finally {
            reader.close();
        }
        List<VideoMainActivity.Channel> channels = new ArrayList<>(channelsByName.values());
        if (channels.isEmpty()) {
            throw new IOException("default channel list is empty");
        }
        cachedChannels = Collections.unmodifiableList(channels);
        return new ArrayList<>(cachedChannels);
    }

    private static String canonicalName(String name) {
        String canonical = name.replace(" ", "").replace("　", "").toUpperCase(Locale.US);
        canonical = canonical.replace("-", "");
        if (!"CCTV4K".equals(canonical)) {
            canonical = removeQualitySuffix(canonical);
        }
        if (canonical.endsWith("频道")) {
            canonical = canonical.substring(0, canonical.length() - 2);
        }
        return canonicalCctvName(canonical);
    }

    private static String removeQualitySuffix(String name) {
        for (String suffix : QUALITY_SUFFIXES) {
            if (name.endsWith(suffix) && name.length() > suffix.length()) {
                return name.substring(0, name.length() - suffix.length());
            }
        }
        return name;
    }

    private static String canonicalCctvName(String name) {
        if (!name.startsWith("CCTV")) {
            return name;
        }
        int end = 4;
        while (end < name.length() && Character.isDigit(name.charAt(end))) {
            end++;
        }
        if (end == 4) {
            return name;
        }
        if (end < name.length() && name.charAt(end) == '+') {
            end++;
        }
        String suffix = name.substring(end);
        for (String knownSuffix : CCTV_NAME_SUFFIXES) {
            if (knownSuffix.equals(suffix)) {
                return name.substring(0, end);
            }
        }
        return name;
    }
}
