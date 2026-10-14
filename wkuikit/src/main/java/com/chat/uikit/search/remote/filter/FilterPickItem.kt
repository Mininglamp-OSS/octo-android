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

import com.xinbida.wukongim.entity.WKChannel

/**
 * 筛选页二级多选页（发送者/包含成员/所在群聊或子区）共用 row 数据。
 * [key] 对发送者/成员是 uid，对频道是 "channelId|channelType"。
 */
data class FilterPickItem(
    val key: String,
    val channel: WKChannel,
    val displayName: String,
    var isCheck: Boolean = false,
)
