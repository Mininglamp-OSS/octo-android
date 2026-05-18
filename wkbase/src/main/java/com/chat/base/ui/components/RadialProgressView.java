/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.base.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Keep;
import androidx.core.content.ContextCompat;

import com.chat.base.R;
import com.chat.base.utils.AndroidUtilities;

public class RadialProgressView extends View {

    private long lastUpdateTime;
    private float radOffset;
    private float currentCircleLength;
    private boolean risingCircleLength;
    private float currentProgressTime;
    private final RectF arcRect = new RectF();
    private boolean useSelfAlpha;
    private float drawingCircleLength;

    private int progressColor;
    private final DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();
    private final AccelerateInterpolator accelerateInterpolator = new AccelerateInterpolator();
    private final Paint progressPaint;
    private int size;

    private float currentProgress;
    private float progressAnimationStart;
    private int progressTime;
    private float animatedProgress;
    private boolean toCircle;
    private float toCircleProgress;
    private boolean noProgress = true;

    private static final float ROTATION_TIME = 2000f;
    private static final float RISING_TIME = 500f;

    public RadialProgressView(Context context) {
        this(context, null);
    }

    public RadialProgressView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RadialProgressView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        size = AndroidUtilities.dp(40);
        progressColor = ContextCompat.getColor(context, R.color.colorAccent);
        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeWidth(AndroidUtilities.dp(3));
        progressPaint.setColor(progressColor);
    }

    public void setUseSelfAlpha(boolean value) {
        useSelfAlpha = value;
    }

    @Keep
    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        if (useSelfAlpha) {
            int a = (int) (alpha * 255);
            Drawable bg = getBackground();
            if (bg != null) bg.setAlpha(a);
            progressPaint.setAlpha(a);
        }
    }

    public void setNoProgress(boolean value) {
        noProgress = value;
    }

    public void setProgress(float value) {
        currentProgress = value;
        if (animatedProgress > value) animatedProgress = value;
        progressAnimationStart = animatedProgress;
        progressTime = 0;
    }

    public void setSize(int value) {
        size = value;
        invalidate();
    }

    public void setStrokeWidth(float value) {
        progressPaint.setStrokeWidth(AndroidUtilities.dp(value));
    }

    public void setProgressColor(int color) {
        progressColor = color;
        progressPaint.setColor(color);
    }

    public void toCircle(boolean toCircle, boolean animated) {
        this.toCircle = toCircle;
        if (!animated) {
            toCircleProgress = toCircle ? 1f : 0f;
        }
    }

    public void sync(RadialProgressView from) {
        lastUpdateTime = from.lastUpdateTime;
        radOffset = from.radOffset;
        toCircle = from.toCircle;
        toCircleProgress = from.toCircleProgress;
        noProgress = from.noProgress;
        currentCircleLength = from.currentCircleLength;
        drawingCircleLength = from.drawingCircleLength;
        currentProgressTime = from.currentProgressTime;
        currentProgress = from.currentProgress;
        progressTime = from.progressTime;
        animatedProgress = from.animatedProgress;
        risingCircleLength = from.risingCircleLength;
        progressAnimationStart = from.progressAnimationStart;
        stepAnimation(17 * 5);
    }

    public boolean isCircle() {
        return Math.abs(drawingCircleLength) >= 360;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int x = (getMeasuredWidth() - size) / 2;
        int y = (getMeasuredHeight() - size) / 2;
        arcRect.set(x, y, x + size, y + size);
        drawingCircleLength = currentCircleLength;
        canvas.drawArc(arcRect, radOffset, drawingCircleLength, false, progressPaint);
        stepAnimation();
    }

    public void draw(Canvas canvas, float cx, float cy) {
        arcRect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
        drawingCircleLength = currentCircleLength;
        canvas.drawArc(arcRect, radOffset, drawingCircleLength, false, progressPaint);
        stepAnimation();
    }

    private void stepAnimation() {
        long now = System.currentTimeMillis();
        long dt = now - lastUpdateTime;
        if (dt > 17) dt = 17;
        lastUpdateTime = now;
        stepAnimation(dt);
    }

    private void stepAnimation(long dt) {
        radOffset += 360f * dt / ROTATION_TIME;
        radOffset %= 360f;

        if (toCircle && toCircleProgress < 1f) {
            toCircleProgress = Math.min(1f, toCircleProgress + 16f / 220f);
        } else if (!toCircle && toCircleProgress > 0f) {
            toCircleProgress = Math.max(0f, toCircleProgress - 16f / 400f);
        }

        if (noProgress) {
            if (toCircleProgress == 0) {
                currentProgressTime += dt;
                if (currentProgressTime >= RISING_TIME) currentProgressTime = RISING_TIME;

                if (risingCircleLength) {
                    currentCircleLength = 4 + 266 * accelerateInterpolator.getInterpolation(currentProgressTime / RISING_TIME);
                } else {
                    currentCircleLength = 4 - 270 * (1f - decelerateInterpolator.getInterpolation(currentProgressTime / RISING_TIME));
                }

                if (currentProgressTime == RISING_TIME) {
                    if (risingCircleLength) {
                        radOffset += 270;
                        currentCircleLength = -266;
                    }
                    risingCircleLength = !risingCircleLength;
                    currentProgressTime = 0;
                }
            } else {
                if (risingCircleLength) {
                    float old = currentCircleLength;
                    currentCircleLength = 4 + 266 * accelerateInterpolator.getInterpolation(currentProgressTime / RISING_TIME);
                    currentCircleLength += 360 * toCircleProgress;
                    if (old - currentCircleLength > 0) radOffset += old - currentCircleLength;
                } else {
                    float old = currentCircleLength;
                    currentCircleLength = 4 - 270 * (1f - decelerateInterpolator.getInterpolation(currentProgressTime / RISING_TIME));
                    currentCircleLength -= 364 * toCircleProgress;
                    if (old - currentCircleLength > 0) radOffset += old - currentCircleLength;
                }
            }
        } else {
            float diff = currentProgress - progressAnimationStart;
            if (diff > 0) {
                progressTime += dt;
                if (progressTime >= 200) {
                    animatedProgress = progressAnimationStart = currentProgress;
                    progressTime = 0;
                } else {
                    animatedProgress = progressAnimationStart + diff * decelerateInterpolator.getInterpolation(progressTime / 200f);
                }
            }
            currentCircleLength = Math.max(4, 360 * animatedProgress);
        }
        invalidate();
    }
}
