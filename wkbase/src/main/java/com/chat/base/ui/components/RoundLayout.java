package com.chat.base.ui.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.chat.base.R;

public class RoundLayout extends FrameLayout {

    private static final int DEFAULT_CORNER = 5;
    private static final int DEFAULT_ALL_CORNER = Integer.MIN_VALUE;

    private int bgColor = Color.TRANSPARENT;
    private float allCorner = DEFAULT_ALL_CORNER;
    private float topLeftCorner = DEFAULT_CORNER;
    private float topRightCorner = DEFAULT_CORNER;
    private float bottomRightCorner = DEFAULT_CORNER;
    private float bottomLeftCorner = DEFAULT_CORNER;

    public RoundLayout(Context context) {
        super(context);
        setViewBackground();
    }

    public RoundLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        extractAttribute(context, attrs);
        setViewBackground();
    }

    public RoundLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        extractAttribute(context, attrs);
        setViewBackground();
    }

    public RoundLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        extractAttribute(context, attrs);
        setViewBackground();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int count = getChildCount();

        if (count > 1) {
            throw new IllegalStateException("View can have only single child");
        }

        super.onLayout(changed, l, t, r, b);
    }

    private void extractAttribute(Context context, AttributeSet attrs) {
        @SuppressLint("CustomViewStyleable") TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.TextViewCorner, 0, 0);
        try {
            bgColor = ta.getColor(R.styleable.TextViewCorner_bgColor, Color.TRANSPARENT);
            allCorner = ta.getDimension(R.styleable.TextViewCorner_allCorner, DEFAULT_ALL_CORNER);
            topLeftCorner = ta.getDimension(R.styleable.TextViewCorner_topLeftCorner, DEFAULT_CORNER);
            topRightCorner = ta.getDimension(R.styleable.TextViewCorner_topRightCorner, DEFAULT_CORNER);
            bottomRightCorner = ta.getDimension(R.styleable.TextViewCorner_bottomRightCorner, DEFAULT_CORNER);
            bottomLeftCorner = ta.getDimension(R.styleable.TextViewCorner_bottomLeftCorner, DEFAULT_CORNER);
        } finally {
            ta.recycle();
        }
    }

    public void setCorner(int all) {
        allCorner = all;
        setViewBackground();
    }

    public void setCorner(int topLeft, int topRight, int bottomRight, int bottomLeft) {
        this.topLeftCorner = topLeft;
        this.topRightCorner = topRight;
        this.bottomRightCorner = bottomRight;
        this.bottomLeftCorner = bottomLeft;
        setViewBackground();
    }

    public void setBgColor(int color) {
        bgColor = color;
        setViewBackground();
    }

    private void setViewBackground() {
        Drawable drawable;
        if (allCorner > 0) {
            drawable = DrawableHelper.getCornerDrawable(
                    allCorner,
                    allCorner,
                    allCorner,
                    allCorner,
                    bgColor);
        } else {
            drawable = DrawableHelper.getCornerDrawable(
                    topLeftCorner,
                    topRightCorner,
                    bottomLeftCorner,
                    bottomRightCorner,
                    bgColor);
        }

        DrawableHelper.setRoundBackground(this, drawable);
    }
}
