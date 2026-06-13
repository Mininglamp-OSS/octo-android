/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.chat.choose

import com.xinbida.wukongim.entity.WKChannel

/**
 * 新建会话页 row 数据. 三个 tab 共用一份结构, 通过 isRobot/showHashPrefix 区分渲染.
 */
data class ForwardDirItem(
    /** uniqueKey ("channelId|channelType"), 选中态 dedupe key */
    val key: String,
    /** 回传给 ChooseChatActivity 的 WKChannel,channelType 已固定 */
    val channel: WKChannel,
    val displayName: String,
    val isRobot: Boolean,
    /** 联系人按 pinyin 排序时用 */
    val pinyin: String,
    /** true → cell 头像位置显示 # 而不是头像(用于群聊视觉) */
    val showHashPrefix: Boolean,
    var isCheck: Boolean = false,
)
