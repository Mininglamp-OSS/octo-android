/*
 * Copyright 2026-present OctoIM contributors
 * Licensed under the Apache License, Version 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package com.octoim.rlottie;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

import java.util.HashMap;

import androidx.appcompat.widget.AppCompatImageView;

public class RLottieImageView extends AppCompatImageView {

    private HashMap<String, Integer> layerColors;
    private RLottieDrawable drawable;
    private boolean autoRepeat;
    private boolean attachedToWindow;
    private boolean playing;
    private boolean startOnAttach;

    public RLottieImageView(Context context) {
        super(context);
    }

    public RLottieImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RLottieImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void clearLayerColors() {
        layerColors = null;
    }

    public void setLayerColor(String layerName, int color) {
        if (layerColors == null) {
            layerColors = new HashMap<>();
        }
        layerColors.put(layerName, color);
        if (drawable != null) {
            drawable.setLayerColor(layerName, color);
        }
    }

    public void replaceColors(int[] colors) {
        if (drawable != null) {
            drawable.replaceColors(colors);
        }
    }

    public void setAnimation(int resId, int w, int h) {
        setAnimation(resId, w, h, null);
    }

    public void setAnimation(int resId, int w, int h, int[] colorReplacement) {
        drawable = new RLottieDrawable(getContext(), resId, "" + resId, w, h, false, colorReplacement);
        drawable.setAutoRepeat(autoRepeat ? 1 : 0);
        if (layerColors != null) {
            drawable.beginApplyLayerColors();
            for (HashMap.Entry<String, Integer> entry : layerColors.entrySet()) {
                drawable.setLayerColor(entry.getKey(), entry.getValue());
            }
            drawable.commitApplyLayerColors();
        }
        drawable.setAllowDecodeSingleFrame(true);
        setImageDrawable(drawable);
    }

    public void setAnimation(RLottieDrawable lottieDrawable) {
        drawable = lottieDrawable;
        if (drawable != null) {
            drawable.setAutoRepeat(autoRepeat ? 1 : 0);
            if (layerColors != null) {
                drawable.beginApplyLayerColors();
                for (HashMap.Entry<String, Integer> entry : layerColors.entrySet()) {
                    drawable.setLayerColor(entry.getKey(), entry.getValue());
                }
                drawable.commitApplyLayerColors();
            }
            drawable.addParentView(this);
            drawable.setAllowDecodeSingleFrame(true);
        }
        setImageDrawable(lottieDrawable);
    }

    public void clearAnimationDrawable() {
        if (drawable != null) {
            drawable.stop();
            drawable.removeParentView(this);
        }
        drawable = null;
        setImageDrawable(null);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;
        if (drawable != null) {
            drawable.addParentView(this);
            if (playing) {
                drawable.start();
            }
        }
        if (startOnAttach) {
            startOnAttach = false;
            playAnimation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;
        if (drawable != null) {
            drawable.stop();
            drawable.removeParentView(this);
        }
    }

    public boolean isPlaying() {
        return drawable != null && drawable.isRunning();
    }

    public void setAutoRepeat(boolean repeat) {
        autoRepeat = repeat;
        if (drawable != null) {
            drawable.setAutoRepeat(repeat ? 1 : 0);
        }
    }

    public void setProgress(float progress) {
        if (drawable != null) {
            drawable.setProgress(progress);
        }
    }

    @Override
    public void setImageResource(int resId) {
        super.setImageResource(resId);
        drawable = null;
    }

    public void playAnimation() {
        if (drawable == null) return;
        playing = true;
        if (attachedToWindow) {
            drawable.start();
        } else {
            startOnAttach = true;
        }
    }

    public void stopAnimation() {
        if (drawable == null) return;
        playing = false;
        drawable.stop();
    }

    public RLottieDrawable getAnimatedDrawable() {
        return drawable;
    }
}
