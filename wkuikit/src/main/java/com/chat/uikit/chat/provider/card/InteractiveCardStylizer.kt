/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.chat.uikit.chat.provider.card

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Button
import android.widget.CompoundButton
import androidx.core.content.ContextCompat
import com.chat.uikit.R

/**
 * 把 AC-SDK 渲染出的原生 Button / CompoundButton **改造成"聊天气泡内合适的紧凑样式"**：
 *
 * ## 为什么要做后置改造而不是注册 ActionElementRenderer
 * Action.Submit / OpenUrl / ToggleVisibility / ShowCard 各有默认 renderer 分支，
 * 逐一 override 工作量大且易漏；ChoiceSet 的 RadioButton / CheckBox 也是 SDK 内部拼装。
 * 一次 post-walk 全部覆盖，副作用可控（rendered.view 每次都是新对象，不需要撤销）。
 *
 * ## 改造项
 * 1. **Button**（含 Submit / OpenUrl / ToggleVisibility / ShowCard）：
 *    - 桌面级 Material 默认 minWidth=88dp / minHeight=48dp / 阴影 + 大写 —— 桌面级尺寸
 *    - 改成胶囊：min-height 28dp / 去 minWidth / textSize 13sp / wrap_content 宽 / 无阴影 / 关 setAllCaps
 *    - **按 Action.style 分色**（对齐 web .ac-pushButton.ac-destructive / .ac-positive）：
 *      - `destructive` → 红字（reminderColor）+ 浅粉底胶囊 → [R.drawable.shape_interactive_card_button_destructive]
 *      - `positive`    → 白字 + 深色底 (colorDark) 胶囊 → [R.drawable.shape_interactive_card_button_positive]
 *      - 默认         → accent 紫字 + accent-tint 底胶囊 → [R.drawable.shape_interactive_card_button]
 * 2. **CompoundButton**（ChoiceSet 里的 RadioButton / CheckBox）：
 *    - SDK 默认 0 垂直 gap，多选项贴一起像"连体胶囊"；加 4dp bottom margin 分开
 *
 * ## Action.style 是怎么传进来的
 * SDK 把 Action 渲染成 Button 后，Button 上没有直接暴露原 Action 的 style —— view 树里
 * 只留下 title (button.text)、icon、click handler。我们在 renderer parse 完 cardJson 后
 * **预扫一遍** Action.\* 节点，收集 `title → style` 映射（[actionStyles] 参数），在 walk view
 * 时按 button.text 反查。这个策略够用是因为同一张卡里不同 Action 的 title 不会重复。
 * 如果未来出现重名 Action 冲突，改成按 button.tag 或注册自定义 ActionElementRenderer。
 */
object InteractiveCardStylizer {

    /**
     * 单次遍历入口。
     *
     * @param root SDK 渲染出的 view tree（`RenderedAdaptiveCard.getView()`）
     * @param actionStyles button title → Action.style，由 renderer 层预先从 cardJson 扫出
     *   传入。空 map 表示所有按钮走默认样式。
     */
    fun stylize(root: View, actionStyles: Map<String, String> = emptyMap()) {
        val params = Params.from(root.context ?: return)
        walkTree(root) { v ->
            when {
                // RadioButton / CheckBox 归 CompoundButton；先判 Button 会被覆盖（Button 是
                // TextView 子类，但 RadioButton 不是 Button）—— 顺序敏感。
                v is CompoundButton -> applyCompound(v, params)
                v is Button -> {
                    val style = actionStyles[v.text?.toString().orEmpty()].orEmpty()
                    applyButton(v, params, style)
                }
            }
        }
    }

    private fun applyButton(v: Button, p: Params, style: String) {
        v.minWidth = 0
        v.minimumWidth = 0
        v.minHeight = p.btnMinH
        v.minimumHeight = p.btnMinH
        v.setPadding(p.btnPadH, p.btnPadV, p.btnPadH, p.btnPadV)
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        v.isAllCaps = false
        v.elevation = 0f
        v.stateListAnimator = null
        v.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        when (style) {
            "destructive" -> {
                v.setTextColor(p.destructive)
                v.setBackgroundResource(R.drawable.shape_interactive_card_button_destructive)
            }
            "positive" -> {
                // 深底白字：主 CTA（"允许"/"确认" 之类）。图标 URL 里已内嵌 white 色值。
                v.setTextColor(Color.WHITE)
                v.setBackgroundResource(R.drawable.shape_interactive_card_button_positive)
            }
            else -> {
                v.setTextColor(p.accent)
                v.setBackgroundResource(R.drawable.shape_interactive_card_button)
            }
        }
        v.layoutParams?.apply {
            height = LayoutParams.WRAP_CONTENT
            width = LayoutParams.WRAP_CONTENT
        }
    }

    private fun applyCompound(v: CompoundButton, p: Params) {
        val lp = v.layoutParams
        if (lp is MarginLayoutParams) {
            lp.bottomMargin = p.choiceGap
            v.layoutParams = lp
        }
    }

    private fun walkTree(view: View, visit: (View) -> Unit) {
        visit(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) walkTree(view.getChildAt(i), visit)
        }
    }

    /** 从 Context 一次性算出的样式常量，避免 walkTree 每个 node 重复 resolve。 */
    private data class Params(
        val accent: Int,
        val destructive: Int,
        val btnPadH: Int,
        val btnPadV: Int,
        val btnMinH: Int,
        val choiceGap: Int,
    ) {
        companion object {
            fun from(ctx: Context): Params {
                val density = ctx.resources.displayMetrics.density
                fun dp(v: Float): Int = (v * density + 0.5f).toInt()
                return Params(
                    accent = ContextCompat.getColor(ctx, com.chat.base.R.color.colorAccent),
                    destructive = ContextCompat.getColor(ctx, com.chat.base.R.color.reminderColor),
                    btnPadH = dp(12f),
                    btnPadV = dp(4f),
                    btnMinH = dp(28f),
                    choiceGap = dp(4f),
                )
            }
        }
    }
}
