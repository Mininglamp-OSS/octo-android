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

package com.chat.base.foldable

import android.app.Activity
import android.os.Build
import android.util.Log
import com.chat.base.R
import com.chat.base.config.WKBinder

/**
 *  · 窄屏 Activity 过渡统一入口（Fix D 自我覆盖版）。
 *
 * ## 为什么下沉到 Activity 自己
 * 首版（PR#195）把 [Activity.overridePendingTransition] 调用塞在
 * [com.chat.uikit.chat.manager.WKIMUtils.startChat] 里，只覆盖了从会话列表点
 * 击的主路径；**子区卡片点击**（WKThreadCreatedProvider）、SearchAllActivity、
 * CreateThreadActivity 都直接 `startActivity(ChatActivity)` 绕过 helper，
 * 导致窄屏下最敏感的「子区跳转」依然是默认 250-350ms 的慢动画。
 *  把入场/出场动画下沉到 ChatActivity 自身（onCreate + finish），
 * 所有调用方不用关心。
 *
 * ## 快进慢出对称（ P1-3）
 * 入场 120ms、非 swipe 返回 ~250-350ms 观感不对称。finish() 里补
 * [applyFastClose]，用反向 slide pair 让返回同样 120ms。
 *
 * ## API 34+ 适配（ P2-3）
 * `overridePendingTransition` 在 Android 14 (API 34) 被 deprecated，改为
 * [Activity.overrideActivityTransition]。后者可在 onCreate 里一次注册
 * OPEN + CLOSE，不用在每次 finish() 时重复调。
 *
 * ## 窄屏判定修正（ P2-2）
 * 原先 `maxWidthPx < 600dp` 在手机横屏（如 640×360dp）下误判为「宽屏」，
 * 不上快动画。现在结合两个信号：
 *  - Embedding 没有在分割当前 Activity：`widthPx == maxWidthPx`
 *  - 设备类是手机：`configuration.smallestScreenWidthDp < 600`
 * 这样折叠/平板分屏仍走系统默认（副栏放置），手机横竖屏都吃到快动画。
 */
object NarrowTransition {

    private const val TAG = "YUJ278-transition"

    /** sw600dp 作为「手机 vs 平板」类别阈值。 */
    private const val TABLET_SW_DP = 600

    /**
     * 当前 Activity 是否跑在「窄屏手机全屏」场景下——也就是需要 120ms 快过渡
     * 的场景。分屏/平板返回 false，调用方就走系统默认。
     *
     * 两个信号缺一不可：
     *  1. `widthPx == maxWidthPx` — Activity Embedding **没有**把当前 Activity
     *     放进副栏（副栏态下 widthPx 总是 < maxWidthPx）。
     *  2. `smallestScreenWidthDp < 600` — 设备本身是手机类，避免平板全屏态
     *     （罕见，比如用户关了 Embedding）也被套快动画。
     */
    @JvmStatic
    fun isNarrow(activity: Activity): Boolean {
        return try {
            val widthPx = PaneMetrics.widthPx(activity)
            val maxWidthPx = PaneMetrics.maxWidthPx(activity)
            val notSplit = widthPx == maxWidthPx
            val phoneClass = activity.resources.configuration.smallestScreenWidthDp < TABLET_SW_DP
            notSplit && phoneClass
        } catch (t: Throwable) {
            // Defensive: 保留手机窄屏行为。WKBinder.isDebug gate 让 release 不输出日志。
            if (WKBinder.isDebug) Log.w(TAG, "isNarrow fallback due to $t")
            activity.resources.configuration.smallestScreenWidthDp < TABLET_SW_DP
        }
    }

    /**
     * 入场快动画。在 [Activity.onCreate] 调用（super.onCreate 之后）。
     *
     * 在 API 34+ 上同时预注册 CLOSE override，这样非 swipe 返回也自动走
     * 对称的 120ms 出场动画，不需要再在 finish() 里重复调用。
     */
    @JvmStatic
    fun applyFastOpen(activity: Activity) {
        if (!isNarrow(activity)) return
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                activity.overrideActivityTransition(
                    Activity.OVERRIDE_TRANSITION_OPEN,
                    R.anim.slide_in_right_fast,
                    R.anim.slide_out_left_fast
                )
                // 同一次性把 CLOSE 也注册掉，finish() 不用重复 gate。
                activity.overrideActivityTransition(
                    Activity.OVERRIDE_TRANSITION_CLOSE,
                    R.anim.slide_in_left_fast,
                    R.anim.slide_out_right_fast
                )
            } else {
                @Suppress("DEPRECATION")
                activity.overridePendingTransition(
                    R.anim.slide_in_right_fast,
                    R.anim.slide_out_left_fast
                )
            }
            if (WKBinder.isDebug) {
                Log.d(TAG, "applyFastOpen sdk=${Build.VERSION.SDK_INT} activity=${activity.javaClass.simpleName}")
            }
        } catch (t: Throwable) {
            // P2-1: 失败日志仅 debug 下输出，避免 release logcat 噪声。
            if (WKBinder.isDebug) Log.w(TAG, "applyFastOpen failed: $t")
        }
    }

    /**
     * 出场快动画。在 [Activity.finish] 里调用（pre-34 必须在 super.finish() **之后**）。
     *
     * 在 API 34+ 上若 [applyFastOpen] 已注册 CLOSE，这里是 no-op；在 pre-34
     * 上 `overridePendingTransition` 只影响接下来一次 finish，所以必须每次调。
     */
    @JvmStatic
    fun applyFastClose(activity: Activity) {
        // 34+ 在 onCreate 已注册，不要重复调（重复调最多是同参数，但也多余）。
        if (Build.VERSION.SDK_INT >= 34) return
        if (!isNarrow(activity)) return
        try {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(
                R.anim.slide_in_left_fast,
                R.anim.slide_out_right_fast
            )
            if (WKBinder.isDebug) {
                Log.d(TAG, "applyFastClose activity=${activity.javaClass.simpleName}")
            }
        } catch (t: Throwable) {
            if (WKBinder.isDebug) Log.w(TAG, "applyFastClose failed: $t")
        }
    }

    /**
     *  · 「用 startActivity 代替 finish() 做返回」场景的出场动画。
     *
     * 典型路径：[com.chat.uikit.chat.ChatReuseNavigator.goBackToList]，为了复用
     * ChatActivity 实例，窄屏返回不走 finish()，而是把 TabActivity 用
     * `FLAG_ACTIVITY_REORDER_TO_FRONT` 带到栈顶、让 ChatActivity 进 onStop 保活。
     *
     * 这条路径在 AMS 眼里是 **OPEN**（TabActivity 在打开），不是 CLOSE。所以：
     *  - [applyFastOpen] 在 onCreate 里用 `overrideActivityTransition(OPEN/CLOSE)`
     *    注册的 CLOSE 动画 **不会** 被触发（ChatActivity 没在 finish）；
     *  - 系统默认应用 OPEN 过渡（新页右滑入 + 旧页左滑出），视觉上就是「又打开了
     *    一个新页面」，用户点击左上角返回按钮会误以为进了更深一层。
     *
     * 修复思路：不依赖 [applyFastOpen] 预注册的 OPEN/CLOSE 覆盖，改为在
     * `startActivity` 之后 **立即** 用 [Activity.overridePendingTransition] 手动
     * 指定反向 slide pair（旧页右滑出 + 新页左滑入）。overridePendingTransition
     * 在 API 34 上被标记 deprecated，但依然有效；这里没有更合适的替代——
     * `overrideActivityTransition` 是绑定到 Activity 自身生命周期的持久覆盖，
     * 不适合描述「我接下来这一次 startActivity 要看起来像 pop」的瞬时语义。
     *
     * 只对窄屏生效；分屏 / 折叠展开态 / 平板直接短路返回 false（这些场景下
     * goBackToList 本来也不会进这条支路）。
     */
    @JvmStatic
    fun applyFastPopViaStartActivity(activity: Activity) {
        if (!isNarrow(activity)) return
        try {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(
                R.anim.slide_in_left_fast,
                R.anim.slide_out_right_fast
            )
            if (WKBinder.isDebug) {
                Log.d(
                    TAG,
                    "applyFastPopViaStartActivity sdk=${Build.VERSION.SDK_INT} " +
                            "activity=${activity.javaClass.simpleName}"
                )
            }
        } catch (t: Throwable) {
            if (WKBinder.isDebug) Log.w(TAG, "applyFastPopViaStartActivity failed: $t")
        }
    }
}
