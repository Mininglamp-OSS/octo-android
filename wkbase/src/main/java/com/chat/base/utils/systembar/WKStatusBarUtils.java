package com.chat.base.utils.systembar;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.ColorInt;

import com.chat.base.utils.AndroidUtilities;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 2019-05-07 11:17
 * 系统状态栏
 */
public class WKStatusBarUtils {

    public static boolean supportTransparentStatusBar() {
        return WKOSUtils.isMiui()
                || WKOSUtils.isFlyme()
                || (WKOSUtils.isOppo() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                || Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    /**
     * 设置状态栏透明
     *
     * @param window
     */
    public static void transparentStatusBar(Window window) {
        if (WKOSUtils.isMiui() || WKOSUtils.isFlyme()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                transparentStatusBarAbove21(window);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            }
        } else if ((WKOSUtils.isOppo() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)) {
            transparentStatusBarAbove21(window);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            transparentStatusBarAbove21(window);
        }
    }

    @TargetApi(21)
    private static void transparentStatusBarAbove21(Window window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        window.setStatusBarColor(Color.TRANSPARENT);
    }

    /**
     * 设置状态栏图标白色主题
     *
     * @param window
     */
    public static void setLightMode(Window window) {
        if (WKOSUtils.isMiui()) {
            setMIUIStatusBarDarkMode(window, false);
        } else if (WKOSUtils.isFlyme()) {
            setFlymeStatusBarDarkMode(window, false);
        } else if (WKOSUtils.isOppo()) {
            setOppoStatusBarDarkMode(window, false);
        } else {
            setStatusBarDarkMode(window, false);
        }
    }

    /**
     * 设置状态栏图片黑色主题
     *
     * @param window
     */
    public static void setDarkMode(Window window) {
        if (WKOSUtils.isMiui()) {
            setMIUIStatusBarDarkMode(window, true);
        } else if (WKOSUtils.isFlyme()) {
            setFlymeStatusBarDarkMode(window, true);
        } else if (WKOSUtils.isOppo()) {
            setOppoStatusBarDarkMode(window, true);
        } else {
            setStatusBarDarkMode(window, true);
        }
    }

    private static void setStatusBarDarkMode(Window window, boolean darkMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (darkMode) {
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            } else {
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            }
        }
    }

    private static void setMIUIStatusBarDarkMode(Window window, boolean darkMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Class<? extends Window> clazz = window.getClass();
            try {
                Class<?> layoutParams = Class.forName("android.view.MiuiWindowManager$LayoutParams");
                Field field = layoutParams.getField("EXTRA_FLAG_STATUS_BAR_DARK_MODE");
                int darkModeFlag = field.getInt(layoutParams);
                Method extraFlagField = clazz.getMethod("setExtraFlags", int.class, int.class);
                extraFlagField.invoke(window, darkMode ? darkModeFlag : 0, darkModeFlag);
            } catch (Exception e) {
            }
        }
        setStatusBarDarkMode(window, darkMode);
    }

    private static void setFlymeStatusBarDarkMode(Window window, boolean darkMode) {
        FlymeStatusBarUtils.setStatusBarDarkIcon(window, darkMode);
    }

    private static final int SYSTEM_UI_FLAG_OP_STATUS_BAR_TINT = 0x00000010;

    private static void setOppoStatusBarDarkMode(Window window, boolean darkMode) {
        int vis = window.getDecorView().getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (darkMode) {
                vis |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                vis &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (darkMode) {
                vis |= SYSTEM_UI_FLAG_OP_STATUS_BAR_TINT;
            } else {
                vis &= ~SYSTEM_UI_FLAG_OP_STATUS_BAR_TINT;
            }
        }
        window.getDecorView().setSystemUiVisibility(vis);
    }

    /**
     * 设置状态栏颜色和透明度
     *
     * @param window
     * @param color
     * @param alpha
     */
    public static void setStatusBarColor(Window window, @ColorInt int color, int alpha) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(calculateStatusColor(color, alpha));
        }
    }

    /**
     * 计算状态栏颜色
     *
     * @param color color值
     * @param alpha alpha值
     * @return 最终的状态栏颜色
     */
    private static int calculateStatusColor(@ColorInt int color, int alpha) {
        if (alpha == 0) {
            return color;
        }
        float a = 1 - alpha / 255f;
        int red = color >> 16 & 0xff;
        int green = color >> 8 & 0xff;
        int blue = color & 0xff;
        red = (int) (red * a + 0.5);
        green = (int) (green * a + 0.5);
        blue = (int) (blue * a + 0.5);
        return 0xff << 24 | red << 16 | green << 8 | blue;
    }

    /**
     * 获取状态栏高度
     *
     * @param context
     * @return
     */
    public static int getStatusBarHeight(Context context) {
        int result = 0;
        try {
            Class<?> c = Class.forName("com.android.internal.R$dimen");
            Object o = c.newInstance();
            Field field = c.getField("status_bar_height");
            int x = (Integer) field.get(o);
            result = context.getResources().getDimensionPixelSize(x);
        } catch (Exception var6) {
            var6.printStackTrace();
        }

        if (result == 0) {
            result = AndroidUtilities.dp(25.0F);
        }

        return result;
    }

    /**
     * 获取导航栏高度
     *
     * @param context
     * @return
     */
    public static int getNavigationBarHeight(Context context) {
        int resourceId = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return context.getResources().getDimensionPixelSize(resourceId);
    }

    public static boolean isNavigationBarExist(Activity activity) {
        if (activity == null) {
            return false;
        }
        if (WKOSUtils.isEmui()) {
            boolean isHide = isHuaWeiHideNav(activity);
            return !isHide;
        }
        if (WKOSUtils.isMiui()) {
            boolean isFull = isMiuiFullScreen(activity);
            return !isFull;
        }
        if (WKOSUtils.isVivo()) {
            boolean isFull = isVivoFullScreen(activity);
            return !isFull;
        }


        WindowManager windowManager = (WindowManager) activity.getSystemService(Service.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();

        DisplayMetrics realDisplayMetrics = new DisplayMetrics();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            display.getRealMetrics(realDisplayMetrics);
        }
        int realHeight = realDisplayMetrics.heightPixels;
        int realWidth = realDisplayMetrics.widthPixels;

        DisplayMetrics displayMetrics = new DisplayMetrics();
        display.getMetrics(displayMetrics);
        int displayHeight = displayMetrics.heightPixels;
        int displayWidth = displayMetrics.widthPixels;

        // 部分无良厂商的手势操作，显示高度 + 导航栏高度，竟然大于物理高度，对于这种情况，直接默认未启用导航栏
        if (displayHeight > displayWidth) {
            if (displayHeight + getNavigationBarHeight(activity) > realHeight)
                return false;
        } else {
            if (displayWidth + getNavigationBarHeight(activity) > realWidth)
                return false;
        }
        return realWidth - displayWidth > 0 || realHeight - displayHeight > 0;
    }

    private static boolean isHuaWeiHideNav(Activity activity) {
        int v;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            v = Settings.System.getInt(activity.getContentResolver(), "navigationbar_is_min", 0);
        } else {
            v = Settings.Global.getInt(activity.getContentResolver(), "navigationbar_is_min", 0);
        }
        return v != 0;
    }

    private static boolean isMiuiFullScreen(Activity activity) {
        int v = Settings.Global.getInt(activity.getContentResolver(), "force_fsg_nav_bar", 0);
        return v != 0;
    }

    private static boolean isVivoFullScreen(Activity activity) {
        int v = Settings.Secure.getInt(activity.getContentResolver(), "navigation_gesture_on", 0);
        return v != 0;
    }

    /**
     * 检测是否有虚拟导航栏
     *
     * @param context
     * @return
     */
    public static boolean checkDeviceHasNavigationBar(Context context) {
        boolean hasNavigationBar = false;
        Resources rs = context.getResources();
        int id = rs.getIdentifier("config_showNavigationBar", "bool", "android");
        if (id > 0) {
            hasNavigationBar = rs.getBoolean(id);
        }
        try {
            Class systemPropertiesClass = Class.forName("android.os.SystemProperties");
            Method m = systemPropertiesClass.getMethod("get", String.class);
            String navBarOverride = (String) m.invoke(systemPropertiesClass, "qemu.hw.mainkeys");
            if ("1".equals(navBarOverride)) {
                hasNavigationBar = false;
            } else if ("0".equals(navBarOverride)) {
                hasNavigationBar = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.e("是否有西南角", hasNavigationBar + "");
        return hasNavigationBar;
    }

    /**
     * 计算View Id
     *
     * @return
     */
    public static int generateViewId() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return View.generateViewId();
        } else {
            return UUID.randomUUID().hashCode();
        }
    }


}
