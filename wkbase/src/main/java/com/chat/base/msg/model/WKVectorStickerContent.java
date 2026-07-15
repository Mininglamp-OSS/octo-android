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
import com.xinbida.wukongim.msgmodel.WKMediaMessageContent;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 矢量贴图 / Lottie 贴图 (contentType=12)。
 *
 * wire 字段对齐服务端 stickerResp / iOS WKLottieStickerContent：
 *   url / width / height / category / placeholder / format
 *
 * Android 不引入 Lottie SDK：`.png/.jpg/.webp/.gif` 后缀由 Glide 直接显示，
 * `.lim/.json`（Lottie JSON）走占位图降级。渲染逻辑集中在
 * WKVectorStickerProvider + StickerUrlUtils，见其说明。
 */
public class WKVectorStickerContent extends WKMediaMessageContent implements Parcelable {
    public int width;
    public int height;
    public String category;
    public String placeholder;
    public String format;

    public WKVectorStickerContent() {
        this.type = WKMsgContentType.WK_VECTOR_STICKER;
    }

    @Override
    public JSONObject encodeMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("url", url == null ? "" : url);
            jsonObject.put("width", width);
            jsonObject.put("height", height);
            jsonObject.put("category", category == null ? "" : category);
            jsonObject.put("placeholder", placeholder == null ? "" : placeholder);
            jsonObject.put("format", format == null ? "lim" : format);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    @Override
    public WKMessageContent decodeMsg(JSONObject jsonObject) {
        this.url = jsonObject.optString("url");
        this.width = jsonObject.optInt("width");
        this.height = jsonObject.optInt("height");
        this.category = jsonObject.optString("category");
        this.placeholder = jsonObject.optString("placeholder");
        this.format = jsonObject.has("format") ? jsonObject.optString("format") : "lim";
        return this;
    }

    protected WKVectorStickerContent(Parcel in) {
        super(in);
        url = in.readString();
        localPath = in.readString();
        width = in.readInt();
        height = in.readInt();
        category = in.readString();
        placeholder = in.readString();
        format = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(url);
        dest.writeString(localPath);
        dest.writeInt(width);
        dest.writeInt(height);
        dest.writeString(category);
        dest.writeString(placeholder);
        dest.writeString(format);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<WKVectorStickerContent> CREATOR = new Creator<WKVectorStickerContent>() {
        @Override
        public WKVectorStickerContent createFromParcel(Parcel in) {
            return new WKVectorStickerContent(in);
        }

        @Override
        public WKVectorStickerContent[] newArray(int size) {
            return new WKVectorStickerContent[size];
        }
    };

    @Override
    public String getDisplayContent() {
        return "[贴图]";
    }

    @Override
    public String getSearchableWord() {
        return "[贴图]";
    }
}
