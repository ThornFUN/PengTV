package com.peng.cast.receiver;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;
import android.view.View.MeasureSpec;

public final class AspectRatioSurfaceView extends SurfaceView {
    private float aspectRatio;

    public AspectRatioSurfaceView(Context context) {
        super(context);
    }

    public AspectRatioSurfaceView(Context context, AttributeSet attributes) {
        super(context, attributes);
    }

    public void setAspectRatio(int width, int height) {
        float newAspectRatio = width > 0 && height > 0 ? (float) width / height : 0;
        if (aspectRatio != newAspectRatio) {
            aspectRatio = newAspectRatio;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availableWidth = MeasureSpec.getSize(widthMeasureSpec);
        int availableHeight = MeasureSpec.getSize(heightMeasureSpec);
        if (aspectRatio <= 0 || availableWidth == 0 || availableHeight == 0) {
            setMeasuredDimension(availableWidth, availableHeight);
            return;
        }
        float availableAspectRatio = (float) availableWidth / availableHeight;
        if (availableAspectRatio > aspectRatio) {
            availableHeight = Math.round(availableWidth / aspectRatio);
        } else {
            availableWidth = Math.round(availableHeight * aspectRatio);
        }
        setMeasuredDimension(availableWidth, availableHeight);
    }
}
