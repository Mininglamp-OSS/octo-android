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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllTabLegMappingTest {

    private val settledEmptyLeg = AllTabEmptyPolicy.Leg(settled = true, isEmpty = true)

    @Test
    fun `pending state is not settled`() {
        val leg = AllTabEmptyPolicy.legFromState(
            loading = true, queryStarted = true, errored = false, isEmpty = true,
        )
        assertFalse("请求在途时腿必须 unsettled", leg.settled)
    }

    @Test
    fun `errored state is always settled`() {
        // 锁住 decide 的前置契约：errored 腿绝不能 unsettled，否则永久转圈。
        val leg = AllTabEmptyPolicy.legFromState(
            loading = false, queryStarted = false, errored = true, isEmpty = true,
        )
        assertTrue("errored 腿必须算已落地", leg.settled)
        assertTrue("errored 语义必须原样传递", leg.errored)
    }

    @Test
    fun `not started is not settled`() {
        val leg = AllTabEmptyPolicy.legFromState(
            loading = false, queryStarted = false, errored = false, isEmpty = true,
        )
        assertFalse("未发起过请求的腿必须 unsettled", leg.settled)
    }

    @Test
    fun `settled empty success`() {
        assertEquals(
            "完成一轮且为空的成功腿应映射为 settledEmpty",
            AllTabEmptyPolicy.Leg(settled = true, isEmpty = true, errored = false),
            AllTabEmptyPolicy.legFromState(
                loading = false, queryStarted = true, errored = false, isEmpty = true,
            ),
        )
    }

    @Test
    fun `ime rerun regression`() {
        // IME 原地重搜：聊天腿在途、文件腿已落地为空，不得报无搜索结果。
        val chatLeg = AllTabEmptyPolicy.legFromState(
            loading = true, queryStarted = true, errored = false, isEmpty = true,
        )
        val filesLeg = AllTabEmptyPolicy.legFromState(
            loading = false, queryStarted = true, errored = false, isEmpty = true,
        )
        assertEquals(
            "IME 原地重搜：聊天腿在途、文件腿已落地为空，不得报无搜索结果",
            AllTabEmptyPolicy.Verdict.LOADING,
            AllTabEmptyPolicy.decide(false, settledEmptyLeg, chatLeg, filesLeg),
        )
    }

    @Test
    fun `chat terminal error regression`() {
        val chatLeg = AllTabEmptyPolicy.legFromState(
            loading = false, queryStarted = true, errored = true, isEmpty = true,
        )
        assertEquals(
            "聊天腿终态错误且其余腿为空应显示部分失败",
            AllTabEmptyPolicy.Verdict.PARTIAL_FAILURE,
            AllTabEmptyPolicy.decide(false, settledEmptyLeg, chatLeg, settledEmptyLeg),
        )
    }
}
