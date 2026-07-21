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
import android.content.res.Configuration
import android.util.Log
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.chat.uikit.R
import com.chat.uikit.chat.msgmodel.InteractiveCardDecision
import com.chat.uikit.chat.msgmodel.InteractiveCardSanitizer
import com.chat.uikit.chat.msgmodel.OctoHostConfig
import io.adaptivecards.objectmodel.AdaptiveCard
import io.adaptivecards.objectmodel.BaseActionElement
import io.adaptivecards.objectmodel.BaseCardElement
import io.adaptivecards.objectmodel.OpenUrlAction
import io.adaptivecards.renderer.AdaptiveCardRenderer
import io.adaptivecards.renderer.RenderedAdaptiveCard
import io.adaptivecards.renderer.actionhandler.ICardActionHandler

/**
 * 交互卡的**渲染层**——把 SDK 渲染（sanitize + parse + render + stylize）从 Provider 里
 * 抽出来，加一层按 messageId 缓存的 [LruCache]，让 RecyclerView 滚动时**同一条消息**能
 * 直接复用已有 View，避开 SWIG C++ 反射的开销。
 *
 * ## 三个设计要点
 *
 * ### 1. 缓存键是 messageId，不是内容 hash
 * 每条消息拥有**独立的 rendered View 实例**。Entry 内额外保存
 * `(cardJsonHash, isDark)` 用于命中校验：命中要求 messageId 匹配**且** hash / 主题都
 * 未变；否则 rebuild。
 *
 * 之所以放弃"按内容 hash 缓存"是因为 View 是**单父节点**对象：当两条消息 payload 完全
 * 相同（bot 群发通知卡挂在多条消息上）且**同屏可见**时，后一条 bind 会把 View 从前一条
 * container 上摘走 → 前一条视觉空白。以 messageId 为键彻底消除这种"抢 view"竞争。
 *
 * bot 编辑帧（contentEdit）到达时 hash 变 → Entry 校验失败 → 天然 rebuild；系统切主题
 * Activity recreate 时 renderer 被 GC 缓存丢失。**无需手动 invalidate**。
 *
 * ### 2. Handler 分成静态 proxy + 动态 context
 * SDK render 时注入的 [ICardActionHandler] 会被 SDK 内部长期持有到 view tree 里。若
 * handler 捕获了当前 [MessageContext]，缓存 view 复用时会导致 Submit 打到旧 messageId。
 *
 * 解决方案：[ProxyHandler] **不捕获 ctx**，每次 dispatch 从 `view.tag` 读动态 ctx。
 * [renderInto] 每次调用都会更新 tag。这样 messageId 复用同一 view 时（同 msg 再次 bind）
 * ctx 保持最新；不同 msg 拿到各自的 view，也不会串。
 *
 * ### 3. View 复用生命周期
 * 缓存 view 复用前**必须先脱离旧父**，否则会崩：
 * ```kotlin
 * (cachedView.parent as? ViewGroup)?.removeView(cachedView)
 * container.addView(cachedView)
 * ```
 *
 * ## 顺带修复的 UX bug
 * 交互卡里用户填一半 Input，滚出屏再滚回来——之前每次 rebind 都是新 view，输入丢失。
 * 缓存后同一 messageId 的 view 复用，SDK Input widget 的内部状态天然保留。
 *
 * @param dispatcher 复用 C1 抽出的业务分派器
 * @param stylize Provider 注入的 post-render 视觉改造（按钮胶囊、CompoundButton 间距），
 *   不下沉到 renderer 是因为它引用 Provider 层的 R.drawable/R.color，跟消息层更近
 * @param cacheSize LRU 上限；默认 32 条消息（估 200-500KB/条 → 最坏 6-16MB）
 */
class InteractiveCardRenderer(
    private val dispatcher: CardActionDispatcher,
    private val stylize: (View) -> Unit,
    cacheSize: Int = DEFAULT_CACHE_SIZE,
) {

    /** 渲染结果：Success 已 attach 到 container；Fallback 由 Provider 显示 plain 兜底。 */
    sealed interface Result {
        object Success : Result

        /**
         * @param reason 具体错误（parse / render exception 的 message）
         * @param cardJsonHash 用于 Provider 侧的日志去重 —— 同一张挂卡在滚动时可能触发
         *   多次 fallback，Provider 用 hash 判断只打首次完整 payload
         */
        data class Fallback(val reason: String, val cardJsonHash: Int) : Result
    }

    /** LRU 缓存：key = messageId；Entry 内含 hash + isDark 用于命中校验（不匹配则 rebuild）。 */
    private val cache = LruCache<String, Entry>(cacheSize)

    /** 长寿命 proxy handler：SDK render 时绑一次，之后每张 view 复用都靠 tag 传 ctx。 */
    private val proxyHandler = ProxyHandler(dispatcher)

    // ─────────────────────────────── Public API ───────────────────────────────

    fun renderInto(
        container: ViewGroup,
        spec: CardRenderSpec,
        ctx: MessageContext,
        context: Context,
    ): Result {
        val isDark = isDarkMode(context)
        val hash = spec.cardJson.hashCode()
        val messageId = ctx.wkMsg.messageID.orEmpty()

        val entry = obtainEntry(messageId, hash, isDark, context, spec)
            ?: return Result.Fallback(
                reason = "sdk parse/render failed",
                cardJsonHash = hash,
            )

        // 更新 view.tag 让 proxy handler 能读到本次 bind 的动态 ctx。
        // 用 id 资源做 key 而不是 hardcoded int，避免跟别处冲突。
        entry.view.setTag(R.id.card_message_context, ctx)

        // 复用前先脱离旧父容器 —— cache 命中时 view 上次可能挂在别处（同 msg 复用 holder）。
        (entry.view.parent as? ViewGroup)?.removeView(entry.view)
        container.addView(entry.view)
        entry.view.layoutParams = entry.view.layoutParams?.apply {
            width = LayoutParams.MATCH_PARENT
        } ?: FrameLayout.LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
        )
        return Result.Success
    }

    /** Activity / Fragment onDestroy 时调；释放所有缓存 view（帮 GC 早些回收 SDK 视图）。 */
    fun clear() {
        cache.evictAll()
    }

    /** Provider 检测到某条消息不再需要（例如被删除）时调，主动释放缓存。可选。 */
    fun invalidate(messageId: String) {
        if (messageId.isBlank()) return
        cache.remove(messageId)
    }

    // ─────────────────────────────── Internal build ───────────────────────────────

    /**
     * 命中缓存则返回既有 Entry；未命中或校验失败（hash/isDark 变了）则重建。
     * messageId 为空（消息未 ack 回填）时**不进缓存**——每次 rebuild，避免拿空串当 key
     * 让多条待 ack 消息互相污染。
     */
    private fun obtainEntry(
        messageId: String,
        hash: Int,
        isDark: Boolean,
        context: Context,
        spec: CardRenderSpec,
    ): Entry? {
        if (messageId.isEmpty()) {
            return build(context, spec, hash, isDark)
        }
        val cached = cache.get(messageId)
        if (cached != null && cached.cardJsonHash == hash && cached.isDark == isDark) {
            return cached
        }
        val fresh = build(context, spec, hash, isDark) ?: return null
        cache.put(messageId, fresh)
        return fresh
    }

    private fun build(context: Context, spec: CardRenderSpec, hash: Int, isDark: Boolean): Entry? {
        return try {
            // 渲染前 sanitize —— 对齐 iOS `WKACardRenderer.wk_sanitizeNode`：
            //  · Input.Toggle 缺 title 补上（AC C++ ObjectModel schema 严格）
            //  · Input.ChoiceSet 一律 style=expanded（绕开 SDK compact 下拉的样式 & 生命周期问题）
            // Sanitizer 用 org.json（不是 fastjson），parse 前先转过来。
            val cardObj = org.json.JSONObject(spec.cardJson)
            val sanitized = InteractiveCardSanitizer.sanitize(cardObj)
            val cardJsonForSdk = sanitized?.toString() ?: spec.cardJson
            val parseResult = AdaptiveCard.DeserializeFromString(cardJsonForSdk, spec.cardVersion)
            val adaptiveCard = parseResult.GetAdaptiveCard()
            val fragmentManager = (context as? FragmentActivity)?.supportFragmentManager
                ?: throw IllegalStateException("context is not a FragmentActivity")
            val rendered = AdaptiveCardRenderer.getInstance().render(
                context,
                fragmentManager,
                adaptiveCard,
                proxyHandler,
                OctoHostConfig.get(context),
            )
            // post-render 视觉改造（按钮胶囊化等）—— 只在**首次构建**时执行一次，
            // 缓存 view 复用不再重复 walk 整棵树。这是缓存带来的额外性能收益。
            stylize(rendered.view)
            Entry(view = rendered.view, rendered = rendered, cardJsonHash = hash, isDark = isDark)
        } catch (t: Throwable) {
            Log.w(TAG, "SDK render 失败: ${t.message}", t)
            null
        }
    }

    private fun isDarkMode(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    // ─────────────────────────────── Types ───────────────────────────────

    /**
     * 缓存条目：view + rendered（SDK 内部输入收集用）+ 用于命中校验的 hash / isDark。
     *
     * cardJsonHash 用 [String.hashCode]（不是全 JSON 字符串）—— 降低占用；即便偶发碰撞，
     * 表现是拿到旧 view 但内容其实变了。为覆盖这种极小概率，Provider 侧的
     * `lastRenderedFingerprint` 与本 Entry 校验独立记录 hash 变化并触发新帧处理，双路
     * 兜底；此处即便未 invalidate，最坏结果是等下次 bind 才刷新，不影响正确性。
     */
    internal data class Entry(
        val view: View,
        val rendered: RenderedAdaptiveCard,
        val cardJsonHash: Int,
        val isDark: Boolean,
    )

    /**
     * SDK 回调 → 动态 ctx dispatch 的桥梁。**长寿命单实例**，不捕获任何 message
     * 特定状态；每次 dispatch 从当前触发 action 的 `rendered.view.tag` 读 ctx。
     */
    private class ProxyHandler(
        private val dispatcher: CardActionDispatcher,
    ) : ICardActionHandler {

        override fun onAction(
            actionElement: BaseActionElement?,
            renderedAdaptiveCard: RenderedAdaptiveCard?,
        ) {
            val view = renderedAdaptiveCard?.view ?: return
            val ctx = view.getTag(R.id.card_message_context) as? MessageContext ?: run {
                Log.w(TAG, "action 触发但 view.tag 里没有 MessageContext，忽略")
                return
            }
            val cardAction = adaptSdkAction(actionElement, renderedAdaptiveCard) ?: return
            Log.d(TAG, "dispatch: id=${cardAction.actionId} allowSubmit=${ctx.allowSubmit}")
            dispatcher.dispatch(ctx, cardAction)
        }

        override fun onMediaPlay(m: BaseCardElement?, r: RenderedAdaptiveCard?) {}
        override fun onMediaStop(m: BaseCardElement?, r: RenderedAdaptiveCard?) {}
    }

    private companion object {
        const val TAG = "InteractiveCard"
        const val DEFAULT_CACHE_SIZE = 32

        /**
         * SDK ↔ 业务边界：把 [BaseActionElement] 展平成纯 Kotlin [CardAction]。
         * SDK 类型（BaseActionElement / OpenUrlAction 等 SWIG binding）**只出现在这里**，
         * dispatcher 与其单测都不依赖 SDK 类型。
         */
        fun adaptSdkAction(
            action: BaseActionElement?,
            rendered: RenderedAdaptiveCard?,
        ): CardAction? {
            action ?: return null
            val type = action.GetElementTypeString().orEmpty()
            val actionId = action.GetId().orEmpty()
            return when (type) {
                "Action.OpenUrl" -> {
                    // OpenUrlAction.dynamic_cast 是 SDK SWIG 提供的向下转型入口。
                    val url = OpenUrlAction.dynamic_cast(action)?.GetUrl().orEmpty()
                    // 二次 isSafeUrl 校验（防 javascript:/file:/intent: 等）。validateOcto 已在 render 前
                    // 拒绝含非法 URL 的整卡，这里是最后一道防线（含未来某天放宽 profile 后的 hedge）。
                    if (!InteractiveCardDecision.isSafeUrl(url)) {
                        Log.w(TAG, "Action.OpenUrl url 不合法，忽略: $url")
                        return null
                    }
                    CardAction.OpenUrl(actionId = actionId, url = url)
                }
                "Action.Submit" -> {
                    // 收集用户输入。SDK 已在渲染层做过 required/validation，未通过时对应 key 缺失。
                    // 平台类型的 String? 值统一 coerce 成 ""，保持 CardAction.Submit 契约的非空 Map。
                    val inputs: Map<String, String> = rendered?.inputs
                        ?.mapValues { (_, v) -> v ?: "" }
                        ?: emptyMap()
                    CardAction.Submit(actionId = actionId, inputs = inputs)
                }
                // Action.ToggleVisibility 由 SDK 内部处理（自动翻转 isVisible），我们不介入。
                // Action.ShowCard / ExecuteAction 走 SDK 内部展开，此处不特殊处理。
                // Action.CopyToClipboard 未支持：AC 3.7.0 Android SDK 无内置 parser，
                // 白名单已在 InteractiveCardDecision 拒绝，含 Copy 的卡整卡降级 plain。
                else -> null
            }
        }
    }
}
