/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.base.msg.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.xinbida.wukongim.message.type.WKMsgContentType;

/**
 * Emoji 贴图 (contentType=13)。
 *
 * 结构与 WKVectorStickerContent 完全一致，服务端也统一按贴图路径管理；
 * 只是发送侧用来区分"贴图 tab 里的 emoji 贴纸"vs"lottie 贴纸"。
 * 对齐 iOS `WKEmojiStickerContent : WKLottieStickerContent`。
 */
public class WKEmojiStickerContent extends WKVectorStickerContent {

    public WKEmojiStickerContent() {
        this.type = WKMsgContentType.WK_EMOJI_STICKER;
    }

    protected WKEmojiStickerContent(Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<WKEmojiStickerContent> CREATOR = new Parcelable.Creator<WKEmojiStickerContent>() {
        @Override
        public WKEmojiStickerContent createFromParcel(Parcel in) {
            return new WKEmojiStickerContent(in);
        }

        @Override
        public WKEmojiStickerContent[] newArray(int size) {
            return new WKEmojiStickerContent[size];
        }
    };

    @Override
    public String getDisplayContent() {
        return "[emoji表情]";
    }

    @Override
    public String getSearchableWord() {
        return "[emoji表情]";
    }
}
