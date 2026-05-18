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
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.chat.base.R;
import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.LayoutHelper;

import java.util.ArrayList;

/**
 * Custom AlertDialog that follows the app's design language.
 * Provides a Builder pattern for constructing dialogs with titles, messages,
 * custom views, item lists, and configurable buttons.
 */
public class AlertDialog extends Dialog implements Drawable.Callback {

    // Dialog type constants
    public static final int ALERT_TYPE_MESSAGE = 0;
    public static final int ALERT_TYPE_LOADING = 2;
    public static final int ALERT_TYPE_SPINNER = 3;

    // Views
    private View customView;
    private int customViewHeight = LayoutHelper.WRAP_CONTENT;
    private TextView titleTextView;
    private TextView secondTitleTextView;
    private TextView subtitleTextView;
    private TextView messageTextView;
    private FrameLayout progressViewContainer;
    private FrameLayout titleContainer;
    private TextView progressViewTextView;
    private ScrollView contentScrollView;
    private LinearLayout scrollContainer;
    private ViewTreeObserver.OnScrollChangedListener onScrollChangedListener;
    private BitmapDrawable[] shadow = new BitmapDrawable[2];
    private boolean[] shadowVisibility = new boolean[2];
    private AnimatorSet[] shadowAnimation = new AnimatorSet[2];
    private int customViewOffset = 12;

    // Listeners
    private OnCancelListener onCancelListener;
    private OnClickListener onClickListener;
    private OnDismissListener onDismissListener;
    private OnClickListener onBackButtonListener;

    // Cancel dialog for spinner mode
    private AlertDialog cancelDialog;

    // Layout tracking
    private int lastScreenWidth;

    // Content data
    private CharSequence[] items;
    private int[] itemIcons;
    private CharSequence title;
    private CharSequence secondTitle;
    private CharSequence subtitle;
    private CharSequence message;
    private int progressViewStyle;
    private int currentProgress;

    // Top area configuration
    private int topResId;
    private View topView;
    private int topHeight = 132;
    private Drawable topDrawable;
    private int topBackgroundColor;
    private boolean topAnimationIsNew;
    private int topAnimationId;
    private int topAnimationSize;
    private boolean topAnimationAutoRepeat = true;

    // Behavior flags
    private boolean messageTextViewClickable = true;
    private boolean canCancel = true;
    private boolean dismissDialogByButtons = true;
    private boolean drawBackground;
    private boolean notDrawBackgroundOnTopView;
    private boolean checkFocusable = true;
    private boolean focusable;
    private boolean verticalButtons;
    private float aspectRatio;

    // Button data
    private CharSequence positiveButtonText;
    private OnClickListener positiveButtonListener;
    private CharSequence negativeButtonText;
    private OnClickListener negativeButtonListener;
    private CharSequence neutralButtonText;
    private OnClickListener neutralButtonListener;
    protected ViewGroup buttonsLayout;

    // Progress views
    private LineProgressView lineProgressView;
    private TextView lineProgressViewPercent;

    // Visual configuration
    private boolean dimEnabled = true;
    private float dimAlpha = 0.5f;
    private boolean dimCustom = false;
    private int backgroundColor;
    private Drawable shadowDrawable;
    private Rect backgroundPaddings;
    private int additionalHorizontalPadding;

    // Blur parameters (kept as no-ops for API compat; native blur handled by window flags)
    private boolean blurredBackground;
    private boolean blurredNativeBackground;
    float blurAlpha = 0.8f;
    private boolean blurBehind;
    private float blurOpacity;

    private final Runnable dismissRunnable = this::dismiss;
    private final Runnable showRunnable = () -> {
        if (isShowing()) {
            return;
        }
        try {
            show();
        } catch (Exception ignore) {
        }
    };

    private final ArrayList<AlertDialogCell> itemViews = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Public API: blur params (callers invoke this on every dialog)
    // -----------------------------------------------------------------------

    public void setBlurParams(float blurAlpha, boolean blurBehind, boolean blurBackground) {
        this.blurAlpha = blurAlpha;
        this.blurBehind = blurBehind;
        this.blurredBackground = blurBackground;
    }

    public void redPositive() {
        TextView button = (TextView) getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(ContextCompat.getColor(getContext(), R.color.red));
        }
    }

    // -----------------------------------------------------------------------
    // AlertDialogCell - item row in the dialog
    // -----------------------------------------------------------------------

    public static class AlertDialogCell extends FrameLayout {

        private final TextView textView;
        private final ImageView imageView;

        public AlertDialogCell(Context context) {
            super(context);

            setBackground(Theme.createSelectorDrawable(ContextCompat.getColor(getContext(), R.color.screen_bg), 2));
            setPadding(AndroidUtilities.dp(23), 0, AndroidUtilities.dp(23), 0);

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(getContext(), R.color.dialogText), PorterDuff.Mode.MULTIPLY));
            addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40, Gravity.CENTER_VERTICAL | (AndroidUtilities.isRTL ? Gravity.RIGHT : Gravity.LEFT)));

            textView = new TextView(context);
            textView.setLines(1);
            textView.setSingleLine(true);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setTextColor(ContextCompat.getColor(getContext(), R.color.dialogText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (AndroidUtilities.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
        }

        public void setTextColor(int color) {
            textView.setTextColor(color);
        }

        public void setGravity(int gravity) {
            textView.setGravity(gravity);
        }

        public void setTextAndIcon(CharSequence text, int icon) {
            textView.setText(text);
            if (icon != 0) {
                imageView.setImageResource(icon);
                imageView.setVisibility(VISIBLE);
                textView.setPadding(AndroidUtilities.isRTL ? 0 : AndroidUtilities.dp(56), 0, AndroidUtilities.isRTL ? AndroidUtilities.dp(56) : 0, 0);
            } else {
                imageView.setVisibility(INVISIBLE);
                textView.setPadding(0, 0, 0, 0);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public AlertDialog(Context context, int progressStyle) {
        super(context, R.style.TransparentDialog);

        blurredNativeBackground = supportsNativeBlur() && progressStyle == ALERT_TYPE_MESSAGE;
        backgroundColor = ContextCompat.getColor(context, R.color.screen_bg);
        final boolean isDark = Theme.isDark();
        blurredBackground = blurredNativeBackground || (!supportsNativeBlur() && isDark);

        backgroundPaddings = new Rect();
        if (progressStyle != ALERT_TYPE_SPINNER || blurredBackground) {
            shadowDrawable = ContextCompat.getDrawable(context, R.mipmap.popup_fixed_alert3);
            blurOpacity = progressStyle == ALERT_TYPE_SPINNER ? 0.55f : (isDark ? 0.80f : 0.985f);
            if (shadowDrawable != null) {
                shadowDrawable.setColorFilter(new PorterDuffColorFilter(backgroundColor, PorterDuff.Mode.MULTIPLY));
                shadowDrawable.getPadding(backgroundPaddings);
            }
        }

        progressViewStyle = progressStyle;
    }

    private boolean supportsNativeBlur() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void show() {
        super.show();
        if (progressViewContainer != null && progressViewStyle == ALERT_TYPE_SPINNER) {
            progressViewContainer.setScaleX(0);
            progressViewContainer.setScaleY(0);
            progressViewContainer.animate()
                    .scaleX(1f).scaleY(1f)
                    .setInterpolator(new OvershootInterpolator(1.3f))
                    .setDuration(190)
                    .start();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout containerView = new LinearLayout(getContext()) {

            private boolean inLayout;
            private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (progressViewStyle == ALERT_TYPE_SPINNER) {
                    showCancelAlert();
                    return false;
                }
                return super.onTouchEvent(event);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (progressViewStyle == ALERT_TYPE_SPINNER) {
                    showCancelAlert();
                    return false;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (progressViewStyle == ALERT_TYPE_SPINNER) {
                    progressViewContainer.measure(
                            MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(86), MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(86), MeasureSpec.EXACTLY));
                    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
                } else {
                    inLayout = true;
                    int width = MeasureSpec.getSize(widthMeasureSpec);
                    int height = MeasureSpec.getSize(heightMeasureSpec);
                    int maxContentHeight;
                    int availableHeight = maxContentHeight = height - getPaddingTop() - getPaddingBottom();
                    int availableWidth = width - getPaddingLeft() - getPaddingRight();

                    int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(availableWidth - AndroidUtilities.dp(48), MeasureSpec.EXACTLY);
                    int childFullWidthMeasureSpec = MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.EXACTLY);
                    LayoutParams layoutParams;

                    if (buttonsLayout != null) {
                        int count = buttonsLayout.getChildCount();
                        for (int a = 0; a < count; a++) {
                            View child = buttonsLayout.getChildAt(a);
                            if (child instanceof TextView) {
                                ((TextView) child).setMaxWidth(AndroidUtilities.dp((availableWidth - AndroidUtilities.dp(24)) / 2));
                            }
                        }
                        buttonsLayout.measure(childFullWidthMeasureSpec, heightMeasureSpec);
                        layoutParams = (LayoutParams) buttonsLayout.getLayoutParams();
                        availableHeight -= buttonsLayout.getMeasuredHeight() + layoutParams.bottomMargin + layoutParams.topMargin;
                    }
                    if (secondTitleTextView != null) {
                        secondTitleTextView.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(childWidthMeasureSpec), MeasureSpec.AT_MOST), heightMeasureSpec);
                    }
                    if (titleTextView != null) {
                        if (secondTitleTextView != null) {
                            titleTextView.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(childWidthMeasureSpec) - secondTitleTextView.getMeasuredWidth() - AndroidUtilities.dp(8), MeasureSpec.EXACTLY), heightMeasureSpec);
                        } else {
                            titleTextView.measure(childWidthMeasureSpec, heightMeasureSpec);
                        }
                    }
                    if (titleContainer != null) {
                        titleContainer.measure(childWidthMeasureSpec, heightMeasureSpec);
                        layoutParams = (LayoutParams) titleContainer.getLayoutParams();
                        availableHeight -= titleContainer.getMeasuredHeight() + layoutParams.bottomMargin + layoutParams.topMargin;
                    }
                    if (subtitleTextView != null) {
                        subtitleTextView.measure(childWidthMeasureSpec, heightMeasureSpec);
                        layoutParams = (LayoutParams) subtitleTextView.getLayoutParams();
                        availableHeight -= subtitleTextView.getMeasuredHeight() + layoutParams.bottomMargin + layoutParams.topMargin;
                    }
                    if (topView != null) {
                        int w = width;
                        int h;
                        if (aspectRatio == 0) {
                            float scale = w / 936.0f;
                            h = (int) (354 * scale);
                        } else {
                            h = (int) (w * aspectRatio);
                        }
                        topView.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
                        topView.getLayoutParams().height = h;
                        availableHeight -= topView.getMeasuredHeight();
                    }
                    if (progressViewStyle == ALERT_TYPE_MESSAGE) {
                        layoutParams = (LayoutParams) contentScrollView.getLayoutParams();

                        if (customView != null) {
                            layoutParams.topMargin = titleTextView == null && messageTextView.getVisibility() == GONE && items == null ? AndroidUtilities.dp(16) : 0;
                            layoutParams.bottomMargin = buttonsLayout == null ? AndroidUtilities.dp(8) : 0;
                        } else if (items != null) {
                            layoutParams.topMargin = titleTextView == null && messageTextView.getVisibility() == GONE ? AndroidUtilities.dp(8) : 0;
                            layoutParams.bottomMargin = AndroidUtilities.dp(8);
                        } else if (messageTextView.getVisibility() == VISIBLE) {
                            layoutParams.topMargin = titleTextView == null ? AndroidUtilities.dp(19) : 0;
                            layoutParams.bottomMargin = AndroidUtilities.dp(20);
                        }

                        availableHeight -= layoutParams.bottomMargin + layoutParams.topMargin;
                        contentScrollView.measure(childFullWidthMeasureSpec, MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.AT_MOST));
                        availableHeight -= contentScrollView.getMeasuredHeight();
                    } else {
                        if (progressViewContainer != null) {
                            progressViewContainer.measure(childWidthMeasureSpec, MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.AT_MOST));
                            layoutParams = (LayoutParams) progressViewContainer.getLayoutParams();
                            availableHeight -= progressViewContainer.getMeasuredHeight() + layoutParams.bottomMargin + layoutParams.topMargin;
                        } else if (messageTextView != null) {
                            messageTextView.measure(childWidthMeasureSpec, MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.AT_MOST));
                            if (messageTextView.getVisibility() != GONE) {
                                layoutParams = (LayoutParams) messageTextView.getLayoutParams();
                                availableHeight -= messageTextView.getMeasuredHeight() + layoutParams.bottomMargin + layoutParams.topMargin;
                            }
                        }
                        if (lineProgressView != null) {
                            lineProgressView.measure(childWidthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(4), MeasureSpec.EXACTLY));
                            layoutParams = (LayoutParams) lineProgressView.getLayoutParams();
                            availableHeight -= lineProgressView.getMeasuredHeight() + layoutParams.bottomMargin + layoutParams.topMargin;

                            lineProgressViewPercent.measure(childWidthMeasureSpec, MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.AT_MOST));
                            layoutParams = (LayoutParams) lineProgressViewPercent.getLayoutParams();
                            availableHeight -= lineProgressViewPercent.getMeasuredHeight() + layoutParams.bottomMargin + layoutParams.topMargin;
                        }
                    }

                    setMeasuredDimension(width, maxContentHeight - availableHeight + getPaddingTop() + getPaddingBottom() - (topAnimationIsNew ? AndroidUtilities.dp(8) : 0));
                    inLayout = false;

                    if (lastScreenWidth != AndroidUtilities.getScreenWidth()) {
                        AndroidUtilities.runOnUIThread(() -> {
                            lastScreenWidth = AndroidUtilities.getScreenWidth();
                            final int calculatedWidth = AndroidUtilities.getScreenWidth() - AndroidUtilities.dp(56);
                            int maxWidth;
                            if (AndroidUtilities.isTablet()) {
                                if (AndroidUtilities.isSmallTablet()) {
                                    maxWidth = AndroidUtilities.dp(446);
                                } else {
                                    maxWidth = AndroidUtilities.dp(496);
                                }
                            } else {
                                maxWidth = AndroidUtilities.dp(356);
                            }

                            Window window = getWindow();
                            if (window != null) {
                                WindowManager.LayoutParams params = new WindowManager.LayoutParams();
                                params.copyFrom(window.getAttributes());
                                params.width = Math.min(maxWidth, calculatedWidth) + backgroundPaddings.left + backgroundPaddings.right;
                                try {
                                    window.setAttributes(params);
                                } catch (Throwable ignored) {
                                }
                            }
                        });
                    }
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                if (progressViewStyle == ALERT_TYPE_SPINNER) {
                    int x = (r - l - progressViewContainer.getMeasuredWidth()) / 2;
                    int y = (b - t - progressViewContainer.getMeasuredHeight()) / 2;
                    progressViewContainer.layout(x, y, x + progressViewContainer.getMeasuredWidth(), y + progressViewContainer.getMeasuredHeight());
                } else if (contentScrollView != null) {
                    if (onScrollChangedListener == null) {
                        onScrollChangedListener = () -> {
                            runShadowAnimation(0, titleTextView != null && contentScrollView.getScrollY() > scrollContainer.getTop());
                            runShadowAnimation(1, buttonsLayout != null && contentScrollView.getScrollY() + contentScrollView.getHeight() < scrollContainer.getBottom());
                            contentScrollView.invalidate();
                        };
                        contentScrollView.getViewTreeObserver().addOnScrollChangedListener(onScrollChangedListener);
                    }
                    onScrollChangedListener.onScrollChanged();
                }
            }

            @Override
            public void requestLayout() {
                if (inLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }

            @Override
            public void draw(Canvas canvas) {
                if (blurredBackground && !blurredNativeBackground) {
                    float r;
                    if (progressViewStyle == ALERT_TYPE_SPINNER && progressViewContainer != null) {
                        r = AndroidUtilities.dp(18);
                        float w = progressViewContainer.getWidth() * progressViewContainer.getScaleX();
                        float h = progressViewContainer.getHeight() * progressViewContainer.getScaleY();
                        AndroidUtilities.rectTmp.set(
                                (getWidth() - w) / 2f,
                                (getHeight() - h) / 2f,
                                (getWidth() + w) / 2f,
                                (getHeight() + h) / 2f);
                    } else {
                        r = AndroidUtilities.dp(10);
                        AndroidUtilities.rectTmp.set(getPaddingLeft(), getPaddingTop(), getMeasuredWidth() - getPaddingRight(), getMeasuredHeight() - getPaddingBottom());
                    }
                    // Draw background color with blur opacity
                    backgroundPaint.setColor(backgroundColor);
                    backgroundPaint.setAlpha((int) (backgroundPaint.getAlpha() * blurOpacity));
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, backgroundPaint);
                }
                super.draw(canvas);
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (drawBackground && !blurredBackground && shadowDrawable != null) {
                    shadowDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                    if (topView != null && notDrawBackgroundOnTopView) {
                        int clipTop = topView.getBottom();
                        canvas.save();
                        canvas.clipRect(0, clipTop, getMeasuredWidth(), getMeasuredHeight());
                        shadowDrawable.draw(canvas);
                        canvas.restore();
                    } else {
                        shadowDrawable.draw(canvas);
                    }
                }
                super.dispatchDraw(canvas);
            }
        };
        containerView.setOrientation(LinearLayout.VERTICAL);
        if ((blurredBackground || progressViewStyle == ALERT_TYPE_SPINNER) && progressViewStyle != ALERT_TYPE_LOADING) {
            containerView.setBackground(null);
            containerView.setPadding(0, 0, 0, 0);
            if (blurredBackground && !blurredNativeBackground) {
                containerView.setWillNotDraw(false);
            }
            drawBackground = false;
        } else {
            if (notDrawBackgroundOnTopView && shadowDrawable != null) {
                Rect rect = new Rect();
                shadowDrawable.getPadding(rect);
                containerView.setPadding(rect.left, rect.top, rect.right, rect.bottom);
                drawBackground = true;
            } else {
                containerView.setBackground(shadowDrawable);
                drawBackground = false;
            }
        }
        containerView.setFitsSystemWindows(Build.VERSION.SDK_INT >= 21);
        setContentView(containerView);

        final boolean hasButtons = positiveButtonText != null || negativeButtonText != null || neutralButtonText != null;

        // Top image / drawable
        if (topResId != 0 || topDrawable != null) {
            ImageView topImageView = new ImageView(getContext());
            if (topDrawable != null) {
                topImageView.setImageDrawable(topDrawable);
            } else {
                topImageView.setImageResource(topResId);
            }
            topImageView.setScaleType(ImageView.ScaleType.CENTER);
            topImageView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), 0, topBackgroundColor));
            topImageView.setPadding(0, 0, 0, 0);
            containerView.addView(topImageView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, topHeight, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));
        } else if (topView != null) {
            topView.setPadding(0, 0, 0, 0);
            containerView.addView(topView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, topHeight, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));
        }

        // Title
        if (title != null) {
            titleContainer = new FrameLayout(getContext());
            containerView.addView(titleContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, topAnimationIsNew ? Gravity.CENTER_HORIZONTAL : 0, 24, 0, 24, 0));

            titleTextView = new TextView(getContext());
            titleTextView.setText(title);
            titleTextView.setTextColor(ContextCompat.getColor(getContext(), R.color.colorDark));
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleTextView.setGravity((topAnimationIsNew ? Gravity.CENTER_HORIZONTAL : AndroidUtilities.isRTL ? Gravity.END : Gravity.START) | Gravity.TOP);
            titleContainer.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (topAnimationIsNew ? Gravity.CENTER_HORIZONTAL : AndroidUtilities.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 0, 19, 0, topAnimationIsNew ? 4 : (subtitle != null ? 2 : (items != null ? 14 : 10))));
        }

        // Second title (beside the main title)
        if (secondTitle != null && title != null) {
            secondTitleTextView = new TextView(getContext());
            secondTitleTextView.setText(secondTitle);
            secondTitleTextView.setTextColor(ContextCompat.getColor(getContext(), R.color.dialogText));
            secondTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            secondTitleTextView.setGravity((AndroidUtilities.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
            titleContainer.addView(secondTitleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (AndroidUtilities.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 0, 21, 0, 0));
        }

        // Subtitle
        if (subtitle != null) {
            subtitleTextView = new TextView(getContext());
            subtitleTextView.setText(subtitle);
            subtitleTextView.setTextColor(ContextCompat.getColor(getContext(), R.color.dialogText));
            subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            subtitleTextView.setGravity((AndroidUtilities.isRTL ? Gravity.END : Gravity.START) | Gravity.TOP);
            containerView.addView(subtitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (AndroidUtilities.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 0, 24, items != null ? 14 : 10));
        }

        // Scroll content area for MESSAGE type
        if (progressViewStyle == ALERT_TYPE_MESSAGE) {
            shadow[0] = (BitmapDrawable) ContextCompat.getDrawable(getContext(), R.mipmap.header_shadow).mutate();
            shadow[1] = (BitmapDrawable) ContextCompat.getDrawable(getContext(), R.mipmap.header_shadow_reverse).mutate();
            shadow[0].setAlpha(0);
            shadow[1].setAlpha(0);
            shadow[0].setCallback(this);
            shadow[1].setCallback(this);

            contentScrollView = new ScrollView(getContext()) {
                @Override
                protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                    boolean result = super.drawChild(canvas, child, drawingTime);
                    if (shadow[0].getPaint().getAlpha() != 0) {
                        shadow[0].setBounds(0, getScrollY(), getMeasuredWidth(), getScrollY() + AndroidUtilities.dp(3));
                        shadow[0].draw(canvas);
                    }
                    if (shadow[1].getPaint().getAlpha() != 0) {
                        shadow[1].setBounds(0, getScrollY() + getMeasuredHeight() - AndroidUtilities.dp(3), getMeasuredWidth(), getScrollY() + getMeasuredHeight());
                        shadow[1].draw(canvas);
                    }
                    return result;
                }
            };
            contentScrollView.setVerticalScrollBarEnabled(false);
            AndroidUtilities.setScrollViewEdgeEffectColor(contentScrollView, ContextCompat.getColor(getContext(), R.color.color999));
            containerView.addView(contentScrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

            scrollContainer = new LinearLayout(getContext());
            scrollContainer.setOrientation(LinearLayout.VERTICAL);
            contentScrollView.addView(scrollContainer, new ScrollView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        // Message text
        messageTextView = new TextView(getContext());
        messageTextView.setTextColor(ContextCompat.getColor(getContext(), R.color.dialogText));
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        messageTextView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        messageTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        messageTextView.setLinkTextColor(ContextCompat.getColor(getContext(), R.color.colorAccent));
        if (!messageTextViewClickable) {
            messageTextView.setClickable(false);
            messageTextView.setEnabled(false);
        }
        messageTextView.setGravity((topAnimationIsNew ? Gravity.CENTER_HORIZONTAL : AndroidUtilities.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);

        if (progressViewStyle == ALERT_TYPE_LOADING) {
            containerView.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (AndroidUtilities.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, title == null ? 19 : 0, 24, 20));

            lineProgressView = new LineProgressView(getContext());
            lineProgressView.setProgress(currentProgress / 100.0f, false);
            lineProgressView.setProgressColor(Theme.colorAccount);
            lineProgressView.setBackColor(ContextCompat.getColor(getContext(), R.color.screen_bg));
            containerView.addView(lineProgressView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 4, Gravity.LEFT | Gravity.CENTER_VERTICAL, 24, 0, 24, 0));

            lineProgressViewPercent = new TextView(getContext());
            lineProgressViewPercent.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            lineProgressViewPercent.setGravity((AndroidUtilities.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            lineProgressViewPercent.setTextColor(ContextCompat.getColor(getContext(), R.color.color999));
            lineProgressViewPercent.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            containerView.addView(lineProgressViewPercent, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (AndroidUtilities.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 23, 4, 23, 24));
            updateLineProgressTextView();
        } else if (progressViewStyle == ALERT_TYPE_SPINNER) {
            setCanceledOnTouchOutside(false);
            setCancelable(false);

            progressViewContainer = new FrameLayout(getContext());
            backgroundColor = ContextCompat.getColor(getContext(), R.color.screen_bg);
            if (!(blurredBackground && !blurredNativeBackground)) {
                progressViewContainer.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), backgroundColor));
            }
            containerView.addView(progressViewContainer, LayoutHelper.createLinear(86, 86, Gravity.CENTER));

            RadialProgressView progressView = new RadialProgressView(getContext());
            progressView.setSize(AndroidUtilities.dp(32));
            progressView.setProgressColor(ContextCompat.getColor(getContext(), R.color.colorAccent));
            progressViewContainer.addView(progressView, LayoutHelper.createFrame(86, 86, Gravity.CENTER));
        } else {
            scrollContainer.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (topAnimationIsNew ? Gravity.CENTER_HORIZONTAL : AndroidUtilities.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 0, 24, customView != null || items != null ? customViewOffset : 0));
        }
        if (!TextUtils.isEmpty(message)) {
            messageTextView.setText(message);
            messageTextView.setVisibility(View.VISIBLE);
        } else {
            messageTextView.setVisibility(View.GONE);
        }

        // Item list
        if (items != null) {
            for (int a = 0; a < items.length; a++) {
                if (items[a] == null) {
                    continue;
                }
                AlertDialogCell cell = new AlertDialogCell(getContext());
                cell.setTextAndIcon(items[a], itemIcons != null ? itemIcons[a] : 0);
                cell.setTag(a);
                itemViews.add(cell);
                scrollContainer.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                cell.setOnClickListener(v -> {
                    if (onClickListener != null) {
                        onClickListener.onClick(AlertDialog.this, (Integer) v.getTag());
                    }
                    dismiss();
                });
            }
        }

        // Custom view
        if (customView != null) {
            if (customView.getParent() != null) {
                ViewGroup viewGroup = (ViewGroup) customView.getParent();
                viewGroup.removeView(customView);
            }
            scrollContainer.addView(customView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, customViewHeight));
        }

        // Buttons
        if (hasButtons) {
            if (!verticalButtons) {
                int buttonsWidth = 0;
                TextPaint paint = new TextPaint();
                paint.setTextSize(AndroidUtilities.dp(14));
                if (positiveButtonText != null) {
                    buttonsWidth += paint.measureText(positiveButtonText, 0, positiveButtonText.length()) + AndroidUtilities.dp(10);
                }
                if (negativeButtonText != null) {
                    buttonsWidth += paint.measureText(negativeButtonText, 0, negativeButtonText.length()) + AndroidUtilities.dp(10);
                }
                if (neutralButtonText != null) {
                    buttonsWidth += paint.measureText(neutralButtonText, 0, neutralButtonText.length()) + AndroidUtilities.dp(10);
                }
                if (buttonsWidth > AndroidUtilities.getScreenWidth() - AndroidUtilities.dp(110)) {
                    verticalButtons = true;
                }
            }
            if (verticalButtons) {
                LinearLayout linearLayout = new LinearLayout(getContext());
                linearLayout.setOrientation(LinearLayout.VERTICAL);
                buttonsLayout = linearLayout;
            } else {
                buttonsLayout = new FrameLayout(getContext()) {
                    @Override
                    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                        int count = getChildCount();
                        View positiveButton = null;
                        int width = right - left;
                        for (int a = 0; a < count; a++) {
                            View child = getChildAt(a);
                            Integer tag = (Integer) child.getTag();
                            if (tag != null) {
                                if (tag == Dialog.BUTTON_POSITIVE) {
                                    positiveButton = child;
                                    if (AndroidUtilities.isRTL) {
                                        child.layout(getPaddingLeft(), getPaddingTop(), getPaddingLeft() + child.getMeasuredWidth(), getPaddingTop() + child.getMeasuredHeight());
                                    } else {
                                        child.layout(width - getPaddingRight() - child.getMeasuredWidth(), getPaddingTop(), width - getPaddingRight(), getPaddingTop() + child.getMeasuredHeight());
                                    }
                                } else if (tag == Dialog.BUTTON_NEGATIVE) {
                                    if (AndroidUtilities.isRTL) {
                                        int x = getPaddingLeft();
                                        if (positiveButton != null) {
                                            x += positiveButton.getMeasuredWidth() + AndroidUtilities.dp(8);
                                        }
                                        child.layout(x, getPaddingTop(), x + child.getMeasuredWidth(), getPaddingTop() + child.getMeasuredHeight());
                                    } else {
                                        int x = width - getPaddingRight() - child.getMeasuredWidth();
                                        if (positiveButton != null) {
                                            x -= positiveButton.getMeasuredWidth() + AndroidUtilities.dp(8);
                                        }
                                        child.layout(x, getPaddingTop(), x + child.getMeasuredWidth(), getPaddingTop() + child.getMeasuredHeight());
                                    }
                                } else if (tag == Dialog.BUTTON_NEUTRAL) {
                                    if (AndroidUtilities.isRTL) {
                                        child.layout(width - getPaddingRight() - child.getMeasuredWidth(), getPaddingTop(), width - getPaddingRight(), getPaddingTop() + child.getMeasuredHeight());
                                    } else {
                                        child.layout(getPaddingLeft(), getPaddingTop(), getPaddingLeft() + child.getMeasuredWidth(), getPaddingTop() + child.getMeasuredHeight());
                                    }
                                }
                            } else {
                                int w = child.getMeasuredWidth();
                                int h = child.getMeasuredHeight();
                                int cl, ct;
                                if (positiveButton != null) {
                                    cl = positiveButton.getLeft() + (positiveButton.getMeasuredWidth() - w) / 2;
                                    ct = positiveButton.getTop() + (positiveButton.getMeasuredHeight() - h) / 2;
                                } else {
                                    cl = ct = 0;
                                }
                                child.layout(cl, ct, cl + w, ct + h);
                            }
                        }
                    }

                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

                        int totalWidth = 0;
                        int availableWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
                        int count = getChildCount();
                        for (int a = 0; a < count; a++) {
                            View child = getChildAt(a);
                            if (child instanceof TextView && child.getTag() != null) {
                                totalWidth += child.getMeasuredWidth();
                            }
                        }
                        if (totalWidth > availableWidth) {
                            View negative = findViewWithTag(BUTTON_NEGATIVE);
                            View neutral = findViewWithTag(BUTTON_NEUTRAL);
                            if (negative != null && neutral != null) {
                                if (negative.getMeasuredWidth() < neutral.getMeasuredWidth()) {
                                    neutral.measure(MeasureSpec.makeMeasureSpec(neutral.getMeasuredWidth() - (totalWidth - availableWidth), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(neutral.getMeasuredHeight(), MeasureSpec.EXACTLY));
                                } else {
                                    negative.measure(MeasureSpec.makeMeasureSpec(negative.getMeasuredWidth() - (totalWidth - availableWidth), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(negative.getMeasuredHeight(), MeasureSpec.EXACTLY));
                                }
                            }
                        }
                    }
                };
            }
            buttonsLayout.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
            containerView.addView(buttonsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));
            if (topAnimationIsNew) {
                buttonsLayout.setTranslationY(-AndroidUtilities.dp(8));
            }

            // Positive button
            if (positiveButtonText != null) {
                TextView textView = createButtonTextView();
                textView.setMinWidth(AndroidUtilities.dp(64));
                textView.setTag(Dialog.BUTTON_POSITIVE);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                textView.setTextColor(ContextCompat.getColor(getContext(), R.color.dialogText));
                textView.setGravity(Gravity.CENTER);
                textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                textView.setText(positiveButtonText.toString());
                textView.setBackground(Theme.getRoundRectSelectorDrawable(AndroidUtilities.dp(6), ContextCompat.getColor(getContext(), R.color.dialogText)));
                textView.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
                if (verticalButtons) {
                    buttonsLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, AndroidUtilities.isRTL ? Gravity.START : Gravity.END));
                } else {
                    buttonsLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP | Gravity.END));
                }
                textView.setOnClickListener(v -> {
                    if (positiveButtonListener != null) {
                        positiveButtonListener.onClick(AlertDialog.this, Dialog.BUTTON_POSITIVE);
                    }
                    if (dismissDialogByButtons) {
                        dismiss();
                    }
                });
            }

            // Negative button
            if (negativeButtonText != null) {
                TextView textView = createButtonTextView();
                textView.setMinWidth(AndroidUtilities.dp(64));
                textView.setTag(Dialog.BUTTON_NEGATIVE);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                textView.setTextColor(ContextCompat.getColor(getContext(), R.color.dialogText));
                textView.setGravity(Gravity.CENTER);
                textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setSingleLine(true);
                textView.setText(negativeButtonText.toString());
                textView.setBackground(Theme.getRoundRectSelectorDrawable(AndroidUtilities.dp(6), ContextCompat.getColor(getContext(), R.color.dialogText)));
                textView.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
                if (verticalButtons) {
                    buttonsLayout.addView(textView, 0, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, AndroidUtilities.isRTL ? Gravity.LEFT : Gravity.RIGHT));
                } else {
                    buttonsLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP | Gravity.RIGHT));
                }
                textView.setOnClickListener(v -> {
                    if (negativeButtonListener != null) {
                        negativeButtonListener.onClick(AlertDialog.this, Dialog.BUTTON_NEGATIVE);
                    }
                    if (dismissDialogByButtons) {
                        cancel();
                    }
                });
            }

            // Neutral button
            if (neutralButtonText != null) {
                TextView textView = createButtonTextView();
                textView.setMinWidth(AndroidUtilities.dp(64));
                textView.setTag(Dialog.BUTTON_NEUTRAL);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                textView.setTextColor(ContextCompat.getColor(getContext(), R.color.dialogText));
                textView.setGravity(Gravity.CENTER);
                textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setSingleLine(true);
                textView.setText(neutralButtonText.toString());
                textView.setBackground(Theme.getRoundRectSelectorDrawable(AndroidUtilities.dp(6), ContextCompat.getColor(getContext(), R.color.dialogText)));
                textView.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
                if (verticalButtons) {
                    buttonsLayout.addView(textView, 1, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, AndroidUtilities.isRTL ? Gravity.START : Gravity.END));
                } else {
                    buttonsLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP | Gravity.START));
                }
                textView.setOnClickListener(v -> {
                    if (neutralButtonListener != null) {
                        neutralButtonListener.onClick(AlertDialog.this, Dialog.BUTTON_NEUTRAL);
                    }
                    if (dismissDialogByButtons) {
                        dismiss();
                    }
                });
            }

            if (verticalButtons) {
                for (int i = 1; i < buttonsLayout.getChildCount(); i++) {
                    ((ViewGroup.MarginLayoutParams) buttonsLayout.getChildAt(i).getLayoutParams()).topMargin = AndroidUtilities.dp(6);
                }
            }
        }

        // Window configuration
        Window window = getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(window.getAttributes());
            if (progressViewStyle == ALERT_TYPE_SPINNER) {
                params.width = WindowManager.LayoutParams.MATCH_PARENT;
            } else {
                if (dimEnabled && !dimCustom) {
                    params.dimAmount = dimAlpha;
                    params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
                } else {
                    params.dimAmount = 0f;
                    params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
                }

                lastScreenWidth = AndroidUtilities.getScreenWidth();
                final int calculatedWidth = AndroidUtilities.getScreenWidth() - AndroidUtilities.dp(48) - additionalHorizontalPadding * 2;
                int maxWidth;
                if (AndroidUtilities.isTablet()) {
                    if (AndroidUtilities.isSmallTablet()) {
                        maxWidth = AndroidUtilities.dp(446);
                    } else {
                        maxWidth = AndroidUtilities.dp(496);
                    }
                } else {
                    maxWidth = AndroidUtilities.dp(356);
                }

                params.width = Math.min(maxWidth, calculatedWidth) + backgroundPaddings.left + backgroundPaddings.right;
            }
            if (customView == null || !checkFocusable || !canTextInput(customView)) {
                params.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            } else {
                params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE;
            }
            if (Build.VERSION.SDK_INT >= 28) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            }

            // Native blur on Android 12+
            if (blurredBackground && supportsNativeBlur()) {
                if (progressViewStyle == ALERT_TYPE_MESSAGE) {
                    blurredNativeBackground = true;
                    window.setBackgroundBlurRadius(50);
                    float rad = AndroidUtilities.dp(12);
                    ShapeDrawable shapeDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
                    shapeDrawable.getPaint().setColor(ColorUtils.setAlphaComponent(backgroundColor, (int) (blurAlpha * 255)));
                    window.setBackgroundDrawable(shapeDrawable);
                    if (blurBehind) {
                        params.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
                        params.setBlurBehindRadius(20);
                    }
                }
            }
            window.setAttributes(params);
        }
    }

    /**
     * Creates a button TextView with standard behavior:
     * - alpha 0.5 when disabled
     * - updates background selector when text color changes
     */
    private TextView createButtonTextView() {
        return new AppCompatTextView(getContext()) {
            @Override
            public void setEnabled(boolean enabled) {
                super.setEnabled(enabled);
                setAlpha(enabled ? 1.0f : 0.5f);
            }

            @Override
            public void setTextColor(int color) {
                super.setTextColor(color);
                setBackground(Theme.getRoundRectSelectorDrawable(AndroidUtilities.dp(6), color));
            }
        };
    }

    // -----------------------------------------------------------------------
    // Back press handling
    // -----------------------------------------------------------------------

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (onBackButtonListener != null) {
            onBackButtonListener.onClick(AlertDialog.this, AlertDialog.BUTTON_NEGATIVE);
        }
    }

    // -----------------------------------------------------------------------
    // Focus / soft input
    // -----------------------------------------------------------------------

    public void setFocusable(boolean value) {
        if (focusable == value) {
            return;
        }
        focusable = value;
        Window window = getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        if (focusable) {
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
            params.flags &= ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        } else {
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
            params.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        }
        window.setAttributes(params);
    }

    // -----------------------------------------------------------------------
    // Appearance setters
    // -----------------------------------------------------------------------

    public void setBackgroundColor(int color) {
        backgroundColor = color;
        if (shadowDrawable != null) {
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(backgroundColor, PorterDuff.Mode.MULTIPLY));
        }
    }

    public void setTextColor(int color) {
        if (titleTextView != null) {
            titleTextView.setTextColor(color);
        }
        if (messageTextView != null) {
            messageTextView.setTextColor(color);
        }
    }

    // -----------------------------------------------------------------------
    // Cancel alert for spinner mode
    // -----------------------------------------------------------------------

    private void showCancelAlert() {
        if (!canCancel || cancelDialog != null) {
            return;
        }
        Builder builder = new Builder(getContext());
        builder.setTitle("...");
        builder.setMessage("...");
        builder.setPositiveButton("...", null);
        builder.setNegativeButton("...", (dialogInterface, i) -> {
            if (onCancelListener != null) {
                onCancelListener.onCancel(AlertDialog.this);
            }
            dismiss();
        });
        builder.setOnDismissListener(dialog -> cancelDialog = null);
        try {
            cancelDialog = builder.show();
        } catch (Exception ignore) {
        }
    }

    // -----------------------------------------------------------------------
    // Shadow animations for scroll content
    // -----------------------------------------------------------------------

    private void runShadowAnimation(final int num, final boolean show) {
        if ((show && !shadowVisibility[num]) || (!show && shadowVisibility[num])) {
            shadowVisibility[num] = show;
            if (shadowAnimation[num] != null) {
                shadowAnimation[num].cancel();
            }
            shadowAnimation[num] = new AnimatorSet();
            if (shadow[num] != null) {
                shadowAnimation[num].playTogether(ObjectAnimator.ofInt(shadow[num], "alpha", show ? 255 : 0));
            }
            shadowAnimation[num].setDuration(150);
            shadowAnimation[num].addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (shadowAnimation[num] != null && shadowAnimation[num].equals(animation)) {
                        shadowAnimation[num] = null;
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (shadowAnimation[num] != null && shadowAnimation[num].equals(animation)) {
                        shadowAnimation[num] = null;
                    }
                }
            });
            try {
                shadowAnimation[num].start();
            } catch (Exception ignored) {
            }
        }
    }

    // -----------------------------------------------------------------------
    // Dialog behavior setters
    // -----------------------------------------------------------------------

    public void setDismissDialogByButtons(boolean value) {
        dismissDialogByButtons = value;
    }

    public void setProgress(int progress) {
        currentProgress = progress;
        if (lineProgressView != null) {
            lineProgressView.setProgress(currentProgress / 100.0f, true);
        }
        updateLineProgressTextView();
    }

    private void updateLineProgressTextView() {
        if (lineProgressViewPercent != null) {
            lineProgressViewPercent.setText(String.format("%d%%", currentProgress));
        }
    }

    public void setCanCancel(boolean value) {
        canCancel = value;
    }

    private boolean canTextInput(View v) {
        if (v.onCheckIsTextEditor()) {
            return true;
        }
        if (!(v instanceof ViewGroup)) {
            return false;
        }
        ViewGroup vg = (ViewGroup) v;
        int i = vg.getChildCount();
        while (i > 0) {
            i--;
            v = vg.getChildAt(i);
            if (canTextInput(v)) {
                return true;
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Dismiss
    // -----------------------------------------------------------------------

    @Override
    public void dismiss() {
        if (onDismissListener != null) {
            onDismissListener.onDismiss(this);
        }
        if (cancelDialog != null) {
            cancelDialog.dismiss();
        }
        try {
            super.dismiss();
        } catch (Throwable ignore) {
        }
        AndroidUtilities.cancelRunOnUIThread(showRunnable);
    }

    // -----------------------------------------------------------------------
    // Top area configuration
    // -----------------------------------------------------------------------

    public void setTopImage(int resId, int backgroundColor) {
        topResId = resId;
        topBackgroundColor = backgroundColor;
    }

    public void setTopImage(Drawable drawable, int backgroundColor) {
        topDrawable = drawable;
        topBackgroundColor = backgroundColor;
    }

    public void setTopHeight(int value) {
        topHeight = value;
    }

    // -----------------------------------------------------------------------
    // Content setters (post-create)
    // -----------------------------------------------------------------------

    public void setTitle(CharSequence text) {
        title = text;
        if (titleTextView != null) {
            titleTextView.setText(text);
        }
    }

    public void setSecondTitle(CharSequence text) {
        secondTitle = text;
    }

    public void setPositiveButton(CharSequence text, final OnClickListener listener) {
        positiveButtonText = text;
        positiveButtonListener = listener;
    }

    public void setNegativeButton(CharSequence text, final OnClickListener listener) {
        negativeButtonText = text;
        negativeButtonListener = listener;
    }

    public void setNeutralButton(CharSequence text, final OnClickListener listener) {
        neutralButtonText = text;
        neutralButtonListener = listener;
    }

    public void setItemColor(int item, int color, int icon) {
        if (item < 0 || item >= itemViews.size()) {
            return;
        }
        AlertDialogCell cell = itemViews.get(item);
        cell.textView.setTextColor(color);
        cell.imageView.setColorFilter(new PorterDuffColorFilter(icon, PorterDuff.Mode.MULTIPLY));
    }

    public int getItemsCount() {
        return itemViews.size();
    }

    public void setMessage(CharSequence text) {
        message = text;
        if (messageTextView != null) {
            if (!TextUtils.isEmpty(message)) {
                messageTextView.setText(message);
                messageTextView.setVisibility(View.VISIBLE);
            } else {
                messageTextView.setVisibility(View.GONE);
            }
        }
    }

    public void setMessageTextViewClickable(boolean value) {
        messageTextViewClickable = value;
    }

    public void setButton(int type, CharSequence text, final OnClickListener listener) {
        switch (type) {
            case BUTTON_NEUTRAL:
                neutralButtonText = text;
                neutralButtonListener = listener;
                break;
            case BUTTON_NEGATIVE:
                negativeButtonText = text;
                negativeButtonListener = listener;
                break;
            case BUTTON_POSITIVE:
                positiveButtonText = text;
                positiveButtonListener = listener;
                break;
        }
    }

    public View getButton(int type) {
        if (buttonsLayout != null) {
            return buttonsLayout.findViewWithTag(type);
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Drawable.Callback for shadow drawables
    // -----------------------------------------------------------------------

    @Override
    public void invalidateDrawable(@NonNull Drawable who) {
        if (contentScrollView != null) {
            contentScrollView.invalidate();
        }
        if (scrollContainer != null) {
            scrollContainer.invalidate();
        }
    }

    @Override
    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
        if (contentScrollView != null) {
            contentScrollView.postDelayed(what, when);
        }
    }

    @Override
    public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
        if (contentScrollView != null) {
            contentScrollView.removeCallbacks(what);
        }
    }

    // -----------------------------------------------------------------------
    // Listener setters
    // -----------------------------------------------------------------------

    @Override
    public void setOnCancelListener(OnCancelListener listener) {
        onCancelListener = listener;
        super.setOnCancelListener(listener);
    }

    public void setPositiveButtonListener(final OnClickListener listener) {
        positiveButtonListener = listener;
    }

    public void showDelayed(long delay) {
        AndroidUtilities.cancelRunOnUIThread(showRunnable);
        AndroidUtilities.runOnUIThread(showRunnable, delay);
    }

    public ViewGroup getButtonsLayout() {
        return buttonsLayout;
    }

    // =======================================================================
    // Builder
    // =======================================================================

    public static class Builder {

        private AlertDialog alertDialog;

        protected Builder(AlertDialog alert) {
            alertDialog = alert;
        }

        public Builder(Context context) {
            this(context, 0);
        }

        public Builder(Context context, int progressViewStyle) {
            alertDialog = new AlertDialog(context, progressViewStyle);
        }

        public Context getContext() {
            return alertDialog.getContext();
        }

        public Builder forceVerticalButtons() {
            alertDialog.verticalButtons = true;
            return this;
        }

        public Builder setItems(CharSequence[] items, final OnClickListener onClickListener) {
            alertDialog.items = items;
            alertDialog.onClickListener = onClickListener;
            return this;
        }

        public Builder setCheckFocusable(boolean value) {
            alertDialog.checkFocusable = value;
            return this;
        }

        public Builder setItems(CharSequence[] items, int[] icons, final OnClickListener onClickListener) {
            alertDialog.items = items;
            alertDialog.itemIcons = icons;
            alertDialog.onClickListener = onClickListener;
            return this;
        }

        public Builder setView(View view) {
            return setView(view, LayoutHelper.WRAP_CONTENT);
        }

        public Builder setView(View view, int height) {
            alertDialog.customView = view;
            alertDialog.customViewHeight = height;
            return this;
        }

        public Builder setTitle(CharSequence title) {
            alertDialog.title = title;
            return this;
        }

        public Builder setSubtitle(CharSequence subtitle) {
            alertDialog.subtitle = subtitle;
            return this;
        }

        public Builder setTopImage(int resId, int backgroundColor) {
            alertDialog.topResId = resId;
            alertDialog.topBackgroundColor = backgroundColor;
            return this;
        }

        public Builder setTopView(View view) {
            alertDialog.topView = view;
            return this;
        }

        public Builder setTopAnimationIsNew(boolean isNew) {
            alertDialog.topAnimationIsNew = isNew;
            return this;
        }

        public Builder setTopImage(Drawable drawable, int backgroundColor) {
            alertDialog.topDrawable = drawable;
            alertDialog.topBackgroundColor = backgroundColor;
            return this;
        }

        public Builder setMessage(CharSequence message) {
            alertDialog.message = message;
            return this;
        }

        public Builder setPositiveButton(CharSequence text, final OnClickListener listener) {
            alertDialog.positiveButtonText = text;
            alertDialog.positiveButtonListener = listener;
            return this;
        }

        public Builder setNegativeButton(CharSequence text, final OnClickListener listener) {
            alertDialog.negativeButtonText = text;
            alertDialog.negativeButtonListener = listener;
            return this;
        }

        public Builder setNeutralButton(CharSequence text, final OnClickListener listener) {
            alertDialog.neutralButtonText = text;
            alertDialog.neutralButtonListener = listener;
            return this;
        }

        public Builder setOnBackButtonListener(final OnClickListener listener) {
            alertDialog.onBackButtonListener = listener;
            return this;
        }

        public Builder setOnCancelListener(OnCancelListener listener) {
            alertDialog.setOnCancelListener(listener);
            return this;
        }

        public Builder setCustomViewOffset(int offset) {
            alertDialog.customViewOffset = offset;
            return this;
        }

        public Builder setMessageTextViewClickable(boolean value) {
            alertDialog.messageTextViewClickable = value;
            return this;
        }

        public AlertDialog create() {
            return alertDialog;
        }

        public AlertDialog show() {
            alertDialog.show();
            return alertDialog;
        }

        public Runnable getDismissRunnable() {
            return alertDialog.dismissRunnable;
        }

        public Builder setOnDismissListener(OnDismissListener onDismissListener) {
            alertDialog.setOnDismissListener(onDismissListener);
            return this;
        }

        public void setTopViewAspectRatio(float aspectRatio) {
            alertDialog.aspectRatio = aspectRatio;
        }

        public Builder setDimEnabled(boolean dimEnabled) {
            alertDialog.dimEnabled = dimEnabled;
            return this;
        }

        public Builder setDimAlpha(float dimAlpha) {
            alertDialog.dimAlpha = dimAlpha;
            return this;
        }

        public void notDrawBackgroundOnTopView(boolean b) {
            alertDialog.notDrawBackgroundOnTopView = b;
            alertDialog.blurredBackground = false;
        }

        public void setButtonsVertical(boolean vertical) {
            alertDialog.verticalButtons = vertical;
        }

        public Builder setOnPreDismissListener(OnDismissListener onDismissListener) {
            alertDialog.onDismissListener = onDismissListener;
            return this;
        }

        public Builder setBlurredBackground(boolean b) {
            alertDialog.blurredBackground = b;
            return this;
        }

        public Builder setAdditionalHorizontalPadding(int padding) {
            alertDialog.additionalHorizontalPadding = padding;
            return this;
        }

        // Top animation stubs (kept for Builder API compatibility; images are shown
        // as static drawables since the Lottie animation dependency was removed)
        public Builder setTopAnimation(int resId, int backgroundColor) {
            alertDialog.topResId = resId;
            alertDialog.topBackgroundColor = backgroundColor;
            return this;
        }

        public Builder setTopAnimation(int resId, int size, boolean autoRepeat, int backgroundColor) {
            alertDialog.topResId = resId;
            alertDialog.topAnimationSize = size;
            alertDialog.topAnimationAutoRepeat = autoRepeat;
            alertDialog.topBackgroundColor = backgroundColor;
            return this;
        }

        public Builder setTopAnimation(int resId, int size, boolean autoRepeat, int backgroundColor, java.util.Map<String, Integer> layerColors) {
            alertDialog.topResId = resId;
            alertDialog.topAnimationSize = size;
            alertDialog.topAnimationAutoRepeat = autoRepeat;
            alertDialog.topBackgroundColor = backgroundColor;
            return this;
        }
    }
}
