package com.peng.video;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PlaybackQueue {
    private static List<VideoMainActivity.Channel> channels = Collections.emptyList();

    private PlaybackQueue() {
    }

    static synchronized void setChannels(List<VideoMainActivity.Channel> values) {
        channels = new ArrayList<>(values);
    }

    static synchronized VideoMainActivity.Channel channelAt(int index) {
        return index >= 0 && index < channels.size() ? channels.get(index) : null;
    }

    static synchronized int size() {
        return channels.size();
    }

    static synchronized List<VideoMainActivity.Channel> channels() {
        return new ArrayList<>(channels);
    }
}
