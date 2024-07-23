package com.chat.login.service;


import android.text.TextUtils;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.ICommonListener;
import com.chat.base.net.IRequestResultErrorInfoListener;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.base.net.ud.WKUploader;
import com.chat.base.utils.WKDeviceUtils;
import com.chat.base.utils.WKTimeUtils;
import com.chat.login.entity.CountryCodeEntity;
import com.chat.login.entity.ThirdAuthCode;
import com.chat.login.entity.ThirdLoginResult;
import com.chat.login.entity.VerfiCodeResult;

import org.json.JSONException;

import java.util.List;

/**
 * 2019-11-19 17:49
 * 登录model
 */
public class LoginModel extends WKBaseModel {
    private LoginModel() {
    }

    private static class LoginModelBinder {
        private static final LoginModel loginModel = new LoginModel();
    }

    public static LoginModel getInstance() {
        return LoginModelBinder.loginModel;
    }

    /**
     * 登录
     *
     * @param name           账号
     * @param pwd            密码
     * @param iLoginListener 返回
     */
    void loginApp(String name, String pwd, final ILoginListener iLoginListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("username", name);
        jsonObject.put("password", pwd);
        JSONObject deviceJson = new JSONObject();
        deviceJson.put("device_id", WKConstants.getDeviceID());
        deviceJson.put("device_name", WKDeviceUtils.getInstance().getDeviceName());
        deviceJson.put("device_model", WKDeviceUtils.getInstance().getSystemModel());
        jsonObject.put("device", deviceJson);

        requestAndErrorBack(createService(LoginService.class).login(jsonObject), new IRequestResultErrorInfoListener<UserInfoEntity>() {
            @Override
            public void onSuccess(UserInfoEntity userInfo) {
                if (userInfo != null) {
                    saveLoginInfo(userInfo);
                    iLoginListener.onResult(HttpResponseCode.success, "", userInfo);
                }
            }

            @Override
            public void onFail(int code, String msg, String errJson) {
                UserInfoEntity userInfoEntity = new UserInfoEntity();
                if (code == 110 && !TextUtils.isEmpty(errJson)) {
                    try {
                        org.json.JSONObject jsonObject1 = new org.json.JSONObject(errJson);
                        userInfoEntity.phone = jsonObject1.optString("phone");
                        userInfoEntity.uid = jsonObject1.optString("uid");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                iLoginListener.onResult(code, msg, userInfoEntity);
            }
        });
    }

    public interface ILoginListener {
        void onResult(int code, String errorMsg, UserInfoEntity userInfo);
    }

    /**
     * pc登录确认
     *
     * @param auth_code 认证码
     * @param listener  返回
     */
    public void webLogin(String auth_code, final ICommonListener listener) {
        request(createService(LoginService.class).webLoginConfirm(auth_code), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                listener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg);
            }
        });
    }

    void getCountries(IChooseCountryCode iChooseCountryCode) {
        request(createService(LoginService.class).getCountries(), new IRequestResultListener<List<CountryCodeEntity>>() {
            @Override
            public void onSuccess(List<CountryCodeEntity> result) {
                iChooseCountryCode.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                iChooseCountryCode.onResult(code, msg, null);
            }
        });
    }

    public interface IChooseCountryCode {
        void onResult(int code, String msg, List<CountryCodeEntity> list);
    }


    void registerCode(String zone, String phone, final IGetVerCodeListener iGetVerfi) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("zone", zone);
        jsonObject.put("phone", phone);
        request(createService(LoginService.class).registerCode(jsonObject), new IRequestResultListener<VerfiCodeResult>() {
            @Override
            public void onSuccess(VerfiCodeResult result) {
                iGetVerfi.onResult(HttpResponseCode.success, "", result.exist);
            }

            @Override
            public void onFail(int code, String msg) {
                iGetVerfi.onResult(code, msg, 0);
            }
        });
    }

    void forgetPwd(String zone, String phone, final IGetVerCodeListener iGetVerCodeListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("zone", zone);
        jsonObject.put("phone", phone);
        request(createService(LoginService.class).forgetPwd(jsonObject), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iGetVerCodeListener.onResult(HttpResponseCode.success, "", 0);
            }

            @Override
            public void onFail(int code, String msg) {
                iGetVerCodeListener.onResult(code, msg, 0);
            }
        });
    }

    void resetPwd(String zone, String phone, String code, String pwd, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("zone", zone);
        jsonObject.put("phone", phone);
        jsonObject.put("code", code);
        jsonObject.put("pwd", pwd);
        request(createService(LoginService.class).pwdForget(jsonObject), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    public interface IGetVerCodeListener {
        void onResult(int code, String msg, int exit);
    }

    void registerApp(String code, String zone, String name, String phone, String password,String inviteCode, final ILoginListener iLoginListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", code);
        jsonObject.put("zone", zone);
        jsonObject.put("name", name);
        jsonObject.put("phone", phone);
        jsonObject.put("password", password);
        jsonObject.put("invite_code", inviteCode);
        JSONObject deviceJson = new JSONObject();
        deviceJson.put("device_id", WKConstants.getDeviceID());
        deviceJson.put("device_name", WKDeviceUtils.getInstance().getDeviceName());
        deviceJson.put("device_model", WKDeviceUtils.getInstance().getSystemModel());
        jsonObject.put("device", deviceJson);
        request(createService(LoginService.class).register(jsonObject), new IRequestResultListener<UserInfoEntity>() {
            @Override
            public void onSuccess(UserInfoEntity userInfo) {
                if (userInfo != null) {
                    saveLoginInfo(userInfo);
                    iLoginListener.onResult(HttpResponseCode.success, "", userInfo);
                }
            }

            @Override
            public void onFail(int code, String msg) {
                iLoginListener.onResult(code, msg, null);
            }
        });
    }


    public void uploadAvatar(String filePath, final IUploadBack iUploadBack) {
        String url = WKApiConfig.baseUrl + "users/" + WKConfig.getInstance().getUid() + "/avatar?uuid=" + WKTimeUtils.getInstance().getCurrentMills();
        WKUploader.getInstance().upload(url, filePath, new WKUploader.IUploadBack() {
            @Override
            public void onSuccess(String url) {
                iUploadBack.onResult(HttpResponseCode.success);
            }

            @Override
            public void onError() {
                iUploadBack.onResult(HttpResponseCode.error);
            }
        });
    }

    public interface IUploadBack {
        void onResult(int code);
    }

    public void updateUserInfo(String key, String value, final ICommonListener iCommonLisenter) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(key, value);
        request(createService(LoginService.class).updateUserInfo(jsonObject), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonLisenter.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonLisenter.onResult(code, msg);
            }
        });
    }

    public void sendLoginAuthVerifCode(String uid, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("uid", uid);
        request(createService(LoginService.class).sendLoginAuthVerifCode(jsonObject), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    /**
     * 设备锁安全校验-检查手机号是否正确
     *
     * @param uid            用户ID
     * @param code           验证码
     * @param iLoginListener 返回
     */
    void checkLoginAuth(String uid, String code, final ILoginListener iLoginListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("uid", uid);
        jsonObject.put("code", code);
        request(createService(LoginService.class).checkLoginAuth(jsonObject), new IRequestResultListener<UserInfoEntity>() {
            @Override
            public void onSuccess(UserInfoEntity userInfo) {
                if (userInfo != null) {
                    saveLoginInfo(userInfo);
                    iLoginListener.onResult(HttpResponseCode.success, "", userInfo);
                }
            }

            @Override
            public void onFail(int code, String msg) {
                iLoginListener.onResult(code, msg, null);
            }
        });
    }

    public void quitPc(final ICommonListener iCommonListener) {
        request(createService(LoginService.class).quitPc(), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }


    public void updateUserSetting(String key, int value, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(key, value);
        request(createService(LoginService.class).setting(jsonObject), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    public void getAuthCode(final IAuthCode iAuthCode) {
        request(createService(LoginService.class).getAuthCode(), new IRequestResultListener<>() {
            @Override
            public void onSuccess(ThirdAuthCode result) {
                iAuthCode.onResult(HttpResponseCode.success, "", result.getAuthcode());
            }

            @Override
            public void onFail(int code, String msg) {
                iAuthCode.onResult(code, msg, "");
            }
        });
    }

    public interface IAuthCode {
        void onResult(int code, String msg, String authCode);
    }

    public void getAuthCodeStatus(String authCode, final ICommonListener iCommonListener) {
        request(createService(LoginService.class).getAuthStatus(authCode), new IRequestResultListener<ThirdLoginResult>() {
            @Override
            public void onSuccess(ThirdLoginResult result) {
                if (result.getStatus() == 1 && result.getResult() != null) {
                    saveLoginInfo(result.getResult());
                    iCommonListener.onResult(HttpResponseCode.success, "");
                }
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    private void saveLoginInfo(UserInfoEntity userInfo) {
        WKConfig.getInstance().saveUserInfo(userInfo);
        WKConfig.getInstance().setToken(userInfo.token);
        if (!TextUtils.isEmpty(userInfo.im_token)) {
            WKConfig.getInstance().setImToken(userInfo.im_token);
        } else WKConfig.getInstance().setImToken(userInfo.token);
        WKConfig.getInstance().setUid(userInfo.uid);
        WKConfig.getInstance().setUserName(userInfo.name);
    }
}
