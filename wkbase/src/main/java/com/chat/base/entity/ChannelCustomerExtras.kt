package com.chat.base.entity

import com.xinbida.wukongim.entity.WKChannelExtras

class WKChannelCustomerExtras : WKChannelExtras() {
    companion object {
        const val joinGroupRemind = "join_group_remind"
        const val memberCount = "member_count"
        const val onlineCount = "online_count"
        const val role = "role"
        const val isExternalGroup = "is_external_group"

        // 是否允许加入外部成员（YUJ-27，web #965）
        // 群级开关：1 允许跨 Space 邀请，0 仅限本 Space
        const val allowExternal = "allow_external"

        // 群归属 Space（YUJ-27/外部群 Phase 1）
        // 客户端做 viewer-relative 判定时用来兜底：当 SetEffectiveSpaceID 与
        // 群 space_id 不一致时，说明当前 viewer 来自不同 Space。
        const val spaceId = "space_id"
    }

}