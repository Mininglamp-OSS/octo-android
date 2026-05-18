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

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.Keep;
import androidx.core.content.ContextCompat;

import com.chat.base.R;
import com.chat.base.utils.AndroidUtilities;

public class CheckBoxSquare extends View {

    private float progress;
    private ObjectAnimator checkAnimator;
    private boolean isChecked;
    private boolean isDisabled;
    private boolean attachedToWindow;

    private int uncheckedColor;
    private int checkedColor;
    private int checkMarkColor;

    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF boxRect = new RectF();
    private final Path checkPath = new Path();

    public CheckBoxSquare(Context context, boolean alert) {
        super(context);
        uncheckedColor = ContextCompat.getColor(context, R.color.popupTextColor);
        checkedColor = ContextCompat.getColor(context, R.color.colorAccent);
        checkMarkColor = ContextCompat.getColor(context, R.color.screen_bg);

        checkPaint.setStyle(Paint.Style.STROKE);
        checkPaint.setStrokeWidth(AndroidUtilities.dp(2));
        checkPaint.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setColors(int unchecked, int checked, int check) {
        uncheckedColor = unchecked;
        checkedColor = checked;
        checkMarkColor = check;
        invalidate();
    }

    @Keep
    public void setProgress(float value) {
        if (progress == value) return;
        progress = value;
        invalidate();
    }

    @Keep
    public float getProgress() {
        return progress;
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checked == isChecked) return;
        isChecked = checked;
        if (attachedToWindow && animated) {
            if (checkAnimator != null) checkAnimator.cancel();
            checkAnimator = ObjectAnimator.ofFloat(this, "progress", checked ? 1f : 0f);
            checkAnimator.setDuration(250);
            checkAnimator.start();
        } else {
            if (checkAnimator != null) checkAnimator.cancel();
            progress = checked ? 1f : 0f;
            invalidate();
        }
    }

    public void setDisabled(boolean disabled) {
        isDisabled = disabled;
        invalidate();
    }

    public boolean isChecked() {
        return isChecked;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getVisibility() != VISIBLE) return;

        float size = Math.min(getWidth(), getHeight());
        float inset = AndroidUtilities.dp(1);
        boxRect.set(inset, inset, size - inset, size - inset);
        float cornerRadius = AndroidUtilities.dp(2);

        int blendedColor = blendColors(uncheckedColor, checkedColor, progress);
        if (isDisabled) {
            blendedColor = ContextCompat.getColor(getContext(), R.color.popupTextColor);
        }

        if (progress < 1f) {
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setStrokeWidth(AndroidUtilities.dp(2));
            boxPaint.setColor(blendedColor);
            canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, boxPaint);
        }

        if (progress > 0f) {
            boxPaint.setStyle(Paint.Style.FILL);
            boxPaint.setColor(blendedColor);
            float scale = progress;
            float cx = size / 2f, cy = size / 2f;
            canvas.save();
            canvas.scale(scale, scale, cx, cy);
            canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, boxPaint);
            canvas.restore();

            checkPaint.setColor(checkMarkColor);
            checkPaint.setAlpha((int) (255 * progress));
            float checkScale = 0.65f;
            float offsetX = size * (1f - checkScale) / 2f;
            float offsetY = size * (1f - checkScale) / 2f;
            checkPath.reset();
            checkPath.moveTo(offsetX + size * checkScale * 0.2f, offsetY + size * checkScale * 0.5f);
            checkPath.lineTo(offsetX + size * checkScale * 0.4f, offsetY + size * checkScale * 0.7f);
            checkPath.lineTo(offsetX + size * checkScale * 0.8f, offsetY + size * checkScale * 0.3f);
            canvas.drawPath(checkPath, checkPaint);
        }
    }

    private static int blendColors(int from, int to, float ratio) {
        float inv = 1f - ratio;
        int r = (int) (Color.red(from) * inv + Color.red(to) * ratio);
        int g = (int) (Color.green(from) * inv + Color.green(to) * ratio);
        int b = (int) (Color.blue(from) * inv + Color.blue(to) * ratio);
        return Color.rgb(r, g, b);
    }
}
