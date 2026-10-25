package com.peng.video;

import android.content.Context;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DefaultChannels {
    private static final String ASSET_NAME = "default_channels.tsv";
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
                VideoMainActivity.Channel channel = channelsByName.get(displayName);
                if (channel == null) {
                    channel = new VideoMainActivity.Channel(displayName, "default-" + index++, url);
                    channelsByName.put(displayName, channel);
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
}
