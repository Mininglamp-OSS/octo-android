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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [InteractiveCardRenderer] 的 JVM 可测部分。
 *
 * 真正的渲染路径依赖 AdaptiveCards SWIG native lib + Android View + FragmentActivity，
 * JVM 单测无法覆盖——那部分靠**手动验证**（滚动性能、Input 状态保留、bot 编辑帧刷新、
 * 深色切换、Action 全路径回归）。
 *
 * 这里只覆盖**纯 Kotlin 语义**：缓存键的等值语义（保证同 payload 命中缓存、不同 payload
 * 一定 miss、深色/亮色不混用）+ Result sealed 结构。
 */
class InteractiveCardRendererTest {

    @Test
    fun `CacheKey with same content-version-mode equals`() {
        val a = InteractiveCardRenderer.CacheKey(cardJsonHash = 123, cardVersion = "1.5", isDark = false)
        val b = InteractiveCardRenderer.CacheKey(cardJsonHash = 123, cardVersion = "1.5", isDark = false)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `CacheKey differs when cardJsonHash differs (bot edit frame → miss → re-render)`() {
        val before = InteractiveCardRenderer.CacheKey(cardJsonHash = 111, cardVersion = "1.5", isDark = false)
        val after = InteractiveCardRenderer.CacheKey(cardJsonHash = 222, cardVersion = "1.5", isDark = false)
        assertNotEquals(before, after)
    }

    @Test
    fun `CacheKey differs when cardVersion differs (server schema upgrade)`() {
        val v15 = InteractiveCardRenderer.CacheKey(cardJsonHash = 111, cardVersion = "1.5", isDark = false)
        val v16 = InteractiveCardRenderer.CacheKey(cardJsonHash = 111, cardVersion = "1.6", isDark = false)
        assertNotEquals(v15, v16)
    }

    @Test
    fun `CacheKey differs when isDark differs (theme switch)`() {
        val light = InteractiveCardRenderer.CacheKey(cardJsonHash = 111, cardVersion = "1.5", isDark = false)
        val dark = InteractiveCardRenderer.CacheKey(cardJsonHash = 111, cardVersion = "1.5", isDark = true)
        assertNotEquals(light, dark)
    }

    @Test
    fun `Success is a singleton object`() {
        val a: InteractiveCardRenderer.Result = InteractiveCardRenderer.Result.Success
        val b: InteractiveCardRenderer.Result = InteractiveCardRenderer.Result.Success
        assertTrue(a === b)
    }

    @Test
    fun `Fallback carries reason and cardJsonHash for provider-side dedup logging`() {
        val fallback = InteractiveCardRenderer.Result.Fallback(reason = "sdk parse", cardJsonHash = 42)
        assertEquals("sdk parse", fallback.reason)
        assertEquals(42, fallback.cardJsonHash)
    }

    @Test
    fun `two Fallback with same reason and hash are equal (data class)`() {
        val a = InteractiveCardRenderer.Result.Fallback(reason = "boom", cardJsonHash = 1)
        val b = InteractiveCardRenderer.Result.Fallback(reason = "boom", cardJsonHash = 1)
        assertEquals(a, b)
    }

    @Test
    fun `CardRenderSpec is a data class value object`() {
        val a = CardRenderSpec(cardJson = "{}", cardVersion = "1.5")
        val b = CardRenderSpec(cardJson = "{}", cardVersion = "1.5")
        assertEquals(a, b)
        assertNotNull(a.hashCode())
    }
}
