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

import com.chat.base.WKBaseApplication;
import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.utils.ActManagerUtils;
import com.xinbida.wukongim.WKIM;

import org.jetbrains.annotations.NotNull;

import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;


/**
 * 2020-07-17 15:21
 * 服务器返回的状态是在http的状态码上
 */
public abstract class BaseObserver<T> implements Observer<T> {
    @Override
    public void onComplete() {
    }

    @Override
    public void onNext(@NotNull T t) {
        //这里直接返回服务器的结果，因为该结果就是你需要的数据。无需在获取data，code，msg啥的了，给后端点个赞
        onSuccess(t);
    }

    @Override
    public void onError(@NotNull Throwable e) {
        ResponseThrowable throwable = ResponseExceptionHandle.getInstance().handleException(e);
        if (throwable != null) {
            String msg = throwable.getMessage();
            if (TextUtils.isEmpty(msg)) msg = "";
            String errJson = throwable.getErrJson();
            if (TextUtils.isEmpty(errJson)) errJson = "";
            if (throwable.getCode() == 401) {
                // 如果 token 已清空（说明已在退出流程中），跳过重复处理
                if (TextUtils.isEmpty(WKConfig.getInstance().getToken())) {
                    return;
                }
                onFail(throwable.getCode(), msg, errJson);
                //关闭UI层数据库
                WKBaseApplication.getInstance().closeDbHelper();
                WKConfig.getInstance().clearInfo();
                WKIM.getInstance().getConnectionManager().disconnect(true);
                ActManagerUtils.getInstance().clearAllActivity();
                EndpointManager.getInstance().invoke("main_show_home_view",0);
            } else {
                onFail(throwable.getCode(), msg, errJson);
            }
        }
    }

    @Override
    public void onSubscribe(@NotNull Disposable d) {
    }

    protected abstract void onSuccess(T result);

    protected abstract void onFail(int code, String msg, String errJson);
}
