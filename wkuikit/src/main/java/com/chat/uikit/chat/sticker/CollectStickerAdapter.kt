/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.chat.sticker

import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.config.WKApiConfig
import com.chat.base.glide.GlideUtils
import com.chat.base.ui.components.FilterImageView
import com.chat.uikit.R

/**
 * "我的贴图" 表情面板 grid adapter。
 * 复用 chat cell provider 的 URL 判定（[StickerUrlUtils]），静态图 Glide 加载，
 * Lottie 走 default_view_bg 占位。点击回调由外层挂 setOnItemClickListener 处理
 * （构造 WKVectorStickerContent 并 sendMessage）。
 */
class CollectStickerAdapter :
    BaseQuickAdapter<WKSticker, BaseViewHolder>(R.layout.item_collect_sticker) {

    override fun convert(holder: BaseViewHolder, item: WKSticker) {
        val imageView = holder.getView<FilterImageView>(R.id.stickerImageView)
        val url = item.path
        when {
            url.isNullOrEmpty() ->
                imageView.setImageResource(com.chat.base.R.drawable.default_view_bg)
            StickerUrlUtils.isStaticImage(url) ->
                GlideUtils.getInstance().showImg(context, WKApiConfig.getShowUrl(url), imageView)
            StickerUrlUtils.isLottieFormat(url, item.format) ->
                imageView.setImageResource(com.chat.base.R.drawable.default_view_bg)
            else ->
                GlideUtils.getInstance().showImg(context, WKApiConfig.getShowUrl(url), imageView)
        }
    }
}
