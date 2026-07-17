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

package com.chat.uikit.search.remote.drill

import com.chat.base.search.channel.ChannelSearchOutcome
import com.chat.base.search.channel.ChannelSearchUiAction
import com.chat.base.search.channel.dto.CombinedHit
import com.chat.base.search.channel.dto.CursorList
import com.chat.base.search.channel.dto.FileHit
import com.chat.base.search.channel.dto.MessageHit
import com.chat.base.search.channel.dto.Pagination
import com.chat.base.search.channel.dto.SearchErrorCode
import com.chat.base.search.global.dto.SearchGlobalMessagesReq
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

@OptIn(ExperimentalCoroutinesApi::class)
class GroupDrillViewModelTest {

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

    private class RecordingCaller : GroupDrillViewModel.SearchCaller {
        data class Pending(
            val req: SearchGlobalMessagesReq,
            val callback: (ChannelSearchOutcome<CursorList<CombinedHit>>) -> Unit,
        )

        val pending = mutableListOf<Pending>()

        override fun call(
            req: SearchGlobalMessagesReq,
            callback: (ChannelSearchOutcome<CursorList<CombinedHit>>) -> Unit,
        ) {
            pending += Pending(req, callback)
        }

        fun completeAt(index: Int, resp: CursorList<CombinedHit>) {
            pending[index].callback(ChannelSearchOutcome.success(resp))
        }

        fun failAt(index: Int, code: String, httpStatus: Int = 500, retryAfterSec: Int = 0) {
            pending[index].callback(
                ChannelSearchOutcome.failure(
                    httpStatus = httpStatus,
                    errorCode = code,
                    retryAfterSec = retryAfterSec,
                ),
            )
        }
    }

    private fun messageHit(id: String): CombinedHit {
        val m = MessageHit().apply {
            message_id = id
            message_seq = 1L
            message_kind = "text"
            snippet = "s"
            sender_id = "u1"
            sent_at = "2026-07-01T00:00:00Z"
            channel_id = "g_xxx"
            channel_type = 2
        }
        return CombinedHit().apply {
            result_type = CombinedHit.TYPE_MESSAGE
            sorted_at = "2026-07-01T00:00:00Z"
            message = m
        }
    }

    private fun fileHit(id: String): CombinedHit {
        val f = FileHit().apply {
            message_id = id
            message_seq = 1L
            file_name = "$id.pdf"
            file_ext = "pdf"
            sender_id = "u1"
            sent_at = "2026-07-01T00:00:00Z"
            channel_id = "g_xxx"
            channel_type = 2
        }
        return CombinedHit().apply {
            result_type = CombinedHit.TYPE_FILE
            sorted_at = "2026-07-01T00:00:00Z"
            file = f
        }
    }

    private fun resp(
        hits: List<CombinedHit>,
        hasMore: Boolean = false,
        nextCursor: String = "",
    ): CursorList<CombinedHit> {
        val cursor = CursorList<CombinedHit>()
        cursor.data = hits
        cursor.pagination = Pagination().apply {
            has_more = hasMore
            next_cursor = nextCursor
        }
        return cursor
    }

    @Test
    fun `init with keyword triggers immediate first page (skip debounce)`() = runTest {
        val caller = RecordingCaller()
        val vm = GroupDrillViewModel(caller, debounceMillis = 300L)

        vm.init(channelId = "g_xxx", channelType = 2, initialKeyword = "hello")
        advanceUntilIdle()
        assertEquals("init 应立即触发首屏", 1, caller.pending.size)
        assertEquals("hello", caller.pending[0].req.keyword)
        assertEquals(1L, caller.pending[0].req.sequence)
        assertNull("首屏 cursor 应为 null", caller.pending[0].req.cursor)

        val body = caller.pending[0].req.filters.channelIds!![0]
        assertEquals("g_xxx", body.channelId)
        assertEquals(2, body.channelType.toInt())
    }

    @Test
    fun `init with empty keyword does not fire`() = runTest {
        val caller = RecordingCaller()
        val vm = GroupDrillViewModel(caller)
        vm.init("g_xxx", 2, "")
        advanceUntilIdle()
        assertTrue(caller.pending.isEmpty())
        assertEquals("", vm.state.value.keyword)
    }

    @Test
    fun `success response replaces hits and stores cursor`() = runTest {
        val caller = RecordingCaller()
        val vm = GroupDrillViewModel(caller)
        vm.init("g_xxx", 2, "hello")

        caller.completeAt(0, resp(listOf(messageHit("m1"), fileHit("f1")), hasMore = true, nextCursor = "cur1"))
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.isLoading)
        assertEquals(2, s.hits.size)
        assertTrue(s.hasMore)
        assertEquals("cur1", s.nextCursor)
        assertNull(s.errorCode)
    }

    @Test
    fun `loadMore appends hits and reuses sequence`() = runTest {
        val caller = RecordingCaller()
        val vm = GroupDrillViewModel(caller)
        vm.init("g_xxx", 2, "hello")

        caller.completeAt(0, resp(listOf(messageHit("m1")), hasMore = true, nextCursor = "cur1"))
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()
        assertEquals(2, caller.pending.size)
        assertEquals("loadMore 应复用 sequence，不递增", 1L, caller.pending[1].req.sequence)
        assertEquals("cur1", caller.pending[1].req.cursor)

        caller.completeAt(1, resp(listOf(messageHit("m2"), messageHit("m3")), hasMore = false, nextCursor = ""))
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("loadMore 应追加而不是替换", 3, s.hits.size)
        assertFalse(s.hasMore)
        assertNull(s.nextCursor)
    }

    @Test
    fun `loadMore skipped when hasMore false`() = runTest {
        val caller = RecordingCaller()
        val vm = GroupDrillViewModel(caller)
        vm.init("g_xxx", 2, "hello")
        caller.completeAt(0, resp(listOf(messageHit("m1")), hasMore = false))
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()
        assertEquals("hasMore=false 时 loadMore 应 no-op", 1, caller.pending.size)
    }

    @Test
    fun `keyword change resets cursor and increments sequence`() = runTest {
        val caller = RecordingCaller()
        val vm = GroupDrillViewModel(caller, debounceMillis = 300L)
        vm.init("g_xxx", 2, "hello")
        caller.completeAt(0, resp(listOf(messageHit("m1")), hasMore = true, nextCursor = "cur1"))
        advanceUntilIdle()

        vm.setKeyword("world"); tick(300L)
        assertEquals(2, caller.pending.size)
        assertEquals("新 keyword 应递增 sequence", 2L, caller.pending[1].req.sequence)
        assertNull("新 keyword 应重置 cursor", caller.pending[1].req.cursor)
    }

    @Test
    fun `stale loadMore response after keyword change is discarded`() = runTest {
        val caller = RecordingCaller()
        val vm = GroupDrillViewModel(caller, debounceMillis = 300L)
        vm.init("g_xxx", 2, "hello")
        caller.completeAt(0, resp(listOf(messageHit("m1")), hasMore = true, nextCursor = "cur1"))
        advanceUntilIdle()

        vm.loadMore()                            // seq=1 pending (loadMore)
        assertEquals(2, caller.pending.size)

        vm.setKeyword("world"); tick(300L)       // seq=2 pending (new search)
        assertEquals(3, caller.pending.size)

        // 迟到的 loadMore (seq=1) 到达 → 必须丢弃
        caller.completeAt(1, resp(listOf(messageHit("m_stale"))))
        advanceUntilIdle()
        assertFalse(
            "过期 loadMore 响应不应污染新 keyword 的 state",
            vm.state.value.hits.any { it.message?.message_id == "m_stale" },
        )

        // 新的 reset (seq=2) 响应
        caller.completeAt(2, resp(listOf(messageHit("m_new"))))
        advanceUntilIdle()
        assertEquals(1, vm.state.value.hits.size)
        assertEquals("m_new", vm.state.value.hits[0].message?.message_id)
    }

    @Test
    fun `empty keyword clears state and cancels debounce`() = runTest {
        val caller = RecordingCaller()
        val vm = GroupDrillViewModel(caller, debounceMillis = 300L)
        vm.init("g_xxx", 2, "hello")
        caller.completeAt(0, resp(listOf(messageHit("m1"))))
        advanceUntilIdle()
        assertEquals(1, vm.state.value.hits.size)

        vm.setKeyword("")
        assertTrue(vm.state.value.hits.isEmpty())
        assertEquals("", vm.state.value.keyword)
    }

    @Test
    fun `reset request failure clears hits`() = runTest {
        val caller = RecordingCaller()
        val vm = GroupDrillViewModel(caller, debounceMillis = 300L)
        vm.init("g_xxx", 2, "hello")
        caller.completeAt(0, resp(listOf(messageHit("m1"), messageHit("m2"))))
        advanceUntilIdle()

        vm.setKeyword("world"); tick(300L)
        caller.failAt(1, SearchErrorCode.UPSTREAM_UNAVAILABLE, httpStatus = 503)
        advanceUntilIdle()

        val s = vm.state.value
        assertTrue("reset 失败应清空旧 hits", s.hits.isEmpty())
        assertEquals(SearchErrorCode.UPSTREAM_UNAVAILABLE, s.errorCode)
        assertEquals(ChannelSearchUiAction.FALLBACK_TO_LOCAL, s.uiAction())
    }

    @Test
    fun `loadMore failure preserves already-loaded hits`() = runTest {
        val caller = RecordingCaller()
        val vm = GroupDrillViewModel(caller)
        vm.init("g_xxx", 2, "hello")
        caller.completeAt(0, resp(listOf(messageHit("m1")), hasMore = true, nextCursor = "cur1"))
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()
        caller.failAt(1, SearchErrorCode.UPSTREAM_UNAVAILABLE, httpStatus = 503)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("loadMore 失败不应清空已加载", 1, s.hits.size)
        assertEquals("m1", s.hits[0].message?.message_id)
        assertEquals(SearchErrorCode.UPSTREAM_UNAVAILABLE, s.errorCode)
    }

    @Test
    fun `rate limited surfaces retry_after in state`() = runTest {
        val caller = RecordingCaller()
        val vm = GroupDrillViewModel(caller)
        vm.init("g_xxx", 2, "hello")
        caller.failAt(0, SearchErrorCode.RATE_LIMITED, httpStatus = 429, retryAfterSec = 7)
        advanceUntilIdle()
        assertEquals(7, vm.state.value.retryAfterSec)
        assertEquals(ChannelSearchUiAction.RATE_LIMITED, vm.state.value.uiAction())
    }
}
