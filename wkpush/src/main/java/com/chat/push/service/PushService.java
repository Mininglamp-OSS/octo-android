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

package com.chat.push.service;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.net.entity.CommonResponse;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.POST;

/**
 * 2020-07-20 17:45
 * 推送module
 */
public interface PushService {

    @POST("user/device_token")
    Observable<CommonResponse> registerAppToken(@Body JSONObject jsonObject);

    @DELETE("user/device_token")
    Observable<CommonResponse> unRegisterAppToken();

    @POST("user/device_badge")
    Observable<CommonResponse> registerBadge(@Body JSONObject jsonObject);
}
