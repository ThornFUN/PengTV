package com.peng.video;

import java.util.ArrayList;
import java.util.List;

final class DefaultChannels {
    private DefaultChannels() {
    }

    static List<VideoMainActivity.Channel> create() {
        List<VideoMainActivity.Channel> channels = new ArrayList<>();
        channels.add(new VideoMainActivity.Channel(
                "楚雄新闻综合",
                "chuxiong-news",
                "http://hwapi.yntv.net/ew265l/848y54.m3u8"));
        channels.add(new VideoMainActivity.Channel(
                "赤峰新闻综合",
                "chifeng-news",
                "http://play1-qk.nmtv.cn/live/1735546697341033.m3u8"));
        return channels;
    }
}
