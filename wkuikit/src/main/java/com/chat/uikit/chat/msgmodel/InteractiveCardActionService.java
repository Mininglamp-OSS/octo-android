/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.chat.uikit.chat.msgmodel;

import com.alibaba.fastjson.JSONObject;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * 交互式卡片（ContentType 17）动作上行接口。
 *
 * <p>对齐 web `octo-web/dmworkbase/InteractiveCard/cardAction.ts::submitCardAction`
 * 与服务端 `octo-server/modules/message/api_card_action.go`。</p>
 *
 * <p><b>协议要点：</b></p>
 * <ul>
 *   <li>路径：{@code POST /v1/message/card/action}（RetrofitUtils.baseUrl 已含 /v1/）</li>
 *   <li>请求体：{@code message_id / channel_id / channel_type / action_id / inputs / client_token}</li>
 *   <li>刻意不传 {@code data}：服务端从存储帧的 Action.Submit 里提取（D11 防伪造，客户端只回传 id）</li>
 *   <li>响应：{@code {accepted, replay}}。receipt 即视为成功；受理后等 bot 改卡新帧（走 CMDSyncMessageExtra）</li>
 * </ul>
 *
 * <p><b>错误映射：</b></p>
 * <ul>
 *   <li>400 {@code ErrMessageCardActionInvalid} / 403 {@code ErrMessageCardActionDenied} → 终态失败</li>
 *   <li>409 {@code ErrMessageCardActionInProgress} / 5xx → 可重试</li>
 * </ul>
 */
public interface InteractiveCardActionService {

    @POST("message/card/action")
    Observable<JSONObject> submitCardAction(@Body JSONObject body);
}
