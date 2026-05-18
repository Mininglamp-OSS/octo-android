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
