package com.google.com.google.android.exoplayer2.extractor.ts;

import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.PositionHolder;

public class TsDurationReader {
    public TsDurationReader(int timestampSearchBytes) {
    }

    public int readDuration(ExtractorInput input, PositionHolder seekPosition, int pcrPid) {
        return pcrPid;
    }

    public boolean isDurationReadFinished() {
        return false;
    }

    public long getDurationUs() {
        return 0;
    }

    public Object getPcrTimestampAdjuster() {
        return null;
    }
}
