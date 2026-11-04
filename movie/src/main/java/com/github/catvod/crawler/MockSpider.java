package com.github.catvod.crawler;

import android.content.Context;
import android.net.Uri;

import java.util.HashMap;
import java.util.List;

public final class MockSpider extends Spider {
    private static final String VOD_ID = "peng-mock-video";
    private static final String DEFAULT_MEDIA_URL =
            "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8";

    private String mediaUrl = DEFAULT_MEDIA_URL;

    @Override
    public void init(Context context, String extend) {
        if (isHttpUrl(extend)) {
            mediaUrl = extend;
        }
    }

    @Override
    public String homeContent(boolean filter) {
        return "{\"class\":[{\"type_id\":\"mock\",\"type_name\":\"本地测试\"}],"
                + "\"list\":[" + vod() + "]}";
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter,
                                  HashMap<String, String> extend) {
        return "{\"page\":1,\"pagecount\":1,\"limit\":1,\"total\":1,"
                + "\"list\":[" + vod() + "]}";
    }

    @Override
    public String detailContent(List<String> ids) {
        return "{\"list\":[{\"vod_id\":\"" + VOD_ID
                + "\",\"vod_name\":\"PENG 本地 Mock 测试视频\","
                + "\"vod_content\":\"用于验证 Android 4.4 播放器链路的公开测试流。\","
                + "\"vod_play_from\":\"Mock\","
                + "\"vod_play_url\":\"播放$" + mediaUrl + "\"}]}";
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return "{\"parse\":0,\"url\":\"" + mediaUrl + "\"}";
    }

    private String vod() {
        return "{\"vod_id\":\"" + VOD_ID
                + "\",\"vod_name\":\"PENG 本地 Mock 测试视频\","
                + "\"vod_pic\":\"\",\"vod_remarks\":\"公开测试流\"}";
    }

    private static boolean isHttpUrl(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        Uri uri = Uri.parse(value);
        return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                && uri.getHost() != null;
    }
}
