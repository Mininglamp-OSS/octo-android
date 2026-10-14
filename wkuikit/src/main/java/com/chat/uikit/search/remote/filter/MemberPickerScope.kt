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

package com.chat.uikit.search.remote.filter

/**
 * [FilterMemberPickerActivity] 的维度口径：同一个多选页被"发送者"与"包含成员"两个 section
 * 复用，但**候选集不同**，不能共用一个过滤谓词。
 *
 * 抽成纯函数而非写在 `loadMembers` 里，是因为 Activity 行为在本仓库无法单测
 * （无 Robolectric、无 Activity 测试），同 [com.chat.uikit.search.remote.FilesTabRenderPolicy]
 * 的取舍：把决策搬到能被覆盖的那一层。
 *
 * ## 为什么发送者含自己与 bot
 *
 * 「只看我发的」是发送者筛选最自然的首选用法，排除自己会让它不可达且**静默失败**
 * ——用户在列表里找不到自己，没有任何提示。bot 在本产品里是一等消息来源
 * （摘要通知、互动卡片），同理需要可选。
 *
 * iOS 可作依据：会话内发送者选择器**显式把自己加为首行「我」**
 * （`WKChannelHistorySenderPickerVC.m:146-152`），群聊分支追加全部成员时也不过滤
 * `robot`（同文件 `:180-196`，尽管 `WKChannelMemberDB` 带 robot 标记）。
 * 即 iOS 的产品意图是"自己是合法发送者"。
 *
 * 注意**不要**拿 iOS 全局筛选页当反证：它的发送者/包含成员候选集都来自同一个
 * `provideFriendCandidates:`（`follow=1` 好友查询），自己与 bot 的缺失是该查询的
 * 副作用，`GlobalSearchV2/` 与 `ChannelHistorySearch/` 里**没有任何** `robot`
 * 或 self 的显式排除谓词——那是实现捷径，不是经过设计的口径。
 *
 * ## 为什么包含成员仍排除自己与 bot
 *
 * 「包含成员」语义是"这个会话里还有谁"，用于收窄到某些人共同在场的会话。自己必然
 * 在自己能搜到的每个会话里，选中等于无约束；bot 同理噪音大于信息量。
 * 这一条是**刻意保留**原 [com.chat.uikit.chat.choose.ForwardDirectoryActivity] 口径，
 * 不是继承未改。
 */
object MemberPickerScope {

    /** 发送者：谁发的消息/文件。含自己与 bot。 */
    const val SENDER = 0

    /** 包含成员：会话里还有谁。排除自己与 bot。 */
    const val MEMBER = 1

    /**
     * 该成员是否应出现在 [scope] 维度的候选列表里。
     *
     * @param scope [SENDER] 或 [MEMBER]；未知值按 [MEMBER] 的保守口径处理
     * @param isSelf 是否为当前登录用户
     * @param isRobot 是否为 bot（`SpaceMember.robot == 1`）
     */
    fun shouldInclude(scope: Int, isSelf: Boolean, isRobot: Boolean): Boolean =
        if (scope == SENDER) true else !isSelf && !isRobot
}
