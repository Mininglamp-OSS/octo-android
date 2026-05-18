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

package com.chat.uikit.space;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.net.entity.CommonResponse;

import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface SpaceService {

    @GET("space/my")
    Observable<List<SpaceEntity>> getMySpaces();

    @POST("space/create")
    Observable<SpaceEntity> createSpace(@Body JSONObject body);

    @GET("space/{space_id}")
    Observable<SpaceEntity> getSpaceDetail(@Path("space_id") String spaceId);

    @GET("space/{space_id}/members")
    Observable<List<SpaceEntity.SpaceMember>> getMembers(
            @Path("space_id") String spaceId,
            @Query("page") int page,
            @Query("limit") int limit);

    @POST("space/{space_id}/invite")
    Observable<SpaceEntity.InviteResult> createInvite(@Path("space_id") String spaceId, @Body JSONObject body);

    @POST("space/join")
    Observable<CommonResponse> joinSpace(@Body JSONObject body);

    @POST("space/{space_id}/leave")
    Observable<CommonResponse> leaveSpace(@Path("space_id") String spaceId);

    @DELETE("space/{space_id}")
    Observable<CommonResponse> disbandSpace(@Path("space_id") String spaceId);

    @POST("space/{space_id}/members/add")
    Observable<CommonResponse> addMembers(@Path("space_id") String spaceId, @Body JSONObject body);

    @POST("space/{space_id}/members/remove")
    Observable<CommonResponse> removeMembers(@Path("space_id") String spaceId, @Body JSONObject body);

    @PUT("space/{space_id}/members/{uid}/role")
    Observable<CommonResponse> changeMemberRole(@Path("space_id") String spaceId, @Path("uid") String uid, @Body JSONObject body);
}
