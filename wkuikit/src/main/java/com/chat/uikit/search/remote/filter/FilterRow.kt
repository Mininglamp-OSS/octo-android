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

package com.chat.uikit.search.remote.filter

import com.chad.library.adapter.base.entity.MultiItemEntity

/**
 * 筛选页行模型。[FilterActivity] 用一个 [com.chad.library.adapter.base.BaseMultiItemQuickAdapter] 渲染
 * 全部 section，用 [itemType] 区分 ItemType，不做真正的 sticky section header（用 [HEADER] 行模拟分组标题）。
 * 每行的点击行为内联为 [onClick] 闭包，由构建行列表的一方（[FilterActivity]）决定具体语义，
 * adapter 本身只负责渲染和转发点击，不需要为每种业务语义建 id 映射。
 */
sealed class FilterRow(override val itemType: Int) : MultiItemEntity {
    companion object {
        const val HEADER = 0
        const val NAV = 1
        const val CHECK = 2
    }

    /** 分组标题，如"发送者"/"日期范围"。 */
    class Header(val title: String) : FilterRow(HEADER)

    /** 跳转行：发送者/所在群聊或子区/包含成员/起始日期/结束日期/快速选择。 */
    class Nav(val title: String, val detail: String, val onClick: () -> Unit) : FilterRow(NAV)

    /**
     * 原地勾选行：聊天类型/消息类型/文件类型/排序。点击后由调用方更新 draft 并重建行列表刷新勾选态。
     * 对齐 iOS WKChannelHistoryFilterVC：选中态用行右侧文字 "✓"（主题色）表示，不用左侧 checkbox
     * 图标，不改变行背景色。聊天类型是独立开关（可都选可都不选），排序是单选，消息类型/文件类型是多选。
     */
    class Check(val title: String, val checked: Boolean, val onClick: () -> Unit) : FilterRow(CHECK)
}
