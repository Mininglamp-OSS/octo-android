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

package com.chat.base.entity

import com.xinbida.wukongim.entity.WKChannelExtras

class WKChannelCustomerExtras : WKChannelExtras() {
    companion object {
        const val joinGroupRemind = "join_group_remind"
        const val memberCount = "member_count"
        const val onlineCount = "online_count"
        const val role = "role"
        const val isExternalGroup = "is_external_group"

        // 是否允许加入外部成员（，web #965）
        // 群级开关：1 允许跨 Space 邀请，0 仅限本 Space
        const val allowExternal = "allow_external"

        // 群归属 Space（/外部群 Phase 1）
        // 客户端做 viewer-relative 判定时用来兜底：当 SetEffectiveSpaceID 与
        // 群 space_id 不一致时，说明当前 viewer 来自不同 Space。
        const val spaceId = "space_id"
    }

}