package com.chat.base.ui.components;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.chat.base.R;

/**
 * 通用加载动画视图
 * 实现 DoubleBounce 效果：两个圆形重叠，交替进行缩放和透明度变化
 */
public class CommonLoadingView extends View {
    
    private static final int DEFAULT_SIZE = 40; // dp
    private static final int DEFAULT_ANIMATION_DURATION = 2000; // ms
    private static final float MIN_SCALE = 0f;
    private static final float MAX_SCALE = 1f;
    private static final float MIN_ALPHA = 0.5f;
    private static final float MAX_ALPHA = 1f;
    
    private Paint paint1;
    private Paint paint2;
    private float size;
    private int baseColor;
    private int animationDuration;
    
    private float scale1 = MIN_SCALE;
    private float alpha1 = MAX_ALPHA;
    private float scale2 = MIN_SCALE;
    private float alpha2 = MAX_ALPHA;
    
    private AnimatorSet animatorSet;
    
    public CommonLoadingView(Context context) {
        this(context, null);
    }
    
    public CommonLoadingView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public CommonLoadingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }
    
    private void init(Context context, AttributeSet attrs) {
        // 初始化画笔
        paint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint1.setStyle(Paint.Style.FILL);
        
        paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint2.setStyle(Paint.Style.FILL);
        
        // 默认值
        float density = context.getResources().getDisplayMetrics().density;
        size = DEFAULT_SIZE * density;
        baseColor = ContextCompat.getColor(context, R.color.colorAccent);
        animationDuration = DEFAULT_ANIMATION_DURATION;
        
        // 读取自定义属性（如果有的话）
        if (attrs != null) {
            // 这里可以添加自定义属性的读取逻辑
        }
        
        updatePaintColors();
        startAnimation();
    }
    
    private void updatePaintColors() {
        // 设置第一个圆的颜色和透明度
        int color1 = setColorAlpha(baseColor, alpha1);
        paint1.setColor(color1);
        
        // 设置第二个圆的颜色和透明度
        int color2 = setColorAlpha(baseColor, alpha2);
        paint2.setColor(color2);
    }
    
    private int setColorAlpha(int color, float alpha) {
        int alphaInt = Math.round(255 * alpha);
        return (color & 0x00FFFFFF) | (alphaInt << 24);
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int dimension = (int) size;
        dimension = Math.max(dimension, getSuggestedMinimumWidth());
        dimension = Math.max(dimension, getSuggestedMinimumHeight());
        
        setMeasuredDimension(
            resolveSize(dimension, widthMeasureSpec),
            resolveSize(dimension, heightMeasureSpec)
        );
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float maxRadius = Math.min(getWidth(), getHeight()) / 2f;
        
        // 绘制第一个圆
        float radius1 = maxRadius * scale1;
        if (radius1 > 0) {
            canvas.drawCircle(centerX, centerY, radius1, paint1);
        }
        
        // 绘制第二个圆
        float radius2 = maxRadius * scale2;
        if (radius2 > 0) {
            canvas.drawCircle(centerX, centerY, radius2, paint2);
        }
    }
    
    private void startAnimation() {
        if (animatorSet != null) {
            animatorSet.cancel();
        }
        
        animatorSet = new AnimatorSet();
        
        // 第一个圆的动画
        ValueAnimator scaleAnimator1 = createScaleAnimator(1, 0);
        ValueAnimator alphaAnimator1 = createAlphaAnimator(1, 0);
        
        // 第二个圆的动画（延迟半个周期）
        ValueAnimator scaleAnimator2 = createScaleAnimator(2, animationDuration / 2);
        ValueAnimator alphaAnimator2 = createAlphaAnimator(2, animationDuration / 2);
        
        animatorSet.playTogether(scaleAnimator1, alphaAnimator1, scaleAnimator2, alphaAnimator2);
        animatorSet.start();
    }
    
    private ValueAnimator createScaleAnimator(final int circleIndex, int startDelay) {
        ValueAnimator animator = ValueAnimator.ofFloat(MIN_SCALE, MAX_SCALE, MIN_SCALE);
        animator.setDuration(animationDuration / 2);
        animator.setStartDelay(startDelay);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = (Float) animation.getAnimatedValue();
                if (circleIndex == 1) {
                    scale1 = value;
                } else {
                    scale2 = value;
                }
                invalidate();
            }
        });
        
        return animator;
    }
    
    private ValueAnimator createAlphaAnimator(final int circleIndex, int startDelay) {
        ValueAnimator animator = ValueAnimator.ofFloat(MAX_ALPHA, MIN_ALPHA, MAX_ALPHA);
        animator.setDuration(animationDuration / 2);
        animator.setStartDelay(startDelay);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = (Float) animation.getAnimatedValue();
                if (circleIndex == 1) {
                    alpha1 = value;
                } else {
                    alpha2 = value;
                }
                updatePaintColors();
                invalidate();
            }
        });
        
        return animator;
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animatorSet != null) {
            animatorSet.cancel();
        }
    }
    
    // 公共方法用于自定义配置
    
    /**
     * 设置基础颜色
     */
    public void setBaseColor(int color) {
        this.baseColor = color;
        updatePaintColors();
        invalidate();
    }
    
    /**
     * 设置大小（像素）
     */
    public void setSize(float size) {
        this.size = size;
        requestLayout();
    }
    
    /**
     * 设置动画持续时间
     */
    public void setAnimationDuration(int duration) {
        this.animationDuration = duration;
        startAnimation();
    }
    
    /**
     * 开始动画
     */
    public void start() {
        startAnimation();
    }
    
    /**
     * 停止动画
     */
    public void stop() {
        if (animatorSet != null) {
            animatorSet.cancel();
        }
    }
}
