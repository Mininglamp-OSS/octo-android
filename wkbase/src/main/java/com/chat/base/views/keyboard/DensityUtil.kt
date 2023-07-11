package com.chat.base.views.keyboard

import android.content.Context
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.WindowManager
import kotlin.math.roundToInt

/**
 * @name
 * @author      FreddyChen
 * @date        2020/06/07 01:05
 * @email       chenshichao@outlook.com
 * @github      https://github.com/FreddyChen
 * @describe
 */
object DensityUtil {

    /**
     * 根据手机的分辨率从 dp 的单位 转成为 px(像素)
     *
     * @param dpValue
     * @return
     */
    fun dp2px(context: Context, dpValue: Float): Int {
        return (dpValue * getDisplayMetrics(context).density).roundToInt()
    }

    /**
     * 根据手机的分辨率从 px(像素) 的单位 转成为 dp
     *
     * @param pxValue
     * @return
     */
    fun px2dp(context: Context, pxValue: Float): Int {
        return (pxValue / getDisplayMetrics(context).density).roundToInt()
    }

    /**
     * sp转px
     *
     * @param spVal
     * @return
     */
    fun sp2px(context: Context, spVal: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            spVal, context.resources.displayMetrics
        ).roundToInt()
    }

    /**
     * px转sp
     *
     * @param pxVal
     * @return
     */
    fun px2sp(context: Context, pxVal: Float): Float {
        return pxVal / getDisplayMetrics(context).scaledDensity
    }

    private fun getDisplayMetrics(context: Context): DisplayMetrics {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val metrics = DisplayMetrics()
        display.getMetrics(metrics)
        return metrics
    }

    /**
     * 获取屏幕宽度
     * @return
     */
    fun getScreenWidth(context: Context): Int {
        return getDisplayMetrics(context).widthPixels
    }

    /**
     * 获取屏幕高度
     * @return
     */
    fun getScreenHeight(context: Context): Int {
        return getDisplayMetrics(context).heightPixels
    }

    /**
     * 获取像素密度
     * @return
     */
    fun getDensity(context: Context): Float {
        return getDisplayMetrics(context).density
    }
}