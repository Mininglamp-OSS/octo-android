package com.chat.push.service;

import android.text.TextUtils;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.RegisterPushToken;
import com.chat.base.net.ICommonListener;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.push.OsUtils;

/**
 * 2020-03-08 22:28
 * 推送管理
 */
public class PushModel extends WKBaseModel {

    private PushModel() {

    }

    private static class PushModelBinder {
        static final PushModel pushModel = new PushModel();
    }

    public static PushModel getInstance() {
        return PushModelBinder.pushModel;
    }

    /**
     * 注册设备推送token
     *
     * @param token     token
     * @param bundle_id Android为包名称
     */
    public void registerDeviceToken(String token, String bundle_id,String from) {
        Log.e("注册来源",from);
        String device_type = "";
        if (OsUtils.isEmui()) {
            device_type = "HMS";
        } else if (OsUtils.isMiui())
            device_type = "MI";
        else if (OsUtils.isOppo()) {
            device_type = "OPPO";
        } else if (OsUtils.isVivo()) {
            device_type = "VIVO";
        }
        //   EndpointManager.getInstance().invoke("register_push_token", new RegisterPushToken(device_type, token));
        JSONObject httpParams = new JSONObject();
        httpParams.put("device_token", token);
        httpParams.put("device_type", device_type);
        httpParams.put("bundle_id", bundle_id);
        request(createService(PushService.class).registerAppToken(httpParams), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                Log.e("注册push", result.status + "");
            }

            @Override
            public void onFail(int code, String msg) {
            }
        });

    }

    /**
     * 注销推送token
     */
    public void unRegisterDeviceToken(final ICommonListener iCommonListener) {
        request(createService(PushService.class).unRegisterAppToken(), new IRequestResultListener<CommonResponse>() {
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
     * 注册红点数量
     *
     * @param badge 数量
     */
    public void registerBadge(int badge) {
        if (TextUtils.isEmpty(WKConfig.getInstance().getToken())) return;
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("badge", badge);
        request(createService(PushService.class).registerBadge(jsonObject), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
            }

            @Override
            public void onFail(int code, String msg) {
            }
        });
    }
}
