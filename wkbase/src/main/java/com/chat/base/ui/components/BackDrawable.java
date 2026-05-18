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

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import com.chat.base.utils.AndroidUtilities;

public class BackDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean alwaysClose;
    private int color = 0xffffffff;
    private float currentRotation;
    private float targetRotation;
    private long lastFrameTime;
    private int arrowRotation;
    private float animationDuration = 300f;

    public BackDrawable(boolean close) {
        super();
        paint.setStrokeWidth(AndroidUtilities.dp(2));
        paint.setStrokeCap(Paint.Cap.ROUND);
        alwaysClose = close;
    }

    public void setColor(int value) {
        color = value;
        invalidateSelf();
    }

    public void setRotatedColor(int value) {
        // kept for API compat, simplified impl uses single color
        invalidateSelf();
    }

    public void setArrowRotation(int angle) {
        arrowRotation = angle;
        invalidateSelf();
    }

    public void setRotation(float rotation, boolean animated) {
        if (animated) {
            targetRotation = rotation;
            lastFrameTime = System.currentTimeMillis();
        } else {
            targetRotation = rotation;
            currentRotation = rotation;
        }
        invalidateSelf();
    }

    public void setAnimationTime(float value) {
        animationDuration = value;
    }

    public void setRotated(boolean value) {
        // kept for API compat
    }

    @Override
    public void draw(Canvas canvas) {
        if (currentRotation != targetRotation) {
            long now = System.currentTimeMillis();
            if (lastFrameTime != 0) {
                float dt = (now - lastFrameTime) / animationDuration;
                if (currentRotation < targetRotation) {
                    currentRotation = Math.min(currentRotation + dt, targetRotation);
                } else {
                    currentRotation = Math.max(currentRotation - dt, targetRotation);
                }
            }
            lastFrameTime = now;
            invalidateSelf();
        }

        paint.setColor(color);
        int cx = getIntrinsicWidth() / 2;
        int cy = getIntrinsicHeight() / 2;

        canvas.save();
        canvas.translate(cx, cy);
        if (arrowRotation != 0) {
            canvas.rotate(arrowRotation);
        }

        float rotation = alwaysClose ? 1f : currentRotation;
        float angle = 135 * rotation;
        canvas.rotate(angle);

        float halfLen = AndroidUtilities.dp(7);
        float armLen = AndroidUtilities.dp(7);

        // horizontal bar
        canvas.drawLine(-halfLen, 0, halfLen + AndroidUtilities.dp(1), 0, paint);
        // top arm
        canvas.drawLine(0, 0, 0, -armLen, paint);
        // bottom arm
        canvas.drawLine(0, 0, 0, armLen, paint);

        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(24);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(24);
    }
}
