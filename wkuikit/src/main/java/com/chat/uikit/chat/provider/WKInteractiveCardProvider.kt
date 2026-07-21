/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.chat.uikit.chat.provider

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Button
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.alibaba.fastjson.JSONObject
import com.chat.base.WKBaseApplication
import com.chat.base.act.WKWebViewActivity
import com.chat.base.base.WKBaseModel
import com.chat.base.config.WKConfig
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.net.IRequestResultListener
import com.chat.base.utils.WKToastUtils
import com.chat.base.views.BubbleLayout
import com.chat.uikit.R
import com.chat.uikit.chat.msgmodel.CardSenderClassifier
import com.chat.uikit.chat.msgmodel.CardSenderTrust
import com.chat.uikit.chat.msgmodel.InteractiveCardActionService
import com.chat.uikit.chat.msgmodel.InteractiveCardDecision
import com.chat.uikit.chat.msgmodel.WKInteractiveCardContent
import com.chat.uikit.chat.provider.card.CardActionDispatcher
import com.chat.uikit.chat.provider.card.CardRenderSpec
import com.chat.uikit.chat.provider.card.InteractiveCardRenderer
import com.chat.uikit.chat.provider.card.MessageContext
import com.chat.uikit.message.MsgModel
import java.lang.ref.WeakReference

/**
 * 交互式卡片消息 Provider（ContentType 17）。
 *
 * **Option A：卡片即气泡**。BubbleLayout 骨架保留（承担长按/选中/reaction/时间戳），
 * 但视觉外壳淡化（透明底、无阴影、无尾巴）；真正可见的"气泡"由 layout 里的
 * `interactiveCardBox`（`shape_interactive_card_bg`：白底 + 8dp 圆角 + 1dp hairline）提供。
 *
 * 渲染细节：
 *  - **contentEdit 优先**：bot 通过 `/v1/bot/message/edit` 改卡后，新帧存
 *    `WKMsg.remoteExtra.contentEditMsgModel`（对齐 web `remoteExtra.contentEdit`）。
 *    渲染时择优取编辑帧，否则用原始 payload。这是 bot 首帧"推理中"+ 次帧"已 plan..."正文段
 *    合并到一张卡的关键 —— 只读原始 payload 会看不到 bot 追加内容。
 *  - 用 [OctoHostConfig]（对齐 web `octoHostConfig.ts`）替代 SDK 默认 HostConfig；
 *  - 渲染失败（JSON 非法 / SDK 抛异常 / native lib 未加载）时兜底展示 [WKInteractiveCardContent.plain]。
 *
 * 交互闭环（对齐 web `InteractiveCardCell.handleCardAction`）：
 *  - **Action.Submit**：收集 [RenderedAdaptiveCard.getInputs] → POST `/v1/message/card/action`
 *    （no-data，D11 契约）→ 受理成功后等 bot 改卡新帧（走 CMDSyncMessageExtra → contentEditMsgModel 重渲）。
 *  - **Action.OpenUrl**：拉起 App 内 [WKWebViewActivity]（与 markdown 链接 / URL 预览卡 /
 *    @文本链接一致），不跳系统浏览器 —— 保留登录态和返回栈，用户回到会话不用重新打开。
 *  - **Action.ToggleVisibility**：SDK 内部处理（展开/收起推理），Provider 不介入。
 *
 * 后续 M1/M2 迭代补齐：白名单裁剪、profile 协商、senderTrust gate、submitting loading 覆盖、
 * positive/destructive 主/次按钮样式等。
 */
class WKInteractiveCardProvider : WKChatBaseProvider() {

    /**
     * 交互卡的高度会随 bot 编辑帧（`remoteExtra.contentEdit`）变化——首帧可能只有
     * "推理中"一小段，次帧到达后长出多个 Container / Input / 按钮。基类默认走
     * [com.chat.base.msg.ChatAdapter.notifyData] 的 in-place 更新在这类场景下会
     * 让 RecyclerView 沿用旧高度定位相邻 item → 相邻消息重叠错位。
     *
     * 覆盖为 true 后，`notifyData` 会自动路由到 `notifyItemChanged`，RecyclerView
     * 会重新测量本 item 并 reflow 相邻 item。
     */
    override val hasDynamicHeight: Boolean = true

    /** 上次渲染该 messageID 时的 cardJson 指纹，用于识别"bot 改卡新帧"→ 自动解除 loading 态。 */
    private val lastRenderedFingerprint = mutableMapOf<String, Int>()

    /** 已打印过 payload 的解析失败签名（cardJson.hashCode），一张挂卡只打一次完整 payload。 */
    private val parseFailLoggedSigs = mutableSetOf<Int>()

    /**
     * messageID → 当前绑定的 cardBox 弱引用。dispatcher 通过 [SubmitUiListener] 回调
     * 通知需要更新哪条消息的视觉时，Provider 从这里查表拿到 view。用弱引用避免 view
     * 被 RecyclerView 回收后阻碍 GC；查表拿到 view 后再对 [View.getTag] 做 messageID
     * 比对，防止 view 已被复用给别条消息导致误改。
     */
    private val cardBoxByMsgId = mutableMapOf<String, WeakReference<FrameLayout>>()

    /** 业务分派器——所有副作用（HTTP / WebView / clipboard / toast / timeout）由 Provider 注入。 */
    private val dispatcher: CardActionDispatcher by lazy { createDispatcher() }

    /** 按内容缓存 rendered view 的渲染层，避开滚动时反复走 SWIG 反射。 */
    private val renderer: InteractiveCardRenderer by lazy {
        InteractiveCardRenderer(
            dispatcher = dispatcher,
            stylize = ::stylizeInteractiveElements,
        )
    }

    private fun createDispatcher(): CardActionDispatcher {
        val appCtx: Context = WKBaseApplication.getInstance().context
        val submitService = object : WKBaseModel() {
            fun get(): InteractiveCardActionService = createService(InteractiveCardActionService::class.java)
        }.get()
        return CardActionDispatcher(
            submitter = CardActionDispatcher.CardSubmitter { body, success, failure ->
                // 借用一次 WKBaseModel.request 的 io/main 线程调度 + BaseObserver 错误分发。
                // 参数名 success/failure 避免与 IRequestResultListener.onSuccess/onFail 覆写方法同名
                // 导致的意外自调（Kotlin 里 override fun onSuccess(r) = onSuccess(r) 会递归）。
                object : WKBaseModel() {
                    fun run() {
                        request(
                            submitService.submitCardAction(body),
                            object : IRequestResultListener<JSONObject> {
                                override fun onSuccess(result: JSONObject?) = success(result)
                                override fun onFail(code: Int, msg: String?) = failure(code, msg)
                            }
                        )
                    }
                }.run()
            },
            webView = CardActionDispatcher.WebViewLauncher { url ->
                // 走 App 内 WebView（对齐 WKTextProvider 链接预览卡、WKMarkwonProvider linkResolver
                // 的处理路径）。FLAG_ACTIVITY_NEW_TASK：context 可能是 application context（Provider 由
                // adapter 持有，而 adapter 可能在非 Activity context 下 bind），保守带 flag 兜底。
                try {
                    val intent = Intent(context, WKWebViewActivity::class.java).apply {
                        putExtra("url", url)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                } catch (e: ActivityNotFoundException) {
                    Log.w(TAG, "无法打开 URL: $url", e)
                    false
                }
            },
            clipboard = CardActionDispatcher.ClipboardWriter { label, text ->
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText(label, text))
            },
            toaster = CardActionDispatcher.Toaster { text ->
                WKToastUtils.getInstance().showToastNormal(text)
            },
            timeoutScheduler = object : CardActionDispatcher.TimeoutScheduler {
                private val handler = Handler(Looper.getMainLooper())
                override fun postDelayed(
                    delayMs: Long,
                    task: () -> Unit,
                ): CardActionDispatcher.TimeoutScheduler.Handle {
                    val runnable = Runnable { task() }
                    handler.postDelayed(runnable, delayMs)
                    return CardActionDispatcher.TimeoutScheduler.Handle {
                        handler.removeCallbacks(runnable)
                    }
                }
            },
            extraSync = CardActionDispatcher.ExtraMsgSyncer { ch, ct ->
                MsgModel.getInstance().syncExtraMsg(ch, ct)
            },
            selfUidProvider = { WKConfig.getInstance().uid },
            uiListener = object : CardActionDispatcher.SubmitUiListener {
                override fun onSubmitStart(messageId: String) = applySubmittingUiFor(messageId)
                override fun onSubmitEnd(messageId: String) = restoreCardBoxFor(messageId)
            },
            strings = CardActionDispatcher.Strings(
                openUrlFailed = appCtx.getString(R.string.base_open_url_failed),
                copySuccess = appCtx.getString(R.string.base_card_copy_success),
                actionRetry = appCtx.getString(R.string.base_card_action_retry),
                actionFailed = appCtx.getString(R.string.base_card_action_failed),
                actionTimeout = appCtx.getString(R.string.base_card_action_timeout),
            ),
        )
    }

    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? =
        LayoutInflater.from(context).inflate(R.layout.chat_item_interactive_card, parentView, false)

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        val cardView = parentView.findViewById<View>(R.id.cardView)
        cardView.layoutParams.width = getViewWidth(from, uiChatMsgItemEntity)

        val cardBox = parentView.findViewById<FrameLayout>(R.id.interactiveCardBox)
        val container = parentView.findViewById<FrameLayout>(R.id.interactiveCardContainer)
        val fallback = parentView.findViewById<TextView>(R.id.interactiveCardFallbackTv)
        val submitOverlay = parentView.findViewById<View>(R.id.interactiveCardSubmitOverlay)
        container.removeAllViews()
        // 每次 bind 先复位视觉态（防止 ViewHolder 复用把上条消息的置灰态带过来），
        // 若当前消息仍在 submitting，下面识别到指纹匹配会重新把置灰加回来。
        cardBox.alpha = 1f
        submitOverlay.visibility = View.GONE

        // contentEdit 优先：bot 改卡后新帧优先展示（对齐 web resolveEffectiveCardContent）。
        val wkMsg = uiChatMsgItemEntity.wkMsg
        val editedContent = wkMsg.remoteExtra?.contentEditMsgModel as? WKInteractiveCardContent
        val model = editedContent ?: (wkMsg.baseContentMsgModel as? WKInteractiveCardContent)
        val cardJson = model?.cardJson?.toString()
        val plain = model?.plain.orEmpty().ifBlank { context.getString(R.string.base_unknow_msg) }
        val messageId = wkMsg.messageID.orEmpty()

        // 用 tag 记住当前 box 绑定的 messageID —— UI 恢复回调靠这个判断视图有没有被
        // RecyclerView 回收给别的消息，避免误改错卡片的态。
        cardBox.tag = messageId
        // 记入 messageID → cardBox 弱引用表，dispatcher 通过 SubmitUiListener 回调
        // 时按 messageID 反查（比原来沿父链 walkUp 更稳，也为 C3 缓存 view 场景铺路）。
        if (messageId.isNotEmpty()) {
            cardBoxByMsgId[messageId] = WeakReference(cardBox)
        }

        // 识别"bot 改卡新帧到达" —— cardJson 指纹变化即视为新帧，自动解除 submitting 态。
        // 契约对齐 web：新帧到达 = 提交闭环完成，无需保留 loading 遮罩。
        if (messageId.isNotEmpty()) {
            val newFp = cardJson?.hashCode() ?: 0
            val oldFp = lastRenderedFingerprint[messageId]
            if (oldFp != null && oldFp != newFp) {
                dispatcher.clearSubmitting(messageId)
            }
            lastRenderedFingerprint[messageId] = newFp
        }

        if (model == null || cardJson.isNullOrBlank()) {
            showFallback(container, fallback, plain)
            resetCellBackground(parentView, uiChatMsgItemEntity, from)
            return
        }

        // ── 三段决策（对齐 web decideCardBody）：sender trust → profile/version 协商 → octo 白名单 ──
        // 任一未通过整卡降级为 plain（fail-closed），避免恶意 direct-socket 塞的 type=17 骗过 UI。
        val trust = CardSenderClassifier.classify(wkMsg.fromUID)
        if (trust == CardSenderTrust.PENDING) {
            // channelInfo 未命中：主动拉取，等 SDK 广播回来后 RecyclerView 会重新 bind → 重新决策。
            CardSenderClassifier.fetchSenderChannelInfo(wkMsg.fromUID.orEmpty())
        }
        val cardObj = model.cardJson
        val decision = InteractiveCardDecision.decide(trust, model.profile, model.cardVersion, cardObj)
        if (decision is InteractiveCardDecision.Decision.Plain) {
            Log.d(TAG, "trust=$trust profile=${model.profile} → 整卡降级 plain")
            showFallback(container, fallback, plain)
            resetCellBackground(parentView, uiChatMsgItemEntity, from)
            return
        }
        if (decision is InteractiveCardDecision.Decision.Hint) {
            Log.d(TAG, "profile/version 不支持 → plain + 更新提示")
            val hint = context.getString(R.string.base_card_need_update)
            showFallback(container, fallback, "$plain\n\n$hint")
            resetCellBackground(parentView, uiChatMsgItemEntity, from)
            return
        }
        val cardDecision = decision as InteractiveCardDecision.Decision.Card

        // 渲染委托给 InteractiveCardRenderer：内部按 (cardJsonHash, cardVersion, isDark)
        // 缓存 rendered view，滚动/多消息同 payload 时复用避开 SDK 重渲。sanitize / parse /
        // SDK render / stylize 全部封装在 renderer 里，Provider 只关心业务态和视觉复位。
        val spec = CardRenderSpec(cardJson = cardJson, cardVersion = model.cardVersion)
        val ctx = MessageContext(wkMsg = wkMsg, allowSubmit = cardDecision.interactive)
        when (val r = renderer.renderInto(container, spec, ctx, context)) {
            is InteractiveCardRenderer.Result.Success -> {
                container.visibility = View.VISIBLE
                fallback.visibility = View.GONE
            }
            is InteractiveCardRenderer.Result.Fallback -> {
                // 解析失败按 cardJson.hashCode 去重打印，避免同一张挂卡在 RecyclerView 复用 /
                // 滚动时刷屏。sig 稳定 → 全 App 生命周期只打一次完整 payload。
                if (parseFailLoggedSigs.add(r.cardJsonHash)) {
                    Log.w(TAG, "AdaptiveCard 渲染失败(首打)：${r.reason} payload=$cardJson")
                } else {
                    Log.d(TAG, "AdaptiveCard 渲染失败(已记录 sig=${r.cardJsonHash})：${r.reason}")
                }
                showFallback(container, fallback, plain)
            }
        }

        // 若消息仍在 submitting（view 被回收后重新 bind 回同一条消息），把 loading 遮罩加回。
        if (dispatcher.isSubmitting(messageId)) {
            applySubmittingUI(cardBox, submitOverlay)
        }

        resetCellBackground(parentView, uiChatMsgItemEntity, from)
    }

    private fun showFallback(container: FrameLayout, fallback: TextView, plain: String) {
        container.removeAllViews()
        container.visibility = View.GONE
        fallback.text = plain
        fallback.visibility = View.VISIBLE
    }

    override val itemViewType: Int
        get() = WKContentType.interactiveCard

    override fun resetCellBackground(
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        // Option A：不走 super 的 setAll(bgType, ...) 灰底气泡逻辑，改成透明外壳。
        // 视觉气泡由 interactiveCardBox（shape_interactive_card_bg）提供；
        // BubbleLayout 只保留长按/选中/reaction 挂点，不参与绘制。
        super.resetCellBackground(parentView, uiChatMsgItemEntity, from)
        parentView.findViewById<BubbleLayout>(R.id.contentLayout)?.apply {
            setBubbleNormalColor(R.color.transparent)
            setBubbleSelectedColor(R.color.transparent)
        }
    }

    override fun resetCellListener(
        position: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellListener(position, parentView, uiChatMsgItemEntity, from)
        parentView.findViewById<BubbleLayout>(R.id.contentLayout)?.let {
            addLongClick(it, uiChatMsgItemEntity)
        }
    }

    /**
     * 后置改造 AC SDK 渲染出的交互控件，让窄气泡内视觉协调：
     *
     * 1. **Action 按钮**（Submit/OpenUrl/ToggleVisibility/ShowCard 都是 android.widget.Button）：
     *    Material 默认 minWidth=88dp、minHeight=48dp、有阴影，桌面级尺寸。改成胶囊：
     *    min-height 28dp、去 minWidth、textSize 13sp、wrap_content 宽、无阴影、accent 文字色。
     * 2. **ChoiceSet RadioButton/CheckBox**：SDK 默认 0px 垂直间距，多选项贴一起像"连体胶囊"，
     *    每个加 4dp bottom margin 分开。
     *
     * 不注册自定义 ActionElementRenderer 的原因：Submit / OpenUrl / ToggleVisibility / ShowCard
     * 有各自的默认 renderer 分支，逐一覆盖工作量大且易漏。post-walk 一次覆盖全部类型，
     * 副作用可控（rendered.view 每次 setData 都是新对象，不需要撤销）。
     */
    private fun stylizeInteractiveElements(root: View) {
        val ctx = context ?: return
        val accent = ContextCompat.getColor(ctx, com.chat.base.R.color.colorAccent)
        val btnPadH = dp(12f)
        val btnPadV = dp(4f)
        val btnMinH = dp(28f)
        val choiceGap = dp(4f)
        walkTree(root) { v ->
            when {
                // RadioButton / CheckBox 归 CompoundButton，先判 Button 会被覆盖（Button 是 TextView 子类，
                // 但 RadioButton 不是 Button）。CompoundButton 优先判定，避免落到 Button 分支。
                v is CompoundButton -> {
                    val lp = v.layoutParams
                    if (lp is MarginLayoutParams) {
                        lp.bottomMargin = choiceGap
                        v.layoutParams = lp
                    }
                }
                v is Button -> {
                    v.minWidth = 0
                    v.minimumWidth = 0
                    v.minHeight = btnMinH
                    v.minimumHeight = btnMinH
                    v.setPadding(btnPadH, btnPadV, btnPadH, btnPadV)
                    v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    v.setTextColor(accent)
                    v.setAllCaps(false)
                    v.setBackgroundResource(R.drawable.shape_interactive_card_button)
                    v.elevation = 0f
                    v.stateListAnimator = null
                    v.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                    v.layoutParams?.apply {
                        height = LayoutParams.WRAP_CONTENT
                        width = LayoutParams.WRAP_CONTENT
                    }
                }
            }
        }
    }

    private fun walkTree(view: View, visit: (View) -> Unit) {
        visit(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) walkTree(view.getChildAt(i), visit)
        }
    }

    private fun dp(value: Float): Int {
        val density = context.resources.displayMetrics.density
        return (value * density + 0.5f).toInt()
    }

    /**
     * SDK ↔ dispatcher 边界适配器已经下沉到
     * [com.chat.uikit.chat.provider.card.InteractiveCardRenderer]（作为 ProxyHandler +
     * adaptSdkAction 静态方法），此处不再持有 SDK 类型。
     */

    /** 应用 submitting UI：cardBox alpha=0.6 + overlay 拦截触摸。 */
    private fun applySubmittingUI(cardBox: View, overlay: View) {
        cardBox.alpha = 0.6f
        overlay.visibility = View.VISIBLE
    }

    /**
     * 通过 messageID 反查 cardBox 并应用 submitting UI。dispatcher 通过
     * [CardActionDispatcher.SubmitUiListener.onSubmitStart] 回调进来。
     */
    private fun applySubmittingUiFor(messageId: String) {
        if (messageId.isEmpty()) return
        val box = cardBoxByMsgId[messageId]?.get() ?: return
        // tag 比对防误改：view 可能已被 RecyclerView 复用给别条消息。
        if (box.tag != messageId) return
        val overlay = box.findViewById<View>(R.id.interactiveCardSubmitOverlay) ?: return
        applySubmittingUI(box, overlay)
    }

    /**
     * 通过 messageID 反查 cardBox 并恢复视觉态。dispatcher 通过
     * [CardActionDispatcher.SubmitUiListener.onSubmitEnd] 回调进来。
     */
    private fun restoreCardBoxFor(messageId: String) {
        if (messageId.isEmpty()) return
        val box = cardBoxByMsgId[messageId]?.get() ?: return
        if (box.tag != messageId) return
        val overlay = box.findViewById<View>(R.id.interactiveCardSubmitOverlay) ?: return
        box.alpha = 1f
        overlay.visibility = View.GONE
    }

    private companion object {
        const val TAG = "InteractiveCard"
    }
}
