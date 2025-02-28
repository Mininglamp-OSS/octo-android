package com.chat.push.push;

import android.content.Context;
import android.util.Log;

import com.chat.base.utils.WKDeviceUtils;
import com.chat.push.WKPushApplication;
import com.chat.push.service.PushModel;
import com.vivo.push.model.UPSNotificationMessage;
import com.vivo.push.sdk.OpenClientPushMessageReceiver;

public class VivoPushMessageReceiverImpl extends OpenClientPushMessageReceiver {

    @Override
    public void onReceiveRegId(Context context, String regId) {
        super.onReceiveRegId(context, regId);
        Log.e("注册vivo推送",regId);
        PushModel.getInstance().registerDeviceToken(regId, WKPushApplication.getInstance().pushBundleID,"");
    }

    @Override
    public void onNotificationMessageClicked(Context context, UPSNotificationMessage msg) {
        super.onNotificationMessageClicked(context, msg);
    }
}
