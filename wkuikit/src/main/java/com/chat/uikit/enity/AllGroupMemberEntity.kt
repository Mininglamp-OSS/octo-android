package com.chat.uikit.enity

import com.xinbida.wukongim.entity.WKChannelMember

class AllGroupMemberEntity(
    val channelMember: WKChannelMember,
    val onLine: Int,
    val lastOfflineTime: String,
    val lastOnlineTime: String,
) {
}