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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.Keep;

import com.chat.base.utils.AndroidUtilities;

public class CheckBox extends View {

    private Drawable checkDrawable;
    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkMarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path checkPath = new Path();

    private boolean drawBackground;
    private boolean hasBorder;

    private float progress;
    private ObjectAnimator checkAnimator;
    private boolean isCheckAnimation = true;

    private boolean attachedToWindow;
    private boolean isChecked;

    private int size = 22;
    private int checkOffset;
    private int color;
    private String checkedText;

    public CheckBox(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CheckBox(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public CheckBox(Context context, int resId) {
        super(context);
        init();
    }

    private void init() {
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(AndroidUtilities.dp(1));
        borderPaint.setColor(0xffffffff);

        checkMarkPaint.setStyle(Paint.Style.STROKE);
        checkMarkPaint.setStrokeWidth(AndroidUtilities.dp(2));
        checkMarkPaint.setStrokeCap(Paint.Cap.ROUND);
        checkMarkPaint.setStrokeJoin(Paint.Join.ROUND);
        checkMarkPaint.setColor(0xffffffff);

        textPaint.setTextSize(AndroidUtilities.dp(18));
    }

    public void setBorderColor(int color) {
        borderPaint.setColor(color);
    }

    public void setResId(Context context, int resId) {
        checkDrawable = context.getResources().getDrawable(resId).mutate();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
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

    public void setDrawBackground(boolean value) { drawBackground = value; }
    public void setHasBorder(boolean value) { hasBorder = value; }
    public void setCheckOffset(int value) { checkOffset = value; }

    public void setSize(int size) {
        this.size = size;
        if (size == 40) {
            textPaint.setTextSize(AndroidUtilities.dp(24));
        }
    }

    public void setStrokeWidth(int value) {
        borderPaint.setStrokeWidth(value);
    }

    public void setColor(int backgroundColor, int checkColor) {
        color = backgroundColor;
        if (checkDrawable != null) {
            checkDrawable.setColorFilter(new PorterDuffColorFilter(checkColor, PorterDuff.Mode.MULTIPLY));
        }
        checkMarkPaint.setColor(checkColor);
        textPaint.setColor(checkColor);
        invalidate();
    }

    public void setBackgroundColor(int backgroundColor) {
        color = backgroundColor;
        invalidate();
    }

    public void setCheckColor(int checkColor) {
        if (checkDrawable != null) {
            checkDrawable.setColorFilter(new PorterDuffColorFilter(checkColor, PorterDuff.Mode.MULTIPLY));
        }
        checkMarkPaint.setColor(checkColor);
        textPaint.setColor(checkColor);
        invalidate();
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

    public void setChecked(boolean checked, boolean animated) {
        setChecked(-1, checked, animated);
    }

    public void setNum(int num) {
        if (num >= 0) {
            checkedText = String.valueOf(num + 1);
        } else if (checkAnimator == null) {
            checkedText = null;
        }
        invalidate();
    }

    public void setChecked(int num, boolean checked, boolean animated) {
        if (num >= 0) {
            checkedText = String.valueOf(num + 1);
            invalidate();
        }
        if (checked == isChecked) return;
        isChecked = checked;

        if (attachedToWindow && animated) {
            isCheckAnimation = checked;
            if (checkAnimator != null) checkAnimator.cancel();
            checkAnimator = ObjectAnimator.ofFloat(this, "progress", checked ? 1f : 0f);
            checkAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    checkAnimator = null;
                    if (!isChecked) checkedText = null;
                }
            });
            checkAnimator.setDuration(300);
            checkAnimator.start();
        } else {
            if (checkAnimator != null) checkAnimator.cancel();
            checkAnimator = null;
            progress = checked ? 1f : 0f;
            if (!checked) checkedText = null;
            invalidate();
        }
    }

    public boolean isChecked() {
        return isChecked;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getVisibility() != VISIBLE) return;
        if (!drawBackground && progress == 0) return;

        float w = getMeasuredWidth();
        float h = getMeasuredHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float rad = w / 2f;

        float scaleProgress = isCheckAnimation ? progress : (1f - progress);
        float bounce = 0.2f;
        if (scaleProgress < bounce) {
            rad -= AndroidUtilities.dp(2) * scaleProgress / bounce;
        } else if (scaleProgress < bounce * 2) {
            rad -= AndroidUtilities.dp(2) - AndroidUtilities.dp(2) * (scaleProgress - bounce) / bounce;
        }

        if (drawBackground) {
            canvas.drawCircle(cx, cy, rad - AndroidUtilities.dp(1), borderPaint);
        }

        if (hasBorder) {
            rad -= AndroidUtilities.dp(2);
        }

        // filled circle grows with roundProgress
        float roundProgress = progress >= 0.5f ? 1f : progress / 0.5f;
        circlePaint.setColor(color);
        canvas.drawCircle(cx, cy, rad * roundProgress, circlePaint);

        // check mark or number appears in second half
        float checkProgress = progress < 0.5f ? 0f : (progress - 0.5f) / 0.5f;
        if (checkProgress > 0) {
            canvas.save();
            canvas.clipRect(0, 0, w, h);
            int alpha = (int) (255 * checkProgress);

            if (checkedText != null) {
                textPaint.setAlpha(alpha);
                float textWidth = textPaint.measureText(checkedText);
                canvas.drawText(checkedText, (w - textWidth) / 2f,
                        AndroidUtilities.dp(size == 40 ? 28 : 21), textPaint);
            } else if (checkDrawable != null) {
                checkDrawable.setAlpha(alpha);
                int dw = size != 24 ? AndroidUtilities.dp(size) / 2 : checkDrawable.getIntrinsicWidth();
                int dh = size != 24 ? AndroidUtilities.dp(size) / 2 : checkDrawable.getIntrinsicHeight();
                int x = (int) ((w - dw) / 2f);
                int y = (int) ((h - dh) / 2f);
                checkDrawable.setBounds(x, y + checkOffset, x + dw, y + dh + checkOffset);
                checkDrawable.draw(canvas);
            } else {
                checkMarkPaint.setAlpha(alpha);
                float scale = 0.5f;
                float sx = cx - AndroidUtilities.dp(size * scale * 0.15f);
                float sy = cy + AndroidUtilities.dp(size * scale * 0.05f);
                checkPath.reset();
                checkPath.moveTo(sx - AndroidUtilities.dp(size * scale * 0.15f),
                        sy - AndroidUtilities.dp(size * scale * 0.05f));
                checkPath.lineTo(sx, sy + AndroidUtilities.dp(size * scale * 0.1f));
                checkPath.lineTo(sx + AndroidUtilities.dp(size * scale * 0.3f),
                        sy - AndroidUtilities.dp(size * scale * 0.2f));
                canvas.drawPath(checkPath, checkMarkPaint);
            }
            canvas.restore();
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.CheckBox");
        info.setCheckable(true);
        info.setChecked(isChecked);
    }
}
