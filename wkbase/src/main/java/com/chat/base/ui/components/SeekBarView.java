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
import android.os.Build;
import android.os.SystemClock;
import android.util.StateSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.chat.base.R;
import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;

public class SeekBarView extends FrameLayout {

    private final SeekBarAccessibilityDelegate seekBarAccessibilityDelegate;
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int thumbSize;
    private final int selectorWidth;
    private int thumbX;
    private int thumbDX;
    private float progressToSet = -100;
    private boolean pressed;
    public SeekBarViewDelegate delegate;
    private boolean reportChanges;
    private float bufferedProgress;
    private Drawable hoverDrawable;
    private long lastUpdateTime;
    private float currentRadius;
    private final int[] pressedState = new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed};
    private float transitionProgress = 1f;
    private int transitionThumbX;
    private boolean twoSided;
    private boolean captured;
    private float startX, startY;

    public interface SeekBarViewDelegate {
        void onSeekBarDrag(boolean stop, float progress);
        void onSeekBarPressed(boolean pressed);
        default CharSequence getContentDescription() { return null; }
        default int getStepsCount() { return 0; }
    }

    public SeekBarView(Context context) {
        this(context, false);
    }

    public SeekBarView(Context context, boolean inPercents) {
        super(context);
        setWillNotDraw(false);

        trackPaint.setColor(ContextCompat.getColor(context, R.color.color999));
        activeTrackPaint.setColor(Theme.colorAccount);

        selectorWidth = AndroidUtilities.dp(32);
        thumbSize = AndroidUtilities.dp(24);
        currentRadius = AndroidUtilities.dp(6);

        if (Build.VERSION.SDK_INT >= 21) {
            hoverDrawable = Theme.createSelectorDrawable(
                    ColorUtils.setAlphaComponent(0xff54AAEB, 40), 1, AndroidUtilities.dp(16));
            hoverDrawable.setCallback(this);
            hoverDrawable.setVisible(true, false);
        }

        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setAccessibilityDelegate(seekBarAccessibilityDelegate = new FloatSeekBarAccessibilityDelegate(inPercents) {
            @Override
            public float getProgress() {
                return SeekBarView.this.getProgress();
            }

            @Override
            public void setProgress(float progress) {
                pressed = true;
                SeekBarView.this.setProgress(progress);
                if (delegate != null) delegate.onSeekBarDrag(true, progress);
                pressed = false;
            }

            @Override
            protected float getDelta() {
                int steps = delegate != null ? delegate.getStepsCount() : 0;
                return steps > 0 ? 1f / steps : super.getDelta();
            }

            @Override
            public CharSequence getContentDescription(View host) {
                return delegate != null ? delegate.getContentDescription() : null;
            }
        });
    }

    public void setColors(int inner, int outer) {
        trackPaint.setColor(inner);
        activeTrackPaint.setColor(outer);
        if (hoverDrawable != null) {
            Theme.setSelectorDrawableColor(hoverDrawable, ColorUtils.setAlphaComponent(outer, 40), true);
        }
    }

    public void setTwoSided(boolean value) { twoSided = value; }
    public boolean isTwoSided() { return twoSided; }
    public void setInnerColor(int color) { trackPaint.setColor(color); }

    public void setOuterColor(int color) {
        activeTrackPaint.setColor(color);
        if (hoverDrawable != null) {
            Theme.setSelectorDrawableColor(hoverDrawable, ColorUtils.setAlphaComponent(color, 40), true);
        }
    }

    public void setReportChanges(boolean value) { reportChanges = value; }
    public void setDelegate(SeekBarViewDelegate d) { delegate = d; }
    public boolean isDragging() { return pressed; }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) { return handleTouch(ev); }

    @Override
    public boolean onTouchEvent(MotionEvent event) { return handleTouch(event); }

    private boolean handleTouch(MotionEvent ev) {
        int action = ev.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            startX = ev.getX();
            startY = ev.getY();
            return true;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            captured = false;
            if (action == MotionEvent.ACTION_UP) {
                ViewConfiguration vc = ViewConfiguration.get(getContext());
                if (Math.abs(ev.getY() - startY) < vc.getScaledTouchSlop()) {
                    int extra = (getMeasuredHeight() - thumbSize) / 2;
                    if (!(thumbX - extra <= ev.getX() && ev.getX() <= thumbX + thumbSize + extra)) {
                        thumbX = clampThumbX((int) ev.getX() - thumbSize / 2);
                    }
                    thumbDX = (int) (ev.getX() - thumbX);
                    pressed = true;
                }
            }
            if (pressed) {
                if (action == MotionEvent.ACTION_UP) {
                    notifyDrag(true);
                }
                if (Build.VERSION.SDK_INT >= 21 && hoverDrawable != null) {
                    hoverDrawable.setState(StateSet.NOTHING);
                }
                if (delegate != null) delegate.onSeekBarPressed(false);
                pressed = false;
                invalidate();
                return true;
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (!captured) {
                ViewConfiguration vc = ViewConfiguration.get(getContext());
                if (Math.abs(ev.getY() - startY) > vc.getScaledTouchSlop()) return false;
                if (Math.abs(ev.getX() - startX) > vc.getScaledTouchSlop()) {
                    captured = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    int extra = (getMeasuredHeight() - thumbSize) / 2;
                    if (ev.getY() >= 0 && ev.getY() <= getMeasuredHeight()) {
                        if (!(thumbX - extra <= ev.getX() && ev.getX() <= thumbX + thumbSize + extra)) {
                            thumbX = clampThumbX((int) ev.getX() - thumbSize / 2);
                        }
                        thumbDX = (int) (ev.getX() - thumbX);
                        pressed = true;
                        if (delegate != null) delegate.onSeekBarPressed(true);
                        if (Build.VERSION.SDK_INT >= 21 && hoverDrawable != null) {
                            hoverDrawable.setState(pressedState);
                            hoverDrawable.setHotspot(ev.getX(), ev.getY());
                        }
                        invalidate();
                        return true;
                    }
                }
            } else if (pressed) {
                thumbX = clampThumbX((int) (ev.getX() - thumbDX));
                if (reportChanges) notifyDrag(false);
                if (Build.VERSION.SDK_INT >= 21 && hoverDrawable != null) {
                    hoverDrawable.setHotspot(ev.getX(), ev.getY());
                }
                invalidate();
                return true;
            }
        }
        return false;
    }

    private int clampThumbX(int x) {
        return Math.max(0, Math.min(x, getMeasuredWidth() - selectorWidth));
    }

    private void notifyDrag(boolean stopped) {
        if (delegate == null) return;
        int trackWidth = getMeasuredWidth() - selectorWidth;
        if (twoSided) {
            float half = trackWidth / 2f;
            if (thumbX >= half) {
                delegate.onSeekBarDrag(stopped, (thumbX - half) / half);
            } else {
                delegate.onSeekBarDrag(stopped, -Math.max(0.01f, 1f - (half - thumbX) / half));
            }
        } else {
            delegate.onSeekBarDrag(stopped, trackWidth > 0 ? (float) thumbX / trackWidth : 0);
        }
    }

    public float getProgress() {
        if (getMeasuredWidth() == 0) return progressToSet;
        int trackWidth = getMeasuredWidth() - selectorWidth;
        return trackWidth > 0 ? thumbX / (float) trackWidth : 0;
    }

    public void setProgress(float progress) { setProgress(progress, false); }

    public void setProgress(float progress, boolean animated) {
        if (getMeasuredWidth() == 0) {
            progressToSet = progress;
            return;
        }
        progressToSet = -100;
        int trackWidth = getMeasuredWidth() - selectorWidth;
        int newThumbX;
        if (twoSided) {
            float cx = trackWidth / 2f;
            newThumbX = progress < 0
                    ? (int) Math.ceil(cx + trackWidth / 2f * -(1f + progress))
                    : (int) Math.ceil(cx + trackWidth / 2f * progress);
        } else {
            newThumbX = (int) Math.ceil(trackWidth * progress);
        }
        if (thumbX != newThumbX) {
            if (animated) {
                transitionThumbX = thumbX;
                transitionProgress = 0f;
            }
            thumbX = clampThumbX(newThumbX);
            invalidate();
        }
    }

    public void setBufferedProgress(float progress) {
        bufferedProgress = progress;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (progressToSet != -100 && getMeasuredWidth() > 0) {
            setProgress(progressToSet);
            progressToSet = -100;
        }
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || who == hoverDrawable;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int cy = getMeasuredHeight() / 2;
        int trackHalf = AndroidUtilities.dp(1);
        int halfSelector = selectorWidth / 2;

        // background track
        canvas.drawRect(halfSelector, cy - trackHalf, getMeasuredWidth() - halfSelector, cy + trackHalf, trackPaint);

        // buffered progress
        if (bufferedProgress > 0) {
            Paint bufPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bufPaint.setColor(ContextCompat.getColor(getContext(), R.color.black));
            canvas.drawRect(halfSelector, cy - trackHalf,
                    halfSelector + bufferedProgress * (getMeasuredWidth() - selectorWidth),
                    cy + trackHalf, bufPaint);
        }

        // active track
        if (twoSided) {
            int mid = getMeasuredWidth() / 2;
            canvas.drawRect(mid - AndroidUtilities.dp(1), cy - AndroidUtilities.dp(6),
                    mid + AndroidUtilities.dp(1), cy + AndroidUtilities.dp(6), activeTrackPaint);
            if (thumbX > (getMeasuredWidth() - selectorWidth) / 2) {
                canvas.drawRect(mid, cy - trackHalf, halfSelector + thumbX, cy + trackHalf, activeTrackPaint);
            } else {
                canvas.drawRect(thumbX + halfSelector, cy - trackHalf, mid, cy + trackHalf, activeTrackPaint);
            }
        } else {
            canvas.drawRect(halfSelector, cy - trackHalf, halfSelector + thumbX, cy + trackHalf, activeTrackPaint);
        }

        // hover ripple
        if (hoverDrawable != null) {
            int y = (getMeasuredHeight() - thumbSize) / 2;
            int dx = thumbX + halfSelector - AndroidUtilities.dp(16);
            int dy = y + thumbSize / 2 - AndroidUtilities.dp(16);
            hoverDrawable.setBounds(dx, dy, dx + AndroidUtilities.dp(32), dy + AndroidUtilities.dp(32));
            hoverDrawable.draw(canvas);
        }

        // thumb circle with radius animation
        boolean needInvalidate = false;
        int targetRad = AndroidUtilities.dp(pressed ? 8 : 6);
        long now = SystemClock.elapsedRealtime();
        long dt = Math.min(now - lastUpdateTime, 16);
        lastUpdateTime = now;

        if (currentRadius != targetRad) {
            float step = AndroidUtilities.dp(1) * (dt / 60f);
            currentRadius = currentRadius < targetRad
                    ? Math.min(currentRadius + step, targetRad)
                    : Math.max(currentRadius - step, targetRad);
            needInvalidate = true;
        }

        if (transitionProgress < 1f) {
            transitionProgress = Math.min(1f, transitionProgress + dt / 225f);
            needInvalidate = true;
        }

        int thumbY = (getMeasuredHeight() - thumbSize) / 2 + thumbSize / 2;
        if (transitionProgress < 1f) {
            float oldScale = 1f - Math.min(1f, transitionProgress * 3f);
            float newScale = transitionProgress;
            if (oldScale > 0f) {
                canvas.drawCircle(transitionThumbX + halfSelector, thumbY, currentRadius * oldScale, activeTrackPaint);
            }
            canvas.drawCircle(thumbX + halfSelector, thumbY, currentRadius * newScale, activeTrackPaint);
        } else {
            canvas.drawCircle(thumbX + halfSelector, thumbY, currentRadius, activeTrackPaint);
        }

        if (needInvalidate) postInvalidateOnAnimation();
    }

    public SeekBarAccessibilityDelegate getSeekBarAccessibilityDelegate() {
        return seekBarAccessibilityDelegate;
    }
}
