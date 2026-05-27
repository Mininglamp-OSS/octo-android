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

package com.chat.base.net;

import android.text.TextUtils;
import android.util.Log;

import com.chat.base.R;
import com.chat.base.WKBaseApplication;
import com.chat.base.utils.WKLogUtils;

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
                                WKLogUtils.e("解析请求【400】不是json结构");
                            }
                        } else {
                            responeThrowable.setMessage("");
                            responeThrowable.setStatus(400);
                        }
                    } catch (IOException ex) {
                        WKLogUtils.e("解析请求【400】结果错误");
                    }
                    break;
                case 401:
                    responeThrowable.setMessage(WKBaseApplication.getInstance().getContext().getString(R.string.error_auth_failed));
                    break;
                case 404:
                    responeThrowable.setMessage(WKBaseApplication.getInstance().getContext().getString(R.string.error_not_found));
                    break;
                case 405:
                case 403:
                case 500:
                case 502:
                case 503:
                    // 尝试解析 body 中的 msg
                    try {
                        String errBody = Objects.requireNonNull(Objects.requireNonNull(httpException.response()).errorBody()).string();
                        if (!TextUtils.isEmpty(errBody)) {
                            JSONObject errObj = new JSONObject(errBody);
                            String errMsg = errObj.optString("msg");
                            if (!TextUtils.isEmpty(errMsg)) {
                                responeThrowable.setMessage(errMsg);
                                break;
                            }
                        }
                    } catch (Exception ignored) {}
                    responeThrowable.setMessage(String.format(WKBaseApplication.getInstance().getContext().getString(R.string.error_request_failed), httpException.code()));
                    break;
                case 504:
                    responeThrowable.setMessage(WKBaseApplication.getInstance().getContext().getString(R.string.error_network_failed));
                    break;
            }
        } else {
            responeThrowable = new ResponseThrowable(e, -1);
            String msg = e.getMessage();
            responeThrowable.setMessage(!TextUtils.isEmpty(msg) ? msg : WKBaseApplication.getInstance().getContext().getString(R.string.error_network_exception));
        }
        return responeThrowable;
    }
}
