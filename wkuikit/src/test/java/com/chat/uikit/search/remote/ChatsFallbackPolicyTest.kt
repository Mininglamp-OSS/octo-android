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
import com.chat.base.search.global.dto.GlobalChannelRef
import com.chat.base.search.global.dto.GlobalSearchFilters
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * B-4 回归：服务端失败 + 已应用筛选条件时，不得用只认关键词的本地聚合冒充筛选结果。
 */
class ChatsFallbackPolicyTest {

    @Test
    fun `no error shows remote results`() {
        assertEquals(
            ChatsFallbackDecision.SHOW_REMOTE,
            ChatsFallbackPolicy.decide(null, GlobalSearchFilters()),
        )
        assertEquals(
            "有筛选条件且服务端成功时同样正常渲染",
            ChatsFallbackDecision.SHOW_REMOTE,
            ChatsFallbackPolicy.decide(null, GlobalSearchFilters(sentAtFrom = "2026-01-01")),
        )
    }

    @Test
    fun `fallback allowed when no filters are active`() {
        assertEquals(
            ChatsFallbackDecision.FALLBACK_TO_LOCAL,
            ChatsFallbackPolicy.decide(
                ChannelSearchUiAction.FALLBACK_TO_LOCAL,
                GlobalSearchFilters(),
            ),
        )
    }

    /** 核心回归：每一种筛选维度生效时，本地兜底都必须被否决。 */
    @Test
    fun `fallback rejected for every active filter dimension`() {
        val filtered = listOf(
            "senderIds" to GlobalSearchFilters(senderIds = listOf("u1")),
            "memberUids" to GlobalSearchFilters(memberUids = listOf("u1")),
            "memberUid" to GlobalSearchFilters(memberUid = "u1"),
            "channelIds" to GlobalSearchFilters(channelIds = listOf(GlobalChannelRef("c1", 2))),
            "channelTypes" to GlobalSearchFilters(channelTypes = listOf(2)),
            "contentTypes" to GlobalSearchFilters(contentTypes = listOf(1)),
            "sentAtFrom" to GlobalSearchFilters(sentAtFrom = "2026-01-01"),
            "sentAtTo" to GlobalSearchFilters(sentAtTo = "2026-02-01"),
        )
        for ((dimension, filters) in filtered) {
            assertEquals(
                "$dimension 生效时本地兜底无法满足该条件，必须走错误态",
                ChatsFallbackDecision.SHOW_ERROR,
                ChatsFallbackPolicy.decide(ChannelSearchUiAction.FALLBACK_TO_LOCAL, filters),
            )
        }
    }

    /** 排序不是筛选条件，不应因此否决本地兜底。 */
    @Test
    fun `sort alone does not reject fallback`() {
        assertEquals(
            ChatsFallbackDecision.FALLBACK_TO_LOCAL,
            ChatsFallbackPolicy.decide(
                ChannelSearchUiAction.FALLBACK_TO_LOCAL,
                GlobalSearchFilters(sortTimeAsc = true),
            ),
        )
    }

    @Test
    fun `other errors always show error state`() {
        val codes = listOf(
            ChannelSearchUiAction.RATE_LIMITED,
            ChannelSearchUiAction.FEATURE_DISABLED,
            ChannelSearchUiAction.BLOCK_NOT_FOUND,
            ChannelSearchUiAction.VALIDATION_ERROR,
            ChannelSearchUiAction.GENERIC_ERROR,
        )
        for (action in codes) {
            assertEquals(
                "$action 不是本地兜底态",
                ChatsFallbackDecision.SHOW_ERROR,
                ChatsFallbackPolicy.decide(action, GlobalSearchFilters()),
            )
        }
    }
}
