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

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

/**
 * 用户收藏贴纸接口（Octo server /modules/sticker/api.go）。
 *
 * Retrofit baseUrl 已包含 /v1/ 前缀（见 RetrofitUtils），此处路径不带 /v1。
 *
 * 关键差异：iOS 用 `POST /sticker/user` 收藏消息里的贴图 —— 那是"上传自己
 * 贴纸"接口，服务端会做 `path 必须以 sticker/{loginUID}/ 开头` 校验 → 收藏
 * 他人贴纸时永远 400。Android 走 `POST /sticker/user/collect` —— 服务端专门
 * 为"从消息收藏他人贴图"设计的入口：不需要 handle 签名、允许他人 uid 前缀、
 * 天然幂等（SHA256(source_path) 唯一键）。
 *
 * 未包含：`DELETE /sticker/user/{stickerId}` —— Android 当前无"取消收藏" UI，
 * 按需再加即可（服务端接口存在）。
 */
public interface StickerService {

    @GET("sticker/user")
    Observable<ListStickerResp> getMyStickers();

    // 收藏他人消息里的贴纸。body: {path, placeholder?, sort?, shortcode?, keywords?}
    @POST("sticker/user/collect")
    Observable<WKSticker> collectSticker(@Body JSONObject body);
}
