/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.summary.markdown

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import androidx.core.content.ContextCompat
import com.chat.uikit.R
import java.util.regex.Pattern

/**
 * 在 [io.noties.markwon.Markwon] 输出的 [Spanned] 上做后处理: 把所有 `[N]` 替换为
 * 紫色圆角 [CitationSpan], 同时叠 [ClickableSpan] 把点击映射回引用 indices 数组。
 *
 * 1:1 对齐 iOS [OctoSummaryMarkdownRender.applyCitationsTo]:
 *   - 紧贴在一起的 `[1][2][3]` 合并成一颗徽章, 文案按 1 个 / 连续区间 / 离散三种格式:
 *       1 个   → "1"
 *       连续   → "首-尾"  (例: 1,2,3 → "1-3")
 *       离散   → 逗号拼   (例: 1,3,5 → "1,3,5")
 *   - 点击时把整组 indices 一起回调, 由 UI 触发 RelatedChat sheet。
 *
 * Markwon 4.6 的 toMarkdownWithTables 已处理标题/列表/加粗/斜体/链接/表格等所有
 * markdown 语法,这里只剩 citation 一个非标准扩展。
 */
object CitationPostProcessor {

    private val PATTERN: Pattern = Pattern.compile("\\[(\\d+)]")

    /**
     * 处理 [source] 输出新 [SpannableStringBuilder]。已知所有 citation indices 由调用方
     * 保留 (用于点击 sheet 的命中过滤),此处只负责文本替换 + span 注入。
     */
    fun process(
        context: Context,
        source: Spanned,
        onCitationClick: (List<Int>) -> Unit,
    ): SpannableStringBuilder {
        val out = SpannableStringBuilder(source)
        val groups = collectGroups(out.toString())
        if (groups.isEmpty()) return out

        val bgColor = ContextCompat.getColor(context, R.color.summary_citation_bg)
        val fgColor = ContextCompat.getColor(context, R.color.summary_purple)

        // 反向替换: 不破坏前组 range
        for (g in groups.asReversed()) {
            val badgeText = badgeTextOf(g.indices)
            val replacement = "​"   // 占位字符,带 ReplacementSpan 渲染徽章
            out.replace(g.start, g.endExclusive, replacement)
            val newEnd = g.start + replacement.length
            out.setSpan(
                CitationSpan(
                    text = badgeText,
                    indices = g.indices.toList(),
                    backgroundColor = bgColor,
                    textColor = fgColor,
                ),
                g.start, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            val capturedIndices = g.indices.toList()
            out.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        onCitationClick(capturedIndices)
                    }
                },
                g.start, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return out
    }

    /** 命中所有 [N] 并按"无字符相邻"合并组,返回的 group 已按出现顺序排序。 */
    private fun collectGroups(text: String): List<Group> {
        val matches = PATTERN.matcher(text)
        val groups = ArrayList<Group>()
        var current: MutableGroup? = null
        while (matches.find()) {
            val s = matches.start()
            val e = matches.end()
            val n = matches.group(1)?.toIntOrNull() ?: continue
            val cur = current
            if (cur == null) {
                current = MutableGroup(start = s, endExclusive = e, indices = mutableListOf(n))
                continue
            }
            if (s == cur.endExclusive) {
                cur.endExclusive = e
                cur.indices.add(n)
            } else {
                groups.add(cur.toGroup())
                current = MutableGroup(start = s, endExclusive = e, indices = mutableListOf(n))
            }
        }
        current?.let { groups.add(it.toGroup()) }
        return groups
    }

    private fun badgeTextOf(indices: List<Int>): String {
        if (indices.size == 1) return indices[0].toString()
        val consecutive = indices.zipWithNext().all { (a, b) -> b == a + 1 }
        return if (consecutive) "${indices.first()}-${indices.last()}"
        else indices.joinToString(",")
    }

    private class MutableGroup(
        val start: Int,
        var endExclusive: Int,
        val indices: MutableList<Int>,
    ) {
        fun toGroup(): Group = Group(start, endExclusive, indices.toList())
    }

    private data class Group(
        val start: Int,
        val endExclusive: Int,
        val indices: List<Int>,
    )
}
