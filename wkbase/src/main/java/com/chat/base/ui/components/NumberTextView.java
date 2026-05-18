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
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.graphics.Typeface;
import android.view.View;

import androidx.annotation.Keep;

import com.chat.base.utils.AndroidUtilities;

import java.util.ArrayList;
import java.util.Locale;

public class NumberTextView extends View {

    private final ArrayList<StaticLayout> letters = new ArrayList<>();
    private final ArrayList<StaticLayout> oldLetters = new ArrayList<>();
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private ObjectAnimator animator;
    private float progress = 0f;
    private int currentNumber = 1;
    private boolean addNumber;
    private boolean center;
    private float textWidth;
    private float oldTextWidth;

    public NumberTextView(Context context) {
        super(context);
    }

    public NumberTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NumberTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
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

    public int getCurrentNumber() {
        return currentNumber;
    }

    public void setAddNumber() {
        addNumber = true;
    }

    public void setNumber(int number, boolean animated) {
        if (currentNumber == number && animated) return;
        if (animator != null) {
            animator.cancel();
            animator = null;
        }

        oldLetters.clear();
        oldLetters.addAll(letters);
        letters.clear();

        String oldText = formatNumber(currentNumber);
        String newText = formatNumber(number);
        boolean forward = addNumber ? number < currentNumber : number > currentNumber;

        boolean widthChanged = false;
        if (center) {
            textWidth = textPaint.measureText(newText);
            oldTextWidth = textPaint.measureText(oldText);
            widthChanged = textWidth != oldTextWidth;
        }

        currentNumber = number;
        progress = 0;

        for (int i = 0; i < newText.length(); i++) {
            String ch = newText.substring(i, i + 1);
            String oldCh = (i < oldText.length() && !oldLetters.isEmpty()) ? oldText.substring(i, i + 1) : null;
            if (!widthChanged && oldCh != null && oldCh.equals(ch)) {
                letters.add(oldLetters.get(i));
                oldLetters.set(i, null);
            } else {
                if (widthChanged && oldCh == null) {
                    oldLetters.add(new StaticLayout("", textPaint, 0,
                            Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false));
                }
                int charWidth = (int) Math.ceil(textPaint.measureText(ch));
                letters.add(new StaticLayout(ch, textPaint, charWidth,
                        Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false));
            }
        }

        if (animated && !oldLetters.isEmpty()) {
            animator = ObjectAnimator.ofFloat(this, "progress", forward ? -1f : 1f, 0f);
            animator.setDuration(addNumber ? 180 : 150);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animator = null;
                    oldLetters.clear();
                }
            });
            animator.start();
        }
        invalidate();
    }

    private String formatNumber(int number) {
        return addNumber ? String.format(Locale.US, "#%d", number)
                         : String.format(Locale.US, "%d", number);
    }

    public void setTextSize(int size) {
        textPaint.setTextSize(AndroidUtilities.dp(size));
        oldLetters.clear();
        letters.clear();
        setNumber(currentNumber, false);
    }

    public void setTextColor(int value) {
        textPaint.setColor(value);
        invalidate();
    }

    public void setTypeface(Typeface typeface) {
        textPaint.setTypeface(typeface);
        oldLetters.clear();
        letters.clear();
        setNumber(currentNumber, false);
    }

    public void setCenterAlign(boolean center) {
        this.center = center;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (letters.isEmpty()) return;

        float lineHeight = letters.get(0).getHeight();
        float slideHeight = addNumber ? AndroidUtilities.dp(4) : lineHeight;

        float startX = 0;
        float oldDeltaX = 0;
        if (center) {
            startX = (getMeasuredWidth() - textWidth) / 2f;
            oldDeltaX = (getMeasuredWidth() - oldTextWidth) / 2f - startX;
        }

        canvas.save();
        canvas.translate(getPaddingLeft() + startX, (getMeasuredHeight() - lineHeight) / 2f);

        int count = Math.max(letters.size(), oldLetters.size());
        for (int i = 0; i < count; i++) {
            canvas.save();
            StaticLayout oldLayout = i < oldLetters.size() ? oldLetters.get(i) : null;
            StaticLayout newLayout = i < letters.size() ? letters.get(i) : null;

            if (progress > 0) {
                if (oldLayout != null) {
                    textPaint.setAlpha((int) (255 * progress));
                    canvas.save();
                    canvas.translate(oldDeltaX, (progress - 1f) * slideHeight);
                    oldLayout.draw(canvas);
                    canvas.restore();
                    if (newLayout != null) {
                        textPaint.setAlpha((int) (255 * (1f - progress)));
                        canvas.translate(0, progress * slideHeight);
                    }
                } else {
                    textPaint.setAlpha(255);
                }
            } else if (progress < 0) {
                if (oldLayout != null) {
                    textPaint.setAlpha((int) (255 * -progress));
                    canvas.save();
                    canvas.translate(oldDeltaX, (1f + progress) * slideHeight);
                    oldLayout.draw(canvas);
                    canvas.restore();
                }
                if (newLayout != null) {
                    if (i == count - 1 || oldLayout != null) {
                        textPaint.setAlpha((int) (255 * (1f + progress)));
                        canvas.translate(0, progress * slideHeight);
                    } else {
                        textPaint.setAlpha(255);
                    }
                }
            } else if (newLayout != null) {
                textPaint.setAlpha(255);
            }

            if (newLayout != null) {
                newLayout.draw(canvas);
            }
            canvas.restore();

            float advance = newLayout != null ? newLayout.getLineWidth(0)
                    : (oldLayout != null ? oldLayout.getLineWidth(0) + AndroidUtilities.dp(1) : 0);
            canvas.translate(advance, 0);

            if (newLayout != null && oldLayout != null) {
                oldDeltaX += oldLayout.getLineWidth(0) - newLayout.getLineWidth(0);
            }
        }
        canvas.restore();
    }
}
