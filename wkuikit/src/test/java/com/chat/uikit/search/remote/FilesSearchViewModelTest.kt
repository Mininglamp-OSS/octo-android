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
import com.chat.base.search.channel.dto.CursorList
import com.chat.base.search.channel.dto.FileHit
import com.chat.base.search.channel.dto.Pagination
import com.chat.base.search.channel.dto.SearchErrorCode
import com.chat.base.search.global.dto.GlobalSearchFilters
import com.chat.base.search.global.dto.SearchGlobalFilesReq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
 * [FilesSearchViewModel] 的分页、浏览态与 loading 态测试。
 *
 * `tick` 封装 "advanceTimeBy + runCurrent"，语义等同"等待 debounce 到期"
 * （StandardTestDispatcher 下 advanceTimeBy 只推进虚拟时间，不 dispatch 到期任务）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FilesSearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.tick(ms: Long = FilesSearchViewModel.DEBOUNCE_MS) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private class RecordingCaller : FilesSearchViewModel.SearchCaller {
        data class Pending(
            val req: SearchGlobalFilesReq,
            val callback: (ChannelSearchOutcome<CursorList<FileHit>>) -> Unit,
        )

        val pending = mutableListOf<Pending>()

        override fun call(
            req: SearchGlobalFilesReq,
            callback: (ChannelSearchOutcome<CursorList<FileHit>>) -> Unit,
        ) {
            pending += Pending(req, callback)
        }

        fun completeAt(index: Int, page: CursorList<FileHit>) {
            pending[index].callback(ChannelSearchOutcome.success(page))
        }

        fun failAt(index: Int, errorCode: String = SearchErrorCode.INTERNAL) {
            pending[index].callback(
                ChannelSearchOutcome.failure(httpStatus = 500, errorCode = errorCode),
            )
        }
    }

    private fun hit(name: String): FileHit = FileHit().apply {
        message_id = name
        file_name = name
        channel_id = "c1"
        channel_type = 2
        sent_at = "2026-06-01T00:00:00Z"
    }

    private fun page(
        files: List<FileHit>,
        hasMore: Boolean = false,
        nextCursor: String = "",
    ): CursorList<FileHit> = CursorList<FileHit>().apply {
        data = files
        pagination = Pagination().apply {
            has_more = hasMore
            next_cursor = nextCursor
        }
    }

    // ---------- B-1：cursor 分页 ----------

    @Test
    fun `preserves next_cursor from first page`() = runTest(dispatcher) {
        val caller = RecordingCaller()
        val vm = FilesSearchViewModel(caller)

        vm.setKeyword("报告")
        tick()
        caller.completeAt(0, page(listOf(hit("a")), hasMore = true, nextCursor = "cur1"))

        assertEquals("cur1", vm.state.value.nextCursor)
        assertTrue(vm.state.value.hasMore)
    }

    @Test
    fun `loadMore appends the next page and carries the cursor`() = runTest(dispatcher) {
        val caller = RecordingCaller()
        val vm = FilesSearchViewModel(caller)

        vm.setKeyword("报告")
        tick()
        caller.completeAt(0, page(listOf(hit("a")), hasMore = true, nextCursor = "cur1"))

        vm.loadMore()
        assertEquals("翻页请求须回传上一页游标", "cur1", caller.pending[1].req.cursor)
        caller.completeAt(1, page(listOf(hit("b")), hasMore = false, nextCursor = ""))

        assertEquals(
            "第二页须追加而非替换",
            listOf("a", "b"),
            vm.state.value.files.map { it.file_name },
        )
        assertFalse(vm.state.value.hasMore)
        assertNull(vm.state.value.nextCursor)
    }

    @Test
    fun `loadMore is a no-op without hasMore`() = runTest(dispatcher) {
        val caller = RecordingCaller()
        val vm = FilesSearchViewModel(caller)

        vm.setKeyword("报告")
        tick()
        caller.completeAt(0, page(listOf(hit("a")), hasMore = false, nextCursor = ""))

        vm.loadMore()
        assertEquals("没有下一页时不应发请求", 1, caller.pending.size)
    }

    @Test
    fun `loadMore does not stack concurrent requests`() = runTest(dispatcher) {
        val caller = RecordingCaller()
        val vm = FilesSearchViewModel(caller)

        vm.setKeyword("报告")
        tick()
        caller.completeAt(0, page(listOf(hit("a")), hasMore = true, nextCursor = "cur1"))

        vm.loadMore()
        vm.loadMore()
        assertEquals("翻页在途时不应重复发起", 2, caller.pending.size)
    }

    /** 服务端回传同一游标时必须停止翻页，否则会无限拉同一页。 */
    @Test
    fun `stalled cursor stops paging`() = runTest(dispatcher) {
        val caller = RecordingCaller()
        val vm = FilesSearchViewModel(caller)

        vm.setKeyword("报告")
        tick()
        caller.completeAt(0, page(listOf(hit("a")), hasMore = true, nextCursor = "cur1"))
        vm.loadMore()
        caller.completeAt(1, page(listOf(hit("b")), hasMore = true, nextCursor = "cur1"))

        assertFalse("游标停滞须强制停止翻页", vm.state.value.hasMore)
        assertNull(vm.state.value.nextCursor)
    }

    @Test
    fun `new keyword resets pagination`() = runTest(dispatcher) {
        val caller = RecordingCaller()
        val vm = FilesSearchViewModel(caller)

        vm.setKeyword("报告")
        tick()
        caller.completeAt(0, page(listOf(hit("a")), hasMore = true, nextCursor = "cur1"))

        vm.setKeyword("方案")
        assertTrue("换词后旧结果须立即清空", vm.state.value.files.isEmpty())
        assertNull("换词后游标须重置", vm.state.value.nextCursor)
        assertFalse(vm.state.value.hasMore)

        tick()
        assertNull("新一轮首屏请求不带游标", caller.pending[1].req.cursor)
    }

    @Test
    fun `filter change resets pagination`() = runTest(dispatcher) {
        val caller = RecordingCaller()
        val vm = FilesSearchViewModel(caller)

        vm.setKeyword("报告")
        tick()
        caller.completeAt(0, page(listOf(hit("a")), hasMore = true, nextCursor = "cur1"))

        vm.setFilters(GlobalSearchFilters(fileExts = listOf("pdf")))
        vm.triggerNow()

        assertNull("应用筛选后游标须重置", vm.state.value.nextCursor)
        assertNull("重新搜索不带游标", caller.pending[1].req.cursor)
        assertEquals(listOf("pdf"), caller.pending[1].req.filters.fileExts)
    }

    /** 翻页失败只停止继续翻页，不应丢掉已加载的页。 */
    @Test
    fun `failed loadMore keeps already loaded pages`() = runTest(dispatcher) {
        val caller = RecordingCaller()
        val vm = FilesSearchViewModel(caller)

        vm.setKeyword("报告")
        tick()
        caller.completeAt(0, page(listOf(hit("a")), hasMore = true, nextCursor = "cur1"))
        vm.loadMore()
        caller.failAt(1)

        assertEquals(listOf("a"), vm.state.value.files.map { it.file_name })
        assertFalse(vm.state.value.hasMore)
    }

    // ---------- 无关键词不搜索 ----------

    /** 产品决定：不输入关键词不返回任何结果，筛选条件不能单独作为查询条件。 */
    @Test
    fun `applying filters with empty keyword issues no request`() = runTest(dispatcher) {
        val caller = RecordingCaller()
        val vm = FilesSearchViewModel(caller)

        vm.setFilters(GlobalSearchFilters(fileExts = listOf("pdf")))
        vm.triggerNow()

        assertEquals("无关键词时不应发起请求", 0, caller.pending.size)
        assertFalse("未发起请求 → 仍是空输入态", vm.state.value.queryStarted)
        assertTrue(vm.state.value.files.isEmpty())
    }

    @Test
    fun `clearing keyword stops searching even with filters active`() = runTest(dispatcher) {
        val caller = RecordingCaller()
        val vm = FilesSearchViewModel(caller)

        vm.setFilters(GlobalSearchFilters(fileExts = listOf("pdf")))
        vm.setKeyword("报告")
        tick()
        caller.completeAt(0, page(listOf(hit("a"))))

        vm.setKeyword("")
        tick()

        assertEquals("清空关键词后不应再发请求", 1, caller.pending.size)
        assertTrue("结果须清空", vm.state.value.files.isEmpty())
        assertFalse("回到空输入态", vm.state.value.queryStarted)
    }

    @Test
    fun `no keyword and no filters does not search`() = runTest(dispatcher) {
        val caller = RecordingCaller()
        val vm = FilesSearchViewModel(caller)

        vm.triggerNow()
        assertEquals(0, caller.pending.size)
        assertFalse(vm.state.value.queryStarted)
    }

    /** 筛选条件仍会随关键词搜索一起下发，只是不能单独触发。 */
    @Test
    fun `filters are sent along with the keyword`() = runTest(dispatcher) {
        val caller = RecordingCaller()
        val vm = FilesSearchViewModel(caller)

        vm.setFilters(GlobalSearchFilters(fileExts = listOf("pdf")))
        vm.setKeyword("报告")
        tick()

        assertEquals(1, caller.pending.size)
        assertEquals("报告", caller.pending[0].req.keyword)
        assertEquals(listOf("pdf"), caller.pending[0].req.filters.fileExts)
    }

    // ---------- B-5：loading 态 ----------

    /**
     * 第二次及以后的搜索在途期间，必须仍是 loading 态。
     * 旧实现用 sticky 的 `*Loaded` 标记，第二轮永远停在 "已加载"，UI 会误报"无搜索结果"。
     */
    @Test
    fun `repeat search stays loading while in flight`() = runTest(dispatcher) {
        val caller = RecordingCaller()
        val vm = FilesSearchViewModel(caller)

        vm.setKeyword("报告")
        tick()
        caller.completeAt(0, page(listOf(hit("a"))))
        assertFalse("首轮完成后不再 loading", vm.state.value.isLoading)

        vm.setKeyword("方案")
        assertTrue("换词后立即进入 loading", vm.state.value.isLoading)
        assertTrue("旧结果须已清空", vm.state.value.files.isEmpty())

        tick()
        assertTrue("请求在途期间仍是 loading", vm.state.value.isLoading)

        caller.completeAt(1, page(emptyList()))
        assertFalse("响应落地后退出 loading", vm.state.value.isLoading)
        assertTrue("此时才是真正的无结果", vm.state.value.files.isEmpty())
    }

    /** debounce 窗口内不应渲染上一轮关键词的结果。 */
    @Test
    fun `old results cleared during debounce window`() = runTest(dispatcher) {
        val caller = RecordingCaller()
        val vm = FilesSearchViewModel(caller)

        vm.setKeyword("报告")
        tick()
        caller.completeAt(0, page(listOf(hit("a"))))

        vm.setKeyword("方案")
        // 还没到 debounce 到期
        tick(FilesSearchViewModel.DEBOUNCE_MS / 2)
        assertTrue("debounce 期间不得显示旧关键词的结果", vm.state.value.files.isEmpty())
    }

    @Test
    fun `stale response for previous keyword is discarded`() = runTest(dispatcher) {
        val caller = RecordingCaller()
        val vm = FilesSearchViewModel(caller)

        vm.setKeyword("报告")
        tick()
        vm.setKeyword("方案")
        tick()

        // 第一轮的响应迟到
        caller.completeAt(0, page(listOf(hit("stale"))))
        assertTrue("过期响应须丢弃", vm.state.value.files.isEmpty())

        caller.completeAt(1, page(listOf(hit("fresh"))))
        assertEquals(listOf("fresh"), vm.state.value.files.map { it.file_name })
    }
}
