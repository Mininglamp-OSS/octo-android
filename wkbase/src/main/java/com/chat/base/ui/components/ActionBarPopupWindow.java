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
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.chat.base.R;
import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.LayoutHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * A custom PopupWindow that provides animated popup menus with optional
 * swipe-back navigation support.
 *
 * <p>The layout inner class ({@link ActionBarPopupWindowLayout}) manages a
 * vertically scrollable list of menu items with scale/alpha enter/exit
 * animations. Items are added to an internal LinearLayout wrapped in a
 * ScrollView so menus that exceed screen height remain scrollable.</p>
 */
@SuppressLint("SoonBlockedPrivateApi")
public class ActionBarPopupWindow extends PopupWindow {

    // --------------- reflection helpers (scroll-change listener) ---------------

    private static Method layoutInScreenMethod;
    private static final Field superListenerField;

    static {
        Field f = null;
        try {
            f = PopupWindow.class.getDeclaredField("mOnScrollChangedListener");
            f.setAccessible(true);
        } catch (NoSuchFieldException ignored) {
        }
        superListenerField = f;
    }

    private static final ViewTreeObserver.OnScrollChangedListener NOP = () -> { };

    // --------------- instance fields ------------------------------------------

    private static final DecelerateInterpolator DECELERATE = new DecelerateInterpolator();

    private ViewTreeObserver.OnScrollChangedListener mSuperScrollListener;
    private ViewTreeObserver mViewTreeObserver;

    private AnimatorSet windowAnimatorSet;
    private boolean animationEnabled = true;
    private int dismissAnimationDuration = 150;
    private boolean isClosingAnimated;
    private long outEmptyTime = -1;

    @SuppressWarnings("unused")
    private boolean pauseNotifications;

    // --------------- constructors ---------------------------------------------

    public ActionBarPopupWindow() {
        super();
        init();
    }

    public ActionBarPopupWindow(Context context) {
        super(context);
        init();
    }

    public ActionBarPopupWindow(int width, int height) {
        super(width, height);
        init();
    }

    public ActionBarPopupWindow(View contentView) {
        super(contentView);
        init();
    }

    public ActionBarPopupWindow(View contentView, int width, int height, boolean focusable) {
        super(contentView, width, height, focusable);
        init();
    }

    public ActionBarPopupWindow(View contentView, int width, int height) {
        super(contentView, width, height);
        init();
    }

    private void init() {
        if (superListenerField != null) {
            try {
                mSuperScrollListener =
                        (ViewTreeObserver.OnScrollChangedListener) superListenerField.get(this);
                superListenerField.set(this, NOP);
            } catch (Exception e) {
                mSuperScrollListener = null;
            }
        }
    }

    // --------------- public API -----------------------------------------------

    public void setAnimationEnabled(boolean value) {
        animationEnabled = value;
    }

    public void setDismissAnimationDuration(int value) {
        dismissAnimationDuration = value;
    }

    public void setEmptyOutAnimation(long time) {
        outEmptyTime = time;
    }

    public void setPauseNotifications(boolean value) {
        pauseNotifications = value;
    }

    @SuppressWarnings("PrivateAPI")
    public void setLayoutInScreen(boolean value) {
        try {
            if (layoutInScreenMethod == null) {
                layoutInScreenMethod =
                        PopupWindow.class.getDeclaredMethod("setLayoutInScreenEnabled", boolean.class);
                layoutInScreenMethod.setAccessible(true);
            }
            layoutInScreenMethod.invoke(this, value);
        } catch (Exception ignored) {
        }
    }

    // --------------- dim behind -----------------------------------------------

    public void dimBehind() {
        View container = getContentView().getRootView();
        Context context = getContentView().getContext();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams p = (WindowManager.LayoutParams) container.getLayoutParams();
        p.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        p.dimAmount = 0.2f;
        wm.updateViewLayout(container, p);
    }

    private void dismissDim() {
        try {
            View container = getContentView().getRootView();
            if (container.getLayoutParams() instanceof WindowManager.LayoutParams) {
                WindowManager.LayoutParams p = (WindowManager.LayoutParams) container.getLayoutParams();
                if ((p.flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0) {
                    p.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
                    p.dimAmount = 0.0f;
                    Context context = getContentView().getContext();
                    WindowManager wm =
                            (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                    wm.updateViewLayout(container, p);
                }
            }
        } catch (Exception ignored) {
        }
    }

    // --------------- scroll-listener management -------------------------------

    private void registerListener(View anchor) {
        if (mSuperScrollListener != null) {
            ViewTreeObserver vto =
                    (anchor.getWindowToken() != null) ? anchor.getViewTreeObserver() : null;
            if (vto != mViewTreeObserver) {
                if (mViewTreeObserver != null && mViewTreeObserver.isAlive()) {
                    mViewTreeObserver.removeOnScrollChangedListener(mSuperScrollListener);
                }
                if ((mViewTreeObserver = vto) != null) {
                    vto.addOnScrollChangedListener(mSuperScrollListener);
                }
            }
        }
    }

    private void unregisterListener() {
        if (mSuperScrollListener != null && mViewTreeObserver != null) {
            if (mViewTreeObserver.isAlive()) {
                mViewTreeObserver.removeOnScrollChangedListener(mSuperScrollListener);
            }
            mViewTreeObserver = null;
        }
    }

    // --------------- show / update overrides ----------------------------------

    @Override
    public void showAsDropDown(View anchor, int xoff, int yoff) {
        try {
            super.showAsDropDown(anchor, xoff, yoff);
            registerListener(anchor);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void showAtLocation(View parent, int gravity, int x, int y) {
        super.showAtLocation(parent, gravity, x, y);
        unregisterListener();
    }

    @Override
    public void update(View anchor, int xoff, int yoff, int width, int height) {
        super.update(anchor, xoff, yoff, width, height);
        registerListener(anchor);
    }

    @Override
    public void update(View anchor, int width, int height) {
        super.update(anchor, width, height);
        registerListener(anchor);
    }

    // --------------- enter animation ------------------------------------------

    /**
     * Plays a scale + per-item-fade enter animation on the content view.
     * The content view (or its first {@link ActionBarPopupWindowLayout} child)
     * is scaled from 0 to 1 on the Y axis while each menu item fades in
     * sequentially.
     */
    public void startAnimation() {
        if (!animationEnabled || windowAnimatorSet != null) {
            return;
        }

        ActionBarPopupWindowLayout content = findLayout();
        if (content == null) {
            return;
        }

        content.setTranslationY(0);
        content.setAlpha(1.0f);
        content.setPivotX(content.getMeasuredWidth());
        content.setPivotY(0);

        int count = content.getItemsCount();
        content.positions.clear();
        int visibleCount = 0;
        for (int i = 0; i < count; i++) {
            View child = content.getItemAt(i);
            child.setAlpha(0.0f);
            if (child.getVisibility() == View.VISIBLE) {
                content.positions.put(child, visibleCount);
                visibleCount++;
            }
        }

        content.lastStartedChild = content.shownFromBotton ? count - 1 : 0;

        float finalScaleY = 1f;
        if (content.getSwipeBack() != null) {
            content.getSwipeBack().invalidateTransforms();
            finalScaleY = content.backScaleY;
        }

        windowAnimatorSet = new AnimatorSet();
        windowAnimatorSet.playTogether(
                ObjectAnimator.ofFloat(content, "backScaleY", 0.0f, finalScaleY),
                ObjectAnimator.ofInt(content, "backAlpha", 0, 255));
        windowAnimatorSet.setDuration(150 + 16L * visibleCount);

        final ActionBarPopupWindowLayout c = content;
        windowAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                windowAnimatorSet = null;
                int n = c.getItemsCount();
                for (int i = 0; i < n; i++) {
                    View child = c.getItemAt(i);
                    child.setAlpha(child.isEnabled() ? 1f : 0.5f);
                }
            }
        });
        windowAnimatorSet.start();
    }

    // --------------- dismiss --------------------------------------------------

    @Override
    public void dismiss() {
        dismiss(true);
    }

    public void dismiss(boolean animated) {
        setFocusable(false);
        dismissDim();

        if (windowAnimatorSet != null) {
            if (animated && isClosingAnimated) {
                return;
            }
            windowAnimatorSet.cancel();
            windowAnimatorSet = null;
        }
        isClosingAnimated = false;

        if (animationEnabled && animated) {
            isClosingAnimated = true;

            ViewGroup viewGroup = (ViewGroup) getContentView();
            ActionBarPopupWindowLayout content = findLayout();

            // Cancel any running per-item animators
            if (content != null && content.itemAnimators != null && !content.itemAnimators.isEmpty()) {
                for (AnimatorSet a : new ArrayList<>(content.itemAnimators)) {
                    a.removeAllListeners();
                    a.cancel();
                }
                content.itemAnimators.clear();
            }

            windowAnimatorSet = new AnimatorSet();
            if (outEmptyTime > 0) {
                windowAnimatorSet.playTogether(ValueAnimator.ofFloat(0, 1f));
                windowAnimatorSet.setDuration(outEmptyTime);
            } else {
                float ty = AndroidUtilities.dp(
                        (content != null && content.shownFromBotton) ? 5 : -5);
                windowAnimatorSet.playTogether(
                        ObjectAnimator.ofFloat(viewGroup, View.TRANSLATION_Y, ty),
                        ObjectAnimator.ofFloat(viewGroup, View.ALPHA, 0.0f));
                windowAnimatorSet.setDuration(dismissAnimationDuration);
            }

            windowAnimatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    windowAnimatorSet = null;
                    isClosingAnimated = false;
                    setFocusable(false);
                    try {
                        ActionBarPopupWindow.super.dismiss();
                    } catch (Exception ignored) {
                    }
                    unregisterListener();
                }
            });
            windowAnimatorSet.start();
        } else {
            try {
                super.dismiss();
            } catch (Exception ignored) {
            }
            unregisterListener();
        }
    }

    // --------------- helpers --------------------------------------------------

    /**
     * Walks the content view hierarchy to locate the first
     * {@link ActionBarPopupWindowLayout}. Returns the content view itself if it
     * is already a layout, or searches one level of children.
     */
    @Nullable
    private ActionBarPopupWindowLayout findLayout() {
        ViewGroup viewGroup = (ViewGroup) getContentView();
        if (viewGroup instanceof ActionBarPopupWindowLayout) {
            return (ActionBarPopupWindowLayout) viewGroup;
        }
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof ActionBarPopupWindowLayout) {
                return (ActionBarPopupWindowLayout) child;
            }
        }
        return null;
    }

    // =========================================================================
    //  ActionBarPopupWindowLayout
    // =========================================================================

    /**
     * A FrameLayout that acts as the root content of an
     * {@link ActionBarPopupWindow}. It hosts a vertically-oriented
     * LinearLayout inside a ScrollView and draws a rounded background
     * drawable that scales/fades during enter/exit animations.
     */
    public static class ActionBarPopupWindowLayout extends FrameLayout {

        public static final int FLAG_USE_SWIPEBACK = 1;

        // --- public mutable fields (accessed by PopupSwipeBackLayout) ---------
        public boolean updateAnimation;
        public boolean swipeBackGravityRight;

        // --- animation state --------------------------------------------------
        private float backScaleX = 1;
        private float backScaleY = 1;
        private int backAlpha = 255;
        int lastStartedChild = 0;
        boolean shownFromBotton;
        private boolean animationEnabled = true;
        ArrayList<AnimatorSet> itemAnimators;
        HashMap<View, Integer> positions = new HashMap<>();

        // --- background -------------------------------------------------------
        private final Rect bgPaddings = new Rect();
        private int backgroundColor = Color.WHITE;
        protected Drawable backgroundDrawable;

        // --- child views ------------------------------------------------------
        private PopupSwipeBackLayout swipeBackLayout;
        private ScrollView scrollView;
        protected LinearLayout linearLayout;

        // --- callbacks --------------------------------------------------------
        private OnDispatchKeyEventListener mOnDispatchKeyEventListener;
        private onSizeChangedListener onSizeChangedListener;

        // --- fit-items --------------------------------------------------------
        private boolean fitItems;
        private View topView;

        // -----------------------------------------------------------------
        //  Constructor
        // -----------------------------------------------------------------

        /**
         * @param context Android context
         * @param resId   Drawable resource for the rounded background (0 for none)
         * @param flags   Combination of FLAG_* constants
         */
        public ActionBarPopupWindowLayout(Context context, int resId, int flags) {
            super(context);

            if (resId != 0) {
                backgroundDrawable = ContextCompat.getDrawable(context, resId).mutate();
                int pad8 = AndroidUtilities.dp(8);
                setPadding(pad8, pad8, pad8, pad8);
            }
            if (backgroundDrawable != null) {
                backgroundDrawable.getPadding(bgPaddings);
                setBackgroundColor(ContextCompat.getColor(context, R.color.screen_bg));
            }

            setWillNotDraw(false);

            // Optional swipe-back wrapper
            if ((flags & FLAG_USE_SWIPEBACK) != 0) {
                swipeBackLayout = new PopupSwipeBackLayout(context);
                addView(swipeBackLayout,
                        LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }

            // ScrollView for scrollable content
            try {
                scrollView = new ScrollView(context);
                scrollView.setVerticalScrollBarEnabled(false);
                ViewGroup scrollParent = (swipeBackLayout != null) ? swipeBackLayout : this;
                scrollParent.addView(scrollView,
                        LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            } catch (Throwable ignored) {
            }

            // Inner linear layout for menu items
            linearLayout = new LinearLayout(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    if (fitItems) {
                        measureFitItems(widthMeasureSpec, heightMeasureSpec);
                    } else {
                        applyMinWidthTags();
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }

                private void measureFitItems(int wSpec, int hSpec) {
                    int maxWidth = 0;
                    int fixWidth = 0;
                    ArrayList<View> viewsToFix = null;
                    for (int i = 0, n = getChildCount(); i < n; i++) {
                        View v = getChildAt(i);
                        if (v.getVisibility() == GONE) continue;
                        Object tag = v.getTag(R.id.width_tag);
                        Object tag2 = v.getTag(R.id.object_tag);
                        Object fitToWidth = v.getTag(R.id.fit_width_tag);
                        if (tag != null) {
                            v.getLayoutParams().width = LayoutHelper.MATCH_PARENT;
                        }
                        measureChildWithMargins(v, wSpec, 0, hSpec, 0);
                        if (fitToWidth != null) {
                            // keep measured size
                        } else if (!(tag instanceof Integer) && tag2 == null) {
                            maxWidth = Math.max(maxWidth, v.getMeasuredWidth());
                            continue;
                        } else if (tag instanceof Integer) {
                            fixWidth = Math.max((Integer) tag, v.getMeasuredWidth());
                        }
                        if (viewsToFix == null) viewsToFix = new ArrayList<>();
                        viewsToFix.add(v);
                    }
                    if (viewsToFix != null) {
                        int target = Math.max(maxWidth, fixWidth);
                        for (View v : viewsToFix) {
                            v.getLayoutParams().width = target;
                        }
                    }
                }

                private void applyMinWidthTags() {
                    for (int i = 0, n = getChildCount(); i < n; i++) {
                        View v = getChildAt(i);
                        if (v.getVisibility() == GONE) continue;
                        Object tag = v.getTag(R.id.min_width_tag);
                        if (tag instanceof Integer) {
                            v.setMinimumWidth(AndroidUtilities.dp((Integer) tag));
                        }
                    }
                }
            };
            linearLayout.setOrientation(LinearLayout.VERTICAL);

            if (scrollView != null) {
                scrollView.addView(linearLayout,
                        new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));
            } else if (swipeBackLayout != null) {
                swipeBackLayout.addView(linearLayout,
                        LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            } else {
                addView(linearLayout,
                        LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
        }

        // -----------------------------------------------------------------
        //  SwipeBack
        // -----------------------------------------------------------------

        @Nullable
        public PopupSwipeBackLayout getSwipeBack() {
            return swipeBackLayout;
        }

        public int addViewToSwipeBack(View v) {
            swipeBackLayout.addView(v,
                    LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            return swipeBackLayout.getChildCount() - 1;
        }

        public void setSwipeBackForegroundColor(int color) {
            if (swipeBackLayout != null) {
                swipeBackLayout.setForegroundColor(color);
            }
        }

        // -----------------------------------------------------------------
        //  Fit / Bottom / TopView
        // -----------------------------------------------------------------

        public void setFitItems(boolean value) {
            fitItems = value;
        }

        /** Note: the method name preserves the original typo for API compatibility. */
        public void setShownFromBotton(boolean value) {
            shownFromBotton = value;
        }

        public void setTopView(View topView) {
            this.topView = topView;
        }

        // -----------------------------------------------------------------
        //  Key event listener
        // -----------------------------------------------------------------

        public void setDispatchKeyEventListener(OnDispatchKeyEventListener listener) {
            mOnDispatchKeyEventListener = listener;
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (mOnDispatchKeyEventListener != null) {
                mOnDispatchKeyEventListener.onDispatchKeyEvent(event);
            }
            return super.dispatchKeyEvent(event);
        }

        // -----------------------------------------------------------------
        //  Background / alpha
        // -----------------------------------------------------------------

        public int getBackgroundColor() {
            return backgroundColor;
        }

        public void setBackgroundColor(int color) {
            if (backgroundColor != color && backgroundDrawable != null) {
                backgroundColor = color;
                backgroundDrawable.setColorFilter(
                        new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
            }
        }

        @Override
        public void setBackground(Drawable drawable) {
            backgroundColor = Color.WHITE;
            backgroundDrawable = drawable;
            if (backgroundDrawable != null) {
                backgroundDrawable.getPadding(bgPaddings);
            }
        }

        public Drawable getBackgroundDrawable() {
            return backgroundDrawable;
        }

        @Keep
        public void setBackAlpha(int value) {
            backAlpha = value;
        }

        @Keep
        public int getBackAlpha() {
            return backAlpha;
        }

        // -----------------------------------------------------------------
        //  Scale properties (animated via ObjectAnimator)
        // -----------------------------------------------------------------

        @Keep
        public void setBackScaleX(float value) {
            if (backScaleX != value) {
                backScaleX = value;
                backScaleY = value;
                invalidate();
                if (onSizeChangedListener != null) {
                    onSizeChangedListener.onSizeChanged();
                }
            }
        }

        @Keep
        public void setBackScaleY(float value) {
            if (backScaleY != value) {
                backScaleY = value;

                if (animationEnabled && updateAnimation) {
                    revealItems(value);
                }

                invalidate();
                if (onSizeChangedListener != null) {
                    onSizeChangedListener.onSizeChanged();
                }
            }
        }

        public float getBackScaleX() {
            return backScaleX;
        }

        public float getBackScaleY() {
            return backScaleY;
        }

        /**
         * Sequentially reveals menu items as the background scale increases.
         */
        private void revealItems(float scaleY) {
            int height = getMeasuredHeight() - AndroidUtilities.dp(16);
            if (shownFromBotton) {
                for (int i = lastStartedChild; i >= 0; i--) {
                    View child = getItemAt(i);
                    if (child.getVisibility() != VISIBLE) continue;
                    Integer pos = positions.get(child);
                    if (pos != null
                            && height - (pos * AndroidUtilities.dp(48) + AndroidUtilities.dp(32))
                            > scaleY * height) {
                        break;
                    }
                    lastStartedChild = i - 1;
                    startChildAnimation(child);
                }
            } else {
                int count = getItemsCount();
                int h = 0;
                for (int i = 0; i < count; i++) {
                    View child = getItemAt(i);
                    if (child.getVisibility() != VISIBLE) continue;
                    h += child.getMeasuredHeight();
                    if (i < lastStartedChild) continue;
                    Integer pos = positions.get(child);
                    if (pos != null && h - AndroidUtilities.dp(24) > scaleY * height) {
                        break;
                    }
                    lastStartedChild = i + 1;
                    startChildAnimation(child);
                }
            }
        }

        private void startChildAnimation(View child) {
            if (!animationEnabled) return;
            AnimatorSet set = new AnimatorSet();
            set.playTogether(
                    ObjectAnimator.ofFloat(child, View.ALPHA,
                            0f, child.isEnabled() ? 1f : 0.5f),
                    ObjectAnimator.ofFloat(child, View.TRANSLATION_Y,
                            AndroidUtilities.dp(shownFromBotton ? 6 : -6), 0));
            set.setDuration(180);
            set.setInterpolator(DECELERATE);
            set.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (itemAnimators != null) itemAnimators.remove(set);
                }
            });
            set.start();
            if (itemAnimators == null) itemAnimators = new ArrayList<>();
            itemAnimators.add(set);
        }

        public void setAnimationEnabled(boolean value) {
            animationEnabled = value;
        }

        // -----------------------------------------------------------------
        //  Child management (delegates to linearLayout)
        // -----------------------------------------------------------------

        @Override
        public void addView(View child) {
            linearLayout.addView(child);
        }

        public void addView(View child, LinearLayout.LayoutParams layoutParams) {
            linearLayout.addView(child, layoutParams);
        }

        public void removeInnerViews() {
            linearLayout.removeAllViews();
        }

        public int getItemsCount() {
            return linearLayout.getChildCount();
        }

        public View getItemAt(int index) {
            return linearLayout.getChildAt(index);
        }

        public void scrollToTop() {
            if (scrollView != null) {
                scrollView.scrollTo(0, 0);
            }
        }

        // -----------------------------------------------------------------
        //  Radial selectors
        // -----------------------------------------------------------------

        public void setupRadialSelectors(int color) {
            int count = linearLayout.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = linearLayout.getChildAt(i);
                child.setBackground(Theme.createRadSelectorDrawable(
                        color, i == 0 ? 6 : 0, i == count - 1 ? 6 : 0));
            }
        }

        public void updateRadialSelectors() {
            int count = linearLayout.getChildCount();
            View firstVisible = null;
            View lastVisible = null;
            for (int i = 0; i < count; i++) {
                View child = linearLayout.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE) {
                    if (firstVisible == null) firstVisible = child;
                    lastVisible = child;
                }
            }

            boolean prevGap = false;
            for (int i = 0; i < count; i++) {
                View child = linearLayout.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE) continue;
                Object tag = child.getTag(R.id.object_tag);
                if (child instanceof ActionBarMenuSubItem) {
                    ((ActionBarMenuSubItem) child)
                            .updateSelectorBackground(child == firstVisible || prevGap,
                                    child == lastVisible);
                }
                prevGap = tag != null;
            }
        }

        // -----------------------------------------------------------------
        //  Size-change listener / visible height
        // -----------------------------------------------------------------

        public void setOnSizeChangedListener(
                ActionBarPopupWindow.onSizeChangedListener listener) {
            this.onSizeChangedListener = listener;
        }

        public int getVisibleHeight() {
            return (int) (getMeasuredHeight() * backScaleY);
        }

        // -----------------------------------------------------------------
        //  Drawing
        // -----------------------------------------------------------------

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (swipeBackGravityRight && swipeBackLayout != null) {
                setTranslationX(getMeasuredWidth() * (1f - backScaleX));
                if (topView != null) {
                    topView.setTranslationX(getMeasuredWidth() * (1f - backScaleX));
                    topView.setAlpha(1f - swipeBackLayout.transitionProgress);
                    float h = topView.getMeasuredHeight() - AndroidUtilities.dp(16);
                    float yOffset = -h * swipeBackLayout.transitionProgress;
                    topView.setTranslationY(yOffset);
                    setTranslationY(yOffset);
                }
            }
            super.dispatchDraw(canvas);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (backgroundDrawable == null) return;

            if (shownFromBotton) {
                drawFromBottom(canvas);
            } else {
                drawFromTop(canvas);
            }
        }

        private void drawFromBottom(Canvas canvas) {
            int height = getMeasuredHeight();
            backgroundDrawable.setAlpha(backAlpha);
            backgroundDrawable.setBounds(
                    0,
                    (int) (height * (1.0f - backScaleY)),
                    (int) (getMeasuredWidth() * backScaleX),
                    height);
            backgroundDrawable.draw(canvas);
        }

        private void drawFromTop(Canvas canvas) {
            int scrollOffset = (scrollView != null) ? scrollView.getScrollY() : 0;
            int h = (int) (getMeasuredHeight() * backScaleY);
            backgroundDrawable.setAlpha(backAlpha);
            backgroundDrawable.setBounds(
                    0,
                    -scrollOffset,
                    (int) (getMeasuredWidth() * backScaleX),
                    h);
            backgroundDrawable.draw(canvas);
        }
    }

    // =========================================================================
    //  Interfaces
    // =========================================================================

    /** Listener for key events dispatched to the popup layout. */
    public interface OnDispatchKeyEventListener {
        void onDispatchKeyEvent(KeyEvent keyEvent);
    }

    /** Callback fired when the visible popup size changes during animation. */
    public interface onSizeChangedListener {
        void onSizeChanged();
    }
}
