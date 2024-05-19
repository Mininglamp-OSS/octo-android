/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package com.chat.base.ui.components;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.Keep;
import androidx.core.content.ContextCompat;

import com.chat.base.R;
import com.chat.base.utils.AndroidUtilities;

public class CheckBoxSquare extends View {

    private RectF rectF;

    private Bitmap drawBitmap;
    private Canvas drawCanvas;

    private float progress;
    private ObjectAnimator checkAnimator;

    private boolean attachedToWindow;
    private boolean isChecked;
    private boolean isDisabled;
    private boolean isAlert;

    private final static float progressBounceDiff = 0.2f;

    private int key1;
    private int key2;
    private int key3;
    private Paint checkboxSquare_eraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint checkboxSquare_backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint checkboxSquare_checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CheckBoxSquare(Context context, boolean alert) {
        super(context);
        checkboxSquare_eraserPaint.setColor(0);
        checkboxSquare_eraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        checkboxSquare_checkPaint.setStyle(Paint.Style.STROKE);
        checkboxSquare_checkPaint.setStrokeWidth(AndroidUtilities.dp(2));
        checkboxSquare_checkPaint.setStrokeCap(Paint.Cap.ROUND);


//        key1 = isAlert ? Theme.key_dialogCheckboxSquareUnchecked : Theme.key_checkboxSquareUnchecked;
//        key2 = isAlert ? Theme.key_dialogCheckboxSquareBackground : Theme.key_checkboxSquareBackground;
//        key3 = isAlert ? Theme.key_dialogCheckboxSquareCheck : Theme.key_checkboxSquareCheck;
        key1 = ContextCompat.getColor(context, R.color.popupTextColor);
        key2 = ContextCompat.getColor(context, R.color.colorAccent);
        key3 = ContextCompat.getColor(context, R.color.screen_bg);
        rectF = new RectF();
        drawBitmap = Bitmap.createBitmap(AndroidUtilities.dp(18), AndroidUtilities.dp(18), Bitmap.Config.ARGB_4444);
        drawCanvas = new Canvas(drawBitmap);
        isAlert = alert;
    }

    public void setColors(int unchecked, int checked, int check) {
        key1 = unchecked;
        key2 = checked;
        key3 = check;
        invalidate();
    }

    @Keep
    public void setProgress(float value) {
        if (progress == value) {
            return;
        }
        progress = value;
        invalidate();
    }

    @Keep
    public float getProgress() {
        return progress;
    }

    private void cancelCheckAnimator() {
        if (checkAnimator != null) {
            checkAnimator.cancel();
        }
    }

    private void animateToCheckedState(boolean newCheckedState) {
        checkAnimator = ObjectAnimator.ofFloat(this, "progress", newCheckedState ? 1 : 0);
        checkAnimator.setDuration(300);
        checkAnimator.start();
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
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checked == isChecked) {
            return;
        }
        isChecked = checked;
        if (attachedToWindow && animated) {
            animateToCheckedState(checked);
        } else {
            cancelCheckAnimator();
            setProgress(checked ? 1.0f : 0.0f);
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
    protected void onDraw(Canvas canvas) {
        if (getVisibility() != VISIBLE) {
            return;
        }

        float checkProgress;
        float bounceProgress;
        int uncheckedColor = key1;
        int color = key2;
        if (progress <= 0.5f) {
            bounceProgress = checkProgress = progress / 0.5f;
            int rD = (int) ((Color.red(color) - Color.red(uncheckedColor)) * checkProgress);
            int gD = (int) ((Color.green(color) - Color.green(uncheckedColor)) * checkProgress);
            int bD = (int) ((Color.blue(color) - Color.blue(uncheckedColor)) * checkProgress);
            int c = Color.rgb(Color.red(uncheckedColor) + rD, Color.green(uncheckedColor) + gD, Color.blue(uncheckedColor) + bD);
            checkboxSquare_backgroundPaint.setColor(c);
        } else {
            bounceProgress = 2.0f - progress / 0.5f;
            checkProgress = 1.0f;
            checkboxSquare_backgroundPaint.setColor(color);
        }
        if (isDisabled) {
            checkboxSquare_backgroundPaint.setColor(ContextCompat.getColor(getContext(), R.color.popupTextColor));
        }
        float bounce = AndroidUtilities.dp(1) * bounceProgress;
        rectF.set(bounce, bounce, AndroidUtilities.dp(18) - bounce, AndroidUtilities.dp(18) - bounce);

        drawBitmap.eraseColor(0);
        drawCanvas.drawRoundRect(rectF, AndroidUtilities.dp(2), AndroidUtilities.dp(2), checkboxSquare_backgroundPaint);

        if (checkProgress != 1) {
            float rad = Math.min(AndroidUtilities.dp(7), AndroidUtilities.dp(7) * checkProgress + bounce);
            rectF.set(AndroidUtilities.dp(2) + rad, AndroidUtilities.dp(2) + rad, AndroidUtilities.dp(16) - rad, AndroidUtilities.dp(16) - rad);
            drawCanvas.drawRect(rectF, checkboxSquare_eraserPaint);
        }

        if (progress > 0.5f) {
            checkboxSquare_checkPaint.setColor(key3);

            int endX = (int) (AndroidUtilities.dp(7) - AndroidUtilities.dp(3) * (1.0f - bounceProgress));
            int endY = (int) (AndroidUtilities.dpf2(13) - AndroidUtilities.dp(3) * (1.0f - bounceProgress));
            drawCanvas.drawLine(AndroidUtilities.dp(7), (int) AndroidUtilities.dpf2(13), endX, endY, checkboxSquare_checkPaint);

            endX = (int) (AndroidUtilities.dpf2(7) + AndroidUtilities.dp(7) * (1.0f - bounceProgress));
            endY = (int) (AndroidUtilities.dpf2(13) - AndroidUtilities.dp(7) * (1.0f - bounceProgress));
            drawCanvas.drawLine((int) AndroidUtilities.dpf2(7), (int) AndroidUtilities.dpf2(13), endX, endY, checkboxSquare_checkPaint);
        }
        canvas.drawBitmap(drawBitmap, 0, 0, null);
    }

}
