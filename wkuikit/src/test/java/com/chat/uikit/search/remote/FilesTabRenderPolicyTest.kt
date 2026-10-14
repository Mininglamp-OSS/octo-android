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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 渲染层回归：翻页失败时已加载的页必须留在屏上。
 *
 * 这一层正是此前缺陷的真正所在——ViewModel 已经保留了结果，
 * 但 collector 用 `if (action == null) state.files else emptyList()` 又把它清掉了，
 * 而当时的 ViewModel 层单测只断言 state.files，测试全绿缺陷照旧。
 */
class FilesTabRenderPolicyTest {

    /** 核心回归：翻页失败（有结果 + 有错误）必须照常渲染，且不得整屏接管。 */
    @Test
    fun `append failure keeps rendering loaded pages`() {
        val d = FilesTabRenderPolicy.decide(ChannelSearchUiAction.FALLBACK_TO_LOCAL, fileCount = 20)

        assertEquals("已加载的 20 条必须留在屏上", 20, d.itemCount)
        assertFalse("不得整屏接管为加载失败", d.takeOverWithError)
        assertTrue("错误须通过 toast 告知", d.toastError)
    }

    /** 每一种错误码在"已有结果"时都不得清屏。 */
    @Test
    fun `no error code clears the list when results exist`() {
        val codes = listOf(
            ChannelSearchUiAction.FALLBACK_TO_LOCAL,
            ChannelSearchUiAction.RATE_LIMITED,
            ChannelSearchUiAction.FEATURE_DISABLED,
            ChannelSearchUiAction.BLOCK_NOT_FOUND,
            ChannelSearchUiAction.VALIDATION_ERROR,
            ChannelSearchUiAction.GENERIC_ERROR,
        )
        for (action in codes) {
            val d = FilesTabRenderPolicy.decide(action, fileCount = 20)
            assertEquals("$action 不应清空已加载的结果", 20, d.itemCount)
            assertFalse("$action 不应整屏接管", d.takeOverWithError)
        }
    }

    /** 首屏失败（无结果 + 有错误）才整屏接管，且不重复弹 toast。 */
    @Test
    fun `first page failure takes over the pane`() {
        val d = FilesTabRenderPolicy.decide(ChannelSearchUiAction.GENERIC_ERROR, fileCount = 0)

        assertEquals(0, d.itemCount)
        assertTrue("首屏失败应整屏展示加载失败 + 重试", d.takeOverWithError)
        assertFalse("空态已经说明了错误，不必再 toast", d.toastError)
    }

    @Test
    fun `success renders results without error affordances`() {
        val d = FilesTabRenderPolicy.decide(null, fileCount = 20)

        assertEquals(20, d.itemCount)
        assertFalse(d.takeOverWithError)
        assertFalse(d.toastError)
    }

    /** 成功但确实没有结果：交给空态走「无搜索结果」，不是错误态。 */
    @Test
    fun `empty success is not an error`() {
        val d = FilesTabRenderPolicy.decide(null, fileCount = 0)

        assertEquals(0, d.itemCount)
        assertFalse("无结果不等于加载失败", d.takeOverWithError)
        assertFalse(d.toastError)
    }
}
