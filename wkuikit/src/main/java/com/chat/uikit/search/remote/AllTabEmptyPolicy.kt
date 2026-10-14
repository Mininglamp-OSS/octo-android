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

/**
 * 「全部」页空态决策。抽成纯函数的理由与 [FilesTabRenderPolicy] 完全相同：
 * 这里的三腿判定曾经用 sticky 标记，在第二轮搜索时永久为 true 而误报「无搜索结果」；
 * 修掉 sticky 之后又出现了反向缺陷——文件腿报错时三个分支全不命中，落到 else，
 * 空态被隐藏，用户看到一张没有任何解释的白板。两次都是 UI 层的判定逻辑出错，
 * ViewModel 单测照常全绿。所以判定必须独立可测。
 */
object AllTabEmptyPolicy {

    /** 一条异步腿的状态。[errored] 为 true 时该腿的 [isEmpty] 无意义，判定中应整腿剔除。 */
    data class Leg(
        val settled: Boolean,
        val isEmpty: Boolean,
        val errored: Boolean = false,
    )

    enum class Verdict {
        /** 空输入提示 */
        EMPTY_KEYWORD,

        /** 搜索中（转圈） */
        LOADING,

        /** 真的无结果 */
        NO_RESULT,

        /** 全部可用的腿都已完成且都为空，但至少有一腿报错 —— 展示「部分内容加载失败 + 重试」 */
        PARTIAL_FAILURE,

        /** 有结果，隐藏空态 */
        HIDE,
    }

    /**
     * @param contactsGroups 联系人/群段（同一次 /search/global 请求，无独立错误态）
     * @param chatRecords 聊天记录段
     * @param files 文件段（无筛选的 allFilesViewModel）
     */
    fun decide(
        isEmptyKeyword: Boolean,
        contactsGroups: Leg,
        chatRecords: Leg,
        files: Leg,
    ): Verdict {
        if (isEmptyKeyword) return Verdict.EMPTY_KEYWORD
        val legs = listOf(contactsGroups, chatRecords, files)
        // 有任何一腿已返回结果 → 立即隐藏空态，不等其它腿（原注释的「任一路完成就退出 loading」）
        if (legs.any { it.settled && !it.errored && !it.isEmpty }) return Verdict.HIDE
        // 全部腿都还没落地 → 转圈。注意：报错的腿算「已落地」（它不会再有结果）。
        if (legs.none { it.settled }) return Verdict.LOADING
        // 还有腿在途 → 继续转圈，不能提前下结论
        if (legs.any { !it.settled }) return Verdict.LOADING
        // 到这里所有腿都已落地且都没有结果。报错的腿整腿剔除，不参与「是否真的无结果」：
        // 若存在报错腿，我们无法断言「真的没有」，但也不能什么都不显示。
        return if (legs.any { it.errored }) Verdict.PARTIAL_FAILURE else Verdict.NO_RESULT
    }
}
