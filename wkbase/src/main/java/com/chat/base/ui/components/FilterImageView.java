package com.chat.base.ui.components;

import android.content.Context;
import android.graphics.ColorMatrixColorFilter;
import android.util.AttributeSet;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.CornerFamily;
import com.chat.base.R;
import com.chat.base.utils.AndroidUtilities;


public class FilterImageView extends ShapeableImageView {
    public final float[] BG_PRESSED = new float[]{1, 0, 0, 0, -50, 0, 1,
            0, 0, -50, 0, 0, 1, 0, -50, 0, 0, 0, 1, 0};
    public final float[] BG_NOT_PRESSED = new float[]{1, 0, 0, 0, 0, 0,
            1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0};

    public FilterImageView(Context context) {
        super(context);
        init();

    }

    public FilterImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }


    public FilterImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    void init() {
        setStrokeColorResource(R.color.borderColor);
        setStrokeWidth(AndroidUtilities.dp(0.1f));
        setShapeAppearanceModel(getShapeAppearanceModel()
                .toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, AndroidUtilities.dp(10))
                .build());
    }

    public void setStrokeWidth(float width) {
        super.setStrokeWidth(AndroidUtilities.dp(width));
    }

    public void setAllCorners(int cornerSize) {
        setShapeAppearanceModel(getShapeAppearanceModel()
                .toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, AndroidUtilities.dp(cornerSize))
                .build());
    }

    public void setCorners(int topLeft, int topRight, int bottomLeft, int bottomRight) {
        setShapeAppearanceModel(getShapeAppearanceModel()
                .toBuilder()
                .setTopLeftCorner(CornerFamily.ROUNDED, AndroidUtilities.dp(topLeft))
                .setTopRightCorner(CornerFamily.ROUNDED, AndroidUtilities.dp(topRight))
                .setBottomRightCorner(CornerFamily.ROUNDED, AndroidUtilities.dp(bottomRight))
                .setBottomLeftCorner(CornerFamily.ROUNDED, AndroidUtilities.dp(bottomLeft))
                .build());
    }

    @Override
    public void setPressed(boolean pressed) {
        updateView(pressed);
        super.setPressed(pressed);
    }

    private void updateView(boolean pressed) {
        if (pressed) {
            this.setDrawingCacheEnabled(true);
            this.setColorFilter(new ColorMatrixColorFilter(BG_PRESSED));
            this.getDrawable().setColorFilter(new ColorMatrixColorFilter(BG_PRESSED));
        } else {
            this.setColorFilter(new ColorMatrixColorFilter(BG_NOT_PRESSED));
            this.getDrawable().setColorFilter(
                    new ColorMatrixColorFilter(BG_NOT_PRESSED));
        }
    }
}
