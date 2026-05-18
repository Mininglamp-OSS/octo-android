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

package com.chat.uikit.contacts.service;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.net.entity.CommonResponse;
import com.chat.uikit.enity.UserInfo;

import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * 2020-07-20 23:06
 */
public interface FriendService {

    @POST("friend/apply")
    Observable<CommonResponse> applyAddFriend(@Body JSONObject jsonObject);

    @POST("friend/sure")
    Observable<CommonResponse> agreeFriendApply(@Body JSONObject jsonObject);

    @GET("friend/sync")
    Observable<List<UserInfo>> syncFriends(@Query("version") long version, @Query("limit") int limit, @Query("api_version") int api_version);

    @GET("friend/sync")
    Observable<List<UserInfo>> syncFriendsWithSpace(@Query("version") long version, @Query("limit") int limit, @Query("api_version") int api_version, @Query("space_id") String spaceId);

    @PUT("users/{uid}/setting")
    Observable<CommonResponse> updateUserSetting(@Path("uid") String uid, @Body JSONObject jsonObject);

}
