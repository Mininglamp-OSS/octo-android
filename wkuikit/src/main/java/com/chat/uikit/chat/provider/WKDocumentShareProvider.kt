/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.uikit.chat.provider

import android.content.Intent
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.chat.base.act.WKWebViewActivity
import com.chat.base.config.WKConfig
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.utils.WKToastUtils
import com.chat.base.ui.Theme
import com.chat.base.views.BubbleLayout
import com.chat.uikit.R
import com.chat.uikit.chat.msgmodel.WKDocumentShareContent
import com.chat.base.foldable.PaneMetrics
import com.chat.base.utils.AndroidUtilities

internal class DocumentShareLoadLifecycle(private val load: (force: Boolean) -> Unit) {
    private var attached = false
    private var observedFocused = false
    private var lostFocus = false
    fun onAttach(hasWindowFocus: Boolean) {
        attached = true
        observedFocused = hasWindowFocus
        lostFocus = false
        load(false)
    }
    fun onFocusChanged(hasWindowFocus: Boolean) {
        if (!attached) return
        if (hasWindowFocus) {
            if (observedFocused && lostFocus) load(true)
            observedFocused = true
            lostFocus = false
        } else if (observedFocused) lostFocus = true
    }
    fun onDetach() { attached = false; observedFocused = false; lostFocus = false }
}

/**
 * 共享文档卡片 Provider（type=18）。标题卡片，点击拉起内置 WebView 打开文档。
 * 不继承 WKFileProvider：无下载/缓存/上传逻辑，文档正文由网页处理。
 */
class WKDocumentShareProvider : WKChatBaseProvider() {

    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
        return LayoutInflater.from(context).inflate(R.layout.chat_item_document_share, parentView, false)
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        val contentLayout = parentView.findViewById<BubbleLayout>(R.id.contentLayout)
        val card = parentView.findViewById<DocumentShareCardView>(R.id.documentShareCard)
        val documentShareRoot = parentView.findViewById<View>(R.id.documentShareRoot)
        contentLayout.setOnClickListener(null)
        val parentWidth = (parentView.parent as? View)?.width?.takeIf { it > 0 } ?: parentView.width
        val measuredWidth = (parentView.parent as? View)?.measuredWidth?.takeIf { it > 0 } ?: parentWidth
        val availableWidth = listOf(parentWidth, measuredWidth).filter { it > 0 }.minOrNull() ?: getViewWidth(from, uiChatMsgItemEntity)
        val paneWidth = PaneMetrics.widthPx(context)
        val legacyWindowWidth = if (AndroidUtilities.isPORTRAIT) paneWidth else maxOf(paneWidth, context.resources.displayMetrics.heightPixels)
        val baseWidth = getViewWidth(from, uiChatMsgItemEntity)
        val reserved = (legacyWindowWidth - baseWidth).coerceAtLeast(0)
        val desired = (paneWidth - reserved).coerceAtLeast(0)
        documentShareRoot.layoutParams.width = minOf(desired, (480 * context.resources.displayMetrics.density).toInt(), availableWidth).coerceAtLeast(0)
        card.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        resetCellBackground(parentView, uiChatMsgItemEntity, from)
        addLongClick(contentLayout, uiChatMsgItemEntity)
        val content = uiChatMsgItemEntity.wkMsg.baseContentMsgModel as? WKDocumentShareContent
        if (content == null) {
            card.clearBinding()
            card.renderResult(WKDocumentShareContent(), DocumentSharePreviewRepository.Result(DocumentSharePreviewRepository.State.error))
            return
        }
        val repository = DocumentSharePreviewRepository.shared()
        var bindingVersion = 0L
        var requestVersion = 0L
        var sessionListener: WKConfig.SessionListener? = null
        var attachVersion = card.lifecycleVersion()
        val messageIdentity = "${uiChatMsgItemEntity.wkMsg.channelType}:${uiChatMsgItemEntity.wkMsg.channelID}:${uiChatMsgItemEntity.wkMsg.clientMsgNO}:${content.docId.orEmpty()}:${content.spaceId.orEmpty()}:${DocumentSharePreviewRepository.normalizeKind(content.kind)}"
        fun validResult(result: DocumentSharePreviewRepository.Result): Boolean {
            val snapshot = WKConfig.getInstance().getSessionSnapshot()
            val context = result.context
            return context == null && snapshot.uid.orEmpty().isEmpty() && snapshot.token.orEmpty().isEmpty() || context?.version == snapshot.version && context.uid == snapshot.uid && context.token == snapshot.token && context.apiOrigin == DocumentSharePreviewRepository.apiOrigin(com.chat.base.config.WKApiConfig.baseUrl) && context.webOrigin == DocumentSharePreviewRepository.webOrigin(com.chat.base.config.WKApiConfig.baseWebUrl)
        }
        fun load(force: Boolean) {
            val loadBinding = bindingVersion
            val loadAttach = attachVersion
            val request = ++requestVersion
            val loadRequest = requestVersion
            val loadIdentity = messageIdentity
            card.setSubscription(null)
            val snapshot = WKConfig.getInstance().getSessionSnapshot()
            val api = DocumentSharePreviewRepository.apiOrigin(com.chat.base.config.WKApiConfig.baseUrl)
            if (snapshot.uid.orEmpty().isEmpty() || snapshot.token.orEmpty().isEmpty() || !DocumentSharePreviewRepository.validDocId(content.docId.orEmpty()) || (content.spaceId.orEmpty().isNotEmpty() && !DocumentSharePreviewRepository.validDocId(content.spaceId.orEmpty())) || api == null) {
                if (card.isCurrent(loadBinding, loadAttach) && loadRequest == requestVersion && loadIdentity == messageIdentity) card.renderResult(content, DocumentSharePreviewRepository.Result(DocumentSharePreviewRepository.State.error))
                return
            }
            var deliveredSynchronously = false
            val subscription = repository.fetch(content, force) { result ->
                val render = {
                    if (card.isCurrent(loadBinding, loadAttach) && request == requestVersion && loadRequest == requestVersion && loadIdentity == messageIdentity && validResult(result)) {
                        card.renderResult(content, result)
                        deliveredSynchronously = true
                    }
                }
                if (Looper.myLooper() == Looper.getMainLooper()) render() else card.post(render)
            }
            card.setSubscription(subscription)
            if (!deliveredSynchronously && card.isCurrent(loadBinding, loadAttach) && request == requestVersion) {
                card.renderResult(content, DocumentSharePreviewRepository.Result(DocumentSharePreviewRepository.State.loading))
            }
        }
        val loadLifecycle = DocumentShareLoadLifecycle(::load)
        val listener = object : DocumentShareCardView.Listener {
            override fun onOpen() { if (card.isCurrent(bindingVersion, attachVersion) && card.isOpenable() && validResult(card.currentResult())) handleDocClick(uiChatMsgItemEntity, content) }
            override fun onCopy() { if (card.isCurrent(bindingVersion, attachVersion) && validResult(card.currentResult())) copyDoc(content) }
            override fun onInstallLongClick() { addLongClick(card, uiChatMsgItemEntity) }
            override fun onWindowFocusChanged(hasWindowFocus: Boolean) { if (card.isCurrent(bindingVersion, attachVersion)) loadLifecycle.onFocusChanged(hasWindowFocus) }
            override fun onAttach(hasWindowFocus: Boolean) {
                attachVersion = card.lifecycleVersion()
                loadLifecycle.onAttach(hasWindowFocus)
                sessionListener?.let(WKConfig.getInstance()::removeSessionListener)
                sessionListener = WKConfig.SessionListener { card.post { if (card.isCurrent(bindingVersion, attachVersion)) { requestVersion++; card.setSubscription(null); card.renderResult(content, DocumentSharePreviewRepository.Result(DocumentSharePreviewRepository.State.loading)); load(false) } } }
                sessionListener?.let(WKConfig.getInstance()::addSessionListener)
            }
            override fun onDetach() { loadLifecycle.onDetach(); sessionListener?.let(WKConfig.getInstance()::removeSessionListener); sessionListener = null; requestVersion++ }
        }
        bindingVersion = card.bindMessage(content, listener)
        if (card.isAttachedToWindow) listener.onAttach(card.hasWindowFocus())
        else card.renderResult(content, DocumentSharePreviewRepository.Result(DocumentSharePreviewRepository.State.loading))
    }

    override val itemViewType: Int
        get() = WKContentType.docShareCard

    override fun resetCellBackground(
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellBackground(parentView, uiChatMsgItemEntity, from)
        val bgType = getMsgBgType(
            uiChatMsgItemEntity.previousMsg,
            uiChatMsgItemEntity.wkMsg,
            uiChatMsgItemEntity.nextMsg
        )
        val root = parentView.findViewById<View>(R.id.documentShareRoot)
        val contentLayout = parentView.findViewById<BubbleLayout>(R.id.contentLayout)
        contentLayout.setBubbleNormalColor(com.chat.base.R.color.transparent)
        contentLayout.setBubbleSelectedColor(com.chat.base.R.color.transparent)
        contentLayout.setBubbleRadius((10 * context.resources.displayMetrics.density).toInt())
        contentLayout.setBubbleBorderSize(0)
        contentLayout.setShadowRadius(0f)
    }

    override fun resetCellListener(
        position: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellListener(position, parentView, uiChatMsgItemEntity, from)
        val card = parentView.findViewById<DocumentShareCardView>(R.id.documentShareCard)
        addLongClick(card, uiChatMsgItemEntity)
    }

    private fun handleDocClick(
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        content: WKDocumentShareContent
    ) {
        val url = DocumentSharePreviewRepository.navigationUrl(content.docId.orEmpty())
            ?: return WKToastUtils.getInstance().showToastNormal(context.getString(R.string.doc_share_link_invalid))
        val intent = Intent(context, WKWebViewActivity::class.java).apply {
            putExtra("url", url)
            putExtra("channelID", uiChatMsgItemEntity.wkMsg.channelID)
            putExtra("channelType", uiChatMsgItemEntity.wkMsg.channelType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun copyDoc(content: WKDocumentShareContent) {
        val url = DocumentSharePreviewRepository.navigationUrl(content.docId.orEmpty())
            ?: return WKToastUtils.getInstance().showToastNormal(context.getString(R.string.doc_share_link_invalid))
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("document", url))
        WKToastUtils.getInstance().showToastNormal(context.getString(R.string.copied))
    }

    companion object {
        @JvmStatic fun buildNavigationUrl(docId: String): String? = DocumentSharePreviewRepository.navigationUrl(docId)
    }
}
