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
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.chat.base.utils.AndroidUtilities;

public class LineProgressView extends View {

    private long lastUpdateTime;
    private float currentProgress;
    private float animationProgressStart;
    private long currentProgressTime;
    private float animatedProgressValue;
    private float animatedAlphaValue = 1.0f;

    private int backColor;
    private int progressColor;

    private final DecelerateInterpolator interpolator = new DecelerateInterpolator();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public LineProgressView(Context context) {
        super(context);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(AndroidUtilities.dp(2));
    }

    public void setProgressColor(int color) {
        progressColor = color;
    }

    public void setBackColor(int color) {
        backColor = color;
    }

    public void setProgress(float value, boolean animated) {
        if (!animated) {
            animatedProgressValue = value;
            animationProgressStart = value;
        } else {
            animationProgressStart = animatedProgressValue;
        }
        if (value != 1) {
            animatedAlphaValue = 1;
        }
        currentProgress = value;
        currentProgressTime = 0;
        lastUpdateTime = System.currentTimeMillis();
        invalidate();
    }

    public float getCurrentProgress() {
        return currentProgress;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (backColor != 0 && animatedProgressValue != 1) {
            paint.setColor(backColor);
            paint.setAlpha((int) (255 * animatedAlphaValue));
            rect.set(0, 0, getWidth(), getHeight());
            float radius = getHeight() / 2f;
            canvas.drawRoundRect(rect, radius, radius, paint);
        }

        paint.setColor(progressColor);
        paint.setAlpha((int) (255 * animatedAlphaValue));
        rect.set(0, 0, getWidth() * animatedProgressValue, getHeight());
        float radius = getHeight() / 2f;
        canvas.drawRoundRect(rect, radius, radius, paint);

        updateAnimation();
    }

    private void updateAnimation() {
        long now = System.currentTimeMillis();
        long dt = now - lastUpdateTime;
        lastUpdateTime = now;

        if (animatedProgressValue != currentProgress) {
            float diff = currentProgress - animationProgressStart;
            if (diff > 0) {
                currentProgressTime += dt;
                if (currentProgressTime >= 300) {
                    animatedProgressValue = currentProgress;
                    animationProgressStart = currentProgress;
                    currentProgressTime = 0;
                } else {
                    animatedProgressValue = animationProgressStart +
                            diff * interpolator.getInterpolation(currentProgressTime / 300f);
                }
            }
            invalidate();
        }

        if (animatedProgressValue >= 1f && animatedAlphaValue != 0) {
            animatedAlphaValue -= dt / 200f;
            if (animatedAlphaValue <= 0) {
                animatedAlphaValue = 0f;
            }
            invalidate();
        }
    }
}
