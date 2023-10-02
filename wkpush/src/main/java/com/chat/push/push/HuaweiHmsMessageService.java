package com.chat.push.push;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.chat.base.WKBaseApplication;
import com.chat.base.utils.WKDeviceUtils;
import com.chat.push.service.PushModel;
import com.huawei.hms.push.HmsMessageService;

/**
 * 2020-03-08 22:10
 * 华为推送服务
 */
public class HuaweiHmsMessageService extends HmsMessageService {
    @Override
    public void onNewToken(String s, Bundle bundle) {
        super.onNewToken(s, bundle);
        if (!TextUtils.isEmpty(s)) {
            String packageName = WKDeviceUtils.getInstance().getPackageName(WKBaseApplication.getInstance().getContext());
            PushModel.getInstance().registerDeviceToken(s, packageName,"service-华为");
        }
    }
}
