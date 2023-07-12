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
