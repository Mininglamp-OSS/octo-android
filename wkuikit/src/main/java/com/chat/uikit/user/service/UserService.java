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

package com.chat.uikit.user.service;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.net.entity.CommonResponse;
import com.chat.uikit.enity.Device;
import com.chat.uikit.enity.MailListEntity;
import com.chat.uikit.enity.OnlineUser;
import com.chat.uikit.enity.OnlineUserAndDevice;
import com.chat.uikit.enity.UserInfo;
import com.chat.uikit.enity.UserQr;
import com.chat.uikit.enity.VerifyTokenResponse;

import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * 2020-07-20 18:02
 * 用户module
 */
public interface UserService {
    @GET("users/{uid}")
    Observable<UserInfo> getUserInfo(@Path("uid") String uid, @Query("group_no") String groupNo);

    @PUT("user/current")
    Observable<CommonResponse> updateUserInfo(@Body JSONObject jsonObject);

    @PUT("friend/remark")
    Observable<CommonResponse> updateFriendRemark(@Body JSONObject jsonObject);

    @DELETE("friends/{uid}")
    Observable<CommonResponse> deleteFriend(@Path("uid") String uid);

    @POST("user/blacklist/{uid}")
    Observable<CommonResponse> addBlackList(@Path("uid") String uid);

    @DELETE("user/blacklist/{uid}")
    Observable<CommonResponse> removeBlackList(@Path("uid") String uid);

    @GET("user/online")
    Observable<OnlineUserAndDevice> onlineUsers();

    @POST("user/online")
    Observable<List<OnlineUser>> getOnlineUsers(@Body JSONArray jsonArray);

    @PUT("user/my/setting")
    Observable<CommonResponse> setting(@Body JSONObject jsonObject);

    @GET("user/qrcode")
    Observable<UserQr> userQr();

    @POST("user/maillist")
    Observable<CommonResponse> uploadContacts(@Body JSONArray jsonArray);

    @GET("user/maillist")
    Observable<List<MailListEntity>> getContacts();

    @POST("user/signal/keys")
    Observable<CommonResponse> signalKeys(@Body JSONObject jsonObject);

    @POST("user/quit")
    Observable<CommonResponse> quit();

    @GET("user/devices/{device_id}")
    Observable<Device> device(@Path("device_id") String device_id);

    @GET("user/destroy/status")
    Observable<JSONObject> getDestroyStatus();

    @POST("user/destroy/apply")
    Observable<JSONObject> applyDestroy(@Body JSONObject body);

    @POST("user/destroy/cancel")
    Observable<CommonResponse> cancelDestroy();

    //  (对齐 web PR#1092 BotDetailModal)：bot 创建者更新 bot 简介。
    // 后端 PUT /robot/:uid/description 内置 creator_uid 校验，非 owner 返回 403。
    @PUT("robot/{uid}/description")
    Observable<CommonResponse> updateBotDescription(@Path("uid") String uid, @Body JSONObject body);

    // ---------------------------------------------------------------------
    //  (#227) · OCTO 实名认证接入（PR#1301 后端已 merge）
    // ---------------------------------------------------------------------

    /**
     * 获取当前登录用户的权威 profile，带 {@code realname_verified/realname/
     * realname_verified_at} 字段。Custom Tabs 回跳后调用以刷新本地缓存。
     */
    @GET("user/current")
    Observable<UserInfoEntity> getCurrentUser();

    /**
     * 客户端发起实名流程前的握手：后端签发一次性 verify-token，返回
     * CAS/Aegis 的 {@code verify_url}（已含 return_to=dmwork://verified）。
     */
    @POST("internal/verify-token")
    Observable<VerifyTokenResponse> createVerifyToken();
}
