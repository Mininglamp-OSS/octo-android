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

package com.chat.uikit.robot.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.uikit.robot.entity.WKRobotInlineQueryResult;
import com.chat.uikit.robot.entity.WKSyncRobotEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface WKRobotService {
    @POST("robot/sync")
    Observable<List<WKSyncRobotEntity>> syncRobot(@Body JSONArray jsonArray);

    @POST("robot/inline_query")
    Observable<WKRobotInlineQueryResult> inlineQuery(@Body JSONObject jsonObject);
}
