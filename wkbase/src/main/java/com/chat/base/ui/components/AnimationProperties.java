package com.chat.base.ui.components;

import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.util.Property;
import android.view.animation.OvershootInterpolator;


public class AnimationProperties {

    public static OvershootInterpolator overshootInterpolator = new OvershootInterpolator(1.9f);

    public static abstract class FloatProperty<T> extends Property<T, Float> {

        public FloatProperty(String name) {
            super(Float.class, name);
        }

        public abstract void setValue(T object, float value);

        @Override
        final public void set(T object, Float value) {
            setValue(object, value);
        }
    }

    public static abstract class IntProperty<T> extends Property<T, Integer> {

        public IntProperty(String name) {
            super(Integer.class, name);
        }

        public abstract void setValue(T object, int value);

        @Override
        final public void set(T object, Integer value) {
            setValue(object, value);
        }
    }

    public static final Property<Paint, Integer> PAINT_ALPHA = new IntProperty<Paint>("alpha") {
        @Override
        public void setValue(Paint object, int value) {
            object.setAlpha(value);
        }

        @Override
        public Integer get(Paint object) {
            return object.getAlpha();
        }
    };

    public static final Property<Paint, Integer> PAINT_COLOR = new IntProperty<Paint>("color") {
        @Override
        public void setValue(Paint object, int value) {
            object.setColor(value);
        }

        @Override
        public Integer get(Paint object) {
            return object.getColor();
        }
    };

    public static final Property<ColorDrawable, Integer> COLOR_DRAWABLE_ALPHA = new IntProperty<ColorDrawable>("alpha") {
        @Override
        public void setValue(ColorDrawable object, int value) {
            object.setAlpha(value);
        }

        @Override
        public Integer get(ColorDrawable object) {
            return object.getAlpha();
        }
    };

    public static final Property<ShapeDrawable, Integer> SHAPE_DRAWABLE_ALPHA = new IntProperty<ShapeDrawable>("alpha") {
        @Override
        public void setValue(ShapeDrawable object, int value) {
            object.getPaint().setAlpha(value);
        }

        @Override
        public Integer get(ShapeDrawable object) {
            return object.getPaint().getAlpha();
        }
    };

}
