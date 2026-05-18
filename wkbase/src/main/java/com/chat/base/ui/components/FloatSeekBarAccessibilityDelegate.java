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

import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

public abstract class FloatSeekBarAccessibilityDelegate extends SeekBarAccessibilityDelegate {

    private final boolean setPercentsEnabled;

    public FloatSeekBarAccessibilityDelegate() {
        this(false);
    }

    public FloatSeekBarAccessibilityDelegate(boolean setPercentsEnabled) {
        this.setPercentsEnabled = setPercentsEnabled;
    }

    @Override
    public void onInitializeAccessibilityNodeInfoInternal(@Nullable View host, AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfoInternal(host, info);
        if (setPercentsEnabled) {
            final AccessibilityNodeInfoCompat infoCompat = AccessibilityNodeInfoCompat.wrap(info);
            infoCompat.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SET_PROGRESS);
            infoCompat.setRangeInfo(AccessibilityNodeInfoCompat.RangeInfoCompat.obtain(AccessibilityNodeInfoCompat.RangeInfoCompat.RANGE_TYPE_FLOAT, getMinValue(), getMaxValue(), getProgress()));
        }
    }

    @Override
    public boolean performAccessibilityActionInternal(@Nullable View host, int action, Bundle args) {
        if (super.performAccessibilityActionInternal(host, action, args)) {
            return true;
        }
        if (action == AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SET_PROGRESS.getId()) {
            setProgress(args.getFloat(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_PROGRESS_VALUE));
            return true;
        }
        return false;
    }

    @Override
    protected void doScroll(View host, boolean backward) {
        float delta = getDelta();
        if (backward) {
            delta *= -1f;
        }
        setProgress(Math.min(getMaxValue(), Math.max(getMinValue(), getProgress() + delta)));
    }

    @Override
    protected boolean canScrollBackward(View host) {
        return getProgress() > getMinValue();
    }

    @Override
    protected boolean canScrollForward(View host) {
        return getProgress() < getMaxValue();
    }

    protected abstract float getProgress();

    protected abstract void setProgress(float progress);

    protected float getMinValue() {
        return 0f;
    }

    protected float getMaxValue() {
        return 1f;
    }

    protected float getDelta() {
        return 0.05f;
    }
}
