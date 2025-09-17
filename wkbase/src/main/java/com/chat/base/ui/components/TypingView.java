package com.chat.base.ui.components;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.chat.base.R;

/**
 * 正在输入动画视图
 * 显示三个圆点的弹跳动画效果
 */
public class TypingView extends View {
    
    private static final int DEFAULT_DOT_COUNT = 3;
    private static final int DEFAULT_DOT_RADIUS = 4; // dp - 减小圆点大小
    private static final int DEFAULT_DOT_SPACING = 8; // dp - 减小间距
    private static final int DEFAULT_ANIMATION_DURATION = 1400; // ms
    private static final int DEFAULT_DELAY_BETWEEN_DOTS = 160; // ms
    
    private Paint paint;
    private int dotCount = DEFAULT_DOT_COUNT;
    private float dotRadius;
    private float dotSpacing;
    private int dotColor;
    private int animationDuration;
    private int delayBetweenDots;
    
    private float[] dotScales;
    private AnimatorSet animatorSet;
    
    public TypingView(Context context) {
        this(context, null);
    }
    
    public TypingView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public TypingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }
    
    private void init(Context context, AttributeSet attrs) {
        // 初始化画笔
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        
        // 默认值
        float density = context.getResources().getDisplayMetrics().density;
        dotRadius = DEFAULT_DOT_RADIUS * density;
        dotSpacing = DEFAULT_DOT_SPACING * density;
        dotColor = ContextCompat.getColor(context, R.color.colorAccent);
        animationDuration = DEFAULT_ANIMATION_DURATION;
        delayBetweenDots = DEFAULT_DELAY_BETWEEN_DOTS;
        
        // 读取自定义属性（如果有的话）
        if (attrs != null) {
            // 这里可以添加自定义属性的读取逻辑
            // TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TypingView);
            // ... 读取属性
            // a.recycle();
        }
        
        paint.setColor(dotColor);
        
        // 初始化圆点缩放数组
        dotScales = new float[dotCount];
        for (int i = 0; i < dotCount; i++) {
            dotScales[i] = 0.5f; // 初始缩放比例，稍微大一点以便可见
        }
        
        startAnimation();
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // 计算所需的宽度：3个圆点的直径 + 2个间距 + 左右边距
        int width = (int) (dotCount * dotRadius * 2 + (dotCount - 1) * dotSpacing + dotRadius * 2);
        int height = (int) (dotRadius * 2 + dotRadius); // 增加一些垂直边距

        width = Math.max(width, getSuggestedMinimumWidth());
        height = Math.max(height, getSuggestedMinimumHeight());

        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec)
        );
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float centerY = getHeight() / 2f;
        float totalWidth = dotCount * dotRadius * 2 + (dotCount - 1) * dotSpacing;
        float startX = (getWidth() - totalWidth) / 2f + dotRadius; // 居中显示

        for (int i = 0; i < dotCount; i++) {
            float centerX = startX + i * (dotRadius * 2 + dotSpacing);
            float scaledRadius = dotRadius * dotScales[i];
            canvas.drawCircle(centerX, centerY, scaledRadius, paint);
        }
    }
    
    private void startAnimation() {
        if (animatorSet != null) {
            animatorSet.cancel();
        }
        
        animatorSet = new AnimatorSet();
        Animator[] animators = new Animator[dotCount];
        
        for (int i = 0; i < dotCount; i++) {
            animators[i] = createDotAnimator(i);
        }
        
        animatorSet.playTogether(animators);
        animatorSet.start();
    }
    
    private ValueAnimator createDotAnimator(final int index) {
        ValueAnimator animator = ValueAnimator.ofFloat(0.5f, 1.0f, 0.5f);
        animator.setDuration(animationDuration / 2);
        animator.setStartDelay(index * delayBetweenDots);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());

        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                dotScales[index] = (Float) animation.getAnimatedValue();
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
     * 设置圆点颜色
     */
    public void setDotColor(int color) {
        this.dotColor = color;
        paint.setColor(color);
        invalidate();
    }
    
    /**
     * 设置圆点半径（像素）
     */
    public void setDotRadius(float radius) {
        this.dotRadius = radius;
        requestLayout();
    }
    
    /**
     * 设置圆点间距（像素）
     */
    public void setDotSpacing(float spacing) {
        this.dotSpacing = spacing;
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
     * 设置圆点之间的延迟时间
     */
    public void setDelayBetweenDots(int delay) {
        this.delayBetweenDots = delay;
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
