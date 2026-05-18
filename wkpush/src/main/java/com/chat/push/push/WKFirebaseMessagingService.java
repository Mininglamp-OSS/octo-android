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

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.chat.push.WKPushApplication;
import com.chat.push.service.PushModel;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class WKFirebaseMessagingService extends FirebaseMessagingService {

    //监控令牌的生成
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.e("获取到FCM令牌111", token);
        if (!TextUtils.isEmpty(token)) {
            PushModel.getInstance().registerDeviceToken(token, WKPushApplication.getInstance().pushBundleID, "FIREBASE");
        }
    }

    //监控推送的消息
    @Override
    public void onMessageReceived(@NonNull RemoteMessage msg) {
        super.onMessageReceived(msg);
        Log.e("收到Firebase推送消息", msg.getFrom());
    }

}
