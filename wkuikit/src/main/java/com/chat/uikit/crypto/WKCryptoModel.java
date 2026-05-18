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

package com.chat.uikit.crypto;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.IRequestResultListener;
import com.chat.uikit.enity.WKSignalData;
import com.xinbida.wukongim.entity.WKChannelType;

public class WKCryptoModel extends WKBaseModel {
    private WKCryptoModel() {
    }

    private static class CryptoModelBinder {
        final static WKCryptoModel model = new WKCryptoModel();
    }

    public static WKCryptoModel getInstance() {
        return CryptoModelBinder.model;
    }

    public void getUserKey(String uid, final @NonNull ISignalData iSignalData) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("channel_id", uid);
        jsonObject.put("channel_type", WKChannelType.PERSONAL);
        request(createService(WKCryptoService.class).getUserSignalData(jsonObject), new IRequestResultListener<WKSignalData>() {
            @Override
            public void onSuccess(WKSignalData result) {
                iSignalData.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                iSignalData.onResult(code, msg, null);
            }
        });
    }

   public interface ISignalData {
        void onResult(int code, String msg, WKSignalData data);
    }
}
