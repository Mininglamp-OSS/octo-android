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

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.chat.uikit.R

/**
 * 简洁中央 HUD, 1:1 对齐 iOS [UIView showHUDWithHide:] (MBProgressHUDModeText):
 *   屏幕中央 + 半透明黑底圆角矩形 + 白字 + ~1s 自动消失, 不带图标。
 *
 * 用 Toast.setView 实现 (API 30+ 该 API 已废弃但项目最低 SDK 未到, 仍可用)。
 * Toast.LENGTH_SHORT = 2s, 与 iOS 1s 接近, 不再为此引入 MBProgressHUD 等价依赖。
 */
object SummaryHud {

    fun show(context: Context, message: CharSequence) {
        if (message.isEmpty()) return
        val view = TextView(context).apply {
            text = message
            setTextColor(ContextCompat.getColor(context, R.color.summary_hud_fg))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_summary_hud)
            val hPad = dp(context, 16f)
            val vPad = dp(context, 12f)
            setPadding(hPad, vPad, hPad, vPad)
        }
        @Suppress("DEPRECATION")
        Toast(context).apply {
            duration = Toast.LENGTH_SHORT
            this.view = view
            setGravity(Gravity.CENTER, 0, 0)
            show()
        }
    }

    fun show(context: Context, resId: Int) {
        show(context, context.getString(resId))
    }

    private fun dp(ctx: Context, v: Float): Int =
        (v * ctx.resources.displayMetrics.density + 0.5f).toInt()
}
