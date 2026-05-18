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

import com.chat.base.config.WKApiConfig;

import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;

/**
 * 2020-07-17 14:52
 * Retrofit管理
 */
public class RetrofitUtils {
    private RetrofitUtils() {
    }

    private static class RetrofitUtilsBinder {
        final static RetrofitUtils retrofit = new RetrofitUtils();
    }

    public static RetrofitUtils getInstance() {
        return RetrofitUtilsBinder.retrofit;
    }

    private Retrofit retrofit;

    public Retrofit getRetrofit() {
        if (retrofit == null) {
            synchronized (RetrofitUtils.class) {
                retrofit = new Retrofit.Builder()
                        .baseUrl(WKApiConfig.baseUrl)
                        .client(OkHttpUtils.getInstance().getOkHttpClient())
                        .addConverterFactory(FastJsonConverterFactory.Companion.create())
                        .addCallAdapterFactory(RxJava3CallAdapterFactory.create()).build();
            }
            // GsonConverterFactory.create(new GsonBuilder().setLenient().create())
        }
        return retrofit;
    }

    public void resetRetrofit() {
        retrofit = null;
    }


    public <T> T createService(Class<T> service) {
        return getRetrofit().create(service);
    }

}
