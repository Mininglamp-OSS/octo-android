package com.chat.base.views;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;

import com.chat.base.ui.components.CubicBezierInterpolator;

/**
 * 2019-11-27 11:57
 */
public class CommonAnim {

    private CommonAnim() {
    }

    private static class DropAnimBinder {
        final static CommonAnim dropAnim = new CommonAnim();
    }

    public static CommonAnim getInstance() {
        return DropAnimBinder.dropAnim;
    }

    public void animateOpen(View v, int startHeight, int mHiddenViewMeasuredHeight, IAnimateEnd iAnimateEnd) {
        v.setVisibility(VISIBLE);
        ValueAnimator animator = createDropAnimator(v, startHeight,
                mHiddenViewMeasuredHeight);
        animator.start();
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                iAnimateEnd.onEnd();
            }
        });
    }


    public void animateOpen(View v, int startHeight, int mHiddenViewMeasuredHeight) {
        v.setVisibility(VISIBLE);
        ValueAnimator animator = createDropAnimator(v, startHeight,
                mHiddenViewMeasuredHeight);
        animator.start();
    }

    public void animateOpen(View v, int mHiddenViewMeasuredHeight) {
        v.setVisibility(VISIBLE);
        ValueAnimator animator = createDropAnimator(v, 0,
                mHiddenViewMeasuredHeight);
        animator.start();
    }

    public interface IAnimateEnd {
        void onEnd();
    }

    public void animateOpen(View v, int mHiddenViewMeasuredHeight, IAnimateEnd iAnimateEnd) {
        ValueAnimator animator = createDropAnimator(v, 0,
                mHiddenViewMeasuredHeight);
        animator.start();
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                iAnimateEnd.onEnd();
            }
        });
        v.setVisibility(VISIBLE);
    }

    public void animateClose(final View view) {
        int origHeight = view.getHeight();
        ValueAnimator animator = createDropAnimator(view, origHeight, 0);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setVisibility(View.GONE);
            }
        });
        animator.start();


    }

    private ValueAnimator createDropAnimator(final View v, int start, int end) {
        ValueAnimator animator = ValueAnimator.ofInt(start, end);
        animator.addUpdateListener(arg0 -> {
            int value = (int) arg0.getAnimatedValue();
            ViewGroup.LayoutParams layoutParams = v.getLayoutParams();
            layoutParams.height = value;
            v.setLayoutParams(layoutParams);

        });
        return animator;
    }

    public void rotateImage(ImageView imageView, float fromDegrees, float toDegrees, int imageResource) {
        float pivotX = imageView.getWidth() / 2f;
        float pivotY = imageView.getHeight() / 2f;
        imageView.setImageResource(imageResource);
        //旋转动画效果   参数值 旋转的开始角度  旋转的结束角度  pivotX x轴伸缩值
        RotateAnimation animation = new RotateAnimation(fromDegrees, toDegrees,
                pivotX, pivotY);
        //该方法用于设置动画的持续时间，以毫秒为单位
        animation.setDuration(300);
        //设置重复次数
        //animation.setRepeatCount(int repeatCount);
        //动画终止时停留在最后一帧
        animation.setFillAfter(true);
        //启动动画
        imageView.startAnimation(animation);
    }

    public void rotateArrow(ImageView arrow, float fromDegrees, float toDegrees) {
        float pivotX = arrow.getWidth() / 2f;
        float pivotY = arrow.getHeight() / 2f;
        //旋转动画效果   参数值 旋转的开始角度  旋转的结束角度  pivotX x轴伸缩值
        RotateAnimation animation = new RotateAnimation(fromDegrees, toDegrees,
                pivotX, pivotY);
        //该方法用于设置动画的持续时间，以毫秒为单位
        animation.setDuration(300);
        //设置重复次数
        //animation.setRepeatCount(int repeatCount);
        //动画终止时停留在最后一帧
        animation.setFillAfter(true);
        //启动动画
        arrow.startAnimation(animation);
    }

    /**
     * @param arrow
     * @param flag  1：朝上
     */
    public void rotateArrow(ImageView arrow, boolean flag) {
        float pivotX = arrow.getWidth() / 2f;
        float pivotY = arrow.getHeight() / 2f;
        float fromDegrees;
        float toDegrees;
        // flag为true则向上
        if (flag) {
            fromDegrees = 180f;
            toDegrees = 360f;
        } else {
            fromDegrees = 0f;
            toDegrees = 180f;
        }
        //旋转动画效果   参数值 旋转的开始角度  旋转的结束角度  pivotX x轴伸缩值
        RotateAnimation animation = new RotateAnimation(fromDegrees, toDegrees,
                pivotX, pivotY);
        //该方法用于设置动画的持续时间，以毫秒为单位
        animation.setDuration(300);
        //设置重复次数
        //animation.setRepeatCount(int repeatCount);
        //动画终止时停留在最后一帧
        animation.setFillAfter(true);
        //启动动画
        arrow.startAnimation(animation);
    }


    public void animImageView(ImageView mImageView) {
        //图片动画
        float toScale = 0.2f;
        PropertyValuesHolder propertyValuesHolderX = PropertyValuesHolder.ofFloat("scaleX", 1.0f, toScale, 1.0f);
        PropertyValuesHolder propertyValuesHolderY = PropertyValuesHolder.ofFloat("scaleY", 1.0f, toScale, 1.0f);
        ObjectAnimator objectAnimator = ObjectAnimator.ofPropertyValuesHolder(mImageView,
                propertyValuesHolderX, propertyValuesHolderY);
        objectAnimator.start();
    }

    public void showLeft2Right(View view) {
        view.setVisibility(VISIBLE);
        TranslateAnimation mShowAction = new TranslateAnimation(Animation.RELATIVE_TO_SELF, -1.0f,
                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF,
                0.0f, Animation.RELATIVE_TO_SELF, 0.0f);
        mShowAction.setRepeatMode(Animation.REVERSE);
        mShowAction.setDuration(150);
        view.startAnimation(mShowAction);
    }

    public void hideRight2Left(View view) {
        view.setVisibility(View.GONE);
        TranslateAnimation mShowAction = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, -1.0f, Animation.RELATIVE_TO_SELF,
                0.0f, Animation.RELATIVE_TO_SELF, 0.0f);
        mShowAction.setRepeatMode(Animation.REVERSE);
        mShowAction.setDuration(150);
        view.startAnimation(mShowAction);
    }

    public void showBottom2Top(View view) {
        showBottom2Top(view, null);
    }

    public void showBottom2Top(View view, final IAnimateEnd iAnimateEnd) {
        TranslateAnimation mShowAction = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF,
                1.0f, Animation.RELATIVE_TO_SELF, 0.0f);
        mShowAction.setRepeatMode(Animation.REVERSE);
        mShowAction.setDuration(300);
        view.setVisibility(INVISIBLE);
        mShowAction.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                view.setVisibility(VISIBLE);
                if (iAnimateEnd != null) iAnimateEnd.onEnd();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        view.startAnimation(mShowAction);
    }

    public void hideTop2Bottom(View view) {
        if (view.getVisibility() == View.GONE) return;
        TranslateAnimation mHiddenAction = new TranslateAnimation(Animation.RELATIVE_TO_SELF,
                0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF,
                1.0f);
        mHiddenAction.setDuration(300);
        mHiddenAction.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                view.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        view.startAnimation(mHiddenAction);
    }

    public void fadeIn(View view, float startAlpha, float endAlpha, long duration) {
        if (view.getVisibility() == VISIBLE) return;

        Animation animation = new AlphaAnimation(startAlpha, endAlpha);
        animation.setDuration(duration);
        view.startAnimation(animation);
        view.setVisibility(VISIBLE);
    }

    public void fadeIn(View view) {
        fadeIn(view, 0F, 1F, 1000);
        view.setEnabled(true);
    }

    public void fadeOut(View view) {
        if (view.getVisibility() != VISIBLE) return;
        view.setEnabled(false);
        Animation animation = new AlphaAnimation(1F, 0F);
        animation.setDuration(1000);
        view.startAnimation(animation);
        view.setVisibility(View.GONE);
    }

    public void slideIntoView(View view) {
        Animation slideAnimation = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.0f);
        slideAnimation.setDuration(500); // 动画持续时间，毫秒
//        slideAnimation.setFillAfter(true); // 动画结束后保持结束状态
        view.startAnimation(slideAnimation);
    }

    // 从底部滑出的动画
    public void slideOutOfView(View view) {
        Animation slideAnimation = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 1.0f);
        slideAnimation.setDuration(500); // 动画持续时间，毫秒
//        slideAnimation.setFillAfter(true); // 动画结束后保持结束状态
        slideAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                view.setVisibility(View.GONE); // 动画结束后隐藏View
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        view.startAnimation(slideAnimation);
    }

    public void showOrHide(View view) {
        view.setTranslationY(view.getHeight());
        AnimatorSet animatorSet = new AnimatorSet();
        ObjectAnimator translateAnim = ObjectAnimator.ofFloat(view, "translationY", view.getHeight(), 0);
        translateAnim.setDuration(200);
        translateAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                view.setVisibility(View.VISIBLE);
            }
        });
        animatorSet.play(translateAnim);
        animatorSet.start();
    }

    public void showOrHide(View view, boolean show) {
        if (show) {
//            slideOutOfView(view);
            showOrHide(view);
        } else {
            slideIntoView(view);
        }
//        this.showOrHide(view, show, true, true);
    }

    public void showOrHide(View view, boolean show, boolean animated) {
        showOrHide(view, show, animated, false);
    }


    public void showOrHide(View view, boolean show, boolean animated, boolean isGone) {

        if (view == null || (show && view.getTag() == null) || (!show && view.getTag() != null)) {
            return;
        }

        view.setTag(show ? null : 1);
        if (animated) {
            if (show) {
                view.setVisibility(VISIBLE);
            }
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(ObjectAnimator.ofFloat(view, View.ALPHA, show ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(view, View.SCALE_X, show ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(view, View.SCALE_Y, show ? 1.0f : 0.0f));
            animatorSet.setDuration(200);
            animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!show) {
                        view.setVisibility(isGone ? View.GONE : INVISIBLE);
                    }
                }
            });
            animatorSet.start();
        } else {
            view.setAlpha(show ? 1.0f : 0.0f);
            view.setScaleX(show ? 1.0f : 0.0f);
            view.setScaleY(show ? 1.0f : 0.0f);
            view.setVisibility(show ? VISIBLE : isGone ? View.GONE : INVISIBLE);
        }
    }
}
