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
 * 抽出来，加一层按内容缓存的 [LruCache]，让 RecyclerView 滚动时**同 payload 卡片**能
 * 直接复用已有 View，避开 SWIG C++ 反射的开销。
 *
 * ## 三个设计要点
 *
 * ### 1. 缓存键是内容，不是消息 ID
 * `(cardJson.hashCode, cardVersion, isDark)` 三维穷举。同一张 bot 群发通知卡挂在 N 条
 * 消息上只渲染 1 次；bot 编辑帧到达时 hash 变 → 天然 miss → 重渲；系统切主题
 * Activity recreate 时 renderer 被 GC 缓存丢失。**无需手动 invalidate**。
 *
 * ### 2. Handler 分成静态 proxy + 动态 context
 * SDK render 时注入的 [ICardActionHandler] 会被 SDK 内部长期持有到 view tree 里。若
 * handler 捕获了当前 [MessageContext]，缓存 view 复用给别条消息时会导致 Submit 打到
 * 旧 messageId。
 *
 * 解决方案：[ProxyHandler] **不捕获 ctx**，每次 dispatch 从 `view.tag` 读动态 ctx。
 * [renderInto] 每次调用都会更新 tag。这样一份 view 可以服务 N 条消息，Submit 永远
 * 找到正确的当前消息。
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
 * 缓存后同 payload view 复用，SDK Input widget 的内部状态天然保留。
 *
 * @param dispatcher 复用 C1 抽出的业务分派器
 * @param stylize Provider 注入的 post-render 视觉改造（按钮胶囊、CompoundButton 间距），
 *   不下沉到 renderer 是因为它引用 Provider 层的 R.drawable/R.color，跟消息层更近
 * @param cacheSize LRU 上限；默认 32 项（估 200-500KB/项 → 最坏 6-16MB）
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

    /** LRU 缓存：key = 内容+主题三维穷举；value 包含 rendered.view 以及 SDK 原生 rendered
     *  对象（后者用于 SDK 内部 input 收集）。 */
    private val cache = LruCache<CacheKey, Entry>(cacheSize)

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
        val key = CacheKey(spec.cardJson.hashCode(), spec.cardVersion, isDark)

        val entry = cache.get(key) ?: run {
            val fresh = build(context, spec) ?: return@run null
            cache.put(key, fresh)
            fresh
        } ?: return Result.Fallback(
            reason = "sdk parse/render failed",
            cardJsonHash = spec.cardJson.hashCode(),
        )

        // 更新 view.tag 让 proxy handler 能读到本次 bind 的动态 ctx。
        // 用 id 资源做 key 而不是 hardcoded int，避免跟别处冲突。
        entry.view.setTag(R.id.card_message_context, ctx)

        // 复用前先脱离旧父容器 —— cache 命中时 view 上次是挂在别的 container 上的。
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

    // ─────────────────────────────── Internal build ───────────────────────────────

    private fun build(context: Context, spec: CardRenderSpec): Entry? {
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
            Entry(view = rendered.view, rendered = rendered)
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
     * 缓存键：内容 + 版本 + 主题。任一维变化都需重渲。
     *
     * cardJson 用 [hashCode] 而不是全字符串——降低 key 内存，冲突概率对 32 项 LRU 可
     * 忽略；即便偶发碰撞，结果是拿到另一张卡的 view，SDK 后续 dispatch 会因 view.tag
     * 里的 ctx 不匹配而降级，无数据一致性风险。
     */
    internal data class CacheKey(
        val cardJsonHash: Int,
        val cardVersion: String,
        val isDark: Boolean,
    )

    /** 缓存条目：view 是复用主体，rendered 保留是给 SDK 内部输入收集用。 */
    private data class Entry(
        val view: View,
        val rendered: RenderedAdaptiveCard,
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
                "Action.CopyToClipboard" -> {
                    // AC SDK 的 BaseActionElement 没有内建 text 字段，需要通过 AdditionalProperties 拿
                    // （对齐 SDK 自定义 action 的通用取值路径）。
                    // AC 3.7.0 Android SDK 默认不认 Action.CopyToClipboard，需要通过 CardActionParserRegistrar
                    // 注册（见 WKUIKitApplication.onCreate）后按钮才会出现，否则此分支永远不触发。
                    CardAction.Copy(actionId = actionId, text = extractCopyText(action))
                }
                // Action.ToggleVisibility 由 SDK 内部处理（自动翻转 isVisible），我们不介入。
                // Action.ShowCard / ExecuteAction 走 SDK 内部展开，此处不特殊处理。
                else -> null
            }
        }

        /** 从 BaseActionElement 的 AdditionalProperties 里取 text 字段。 */
        private fun extractCopyText(action: BaseActionElement): String {
            return try {
                val props = action.GetAdditionalProperties()?.toString().orEmpty()
                if (props.isBlank()) return ""
                org.json.JSONObject(props).optString("text", "")
            } catch (t: Throwable) {
                Log.w(TAG, "解析 CopyToClipboard.text 失败: ${t.message}")
                ""
            }
        }
    }
}
