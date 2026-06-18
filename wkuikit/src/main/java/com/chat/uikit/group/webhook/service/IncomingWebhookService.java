/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.uikit.group.webhook.service;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.net.entity.CommonResponse;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

/**
 * 群入站 Webhook 网络接口，6 个端点与 octo-server `/v1/groups/{group_no}/incoming-webhooks*`
 * 一一对应，命名 / 顺序与 iOS WKIncomingWebhookManager 对齐。
 */
public interface IncomingWebhookService {

    @GET("groups/{groupNo}/incoming-webhooks")
    Observable<JSONObject> list(@Path("groupNo") String groupNo);

    @POST("groups/{groupNo}/incoming-webhooks")
    Observable<JSONObject> create(@Path("groupNo") String groupNo, @Body JSONObject body);

    @PUT("groups/{groupNo}/incoming-webhooks/{webhookId}")
    Observable<CommonResponse> update(@Path("groupNo") String groupNo,
                                      @Path("webhookId") String webhookId,
                                      @Body JSONObject body);

    @HTTP(method = "DELETE", path = "groups/{groupNo}/incoming-webhooks/{webhookId}", hasBody = false)
    Observable<CommonResponse> delete(@Path("groupNo") String groupNo,
                                      @Path("webhookId") String webhookId);

    @POST("groups/{groupNo}/incoming-webhooks/{webhookId}/regenerate")
    Observable<JSONObject> regenerate(@Path("groupNo") String groupNo,
                                      @Path("webhookId") String webhookId);

    @POST("groups/{groupNo}/incoming-webhooks/{webhookId}/test")
    Observable<CommonResponse> test(@Path("groupNo") String groupNo,
                                    @Path("webhookId") String webhookId);
}
