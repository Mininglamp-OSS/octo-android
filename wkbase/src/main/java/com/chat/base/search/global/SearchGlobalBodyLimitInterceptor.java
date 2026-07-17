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

package com.chat.base.search.global;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.List;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 对全局聚合搜索两个端点的 HTTP 响应做 raw-body 字节上限检查，防止 hostile / 误配服务端
 * 或异常 payload 让 Retrofit + FastJson buffer 全量后 OOM。
 *
 * <p>覆盖端点（严格路径匹配，防子路径误命中）：
 * <ul>
 *   <li>{@code POST /v1/messages/_search_global_groups} (L1)</li>
 *   <li>{@code POST /v1/messages/_search_global_messages} (L2)</li>
 * </ul>
 *
 * <p>不覆盖 {@code _search_global_files}（未接入）以及其它 {@code /messages/_search*}
 * 频道内端点（走 auth 且响应可控）。同 {@link com.chat.base.emoji.EmojiManifestBodyLimitInterceptor}
 * 一样必须挂在 {@code LogInterceptor} 之后（list 末尾，最内层），否则 LogInterceptor
 * 已经 body().string() 全量读完，本 interceptor 检查太晚。
 *
 * <p>双保险策略：先看 {@code Content-Length}，无 length / chunked 时再用
 * {@link ResponseBody#peekBody(long)} peek 到上限 + 1 字节。
 *
 * <p>上限 2 MB：
 * <ul>
 *   <li>L1 服务端上限 maxGroups=200，每桶 preview 最多 20 条，snippet + 元信息
 *       约 500B/条 → ~2MB 上限估算</li>
 *   <li>L2 page_size max=100，每条含合并转发 inner_messages 可能 5KB → ~500KB 上限估算</li>
 * </ul>
 * 保守取 2MB，命中即拒（Retrofit onFail → UI FALLBACK_TO_LOCAL 走本地兜底）。
 */
public final class SearchGlobalBodyLimitInterceptor implements Interceptor {

    /** 2 MB。参见 KDoc 中的上限估算。 */
    static final long MAX_SEARCH_BODY_BYTES = 2L * 1024L * 1024L;

    private static final String ENDPOINT_L1 = "_search_global_groups";
    private static final String ENDPOINT_L2 = "_search_global_messages";

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        if (!isTargetUrl(request)) {
            return chain.proceed(request);
        }
        Response response = chain.proceed(request);
        ResponseBody body = response.body();
        if (body == null) {
            return response;
        }

        // 快路径：Content-Length 已知且超上限直接拒
        long contentLength = body.contentLength();
        if (contentLength > MAX_SEARCH_BODY_BYTES) {
            response.close();
            throw new IOException("search global body too large: Content-Length="
                    + contentLength + " > " + MAX_SEARCH_BODY_BYTES);
        }

        // 慢路径：Content-Length 缺失 / chunked / 服务端说谎——peek 上限+1 字节
        ResponseBody peeked = response.peekBody(MAX_SEARCH_BODY_BYTES + 1);
        byte[] bytes = peeked.bytes();
        if (bytes.length > MAX_SEARCH_BODY_BYTES) {
            response.close();
            throw new IOException("search global body exceeds "
                    + MAX_SEARCH_BODY_BYTES + " bytes");
        }

        // 已 peek 到的字节复用为新 body，让下游 Retrofit 消费
        MediaType contentType = body.contentType();
        response.close();
        return response.newBuilder()
                .body(ResponseBody.create(bytes, contentType))
                .build();
    }

    /** 匹配 URL 路径末尾为 {@code messages/_search_global_groups}
     *  或 {@code messages/_search_global_messages}——严格路径匹配。 */
    private static boolean isTargetUrl(Request request) {
        List<String> segments = request.url().pathSegments();
        int n = segments.size();
        if (n < 2 || !"messages".equals(segments.get(n - 2))) {
            return false;
        }
        String last = segments.get(n - 1);
        return ENDPOINT_L1.equals(last) || ENDPOINT_L2.equals(last);
    }
}
