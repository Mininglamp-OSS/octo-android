/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.chat.provider

import com.xinbida.wukongim.message.type.WKMsgContentType

/**
 * Emoji 贴图 (type=13) provider —— 结构与 vector sticker 一致，仅换 itemViewType。
 *
 * 独立子类的必要性：chad BaseProviderMultiAdapter.addItemProvider() 按
 * provider.itemViewType 存 key，直接 new WKVectorStickerProvider() 复用会让
 * mProviders 只留一份 type=12 映射，type=13 到达时 onCreateDefViewHolder 找不到崩。
 */
class WKEmojiStickerProvider : WKVectorStickerProvider() {
    override val itemViewType: Int
        get() = WKMsgContentType.WK_EMOJI_STICKER
}
