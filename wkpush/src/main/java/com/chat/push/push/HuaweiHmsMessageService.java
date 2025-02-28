package com.chat.push.push;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.chat.base.WKBaseApplication;
import com.chat.base.utils.WKDeviceUtils;
import com.chat.push.WKPushApplication;
import com.chat.push.service.PushModel;
import com.huawei.hms.push.HmsMessageService;
import com.huawei.hms.push.RemoteMessage;

/**
 * 2020-03-08 22:10
 * 华为推送服务
 */
public class HuaweiHmsMessageService extends HmsMessageService {
    @Override
    public void onNewToken(String s, Bundle bundle) {
        super.onNewToken(s, bundle);
        if (!TextUtils.isEmpty(s)) {
            PushModel.getInstance().registerDeviceToken(s, WKPushApplication.getInstance().pushBundleID,"");
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.e("收到偷穿消息",remoteMessage.getData());
    }
}
