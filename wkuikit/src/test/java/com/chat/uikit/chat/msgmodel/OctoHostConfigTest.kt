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

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [OctoHostConfig] 里跟 Android 资源无关的部分（Palette / buildJson 模板）JVM 单测。
 *
 * `get(context)` / `paletteFromResources(context)` 依赖 Android Context 与 values-night 资源
 * 挑选，属于集成层——单测通过手工构造 [OctoHostConfig.Palette] 验证 JSON 模板正确、
 * 光/暗两个不同 palette 会产出不同 JSON。资源层的正确性由手动切主题验证。
 */
class OctoHostConfigTest {

    // 这里 lightPalette / darkPalette 是**合成测试数据**——用来验证「同一模板 + 不同 palette
    // 产不同 JSON」的模板正确性，**不代表** `values-night/color.xml` 里的真实生产用色（比如生
    // 产环境 good/warning/attention 三种夜间背景对齐 iOS 用的是 default 底色 #1C1C1E，这里为
    // 了让模板区分度可见故意给了不同值）。资源层的正确性由手动切系统主题验收。
    private val lightPalette = OctoHostConfig.Palette(
        cardBg = "#FFFFFFFF",
        emphasisBg = "#FFEEEEF2",
        accentBg = "#FFEBE8FE",
        goodBg = "#FFE7FBE9",
        warningBg = "#FFFFF8E5",
        attentionBg = "#FFFFEBEB",
        textPrimary = "#FF313131",
        textSubtle = "#FF999999",
        separatorLine = "#FFE5E5E5",
    )

    private val darkPalette = OctoHostConfig.Palette(
        cardBg = "#FF1C1C1E",
        emphasisBg = "#FF2C2C2E",
        accentBg = "#FF2E2547",
        goodBg = "#FF1F3D24",
        warningBg = "#FF3D3016",
        attentionBg = "#FF3D1B1B",
        textPrimary = "#FFFFFFFF",
        textSubtle = "#FFB0B0B0",
        separatorLine = "#FF2C2C2E",
    )

    @Test
    fun `buildJson produces valid parseable JSON`() {
        val json = OctoHostConfig.buildJson(lightPalette)
        // 不抛异常即算 valid（org.json 会严格挑格式错误）
        JSONObject(json)
    }

    @Test
    fun `light palette values land in containerStyles backgrounds`() {
        val root = JSONObject(OctoHostConfig.buildJson(lightPalette))
        val cs = root.getJSONObject("containerStyles")
        assertEquals(lightPalette.cardBg, cs.getJSONObject("default").getString("backgroundColor"))
        assertEquals(lightPalette.emphasisBg, cs.getJSONObject("emphasis").getString("backgroundColor"))
        assertEquals(lightPalette.accentBg, cs.getJSONObject("accent").getString("backgroundColor"))
        assertEquals(lightPalette.goodBg, cs.getJSONObject("good").getString("backgroundColor"))
        assertEquals(lightPalette.warningBg, cs.getJSONObject("warning").getString("backgroundColor"))
        assertEquals(lightPalette.attentionBg, cs.getJSONObject("attention").getString("backgroundColor"))
    }

    @Test
    fun `foregroundColors default follows textPrimary and subtle follows textSubtle`() {
        val root = JSONObject(OctoHostConfig.buildJson(darkPalette))
        val defaultFg = root.getJSONObject("containerStyles").getJSONObject("default")
            .getJSONObject("foregroundColors").getJSONObject("default")
        assertEquals(darkPalette.textPrimary, defaultFg.getString("default"))
        assertEquals(darkPalette.textSubtle, defaultFg.getString("subtle"))
    }

    @Test
    fun `separator lineColor comes from palette`() {
        val root = JSONObject(OctoHostConfig.buildJson(darkPalette))
        assertEquals(darkPalette.separatorLine, root.getJSONObject("separator").getString("lineColor"))
    }

    @Test
    fun `brand accent color stays fixed across palettes`() {
        val lightRoot = JSONObject(OctoHostConfig.buildJson(lightPalette))
        val darkRoot = JSONObject(OctoHostConfig.buildJson(darkPalette))
        val brand = "#7761F4"
        val lightAccent = lightRoot.getJSONObject("containerStyles").getJSONObject("default")
            .getJSONObject("foregroundColors").getJSONObject("accent").getString("default")
        val darkAccent = darkRoot.getJSONObject("containerStyles").getJSONObject("default")
            .getJSONObject("foregroundColors").getJSONObject("accent").getString("default")
        assertEquals("品牌紫两模式共用", brand, lightAccent)
        assertEquals("品牌紫两模式共用", brand, darkAccent)
    }

    @Test
    fun `light and dark palettes produce structurally different JSON`() {
        val light = OctoHostConfig.buildJson(lightPalette)
        val dark = OctoHostConfig.buildJson(darkPalette)
        assertNotEquals("light 和 dark JSON 不能相同（否则夜间没适配）", light, dark)
    }

    @Test
    fun `all six containerStyles have backgroundColor field`() {
        val cs = JSONObject(OctoHostConfig.buildJson(lightPalette)).getJSONObject("containerStyles")
        listOf("default", "emphasis", "accent", "good", "warning", "attention").forEach { style ->
            assertTrue(
                "containerStyle=$style 缺 backgroundColor",
                cs.getJSONObject(style).has("backgroundColor"),
            )
        }
    }

    @Test
    fun `fontSizes match compact scheme (13 default, not SDK default 14)`() {
        val root = JSONObject(OctoHostConfig.buildJson(lightPalette))
        val sizes = root.getJSONObject("fontSizes")
        assertEquals(12, sizes.getInt("small"))
        assertEquals(13, sizes.getInt("default"))
        assertEquals(14, sizes.getInt("medium"))
        assertEquals(17, sizes.getInt("large"))
        assertEquals(20, sizes.getInt("extraLarge"))
    }

    @Test
    fun `actions block enforces left alignment and 8dp button spacing`() {
        val actions = JSONObject(OctoHostConfig.buildJson(lightPalette)).getJSONObject("actions")
        assertEquals("left", actions.getString("actionAlignment"))
        assertEquals(8, actions.getInt("buttonSpacing"))
    }
}
