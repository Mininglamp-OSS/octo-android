/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.chat.uikit.chat.msgmodel

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.chat.uikit.R
import io.adaptivecards.objectmodel.HostConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * OctoIM 的 AdaptiveCards HostConfig（对齐 web `octo-web/dmworkbase/InteractiveCard/sdk/octoHostConfig.ts`）。
 *
 * ## 关键差异（相对 SDK 默认 `HostConfig()`）
 *  - **字号紧凑一档**：default=13sp（默认 14）、medium=14（默认 17）、large=17（默认 21）、
 *    extraLarge=20（默认 26）。窄气泡里读起来不那么大而空。
 *  - **spacing.padding=12dp**：SDK 默认 10dp，太局促；与 web 保持一致。
 *  - **actions.buttonSpacing=8dp、alignment=left**：按钮左对齐、间距 8dp，避免默认 stretch
 *    把「收起推理」拉满行、把标题挤到换行。
 *  - **containerStyles 六种全部显式给背景**：SDK 默认只有 default 有背景色，
 *    emphasis/accent/good/warning/attention 全 `undefined`，服务端一给 `style: "emphasis"` 就退化
 *    成纯白（详见 web 排查结论）。这里显式补齐色值。
 *
 * ## 深色模式（C2）
 *
 * 拆成两块：
 *  - **值**（[Palette]）从 Android 资源系统读，`values-night/color.xml` 自动生效——单一
 *    source of truth，未来调色只改一份 `color.xml` 即可。
 *  - **模板**（[buildJson]）是纯 Kotlin 函数，把 [Palette] 内嵌进 JSON 后交给
 *    SDK 反序列化。JVM 单测可以直接构造 [Palette] 验证模板正确。
 *
 * 品牌语义色（accent 品牌紫 / good 绿 / warning 黄 / attention 红）**不进 palette**：它们
 * 是纯品牌/语义色，两种模式下都保持同一色相，subtle 变体走 `#AARRGGBB` alpha 复用主色，
 * 硬编码在模板里更简洁。
 *
 * ## 缓存
 *
 * `HostConfig.DeserializeFromString` 走 SWIG 反射有开销，缓存 light/dark 两个实例（size 2）。
 * 系统切主题会 Activity recreate → 新一轮 [get] 自然从当前 context 拿正确 mode 的 palette。
 */
object OctoHostConfig {

    /**
     * HostConfig 里所有随主题变化的色值。JVM 层的纯数据结构，方便单测。
     *
     * 与 [buildJson] 严格 1:1——加字段务必同步改模板，否则字段被静默忽略。
     */
    data class Palette(
        val cardBg: String,
        val emphasisBg: String,
        val accentBg: String,
        val goodBg: String,
        val warningBg: String,
        val attentionBg: String,
        val textPrimary: String,
        val textSubtle: String,
        val separatorLine: String,
    )

    private val cached = ConcurrentHashMap<Boolean, HostConfig>(2)

    /**
     * 拿到当前 [context] 主题对应的 HostConfig。首次调用会构造并缓存；同一主题再调返回
     * 同一实例。Activity 因主题切换 recreate 后，context 是新的但结果仍从 cache 命中
     * （cache key 是 isDark 布尔，与 context 实例无关）。
     *
     * 内部用 [applicationContext] 而不是传入的 context——避免 singleton 意外持有
     * Activity；也确保 `configuration.uiMode` 从 App 全局配置读，跟系统主题一致。
     */
    fun get(context: Context): HostConfig {
        val appCtx = context.applicationContext
        val isDark = (appCtx.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        // computeIfAbsent 在 ConcurrentHashMap 上是原子的 + 保证 compute 只跑一次，
        // 避免并发 miss 时重复走昂贵的 SWIG 反序列化。
        return cached.computeIfAbsent(isDark) {
            HostConfig.DeserializeFromString(buildJson(paletteFromResources(appCtx)))
        }
    }

    /**
     * 从 Android 资源系统读色，`values-night/color.xml` 自动生效。
     *
     * 复用已有 flip token：
     *  - `textPrimary` = `colorDark`（wkbase：#313131 → #FFFFFF）
     *  - `separatorLine` = `dividerColor`（wkbase：#E5E5E5 → #2C2C2E）
     *
     * 新增 card 专属 token（避免动全局 white / colorF5F5F5 / color999 影响其它模块）：
     *  - 6 个 `interactive_card_bg_*`（AC containerStyles 六种独有底色）
     *  - 1 个 `interactive_card_text_subtle`（color999 全局无夜间值）
     */
    internal fun paletteFromResources(context: Context): Palette = Palette(
        cardBg = hex(context, R.color.interactive_card_bg),
        emphasisBg = hex(context, R.color.interactive_card_bg_emphasis),
        accentBg = hex(context, R.color.interactive_card_bg_accent),
        goodBg = hex(context, R.color.interactive_card_bg_good),
        warningBg = hex(context, R.color.interactive_card_bg_warning),
        attentionBg = hex(context, R.color.interactive_card_bg_attention),
        textPrimary = hex(context, com.chat.base.R.color.colorDark),
        textSubtle = hex(context, R.color.interactive_card_text_subtle),
        separatorLine = hex(context, com.chat.base.R.color.dividerColor),
    )

    /**
     * 纯函数：把 [palette] 展开到 HostConfig JSON 模板里。品牌语义色（accent/good/warning/
     * attention）不参与主题——硬编码在模板中。
     */
    internal fun buildJson(palette: Palette): String = """
        {
          "spacing": {
            "small": 4,
            "default": 8,
            "medium": 12,
            "large": 16,
            "extraLarge": 20,
            "padding": 12
          },
          "separator": {
            "lineThickness": 1,
            "lineColor": "${palette.separatorLine}"
          },
          "supportsInteractivity": true,
          "fontTypes": {
            "default": {
              "fontFamily": "sans-serif",
              "fontSizes": { "small": 12, "default": 13, "medium": 14, "large": 17, "extraLarge": 20 },
              "fontWeights": { "lighter": 300, "default": 400, "bolder": 600 }
            },
            "monospace": {
              "fontFamily": "monospace",
              "fontSizes": { "small": 12, "default": 13, "medium": 14, "large": 17, "extraLarge": 20 },
              "fontWeights": { "lighter": 300, "default": 400, "bolder": 600 }
            }
          },
          "fontSizes": { "small": 12, "default": 13, "medium": 14, "large": 17, "extraLarge": 20 },
          "fontWeights": { "lighter": 300, "default": 400, "bolder": 600 },
          "containerStyles": {
            "default":   { "backgroundColor": "${palette.cardBg}",       "foregroundColors": ${foregroundColorsJson(palette)} },
            "emphasis":  { "backgroundColor": "${palette.emphasisBg}",   "foregroundColors": ${foregroundColorsJson(palette)} },
            "accent":    { "backgroundColor": "${palette.accentBg}",     "foregroundColors": ${foregroundColorsJson(palette)} },
            "good":      { "backgroundColor": "${palette.goodBg}",       "foregroundColors": ${foregroundColorsJson(palette)} },
            "warning":   { "backgroundColor": "${palette.warningBg}",    "foregroundColors": ${foregroundColorsJson(palette)} },
            "attention": { "backgroundColor": "${palette.attentionBg}",  "foregroundColors": ${foregroundColorsJson(palette)} }
          },
          "imageSizes": { "small": 40, "medium": 80, "large": 160 },
          "actions": {
            "maxActions": 5,
            "spacing": "default",
            "buttonSpacing": 8,
            "showCard": { "actionMode": "inline", "inlineTopMargin": 8 },
            "actionsOrientation": "horizontal",
            "actionAlignment": "left"
          },
          "adaptiveCard": { "allowCustomStyle": false },
          "imageSet": { "imageSize": "medium", "maxImageHeight": 100 },
          "factSet": {
            "title": { "color": "default", "size": "default", "isSubtle": true,  "weight": "default", "wrap": true, "maxWidth": 120 },
            "value": { "color": "default", "size": "default", "isSubtle": false, "weight": "default", "wrap": true },
            "spacing": 6
          }
        }
    """.trimIndent()

    /**
     * 各 containerStyle 共用同一份 foregroundColors —— 只有 default/subtle 跟 palette 走。
     * 刻意返回 minified 单行 JSON：内嵌进外层 [buildJson] 模板时不破坏外层缩进
     * （否则 trimIndent 会因内嵌行有 0 缩进而放弃 trim，导致日志里 JSON 缩进错乱）。
     */
    private fun foregroundColorsJson(p: Palette): String =
        """{"default":{"default":"${p.textPrimary}","subtle":"${p.textSubtle}"},""" +
        """"accent":{"default":"$ACCENT","subtle":"$ACCENT_SUBTLE"},""" +
        """"good":{"default":"$GOOD","subtle":"$GOOD_SUBTLE"},""" +
        """"warning":{"default":"$WARNING","subtle":"$WARNING_SUBTLE"},""" +
        """"attention":{"default":"$ATTENTION","subtle":"$ATTENTION_SUBTLE"}}"""

    private fun hex(context: Context, @ColorRes id: Int): String {
        val c = ContextCompat.getColor(context, id)
        // getColor 返回 AARRGGBB int；HostConfig 认 #RRGGBB 和 #AARRGGBB 两种格式。
        return String.format("#%08X", c)
    }

    // ── 品牌 / 语义色：两种模式下都保持同一色相，不进 Palette ─────────────────
    private const val ACCENT = "#7761F4"           // 品牌紫（与 App colorAccent 对齐）
    private const val ACCENT_SUBTLE = "#957761F4"  // 品牌紫 alpha 0x95 ≈ 58%
    private const val GOOD = "#22C55E"
    private const val GOOD_SUBTLE = "#DD22C55E"    // alpha 0xDD ≈ 87%
    private const val WARNING = "#FFC107"
    private const val WARNING_SUBTLE = "#DDFFC107"
    private const val ATTENTION = "#F80303"
    private const val ATTENTION_SUBTLE = "#DDF80303"
}
