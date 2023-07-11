package com.chat.uikit.view;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chat.base.utils.AndroidUtilities;


public class PlayPauseDrawable extends Drawable {

    private final Paint paint;
    private int size;

    private boolean pause;
    private float progress;
    private long lastUpdateTime;

    private boolean firstTimeNotAnimated = false;
    private boolean first = true;

    public PlayPauseDrawable() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
    }

    public void setSize(int size) {
        this.size = size;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        long newUpdateTime = AnimationUtils.currentAnimationTimeMillis();
        long dt = newUpdateTime - lastUpdateTime;
        lastUpdateTime = newUpdateTime;
        if (dt > 18) {
            dt = 16;
        }
        if (pause && progress < 1f) {
            progress += dt / 300f;
            if (progress >= 1f) {
                progress = 1f;
            } else {
                invalidateSelf();
            }
        } else if (!pause && progress > 0f) {
            progress -= dt / 300f;
            if (progress <= 0f) {
                progress = 0f;
            } else {
                invalidateSelf();
            }
        }
        final Rect bounds = getBounds();
        canvas.save();
        canvas.translate(bounds.centerX() + AndroidUtilities.dp(1) * (1.0f - progress), bounds.centerY());
        final float ms = 500.0f * progress;
        final float rotation;
        if (ms < 100) {
            rotation = -5 * CubicBezierInterpolator.EASE_BOTH.getInterpolation(ms / 100.0f);
        } else if (ms < 484) {
            rotation = -5 + 95 * CubicBezierInterpolator.EASE_BOTH.getInterpolation((ms - 100) / 384);
        } else {
            rotation = 90;
        }
        canvas.scale(1.45f * size / AndroidUtilities.dp(28), 1.5f * size / AndroidUtilities.dp(28));
        canvas.rotate(rotation);
        getPathAnimator().draw(canvas, paint, ms);
        canvas.scale(1.0f, -1.0f);
        getPathAnimator().draw(canvas, paint, ms);
        canvas.restore();
    }

    public void setFirstTimeNotAnimated(boolean firstTimeNotAnimated) {
        this.firstTimeNotAnimated = firstTimeNotAnimated;
    }

    public void setPause(boolean pause) {
        if (firstTimeNotAnimated && first) {
            setPause(pause, false);
            first = false;
        } else {
            setPause(pause, true);
        }
    }

    public void setPause(boolean pause, boolean animated) {
        if (this.pause != pause) {
            this.pause = pause;
            if (!animated) {
                progress = pause ? 1f : 0f;
            }
            this.lastUpdateTime = AnimationUtils.currentAnimationTimeMillis();
            invalidateSelf();
        }
    }

    @Override
    public void setAlpha(int i) {
        paint.setAlpha(i);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }


    PathAnimator getPathAnimator() {
        PathAnimator pathAnimator = new PathAnimator(0.293f, -26f, -28f, 1.0f);
        pathAnimator.addSvgKeyFrame(
                "M 34.141 16.042 C 37.384 17.921 40.886 20.001 44.211 21.965 C 46.139 23.104 49.285 24.729 49.586 25.917 C 50.289 28.687 48.484 30 46.274 30 L 6 30.021 C 3.79 30.021 2.075 30.023 2 26.021 L 2.009 3.417 C 2.009 0.417 5.326 -0.58 7.068 0.417 C 10.545 2.406 25.024 10.761 34.141 16.042 Z",
                166f
        );
        pathAnimator.addSvgKeyFrame(
                "M 37.843 17.769 C 41.143 19.508 44.131 21.164 47.429 23.117 C 48.542 23.775 49.623 24.561 49.761 25.993 C 50.074 28.708 48.557 30 46.347 30 L 6 30.012 C 3.79 30.012 2 28.222 2 26.012 L 2.009 4.609 C 2.009 1.626 5.276 0.664 7.074 1.541 C 10.608 3.309 28.488 12.842 37.843 17.769 Z",
                200f
        );
        pathAnimator.addSvgKeyFrame(
                "M 40.644 18.756 C 43.986 20.389 49.867 23.108 49.884 25.534 C 49.897 27.154 49.88 24.441 49.894 26.059 C 49.911 28.733 48.6 30 46.39 30 L 6 30.013 C 3.79 30.013 2 28.223 2 26.013 L 2.008 5.52 C 2.008 2.55 5.237 1.614 7.079 2.401 C 10.656 4 31.106 14.097 40.644 18.756 Z",
                217f
        );
        pathAnimator.addSvgKeyFrame(
                "M 43.782 19.218 C 47.117 20.675 50.075 21.538 50.041 24.796 C 50.022 26.606 50.038 24.309 50.039 26.104 C 50.038 28.736 48.663 30 46.453 30 L 6 29.986 C 3.79 29.986 2 28.196 2 25.986 L 2.008 6.491 C 2.008 3.535 5.196 2.627 7.085 3.316 C 10.708 4.731 33.992 14.944 43.782 19.218 Z",
                234f
        );
        pathAnimator.addSvgKeyFrame(
                "M 47.421 16.941 C 50.544 18.191 50.783 19.91 50.769 22.706 C 50.761 24.484 50.76 23.953 50.79 26.073 C 50.814 27.835 49.334 30 47.124 30 L 5 30.01 C 2.79 30.01 1 28.22 1 26.01 L 1.001 10.823 C 1.001 8.218 3.532 6.895 5.572 7.26 C 7.493 8.01 47.421 16.941 47.421 16.941 Z",
                267f
        );
        pathAnimator.addSvgKeyFrame(
                "M 47.641 17.125 C 50.641 18.207 51.09 19.935 51.078 22.653 C 51.07 24.191 51.062 21.23 51.088 23.063 C 51.109 24.886 49.587 27 47.377 27 L 5 27.009 C 2.79 27.009 1 25.219 1 23.009 L 0.983 11.459 C 0.983 8.908 3.414 7.522 5.476 7.838 C 7.138 8.486 47.641 17.125 47.641 17.125 Z",
                300f
        );
        pathAnimator.addSvgKeyFrame(
                "M 48 7 C 50.21 7 52 8.79 52 11 C 52 19 52 19 52 19 C 52 21.21 50.21 23 48 23 L 4 23 C 1.79 23 0 21.21 0 19 L 0 11 C 0 8.79 1.79 7 4 7 C 48 7 48 7 48 7 Z",
                383f
        );
        return pathAnimator;
    }
}
