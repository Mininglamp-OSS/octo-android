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

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.chat.base.R;
import com.chat.base.utils.AndroidUtilities;

/**
 * 搜索页分类 tab 栏：不等宽文字 tab（按内容自适应宽度）+ 弹性间隙撑满整行 + 底部下划线指示器。
 *
 * 与 {@link SegmentTabView} 的区别：后者硬编码 2 个等宽 tab + 药丸背景，用于 ChatFragment
 * 群聊/私聊切换；搜索页需要 5 个不等宽 tab + 下划线，视觉和交互都不同，故新建而非扩展。
 */
public class SearchTabBarView extends FrameLayout {

    public interface OnTabSelectedListener {
        void onTabSelected(int index);
    }

    private final TextView[] tabs;
    private final View indicatorView;
    private int selectedIndex = 0;
    private OnTabSelectedListener listener;
    private boolean indicatorPositioned = false;

    public SearchTabBarView(@NonNull Context context, String[] tabTitles) {
        super(context);
        tabs = new TextView[tabTitles.length];

        LinearLayout tabRow = new LinearLayout(context);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setGravity(Gravity.CENTER_VERTICAL);
        addView(tabRow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < tabTitles.length; i++) {
            TextView tv = new TextView(context);
            tv.setText(tabTitles[i]);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tabRow.addView(tv, lp);

            final int index = i;
            tv.setOnClickListener(v -> selectTab(index));
            tabs[i] = tv;

            // tab 间弹性间隙：撑满整行宽度，同时保持所有间隙相等。
            if (i < tabTitles.length - 1) {
                View spacer = new View(context);
                tabRow.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1f));
            }
        }

        indicatorView = new View(context);
        indicatorView.setBackgroundColor(ContextCompat.getColor(context, R.color.colorDark));
        LayoutParams indicatorLp = new LayoutParams(0, AndroidUtilities.dp(3));
        indicatorLp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        addView(indicatorView, indicatorLp);

        updateTabStyles();
    }

    public void setOnTabSelectedListener(OnTabSelectedListener listener) {
        this.listener = listener;
    }

    public void selectTab(int index) {
        if (index == selectedIndex) return;
        selectedIndex = index;
        updateTabStyles();
        animateIndicator();
        if (listener != null) {
            listener.onTabSelected(index);
        }
    }

    public void selectTabWithoutCallback(int index) {
        if (index == selectedIndex) return;
        selectedIndex = index;
        updateTabStyles();
        animateIndicator();
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    private void updateTabStyles() {
        for (int i = 0; i < tabs.length; i++) {
            if (i == selectedIndex) {
                tabs[i].setTextColor(ContextCompat.getColor(getContext(), R.color.colorDark));
                tabs[i].getPaint().setFakeBoldText(true);
            } else {
                tabs[i].setTextColor(ContextCompat.getColor(getContext(), R.color.color999));
                tabs[i].getPaint().setFakeBoldText(false);
            }
        }
    }

    private void animateIndicator() {
        TextView target = tabs[selectedIndex];
        float newLeft = target.getLeft();
        float newWidth = target.getWidth();

        if (!indicatorPositioned) {
            positionIndicator(newLeft, newWidth);
            return;
        }

        float startLeft = indicatorView.getTranslationX();
        float startWidth = indicatorView.getLayoutParams().width;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(200);
        animator.addUpdateListener(a -> {
            float fraction = (float) a.getAnimatedValue();
            float left = startLeft + (newLeft - startLeft) * fraction;
            float width = startWidth + (newWidth - startWidth) * fraction;
            positionIndicator(left, width);
        });
        animator.start();
    }

    private void positionIndicator(float left, float width) {
        indicatorView.setTranslationX(left);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) indicatorView.getLayoutParams();
        lp.width = (int) width;
        indicatorView.setLayoutParams(lp);
        indicatorPositioned = true;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (!indicatorPositioned || changed) {
            TextView target = tabs[selectedIndex];
            positionIndicator(target.getLeft(), target.getWidth());
        }
    }
}
