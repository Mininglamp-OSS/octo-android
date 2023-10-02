package com.chat.base.utils;

import android.text.TextUtils;
import android.util.Log;

import com.chat.base.config.WKBinder;

/**
 * 2020-07-17 15:03
 * 日志打印
 */
public class WKLogUtils {
    public final static String DEFAULT_TAG = "tsddLog";
    public static boolean LOGGABLE = WKBinder.isDebug;

    public static void d(String tag, String str) {
        if (LOGGABLE && !TextUtils.isEmpty(str)) {
            log(tag, str, Log.DEBUG);
        }
    }

    /**
     * @param str 内容
     */
    public static void d(String str) {
        if (LOGGABLE && !TextUtils.isEmpty(str)) {
            log(DEFAULT_TAG, str, Log.DEBUG);
        }
    }

    /**
     * 打印warning级别的log
     *
     * @param tag tag标签
     * @param str 内容
     * @param tr  An exception to log
     */
    public static void w(String tag, String str, Throwable tr) {
        if (LOGGABLE && !TextUtils.isEmpty(str)) {
            Log.w(tag, str, tr);
        }
    }

    /**
     * 打印warning级别的log
     *
     * @param tag tag标签
     * @param str 内容
     */
    public static void w(String tag, String str) {
        if (LOGGABLE && !TextUtils.isEmpty(str)) {
            log(tag, str, Log.WARN);
        }
    }

    /**
     * 打印warning级别的log
     *
     * @param str 内容
     */
    public static void w(String str) {
        if (LOGGABLE && !TextUtils.isEmpty(str)) {
            log(DEFAULT_TAG, str, Log.WARN);
        }
    }

    /**
     * 打印error级别的log
     *
     * @param tag tag标签
     * @param str 内容
     * @param tr  An exception to log
     */
    public static void e(String tag, String str, Throwable tr) {
        if (LOGGABLE && !TextUtils.isEmpty(str)) {
            Log.e(tag, str, tr);
        }
    }

    /**
     * 打印error级别的log
     *
     * @param tag tag标签
     * @param str 内容
     */
    public static void e(String tag, String str) {
        if (LOGGABLE && !TextUtils.isEmpty(str)) {
            log(tag, str, Log.ERROR);
        }
    }

    /**
     * 打印error级别的log
     *
     * @param str 内容
     */
    public static void e(String str) {
        if (LOGGABLE && !TextUtils.isEmpty(str)) {
            log(DEFAULT_TAG, str, Log.ERROR);
        }
    }

    /**
     * 打印info级别的log
     *
     * @param tag tag标签
     * @param str 内容
     */
    public static void i(String tag, String str) {
        if (LOGGABLE && !TextUtils.isEmpty(str)) {
            log(tag, str, Log.INFO);
        }
    }

    /**
     * 打印info级别的log
     *
     * @param str 内容
     */
    public static void i(String str) {
        if (LOGGABLE && !TextUtils.isEmpty(str)) {
            log(DEFAULT_TAG, str, Log.INFO);
        }
    }

    /**
     * 打印verbose级别的log
     *
     * @param tag tag标签
     * @param str 内容
     */
    public static void v(String tag, String str) {
        if (LOGGABLE && !TextUtils.isEmpty(str)) {
            log(tag, str, Log.VERBOSE);
        }
    }

    /**
     * 打印verbose级别的log
     *
     * @param str 内容
     */
    public static void v(String str) {
        if (LOGGABLE && !TextUtils.isEmpty(str)) {
            log(DEFAULT_TAG, str, Log.VERBOSE);
        }
    }

    /**
     * 截取输出log，Log类只能输出 4*1024KB大小的log，超过超度即显示不全。次方法可以全部显示log
     *
     * @param tag   log tag
     * @param str   log内容
     * @param level log级别：Log.VERBOSE、Log.DEBUG …… 传错值默认为debug级别log
     */
    private static void log(String tag, String str, int level) {
        if (str != null) {
            int index = 0;
            int maxLength = 3900;
            String sub;
            int length = str.length();
            while (index < length) {
                if (length <= index + maxLength) {
                    sub = str.substring(index);
                } else {
                    sub = str.substring(index, index + maxLength);
                }
                index += maxLength;
                if (level == Log.VERBOSE) {
                    Log.v(tag, sub);
                } else if (level == Log.DEBUG) {
                    Log.d(tag, sub);
                } else if (level == Log.INFO) {
                    Log.i(tag, sub);
                } else if (level == Log.WARN) {
                    Log.w(tag, sub);
                } else if (level == Log.ERROR) {
                    Log.e(tag, sub);
                } else {
                    Log.d(tag, sub);
                }
            }
        }
    }

}
