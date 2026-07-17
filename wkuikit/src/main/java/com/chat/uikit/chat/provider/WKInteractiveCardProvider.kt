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
import android.content.Intent
import android.graphics.Color
import android.net.Uri
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
import androidx.fragment.app.FragmentActivity
import com.alibaba.fastjson.JSONObject
import com.chat.base.WKBaseApplication
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
import com.chat.uikit.chat.msgmodel.OctoHostConfig
import com.chat.uikit.chat.msgmodel.WKInteractiveCardContent
import com.xinbida.wukongim.entity.WKChannelType
import com.xinbida.wukongim.entity.WKMsg
import io.adaptivecards.objectmodel.AdaptiveCard
import io.adaptivecards.objectmodel.BaseActionElement
import io.adaptivecards.objectmodel.BaseCardElement
import io.adaptivecards.objectmodel.OpenUrlAction
import io.adaptivecards.renderer.AdaptiveCardRenderer
import io.adaptivecards.renderer.RenderedAdaptiveCard
import io.adaptivecards.renderer.actionhandler.ICardActionHandler
import java.util.UUID

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
 *  - **Action.OpenUrl**：`Intent.ACTION_VIEW` 打开系统浏览器。
 *  - **Action.ToggleVisibility**：SDK 内部处理（展开/收起推理），Provider 不介入。
 *
 * 后续 M1/M2 迭代补齐：白名单裁剪、profile 协商、senderTrust gate、submitting loading 覆盖、
 * positive/destructive 主/次按钮样式等。
 */
class WKInteractiveCardProvider : WKChatBaseProvider() {

    /** 提交中的 messageID 集合，防重复点击（幂等仍由服务端保证）。 */
    private val submittingIds = mutableSetOf<String>()

    /** 各 messageID 的 10s 超时 Runnable，key = messageID。 */
    private val pendingTimeouts = mutableMapOf<String, Runnable>()

    /** 上次渲染该 messageID 时的 cardJson 指纹，用于识别"bot 改卡新帧"→ 自动解除 loading 态。 */
    private val lastRenderedFingerprint = mutableMapOf<String, Int>()

    /** 主线程 Handler，用于 postDelayed 超时。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 提交超时（对齐 web SUBMIT_TIMEOUT_MS = 10000ms）：bot 迟迟不改卡则恢复可点 + 提示。 */
    private val submitTimeoutMs: Long = 10_000L

    /** Retrofit 服务懒加载。 */
    private val cardActionService by lazy {
        object : WKBaseModel() {
            fun get() = createService(InteractiveCardActionService::class.java)
        }.get()
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

        // 用 tag 记住当前 box 绑定的 messageID —— 10s 超时 Runnable 靠这个判断
        // 视图有没有被 RecyclerView 回收给别的消息，避免误改错卡片的态。
        cardBox.tag = messageId

        // 识别"bot 改卡新帧到达" —— cardJson 指纹变化即视为新帧，自动解除 submitting 态。
        // 契约对齐 web：新帧到达 = 提交闭环完成，无需保留 loading 遮罩。
        if (messageId.isNotEmpty()) {
            val newFp = cardJson?.hashCode() ?: 0
            val oldFp = lastRenderedFingerprint[messageId]
            if (oldFp != null && oldFp != newFp) {
                clearSubmittingState(messageId)
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

        try {
            val parseResult = AdaptiveCard.DeserializeFromString(cardJson, model.cardVersion)
            val adaptiveCard = parseResult.GetAdaptiveCard()
            val fragmentManager = (context as? FragmentActivity)?.supportFragmentManager
                ?: throw IllegalStateException("context is not a FragmentActivity")
            val rendered: RenderedAdaptiveCard = AdaptiveCardRenderer.getInstance().render(
                context,
                fragmentManager,
                adaptiveCard,
                CardActionHandler(wkMsg, allowSubmit = cardDecision.interactive),
                OctoHostConfig.get()
            )
            // SDK 输出的 rendered.view 是 wrap_content 宽度（表现为不铺满盒子，正文被挤换行）。
            // 对齐 web `.wk-interactive-card-sdk > .ac-adaptiveCard { width: 100% }`：强制 match_parent。
            container.addView(rendered.view)
            rendered.view.layoutParams = rendered.view.layoutParams?.apply {
                width = LayoutParams.MATCH_PARENT
            } ?: FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            // SDK 用 android.widget.Button（Material 默认 minWidth=88dp、minHeight=48dp、有阴影），
            // 在窄气泡里的表现是：按钮把 ColumnSet 的 auto 列撑到 264px，反过来把左侧正文列压到只剩
            // 170px 换行严重。移动端也不需要桌面级点击目标（44dp 也够）。post-walk 后置改造，让所有
            // Action 按钮变成"胶囊"样式：min-height 28dp、去 minWidth、textSize 13sp、无阴影、
            // 胶囊 drawable、accent 文字色。对齐 web `.ac-pushButton` 视觉。
            // 同时：ChoiceSet 的 RadioButton 默认 0px 间距，4 个选项贴在一起像"连体胶囊"，
            // 需要给每个 CompoundButton 加 4dp bottom margin 分开。
            stylizeInteractiveElements(rendered.view)
            container.visibility = View.VISIBLE
            fallback.visibility = View.GONE
        } catch (t: Throwable) {
            Log.w(TAG, "AdaptiveCard 渲染失败，降级到 plain: ${t.message}", t)
            showFallback(container, fallback, plain)
        }

        // 若消息仍在 submitting（view 被回收后重新 bind 回同一条消息），把 loading 遮罩加回。
        if (messageId.isNotEmpty() && messageId in submittingIds) {
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
     * SDK action 回调。绑定当前 [wkMsg] 以便 Submit 时提取 messageID/channel/fromUID。
     * [allowSubmit] 只在 sender trust=bot 且 profile=octo/v2 时为 true；webhook 卡展示-only。
     * 每次 [setData] 都是新实例（rendered.view 也是新的），无生命周期泄漏问题。
     */
    private inner class CardActionHandler(
        private val wkMsg: WKMsg,
        private val allowSubmit: Boolean
    ) : ICardActionHandler {

        override fun onAction(
            actionElement: BaseActionElement?,
            renderedAdaptiveCard: RenderedAdaptiveCard?
        ) {
            val action = actionElement ?: return
            val type = action.GetElementTypeString().orEmpty()
            Log.d(TAG, "onAction: id=${action.GetId()} type=$type allowSubmit=$allowSubmit")
            when (type) {
                "Action.OpenUrl" -> handleOpenUrl(action)
                "Action.Submit" -> {
                    if (!allowSubmit) {
                        Log.d(TAG, "Submit 被 trust gate 阻拦（webhook 卡展示-only）")
                        return
                    }
                    handleSubmit(action, renderedAdaptiveCard)
                }
                "Action.CopyToClipboard" -> handleCopyToClipboard(action)
                // Action.ToggleVisibility 由 SDK 内部处理（自动翻转 isVisible），Provider 不介入。
                // Action.ShowCard / ExecuteAction 走同样的 Submit 分支或 SDK 内部展开，此处不特殊处理。
            }
        }

        override fun onMediaPlay(m: BaseCardElement?, r: RenderedAdaptiveCard?) {}
        override fun onMediaStop(m: BaseCardElement?, r: RenderedAdaptiveCard?) {}

        private fun handleOpenUrl(action: BaseActionElement) {
            // OpenUrlAction.dynamic_cast 是 SDK SWIG 提供的向下转型入口。
            val url = OpenUrlAction.dynamic_cast(action)?.GetUrl().orEmpty()
            // 二次 isSafeUrl 校验（防 javascript:/file:/intent: 等）。validateOcto 已在 render 前
            // 拒绝含非法 URL 的整卡，这里是最后一道防线（含未来某天放宽 profile 后的 hedge）。
            if (!InteractiveCardDecision.isSafeUrl(url)) {
                Log.w(TAG, "Action.OpenUrl url 不合法，忽略: $url")
                return
            }
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "无法打开 URL: $url", e)
                WKToastUtils.getInstance().showToastNormal(
                    WKBaseApplication.getInstance().context.getString(R.string.base_open_url_failed)
                )
            }
        }

        /**
         * Action.CopyToClipboard 处理：把 action.text 复制到剪贴板 + 弹 toast。
         * 对齐 web `CopyToClipboardAction` 自定义 action。
         *
         * 注意：AC 3.7.0 Android SDK 默认不认 Action.CopyToClipboard，需要注册自定义 parser 才会
         * 渲染出按钮。当前实现假定服务端确实下发这类 action、且 SDK 已通过 [CardActionParserRegistrar]
         * 注册（见 [WKUIKitApplication.onCreate]）。若 SDK 未注册，此分支永远不会触发（按钮不会出现），
         * 也不影响其它 action 正常工作。
         */
        private fun handleCopyToClipboard(action: BaseActionElement) {
            // AC SDK 的 BaseActionElement 没有内建 text 字段，需要通过 AdditionalProperties 拿
            // （对齐 SDK 自定义 action 的通用取值路径）。
            val text = extractCopyText(action)
            if (text.isEmpty()) return
            try {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(
                    android.content.ClipData.newPlainText("interactive-card-copy", text)
                )
                WKToastUtils.getInstance().showToastNormal(
                    WKBaseApplication.getInstance().context.getString(R.string.base_card_copy_success)
                )
            } catch (t: Throwable) {
                Log.w(TAG, "CopyToClipboard 失败: ${t.message}", t)
            }
        }

        /** 从 BaseActionElement 的 AdditionalProperties 里取 text 字段（自定义 action 的通用取值路径）。 */
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

        private fun handleSubmit(action: BaseActionElement, rendered: RenderedAdaptiveCard?) {
            val actionId = action.GetId()?.takeIf { it.isNotBlank() } ?: run {
                Log.w(TAG, "Action.Submit 无 id，忽略")
                return
            }
            val messageId = wkMsg.messageID?.takeIf { it.isNotBlank() } ?: return
            // 防重复点击（幂等仍由服务端 D4 保证，这里只是 UX 层保护）。
            if (!submittingIds.add(messageId)) {
                Log.d(TAG, "$messageId 已在提交中，忽略重复点击")
                return
            }

            // 应用 loading UX：cardBox alpha=0.6 + overlay 拦截触摸，防止用户重复点或误点。
            // 视图引用可能会被 RecyclerView 回收，所以从 renderedCard.view 反推最近的 cardBox 父容器。
            val cardBox = findCardBoxFromRenderedView(rendered)
            val overlay = cardBox?.findViewById<View>(R.id.interactiveCardSubmitOverlay)
            if (cardBox != null && overlay != null) {
                applySubmittingUI(cardBox, overlay)
                armSubmitTimeout(messageId)
            }

            // 收集用户输入（SDK 已做输入验证，未通过时 map 可能为 null）。
            val inputs = rendered?.inputs ?: emptyMap<String, String>()

            // person DM 且 channelID == 自己 uid（系统 bot 塌缩场景）→ 回退到 fromUID。
            // 对齐 web resolveCardActionChannelId。group/topic 或普通 DM 原样保留。
            val selfUid = WKConfig.getInstance().uid
            val channelId = wkMsg.channelID
            val fixedChannelId =
                if (wkMsg.channelType == WKChannelType.PERSONAL &&
                    !selfUid.isNullOrBlank() &&
                    channelId == selfUid &&
                    !wkMsg.fromUID.isNullOrBlank()
                ) wkMsg.fromUID else channelId

            val body = JSONObject().apply {
                put("message_id", messageId)
                put("channel_id", fixedChannelId)
                put("channel_type", wkMsg.channelType.toInt())
                put("action_id", actionId)
                put("inputs", JSONObject(inputs.toMap<String, String>()))
                put("client_token", UUID.randomUUID().toString())
                // 刻意不传 data —— 服务端从存储帧提取（D11 防伪造）。
            }

            Log.d(TAG, "Submit: msgId=$messageId actionId=$actionId inputs=${inputs.size} chan=$fixedChannelId")

            // 走 WKBaseModel.request 的 io/main 线程调度 + BaseObserver 错误分发。
            object : WKBaseModel() {
                fun submit() {
                    request(
                        cardActionService.submitCardAction(body),
                        object : IRequestResultListener<JSONObject> {
                            override fun onSuccess(result: JSONObject?) {
                                // HTTP 受理成功：不清 submitting——等 bot 改卡新帧到达时 setData 内自动清。
                                // 若 bot 迟迟不响应，10s 超时会兜底恢复可点。
                                Log.d(TAG, "Submit 成功: accepted=${result?.getBoolean("accepted")} replay=${result?.getBoolean("replay")}")
                            }

                            override fun onFail(code: Int, msg: String?) {
                                // HTTP 失败：立即清 submitting + 恢复 UI + toast。
                                clearSubmittingState(messageId)
                                Log.w(TAG, "Submit 失败: code=$code msg=$msg")
                                val toast = when {
                                    code == 409 || code >= 500 ->
                                        WKBaseApplication.getInstance().context.getString(R.string.base_card_action_retry)
                                    else ->
                                        WKBaseApplication.getInstance().context.getString(R.string.base_card_action_failed)
                                }
                                WKToastUtils.getInstance().showToastNormal(toast)
                                // 触发一次 UI 刷新以恢复 alpha（走 tag 判断避免影响到其他消息）。
                                if (cardBox != null && overlay != null) {
                                    restoreCardBoxByMessageId(cardBox, overlay, messageId)
                                }
                            }
                        }
                    )
                }
            }.submit()
        }
    }

    /**
     * 找到 SDK 渲染 view 所在的 [R.id.interactiveCardBox]（Provider layout 里的白底卡片容器）。
     * SDK 的 rendered.view 会 addView 进 `interactiveCardContainer`，其父即 `interactiveCardBox`。
     * 用 view.rootView.findViewById 有风险（可能拿到别条消息的 box），故沿父链回溯。
     */
    private fun findCardBoxFromRenderedView(rendered: RenderedAdaptiveCard?): FrameLayout? {
        var v: View? = rendered?.view?.parent as? View
        var depth = 0
        while (v != null && depth < 6) {
            if (v.id == R.id.interactiveCardBox && v is FrameLayout) return v
            v = v.parent as? View
            depth++
        }
        return null
    }

    /** 应用 submitting UI：cardBox alpha=0.6 + overlay 拦截触摸。 */
    private fun applySubmittingUI(cardBox: View, overlay: View) {
        cardBox.alpha = 0.6f
        overlay.visibility = View.VISIBLE
    }

    /** 恢复 cardBox 视觉态 —— 走 tag 判断避免 view 被回收给其他消息后误改。 */
    private fun restoreCardBoxByMessageId(cardBox: View, overlay: View, messageId: String) {
        if (cardBox.tag == messageId) {
            cardBox.alpha = 1f
            overlay.visibility = View.GONE
        }
    }

    /**
     * 布置 10s 超时兜底：bot 迟迟不改卡时恢复可点 + 提示"操作已提交，机器人稍后响应"。
     * 对齐 web SUBMIT_TIMEOUT_MS = 10000ms。
     */
    private fun armSubmitTimeout(messageId: String) {
        // 先清掉上一次的（幂等）
        pendingTimeouts.remove(messageId)?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingTimeouts.remove(messageId)
            // 只有当仍在 submitting 时才动 UI（bot 已回帧的话 setData 里已经清过）
            if (submittingIds.remove(messageId)) {
                Log.d(TAG, "Submit 10s 超时，恢复可点: $messageId")
                WKToastUtils.getInstance().showToastNormal(
                    WKBaseApplication.getInstance().context.getString(R.string.base_card_action_timeout)
                )
                // 视图状态在下次 setData 会自动复位；本次超时不主动 findView 更新，避免访问陈旧引用。
            }
        }
        pendingTimeouts[messageId] = runnable
        mainHandler.postDelayed(runnable, submitTimeoutMs)
    }

    /** 清 submitting 全套状态（HTTP 失败 / bot 新帧到达都走这个）。 */
    private fun clearSubmittingState(messageId: String) {
        submittingIds.remove(messageId)
        pendingTimeouts.remove(messageId)?.let { mainHandler.removeCallbacks(it) }
    }

    private companion object {
        const val TAG = "InteractiveCard"
    }
}
