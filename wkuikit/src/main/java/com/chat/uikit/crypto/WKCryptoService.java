package com.chat.uikit.crypto;

import com.alibaba.fastjson.JSONObject;
import com.chat.uikit.enity.WKSignalData;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface WKCryptoService {
    @POST("user/signal/getkey")
    Observable<WKSignalData> getUserSignalData(@Body JSONObject jsonObject);
}
