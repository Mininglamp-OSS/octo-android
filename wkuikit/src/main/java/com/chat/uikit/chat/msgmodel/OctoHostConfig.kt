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

import io.adaptivecards.objectmodel.HostConfig

/**
 * OctoIM 的 AdaptiveCards HostConfig（对齐 web `octo-web/dmworkbase/InteractiveCard/sdk/octoHostConfig.ts`）。
 *
 * 关键差异（相对 SDK 默认 `HostConfig()`）：
 *  - **字号紧凑一档**：default=13sp（默认 14）、medium=14（默认 17）、large=17（默认 21）、
 *    extraLarge=20（默认 26）。窄气泡里读起来不那么大而空。
 *  - **spacing.padding=16dp**：SDK 默认 10dp，太局促；与 web 保持一致。
 *  - **actions.buttonSpacing=8dp、alignment=left**：按钮左对齐、间距 8dp，避免默认 stretch
 *    把「收起推理」拉满行、把标题挤到换行。
 *  - **containerStyles 六种全部显式给背景**：SDK 默认只有 default 有背景色，
 *    emphasis/accent/good/warning/attention 全 `undefined`，服务端一给 `style: "emphasis"` 就退化
 *    成纯白（详见 web 排查结论）。这里显式补齐色值。
 *  - **前景色按语义**：good=#22C55E、warning=#FFC107、attention=#F80303，
 *    accent=品牌紫 #7761F4（与 App colorAccent 对齐）。
 *
 * 通过 [io.adaptivecards.objectmodel.HostConfig.DeserializeFromString] 一把加载 JSON，
 * 比逐字段调 setter 简洁 10 倍，且和 web 用同一份 schema。
 *
 * 单例缓存：整个 App 只有一套主题，无需每次 renderer 都重新反序列化。
 */
object OctoHostConfig {

    @Volatile
    private var cached: HostConfig? = null

    fun get(): HostConfig {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: HostConfig.DeserializeFromString(JSON).also { cached = it }
        }
    }

    // 颜色源：wkbase/res/values/color.xml 常用值（后续要暗色主题时可拓展为按 theme 切换）。
    // - textPrimary = colorDark #313131
    // - textSecondary = color999 #999999
    // - accent = colorAccent #7761F4
    // - surface = white #FFFFFF
    // - elevated = colorF5F5F5 #F5F5F5
    // - border subtle = dividerColor #E5E5E5
    private val JSON = """
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
            "lineColor": "#E5E5E5"
          },
          "supportsInteractivity": true,
          "fontTypes": {
            "default": {
              "fontFamily": "sans-serif",
              "fontSizes": {
                "small": 12,
                "default": 13,
                "medium": 14,
                "large": 17,
                "extraLarge": 20
              },
              "fontWeights": {
                "lighter": 300,
                "default": 400,
                "bolder": 600
              }
            },
            "monospace": {
              "fontFamily": "monospace",
              "fontSizes": {
                "small": 12,
                "default": 13,
                "medium": 14,
                "large": 17,
                "extraLarge": 20
              },
              "fontWeights": {
                "lighter": 300,
                "default": 400,
                "bolder": 600
              }
            }
          },
          "fontSizes": {
            "small": 12,
            "default": 13,
            "medium": 14,
            "large": 17,
            "extraLarge": 20
          },
          "fontWeights": {
            "lighter": 300,
            "default": 400,
            "bolder": 600
          },
          "containerStyles": {
            "default": {
              "backgroundColor": "#FFFFFF",
              "foregroundColors": {
                "default": { "default": "#313131", "subtle": "#999999" },
                "accent":  { "default": "#7761F4", "subtle": "#957761F4" },
                "good":    { "default": "#22C55E", "subtle": "#DD22C55E" },
                "warning": { "default": "#FFC107", "subtle": "#DDFFC107" },
                "attention": { "default": "#F80303", "subtle": "#DDF80303" }
              }
            },
            "emphasis": {
              "backgroundColor": "#EEEEF2",
              "foregroundColors": {
                "default": { "default": "#313131", "subtle": "#999999" },
                "accent":  { "default": "#7761F4", "subtle": "#957761F4" },
                "good":    { "default": "#22C55E", "subtle": "#DD22C55E" },
                "warning": { "default": "#FFC107", "subtle": "#DDFFC107" },
                "attention": { "default": "#F80303", "subtle": "#DDF80303" }
              }
            },
            "accent": {
              "backgroundColor": "#EBE8FE",
              "foregroundColors": {
                "default": { "default": "#313131", "subtle": "#999999" },
                "accent":  { "default": "#7761F4", "subtle": "#957761F4" },
                "good":    { "default": "#22C55E", "subtle": "#DD22C55E" },
                "warning": { "default": "#FFC107", "subtle": "#DDFFC107" },
                "attention": { "default": "#F80303", "subtle": "#DDF80303" }
              }
            },
            "good": {
              "backgroundColor": "#E7FBE9",
              "foregroundColors": {
                "default": { "default": "#313131", "subtle": "#999999" },
                "accent":  { "default": "#7761F4", "subtle": "#957761F4" },
                "good":    { "default": "#22C55E", "subtle": "#DD22C55E" },
                "warning": { "default": "#FFC107", "subtle": "#DDFFC107" },
                "attention": { "default": "#F80303", "subtle": "#DDF80303" }
              }
            },
            "warning": {
              "backgroundColor": "#FFF8E5",
              "foregroundColors": {
                "default": { "default": "#313131", "subtle": "#999999" },
                "accent":  { "default": "#7761F4", "subtle": "#957761F4" },
                "good":    { "default": "#22C55E", "subtle": "#DD22C55E" },
                "warning": { "default": "#FFC107", "subtle": "#DDFFC107" },
                "attention": { "default": "#F80303", "subtle": "#DDF80303" }
              }
            },
            "attention": {
              "backgroundColor": "#FFEBEB",
              "foregroundColors": {
                "default": { "default": "#313131", "subtle": "#999999" },
                "accent":  { "default": "#7761F4", "subtle": "#957761F4" },
                "good":    { "default": "#22C55E", "subtle": "#DD22C55E" },
                "warning": { "default": "#FFC107", "subtle": "#DDFFC107" },
                "attention": { "default": "#F80303", "subtle": "#DDF80303" }
              }
            }
          },
          "imageSizes": {
            "small": 40,
            "medium": 80,
            "large": 160
          },
          "actions": {
            "maxActions": 5,
            "spacing": "default",
            "buttonSpacing": 8,
            "showCard": {
              "actionMode": "inline",
              "inlineTopMargin": 8
            },
            "actionsOrientation": "horizontal",
            "actionAlignment": "left"
          },
          "adaptiveCard": {
            "allowCustomStyle": false
          },
          "imageSet": {
            "imageSize": "medium",
            "maxImageHeight": 100
          },
          "factSet": {
            "title": {
              "color": "default",
              "size": "default",
              "isSubtle": true,
              "weight": "default",
              "wrap": true,
              "maxWidth": 120
            },
            "value": {
              "color": "default",
              "size": "default",
              "isSubtle": false,
              "weight": "default",
              "wrap": true
            },
            "spacing": 6
          }
        }
    """.trimIndent()
}
