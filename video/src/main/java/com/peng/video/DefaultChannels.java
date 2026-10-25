package com.peng.video;

import android.content.Context;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class DefaultChannels {
    private static final String ASSET_NAME = "default_channels.tsv";
    private static List<VideoMainActivity.Channel> cachedChannels;

    private DefaultChannels() {
    }

    static synchronized List<VideoMainActivity.Channel> create(Context context) throws IOException {
        if (cachedChannels != null) {
            return new ArrayList<>(cachedChannels);
        }

        List<VideoMainActivity.Channel> channels = new ArrayList<>();
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
                channels.add(new VideoMainActivity.Channel(
                        category + " · " + name,
                        "default-" + index++,
                        url));
            }
        } finally {
            reader.close();
        }
        if (channels.isEmpty()) {
            throw new IOException("default channel list is empty");
        }
        cachedChannels = Collections.unmodifiableList(channels);
        return new ArrayList<>(cachedChannels);
    }
}
