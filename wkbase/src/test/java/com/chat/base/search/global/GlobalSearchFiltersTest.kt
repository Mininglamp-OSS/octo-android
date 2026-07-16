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

package com.chat.base.search.global

import com.chat.base.search.global.dto.GlobalChannelRef
import com.chat.base.search.global.dto.GlobalSearchFilters
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchFiltersTest {

    @Test
    fun isEmpty_returns_true_when_all_null() {
        assertTrue(GlobalSearchFilters().isEmpty())
    }

    @Test
    fun isEmpty_returns_true_when_all_blank_or_empty() {
        val f = GlobalSearchFilters(
            senderIds = emptyList(),
            memberUids = emptyList(),
            memberUid = "",
            channelIds = emptyList(),
            channelTypes = emptyList(),
            contentTypes = emptyList(),
            sentAtFrom = "",
            sentAtTo = "",
        )
        assertTrue(f.isEmpty())
    }

    @Test
    fun isEmpty_false_when_any_field_present() {
        assertFalse(GlobalSearchFilters(senderIds = listOf("u1")).isEmpty())
        assertFalse(GlobalSearchFilters(memberUids = listOf("u1")).isEmpty())
        assertFalse(GlobalSearchFilters(memberUid = "u1").isEmpty())
        assertFalse(GlobalSearchFilters(channelIds = listOf(GlobalChannelRef("c", 2))).isEmpty())
        assertFalse(GlobalSearchFilters(channelTypes = listOf(2)).isEmpty())
        assertFalse(GlobalSearchFilters(contentTypes = listOf(1)).isEmpty())
        assertFalse(GlobalSearchFilters(sentAtFrom = "2026-06-01").isEmpty())
        assertFalse(GlobalSearchFilters(sentAtTo = "2026-06-30").isEmpty())
    }

    @Test
    fun canTriggerL1_true_when_keyword_present() {
        assertTrue(GlobalSearchFilters().canTriggerL1(hasKeyword = true))
    }

    @Test
    fun canTriggerL1_true_when_sender_or_member_or_channel_present() {
        assertTrue(GlobalSearchFilters(senderIds = listOf("u1")).canTriggerL1(hasKeyword = false))
        assertTrue(GlobalSearchFilters(memberUids = listOf("u1")).canTriggerL1(hasKeyword = false))
        assertTrue(GlobalSearchFilters(memberUid = "u1").canTriggerL1(hasKeyword = false))
        assertTrue(
            GlobalSearchFilters(channelIds = listOf(GlobalChannelRef("c", 2)))
                .canTriggerL1(hasKeyword = false),
        )
    }

    @Test
    fun canTriggerL1_false_when_only_time_or_content_type_present() {
        // 服务端 §2.3 明确：sent_at / content_types / channel_types 单独存在不触发 L1
        assertFalse(GlobalSearchFilters(sentAtFrom = "2026-06-01").canTriggerL1(hasKeyword = false))
        assertFalse(GlobalSearchFilters(contentTypes = listOf(1)).canTriggerL1(hasKeyword = false))
        assertFalse(GlobalSearchFilters(channelTypes = listOf(2)).canTriggerL1(hasKeyword = false))
    }

    @Test
    fun canTriggerL1_false_when_empty_and_no_keyword() {
        assertFalse(GlobalSearchFilters().canTriggerL1(hasKeyword = false))
    }
}
