package com.chat.uikit.user.service;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.net.entity.CommonResponse;
import com.chat.uikit.enity.MailListEntity;
import com.chat.uikit.enity.OnlineUser;
import com.chat.uikit.enity.OnlineUserAndDevice;
import com.chat.uikit.enity.UserInfo;
import com.chat.uikit.enity.UserQr;

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

}
