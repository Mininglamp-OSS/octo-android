/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.chat.uikit.chat.preview

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chat.base.WKBaseApplication
import com.chat.base.act.WKWebViewActivity
import com.chat.base.foldable.PaneMetrics
import com.chat.base.utils.AndroidUtilities
import com.chat.base.views.BubbleLayout
import com.chat.uikit.BuildConfig
import com.chat.uikit.R
import com.chat.uikit.chat.provider.card.CardActionDispatcher
import com.chat.uikit.chat.provider.card.CardRenderSpec
import com.chat.uikit.chat.provider.card.InteractiveCardRenderer
import com.chat.uikit.chat.provider.card.InteractiveCardStylizer
import com.chat.uikit.chat.provider.card.MessageContext
import com.xinbida.wukongim.entity.WKChannelType
import com.xinbida.wukongim.entity.WKMsg

/**
 * AdaptiveCard 模板可视化预览页（**debug-only**，通过 `adb shell am start` 拉起）：
 * ```
 * adb shell am start -n com.chat.demo/com.chat.uikit.chat.preview.InteractiveCardPreviewActivity
 * ```
 *
 * ## 设计目标
 * 让"服务端交付一份 golden card JSON → App 里长这样"这条链路能被设计和研发**独立看到、快速迭代**，
 * 而不是等埋到真实 bot / 真实群里才发现渲染出问题。
 *
 * ## 保真度约束（重要）
 * 最终卡片跑在**聊天气泡内**——预览页必须严格复用聊天窗口那一套：
 *  1. **气泡骨架**：直接 include `chat_item_interactive_card`（Provider 生产环境用的同一份 layout）
 *  2. **气泡宽度**：走 `WKChatBaseProvider.getViewWidth()` 同一份公式（PaneMetrics − 104/144dp offset）
 *  3. **渲染管线**：SDK 反序列化 / stylize / SVG 图标加载全部走 [InteractiveCardRenderer]
 *     （生产 Provider 用的同一个类），不新造渲染路径
 *  4. **主题**：Activity 继承 `Theme.AppCompat.DayNight`，[toggleTheme] 走
 *     `AppCompatDelegate.setDefaultNightMode` → Activity recreate → [OctoHostConfig] 自然拿到
 *     正确 palette
 *
 * 唯一不"真"的是 [CardActionDispatcher] —— submit 走 toast、openUrl 走真实 WebView，
 * 便于在预览页也能点按钮验交互（真提交不打服务端）。
 *
 * ## 扩展点
 * 新增卡片样式：`assets/interactive_cards/<templateId>/goldens/<state>.card.json` 加文件即可，
 * [PreviewGoldenLoader] 自动扫。
 */
class InteractiveCardPreviewActivity : AppCompatActivity() {

    /** 气泡在聊天里的三种可能位置。宽度会跟着变，`getViewWidth()` 公式的实际再现。 */
    private enum class Side { RECV_GROUP, SEND, RECV_PERSONAL }

    private var currentSide: Side = Side.RECV_GROUP

    private lateinit var recyclerView: RecyclerView
    private lateinit var widthLabel: TextView
    private lateinit var toggleThemeBtn: Button
    private lateinit var toggleSideBtn: Button

    private var goldens: List<PreviewGoldenLoader.Golden> = emptyList()

    /** 独立于 Provider 的 dispatcher —— submit / toast 都走本地反馈，OpenUrl 走真 WebView。 */
    private val dispatcher: CardActionDispatcher by lazy { createPreviewDispatcher() }

    private val renderer: InteractiveCardRenderer by lazy {
        InteractiveCardRenderer(dispatcher = dispatcher)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Debug-only 二次拦截：即便 manifest 层 exported=false 已经防外部 App 启动，
        // 这里再 gate 一次防"通过反射或内部代码不小心引用"到 release 版触发预览页。
        // Preview 只服务开发对稿场景，release 用户不应看到 AC 卡的调试骨架。
        if (!BuildConfig.DEBUG) {
            finish()
            return
        }
        setContentView(R.layout.activity_interactive_card_preview)
        title = "AdaptiveCard 预览"

        recyclerView = findViewById(R.id.previewList)
        widthLabel = findViewById(R.id.widthLabel)
        toggleThemeBtn = findViewById(R.id.toggleThemeBtn)
        toggleSideBtn = findViewById(R.id.toggleSideBtn)

        toggleThemeBtn.setOnClickListener { flipTheme() }
        toggleSideBtn.setOnClickListener { cycleSide() }

        goldens = PreviewGoldenLoader.loadAll(this)
        if (goldens.isEmpty()) {
            Toast.makeText(this, "assets/interactive_cards/ 下没有找到 golden", Toast.LENGTH_LONG).show()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = PreviewCardAdapter(
            activity = this,
            renderer = renderer,
            goldens = goldens,
            bubbleWidthProvider = ::bubbleWidthPx,
            sideProvider = ::currentSide,
        )

        refreshToolbarLabels()
    }

    override fun onDestroy() {
        renderer.clear()
        dispatcher.onDestroy()
        super.onDestroy()
    }

    // ─────────────────────── Toolbar actions ───────────────────────

    private fun flipTheme() {
        val isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        val newMode = if (isDark) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        AppCompatDelegate.setDefaultNightMode(newMode)
        // setDefaultNightMode 会触发 Activity recreate；无需手动 refresh。
    }

    private fun cycleSide() {
        currentSide = when (currentSide) {
            Side.RECV_GROUP -> Side.SEND
            Side.SEND -> Side.RECV_PERSONAL
            Side.RECV_PERSONAL -> Side.RECV_GROUP
        }
        recyclerView.adapter?.notifyDataSetChanged()
        refreshToolbarLabels()
    }

    private fun refreshToolbarLabels() {
        toggleSideBtn.text = when (currentSide) {
            Side.RECV_GROUP -> "群接收 →"
            Side.SEND -> "自己发送 →"
            Side.RECV_PERSONAL -> "私聊接收 →"
        }
        val widthDp = (bubbleWidthPx() / resources.displayMetrics.density).toInt()
        widthLabel.text = "气泡 ${widthDp}dp"
    }

    // ─────────────────────── Bubble width (镜像 getViewWidth 公式) ───────────────────────

    /**
     * 复刻 [com.chat.base.msgitem.WKChatBaseProvider.getViewWidth] 的算法：
     *  - `maxWidth = PaneMetrics.widthPx(this)`（Embedding 下自动拿正确 pane 宽）
     *  - SEND / PERSONAL 接收：`maxWidth − dp(70 + 34)`
     *  - 群接收：`maxWidth − dp(70 + 40 + 34)`
     *  - 预览不考虑 flame / pinned 修正（现实中挂在交互卡上的极少）
     */
    private fun bubbleWidthPx(): Int {
        val paneWidth = PaneMetrics.widthPx(this)
        val checkBoxMargin = 34
        val extra = if (currentSide == Side.RECV_GROUP) 40 else 0
        return paneWidth - AndroidUtilities.dp((70 + extra + checkBoxMargin).toFloat())
    }

    private fun sendSide(): Boolean = currentSide == Side.SEND

    // ─────────────────────── Dispatcher 工厂（预览专用副本） ───────────────────────

    private fun createPreviewDispatcher(): CardActionDispatcher {
        val appCtx = WKBaseApplication.getInstance().context
        val toast = { text: String ->
            Toast.makeText(appCtx, text, Toast.LENGTH_SHORT).show()
        }
        return CardActionDispatcher(
            submitter = CardActionDispatcher.CardSubmitter { body, success, _ ->
                Log.d(TAG, "[Preview] Submit body=$body")
                val actionId = body["action_id"]?.toString() ?: "?"
                toast("Submit（预览模式，未真提交）：$actionId")
                // 假装成功 —— 预览页想看下 submitting 态自动散开
                Handler(Looper.getMainLooper()).postDelayed({ success(null) }, 800)
            },
            webView = CardActionDispatcher.WebViewLauncher { url ->
                try {
                    val intent = Intent(this, WKWebViewActivity::class.java).apply {
                        putExtra("url", url)
                    }
                    startActivity(intent)
                    true
                } catch (t: Throwable) {
                    Log.w(TAG, "预览页打开 URL 失败: $url", t)
                    false
                }
            },
            toaster = CardActionDispatcher.Toaster { text -> toast(text) },
            timeoutScheduler = object : CardActionDispatcher.TimeoutScheduler {
                private val handler = Handler(Looper.getMainLooper())
                override fun postDelayed(
                    delayMs: Long,
                    task: () -> Unit,
                ): CardActionDispatcher.TimeoutScheduler.Handle {
                    val runnable = Runnable { task() }
                    handler.postDelayed(runnable, delayMs)
                    return CardActionDispatcher.TimeoutScheduler.Handle { handler.removeCallbacks(runnable) }
                }
            },
            extraSync = CardActionDispatcher.ExtraMsgSyncer { _, _ -> /* no-op */ },
            selfUidProvider = { "preview_user" },
            uiListener = object : CardActionDispatcher.SubmitUiListener {
                override fun onSubmitStart(messageId: String) { /* 预览不做遮罩 */ }
                override fun onSubmitEnd(messageId: String) { /* 预览不做遮罩 */ }
            },
            strings = CardActionDispatcher.Strings(
                openUrlFailed = "打开 URL 失败",
                actionRetry = "网络异常，稍后重试",
                actionFailed = "操作失败",
                actionTimeout = "请求超时",
            ),
        )
    }

    // ─────────────────────── Adapter ───────────────────────

    private class PreviewCardAdapter(
        private val activity: AppCompatActivity,
        private val renderer: InteractiveCardRenderer,
        private val goldens: List<PreviewGoldenLoader.Golden>,
        private val bubbleWidthProvider: () -> Int,
        private val sideProvider: () -> Side,
    ) : RecyclerView.Adapter<PreviewVH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewVH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_card_preview, parent, false)
            return PreviewVH(v)
        }

        override fun onBindViewHolder(holder: PreviewVH, position: Int) {
            val g = goldens[position]
            holder.label.text = "${g.templateId} / ${g.state}"
            val side = sideProvider()
            val bubbleWidth = bubbleWidthProvider()

            val cardView = holder.itemView.findViewById<View>(R.id.cardView)
            val slot = holder.itemView.findViewById<FrameLayout>(R.id.previewBubbleSlot)

            // BubbleLayout.onDraw 会读 mBubbleNormalColor 走 ContextCompat.getColor —— 未初始化时
            // colorId=0 会 crash。Provider 生产链路在 resetCellBackground 里把它设成 transparent；
            // 预览页没有那套 flow，所以在这里手动对齐。
            holder.itemView.findViewById<BubbleLayout>(R.id.contentLayout)?.apply {
                setBubbleNormalColor(com.chat.base.R.color.transparent)
                setBubbleSelectedColor(com.chat.base.R.color.transparent)
            }

            // 复刻 Provider 的宽度赋值 —— cardView 是 chat_item_interactive_card 的 outer LinearLayout。
            (cardView.layoutParams as? FrameLayout.LayoutParams)?.apply {
                width = bubbleWidth
                gravity = if (side == Side.SEND) Gravity.END else Gravity.START
                cardView.layoutParams = this
            } ?: run {
                // FrameLayout 的 include 默认给 MATCH_PARENT LayoutParams，理论上一定命中。
                // 保底再包一次，避免 include 主题不同带来的意外类型。
                cardView.layoutParams = FrameLayout.LayoutParams(
                    bubbleWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = if (side == Side.SEND) Gravity.END else Gravity.START
                }
            }
            // 用 tag 记 slot 视觉参数无关，dispatcher 侧的 UI 反馈不参与预览。

            val container = holder.itemView.findViewById<FrameLayout>(R.id.interactiveCardContainer)
            container.removeAllViews()
            val fallback = holder.itemView.findViewById<TextView>(R.id.interactiveCardFallbackTv)
            fallback.visibility = View.GONE
            val submitOverlay = holder.itemView.findViewById<View>(R.id.interactiveCardSubmitOverlay)
            submitOverlay.visibility = View.GONE

            // 稳定的 preview messageID —— renderer LRU 靠这个复用同一张 rendered view。
            // 每张 golden 一个独立 messageID，避免不同 golden 挂同一个 view 上互相"抢"。
            val previewMsgId = "preview::${g.templateId}::${g.state}"

            val wkMsg = WKMsg().apply {
                messageID = previewMsgId
                channelType = if (side == Side.RECV_PERSONAL || side == Side.SEND) {
                    WKChannelType.PERSONAL
                } else {
                    WKChannelType.GROUP
                }
                fromUID = if (side == Side.SEND) "preview_user" else "preview_bot"
            }
            val ctx = MessageContext(wkMsg = wkMsg, allowSubmit = true)
            val spec = CardRenderSpec(cardJson = g.cardJson, cardVersion = g.cardVersion)

            when (val r = renderer.renderInto(container, spec, ctx, activity)) {
                is InteractiveCardRenderer.Result.Success -> {
                    container.visibility = View.VISIBLE
                    fallback.visibility = View.GONE
                }
                is InteractiveCardRenderer.Result.Fallback -> {
                    container.visibility = View.GONE
                    fallback.text = "渲染失败：${r.reason}"
                    fallback.visibility = View.VISIBLE
                }
            }
        }

        override fun getItemCount(): Int = goldens.size
    }

    private class PreviewVH(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.previewRowLabel)
    }

    private companion object {
        const val TAG = "CardPreview"
    }
}
