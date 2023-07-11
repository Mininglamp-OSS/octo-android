package com.chat.base.ui.components;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.view.View;

import androidx.annotation.ColorInt;

public class DrawableHelper {

    public static void setRoundBackground(View view, Drawable drawable) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.setBackground(drawable);
        } else {
            view.setBackgroundDrawable(drawable);
        }
    }

    public static Drawable getCornerDrawable(float topLeft,
                                             float topRight,
                                             float bottomLeft,
                                             float bottomRight,
                                             @ColorInt int color) {

        float[] outerR = new float[8];
        outerR[0] = topLeft;
        outerR[1] = topLeft;
        outerR[2] = topRight;
        outerR[3] = topRight;
        outerR[4] = bottomRight;
        outerR[5] = bottomRight;
        outerR[6] = bottomLeft;
        outerR[7] = bottomLeft;

        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setShape(new RoundRectShape(outerR, null, null));
        drawable.getPaint().setColor(color);

        return drawable;
    }
}
