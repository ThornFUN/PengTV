package com.peng.video;

import java.util.ArrayList;
import java.util.List;

final class DefaultChannels {
    private DefaultChannels() {
    }

    static List<VideoMainActivity.Channel> create() {
        List<VideoMainActivity.Channel> channels = new ArrayList<>();
        channels.add(new VideoMainActivity.Channel(
                "APA TV",
                "apa-tv",
                "http://stream.apa.tv/apastream/index.m3u8"));
        return channels;
    }
}
