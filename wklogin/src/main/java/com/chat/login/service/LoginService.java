package com.chat.login.service;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.net.entity.CommonResponse;
import com.chat.login.entity.CountryCodeEntity;
import com.chat.login.entity.ThirdAuthCode;
import com.chat.login.entity.ThirdLoginResult;
import com.chat.login.entity.VerfiCodeResult;

import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Query;

/**
 * 2020-07-17 15:53
 * 登录
 */
public interface LoginService {

    @POST("user/login")
    Observable<UserInfoEntity> login(@Body JSONObject jsonObject);

    @GET("user/grant_login")
    Observable<CommonResponse> webLoginConfirm(@Query("auth_code") String auth_code);


    @GET("common/countries")
    Observable<List<CountryCodeEntity>> getCountries();

    @POST("user/sms/registercode")
    Observable<VerfiCodeResult> registerCode(@Body JSONObject jsonObject);

    @POST("user/sms/forgetpwd")
    Observable<CommonResponse> forgetPwd(@Body JSONObject jsonObject);

    @POST("user/pwdforget")
    Observable<CommonResponse> pwdForget(@Body JSONObject jsonObject);

    @POST("user/register")
    Observable<UserInfoEntity> register(@Body JSONObject jsonObject);

    @PUT("user/current")
    Observable<CommonResponse> updateUserInfo(@Body JSONObject jsonObject);

    @POST("user/sms/login_check_phone")
    Observable<CommonResponse> sendLoginAuthVerifCode(@Body JSONObject jsonObject);

    @POST("user/login/check_phone")
    Observable<UserInfoEntity> checkLoginAuth(@Body JSONObject jsonObject);

    @POST("user/pc/quit")
    Observable<CommonResponse> quitPc();

    @PUT("user/my/setting")
    Observable<CommonResponse> setting(@Body JSONObject jsonObject);

    @GET("user/thirdlogin/authcode")
    Observable<ThirdAuthCode> getAuthCode();

    @GET("user/thirdlogin/authstatus")
    Observable<ThirdLoginResult> getAuthStatus(@Query("authcode") String authcode);
}
