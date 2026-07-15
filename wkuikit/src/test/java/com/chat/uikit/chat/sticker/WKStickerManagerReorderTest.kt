/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.chat.sticker

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `WKStickerManager.reorderCache` 契约回归。
 *
 * 头图设计决策：`reorder(orderedIds)` 必须**只**用参数 `orderedIds` 排序，不能
 * 走 `applyOrder(StickerLocalOrderStore.read(), cache)` 路径——因为
 * `StickerLocalOrderStore.write()` 走 `apply()` 异步落盘，同 tick `read()` 会拿旧值，
 * 用户实测表现为"拖动松手后视觉反弹回旧序"。
 *
 * 这个测试锁死"只由入参决定结果"的契约：如果有人回退 `reorder()` 内部实现改回
 * `applyOrder(read(), cache)`，纯函数 `reorderCache` 的入参就变成了 SP 里的旧顺序，
 * 用当前入参 orderedIds 断言就会挂。
 */
class WKStickerManagerReorderTest {

    private fun mk(id: String): WKSticker = WKSticker().also { it.sticker_id = id }

    @Test
    fun `reorderCache uses only params, no persisted read`() {
        // 模拟：当前 cache 顺序 = [a, b, c]（假设服务端返回顺序）
        // 用户拖拽后传入 orderedIds = [c, a, b]
        val cache = listOf(mk("a"), mk("b"), mk("c"))
        val userDraggedOrder = listOf("c", "a", "b")

        val result = WKStickerManager.reorderCache(userDraggedOrder, cache)

        // 结果必须严格按 orderedIds 参数排——不能依赖任何持久化 read()。
        assertEquals(userDraggedOrder, result.map { it.sticker_id })
    }

    @Test
    fun `reorderCache preserves server-fresh ids by appending to tail`() {
        // 用户只拖过 c、a；b、d 是别端刚同步来的新贴纸（用户还没拖过）。
        val cache = listOf(mk("a"), mk("b"), mk("c"), mk("d"))
        val partialUserOrder = listOf("c", "a")

        val result = WKStickerManager.reorderCache(partialUserOrder, cache)

        // 前置 c、a；未登记的 b、d 按 cache 顺序追加尾部。
        assertEquals(listOf("c", "a", "b", "d"), result.map { it.sticker_id })
    }

    @Test
    fun `reorderCache with empty params returns cache unchanged`() {
        val cache = listOf(mk("a"), mk("b"))
        val result = WKStickerManager.reorderCache(emptyList(), cache)
        assertEquals(listOf("a", "b"), result.map { it.sticker_id })
    }
}
