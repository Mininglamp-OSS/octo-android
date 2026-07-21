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
 * 深色切换、Action 全路径回归、以及"两条同 payload 消息同屏"不再互抢 view 的回归）。
 *
 * 这里只覆盖**纯 Kotlin 语义**：Result sealed 结构 + CardRenderSpec 值对象。
 * 缓存键换成 messageId 后不再是纯值对象（内含 View 和 RenderedAdaptiveCard），无法在
 * JVM 层直接构造校验；命中/失效路径靠仪器化测试或手动回归覆盖。
 */
class InteractiveCardRendererTest {

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
    fun `Fallback differs when hash differs (bot edit frame → new fallback log)`() {
        val a = InteractiveCardRenderer.Result.Fallback(reason = "boom", cardJsonHash = 1)
        val b = InteractiveCardRenderer.Result.Fallback(reason = "boom", cardJsonHash = 2)
        assertNotEquals(a, b)
    }

    @Test
    fun `CardRenderSpec is a data class value object`() {
        val a = CardRenderSpec(cardJson = "{}", cardVersion = "1.5")
        val b = CardRenderSpec(cardJson = "{}", cardVersion = "1.5")
        assertEquals(a, b)
        assertNotNull(a.hashCode())
    }
}
