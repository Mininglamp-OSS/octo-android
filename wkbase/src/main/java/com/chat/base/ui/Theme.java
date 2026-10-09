/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import android.graphics.Typeface;
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
import android.os.PowerManager;
import android.util.StateSet;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import com.chat.base.WKBaseApplication;
import com.chat.base.R;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.ui.components.RoundTextView;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.SvgHelper;

import java.lang.reflect.Method;

public class Theme {
    public static int colorAccount = 0xFF7761F4;
    public static int colorAccountDisable = 0x957761F4;
    public static int color999 = 0xFF999999;
    public static int colorCCC = 0xFFCCCCCC;
    public static int pressedColor = 0xff8c9197;
    private static final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public static final String LIGHT_MODE = "light";
    public static final String DARK_MODE = "dark";
    public static final String DEFAULT_MODE = "default";
    public static final String wk_theme_pref = "wk_theme_pref";

    /**
     * 主题静态色值原本只在类加载时赋值一次，切换深浅色只触发 Activity recreate，
     * 不会重新加载类，导致这些颜色一直停留在旧值。此处在 uiMode 变化后
     * 重新从资源解析，使其吃到 values-night 覆盖。
     *
     * 只在 {@link #applyResolvedTheme} 内、night mode 真正生效变化时调用，
     * 传入的 context 必须按 targetNightMode（而非系统当前 uiMode）解析，
     * 否则手动锁定 light/dark 时颜色会被系统状态污染。
     */
    private static void refreshColors(@NonNull Context context, int targetNightMode) {
        ThemeTrace.log("Theme.refreshColors.in",
                "targetNightMode=" + ThemeTrace.nightModeName(targetNightMode)
                        + " " + ThemeTrace.snapshot(context)
                        + " before:" + ThemeTrace.colors());
        Configuration config = new Configuration(context.getResources().getConfiguration());
        int nightBit = targetNightMode == AppCompatDelegate.MODE_NIGHT_YES
                ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightBit;
        Context themedContext = context.createConfigurationContext(config);
        ThemeTrace.log("Theme.refreshColors.ctx",
                "overriddenConfig=" + ThemeTrace.uiMode(config)
                        + " themedCtxRes=" + ThemeTrace.uiMode(themedContext.getResources().getConfiguration()));
        colorAccount = ContextCompat.getColor(themedContext, R.color.colorAccent);
        colorAccountDisable = ContextCompat.getColor(themedContext, R.color.colorAccentUn);
        color999 = ContextCompat.getColor(themedContext, R.color.color999);
        colorCCC = ContextCompat.getColor(themedContext, R.color.clrCCC);
        pressedColor = ContextCompat.getColor(themedContext, R.color.pressedColor);
        ThemeTrace.log("Theme.refreshColors.out", "after:" + ThemeTrace.colors());
    }

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

    // isDark() 可能被消息渲染等非主线程路径调用，volatile 保证跨线程可见性。
    // 记录【实际生效】的 night mode（YES/NO），由 refreshColorsForCurrentMode 在读到
    // 真实 uiMode 后写入；不再存放 FOLLOW_SYSTEM 这类"策略值"。
    private static volatile int currentEffectiveNightMode = AppCompatDelegate.MODE_NIGHT_UNSPECIFIED;

    // 上一次下发给 AppCompat 的 night mode（可能是 FOLLOW_SYSTEM 等策略值），
    // 仅用于跳过重复的 setDefaultNightMode，避免多余的 Activity 重建。
    private static volatile int lastRequestedNightMode = AppCompatDelegate.MODE_NIGHT_UNSPECIFIED;

    private static void applyTheme(@NonNull String themePref) {
        applyResolvedTheme(themePref);
    }

    public static void applyTheme() {
        String themePref =
                WKSharedPreferencesUtil.getInstance().getSP(Theme.wk_theme_pref, Theme.DEFAULT_MODE);
        ThemeTrace.logWithStack("Theme.applyTheme", "themePref=" + themePref
                + " " + ThemeTrace.snapshot(WKBaseApplication.getInstance().getContext()));
        Theme.applyTheme(themePref);
    }

    /**
     * 系统深浅色变化时的兼容入口。
     *
     * 回归官方模式后，"跟随系统"由 MODE_NIGHT_FOLLOW_SYSTEM 表达，系统深浅色变化时
     * App 无需再手动同步 night mode——系统自身已经会因 uiMode 变化重建 Activity。
     * 这里只剩下静态色值的刷新（{@link #refreshColors} 覆盖的那几个 Theme.colorXxx），
     * 它们不随资源系统自动更新，仍需显式重取。
     *
     * @deprecated 保留仅为兼容既有调用点。新代码不要调用：主题切换交给 AppCompat，
     *             静态色值刷新由 {@link #refreshColorsForCurrentMode} 在 Activity
     *             重建后触发。
     */
    @Deprecated
    public static void applyThemeForSystemMode(boolean systemDark) {
        ThemeTrace.logWithStack("Theme.applyThemeForSystemMode",
                "argSystemDark=" + systemDark
                        + " " + ThemeTrace.snapshot(WKBaseApplication.getInstance().getContext()));
        refreshColorsForCurrentMode();
    }

    /**
     * 解析"此刻是否应当深色"，作为静态色值取色的依据。
     *
     * 按 themePref 分流，不能统一读某个 context 的 uiMode：
     *  - light/dark：偏好本身就是答案，直接返回。
     *  - 跟随系统（API 29+）：必须查 {@link Resources#getSystem()} 的系统级 uiMode。App 若曾经
     *    锁定过 light/dark，{@code setDefaultNightMode} 会把 Application/Activity 的
     *    Configuration 钉成那个强制值；切回"跟随系统"后该值不会立刻同步成系统真实状态，
     *    读它会得到上一次锁定的残留（表现为"系统已深色、切到跟随系统仍是浅色"）。
     *    Resources.getSystem() 是框架全局 Resources，不受 App 级覆写影响。
     *  - 跟随系统（API 23-28）：系统没有全局深色开关，交给 AppCompat 的
     *    MODE_NIGHT_AUTO_BATTERY，即"省电模式开启时深色"。静态色值必须用同一个数据源
     *    （{@link PowerManager#isPowerSaveMode()}）自己判一遍，否则会与 AppCompat 的
     *    实际渲染结果相反。
     */
    private static boolean shouldBeDarkNow() {
        String themePref = getTheme();
        int sysNight = Resources.getSystem().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        boolean result;
        String source;
        if (DARK_MODE.equals(themePref)) {
            result = true;
            source = "themePref";
        } else if (LIGHT_MODE.equals(themePref)) {
            result = false;
            source = "themePref";
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            result = sysNight == Configuration.UI_MODE_NIGHT_YES;
            source = "Resources.getSystem()";
        } else {
            result = isPowerSaveModeNow();
            source = "PowerManager.isPowerSaveMode()";
        }
        // 去抖：shouldBeDarkNow 在渲染热路径被高频调用，逐帧打日志会给主线程注入延迟、
        // 垫平竞态窗口。只在【判定结果发生变化】时落一条，既覆盖每一次真实翻转，又几乎零噪声。
        if (lastLoggedShouldBeDark == null || result != lastLoggedShouldBeDark) {
            lastLoggedShouldBeDark = result;
            ThemeTrace.log("Theme.shouldBeDarkNow.change",
                    "themePref=" + themePref
                            + " source=" + source
                            + " sysNight=" + ThemeTrace.nightBitName(sysNight)
                            + " result=" + result);
        }
        return result;
    }

    /**
     * API 23-28 没有系统级深色开关，"跟随系统"走 MODE_NIGHT_AUTO_BATTERY，AppCompat
     * 按省电模式状态决定是否深色；{@link #shouldBeDarkNow()} 需要在同一个数据源上自己
     * 查一遍，保证静态色值与 AppCompat 的渲染结果一致。
     *
     * 这是"当前是否开着省电模式"的近似判断，不是对 AppCompat 内部判定逻辑的精确复刻
     * （不同厂商 ROM 的省电-深色联动策略可能有细微差异）。该近似符合本次修复范围：
     * 只覆盖 API 23-28 跟随系统这一存量场景，允许极少数机型上 isDark() 判断与
     * AppCompat 实际渲染有短暂不一致。
     */
    private static boolean isPowerSaveModeNow() {
        Context context = WKBaseApplication.getInstance().getContext();
        if (context == null) {
            return false;
        }
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isPowerSaveMode();
    }

    // 仅用于 shouldBeDarkNow 日志去抖，记录上一次已打印的判定结果。Boolean 用可空初值
    // 保证首次调用必落一条。
    private static volatile Boolean lastLoggedShouldBeDark = null;

    /**
     * 按当前应当生效的模式刷新静态色值。
     *
     * 取色依据来自 {@link #shouldBeDarkNow()}，而不是某个 context 当前的 uiMode——
     * {@code setDefaultNightMode} 是异步生效的，调用后紧接着读 context 的 uiMode 仍是
     * 旧值；且 App 曾锁定过具体模式时，该值会残留为强制值。
     */
    public static void refreshColorsForCurrentMode() {
        Context context = WKBaseApplication.getInstance().getContext();
        ThemeTrace.logWithStack("Theme.refreshColorsForCurrentMode.in",
                "context=" + (context == null ? "null" : "nonNull")
                        + " currentEffectiveNightMode=" + ThemeTrace.nightModeName(currentEffectiveNightMode)
                        + " lastRequestedNightMode=" + ThemeTrace.nightModeName(lastRequestedNightMode)
                        + " " + ThemeTrace.snapshot(context));
        if (context == null) {
            ThemeTrace.log("Theme.refreshColorsForCurrentMode.skip", "reason=contextNull");
            return;
        }
        int resolved = shouldBeDarkNow()
                ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        ThemeTrace.log("Theme.refreshColorsForCurrentMode.resolved",
                "prevEffective=" + ThemeTrace.nightModeName(currentEffectiveNightMode)
                        + " newEffective=" + ThemeTrace.nightModeName(resolved)
                        + " changed=" + (currentEffectiveNightMode != resolved));
        currentEffectiveNightMode = resolved;
        refreshColors(context, resolved);
    }

    /**
     * themePref 决定交给 AppCompat 的 night mode：
     * light/dark 映射到 MODE_NIGHT_NO/YES；"跟随系统"（default）在 API 29+ 映射到
     * MODE_NIGHT_FOLLOW_SYSTEM，由系统与 AppCompat 自行决定何时深色；API 23-28
     * 没有系统级深色开关（FOLLOW_SYSTEM 在那里恒为浅色），沿用
     * MODE_NIGHT_AUTO_BATTERY，即"省电模式开启时深色"。
     *
     * 交给 AppCompat 自行解析是官方推荐做法。此前"跟随系统"手动把系统模式解析成具体
     * YES/NO 再调 setDefaultNightMode，会在一次系统深浅色切换中产生两次 Activity 重建——
     * 系统因 uiMode 变化重建一次，setDefaultNightMode 又触发 AppCompat 重建一次
     * （AppCompat 1.1.0 起 setDefaultNightMode 自带重建）。当时之所以绕开
     * FOLLOW_SYSTEM，根因是 AppTheme 的 parent 在非 night 分支被写成 .Light、
     * 只靠 values-night 切换 parent，主题不具备 DayNight 行为；该配置已修正。
     *
     * 若目标与上次下发值相同则跳过，避免重复触发重建（对齐 App 内点击同一模式
     * 不重启的行为）。
     *
     * 静态色值不在这里刷新：setDefaultNightMode 之后 Activity 会重建，届时由
     * {@link #refreshColorsForCurrentMode} 按真正生效的 uiMode 取色。
     */
    private static void applyResolvedTheme(String themePref) {
        int targetNightMode = switch (themePref) {
            case LIGHT_MODE -> AppCompatDelegate.MODE_NIGHT_NO;
            case DARK_MODE -> AppCompatDelegate.MODE_NIGHT_YES;
            // API 23-28 没有系统级深色开关，FOLLOW_SYSTEM 在那里恒解析为浅色；
            // 沿用 AUTO_BATTERY 保留"省电模式即深色"这一 pre-Q 的既有能力。
            default -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    : AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY;
        };
        int currentDefault = AppCompatDelegate.getDefaultNightMode();
        ThemeTrace.log("Theme.applyResolvedTheme.in",
                "themePref=" + themePref
                        + " targetNightMode=" + ThemeTrace.nightModeName(targetNightMode)
                        + " lastRequestedNightMode=" + ThemeTrace.nightModeName(lastRequestedNightMode)
                        + " currentDefaultNightMode=" + ThemeTrace.nightModeName(currentDefault));
        if (targetNightMode == lastRequestedNightMode
                && targetNightMode == currentDefault) {
            ThemeTrace.log("Theme.applyResolvedTheme.skip",
                    "reason=sameAsLastRequestedAndCurrentDefault"
                            + " targetNightMode=" + ThemeTrace.nightModeName(targetNightMode));
            return;
        }
        lastRequestedNightMode = targetNightMode;
        // 记录“下发前”当前可见 Activity 的 context uiMode。App 内切换场景下，此刻它仍是
        // 上一次锁定的强制值（如 light 锁定后为 NIGHT_NO），setDefaultNightMode 只是 post
        // 了一个 recreate，还没同步改它。
        int actNightBefore = currentActivityNightBit();
        ThemeTrace.log("Theme.applyResolvedTheme.setDefaultNightMode.pre",
                "set=" + ThemeTrace.nightModeName(targetNightMode)
                        + " actCtxNight=" + ThemeTrace.nightBitName(actNightBefore));
        AppCompatDelegate.setDefaultNightMode(targetNightMode);
        // 下发后立刻再读一次可见 Activity 的 uiMode：若与下发前相同，说明 recreate 尚未
        // 同步发生（还在 handler 队列里），补刷就会“抢跑”。
        int actNightAfter = currentActivityNightBit();
        ThemeTrace.log("Theme.applyResolvedTheme.setDefaultNightMode.post",
                "defaultNightMode=" + ThemeTrace.nightModeName(AppCompatDelegate.getDefaultNightMode())
                        + " actCtxNightBefore=" + ThemeTrace.nightBitName(actNightBefore)
                        + " actCtxNightAfter=" + ThemeTrace.nightBitName(actNightAfter)
                        + " recreateAlreadyApplied=" + (actNightBefore != actNightAfter));
        // 冷启动等尚无 Activity 重建可依赖的路径，这里补一次即时刷新；
        // 重建路径下 Activity 侧还会再刷一次，两者结果一致，重复无副作用。
        refreshColorsForCurrentMode();
        // 补刷后：静态色值已按"应当生效模式"取好，但可见 Activity 的 Configuration 可能
        // 还停在旧值（recreate 未跑）。这里做一次轻量错位探针——若 SKEW! 出现，即证明
        // 存在"静态色值已新、页面配置仍旧"的半帧窗口（页面部分变色的根因）。
        ThemeTrace.skew("applyResolvedTheme.afterRefresh", isDarkRaw(), currentActivityNightBit());
    }

    /**
     * 当前可见 Activity 的 uiMode night 位；无可见 Activity 时返回 UNDEFINED。
     * 只读不写，供错位探针使用。
     */
    private static int currentActivityNightBit() {
        Context act = com.chat.base.utils.ActManagerUtils.getInstance().getCurrentActivity();
        if (act == null) {
            return Configuration.UI_MODE_NIGHT_UNDEFINED;
        }
        return act.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    }

    /** 不带日志的 isDark 原值，供探针内部调用，避免探针自身触发 Theme.isDark 的日志噪声。 */
    private static boolean isDarkRaw() {
        return currentEffectiveNightMode == AppCompatDelegate.MODE_NIGHT_YES;
    }

    public static String getTheme() {
        return WKSharedPreferencesUtil.getInstance().getSP(Theme.wk_theme_pref, Theme.DEFAULT_MODE);
    }

    public static void setTheme(String s) {
        String previous = getTheme();
        ThemeTrace.logWithStack("Theme.setTheme",
                "from=" + previous + " to=" + s
                        + " " + ThemeTrace.snapshot(WKBaseApplication.getInstance().getContext()));
        WKSharedPreferencesUtil.getInstance().putSP(Theme.wk_theme_pref, s);
        Theme.applyTheme(s);
    }

    public static boolean isSystemDarkMode(Context context) {
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        return uiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_YES;
    }

    public static boolean getDarkModeStatus(Context context) {
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean result = mode == Configuration.UI_MODE_NIGHT_YES;
        // 轻量：不再每次采样 Resources.getSystem()（那是重操作，会给渲染路径注入延迟、
        // 垫平竞态窗口）。改用错位探针把“context 态 vs 静态色值态”一行打出，错位才醒目告警。
        ThemeTrace.skew("getDarkModeStatus", isDarkRaw(), mode);
        return result;
    }

    public static int getSwitchViewTrackColor() {
        if (isDark()) {
            return 0xFF585858;
        } else
            return Theme.colorCCC;
    }

    public static int getSwitchViewThumbColor() {
        if (isDark()) {
            return 0xFF212223;
        } else
            return 0xFFFFFFFF;
    }

    public static boolean isDark() {
        // 与静态色值取色使用同一个判定源，避免两者在"刚切换、配置尚未同步"的窗口内不一致。
        // 不在此处加日志：isDark 是渲染热路径，且它与静态色值同源、无法自证错位；真正
        // 有价值的错位（静态态 vs context 配置）由 getDarkModeStatus / OctoHostConfig 的
        // skew 探针捕获。这里保持零额外开销，避免垫平竞态窗口。
        return shouldBeDarkNow();
    }

    public static RoundTextView getChannelCategoryTV(Context context, String text, int bgColor, int textColor, int borderColor) {
        RoundTextView roundTextView = new RoundTextView(context);
        roundTextView.setTextSize(12);
        roundTextView.setText(text);
        roundTextView.setTypeface(Typeface.DEFAULT_BOLD);
        roundTextView.setLines(1);
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

    public static void setDrawableColor(Drawable drawable, int color) {
        if (drawable == null) {
            return;
        }
        if (drawable instanceof ShapeDrawable) {
            ((ShapeDrawable) drawable).getPaint().setColor(color);
        }  else {
            drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        }
    }

    public static int multAlpha(int color, float multiply) {
        if (multiply == 1f)
            return color;
        return ColorUtils.setAlphaComponent(color, MathUtils.clamp((int) (Color.alpha(color) * multiply), 0, 0xFF));
    }
}
