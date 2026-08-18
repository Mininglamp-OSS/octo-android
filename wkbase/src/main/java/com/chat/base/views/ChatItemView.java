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

package com.chat.base.views;

import android.animation.LayoutTransition;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

public class ChatItemView extends LinearLayout {
    public ChatItemView(Context context) {
        this(context, null);
    }

    public ChatItemView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    private IViewClick iViewClick;
    private boolean isDelivered;

    /**
     * 关掉 item 子树里所有 {@link LayoutTransition} 的 {@code animateParentHierarchy}。
     *
     * <p>气泡内部几个容器开着 {@code animateLayoutChanges="true"}（wkBaseContentLayout /
     * contentLayout / contentTvLayout / textContentLayout）。绑定时
     * {@code removeAllViews() + addView()}、以及引用块 {@code addView} 进气泡，都会触发它们的
     * CHANGING 动画。而该动画默认 {@code mAnimateParentHierarchy=true}，会<b>沿父链一路向上</b>
     * 把每一层祖先的 bounds 也纳入动画——包括本类（RecyclerView 的直接子 view）。
     *
     * <p>它用 ObjectAnimator 直接动 {@code "top"/"bottom"} 属性（即 {@code View.setTop/setBottom}），
     * 绕过 {@code layout()} 与 {@code offsetTopAndBottom()}，把 LayoutManager 刚排好的位置改写成
     * 动画开始时捕获的旧 bounds 并停在那里——表现就是 item 退回上一次布局的位置、压住相邻消息。
     *
     * <p>真机定位过程：LayoutManager 每一代排版的输出都连续（逐出口取样确认），几何流水账里也没有
     * 对应的 layout / offset 记录，而 item 的 frame 确实变了——排除法只剩这一条路，关掉后复现不再
     * 出现。
     *
     * <p>关掉后气泡内部动画照旧，只是不再伸手改祖先的几何。
     */
    public static void disableParentHierarchyTransitions(View root) {
        if (!(root instanceof ViewGroup)) return;
        ViewGroup vg = (ViewGroup) root;
        LayoutTransition transition = vg.getLayoutTransition();
        if (transition != null) {
            transition.setAnimateParentHierarchy(false);
        }
        for (int i = 0, n = vg.getChildCount(); i < n; i++) {
            disableParentHierarchyTransitions(vg.getChildAt(i));
        }
    }

    public void setTouchData(boolean isDelivered, IViewClick iViewClick) {
        this.isDelivered = isDelivered;
        this.iViewClick = iViewClick;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!isDelivered) {
            if (ev.getAction() == MotionEvent.ACTION_UP) {
                iViewClick.onClick();
            }
            return true;
        } else {
            return super.dispatchTouchEvent(ev);
        }
    }

//    @Override
//    public boolean onInterceptTouchEvent(MotionEvent ev) {
//        if (!isDelivered) {
//            if (ev.getAction() == MotionEvent.ACTION_UP) {
//                iViewClick.onClick();
//            }
//            return true;
//        } else {
//            return super.onInterceptTouchEvent(ev);
//        }
//    }

    public interface IViewClick {
        void onClick();
    }
}
