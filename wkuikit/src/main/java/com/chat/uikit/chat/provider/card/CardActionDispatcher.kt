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

import android.util.Log
import com.alibaba.fastjson.JSONObject
import com.xinbida.wukongim.entity.WKChannelType
import java.util.UUID

/**
 * 交互式卡片的**业务分派器**——纯 Kotlin，全部副作用通过 SAM 接口注入，
 * JVM 单测无需 Robolectric 也无需 SDK 类。
 *
 * ## 职责
 *
 *  - [CardAction.OpenUrl] → 拉起 App 内 WebView（[WebViewLauncher]）
 *  - [CardAction.Submit] → POST /v1/message/card/action（[CardSubmitter]），
 *    含防重复点击、10s 超时兜底、成功后 syncExtraMsg + 500/1500ms 兜底 retry
 *
 * `Action.CopyToClipboard` 未支持 —— AC 3.7.0 Android SDK 无内置
 * `CopyToClipboardActionParser`，白名单已在 [com.chat.uikit.chat.msgmodel.InteractiveCardDecision]
 * 移除该 action 类型，服务端下发含 Copy 的卡整卡降级 plain。
 *
 * ## 状态归属
 *
 * `submittingIds` / `pendingTimeouts` 归 dispatcher 私有——它们是「Submit 生命
 * 周期」的一部分，跟 view 无关。View 视觉状态（`alpha` / overlay）通过
 * [SubmitUiListener] 反向通知 Provider 处理，dispatcher 不碰 view 树。
 *
 * ## 与 UI 的接口
 *
 * Provider 收到 [SubmitUiListener.onSubmitStart] / [SubmitUiListener.onSubmitEnd]
 * 后自己查表找对应 cardBox 并复用现有的 tag 判断（防 RecyclerView 复用误改）。
 *
 * ## 与 render 域的接口
 *
 * 当 Provider 检测到 bot 改卡新帧到达（cardJson 指纹变化）时调
 * [clearSubmitting] 手动清 submitting 态；dispatcher 不主动感知 render。
 */
class CardActionDispatcher(
    private val submitter: CardSubmitter,
    private val webView: WebViewLauncher,
    private val toaster: Toaster,
    private val timeoutScheduler: TimeoutScheduler,
    private val extraSync: ExtraMsgSyncer,
    private val selfUidProvider: () -> String?,
    private val uiListener: SubmitUiListener,
    private val strings: Strings,
    private val submitTimeoutMs: Long = 10_000L,
) {

    /** POST /v1/message/card/action 的抽象——真实实现走 WKBaseModel + Retrofit。 */
    fun interface CardSubmitter {
        fun submit(
            body: JSONObject,
            onSuccess: (JSONObject?) -> Unit,
            onFail: (code: Int, msg: String?) -> Unit,
        )
    }

    /** Action.OpenUrl 打开 App 内 WebView。返回 false = ActivityNotFound 之类兜底失败。 */
    fun interface WebViewLauncher {
        fun open(url: String): Boolean
    }

    fun interface Toaster {
        fun show(text: String)
    }

    /** postDelayed 抽象——真实实现走 `Handler(mainLooper)`；测试用 fake 手动 fire。 */
    interface TimeoutScheduler {
        fun interface Handle {
            fun cancel()
        }

        fun postDelayed(delayMs: Long, task: () -> Unit): Handle
    }

    fun interface ExtraMsgSyncer {
        fun sync(channelId: String, channelType: Byte)
    }

    /** Submit 生命周期钩子——Provider 收到后查 cardBox 表更新视觉。 */
    interface SubmitUiListener {
        fun onSubmitStart(messageId: String)
        fun onSubmitEnd(messageId: String)
    }

    /**
     * dispatcher 需要的本地化字符串——避免测试拉 Android Context。Provider 用真实
     * Resources 构造这个 holder 一次，传入。
     */
    data class Strings(
        val openUrlFailed: String,
        /** 409 / 5xx 可重试类错误 toast */
        val actionRetry: String,
        /** 400 / 403 / 其它 4xx 终态错误 toast */
        val actionFailed: String,
        /** 10s 超时 toast */
        val actionTimeout: String,
    )

    /** 提交中的 messageID 集合，防重复点击。 */
    private val submittingIds = mutableSetOf<String>()

    /** 各 messageID 的 10s 超时 handle，key = messageID。 */
    private val pendingTimeouts = mutableMapOf<String, TimeoutScheduler.Handle>()

    // ─────────────────────────────── Public API ───────────────────────────────

    fun dispatch(ctx: MessageContext, action: CardAction) {
        Log.d(TAG, "dispatch: id=${action.actionId} type=${action::class.simpleName} allowSubmit=${ctx.allowSubmit}")
        when (action) {
            is CardAction.OpenUrl -> handleOpenUrl(action)
            is CardAction.Submit -> {
                if (!ctx.allowSubmit) {
                    Log.d(TAG, "Submit 被 trust gate 阻拦（webhook 卡展示-only）")
                    return
                }
                handleSubmit(ctx, action)
            }
        }
    }

    /**
     * Bot 改卡新帧到达时调（cardJson 指纹变化 = 提交闭环完成）。清 submitting
     * 集合并取消 pending 超时；不动 UI（UI 由 [SubmitUiListener.onSubmitEnd]
     * 或下次 setData 的复位分支处理）。
     */
    fun clearSubmitting(messageId: String) {
        if (messageId.isBlank()) return
        submittingIds.remove(messageId)
        pendingTimeouts.remove(messageId)?.cancel()
    }

    /** 判断某 messageID 当前是否处于 submitting 中——Provider 在 bind 时用来复位视觉。 */
    fun isSubmitting(messageId: String): Boolean =
        messageId.isNotBlank() && messageId in submittingIds

    /** Activity/Fragment 销毁时清所有 pending 超时（当前 Provider 未 hook；预留接口）。 */
    fun onDestroy() {
        pendingTimeouts.values.forEach { it.cancel() }
        pendingTimeouts.clear()
        submittingIds.clear()
    }

    // ─────────────────────────────── OpenUrl ───────────────────────────────

    private fun handleOpenUrl(action: CardAction.OpenUrl) {
        // URL 合法性由 SDK adapter 层保证过一遍（isSafeUrl），这里再做防御性 blank 检查。
        if (action.url.isBlank()) {
            Log.w(TAG, "Action.OpenUrl url blank，忽略")
            return
        }
        val ok = webView.open(action.url)
        if (!ok) {
            Log.w(TAG, "无法打开 URL: ${action.url}")
            toaster.show(strings.openUrlFailed)
        }
    }

    // ─────────────────────────────── Submit ───────────────────────────────

    private fun handleSubmit(ctx: MessageContext, action: CardAction.Submit) {
        val actionId = action.actionId.takeIf { it.isNotBlank() } ?: run {
            Log.w(TAG, "Action.Submit 无 id，忽略")
            return
        }
        val messageId = ctx.wkMsg.messageID?.takeIf { it.isNotBlank() } ?: return

        // 防重复点击（幂等仍由服务端 D4 保证，这里只是 UX 层保护）。
        if (!submittingIds.add(messageId)) {
            Log.d(TAG, "$messageId 已在提交中，忽略重复点击")
            return
        }

        uiListener.onSubmitStart(messageId)
        armSubmitTimeout(messageId)

        val body = buildSubmitBody(ctx, actionId, action.inputs)
        Log.d(TAG, "Submit: msgId=$messageId actionId=$actionId inputs=${action.inputs.size}")

        submitter.submit(
            body,
            onSuccess = { result ->
                // HTTP 受理成功。照搬撤回同款模式：
                //  1. 立即清 submitting，UI 恢复交互（即便 bot 慢也不视觉卡死）
                //  2. 主动 syncExtraMsg（不等 CMD）
                //  3. 500/1500ms 兜底 retry（对齐撤回节奏，兜服务端 CMD/extra 写入时序竞态）
                Log.d(
                    TAG,
                    "Submit 成功: accepted=${result?.getBoolean("accepted")} " +
                        "replay=${result?.getBoolean("replay")}, 主动 sync 拉新帧"
                )
                clearSubmitting(messageId)
                uiListener.onSubmitEnd(messageId)
                val ch = ctx.wkMsg.channelID
                val ct = ctx.wkMsg.channelType
                if (!ch.isNullOrBlank()) {
                    extraSync.sync(ch, ct)
                    timeoutScheduler.postDelayed(500L) { extraSync.sync(ch, ct) }
                    timeoutScheduler.postDelayed(1500L) { extraSync.sync(ch, ct) }
                }
            },
            onFail = { code, msg ->
                clearSubmitting(messageId)
                Log.w(TAG, "Submit 失败: code=$code msg=$msg")
                val toast = if (code == 409 || code >= 500) strings.actionRetry else strings.actionFailed
                toaster.show(toast)
                uiListener.onSubmitEnd(messageId)
            },
        )
    }

    /**
     * PERSONAL DM 且 channelID == 自己 uid（系统 bot 塌缩场景）→ 回退到 fromUID。
     * 对齐 web `resolveCardActionChannelId`。group/topic 或普通 DM 原样保留。
     */
    private fun buildSubmitBody(
        ctx: MessageContext,
        actionId: String,
        inputs: Map<String, String>,
    ): JSONObject {
        val selfUid = selfUidProvider()
        val wkMsg = ctx.wkMsg
        val channelId = wkMsg.channelID
        val fixedChannelId =
            if (wkMsg.channelType == WKChannelType.PERSONAL &&
                !selfUid.isNullOrBlank() &&
                channelId == selfUid &&
                !wkMsg.fromUID.isNullOrBlank()
            ) wkMsg.fromUID else channelId

        return JSONObject().apply {
            put("message_id", wkMsg.messageID)
            put("channel_id", fixedChannelId)
            put("channel_type", wkMsg.channelType.toInt())
            put("action_id", actionId)
            put("inputs", JSONObject(inputs.toMap<String, Any>()))
            put("client_token", UUID.randomUUID().toString())
            // 刻意不传 data —— 服务端从存储帧提取（D11 防伪造）。
        }
    }

    private fun armSubmitTimeout(messageId: String) {
        // 先清掉上一次的（幂等）
        pendingTimeouts.remove(messageId)?.cancel()
        val handle = timeoutScheduler.postDelayed(submitTimeoutMs) {
            pendingTimeouts.remove(messageId)
            // 只有当仍在 submitting 时才动 UI（bot 已回帧的话 setData / clearSubmitting 已经清过）
            if (submittingIds.remove(messageId)) {
                Log.d(TAG, "Submit 10s 超时，恢复可点: $messageId")
                toaster.show(strings.actionTimeout)
                // 视图状态在下次 setData 会自动复位；本次超时不主动更新，避免访问陈旧引用。
            }
        }
        pendingTimeouts[messageId] = handle
    }

    private companion object {
        const val TAG = "InteractiveCard"
    }
}
