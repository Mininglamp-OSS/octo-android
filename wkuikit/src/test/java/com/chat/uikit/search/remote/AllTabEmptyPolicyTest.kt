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

import org.junit.Assert.assertEquals
import org.junit.Test

class AllTabEmptyPolicyTest {

    private fun leg(settled: Boolean, isEmpty: Boolean, errored: Boolean = false) =
        AllTabEmptyPolicy.Leg(settled, isEmpty, errored)

    private val pending = leg(settled = false, isEmpty = true)
    private val settledEmpty = leg(settled = true, isEmpty = true)
    private val settledHit = leg(settled = true, isEmpty = false)
    private val failed = leg(settled = true, isEmpty = true, errored = true)

    @Test
    fun `empty keyword shows prompt`() {
        assertEquals(
            "空关键词应优先显示输入提示",
            AllTabEmptyPolicy.Verdict.EMPTY_KEYWORD,
            AllTabEmptyPolicy.decide(true, failed, failed, failed),
        )
    }

    @Test
    fun `all legs pending shows loading`() {
        assertEquals(
            "三腿都在途应显示搜索中",
            AllTabEmptyPolicy.Verdict.LOADING,
            AllTabEmptyPolicy.decide(false, pending, pending, pending),
        )
    }

    @Test
    fun `files still in flight does not report no result`() {
        // P1-1 回归：文件腿还没落地时绝不能报「无搜索结果」（旧 sticky 实现误报）。
        assertEquals(
            "文件腿在途时应继续转圈而非误报无结果",
            AllTabEmptyPolicy.Verdict.LOADING,
            AllTabEmptyPolicy.decide(false, settledEmpty, settledEmpty, pending),
        )
    }

    @Test
    fun `filters rerun keeps loading instead of no result`() {
        // P1-1 的 reapplyFilters 变体：这正是「第二轮搜索请求在途」的状态。
        assertEquals(
            "第二轮搜索请求在途时应继续转圈",
            AllTabEmptyPolicy.Verdict.LOADING,
            AllTabEmptyPolicy.decide(false, settledEmpty, settledEmpty, pending),
        )
    }

    @Test
    fun `files error with other legs empty shows partial failure`() {
        // I4 回归：旧实现在此落到 else → 空态隐藏 → 白板。
        val verdict = AllTabEmptyPolicy.decide(false, settledEmpty, settledEmpty, failed)
        assertEquals(
            "文件腿报错且其余腿为空应显示部分失败",
            AllTabEmptyPolicy.Verdict.PARTIAL_FAILURE,
            verdict,
        )
        assertEquals("不得误判为隐藏空态（白板）", false, verdict == AllTabEmptyPolicy.Verdict.HIDE)
        assertEquals("不得误判为无结果", false, verdict == AllTabEmptyPolicy.Verdict.NO_RESULT)
    }

    @Test
    fun `files error does not block a result from another leg`() {
        assertEquals(
            "文件腿报错不得顶掉其它腿已有结果",
            AllTabEmptyPolicy.Verdict.HIDE,
            AllTabEmptyPolicy.decide(false, settledHit, settledEmpty, failed),
        )
    }

    @Test
    fun `all legs settled and empty reports no result`() {
        assertEquals(
            "三腿都完成且为空才是真的无结果",
            AllTabEmptyPolicy.Verdict.NO_RESULT,
            AllTabEmptyPolicy.decide(false, settledEmpty, settledEmpty, settledEmpty),
        )
    }

    @Test
    fun `any leg with results hides empty view while others pending`() {
        assertEquals(
            "任一腿已有结果即隐藏空态，不等其它腿",
            AllTabEmptyPolicy.Verdict.HIDE,
            AllTabEmptyPolicy.decide(false, settledHit, pending, pending),
        )
    }

    @Test
    fun `chat records error with files hit hides empty view`() {
        // 已有结果优先于任何 pending/error。
        assertEquals(
            "已有结果优先于任何 pending/error",
            AllTabEmptyPolicy.Verdict.HIDE,
            AllTabEmptyPolicy.decide(false, failed, settledHit, pending),
        )
    }
}
