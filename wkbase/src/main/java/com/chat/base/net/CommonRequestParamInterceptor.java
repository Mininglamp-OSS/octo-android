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

import android.os.Build;
import android.text.TextUtils;

import com.chat.base.WKBaseApplication;
import com.chat.base.config.WKConfig;
import com.chat.base.space.SpaceFilter;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 2020-07-17 15:08
 * 公共请求参数
 *
 * <p>EP3 · ：动态注入 {@code X-Space-Id} header，Space 切换时实时生效。
 * 对齐  #1039，后端 {@code } 过滤分支强依赖该 header。
 */
public class CommonRequestParamInterceptor implements Interceptor {

    /** 当前生效 Space header 名（与 {@code APIClient} / 后端 {@code SetEffectiveSpaceID} 对齐）。 */
    public static final String HEADER_SPACE_ID = "X-Space-Id";

    @NotNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Request.Builder builder = request.newBuilder();
        Map<String, String> commonParams = getCommonParams();
        for (Map.Entry<String, String> entry : commonParams.entrySet()) {
            if (!TextUtils.isEmpty(entry.getValue())) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
        }

        // 每次请求动态读取当前 Space，Space 切换后立即生效；空串不注入。
        String currentSpaceId = SpaceFilter.getCurrentSpaceId();
        if (!TextUtils.isEmpty(currentSpaceId)) {
            // removeHeader 防止业务层显式覆盖后重复注入
            builder.removeHeader(HEADER_SPACE_ID);
            builder.addHeader(HEADER_SPACE_ID, currentSpaceId);
        }

        request = builder.build();
        return chain.proceed(request);
    }

    private Map<String, String> getCommonParams() {
        Map<String, String> mCommonParams = new HashMap<>();
        mCommonParams.put("token", WKConfig.getInstance().getToken());
        mCommonParams.put("model", Build.MODEL);
        mCommonParams.put("os", "Android");
        mCommonParams.put("appid", WKBaseApplication.getInstance().appID);
        mCommonParams.put("version", WKBaseApplication.getInstance().versionName);
        mCommonParams.put("package", WKBaseApplication.getInstance().packageName);
        return mCommonParams;
    }
}
