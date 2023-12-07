package com.chat.base.net;

import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;

import retrofit2.HttpException;

/**
 * 2020-07-20 14:23
 * 请求异常处理
 */
public class ResponseExceptionHandle {
    private ResponseExceptionHandle() {
    }

    private static class ResponseExceptionHandleBinder {
        final static ResponseExceptionHandle response = new ResponseExceptionHandle();
    }

    public static ResponseExceptionHandle getInstance() {
        return ResponseExceptionHandleBinder.response;
    }

    public ResponseThrowable handleException(Throwable e) {
        ResponseThrowable responeThrowable = null;
        if (e instanceof HttpException) {
            HttpException httpException = (HttpException) e;

            responeThrowable = new ResponseThrowable(e, httpException.code());
            switch (httpException.code()) {
                case 400:
                    try {
                        String errorStr = Objects.requireNonNull(Objects.requireNonNull(httpException.response()).errorBody()).string();
                        if (!TextUtils.isEmpty(errorStr)) {
                            try {
                                Log.e("错误信息：", errorStr);
                                JSONObject jsonObject = new JSONObject(errorStr);
                                int status = jsonObject.optInt("status");
                                String msg = jsonObject.optString("msg");
                                responeThrowable.setMessage(msg);
                                responeThrowable.setErrJson(errorStr);
                                responeThrowable.setStatus(status);
                                Log.e("请求错误：", status + "|" + msg);
                            } catch (JSONException ex) {
                                ex.printStackTrace();
                            }
                        } else {
                            responeThrowable.setMessage("");
                            responeThrowable.setStatus(400);
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    break;
                case 401:
                    responeThrowable.setMessage("认证失败");
                    break;
                case 404:
                    responeThrowable.setMessage("请求地址不存在");
                    break;
                case 504:
                    responeThrowable.setMessage("网络连接失败");
                    break;
            }
        } else if (e instanceof RuntimeException) {
            Log.e("服务器运行时错误：", e.getMessage());
        }
        return responeThrowable;
    }
}
