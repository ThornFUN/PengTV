package com.peng.video;

import android.content.Context;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

final class LivePlaybackFactory {
    private static final int MIN_BUFFER_MS = 20_000;
    private static final int MAX_BUFFER_MS = 30_000;
    private static final int PLAYBACK_BUFFER_MS = 6_000;
    private static final int REBUFFER_PLAYBACK_BUFFER_MS = 8_000;
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private LivePlaybackFactory() {
    }

    static ExoPlayer create(Context context) {
        int playbackQuality = VideoPreferences.playbackQuality(context);
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(context);
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setMaxVideoSize(maxVideoWidth(playbackQuality), maxVideoHeight(playbackQuality))
                .setMaxVideoBitrate(maxVideoBitrate(playbackQuality)));
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(MIN_BUFFER_MS, MAX_BUFFER_MS, PLAYBACK_BUFFER_MS,
                        REBUFFER_PLAYBACK_BUFFER_MS)
                .build();
        DefaultHttpDataSource.Factory httpDataSource = new DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(READ_TIMEOUT_MS)
                .setAllowCrossProtocolRedirects(true);
        DefaultDataSource.Factory dataSource = new DefaultDataSource.Factory(context, httpDataSource);
        return new ExoPlayer.Builder(context)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSource))
                .build();
    }

    private static int maxVideoWidth(int playbackQuality) {
        return playbackQuality == VideoPreferences.QUALITY_SMOOTH ? 854
                : playbackQuality == VideoPreferences.QUALITY_BALANCED ? 1280 : 1920;
    }

    private static int maxVideoHeight(int playbackQuality) {
        return playbackQuality == VideoPreferences.QUALITY_SMOOTH ? 480
                : playbackQuality == VideoPreferences.QUALITY_BALANCED ? 720 : 1080;
    }

    private static int maxVideoBitrate(int playbackQuality) {
        return playbackQuality == VideoPreferences.QUALITY_SMOOTH ? 1_500_000
                : playbackQuality == VideoPreferences.QUALITY_BALANCED ? 3_000_000 : 6_000_000;
    }
}
