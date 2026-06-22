/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.summary.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chat.base.summary.model.CitationContextMessage
import com.chat.base.summary.model.CitationItem
import com.chat.uikit.R
import com.chat.uikit.databinding.SheetSummaryRelatedChatBinding
import com.chat.uikit.databinding.ItemSummaryRelatedChatBinding
import com.chat.uikit.summary.time.RelativeTime
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/**
 * 关联聊天 sheet, 1:1 对齐 iOS [OctoRelatedChatSheet]:
 *   - 顶部 grabber + 标题 + 关闭
 *   - 来源 (citation.source)
 *   - 列表: 每条 citation 展开成 contextBefore[] + hit + contextAfter[]
 *     - hit 行: 紫淡底 + 左紫条 + "原消息 →" 跳转按钮
 *     - context 行: 灰淡底
 *
 * 单 channel 时不显示 channel 切换 chips (与 iOS 同). 多 channel 切换暂用 sheet 内
 * channel 标签拼接 (复刻迁到 PR6 之后).
 */
class SummaryRelatedChatSheet : BottomSheetDialogFragment() {

    private var _binding: SheetSummaryRelatedChatBinding? = null
    private val binding get() = _binding!!

    private var rows: List<Row> = emptyList()
    private var sourceText: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = SheetSummaryRelatedChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.closeIv.setOnClickListener { dismiss() }
        binding.sourceLabel.text = sourceText
        binding.listRv.layoutManager = LinearLayoutManager(requireContext())
        binding.listRv.adapter = RowAdapter(rows) { row -> onJumpOriginal(row) }
    }

    private fun onJumpOriginal(row: Row) {
        val rawCid = row.channelId.orEmpty()
        if (rawCid.isEmpty()) return
        val activity = activity as? androidx.activity.ComponentActivity ?: return
        val ctype = row.channelType.toByte()
        // DM 场景 citation.channel_id 是 "<myUid>@<peerUid>" 复合串, SDK
        // WKChannel(PERSONAL) 按 peerUid 单值建索引, 直接喂复合串拉消息全 miss。
        // 1:1 对齐 iOS commit ed2f841 OctoRelatedChatSheet.resolvedChannelIdForJump:
        // 只在 PERSONAL+含"@" 时拆, 群聊/子区路径原样透传不动。
        val cid = resolveChannelIdForJump(rawCid, ctype)
        val mseq = row.messageSeq
        // ChatActivity 期待 orderSeq, citation 给的是 message_seq, 必须 getMessageOrderSeq
        // 转换 + DB 查询走 IO 线程, 切回主线程触发跳转 + 关闭 sheet。
        // 直接构造 Intent 启动, 不走 EndpointSID.chatView → ChatReuseNavigator 的 narrow 路径
        // (它会加 REORDER_TO_FRONT, 配合 ChatActivity 默认返回行为 goBackToList → TabActivity
        // (singleTask) 清栈, 用户按返回会落到上下文主页, 不是详情页). 加 from_summary_detail
        // extra 让 ChatActivity 跳过 goBackToList, 走标准 finish 回详情。
        activity.lifecycleScope.launch {
            val orderSeq = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.xinbida.wukongim.WKIM.getInstance().msgManager
                    .getMessageOrderSeq(mseq.toLong(), cid, ctype)
            }
            val intent = android.content.Intent(activity, com.chat.uikit.chat.ChatActivity::class.java)
            intent.putExtra("channelId", cid)
            intent.putExtra("channelType", ctype)
            intent.putExtra("tipsOrderSeq", orderSeq)
            intent.putExtra("from_summary_detail", true)
            activity.startActivity(intent)
            dismissAllowingStateLoss()
        }
    }

    /**
     * 把 citation.channel_id 归一化成 SDK 期望的 channelId:
     *   - 非 PERSONAL 或不含 "@" → 原样返回 (群聊 / 子区 / 已经是单值的 DM)
     *   - PERSONAL + "<myUid>@<peerUid>" 复合串 → 拆 "@" 后取非 myUid 那段作为 peerUid;
     *     都没有兜底原 cid 不让 jump 触发空白
     *
     * 与 iOS commit ed2f841 OctoRelatedChatSheet.resolvedChannelIdForJump: 同口径。
     */
    private fun resolveChannelIdForJump(cid: String, ctype: Byte): String {
        if (cid.isEmpty()) return cid
        if (ctype != com.xinbida.wukongim.entity.WKChannelType.PERSONAL) return cid
        if (!cid.contains("@")) return cid
        val parts = cid.split("@")
        val myUid = com.chat.base.config.WKConfig.getInstance().uid
        for (p in parts) {
            if (p.isEmpty()) continue
            if (!myUid.isNullOrEmpty() && p == myUid) continue
            return p
        }
        return cid
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun bind(citations: List<CitationItem>, activeIndices: List<Int>) {
        val activeSet = activeIndices.toHashSet()
        val relevant = citations.filter { it.index in activeSet }
        sourceText = relevant.firstOrNull()?.source.orEmpty()
        rows = buildRows(relevant)
    }

    private fun buildRows(citations: List<CitationItem>): List<Row> {
        val out = mutableListOf<Row>()
        for (c in citations) {
            for (m in c.contextBefore) out += Row.context(m, c.channelId, c.channelType)
            out += Row.hit(c)
            for (m in c.contextAfter) out += Row.context(m, c.channelId, c.channelType)
        }
        return out
    }

    /**
     * 单条行数据. hit = true 是命中 citation, 显示紫淡底 + 跳转按钮; 否则是 context 行。
     */
    data class Row(
        val isHit: Boolean,
        val sender: String,
        val content: String,
        val sentAt: String,
        val channelId: String?,
        val channelType: Int,
        val messageSeq: Int,
    ) {
        companion object {
            fun hit(c: CitationItem) = Row(
                isHit = true,
                sender = c.sender,
                content = c.content,
                sentAt = c.sentAt,
                channelId = c.channelId,
                channelType = c.channelType,
                messageSeq = c.messageSeq,
            )

            fun context(m: CitationContextMessage, channelId: String?, channelType: Int) = Row(
                isHit = false,
                sender = m.sender,
                content = m.content,
                sentAt = m.sentAt,
                channelId = channelId,
                channelType = channelType,
                messageSeq = m.messageSeq,
            )
        }
    }

    private class RowAdapter(
        private val rows: List<Row>,
        private val onJump: (Row) -> Unit,
    ) : RecyclerView.Adapter<RowVH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowVH {
            val b = ItemSummaryRelatedChatBinding.inflate(
                LayoutInflater.from(parent.context), parent, false,
            )
            return RowVH(b)
        }
        override fun getItemCount() = rows.size
        override fun onBindViewHolder(holder: RowVH, position: Int) {
            holder.bind(rows[position], onJump)
        }
    }

    private class RowVH(private val b: ItemSummaryRelatedChatBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(row: Row, onJump: (Row) -> Unit) {
            b.bubble.setBackgroundResource(
                if (row.isHit) R.drawable.bg_summary_related_hit
                else R.drawable.bg_summary_related_context,
            )
            b.hitBar.visibility = if (row.isHit) View.VISIBLE else View.GONE
            b.nameTv.text = row.sender
            b.timeTv.text = RelativeTime.localFromISO(row.sentAt)
            b.bodyTv.text = row.content
            val canJump = row.isHit && !row.channelId.isNullOrEmpty() && row.messageSeq > 0
            b.jumpBtn.visibility = if (canJump) View.VISIBLE else View.GONE
            b.jumpBtn.setOnClickListener { if (canJump) onJump(row) }
        }
    }

    companion object {
        private const val TAG = "SummaryRelatedChatSheet"

        fun show(
            fm: FragmentManager,
            citations: List<CitationItem>,
            activeIndices: List<Int>,
        ) {
            val sheet = SummaryRelatedChatSheet().also { it.bind(citations, activeIndices) }
            sheet.show(fm, TAG)
        }
    }
}
