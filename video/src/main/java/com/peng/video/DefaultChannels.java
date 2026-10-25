package com.peng.video;

import java.util.ArrayList;
import java.util.List;

final class DefaultChannels {
    private DefaultChannels() {
    }

    static List<VideoMainActivity.Channel> create() {
        List<VideoMainActivity.Channel> channels = new ArrayList<>();
        channels.add(new VideoMainActivity.Channel(
                "NASA TV Public",
                "nasa-tv-public",
                "https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8"));
        channels.add(new VideoMainActivity.Channel(
                "NASA TV Media",
                "nasa-tv-media",
                "https://ntv2.akamaized.net/hls/live/2013923/NASA-NTV2-HLS/master.m3u8"));
        return channels;
    }
}
