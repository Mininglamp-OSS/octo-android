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

package com.chat.uikit.search.remote

import com.chat.base.search.channel.ChannelSearchUiAction
import com.chat.base.search.global.dto.GlobalSearchFilters

/**
 * 聊天记录 tab 在服务端失败后该如何收场的决策。抽成纯函数以便单测——
 * 原逻辑内联在 `GlobalActivity` 的 collect 块里，依赖 Activity/IMSDK 无法单测。
 */
enum class ChatsFallbackDecision {
    /** 服务端结果可用，正常渲染。 */
    SHOW_REMOTE,

    /** 服务端不可用但无筛选条件，可退回 IMSDK 本地关键词聚合。 */
    FALLBACK_TO_LOCAL,

    /** 展示可重试的错误态，不渲染任何结果。 */
    SHOW_ERROR,
}

object ChatsFallbackPolicy {

    /**
     * 关键约束：IMSDK 的 `msgManager.search(keyword)` **只接受关键词**，
     * 发送人 / 所在群或子区 / 包含成员 / 消息类型 / 日期范围全部无法下传。
     *
     * 因此当用户已应用实质筛选条件时，本地兜底的结果**不满足**筛选条件，
     * 而筛选 chip 仍挂在界面上声称这些条件生效——会出现「限定某群+某时间段，
     * 却渲染出其它群、其它时间的命中」这种自相矛盾的展示。
     *
     * 这种情况下宁可走可重试的错误态：让用户知道这次没搜到，而不是给出假结果。
     * 无筛选条件时本地兜底与服务端语义一致（都是纯关键词），可以安全使用。
     */
    fun decide(
        action: ChannelSearchUiAction?,
        filters: GlobalSearchFilters,
    ): ChatsFallbackDecision = when {
        action == null -> ChatsFallbackDecision.SHOW_REMOTE
        action == ChannelSearchUiAction.FALLBACK_TO_LOCAL && filters.isEmpty() ->
            ChatsFallbackDecision.FALLBACK_TO_LOCAL
        else -> ChatsFallbackDecision.SHOW_ERROR
    }
}
