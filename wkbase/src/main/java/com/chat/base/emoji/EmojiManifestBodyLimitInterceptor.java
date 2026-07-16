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

package com.chat.base.emoji;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.List;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 对 {@code GET /v1/common/emojis} 的 HTTP 响应做 raw-body 字节上限检查，防止 hostile
 * 或误配的服务端（此端点<b>公开无鉴权</b>）推巨大 body → Retrofit + FastJson buffer
 * 全内容后 parse 时 OOM。
 *
 * <p>Sanitizer 的 {@code MAX_ITEMS} / {@code MAX_URL_LEN} 都是**对象已解出后**才生效，
 * 挡不住 parse-time OOM。此拦截器补齐这个前置边界（PR #94 Jerry-Xin C / yujiawei P1）。
 *
 * <p><b>作用范围严格限定</b>：只对 URL 路径末尾是 {@code common/emojis} 的请求生效，
 * 不影响其它接口的 body 大小行为（避免"全局 body 上限"式误伤，如文件下载/大分页列表）。
 *
 * <p>双保险策略：
 * <ol>
 *   <li>先读 {@code Content-Length} header（cheap 检查，直接判断上限）；</li>
 *   <li>无 Content-Length 或 chunked 编码下再用 {@link ResponseBody#peekBody(long)}
 *       peek 到上限 + 1 字节，长度超过上限则拒绝。</li>
 * </ol>
 *
 * <p>命中上限：关闭响应体、抛 {@link IOException}——Retrofit 收到后由
 * {@code WKCommonModel.getEmojis} 的 {@code onFail} 兜底，本地保留 xml + 上次缓存。
 */
public final class EmojiManifestBodyLimitInterceptor implements Interceptor {

    /** 1 MB。当前服务端 manifest 4 项 url="" 全部约 200 字节；这是 5000× 余量的合理上限。
     *  即使未来加带 CDN URL 的表情，单条 URL 2KB × 500 项理论上限也才 1MB。 */
    static final long MAX_MANIFEST_BODY_BYTES = 1024L * 1024L;

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        if (!isEmojiManifestUrl(request)) {
            return chain.proceed(request);
        }
        Response response = chain.proceed(request);
        ResponseBody body = response.body();
        if (body == null) {
            return response;
        }

        // 快路径：Content-Length 已知且超上限直接拒
        long contentLength = body.contentLength();
        if (contentLength > MAX_MANIFEST_BODY_BYTES) {
            response.close();
            throw new IOException("emoji manifest body too large: Content-Length="
                    + contentLength + " > " + MAX_MANIFEST_BODY_BYTES);
        }

        // 慢路径：Content-Length 缺失 / chunked / 服务端说谎——peek 上限+1 字节
        // peekBody 在 body 更大时会返回**至多** byteCount 字节，所以 byteCount = MAX+1 时
        // 拿到的字节数 > MAX 说明真实 body 至少有 MAX+1 → 超限。
        ResponseBody peeked = response.peekBody(MAX_MANIFEST_BODY_BYTES + 1);
        byte[] bytes = peeked.bytes();
        if (bytes.length > MAX_MANIFEST_BODY_BYTES) {
            response.close();
            throw new IOException("emoji manifest body exceeds " + MAX_MANIFEST_BODY_BYTES + " bytes");
        }

        // 用已 peek 到的字节重建 response body（原 body 已随 peek 处于可再消费但等价的状态；
        // 但严谨起见我们把 peek 到的字节直接作为新 body，让下游 Retrofit 消费）。
        MediaType contentType = body.contentType();
        response.close();
        return response.newBuilder()
                .body(ResponseBody.create(bytes, contentType))
                .build();
    }

    /** 匹配 URL 路径末尾为 {@code common/emojis}——严格路径匹配，防子路径误命中。 */
    private static boolean isEmojiManifestUrl(Request request) {
        List<String> segments = request.url().pathSegments();
        int n = segments.size();
        return n >= 2
                && "emojis".equals(segments.get(n - 1))
                && "common".equals(segments.get(n - 2));
    }
}
