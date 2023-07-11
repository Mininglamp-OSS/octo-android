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
