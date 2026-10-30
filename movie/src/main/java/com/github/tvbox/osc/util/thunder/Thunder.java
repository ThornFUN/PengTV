package com.github.tvbox.osc.util.thunder;

import android.content.Context;

public final class Thunder {

    private Thunder() {
    }

    public interface ThunderCallback {
        void status(int code, String info);

        void list(String playList);

        void play(String url);
    }

    public static void stop() {
    }

    public static void parse(Context context, String url, ThunderCallback callback) {
        if (isSupportUrl(url)) {
            callback.status(-1, "PENG影视不支持磁力或迅雷下载链接");
        }
    }

    public static boolean play(String url, ThunderCallback callback) {
        if (!isSupportUrl(url)) {
            return false;
        }
        callback.status(-1, "PENG影视不支持磁力或迅雷下载链接");
        return true;
    }

    public static boolean isSupportUrl(String url) {
        if (url == null) {
            return false;
        }
        String normalized = url.toLowerCase();
        return normalized.startsWith("magnet:") || normalized.startsWith("thunder");
    }
}
