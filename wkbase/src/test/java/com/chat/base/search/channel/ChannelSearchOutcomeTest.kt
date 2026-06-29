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

package com.chat.base.search.channel

import com.chat.base.search.channel.dto.SearchErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelSearchOutcomeTest {

    @Test
    fun success_carries_data_and_no_error() {
        val out = ChannelSearchOutcome.success("ok-data")
        assertTrue(out.ok)
        assertEquals("ok-data", out.data)
        assertNull(out.errorCode)
        assertEquals(200, out.httpStatus)
        assertFalse(out.fromLocalFallback)
    }

    @Test
    fun success_can_mark_fallback() {
        val out = ChannelSearchOutcome.success("local", fromLocalFallback = true)
        assertTrue(out.ok)
        assertTrue(out.fromLocalFallback)
    }

    @Test
    fun failure_carries_error_only() {
        val out = ChannelSearchOutcome.failure<String>(
            httpStatus = 503,
            errorCode = SearchErrorCode.UPSTREAM_UNAVAILABLE,
        )
        assertFalse(out.ok)
        assertNull(out.data)
        assertEquals(503, out.httpStatus)
        assertEquals(SearchErrorCode.UPSTREAM_UNAVAILABLE, out.errorCode)
    }

    // --- uiAction 映射，与计划里 7 个错误码的 UI 行为表对齐 ---

    @Test
    fun uiAction_upstream_unavailable_triggers_local_fallback() {
        val out = ChannelSearchOutcome.failure<String>(503, SearchErrorCode.UPSTREAM_UNAVAILABLE)
        assertEquals(ChannelSearchUiAction.FALLBACK_TO_LOCAL, out.uiAction())
    }

    @Test
    fun uiAction_local_network_also_triggers_fallback() {
        val out = ChannelSearchOutcome.failure<String>(0, ChannelSearchOutcome.LOCAL_ERROR_NETWORK)
        assertEquals(ChannelSearchUiAction.FALLBACK_TO_LOCAL, out.uiAction())
    }

    @Test
    fun uiAction_rate_limited_does_not_trigger_fallback() {
        val out = ChannelSearchOutcome.failure<String>(429, SearchErrorCode.RATE_LIMITED, retryAfterSec = 1)
        assertEquals(ChannelSearchUiAction.RATE_LIMITED, out.uiAction())
    }

    @Test
    fun uiAction_disabled_disables_feature() {
        val out = ChannelSearchOutcome.failure<String>(503, SearchErrorCode.DISABLED)
        assertEquals(ChannelSearchUiAction.FEATURE_DISABLED, out.uiAction())
    }

    @Test
    fun uiAction_not_found_blocks() {
        val out = ChannelSearchOutcome.failure<String>(404, SearchErrorCode.NOT_FOUND)
        assertEquals(ChannelSearchUiAction.BLOCK_NOT_FOUND, out.uiAction())
    }

    @Test
    fun uiAction_validation_failed_is_validation_error() {
        val out = ChannelSearchOutcome.failure<String>(400, SearchErrorCode.VALIDATION_FAILED)
        assertEquals(ChannelSearchUiAction.VALIDATION_ERROR, out.uiAction())
    }

    @Test
    fun uiAction_depth_exceeded_classified_as_validation() {
        val out = ChannelSearchOutcome.failure<String>(400, SearchErrorCode.DEPTH_EXCEEDED)
        assertEquals(ChannelSearchUiAction.VALIDATION_ERROR, out.uiAction())
    }

    @Test
    fun uiAction_internal_falls_to_generic() {
        val out = ChannelSearchOutcome.failure<String>(500, SearchErrorCode.INTERNAL)
        assertEquals(ChannelSearchUiAction.GENERIC_ERROR, out.uiAction())
    }
}
