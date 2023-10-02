package com.chat.base.ui;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.NinePatchDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.util.Log;
import android.util.StateSet;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.chat.base.WKBaseApplication;
import com.chat.base.R;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.ui.components.RoundTextView;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.SvgHelper;

import org.telegram.ui.Components.RLottieDrawable;

import java.lang.reflect.Method;

public class Theme {
    public static int colorAccount = 0xFFf65835;
    public static int colorAccountDisable = 0x95F65835;
    public static int color999 = 0xFF999999;
    public static int colorCCC = 0xFFCCCCCC;
    public static int pressedColor = 0xff8c9197;
    private static final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final String LIGHT_MODE = "light";
    public static final String DARK_MODE = "dark";
    public static final String DEFAULT_MODE = "default";
    public static final String wk_theme_pref = "wk_theme_pref";

    //    public static final int[][] defaultColorsLight = new int[][]{
//            new int[]{0xa6B0CDEB, 0xa69FB0EA, 0xa6BBEAD5, 0xa6B2E3DD},
//            new int[]{0xa640CDDE, 0xa6AC86ED, 0xa6E984D8, 0xa6EFD359},
//            new int[]{0xa6DBDDBB, 0xa66BA587, 0xa6D5D88D, 0xa688B884},
//            new int[]{0xa6DAEACB, 0xa6A2B4FF, 0xa6ECCBFF, 0xa6B9E2FF},
//            new int[]{0xa6B2B1EE, 0xa6D4A7C9, 0xa66C8CD4, 0xa64CA3D4},
//            new int[]{0xa6DCEB92, 0xa68FE1D6, 0xa667A3F2, 0xa685D685},
//            new int[]{0xa68ADBF2, 0xa6888DEC, 0xa6E39FEA, 0xa6679CED},
//            new int[]{0xa6FFC3B2, 0xa6E2C0FF, 0xa6FFE7B2, 0xa6FDFF8C},
//            new int[]{0xa697BEEB, 0xa6B1E9EA, 0xa6C6B1EF, 0xa6EFB7DC},
//            new int[]{0xa6E4B2EA, 0xa68376C2, 0xa6EAB9D9, 0xa6B493E6},
//            new int[]{0xa6D1A3E2, 0xa6EDD594, 0xa6E5A1D0, 0xa6ECD893},
//            new int[]{0xa6EAA36E, 0xa6F0E486, 0xa6F29EBF, 0xa6E8C06E},
//            new int[]{0xa67EC289, 0xa6E4D573, 0xa6AFD677, 0xa6F0C07A},
//    };
    public static final int[][] defaultColorsDark = new int[][]{
            new int[]{0xa65f4167, 0xa6171c2f, 0xa6363d4d, 0xa628293b},
            new int[]{0xa66c3407, 0xa6411f05, 0xa61b0d02, 0xa6080401},
            new int[]{0xa63c0b42, 0xa6210324, 0xa6120314, 0xa62f243f},
            new int[]{0xa6645b12, 0xa6463f09, 0xa6353003, 0xa6E8C06E},
            new int[]{0xa6135360, 0xa607333d, 0xa6021a1f, 0xa6000000},
            new int[]{0xa628072c, 0xa61b1346, 0xa64e0b36, 0xa66e0a6e},
            new int[]{0xa60e4805, 0xa64b044c, 0xa6094c4c, 0xa6555404},
            new int[]{0xa6512908, 0xa6590d41, 0xa66e1606, 0xa6060f6e},
            new int[]{0xa6644141, 0xa6595326, 0xa6590526, 0xa61b1d2c},
            new int[]{0xa67d2f58, 0xa6503f27, 0xa6052037, 0xa6201144},
            new int[]{0xa6144260, 0xa6421b13, 0xa611234e, 0xa6213b4a},
            new int[]{0xa64e1455, 0xa61a1242, 0xa63d0a2b, 0xa628242e},
            new int[]{0xa6482002, 0xa6393303, 0xa63d031a, 0xa62e2002},
    };

    public static GradientDrawable getBackground(int color, float radius, int width, int height) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setSize(AndroidUtilities.dp(width), AndroidUtilities.dp(height));
        d.setCornerRadius(AndroidUtilities.dp(radius));
        return d;
    }

    public static GradientDrawable getBackground(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(AndroidUtilities.dp(radius));
        return d;
    }

    public static GradientDrawable.Orientation getGradientOrientation(int gradientAngle) {
        switch (gradientAngle) {
            case 0:
                return GradientDrawable.Orientation.BOTTOM_TOP;
            case 90:
                return GradientDrawable.Orientation.LEFT_RIGHT;
            case 135:
                return GradientDrawable.Orientation.TL_BR;
            case 180:
                return GradientDrawable.Orientation.TOP_BOTTOM;
            case 225:
                return GradientDrawable.Orientation.TR_BL;
            case 270:
                return GradientDrawable.Orientation.RIGHT_LEFT;
            case 315:
                return GradientDrawable.Orientation.BR_TL;
            default:
                return GradientDrawable.Orientation.BL_TR;
        }
    }

    public static int getPressedColor() {
        int color = pressedColor;
        color = Color.argb(30, Color.red(color), Color.green(color), Color.blue(color));
        return color;
    }

    private static void applyTheme(@NonNull String themePref) {
        switch (themePref) {
            case LIGHT_MODE -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
            case DARK_MODE -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            }
            default -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY);
                }
            }
        }
    }

    public static void applyTheme() {
        String themePref =
                WKSharedPreferencesUtil.getInstance().getSP(Theme.wk_theme_pref, Theme.DEFAULT_MODE);
        Theme.applyTheme(themePref);
    }

    public static String getTheme() {
        return WKSharedPreferencesUtil.getInstance().getSP(Theme.wk_theme_pref, Theme.DEFAULT_MODE);
    }

    public static void setTheme(String s) {
        WKSharedPreferencesUtil.getInstance().putSP(Theme.wk_theme_pref, s);
        Theme.applyTheme(s);
    }

    public static boolean isSystemDarkMode(Context context) {
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        return uiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_YES;
    }

    public static boolean getDarkModeStatus(Context context) {
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int getSwitchViewTrackColor() {
        String wk_theme_pref = WKSharedPreferencesUtil.getInstance().getSP(Theme.wk_theme_pref, Theme.DEFAULT_MODE);
        if (wk_theme_pref.equals(DARK_MODE)) {
            return 0xFF585858;
        } else
            return Theme.colorCCC;
    }

    public static int getSwitchViewThumbColor() {
        String wk_theme_pref = WKSharedPreferencesUtil.getInstance().getSP(Theme.wk_theme_pref, Theme.DEFAULT_MODE);
        if (wk_theme_pref.equals(DARK_MODE)) {
            return 0xFF212223;
        } else
            return 0xFFFFFFFF;
    }

    public static boolean isDark() {
        String wk_theme_pref = WKSharedPreferencesUtil.getInstance().getSP(Theme.wk_theme_pref, Theme.DEFAULT_MODE);
        return wk_theme_pref.equals(DARK_MODE);
    }

    private static Drawable ticksSingleDrawable;
    private static Drawable ticksDoubleDrawable;

    public static Drawable getTicksSingleDrawable() {
        if (ticksSingleDrawable == null) {
            RLottieDrawable drawable = new RLottieDrawable(WKBaseApplication.getInstance().getContext(), R.raw.ticks_single, "ticks_single", AndroidUtilities.dp(22), AndroidUtilities.dp(22));
            drawable.setCurrentFrame(drawable.getFramesCount() - 1);
            ticksSingleDrawable = drawable.getCurrent();
        }
        return ticksSingleDrawable;
    }

    public static Drawable getTicksDoubleDrawable() {
        if (ticksDoubleDrawable == null) {
            RLottieDrawable drawable = new RLottieDrawable(WKBaseApplication.getInstance().getContext(), R.raw.ticks_double, "ticks_double", AndroidUtilities.dp(22), AndroidUtilities.dp(22));
            drawable.setCurrentFrame(drawable.getFramesCount() - 1);
            ticksDoubleDrawable = drawable.getCurrent();
        }
        return ticksDoubleDrawable;
    }

    public static RoundTextView getChannelCategoryTV(Context context, String text, int bgColor, int textColor, int borderColor) {
        RoundTextView roundTextView = new RoundTextView(context);
        roundTextView.setTextSize(12);
        roundTextView.setText(text);
        roundTextView.setPadding(AndroidUtilities.dp(2), 0, AndroidUtilities.dp(2), 0);
        roundTextView.setTextColor(textColor);
        roundTextView.setBorderColor(borderColor);
        roundTextView.setBackGroundColor(bgColor);
        roundTextView.setAllRadius(3);
        return roundTextView;
    }


    public static Drawable createRadSelectorDrawable(int color, int topRad, int bottomRad) {
        if (Build.VERSION.SDK_INT >= 21) {
            maskPaint.setColor(0xffffffff);
            Drawable maskDrawable = new RippleRadMaskDrawable(topRad, bottomRad);
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{color}
            );
            return new RippleDrawable(colorStateList, null, maskDrawable);
        } else {
            StateListDrawable stateListDrawable = new StateListDrawable();
            stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(color));
            stateListDrawable.addState(new int[]{android.R.attr.state_selected}, new ColorDrawable(color));
            stateListDrawable.addState(StateSet.WILD_CARD, new ColorDrawable(0x00000000));
            return stateListDrawable;
        }
    }


    public static class RippleRadMaskDrawable extends Drawable {
        private Path path = new Path();
        private float[] radii = new float[8];
        boolean invalidatePath = true;

        public RippleRadMaskDrawable(float top, float bottom) {
            radii[0] = radii[1] = radii[2] = radii[3] = AndroidUtilities.dp(top);
            radii[4] = radii[5] = radii[6] = radii[7] = AndroidUtilities.dp(bottom);
        }

        public RippleRadMaskDrawable(float topLeft, float topRight, float bottomRight, float bottomLeft) {
            radii[0] = radii[1] = AndroidUtilities.dp(topLeft);
            radii[2] = radii[3] = AndroidUtilities.dp(topRight);
            radii[4] = radii[5] = AndroidUtilities.dp(bottomRight);
            radii[6] = radii[7] = AndroidUtilities.dp(bottomLeft);
        }

        public void setRadius(float top, float bottom) {
            radii[0] = radii[1] = radii[2] = radii[3] = AndroidUtilities.dp(top);
            radii[4] = radii[5] = radii[6] = radii[7] = AndroidUtilities.dp(bottom);
            invalidatePath = true;
            invalidateSelf();
        }

        public void setRadius(float topLeft, float topRight, float bottomRight, float bottomLeft) {
            radii[0] = radii[1] = AndroidUtilities.dp(topLeft);
            radii[2] = radii[3] = AndroidUtilities.dp(topRight);
            radii[4] = radii[5] = AndroidUtilities.dp(bottomRight);
            radii[6] = radii[7] = AndroidUtilities.dp(bottomLeft);
            invalidatePath = true;
            invalidateSelf();
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            invalidatePath = true;
        }

        @Override
        public void draw(Canvas canvas) {
            if (invalidatePath) {
                invalidatePath = false;
                path.reset();
                AndroidUtilities.rectTmp.set(getBounds());
                path.addRoundRect(AndroidUtilities.rectTmp, radii, Path.Direction.CW);
            }
            canvas.drawPath(path, maskPaint);
        }

        @Override
        public void setAlpha(int alpha) {

        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return PixelFormat.UNKNOWN;
        }
    }

    public static void setColorFilter(Context context, ImageView imageView, int color) {
        imageView.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(context, color), PorterDuff.Mode.MULTIPLY));
    }


    public static void setColorFilter(ImageView imageView, int color) {
        imageView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
    }


    private static Method StateListDrawable_getStateDrawableMethod;

    @SuppressLint("PrivateApi")
    private static Drawable getStateDrawable(Drawable drawable, int index) {
        if (Build.VERSION.SDK_INT >= 29 && drawable instanceof StateListDrawable) {
            return ((StateListDrawable) drawable).getStateDrawable(index);
        } else {
            if (StateListDrawable_getStateDrawableMethod == null) {
                try {
                    StateListDrawable_getStateDrawableMethod = StateListDrawable.class.getDeclaredMethod("getStateDrawable", int.class);
                } catch (Throwable ignore) {

                }
            }
            if (StateListDrawable_getStateDrawableMethod == null) {
                return null;
            }
            try {
                return (Drawable) StateListDrawable_getStateDrawableMethod.invoke(drawable, index);
            } catch (Exception ignore) {

            }
            return null;
        }
    }

    public static void setSelectorDrawableColor(Drawable drawable, int color, boolean selected) {
        if (drawable instanceof StateListDrawable) {
            try {
                Drawable state;
                if (selected) {
                    state = getStateDrawable(drawable, 0);
                    if (state instanceof ShapeDrawable) {
                        ((ShapeDrawable) state).getPaint().setColor(color);
                    } else {
                        state.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                    }
                    state = getStateDrawable(drawable, 1);
                } else {
                    state = getStateDrawable(drawable, 2);
                }
                if (state instanceof ShapeDrawable) {
                    ((ShapeDrawable) state).getPaint().setColor(color);
                } else {
                    state.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                }
            } catch (Throwable ignore) {

            }
        } else if (Build.VERSION.SDK_INT >= 21 && drawable instanceof RippleDrawable) {
            RippleDrawable rippleDrawable = (RippleDrawable) drawable;
            if (selected) {
                rippleDrawable.setColor(new ColorStateList(
                        new int[][]{StateSet.WILD_CARD},
                        new int[]{color}
                ));
            } else {
                if (rippleDrawable.getNumberOfLayers() > 0) {
                    Drawable drawable1 = rippleDrawable.getDrawable(0);
                    if (drawable1 instanceof ShapeDrawable) {
                        ((ShapeDrawable) drawable1).getPaint().setColor(color);
                    } else {
                        drawable1.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                    }
                }
            }
        }
    }

    public static void setPressedBackground(View imageView) {
        if (Build.VERSION.SDK_INT >= 21) {
            imageView.setBackground(createSelectorDrawable(getPressedColor()));
        }
    }

    public static Drawable createSelectorDrawable(int color) {
        return createSelectorDrawable(color, 1, -1);
    }

    public static Drawable createSelectorDrawable(int color, int maskType) {
        return createSelectorDrawable(color, maskType, -1);
    }

    public static Drawable createSelectorDrawable(int color, int maskType, int radius) {
        Drawable drawable;
        if (Build.VERSION.SDK_INT >= 21) {
            Drawable maskDrawable = null;
            if ((maskType == 1 || maskType == 5) && Build.VERSION.SDK_INT >= 23) {
                maskDrawable = null;
            } else if (maskType == 1 || maskType == 3 || maskType == 4 || maskType == 5 || maskType == 6 || maskType == 7) {
                maskPaint.setColor(0xffffffff);
                maskDrawable = new Drawable() {

                    RectF rect;

                    @Override
                    public void draw(Canvas canvas) {
                        android.graphics.Rect bounds = getBounds();
                        if (maskType == 7) {
                            if (rect == null) {
                                rect = new RectF();
                            }
                            rect.set(bounds);
                            canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), maskPaint);
                        } else {
                            int rad;
                            if (maskType == 1 || maskType == 6) {
                                rad = AndroidUtilities.dp(20);
                            } else if (maskType == 3) {
                                rad = (Math.max(bounds.width(), bounds.height()) / 2);
                            } else {
                                rad = (int) Math.ceil(Math.sqrt((bounds.left - bounds.centerX()) * (bounds.left - bounds.centerX()) + (bounds.top - bounds.centerY()) * (bounds.top - bounds.centerY())));
                            }
                            canvas.drawCircle(bounds.centerX(), bounds.centerY(), rad, maskPaint);
                        }
                    }

                    @Override
                    public void setAlpha(int alpha) {

                    }

                    @Override
                    public void setColorFilter(ColorFilter colorFilter) {

                    }

                    @Override
                    public int getOpacity() {
                        return PixelFormat.UNKNOWN;
                    }
                };
            } else if (maskType == 2) {
                maskDrawable = new ColorDrawable(0xffffffff);
            }
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{color}
            );
            RippleDrawable rippleDrawable = new RippleDrawable(colorStateList, null, maskDrawable);
            if (Build.VERSION.SDK_INT >= 23) {
                if (maskType == 1) {
                    rippleDrawable.setRadius(radius <= 0 ? AndroidUtilities.dp(20) : radius);
                } else if (maskType == 5) {
                    rippleDrawable.setRadius(RippleDrawable.RADIUS_AUTO);
                }
            }
            return rippleDrawable;
        } else {
            StateListDrawable stateListDrawable = new StateListDrawable();
            stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(color));
            stateListDrawable.addState(new int[]{android.R.attr.state_selected}, new ColorDrawable(color));
            stateListDrawable.addState(StateSet.WILD_CARD, new ColorDrawable(0x00000000));
            return stateListDrawable;
        }
    }


    @TargetApi(21)
    @SuppressLint("DiscouragedPrivateApi")
    public static void setRippleDrawableForceSoftware(RippleDrawable drawable) {
        if (drawable == null) {
            return;
        }
        try {
            Method method = RippleDrawable.class.getDeclaredMethod("setForceSoftware", boolean.class);
            method.invoke(drawable, true);
        } catch (Throwable ignore) {

        }
    }

    public static Drawable createRoundRectDrawable(int topRad, int bottomRad, int defaultColor) {
        ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(new float[]{topRad, topRad, topRad, topRad, bottomRad, bottomRad, bottomRad, bottomRad}, null, null));
        defaultDrawable.getPaint().setColor(defaultColor);
        return defaultDrawable;
    }


    public static Drawable createRoundRectDrawable(int rad, int defaultColor) {
        ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
        defaultDrawable.getPaint().setColor(defaultColor);
        return defaultDrawable;
    }

    public static Drawable createEmojiIconSelectorDrawable(Context context, int resource, int defaultColor, int pressedColor) {
        Resources resources = context.getResources();
        Drawable defaultDrawable = resources.getDrawable(resource).mutate();
        if (defaultColor != 0) {
            defaultDrawable.setColorFilter(new PorterDuffColorFilter(defaultColor, PorterDuff.Mode.MULTIPLY));
        }
        Drawable pressedDrawable = resources.getDrawable(resource).mutate();
        if (pressedColor != 0) {
            pressedDrawable.setColorFilter(new PorterDuffColorFilter(pressedColor, PorterDuff.Mode.MULTIPLY));
        }
        StateListDrawable stateListDrawable = new StateListDrawable() {
            @Override
            public boolean selectDrawable(int index) {
                if (Build.VERSION.SDK_INT < 21) {
                    Drawable drawable = Theme.getStateDrawable(this, index);
                    ColorFilter colorFilter = null;
                    if (drawable instanceof BitmapDrawable) {
                        colorFilter = ((BitmapDrawable) drawable).getPaint().getColorFilter();
                    } else if (drawable instanceof NinePatchDrawable) {
                        colorFilter = ((NinePatchDrawable) drawable).getPaint().getColorFilter();
                    }
                    boolean result = super.selectDrawable(index);
                    if (colorFilter != null) {
                        drawable.setColorFilter(colorFilter);
                    }
                    return result;
                }
                return super.selectDrawable(index);
            }
        };
        stateListDrawable.setEnterFadeDuration(1);
        stateListDrawable.setExitFadeDuration(200);
        stateListDrawable.addState(new int[]{android.R.attr.state_selected}, pressedDrawable);
        stateListDrawable.addState(new int[]{}, defaultDrawable);
        return stateListDrawable;
    }

    public static Bitmap createBitmap(int width, int height, int color) {
        Bitmap bitmap = Bitmap.createBitmap(width, height,
                Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
//        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setTextSize(100);
        paint.setColor(Color.GREEN);
        paint.setFlags(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
//        canvas.drawText("CSDN", 100, 100, paint);
        return bitmap;
    }

    public static Bitmap drawBitmapBg(int color, Bitmap orginBitmap) {
        Paint paint = new Paint();
        paint.setColor(color);
        Bitmap bitmap = Bitmap.createBitmap(orginBitmap.getWidth(),
                orginBitmap.getHeight(), orginBitmap.getConfig());
        Canvas canvas = new Canvas(orginBitmap);
        canvas.drawRect(0, 0, orginBitmap.getWidth(), orginBitmap.getHeight(), paint);
        canvas.drawBitmap(orginBitmap, 0, 0, paint);
        return bitmap;

    }


    public static Drawable createSimpleSelectorRoundRectDrawable(int rad, int defaultColor, int pressedColor) {
        return createSimpleSelectorRoundRectDrawable(rad, defaultColor, pressedColor, pressedColor);
    }

    public static Drawable createSimpleSelectorRoundRectDrawable(int rad, int defaultColor, int pressedColor, int maskColor) {
        ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
        defaultDrawable.getPaint().setColor(defaultColor);
        ShapeDrawable pressedDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
        pressedDrawable.getPaint().setColor(maskColor);
        if (Build.VERSION.SDK_INT >= 21) {
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{pressedColor}
            );
            return new RippleDrawable(colorStateList, defaultDrawable, pressedDrawable);
        } else {
            StateListDrawable stateListDrawable = new StateListDrawable();
            stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressedDrawable);
            stateListDrawable.addState(new int[]{android.R.attr.state_selected}, pressedDrawable);
            stateListDrawable.addState(StateSet.WILD_CARD, defaultDrawable);
            return stateListDrawable;
        }
    }


    public static Drawable getRoundRectSelectorDrawable(int color) {
        return getRoundRectSelectorDrawable(AndroidUtilities.dp(3), color);
    }

    public static Drawable getRoundRectSelectorDrawable(int corners, int color) {
        if (Build.VERSION.SDK_INT >= 21) {
            Drawable maskDrawable = createRoundRectDrawable(corners, 0xffffffff);
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{(color & 0x00ffffff) | 0x19000000}
            );
            return new RippleDrawable(colorStateList, null, maskDrawable);
        } else {
            StateListDrawable stateListDrawable = new StateListDrawable();
            stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, createRoundRectDrawable(corners, (color & 0x00ffffff) | 0x19000000));
            stateListDrawable.addState(new int[]{android.R.attr.state_selected}, createRoundRectDrawable(corners, (color & 0x00ffffff) | 0x19000000));
            stateListDrawable.addState(StateSet.WILD_CARD, new ColorDrawable(0x00000000));
            return stateListDrawable;
        }
    }

}
