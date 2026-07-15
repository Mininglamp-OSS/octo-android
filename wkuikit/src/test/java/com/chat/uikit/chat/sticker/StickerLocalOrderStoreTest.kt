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
 * 本地顺序表的纯函数逻辑回归。SP 写入路径依赖 Android runtime，此处只测排序 /
 * 修剪算法（两个纯函数）；SP 层由 instrumented test 或 runtime 验证。
 */
class StickerLocalOrderStoreTest {

    private fun mk(id: String): WKSticker = WKSticker().also { it.sticker_id = id }

    @Test
    fun `empty local order returns server list unchanged`() {
        val server = listOf(mk("a"), mk("b"), mk("c"))
        val out = StickerLocalOrderStore.applyOrder(emptyList(), server)
        assertEquals(listOf("a", "b", "c"), out.map { it.sticker_id })
    }

    @Test
    fun `full local order reorders server list`() {
        val server = listOf(mk("a"), mk("b"), mk("c"))
        val out = StickerLocalOrderStore.applyOrder(listOf("c", "a", "b"), server)
        assertEquals(listOf("c", "a", "b"), out.map { it.sticker_id })
    }

    @Test
    fun `partial local order puts registered first then server-fresh appended`() {
        // 用户拖过 c,a；此后 b, d 是别端新增（服务端返回顺序里）
        val server = listOf(mk("a"), mk("b"), mk("c"), mk("d"))
        val out = StickerLocalOrderStore.applyOrder(listOf("c", "a"), server)
        // c,a 按本地顺序前置；b,d 按服务端返回顺序追加
        assertEquals(listOf("c", "a", "b", "d"), out.map { it.sticker_id })
    }

    @Test
    fun `stale ids in local order are ignored`() {
        // 本地顺序里的 x 服务端已删，applyOrder 会忽略它（不会 NPE 也不会占位）
        val server = listOf(mk("a"), mk("b"))
        val out = StickerLocalOrderStore.applyOrder(listOf("x", "a"), server)
        assertEquals(listOf("a", "b"), out.map { it.sticker_id })
    }

    @Test
    fun `empty server list returns empty`() {
        val out = StickerLocalOrderStore.applyOrder(listOf("a", "b"), emptyList())
        assertEquals(emptyList<String>(), out.map { it.sticker_id })
    }

    @Test
    fun `prune removes stale ids and preserves order`() {
        val out = StickerLocalOrderStore.pruneOrder(
            listOf("a", "x", "b", "y", "c"),
            setOf("a", "b", "c")
        )
        assertEquals(listOf("a", "b", "c"), out)
    }

    @Test
    fun `prune with empty valid set returns empty`() {
        val out = StickerLocalOrderStore.pruneOrder(listOf("a", "b"), emptySet())
        assertEquals(emptyList<String>(), out)
    }

    @Test
    fun `prune with all valid keeps everything`() {
        val out = StickerLocalOrderStore.pruneOrder(
            listOf("a", "b", "c"),
            setOf("a", "b", "c", "d")
        )
        assertEquals(listOf("a", "b", "c"), out)
    }
}
