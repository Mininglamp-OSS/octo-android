/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.summary

import android.app.Activity
import android.app.Application
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.chat.base.utils.ActManagerUtils
import com.chat.uikit.R

/**
 * 文字 + 紫色 action 按钮的轻量胶囊 toast, 1:1 对齐 iOS [OctoActionToast]:
 *   - 底部胶囊 h=48dp, 圆角 24, 距 bottom safearea 80dp, 左右 16dp
 *   - 浅色: 黑底白字 / 深色: 白底黑字 (复用 summary_hud_*)
 *   - 右侧紫色 action 按钮 (semibold 15sp), 点击触发 onAction 并 dismiss
 *   - 3.5s 自动消失, slide-up + fade 进出
 *   - 同 host 重入: 先把上一条 dismiss 掉避免堆叠
 *
 * 与 [SummaryHud] (CSToast 风格的中央 1s 黑块) 的分工:
 *   - SummaryHud  —— 纯文本提示, ~1s 自动消失, 不可点
 *   - ActionToast —— 提示 + 一个动作 (查看/打开/...), action 区可点
 *
 * Host 选择:
 *   [show] 接收一个 application 引用 + 一个 "排除 activity" (createActivity 自身), 注册一个
 *   一次性 [Application.ActivityLifecycleCallbacks], 等到下一个不是排除项的 activity onResumed
 *   时把 toast 挂在它的 contentView 上。这样可以稳定避开 createActivity 已 finish 但还没完成
 *   onPause/onStop 的窗口期 — 直接 [ActManagerUtils.currentActivity] 拿到的会是正在 destroy 的
 *   createActivity, toast 跟着 window 一起被拆,用户只看到 "闪一下"。
 *
 *   1:1 对齐 iOS [OctoSummaryCreateVC.onSubmit] 那条 `dispatch_async` 让 nav stack 切完再读
 *   topViewController 的逻辑。
 */
object SummaryActionToast {

    private const val DURATION_MS = 3500L
    private const val ANIM_IN_MS = 280L
    private const val ANIM_OUT_MS = 220L
    private val TAG_ID = R.id.summary_action_toast_tag
    private const val TOAST_TAG_VALUE = "summary_action_toast"
    private const val WAIT_TIMEOUT_MS = 3000L

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 推荐入口: 等下一个非 [excludeActivity] 的 activity onResumed 后挂 toast。
     * 调用时机典型场景: createActivity finish() 之后立刻调, 把自身传 excludeActivity,
     * 让 toast 落到列表页 / 聊天页 (任意先回到前台的) 而不是已 destroy 的 createActivity。
     *
     * 找不到符合条件的 activity (3s 超时) 时静默放弃, 不抛异常。
     */
    @JvmStatic
    fun show(
        application: Application,
        excludeActivity: Activity?,
        text: CharSequence,
        actionTitle: CharSequence,
        onAction: (() -> Unit)?,
    ) {
        if (TextUtils.isEmpty(text)) return
        // 先看当前前台是否已经是合格的目标 activity (excludeActivity 不在前台 / 已经切出去)
        val current = ActManagerUtils.getInstance().currentActivity
        if (current != null && current !== excludeActivity && !current.isFinishing) {
            showOn(current, text, actionTitle, onAction)
            return
        }
        // 否则注册一次性 listener, 等下一个非 exclude 的 activity resume 时弹
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            private var fired = false
            override fun onActivityResumed(activity: Activity) {
                if (fired) return
                if (activity === excludeActivity) return  // 还是 createActivity 自己, 等下一个
                if (activity.isFinishing) return
                fired = true
                application.unregisterActivityLifecycleCallbacks(this)
                showOn(activity, text, actionTitle, onAction)
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        application.registerActivityLifecycleCallbacks(callbacks)
        // 兜底: 3s 内没等到合格 activity 就放弃, 防止 callbacks 永远注册占着内存
        mainHandler.postDelayed({
            application.unregisterActivityLifecycleCallbacks(callbacks)
        }, WAIT_TIMEOUT_MS)
    }

    /**
     * 在指定 activity 上直接弹。用于明确知道 host 的场景。
     */
    @JvmStatic
    fun showOn(
        activity: Activity,
        text: CharSequence,
        actionTitle: CharSequence,
        onAction: (() -> Unit)?,
    ) {
        if (TextUtils.isEmpty(text)) return
        val host = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        // 同 host 上若有上一次未消失的 toast, 先收掉, 避免堆叠遮挡。
        for (i in host.childCount - 1 downTo 0) {
            val v = host.getChildAt(i)
            if (v.getTag(TAG_ID) == TOAST_TAG_VALUE) host.removeView(v)
        }

        val ctx = activity
        val capsule = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setTag(TAG_ID, TOAST_TAG_VALUE)
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 24f).toFloat()
                setColor(ContextCompat.getColor(ctx, R.color.summary_hud_bg))
            }
            elevation = dp(ctx, 6f).toFloat()
        }

        val label = TextView(ctx).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ContextCompat.getColor(ctx, R.color.summary_hud_fg))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
        }
        val labelLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(ctx, 18f)
            marginEnd = dp(ctx, 8f)
        }
        capsule.addView(label, labelLp)

        val actionBtn = TextView(ctx).apply {
            this.text = actionTitle
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(ContextCompat.getColor(ctx, R.color.summary_purple))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
            // 容器高 MATCH_PARENT (= 48dp 整个胶囊高), 必须显式 CENTER 让文字垂直居中,
            // 默认 start|top 会把"查看"贴到胶囊顶, 与 iOS UIButton 自带 vertical center 不一致。
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            // ripple 用 selectableItemBackground 不太协调 (深色模式下高亮过暗); 简单 alpha 反馈即可
            setOnTouchListener { v, ev ->
                when (ev.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> v.alpha = 0.6f
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> v.alpha = 1f
                }
                false
            }
            val hPad = dp(ctx, 10f)
            setPadding(hPad, 0, hPad, 0)
        }
        val actionLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT,
        ).apply { marginEnd = dp(ctx, 6f) }
        capsule.addView(actionBtn, actionLp)

        // 容器 frame 顶到 host 全宽, 居中胶囊由 capsule 自身的 layoutParams 控制 (左右 margin + bottom margin)。
        val capsuleLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dp(ctx, 48f),
        ).apply {
            gravity = Gravity.BOTTOM
            leftMargin = dp(ctx, 16f)
            rightMargin = dp(ctx, 16f)
            // 兜底 80dp; rootInsets 读到 navbar 高度后再追加, 避免被系统底栏盖住
            bottomMargin = dp(ctx, 80f)
        }
        host.addView(capsule, capsuleLp)

        // 把当前 window 的底部 inset (gestures bar / nav bar) 算进 bottomMargin, 与 iOS safeAreaLayoutGuide 同口径。
        ViewCompat.setOnApplyWindowInsetsListener(capsule) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val lp = v.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = dp(ctx, 80f) + maxOf(nav, ime)
            v.layoutParams = lp
            insets
        }
        ViewCompat.requestApplyInsets(capsule)

        // 进场动画: y +16dp → 0 + alpha 0 → 1
        capsule.alpha = 0f
        capsule.translationY = dp(ctx, 16f).toFloat()
        capsule.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(ANIM_IN_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()

        val fired = booleanArrayOf(false)
        actionBtn.setOnClickListener {
            if (fired[0]) return@setOnClickListener
            fired[0] = true
            dismiss(host, capsule)
            onAction?.invoke()
        }

        mainHandler.postDelayed({
            if (!fired[0]) dismiss(host, capsule)
        }, DURATION_MS)
    }

    private fun dismiss(host: ViewGroup, capsule: View) {
        if (capsule.parent !== host) return
        capsule.animate()
            .alpha(0f)
            .translationY(dp(host.context, 16f).toFloat())
            .setDuration(ANIM_OUT_MS)
            .withEndAction { if (capsule.parent === host) host.removeView(capsule) }
            .start()
    }

    private fun dp(ctx: android.content.Context, v: Float): Int =
        (v * ctx.resources.displayMetrics.density + 0.5f).toInt()
}
