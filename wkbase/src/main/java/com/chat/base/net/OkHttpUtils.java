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

import android.util.Log;

import com.chat.base.WKBaseApplication;
import com.chat.base.emoji.EmojiManifestBodyLimitInterceptor;
import com.chat.base.search.global.SearchGlobalBodyLimitInterceptor;
import com.chat.base.utils.WKNetUtil;

import java.io.File;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.X509TrustManager;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.ConnectionPool;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 2020-07-17 14:55
 */
public class OkHttpUtils {
    private OkHttpUtils() {
    }

    private static class OkHttpUtilsBinder {
        final static OkHttpUtils okHttp = new OkHttpUtils();
    }

    public static OkHttpUtils getInstance() {
        return OkHttpUtilsBinder.okHttp;
    }

    private OkHttpClient sOkHttpClient;
    //缓存天数
    private static final long CACHE_STALE_SEC = 60 * 60 * 24 * 2;

    public OkHttpClient getOkHttpClient() {
        if (sOkHttpClient == null) {
            synchronized (OkHttpUtils.class) {
                Cache cache = new Cache(new File(WKBaseApplication.getInstance().getContext().getCacheDir(), "HttpCache"),
                        1024 * 1024 * 100);
                if (sOkHttpClient == null) {
                    sOkHttpClient = new OkHttpClient.Builder().cache(cache)
                            .connectTimeout(60 * 10, TimeUnit.SECONDS)
                            .readTimeout(60 * 10, TimeUnit.SECONDS)
                            .writeTimeout(60 * 10, TimeUnit.SECONDS).connectionPool(new ConnectionPool(5, 5, TimeUnit.SECONDS))
                            .sslSocketFactory(SSLSocketClient.getSSLSocketFactory(), new X509TrustManager() {
                                @Override
                                public void checkClientTrusted(X509Certificate[] chain, String authType) {

                                }

                                @Override
                                public void checkServerTrusted(X509Certificate[] chain, String authType) {

                                }

                                @Override
                                public X509Certificate[] getAcceptedIssuers() {
                                    return new X509Certificate[0];
                                }
                            })
                            .hostnameVerifier(SSLSocketClient.getHostnameVerifier())
                            .addInterceptor(mRewriteCacheControlInterceptor)
                            .addInterceptor(new CommonRequestParamInterceptor())
                            .addNetworkInterceptor(mRewriteCacheControlInterceptor)
                            // LogInterceptor 只在 debug 生效（内部 WKBinder.isDebug 判定，release 走
                            // chain.proceed 直通）。它对 body 有 256KB 上界：小 body 照旧全打，大 body
                            // 只打大小——原实现会把整个响应体物化五六份，debug 包凭空多吃 80MB+，
                            // 既带偏堆测量，又把 logcat 缓冲区冲爆。详见 LogInterceptor.MAX_LOG_BODY_BYTES。
                            .addInterceptor(new LogInterceptor())
                            // Emoji manifest 端点公开无鉴权，parse 前先做 raw body 字节上限
                            // 检查，防 hostile / MITM 推巨大 body → FastJson buffer 后 OOM。
                            // 只对 URL 路径末尾 common/emojis 生效，不影响其它接口。
                            //
                            // ⚠️ 必须放在 LogInterceptor 之后（即 list 末尾，最内层）：
                            // application interceptor 按 list order 由外到内包装，list 末尾的
                            // interceptor 最先看到 response，能在任何人读 body 之前先做大小检查。
                            // （LogInterceptor 现在只 peek 最多 256KB，不再整包读，但这个顺序仍是
                            // 正确的：让上限检查处在最内层，谁都别想先碰到超大 body。）
                            // 参考 PR #94 Jerry-Xin round-3 review B2。
                            .addInterceptor(new EmojiManifestBodyLimitInterceptor())
                            // 全局搜索 L1/L2 端点也做 body 大小上限（同 Emoji 拦截器语义），
                            // 挂在 LogInterceptor 之后避免 Log 先 body().string() OOM。
                            // PR #95 review 主要级 - OctoBoooot: 新聚合端点缺 body cap。
                            .addInterceptor(new SearchGlobalBodyLimitInterceptor()).build();
                }
            }
        }
        return sOkHttpClient;
    }

    private final Interceptor mRewriteCacheControlInterceptor = chain -> {
        Request request = chain.request();
        if (!WKNetUtil.isNetworkAvailable(WKBaseApplication.getInstance().getContext())) {
            request = request.newBuilder()
                    .cacheControl(CacheControl.FORCE_CACHE)
                    .build();
            Log.e("无网络连接：", "------->");
        }
        Response originalResponse = chain.proceed(request);
        if (WKNetUtil.isNetworkAvailable(WKBaseApplication.getInstance().getContext())) {
            //有网的时候读接口上的@Headers里的配置，你可以在这里进行统一的设置
            String cacheControl = request.cacheControl().toString();
            return originalResponse.newBuilder()
                    .header("Cache-Control", cacheControl)
                    .removeHeader("Pragma")
                    .build();
        } else {
            return originalResponse.newBuilder()
                    .header("Cache-Control", "public, only-if-cached, max-stale=" + CACHE_STALE_SEC)
                    .removeHeader("Pragma")
                    .build();
        }
    };

}
