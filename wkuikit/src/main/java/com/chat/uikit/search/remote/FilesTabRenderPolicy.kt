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

/**
 * 文件 tab 的渲染决策：某个 state 该渲染多少条、是否整屏接管为错误态、错误是否走 toast。
 *
 * 抽成纯函数是因为这里曾经出过一个"ViewModel 修了、UI 又丢掉"的缺陷：
 * ViewModel 在翻页失败时刻意保留已加载的页，但 Activity 的 collector 当时写的是
 * `setList(if (action == null) state.files else emptyList())`，只要 errorCode 非空
 * 就清空列表 —— 保留逻辑被完全抵消，用户已看到的 20 条结果被整屏「加载失败」顶掉。
 *
 * 当时的 ViewModel 层单测只断言 `state.files`，所以测试全绿而缺陷照旧。
 * 把决策抽出来单测，覆盖的就是缺陷真正所在的那一层。
 */
object FilesTabRenderPolicy {

    /**
     * @param itemCount 该 state 下应渲染的条目数（恒等于 `state.files.size`，列表无条件渲染）
     * @param takeOverWithError 是否让空态整屏接管为「加载失败 + 重试」
     * @param toastError 是否用 toast 提示错误（列表有内容、空态不可见时的唯一告知途径）
     */
    data class Decision(
        val itemCount: Int,
        val takeOverWithError: Boolean,
        val toastError: Boolean,
    )

    fun decide(action: ChannelSearchUiAction?, fileCount: Int): Decision {
        val hasResults = fileCount > 0
        val failed = action != null
        return Decision(
            // 列表永远照原样渲染：翻页失败时 ViewModel 保留的页必须留在屏上。
            itemCount = fileCount,
            // 只有"失败且一条都没有"才值得整屏接管（首屏失败 / 接口未部署）。
            takeOverWithError = failed && !hasResults,
            // 失败但已有结果 → 空态不可见，只能 toast（翻页失败场景）。
            toastError = failed && hasResults,
        )
    }
}
