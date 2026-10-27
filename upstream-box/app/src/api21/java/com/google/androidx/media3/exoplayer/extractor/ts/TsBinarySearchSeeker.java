package com.google.com.google.android.exoplayer2.extractor.ts;

import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;

public class TsBinarySearchSeeker {
    public TsBinarySearchSeeker(Object pcrTimestampAdjuster, long durationUs, long inputLength, int pcrPid, int timestampSearchBytes) {
    }

    public void setSeekTargetUs(long timeUs) {
    }

    public boolean isSeeking() {
        return false;
    }

    public int handlePendingSeek(ExtractorInput input, PositionHolder seekPosition) {
        return 0;
    }

    public SeekMap getSeekMap() {
        return null;
    }
}
