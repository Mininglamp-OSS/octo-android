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

import com.chat.base.search.channel.ChannelSearchOutcome
import com.chat.base.search.channel.ChannelSearchUiAction
import com.chat.base.search.channel.dto.Pagination
import com.chat.base.search.channel.dto.SearchErrorCode
import com.chat.base.search.global.dto.GroupBucket
import com.chat.base.search.global.dto.GroupsResult
import com.chat.base.search.global.dto.SearchGlobalGroupsReq
import com.chat.base.search.global.dto.SearchGlobalGroupsResp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * StandardTestDispatcher 语义：
 *  - `advanceTimeBy(N)` 只推进虚拟时间，不执行到期任务；到期任务需 `runCurrent()` 显式 dispatch。
 *  - `advanceUntilIdle()` = 推进 + 反复执行直到无任务。
 * 本文用 [tick] 封装 "advance + runCurrent" 组合，语义等同"等待 debounce 到期"。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GlobalSearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.tick(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private class RecordingCaller : GlobalSearchViewModel.SearchCaller {
        data class Pending(
            val req: SearchGlobalGroupsReq,
            val callback: (ChannelSearchOutcome<SearchGlobalGroupsResp>) -> Unit,
        )

        val pending = mutableListOf<Pending>()

        override fun call(
            req: SearchGlobalGroupsReq,
            callback: (ChannelSearchOutcome<SearchGlobalGroupsResp>) -> Unit,
        ) {
            pending += Pending(req, callback)
        }

        fun completeAt(index: Int, resp: SearchGlobalGroupsResp) {
            pending[index].callback(ChannelSearchOutcome.success(resp))
        }

        fun failAt(
            index: Int,
            errorCode: String,
            httpStatus: Int = 500,
            retryAfterSec: Int = 0,
        ) {
            pending[index].callback(
                ChannelSearchOutcome.failure(
                    httpStatus = httpStatus,
                    errorCode = errorCode,
                    retryAfterSec = retryAfterSec,
                ),
            )
        }
    }

    private fun resp(
        seq: Long,
        groups: List<GroupBucket> = emptyList(),
        hasMore: Boolean = false,
    ): SearchGlobalGroupsResp {
        val r = SearchGlobalGroupsResp()
        r.data = GroupsResult().apply {
            sequence = seq
            query_id = "q$seq"
            total_groups = groups.size.toLong()
            total_groups_approx = true
            this.groups = groups
        }
        r.pagination = Pagination().apply {
            has_more = hasMore
            next_cursor = ""
        }
        return r
    }

    private fun bucket(channelId: String, matchCount: Long = 1L): GroupBucket =
        GroupBucket().apply {
            channel_id = channelId
            channel_type = 2
            group_name = "grp-$channelId"
            match_count = matchCount
            match_count_approx = true
            latest_at = "2026-07-14T10:00:00+08:00"
        }

    @Test
    fun `initial state is empty`() = runTest {
        val vm = GlobalSearchViewModel(RecordingCaller())
        val s = vm.state.value
        assertEquals("", s.keyword)
        assertFalse(s.isLoading)
        assertTrue(s.groups.isEmpty())
        assertNull(s.errorCode)
        assertEquals(0L, s.sequence)
    }

    @Test
    fun `keyword change triggers request only after debounce`() = runTest {
        val caller = RecordingCaller()
        val vm = GlobalSearchViewModel(caller, debounceMillis = 300L)

        vm.setKeyword("hello")
        assertEquals("hello", vm.state.value.keyword)
        assertTrue("debounce 未过前不应触发请求", caller.pending.isEmpty())

        tick(299L)
        assertTrue("到 299ms 时仍不应触发", caller.pending.isEmpty())

        tick(1L)
        assertEquals("到 300ms 应触发一次", 1, caller.pending.size)
        assertEquals("hello", caller.pending[0].req.keyword)
        assertEquals(1L, caller.pending[0].req.sequence)
    }

    @Test
    fun `rapid keyword changes cancel earlier debounce and only send latest`() = runTest {
        val caller = RecordingCaller()
        val vm = GlobalSearchViewModel(caller, debounceMillis = 300L)

        vm.setKeyword("h"); tick(100L)
        vm.setKeyword("he"); tick(100L)
        vm.setKeyword("hel"); tick(100L)
        vm.setKeyword("hell"); tick(100L)
        vm.setKeyword("hello"); tick(299L)
        assertTrue(caller.pending.isEmpty())
        tick(1L)
        assertEquals("多次快速输入只应触发最后一次", 1, caller.pending.size)
        assertEquals("hello", caller.pending[0].req.keyword)
    }

    @Test
    fun `empty keyword clears state and cancels pending debounce`() = runTest {
        val caller = RecordingCaller()
        val vm = GlobalSearchViewModel(caller, debounceMillis = 300L)

        vm.setKeyword("hello"); tick(300L)
        caller.completeAt(0, resp(seq = 1L, groups = listOf(bucket("g1"))))
        advanceUntilIdle()
        assertEquals(1, vm.state.value.groups.size)

        vm.setKeyword("")
        assertEquals("", vm.state.value.keyword)
        assertTrue("清空 keyword 后 groups 应清空", vm.state.value.groups.isEmpty())
        assertFalse(vm.state.value.isLoading)

        // 后续再快速输入然后立刻清空，都不应再触发
        vm.setKeyword("x")
        vm.setKeyword("")
        tick(500L)
        assertEquals("清空后无新输入不应发请求", 1, caller.pending.size)
    }

    @Test
    fun `sequence increments per triggered request and echoes back`() = runTest {
        val caller = RecordingCaller()
        val vm = GlobalSearchViewModel(caller, debounceMillis = 300L)

        vm.setKeyword("a"); tick(300L)
        assertEquals(1L, caller.pending[0].req.sequence)

        caller.completeAt(0, resp(seq = 1L))
        advanceUntilIdle()

        vm.setKeyword("ab"); tick(300L)
        assertEquals(2L, caller.pending[1].req.sequence)
    }

    @Test
    fun `stale response with older sequence is discarded`() = runTest {
        val caller = RecordingCaller()
        val vm = GlobalSearchViewModel(caller, debounceMillis = 300L)

        vm.setKeyword("a"); tick(300L)
        vm.setKeyword("ab"); tick(300L)

        // 服务端先返回 seq=2（新），再返回 seq=1（旧）
        caller.completeAt(1, resp(seq = 2L, groups = listOf(bucket("g_new"))))
        advanceUntilIdle()
        assertEquals(listOf("g_new"), vm.state.value.groups.map { it.channel_id })

        caller.completeAt(0, resp(seq = 1L, groups = listOf(bucket("g_old"))))
        advanceUntilIdle()
        assertEquals(
            "过期响应必须丢弃，state 保持最新",
            listOf("g_new"),
            vm.state.value.groups.map { it.channel_id },
        )
    }

    @Test
    fun `response with mismatched echo sequence is discarded`() = runTest {
        val caller = RecordingCaller()
        val vm = GlobalSearchViewModel(caller, debounceMillis = 300L)

        vm.setKeyword("hello"); tick(300L)
        // 请求 seq=1，但服务端 echo 回 seq=99（防御性检查）
        caller.completeAt(0, resp(seq = 99L, groups = listOf(bucket("bogus"))))
        advanceUntilIdle()
        assertTrue("echo 不匹配的响应必须丢弃", vm.state.value.groups.isEmpty())
    }

    @Test
    fun `success response populates groups and pagination`() = runTest {
        val caller = RecordingCaller()
        val vm = GlobalSearchViewModel(caller, debounceMillis = 300L)

        vm.setKeyword("hello"); tick(300L)
        assertTrue(vm.state.value.isLoading)

        caller.completeAt(
            0,
            resp(
                seq = 1L,
                groups = listOf(bucket("g1", 12), bucket("g2", 3)),
                hasMore = true,
            ),
        )
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.isLoading)
        assertEquals(2, s.groups.size)
        assertEquals("g1", s.groups[0].channel_id)
        assertEquals(2L, s.totalGroups)
        assertTrue(s.totalGroupsApprox)
        assertTrue(s.hasMore)
        assertNull(s.errorCode)
    }

    @Test
    fun `failure sets errorCode and clears groups`() = runTest {
        val caller = RecordingCaller()
        val vm = GlobalSearchViewModel(caller, debounceMillis = 300L)

        vm.setKeyword("hello"); tick(300L)
        caller.completeAt(0, resp(seq = 1L, groups = listOf(bucket("g1"))))
        advanceUntilIdle()
        assertEquals(1, vm.state.value.groups.size)

        vm.setKeyword("world"); tick(300L)
        caller.failAt(1, errorCode = SearchErrorCode.UPSTREAM_UNAVAILABLE, httpStatus = 503)
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.isLoading)
        assertTrue("失败后 groups 应清空", s.groups.isEmpty())
        assertEquals(SearchErrorCode.UPSTREAM_UNAVAILABLE, s.errorCode)
        assertEquals(ChannelSearchUiAction.FALLBACK_TO_LOCAL, s.uiAction())
    }

    @Test
    fun `rate limited failure carries retry_after`() = runTest {
        val caller = RecordingCaller()
        val vm = GlobalSearchViewModel(caller, debounceMillis = 300L)

        vm.setKeyword("hello"); tick(300L)
        caller.failAt(
            0,
            errorCode = SearchErrorCode.RATE_LIMITED,
            httpStatus = 429,
            retryAfterSec = 5,
        )
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(SearchErrorCode.RATE_LIMITED, s.errorCode)
        assertEquals(5, s.retryAfterSec)
        assertEquals(ChannelSearchUiAction.RATE_LIMITED, s.uiAction())
    }

    @Test
    fun `triggerNow skips debounce`() = runTest {
        val caller = RecordingCaller()
        val vm = GlobalSearchViewModel(caller, debounceMillis = 300L)

        vm.setKeyword("hello")
        vm.triggerNow()
        advanceUntilIdle()
        assertEquals(1, caller.pending.size)
        assertEquals("hello", caller.pending[0].req.keyword)
    }

    @Test
    fun `triggerNow no-op when keyword empty`() = runTest {
        val caller = RecordingCaller()
        val vm = GlobalSearchViewModel(caller, debounceMillis = 300L)
        vm.triggerNow()
        advanceUntilIdle()
        assertTrue(caller.pending.isEmpty())
    }

    @Test
    fun `reset cancels debounce and clears state`() = runTest {
        val caller = RecordingCaller()
        val vm = GlobalSearchViewModel(caller, debounceMillis = 300L)

        vm.setKeyword("hello")
        vm.reset()
        assertEquals("", vm.state.value.keyword)
        tick(500L)
        assertTrue("reset 后原 debounce 不应触发", caller.pending.isEmpty())
    }

    @Test
    fun `response after keyword cleared is discarded`() = runTest {
        val caller = RecordingCaller()
        val vm = GlobalSearchViewModel(caller, debounceMillis = 300L)

        vm.setKeyword("hello"); tick(300L)   // seq=1 pending
        vm.setKeyword("")                    // 用户清空
        caller.completeAt(0, resp(seq = 1L, groups = listOf(bucket("g_late"))))
        advanceUntilIdle()
        assertTrue("keyword 已清空时迟到响应应丢弃", vm.state.value.groups.isEmpty())
    }
}
