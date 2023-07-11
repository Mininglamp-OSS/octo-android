package com.chat.base.ui.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import com.chat.base.R;
import com.chat.base.utils.AndroidUtilities;

public class RoundTextView extends AppCompatTextView {
    int borderWidth = AndroidUtilities.dp(1);
    int borderColor = 0;
    float radius = 0f;
    float topLeftRadius = 0f;
    float topRightRadius = 0f;
    float bottomLeftRadius = 0f;
    float bottomRightRadius = 0f;
    int backgroundColor = 0;

    public RoundTextView(Context context) {
        super(context);
        reset();
    }

    public RoundTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public RoundTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.RoundTextView, 0, 0);

        if (attributes != null) {
            borderWidth = attributes.getDimensionPixelSize(R.styleable.RoundTextView_tvBorderWidth, 0);
            borderColor = attributes.getColor(R.styleable.RoundTextView_tvBorderColor, Color.BLACK);
            radius = attributes.getDimension(R.styleable.RoundTextView_tvCornerRadius, 0);
            topLeftRadius = attributes.getDimension(R.styleable.RoundTextView_tvTopLeftRadius, 0);
            topRightRadius = attributes.getDimension(R.styleable.RoundTextView_tvTopRightRadius, 0);
            bottomLeftRadius = attributes.getDimension(R.styleable.RoundTextView_tvBottomLeftRadius, 0);
            bottomRightRadius = attributes.getDimension(R.styleable.RoundTextView_tvBottomRightRadius, 0);
            backgroundColor = attributes.getColor(R.styleable.RoundTextView_tvBackgroundColor, Color.WHITE);
            attributes.recycle();
            reset();
        }
    }

    private void reset() {
        GradientDrawable gd = new GradientDrawable();//创建drawable
        gd.setColor(backgroundColor);
        if (borderWidth > 0) {
            gd.setStroke(borderWidth, borderColor);
        }

        if (topLeftRadius > 0 || topRightRadius > 0 || bottomLeftRadius > 0 || bottomRightRadius > 0) {
            float[] radii = new float[]{
                    topLeftRadius, topLeftRadius,
                    topRightRadius, topRightRadius,
                    bottomRightRadius, bottomRightRadius,
                    bottomLeftRadius, bottomLeftRadius
            };
            gd.setCornerRadii(radii);
        } else {
            gd.setCornerRadius(radius);
        }

        this.setBackground(gd);
    }

    public void setBackGroundColor(int color) {
        GradientDrawable myGrad = (GradientDrawable) getBackground();
        myGrad.setColor(color);
        backgroundColor = color;
        // reset();
    }

    public void setBorderColor(int color) {
        borderColor = color;
        borderWidth = AndroidUtilities.dp(1);
        reset();
    }

    public void setAllRadius(float radius) {
        topLeftRadius = topRightRadius = bottomRightRadius = bottomLeftRadius = AndroidUtilities.dp(radius);
        reset();
    }
}
