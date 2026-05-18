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

package com.chat.base.base;

import com.chat.base.net.BaseObserver;
import com.chat.base.net.IRequestResultErrorInfoListener;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.RetrofitUtils;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableTransformer;
import io.reactivex.rxjava3.schedulers.Schedulers;


/**
 * 2020-07-17 15:18
 * 基础网络model
 */
public class WKBaseModel {
    protected <T> void request(Observable<T> observable, final IRequestResultListener<T> iRequestResultListener) {
        quest(observable).subscribe(new BaseObserver<T>() {

            @Override
            protected void onSuccess(T result) {
                iRequestResultListener.onSuccess(result);
            }

            @Override
            protected void onFail(int code, String msg, String errJson) {
                iRequestResultListener.onFail(code, msg);
            }
        });
    }

    protected <T> void requestAndErrorBack(Observable<T> observable, final IRequestResultErrorInfoListener<T> iRequestResultErrorInfoListener) {
        quest(observable).subscribe(new BaseObserver<T>() {
            @Override
            protected void onSuccess(T result) {
                iRequestResultErrorInfoListener.onSuccess(result);
            }

            @Override
            protected void onFail(int code, String msg, String errJson) {
                iRequestResultErrorInfoListener.onFail(code, msg, errJson);
            }
        });
    }

    protected static <T> T createService(Class<T> service) {
        return RetrofitUtils.getInstance().createService(service);
    }

    private static <T> ObservableTransformer<T, T> handle() {
        return upstream -> upstream
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }


    private static <T> Observable<T> quest(Observable<T> observable) {
        return observable.compose(handle());
    }

}
