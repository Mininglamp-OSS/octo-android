/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.chat.sticker;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.net.entity.CommonResponse;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * 用户收藏 / 上传贴纸接口（Octo server /modules/sticker/api.go）。
 *
 * Retrofit baseUrl 已包含 /v1/ 前缀（见 RetrofitUtils），此处路径不带 /v1。
 *
 * <h3>接口分工</h3>
 * <ul>
 *   <li>{@link #getMyStickers()} —— GET /sticker/user，列当前用户全部收藏</li>
 *   <li>{@link #collectSticker(JSONObject)} —— POST /sticker/user/collect，
 *       "从他人消息里收藏贴图"专用入口。允许他人 uid 前缀、不需要 handle 签名、
 *       天然幂等（SHA256(source_path) 唯一键）</li>
 *   <li>{@link #uploadSticker(JSONObject)} —— POST /sticker/user，
 *       "上传自己的贴纸"入口。path 必须以 sticker/{loginUID}/ 开头，
 *       服务端可能要求 handle 字段（HMAC 签名，视配置 sticker.handle_required）</li>
 *   <li>{@link #deleteSticker(String)} —— DELETE /sticker/user/{sticker_id}，
 *       单条软删</li>
 * </ul>
 *
 * <h3>为什么 collect 走单独入口</h3>
 * iOS 端历史上错用 POST /sticker/user 收藏他人贴图 —— 服务端 uid 前缀校验直接
 * 400 → iOS "添加表情"无反应。Android 从一开始就走 /collect 这条正确路径。
 */
public interface StickerService {

    @GET("sticker/user")
    Observable<ListStickerResp> getMyStickers();

    // 收藏他人消息里的贴纸。body: {path, placeholder?, sort?, shortcode?, keywords?}
    @POST("sticker/user/collect")
    Observable<WKSticker> collectSticker(@Body JSONObject body);

    // 上传自己的贴纸（需先走 /file/upload 拿到 path）。
    // body: {path, width, height, format, handle?}
    @POST("sticker/user")
    Observable<WKSticker> uploadSticker(@Body JSONObject body);

    // 删除单条收藏（服务端软删）。204/200 都视为成功。
    @DELETE("sticker/user/{sticker_id}")
    Observable<CommonResponse> deleteSticker(@Path("sticker_id") String stickerId);
}
