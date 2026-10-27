package com.github.tvbox.osc.player;

import android.content.Context;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Player;

public class EXOmPlayer extends IjkmPlayer {
    public EXOmPlayer(Context context) {
        super(context, null);
    }

    public void selectExoTrack(@Nullable TrackInfoBean videoTrackBean) {
        if (videoTrackBean != null) setTrack(videoTrackBean.trackId);
    }

    public void setOnTimedTextListener(Player.Listener listener) {
        // API 19 routes this legacy player choice through IJK, whose timed-text callback is registered separately.
    }
}