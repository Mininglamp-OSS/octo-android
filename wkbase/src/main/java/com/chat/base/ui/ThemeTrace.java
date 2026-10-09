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

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * 深浅色切换全链路埋点。
 *
 * <p>抓取方式：{@code adb logcat -s ThemeTrace}
 *
 * <p>只做状态取样与格式化，不做任何判断/结论。每条日志统一形如
 * {@code [节点] key=value key=value ...}，其中所有 uiMode / nightMode 值都以
 * 可读符号 + 原始数值给出，便于直接比对各环节实际读到的是什么。
 */
public final class ThemeTrace {

    public static final String TAG = "ThemeTrace";

    /** 埋点总开关。排查完成后置 false 即整链静默；需要时再置 true，无需改各埋点。 */
    public static volatile boolean ENABLED = false;

    private ThemeTrace() {
    }

    public static void log(String node, String detail) {
        if (!ENABLED) {
            return;
        }
        Log.i(TAG, "[" + node + "] " + detail
                + " thread=" + Thread.currentThread().getName()
                + " main=" + (Looper.myLooper() == Looper.getMainLooper())
                + " t=" + android.os.SystemClock.uptimeMillis());
    }

    /** 带调用栈的埋点，用于确认某节点是被谁触发的。 */
    public static void logWithStack(String node, String detail) {
        if (!ENABLED) {
            return;
        }
        log(node, detail);
        Log.i(TAG, "[" + node + "] callers=" + callers(8));
    }

    /**
     * uiMode 的 night 位 → 可读串。同时带出原始 uiMode（含非 night 位，如车机/桌面 type），
     * 便于区分“没有 night 位”与“night 位为 NO”。
     */
    public static String uiMode(Configuration config) {
        if (config == null) {
            return "config=null";
        }
        int night = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightBitName(night) + "(raw.uiMode=0x" + Integer.toHexString(config.uiMode) + ")";
    }

    public static String nightBitName(int nightBit) {
        switch (nightBit) {
            case Configuration.UI_MODE_NIGHT_YES:
                return "NIGHT_YES";
            case Configuration.UI_MODE_NIGHT_NO:
                return "NIGHT_NO";
            case Configuration.UI_MODE_NIGHT_UNDEFINED:
                return "NIGHT_UNDEFINED";
            default:
                return "NIGHT_?" + nightBit;
        }
    }

    /** AppCompatDelegate 的 MODE_NIGHT_* 常量 → 可读串。 */
    public static String nightModeName(int mode) {
        switch (mode) {
            case AppCompatDelegate.MODE_NIGHT_NO:
                return "MODE_NIGHT_NO(1)";
            case AppCompatDelegate.MODE_NIGHT_YES:
                return "MODE_NIGHT_YES(2)";
            case AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM:
                return "MODE_NIGHT_FOLLOW_SYSTEM(-1)";
            case AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY:
                return "MODE_NIGHT_AUTO_BATTERY(3)";
            case AppCompatDelegate.MODE_NIGHT_UNSPECIFIED:
                return "MODE_NIGHT_UNSPECIFIED(-100)";
            default:
                return "MODE_?" + mode;
        }
    }

    /**
     * 一次性采样“此刻各个配置源分别是什么”。深浅色问题的核心就是这几个源会不一致：
     * framework 全局 Resources / Application Resources / 传入 context 的 Resources /
     * AppCompat 的 defaultNightMode。
     */
    public static String snapshot(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("sysRes=").append(uiMode(Resources.getSystem().getConfiguration()));
        sb.append(" defaultNightMode=").append(nightModeName(AppCompatDelegate.getDefaultNightMode()));
        if (context != null) {
            sb.append(" ctx=").append(context.getClass().getSimpleName())
                    .append("@").append(Integer.toHexString(System.identityHashCode(context)));
            sb.append(" ctxRes=").append(uiMode(context.getResources().getConfiguration()));
            Context app = context.getApplicationContext();
            if (app != null && app != context) {
                sb.append(" appRes=").append(uiMode(app.getResources().getConfiguration()));
            }
        }
        return sb.toString();
    }

    /** 当前 Theme 静态色值快照。 */
    public static String colors() {
        return "colorAccount=" + hex(Theme.colorAccount)
                + " colorAccountDisable=" + hex(Theme.colorAccountDisable)
                + " color999=" + hex(Theme.color999)
                + " colorCCC=" + hex(Theme.colorCCC)
                + " pressedColor=" + hex(Theme.pressedColor);
    }

    public static String hex(int color) {
        return String.format("#%08X", color);
    }

    /** 跳过 ThemeTrace 自身帧，取上层调用链。 */
    private static String callers(int depth) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder();
        int printed = 0;
        for (StackTraceElement e : stack) {
            String cls = e.getClassName();
            if (cls.startsWith("dalvik.") || cls.startsWith("java.lang.Thread")
                    || cls.equals(ThemeTrace.class.getName())) {
                continue;
            }
            if (printed > 0) {
                sb.append(" <- ");
            }
            sb.append(simpleName(cls)).append('.').append(e.getMethodName())
                    .append(':').append(e.getLineNumber());
            if (++printed >= depth) {
                break;
            }
        }
        return sb.toString();
    }

    private static String simpleName(String className) {
        int i = className.lastIndexOf('.');
        return i < 0 ? className : className.substring(i + 1);
    }

    /**
     * 轻量“抢跑/错位”探针，专门抓静态色值与 Activity Configuration 在同一帧不一致的窗口。
     *
     * <p>刻意做到极低开销：不抓栈、不做多次 Resources.getSystem() 采样、只读已在手的
     * 布尔/int 并一次性拼串。目的就是尽量不给主线程注入延迟，避免把待测的竞态窗口
     * “垫平”——重日志（snapshot/getStackTrace/Log 锁）会让 AppCompat 那个 posted
     * recreate 抢在下一次读取前跑完，从而复现不出 bug。
     *
     * @param staticIsDark 当前静态色值对应的 dark 态（Theme.isDark 的判定结果）
     * @param ctxNightBit  当前 Activity context 的 uiMode night 位
     */
    public static void skew(String node, boolean staticIsDark, int ctxNightBit) {
        if (!ENABLED) {
            return;
        }
        boolean ctxDark = ctxNightBit == Configuration.UI_MODE_NIGHT_YES;
        boolean mismatch = staticIsDark != ctxDark;
        // 只有错位时才落一条醒目日志（附栈），一致时用最短串，进一步压低开销。
        if (mismatch) {
            Log.w(TAG, "[SKEW!] " + node
                    + " staticIsDark=" + staticIsDark
                    + " ctx=" + nightBitName(ctxNightBit)
                    + " -> 静态色值与 Activity 配置不一致(半帧错位)"
                    + " callers=" + callers(6));
        } else {
            Log.i(TAG, "[skew.ok] " + node
                    + " staticIsDark=" + staticIsDark
                    + " ctx=" + nightBitName(ctxNightBit));
        }
    }
}
