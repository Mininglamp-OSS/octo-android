package com.chat.base.okgo;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.lzy.okgo.convert.Converter;

import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 2020-06-19 10:02
 * 上传返回
 */
public class UploadResultCovert implements Converter<UploadResultEntity> {
    @Override
    public UploadResultEntity convertResponse(Response response) throws Throwable {
        ResponseBody body = response.body();
        if (body == null) return null;
        String result = body.string();
        if (!TextUtils.isEmpty(result)) {
            return new Gson().fromJson(result, UploadResultEntity.class);
        } else return null;
    }
}
