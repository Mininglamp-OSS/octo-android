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

package com.chat.base.utils;

import android.annotation.SuppressLint;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;

import androidx.core.view.ViewCompat;

public class LayoutHelper {

    public static final int MATCH_PARENT = -1;
    public static final int WRAP_CONTENT = -2;

    private LayoutHelper() {}

    private static int resolve(float size) {
        return (int) (size < 0 ? size : AndroidUtilities.dp(size));
    }

    // region ScrollView

    public static ScrollView.LayoutParams createScroll(int width, int height, int gravity) {
        return new ScrollView.LayoutParams(resolve(width), resolve(height), gravity);
    }

    public static ScrollView.LayoutParams createScroll(int width, int height, int gravity,
            float leftMargin, float topMargin, float rightMargin, float bottomMargin) {
        ScrollView.LayoutParams lp = new ScrollView.LayoutParams(resolve(width), resolve(height), gravity);
        lp.leftMargin = AndroidUtilities.dp(leftMargin);
        lp.topMargin = AndroidUtilities.dp(topMargin);
        lp.rightMargin = AndroidUtilities.dp(rightMargin);
        lp.bottomMargin = AndroidUtilities.dp(bottomMargin);
        return lp;
    }

    // endregion

    // region FrameLayout

    public static FrameLayout.LayoutParams createFrame(int width, float height, int gravity,
            float leftMargin, float topMargin, float rightMargin, float bottomMargin) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(resolve(width), resolve(height), gravity);
        lp.setMargins(AndroidUtilities.dp(leftMargin), AndroidUtilities.dp(topMargin),
                AndroidUtilities.dp(rightMargin), AndroidUtilities.dp(bottomMargin));
        return lp;
    }

    public static FrameLayout.LayoutParams createFrame(int width, int height, int gravity) {
        return new FrameLayout.LayoutParams(resolve(width), resolve(height), gravity);
    }

    public static FrameLayout.LayoutParams createFrame(int width, float height) {
        return new FrameLayout.LayoutParams(resolve(width), resolve(height));
    }

    public static FrameLayout.LayoutParams createFrame(float width, float height, int gravity) {
        return new FrameLayout.LayoutParams(resolve(width), resolve(height), gravity);
    }

    public static FrameLayout.LayoutParams createFrameRelatively(float width, float height, int gravity,
            float startMargin, float topMargin, float endMargin, float bottomMargin) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                resolve(width), resolve(height), resolveAbsoluteGravity(gravity));
        lp.leftMargin = AndroidUtilities.dp(AndroidUtilities.isRTL ? endMargin : startMargin);
        lp.topMargin = AndroidUtilities.dp(topMargin);
        lp.rightMargin = AndroidUtilities.dp(AndroidUtilities.isRTL ? startMargin : endMargin);
        lp.bottomMargin = AndroidUtilities.dp(bottomMargin);
        return lp;
    }

    public static FrameLayout.LayoutParams createFrameRelatively(float width, float height, int gravity) {
        return new FrameLayout.LayoutParams(resolve(width), resolve(height), resolveAbsoluteGravity(gravity));
    }

    // endregion

    // region RelativeLayout

    public static RelativeLayout.LayoutParams createRelative(float width, float height,
            int leftMargin, int topMargin, int rightMargin, int bottomMargin,
            int alignParent, int alignRelative, int anchorRelative) {
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(resolve(width), resolve(height));
        if (alignParent >= 0) lp.addRule(alignParent);
        if (alignRelative >= 0 && anchorRelative >= 0) lp.addRule(alignRelative, anchorRelative);
        lp.leftMargin = AndroidUtilities.dp(leftMargin);
        lp.topMargin = AndroidUtilities.dp(topMargin);
        lp.rightMargin = AndroidUtilities.dp(rightMargin);
        lp.bottomMargin = AndroidUtilities.dp(bottomMargin);
        return lp;
    }

    public static RelativeLayout.LayoutParams createRelative(int width, int height,
            int leftMargin, int topMargin, int rightMargin, int bottomMargin) {
        return createRelative(width, height, leftMargin, topMargin, rightMargin, bottomMargin, -1, -1, -1);
    }

    public static RelativeLayout.LayoutParams createRelative(int width, int height,
            int leftMargin, int topMargin, int rightMargin, int bottomMargin, int alignParent) {
        return createRelative(width, height, leftMargin, topMargin, rightMargin, bottomMargin, alignParent, -1, -1);
    }

    public static RelativeLayout.LayoutParams createRelative(float width, float height,
            int leftMargin, int topMargin, int rightMargin, int bottomMargin,
            int alignRelative, int anchorRelative) {
        return createRelative(width, height, leftMargin, topMargin, rightMargin, bottomMargin, -1, alignRelative, anchorRelative);
    }

    public static RelativeLayout.LayoutParams createRelative(int width, int height,
            int alignParent, int alignRelative, int anchorRelative) {
        return createRelative(width, height, 0, 0, 0, 0, alignParent, alignRelative, anchorRelative);
    }

    public static RelativeLayout.LayoutParams createRelative(int width, int height) {
        return createRelative(width, height, 0, 0, 0, 0, -1, -1, -1);
    }

    public static RelativeLayout.LayoutParams createRelative(int width, int height, int alignParent) {
        return createRelative(width, height, 0, 0, 0, 0, alignParent, -1, -1);
    }

    public static RelativeLayout.LayoutParams createRelative(int width, int height,
            int alignRelative, int anchorRelative) {
        return createRelative(width, height, 0, 0, 0, 0, -1, alignRelative, anchorRelative);
    }

    // endregion

    // region LinearLayout

    public static LinearLayout.LayoutParams createLinear(int width, int height, float weight,
            int gravity, int leftMargin, int topMargin, int rightMargin, int bottomMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(resolve(width), resolve(height), weight);
        lp.setMargins(AndroidUtilities.dp(leftMargin), AndroidUtilities.dp(topMargin),
                AndroidUtilities.dp(rightMargin), AndroidUtilities.dp(bottomMargin));
        lp.gravity = gravity;
        return lp;
    }

    public static LinearLayout.LayoutParams createLinear(int width, int height, float weight,
            int leftMargin, int topMargin, int rightMargin, int bottomMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(resolve(width), resolve(height), weight);
        lp.setMargins(AndroidUtilities.dp(leftMargin), AndroidUtilities.dp(topMargin),
                AndroidUtilities.dp(rightMargin), AndroidUtilities.dp(bottomMargin));
        return lp;
    }

    public static LinearLayout.LayoutParams createLinear(int width, int height, int gravity,
            int leftMargin, int topMargin, int rightMargin, int bottomMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(resolve(width), resolve(height));
        lp.setMargins(AndroidUtilities.dp(leftMargin), AndroidUtilities.dp(topMargin),
                AndroidUtilities.dp(rightMargin), AndroidUtilities.dp(bottomMargin));
        lp.gravity = gravity;
        return lp;
    }

    public static LinearLayout.LayoutParams createLinear(int width, int height,
            float leftMargin, float topMargin, float rightMargin, float bottomMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(resolve(width), resolve(height));
        lp.setMargins(AndroidUtilities.dp(leftMargin), AndroidUtilities.dp(topMargin),
                AndroidUtilities.dp(rightMargin), AndroidUtilities.dp(bottomMargin));
        return lp;
    }

    public static LinearLayout.LayoutParams createLinear(int width, int height, float weight, int gravity) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(resolve(width), resolve(height), weight);
        lp.gravity = gravity;
        return lp;
    }

    public static LinearLayout.LayoutParams createLinear(int width, int height, int gravity) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(resolve(width), resolve(height));
        lp.gravity = gravity;
        return lp;
    }

    public static LinearLayout.LayoutParams createLinear(int width, int height, float weight) {
        return new LinearLayout.LayoutParams(resolve(width), resolve(height), weight);
    }

    public static LinearLayout.LayoutParams createLinear(int width, int height) {
        return new LinearLayout.LayoutParams(resolve(width), resolve(height));
    }

    // endregion

    // region Gravity helpers

    private static int resolveAbsoluteGravity(int gravity) {
        return Gravity.getAbsoluteGravity(gravity,
                AndroidUtilities.isRTL ? ViewCompat.LAYOUT_DIRECTION_RTL : ViewCompat.LAYOUT_DIRECTION_LTR);
    }

    @SuppressLint("RtlHardcoded")
    public static int getAbsoluteGravityStart() {
        return AndroidUtilities.isRTL ? Gravity.RIGHT : Gravity.LEFT;
    }

    @SuppressLint("RtlHardcoded")
    public static int getAbsoluteGravityEnd() {
        return AndroidUtilities.isRTL ? Gravity.LEFT : Gravity.RIGHT;
    }

    // endregion
}
