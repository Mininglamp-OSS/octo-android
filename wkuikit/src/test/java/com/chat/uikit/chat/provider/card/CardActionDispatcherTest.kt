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

import com.alibaba.fastjson.JSONObject
import com.xinbida.wukongim.entity.WKChannelType
import com.xinbida.wukongim.entity.WKMsg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [CardActionDispatcher] 的行为单测。SDK 类型不出现在本文件——所有输入都是纯
 * Kotlin [CardAction] 值对象；所有副作用（HTTP / WebView / clipboard / toast /
 * timeout / extra sync）通过 fake 观察。
 */
class CardActionDispatcherTest {

    private lateinit var submitter: FakeCardSubmitter
    private lateinit var webView: RecordingWebViewLauncher
    private lateinit var toaster: RecordingToaster
    private lateinit var scheduler: FakeTimeoutScheduler
    private lateinit var extraSync: RecordingExtraSync
    private lateinit var uiListener: RecordingUiListener
    private lateinit var dispatcher: CardActionDispatcher

    private val strings = CardActionDispatcher.Strings(
        openUrlFailed = "open_failed",
        actionRetry = "retry",
        actionFailed = "failed",
        actionTimeout = "timeout",
    )

    private var selfUid: String? = "me"

    @Before
    fun setUp() {
        submitter = FakeCardSubmitter()
        webView = RecordingWebViewLauncher(returns = true)
        toaster = RecordingToaster()
        scheduler = FakeTimeoutScheduler()
        extraSync = RecordingExtraSync()
        uiListener = RecordingUiListener()
        dispatcher = CardActionDispatcher(
            submitter = submitter,
            webView = webView,
            toaster = toaster,
            timeoutScheduler = scheduler,
            extraSync = extraSync,
            selfUidProvider = { selfUid },
            uiListener = uiListener,
            strings = strings,
        )
    }

    // ─────────────────────────────── OpenUrl ───────────────────────────────

    @Test
    fun `dispatch OpenUrl launches WebView`() {
        dispatcher.dispatch(ctx(), CardAction.OpenUrl(actionId = "a1", url = "https://example.com/x"))
        assertEquals(listOf("https://example.com/x"), webView.calls)
        assertTrue("no toast on success", toaster.messages.isEmpty())
    }

    @Test
    fun `dispatch OpenUrl blank url is ignored`() {
        dispatcher.dispatch(ctx(), CardAction.OpenUrl(actionId = "a1", url = ""))
        assertTrue(webView.calls.isEmpty())
    }

    @Test
    fun `dispatch OpenUrl launcher fails triggers toast`() {
        webView.returns = false
        dispatcher.dispatch(ctx(), CardAction.OpenUrl(actionId = "a1", url = "https://x"))
        assertEquals(listOf("open_failed"), toaster.messages)
    }

    // ─────────────────────────────── Submit trust gate ───────────────────────────────

    @Test
    fun `dispatch Submit with allowSubmit=false is blocked before HTTP`() {
        dispatcher.dispatch(
            ctx(allowSubmit = false),
            CardAction.Submit(actionId = "a1", inputs = emptyMap()),
        )
        assertTrue("no HTTP", submitter.calls.isEmpty())
        assertTrue("no UI callback", uiListener.starts.isEmpty())
    }

    // ─────────────────────────────── Submit body composition ───────────────────────────────

    @Test
    fun `dispatch Submit body carries required keys and no data field`() {
        dispatcher.dispatch(
            ctx(msg = msg("m1", "chan1", WKChannelType.GROUP, "peer")),
            CardAction.Submit(actionId = "a1", inputs = mapOf("x" to "1", "y" to "2")),
        )
        val body = submitter.calls.single().body
        assertEquals("m1", body.getString("message_id"))
        assertEquals("chan1", body.getString("channel_id"))
        assertEquals(WKChannelType.GROUP.toInt(), body.getIntValue("channel_type"))
        assertEquals("a1", body.getString("action_id"))
        assertEquals(2, body.getJSONObject("inputs").size)
        assertEquals("1", body.getJSONObject("inputs").getString("x"))
        assertNotNull("client_token 必填", body.getString("client_token"))
        assertFalse("刻意不传 data（D11 防伪造）", body.containsKey("data"))
    }

    @Test
    fun `dispatch Submit collapses PERSONAL DM to fromUID when channelID == selfUid`() {
        selfUid = "me"
        dispatcher.dispatch(
            // 系统 bot 塌缩场景：PERSONAL + channelID==selfUid → 打给 fromUID
            ctx(msg = msg("m1", "me", WKChannelType.PERSONAL, "botUid")),
            CardAction.Submit(actionId = "a1", inputs = emptyMap()),
        )
        assertEquals("botUid", submitter.calls.single().body.getString("channel_id"))
    }

    @Test
    fun `dispatch Submit preserves channelID on non-PERSONAL channels`() {
        selfUid = "me"
        dispatcher.dispatch(
            ctx(msg = msg("m1", "me", WKChannelType.GROUP, "peer")),
            CardAction.Submit(actionId = "a1", inputs = emptyMap()),
        )
        // GROUP 不塌缩，即便 channelID 恰巧等于 selfUid
        assertEquals("me", submitter.calls.single().body.getString("channel_id"))
    }

    @Test
    fun `dispatch Submit no actionId is rejected before HTTP`() {
        dispatcher.dispatch(
            ctx(),
            CardAction.Submit(actionId = "", inputs = emptyMap()),
        )
        assertTrue(submitter.calls.isEmpty())
    }

    // ─────────────────────────────── Submit UI hooks ───────────────────────────────

    @Test
    fun `dispatch Submit fires onSubmitStart before HTTP`() {
        dispatcher.dispatch(ctx(), CardAction.Submit(actionId = "a1", inputs = emptyMap()))
        assertEquals(listOf("m1"), uiListener.starts)
    }

    @Test
    fun `dispatch Submit onSuccess fires onSubmitEnd and triggers extra sync + retries`() {
        dispatcher.dispatch(
            ctx(msg = msg("m1", "chan1", WKChannelType.PERSONAL, "peer")),
            CardAction.Submit(actionId = "a1", inputs = emptyMap()),
        )
        submitter.calls.single().onSuccess(JSONObject())
        assertEquals(listOf("m1"), uiListener.ends)
        // 立即 sync 一次 + 两个 delayed
        assertEquals(1, extraSync.calls.size)
        assertEquals("chan1", extraSync.calls[0].first)
        assertEquals(listOf(500L, 1500L), scheduler.scheduledDelays().sorted())
        // fire delayed → 触发额外 sync
        scheduler.fireAll()
        assertEquals(3, extraSync.calls.size)
    }

    @Test
    fun `dispatch Submit onFail 409 shows retry toast and fires onSubmitEnd`() {
        dispatcher.dispatch(ctx(), CardAction.Submit(actionId = "a1", inputs = emptyMap()))
        submitter.calls.single().onFail(409, "conflict")
        assertEquals(listOf("retry"), toaster.messages)
        assertEquals(listOf("m1"), uiListener.ends)
    }

    @Test
    fun `dispatch Submit onFail 400 shows failed toast`() {
        dispatcher.dispatch(ctx(), CardAction.Submit(actionId = "a1", inputs = emptyMap()))
        submitter.calls.single().onFail(400, "bad")
        assertEquals(listOf("failed"), toaster.messages)
    }

    @Test
    fun `dispatch Submit onFail 500 uses retry toast (server side)`() {
        dispatcher.dispatch(ctx(), CardAction.Submit(actionId = "a1", inputs = emptyMap()))
        submitter.calls.single().onFail(500, "boom")
        assertEquals(listOf("retry"), toaster.messages)
    }

    // ─────────────────────────────── Dedup & timeout ───────────────────────────────

    @Test
    fun `dispatch Submit twice for same messageId is deduped`() {
        dispatcher.dispatch(ctx(), CardAction.Submit(actionId = "a1", inputs = emptyMap()))
        dispatcher.dispatch(ctx(), CardAction.Submit(actionId = "a1", inputs = emptyMap()))
        assertEquals(1, submitter.calls.size)
    }

    @Test
    fun `dispatch Submit timeout fires toast when still submitting`() {
        dispatcher.dispatch(ctx(), CardAction.Submit(actionId = "a1", inputs = emptyMap()))
        // 找到 10_000ms 那个任务（不是 500/1500 的兜底 sync）
        val timeout = scheduler.entries.single { it.delayMs == 10_000L }
        timeout.fire()
        assertEquals(listOf("timeout"), toaster.messages)
        assertFalse(dispatcher.isSubmitting("m1"))
    }

    @Test
    fun `dispatch Submit timeout after success is no-op (already cleared)`() {
        dispatcher.dispatch(ctx(), CardAction.Submit(actionId = "a1", inputs = emptyMap()))
        submitter.calls.single().onSuccess(null)
        // success 已经 cancel 了 timeout；即便 fake 手动 fire 也应该不产 toast
        val timeout = scheduler.entries.singleOrNull { it.delayMs == 10_000L }
        // dispatcher.clearSubmitting 应 cancel timeout → cancelled 标记
        assertTrue("timeout handle 应已被 cancel", timeout == null || timeout.cancelled)
    }

    @Test
    fun `clearSubmitting removes state and cancels pending timeout`() {
        dispatcher.dispatch(ctx(), CardAction.Submit(actionId = "a1", inputs = emptyMap()))
        assertTrue(dispatcher.isSubmitting("m1"))
        dispatcher.clearSubmitting("m1")
        assertFalse(dispatcher.isSubmitting("m1"))
        val timeout = scheduler.entries.single { it.delayMs == 10_000L }
        assertTrue(timeout.cancelled)
    }

    @Test
    fun `onDestroy cancels all pending timeouts and clears state`() {
        dispatcher.dispatch(
            ctx(msg = msg("m1", "chan", WKChannelType.GROUP, "u")),
            CardAction.Submit(actionId = "a1", inputs = emptyMap()),
        )
        dispatcher.dispatch(
            ctx(msg = msg("m2", "chan", WKChannelType.GROUP, "u")),
            CardAction.Submit(actionId = "a2", inputs = emptyMap()),
        )
        dispatcher.onDestroy()
        assertFalse(dispatcher.isSubmitting("m1"))
        assertFalse(dispatcher.isSubmitting("m2"))
        assertTrue(scheduler.entries.all { it.cancelled })
    }

    // ─────────────────────────────── Helpers ───────────────────────────────

    private fun msg(
        messageId: String = "m1",
        channelId: String = "chan1",
        channelType: Byte = WKChannelType.PERSONAL,
        fromUid: String = "peer",
    ): WKMsg = WKMsg().apply {
        this.messageID = messageId
        this.channelID = channelId
        this.channelType = channelType
        this.fromUID = fromUid
    }

    private fun ctx(
        msg: WKMsg = msg(),
        allowSubmit: Boolean = true,
    ) = MessageContext(wkMsg = msg, allowSubmit = allowSubmit)

    // ─────────────────────────────── Fakes ───────────────────────────────

    private class FakeCardSubmitter : CardActionDispatcher.CardSubmitter {
        data class Call(
            val body: JSONObject,
            val onSuccess: (JSONObject?) -> Unit,
            val onFail: (Int, String?) -> Unit,
        )

        val calls = mutableListOf<Call>()

        override fun submit(
            body: JSONObject,
            onSuccess: (JSONObject?) -> Unit,
            onFail: (Int, String?) -> Unit,
        ) {
            calls += Call(body, onSuccess, onFail)
        }
    }

    private class RecordingWebViewLauncher(var returns: Boolean) : CardActionDispatcher.WebViewLauncher {
        val calls = mutableListOf<String>()
        override fun open(url: String): Boolean {
            calls += url
            return returns
        }
    }

    private class RecordingToaster : CardActionDispatcher.Toaster {
        val messages = mutableListOf<String>()
        override fun show(text: String) {
            messages += text
        }
    }

    private class RecordingExtraSync : CardActionDispatcher.ExtraMsgSyncer {
        val calls = mutableListOf<Pair<String, Byte>>()
        override fun sync(channelId: String, channelType: Byte) {
            calls += channelId to channelType
        }
    }

    private class RecordingUiListener : CardActionDispatcher.SubmitUiListener {
        val starts = mutableListOf<String>()
        val ends = mutableListOf<String>()
        override fun onSubmitStart(messageId: String) {
            starts += messageId
        }

        override fun onSubmitEnd(messageId: String) {
            ends += messageId
        }
    }

    /**
     * 手动可控的 scheduler：postDelayed 只记账不实际调度；测试通过 [fireAll] 或
     * 找特定 delayMs 的 entry.fire() 触发。
     */
    private class FakeTimeoutScheduler : CardActionDispatcher.TimeoutScheduler {
        class Entry(val delayMs: Long, val task: () -> Unit) {
            var cancelled: Boolean = false
                private set

            fun fire() {
                if (!cancelled) task()
            }

            fun markCancelled() {
                cancelled = true
            }
        }

        val entries = mutableListOf<Entry>()

        override fun postDelayed(
            delayMs: Long,
            task: () -> Unit,
        ): CardActionDispatcher.TimeoutScheduler.Handle {
            val entry = Entry(delayMs, task)
            entries += entry
            return CardActionDispatcher.TimeoutScheduler.Handle { entry.markCancelled() }
        }

        fun fireAll() {
            // 拷贝快照：task 触发时可能又 postDelayed，避免 ConcurrentModificationException
            entries.toList().forEach { it.fire() }
        }

        fun scheduledDelays(): List<Long> = entries.filter { !it.cancelled }.map { it.delayMs }
    }
}
