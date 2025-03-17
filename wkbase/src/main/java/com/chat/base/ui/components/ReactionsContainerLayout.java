package com.chat.base.ui.components;

import static androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.Property;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.R;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.msgitem.ReactionSticker;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.views.RecyclerListView;

import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ReactionsContainerLayout extends FrameLayout {
    public final static Property<ReactionsContainerLayout, Float> TRANSITION_PROGRESS_VALUE = new Property<ReactionsContainerLayout, Float>(Float.class, "transitionProgress") {
        @Override
        public Float get(ReactionsContainerLayout reactionsContainerLayout) {
            return reactionsContainerLayout.transitionProgress;
        }

        @Override
        public void set(ReactionsContainerLayout object, Float value) {
            object.setTransitionProgress(value);
        }
    };

    private final static int ALPHA_DURATION = 150;
    private final static float SIDE_SCALE = 0.6f;
    private final static float SCALE_PROGRESS = 0.75f;
    private final static float CLIP_PROGRESS = 0.25f;
    public final RecyclerListView recyclerListView;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint leftShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rightShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float leftAlpha, rightAlpha;
    private float transitionProgress = 1f;
    private final RectF rect = new RectF();
    private final Path mPath = new Path();
    private float radius = AndroidUtilities.dp(72);
    private final float bigCircleRadius = AndroidUtilities.dp(8);
    private final float smallCircleRadius = bigCircleRadius / 2;
    private final int bigCircleOffset = AndroidUtilities.dp(36);
    ValueAnimator cancelPressedAnimation;
    private List<ReactionSticker> reactionsList = new ArrayList<>();

    private final LinearLayoutManager linearLayoutManager;
    private final RecyclerView.Adapter listAdapter;

    private final int[] location = new int[2];

    private ReactionsContainerDelegate delegate;

    private final Rect shadowPad = new Rect();
    private final Drawable shadow;
    private final boolean animationEnabled;

    private String pressedReaction;
    private int pressedReactionPosition;
    private float pressedProgress;
    private float cancelPressedProgress;
    private float pressedViewScale;
    private float otherViewsScale;
    private boolean clicked;
    long lastReactionSentTime;

    public ReactionsContainerLayout(@NonNull Context context) {
        super(context);
        Object object = EndpointManager.getInstance().invoke("reaction_sticker", null);
        List<ReactionSticker> list = (List<ReactionSticker>) object;
        if (list == null) {
            list = new ArrayList<>();
        }
        reactionsList.addAll(list);
        animationEnabled = true;
        shadow = ContextCompat.getDrawable(context, R.mipmap.reactions_bubble_shadow);
        shadowPad.left = shadowPad.top = shadowPad.right = shadowPad.bottom = AndroidUtilities.dp(7);
        shadow.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(getContext(), R.color.screen_bg), PorterDuff.Mode.MULTIPLY));
        recyclerListView = new RecyclerListView(context) {
            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (((ReactionHolderView) child).currentReaction.name.equals(pressedReaction)) {
                    return true;
                }
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
        recyclerListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                super.getItemOffsets(outRect, view, parent, state);
                int position = parent.getChildAdapterPosition(view);
                if (position == 0) {
                    outRect.left = AndroidUtilities.dp(6);
                }
                outRect.right = AndroidUtilities.dp(4);
                if (position == listAdapter.getItemCount() - 1) {
                    outRect.right = AndroidUtilities.dp(6);
                }
            }
        });
        recyclerListView.setLayoutManager(linearLayoutManager);
        recyclerListView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerListView.setAdapter(listAdapter = new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ReactionHolderView hv = new ReactionHolderView(context);
                int size = getLayoutParams().height - getPaddingTop() - getPaddingBottom();
                hv.setLayoutParams(new RecyclerView.LayoutParams(size - AndroidUtilities.dp(12), size));
                return new RecyclerListView.Holder(hv);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ReactionHolderView h = (ReactionHolderView) holder.itemView;
                h.setScaleX(1);
                h.setScaleY(1);
                h.setReaction(reactionsList.get(position));
            }

            @Override
            public int getItemCount() {
                return reactionsList.size();
            }
        });
        recyclerListView.addOnScrollListener(new LeftRightShadowsListener());
        recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (recyclerView.getChildCount() > 2) {
                    float sideDiff = 1f - SIDE_SCALE;

                    recyclerView.getLocationInWindow(location);
                    int rX = location[0];

                    View ch1 = recyclerView.getChildAt(0);
                    ch1.getLocationInWindow(location);
                    int ch1X = location[0];

                    int dX1 = ch1X - rX;
                    float s1 = SIDE_SCALE + (1f - Math.min(1, -Math.min(dX1, 0f) / ch1.getWidth())) * sideDiff;
                    if (Float.isNaN(s1)) s1 = 1f;
                    ((ReactionHolderView) ch1).sideScale = s1;

                    View ch2 = recyclerView.getChildAt(recyclerView.getChildCount() - 1);
                    ch2.getLocationInWindow(location);
                    int ch2X = location[0];

                    int dX2 = rX + recyclerView.getWidth() - (ch2X + ch2.getWidth());
                    float s2 = SIDE_SCALE + (1f - Math.min(1, -Math.min(dX2, 0f) / ch2.getWidth())) * sideDiff;
                    if (Float.isNaN(s2)) s2 = 1f;
                    ((ReactionHolderView) ch2).sideScale = s2;
                }
                for (int i = 1; i < recyclerListView.getChildCount() - 1; i++) {
                    View ch = recyclerListView.getChildAt(i);
                    ((ReactionHolderView) ch).sideScale = 1f;
                }
                invalidate();

            }
        });
        recyclerListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int i = parent.getChildAdapterPosition(view);
                if (i == 0)
                    outRect.left = AndroidUtilities.dp(8);
                if (i == listAdapter.getItemCount() - 1)
                    outRect.right = AndroidUtilities.dp(8);
            }
        });
        addView(recyclerListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        invalidateShaders();

        bgPaint.setColor(ContextCompat.getColor(getContext(), R.color.screen_bg));
        startEnterAnimation();
//        setReactionsList(EmojiManager.getReactionStickers());
    }

    public void setDelegate(ReactionsContainerDelegate delegate) {
        this.delegate = delegate;
    }

    @SuppressLint("NotifyDataSetChanged")
    private void setReactionsList(List<ReactionSticker> reactionsList) {
        this.reactionsList = reactionsList;
        int size = getLayoutParams().height - getPaddingTop() - getPaddingBottom();
        if (size * reactionsList.size() < AndroidUtilities.dp(200)) {
            getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        listAdapter.notifyDataSetChanged();
    }

    HashSet<View> lastVisibleViews = new HashSet<>();
    HashSet<View> lastVisibleViewsTmp = new HashSet<>();

    @Override
    protected void dispatchDraw(Canvas canvas) {
        lastVisibleViewsTmp.clear();
        lastVisibleViewsTmp.addAll(lastVisibleViews);
        lastVisibleViews.clear();

        if (pressedReaction != null) {
            if (pressedProgress != 1f) {
                pressedProgress += 16f / 1500f;
                if (pressedProgress >= 1f) {
                    pressedProgress = 1f;
                }
                invalidate();
            }
        }

        float cPr = (Math.max(CLIP_PROGRESS, Math.min(transitionProgress, 1f)) - CLIP_PROGRESS) / (1f - CLIP_PROGRESS);
        float br = bigCircleRadius * cPr, sr = smallCircleRadius * cPr;

        pressedViewScale = 1 + 2 * pressedProgress;
        otherViewsScale = 1 - 0.15f * pressedProgress;

        int s = canvas.save();
        float pivotX = AndroidUtilities.isRTL ? getWidth() * 0.125f : getWidth() * 0.875f;

        if (transitionProgress <= SCALE_PROGRESS) {
            float sc = transitionProgress / SCALE_PROGRESS;
            canvas.scale(sc, sc, pivotX, getHeight() / 2f);
        }

        float lt = 0, rt = 1;
        if (AndroidUtilities.isRTL) {
            rt = Math.max(CLIP_PROGRESS, transitionProgress);
        } else {
            lt = (1f - Math.max(CLIP_PROGRESS, transitionProgress));
        }
        rect.set(getPaddingLeft() + (getWidth() - getPaddingRight()) * lt, getPaddingTop() + recyclerListView.getMeasuredHeight() * (1f - otherViewsScale), (getWidth() - getPaddingRight()) * rt, getHeight() - getPaddingBottom());
        radius = rect.height() / 2f;
        shadow.setBounds((int) (getPaddingLeft() + (getWidth() - getPaddingRight() + shadowPad.right) * lt - shadowPad.left), getPaddingTop() - shadowPad.top, (int) ((getWidth() - getPaddingRight() + shadowPad.right) * rt), getHeight() - getPaddingBottom() + shadowPad.bottom);
        shadow.draw(canvas);
        canvas.restoreToCount(s);

        s = canvas.save();
        if (transitionProgress <= SCALE_PROGRESS) {
            float sc = transitionProgress / SCALE_PROGRESS;
            canvas.scale(sc, sc, pivotX, getHeight() / 2f);
        }
        canvas.drawRoundRect(rect, radius, radius, bgPaint);
        canvas.restoreToCount(s);

        mPath.rewind();
        mPath.addRoundRect(rect, radius, radius, Path.Direction.CW);

        s = canvas.save();
        if (transitionProgress <= SCALE_PROGRESS) {
            float sc = transitionProgress / SCALE_PROGRESS;
            canvas.scale(sc, sc, pivotX, getHeight() / 2f);
        }

        if (transitionProgress != 0 && getAlpha() == 1f) {
            int delay = 0;
            for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                ReactionHolderView view = (ReactionHolderView) recyclerListView.getChildAt(i);
                checkPressedProgress(canvas, view);
                if (view.getX() + view.getMeasuredWidth() / 2f > 0 && view.getX() + view.getMeasuredWidth() / 2f < recyclerListView.getWidth()) {
                    if (!lastVisibleViewsTmp.contains(view)) {
                        view.play(delay);
                        delay += 30;
                    }
                    lastVisibleViews.add(view);
                } else if (!view.isEnter) {
                    view.resetAnimation();
                }
            }
        }

        canvas.clipPath(mPath);
        canvas.translate((AndroidUtilities.isRTL ? -1 : 1) * getWidth() * (1f - transitionProgress), 0);
        super.dispatchDraw(canvas);

        float p = clamp(leftAlpha * transitionProgress, 1f, 1f);
        leftShadowPaint.setAlpha((int) (p * 0xFF));
        canvas.drawRect(rect, leftShadowPaint);
        p = clamp(rightAlpha * transitionProgress, 1f, 1f);
        rightShadowPaint.setAlpha((int) (p * 0xFF));
        canvas.drawRect(rect, rightShadowPaint);
        canvas.restoreToCount(s);

        canvas.save();

        canvas.clipRect(0, rect.bottom, getMeasuredWidth(), getMeasuredHeight());
        float cx = AndroidUtilities.isRTL ? bigCircleOffset : getWidth() - bigCircleOffset, cy = getHeight() - getPaddingBottom();
        int sPad = AndroidUtilities.dp(3);
        shadow.setBounds((int) (cx - br - sPad * cPr), (int) (cy - br - sPad * cPr), (int) (cx + br + sPad * cPr), (int) (cy + br + sPad * cPr));
        shadow.draw(canvas);
        canvas.drawCircle(cx, cy, br, bgPaint);
//
        cx = AndroidUtilities.isRTL ? bigCircleOffset - bigCircleRadius : getWidth() - bigCircleOffset + bigCircleRadius;
        cy = getHeight() - smallCircleRadius - sPad;
        sPad = -AndroidUtilities.dp(1);
        shadow.setBounds((int) (cx - br - sPad * cPr), (int) (cy - br - sPad * cPr), (int) (cx + br + sPad * cPr), (int) (cy + br + sPad * cPr));
        shadow.draw(canvas);
        canvas.drawCircle(cx, cy, sr, bgPaint);
        canvas.restore();
    }


    public int getTotalWidth() {
        return AndroidUtilities.dp(36) * reactionsList.size() + AndroidUtilities.dp(16);
    }

    public int getItemsCount() {
        return reactionsList.size();
    }

    public float clamp(float value, float top, float bottom) {
        return Math.max(Math.min(value, top), bottom);
    }

    private void checkPressedProgress(Canvas canvas, ReactionHolderView view) {
        if (view.currentReaction.name.equals(pressedReaction)) {
            view.setPivotX(view.getMeasuredWidth() >> 1);
//            view.setPivotY(view.backupImageView.getY() + view.backupImageView.getMeasuredHeight());
            view.setScaleX(pressedViewScale);
            view.setScaleY(pressedViewScale);

            if (!clicked) {
                if (cancelPressedAnimation == null) {
                    view.pressedBackupImageView.setVisibility(View.VISIBLE);
                    view.pressedBackupImageView.setAlpha(1f);
//                    if (view.pressedBackupImageView.getImageReceiver().hasBitmapImage()) {
//                        view.backupImageView.setAlpha(0f);
//                    }
                } else {
                    view.pressedBackupImageView.setAlpha(1f - cancelPressedProgress);
//                    view.backupImageView.setAlpha(cancelPressedProgress);
                }
                if (pressedProgress == 1f) {
                    clicked = true;
                    if (System.currentTimeMillis() - lastReactionSentTime > 300) {
                        lastReactionSentTime = System.currentTimeMillis();
                        int[] location = new int[2];
                        view.getLocationOnScreen(location);
                        delegate.onReactionClicked(view, view.currentReaction.name, true, location);
                    }
                }
            }

            canvas.save();
            float x = recyclerListView.getX() + view.getX();
            float additionalWidth = (view.getMeasuredWidth() * view.getScaleX() - view.getMeasuredWidth()) / 2f;
            if (x - additionalWidth < 0 && view.getTranslationX() >= 0) {
                view.setTranslationX(-(x - additionalWidth));
            } else if (x + view.getMeasuredWidth() + additionalWidth > getMeasuredWidth() && view.getTranslationX() <= 0) {
                view.setTranslationX(getMeasuredWidth() - x - view.getMeasuredWidth() - additionalWidth);
            } else {
                view.setTranslationX(0);
            }
            x = recyclerListView.getX() + view.getX();
            canvas.translate(x, recyclerListView.getY() + view.getY());
            canvas.scale(view.getScaleX(), view.getScaleY(), view.getPivotX(), view.getPivotY());
            view.draw(canvas);
            canvas.restore();
        } else {
            int position = recyclerListView.getChildAdapterPosition(view);
            float translationX;
            translationX = (view.getMeasuredWidth() * (pressedViewScale - 1f)) / 3f - view.getMeasuredWidth() * (1f - otherViewsScale) * (Math.abs(pressedReactionPosition - position) - 1);

            if (position < pressedReactionPosition) {
                view.setPivotX(0);
                view.setTranslationX(-translationX);
            } else {
                view.setPivotX(view.getMeasuredWidth());
                view.setTranslationX(translationX);
            }
            view.setPivotY(view.pressedBackupImageView.getY() + view.pressedBackupImageView.getMeasuredHeight());
            view.setScaleX(otherViewsScale);
            view.setScaleY(otherViewsScale);
//            view.backupImageView.setScaleX(view.sideScale);
//            view.backupImageView.setScaleY(view.sideScale);
            view.pressedBackupImageView.setVisibility(View.VISIBLE);

//            view.backupImageView.setAlpha(1f);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invalidateShaders();
    }

    /**
     * Invalidates shaders
     */
    private void invalidateShaders() {
        int dp = AndroidUtilities.dp(30);
        float cy = getHeight() / 2f;
        int clr = ContextCompat.getColor(getContext(), R.color.screen_bg);
        leftShadowPaint.setShader(new LinearGradient(0, cy, dp, cy, clr, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        rightShadowPaint.setShader(new LinearGradient(getWidth(), cy, getWidth() - dp, cy, clr, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        invalidate();
    }

    public void setTransitionProgress(float transitionProgress) {
        this.transitionProgress = transitionProgress;
        invalidate();
    }


    public void startEnterAnimation() {
        setTransitionProgress(0);
        setAlpha(1f);
        ObjectAnimator animator = ObjectAnimator.ofFloat(this, ReactionsContainerLayout.TRANSITION_PROGRESS_VALUE, 0f, 1f).setDuration(400);
        animator.setInterpolator(new OvershootInterpolator(1.004f));
        animator.start();
    }

    private final class LeftRightShadowsListener extends RecyclerView.OnScrollListener {
        private boolean leftVisible, rightVisible;
        private ValueAnimator leftAnimator, rightAnimator;

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            boolean l = linearLayoutManager.findFirstVisibleItemPosition() != 0;
            if (l != leftVisible) {
                if (leftAnimator != null)
                    leftAnimator.cancel();
                leftAnimator = startAnimator(leftAlpha, l ? 1 : 0, aFloat -> {
                    leftShadowPaint.setAlpha((int) ((leftAlpha = aFloat) * 0xFF));
                    invalidate();
                }, () -> leftAnimator = null);

                leftVisible = l;
            }

            boolean r = linearLayoutManager.findLastVisibleItemPosition() != listAdapter.getItemCount() - 1;
            if (r != rightVisible) {
                if (rightAnimator != null)
                    rightAnimator.cancel();
                rightAnimator = startAnimator(rightAlpha, r ? 1 : 0, aFloat -> {
                    rightShadowPaint.setAlpha((int) ((rightAlpha = aFloat) * 0xFF));
                    invalidate();
                }, () -> rightAnimator = null);

                rightVisible = r;
            }
        }

        private ValueAnimator startAnimator(float fromAlpha, float toAlpha, Consumer<Float> callback, Runnable onEnd) {
            ValueAnimator a = ValueAnimator.ofFloat(fromAlpha, toAlpha).setDuration((long) (Math.abs(toAlpha - fromAlpha) * ALPHA_DURATION));
            a.addUpdateListener(animation -> callback.accept((Float) animation.getAnimatedValue()));
            a.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onEnd.run();
                }
            });
            a.start();
            return a;
        }
    }

    public final class ReactionHolderView extends FrameLayout {
        //        public AppCompatImageView backupImageView;
        public RLottieImageView pressedBackupImageView;
        public ReactionSticker currentReaction;
        public float sideScale = 1f;
        private boolean isEnter;

        Runnable playRunnable = new Runnable() {
            @Override
            public void run() {
                if (pressedBackupImageView.getAnimatedDrawable() != null && !pressedBackupImageView.getAnimatedDrawable().isRunning()) {
                    pressedBackupImageView.getAnimatedDrawable().start();
                }
            }
        };

        ReactionHolderView(Context context) {
            super(context);
            pressedBackupImageView = new RLottieImageView(context) {
                @Override
                public void invalidate() {
                    super.invalidate();
                    ReactionsContainerLayout.this.invalidate();
                }
            };
            addView(pressedBackupImageView, LayoutHelper.createFrame(34, 34, Gravity.CENTER, 5, 5, 5, 5));
        }

        private void setReaction(ReactionSticker react) {
            resetAnimation();
            currentReaction = react;
            RLottieDrawable drawable = new RLottieDrawable(getContext(), currentReaction.resourceID, currentReaction.name, AndroidUtilities.dp(30), AndroidUtilities.dp(30));
            pressedBackupImageView.setAutoRepeat(false);
            pressedBackupImageView.setAnimation(drawable);
            pressedBackupImageView.playAnimation();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            resetAnimation();
        }

        public boolean play(int delay) {
            if (!animationEnabled) {
                resetAnimation();
                isEnter = true;
                return false;
            }
            AndroidUtilities.cancelRunOnUIThread(playRunnable);
            return false;
        }

        public void resetAnimation() {
            AndroidUtilities.cancelRunOnUIThread(playRunnable);
            if (pressedBackupImageView.getAnimatedDrawable() != null) {
                if (animationEnabled) {
                    pressedBackupImageView.getAnimatedDrawable().setCurrentFrame(0, false, true);
                } else {
                    pressedBackupImageView.getAnimatedDrawable().setCurrentFrame(pressedBackupImageView.getAnimatedDrawable().getFramesCount() - 1, false, true);
                }
            }
            isEnter = false;
        }

        Runnable longPressRunnable = new Runnable() {
            @Override
            public void run() {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                pressedReactionPosition = reactionsList.indexOf(currentReaction);
                pressedReaction = currentReaction.name;
                ReactionsContainerLayout.this.invalidate();
            }
        };
        float pressedX, pressedY;
        boolean pressed;

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (cancelPressedAnimation != null) {
                return false;
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                pressed = true;
                pressedX = event.getX();
                pressedY = event.getY();
                if (sideScale == 1f) {
//                    AndroidUtilities.runOnUIThread(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                }
            }
            float touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop() * 2f;
            boolean cancelByMove = event.getAction() == MotionEvent.ACTION_MOVE && (Math.abs(pressedX - event.getX()) > touchSlop || Math.abs(pressedY - event.getY()) > touchSlop);
            if (cancelByMove || event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (event.getAction() == MotionEvent.ACTION_UP && pressed && (pressedReaction == null || pressedProgress > 0.8f) && delegate != null) {
                    clicked = true;
                    if (System.currentTimeMillis() - lastReactionSentTime > 300) {
                        lastReactionSentTime = System.currentTimeMillis();
                        int[] location = new int[2];
                        this.getLocationOnScreen(location);
//                        int x = location[0];
//                        int y = location[1];
                        delegate.onReactionClicked(this, currentReaction.name, pressedProgress > 0.8f, location);
                    }

                }
                if (!clicked) {
                    cancelPressed();
                }

//                AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                pressed = false;
            }
            return true;
        }
    }

    private void cancelPressed() {
        if (pressedReaction != null) {
            cancelPressedProgress = 0f;
            float fromProgress = pressedProgress;
            cancelPressedAnimation = ValueAnimator.ofFloat(0, 1f);
            cancelPressedAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    cancelPressedProgress = (float) valueAnimator.getAnimatedValue();
                    pressedProgress = fromProgress * (1f - cancelPressedProgress);
                    ReactionsContainerLayout.this.invalidate();
                }
            });
            cancelPressedAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    cancelPressedAnimation = null;
                    pressedProgress = 0;
                    pressedReaction = null;
                    ReactionsContainerLayout.this.invalidate();
                }
            });
            cancelPressedAnimation.setDuration(150);
            cancelPressedAnimation.setInterpolator(CubicBezierInterpolator.DEFAULT);
            cancelPressedAnimation.start();
        }
    }

    public interface ReactionsContainerDelegate {
        void onReactionClicked(View v, String name, boolean longpress, int[] localtion);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }


    @Override
    public void setAlpha(float alpha) {
        if (getAlpha() != alpha && alpha == 0) {
            lastVisibleViews.clear();
            for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                ReactionHolderView view = (ReactionHolderView) recyclerListView.getChildAt(i);
                view.resetAnimation();
            }
        }
        super.setAlpha(alpha);
    }
}