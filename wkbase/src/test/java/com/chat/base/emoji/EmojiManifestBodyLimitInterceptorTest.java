/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.base.emoji;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 用手写 fake {@link Interceptor.Chain} 覆盖 {@link EmojiManifestBodyLimitInterceptor}
 * 的行为——不引入 mockwebserver 依赖，纯 JVM 单测。
 *
 * <p>核心保证：
 * <ol>
 *   <li>非 emoji URL 请求原样透传</li>
 *   <li>emoji URL Content-Length 超上限 → 抛 IOException（快路径）</li>
 *   <li>emoji URL Content-Length 缺失但实际 body 超上限 → 抛 IOException（peek 兜底）</li>
 *   <li>emoji URL body 在上限内 → 正常返回</li>
 * </ol>
 */
public class EmojiManifestBodyLimitInterceptorTest {

    private static final MediaType JSON = MediaType.get("application/json");

    private static Request req(String url) {
        return new Request.Builder().url(url).build();
    }

    private static Response ok(Request request, ResponseBody body) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body)
                .build();
    }

    /** 允许 declaredContentLength 传 -1 以模拟 "服务端没给 Content-Length"（chunked）。 */
    private static ResponseBody bodyWith(byte[] bytes, long declaredContentLength) {
        if (declaredContentLength < 0) {
            // 走 create(byte[], MediaType) 的默认 —— contentLength() 返 bytes.length。
            // OkHttp 没有"不带 length"的直接构造器；测试时用一个包装类模拟 -1。
            return new ResponseBody() {
                @Override public MediaType contentType() { return JSON; }
                @Override public long contentLength() { return -1L; }
                @Override public okio.BufferedSource source() {
                    okio.Buffer buf = new okio.Buffer();
                    buf.write(bytes);
                    return buf;
                }
            };
        }
        return ResponseBody.create(bytes, JSON);
    }

    private static class FakeChain implements Interceptor.Chain {
        final Request request;
        final Response response;

        FakeChain(Request request, Response response) {
            this.request = request;
            this.response = response;
        }

        @Override public Request request() { return request; }
        @Override public Response proceed(Request r) throws IOException { return response; }
        @Override public Connection connection() { return null; }
        @Override public Call call() { throw new UnsupportedOperationException(); }
        @Override public int connectTimeoutMillis() { return 0; }
        @Override public Interceptor.Chain withConnectTimeout(int t, TimeUnit u) { return this; }
        @Override public int readTimeoutMillis() { return 0; }
        @Override public Interceptor.Chain withReadTimeout(int t, TimeUnit u) { return this; }
        @Override public int writeTimeoutMillis() { return 0; }
        @Override public Interceptor.Chain withWriteTimeout(int t, TimeUnit u) { return this; }
    }

    // ---- 非 emoji URL：原样透传 ----

    @Test
    public void non_emoji_url_passes_through_untouched() throws IOException {
        byte[] payload = new byte[(int) EmojiManifestBodyLimitInterceptor.MAX_MANIFEST_BODY_BYTES + 100];
        Request request = req("https://example.com/v1/common/other-endpoint");
        Response response = ok(request, bodyWith(payload, payload.length));

        Response out = new EmojiManifestBodyLimitInterceptor().intercept(new FakeChain(request, response));

        // 非 emoji URL 不做任何检查，即使超上限也应原样返回
        assertNotNull(out.body());
        assertEquals(payload.length, out.body().contentLength());
    }

    // ---- emoji URL + Content-Length 明确超上限 → 快路径抛异常 ----

    @Test
    public void emoji_url_with_oversized_content_length_throws() {
        byte[] payload = new byte[(int) EmojiManifestBodyLimitInterceptor.MAX_MANIFEST_BODY_BYTES + 10];
        Request request = req("https://example.com/v1/common/emojis");
        Response response = ok(request, bodyWith(payload, payload.length));

        try {
            new EmojiManifestBodyLimitInterceptor().intercept(new FakeChain(request, response));
            fail("expected IOException on oversized Content-Length");
        } catch (IOException e) {
            assertEquals(true, e.getMessage().contains("Content-Length"));
        }
    }

    // ---- emoji URL + Content-Length 缺失（-1）但实际超上限 → peek 兜底抛异常 ----

    @Test
    public void emoji_url_chunked_oversized_body_throws() {
        byte[] payload = new byte[(int) EmojiManifestBodyLimitInterceptor.MAX_MANIFEST_BODY_BYTES + 10];
        Request request = req("https://example.com/v1/common/emojis");
        Response response = ok(request, bodyWith(payload, -1)); // chunked

        try {
            new EmojiManifestBodyLimitInterceptor().intercept(new FakeChain(request, response));
            fail("expected IOException on oversized chunked body");
        } catch (IOException e) {
            assertEquals(true, e.getMessage().contains("exceeds"));
        }
    }

    // ---- emoji URL + body 在上限内 → 正常返回，body 内容保持一致 ----

    @Test
    public void emoji_url_within_limit_passes_through() throws IOException {
        String json = "{\"version\":1,\"list\":[{\"key\":\"[a]\",\"name\":\"a\",\"url\":\"\"}]}";
        byte[] payload = json.getBytes();
        Request request = req("https://example.com/v1/common/emojis");
        Response response = ok(request, bodyWith(payload, payload.length));

        Response out = new EmojiManifestBodyLimitInterceptor().intercept(new FakeChain(request, response));

        assertNotNull(out.body());
        assertArrayEquals(payload, out.body().bytes());
    }

    // ---- URL 路径匹配严格：`.../common/emojis-and-more` 不该被误命中 ----

    @Test
    public void url_path_match_is_strict() throws IOException {
        byte[] payload = new byte[(int) EmojiManifestBodyLimitInterceptor.MAX_MANIFEST_BODY_BYTES + 100];
        Request request = req("https://example.com/v1/common/emojis-and-more"); // 前缀相同但非 emojis
        Response response = ok(request, bodyWith(payload, payload.length));

        Response out = new EmojiManifestBodyLimitInterceptor().intercept(new FakeChain(request, response));

        // 不该被拦截
        assertEquals(payload.length, out.body().contentLength());
    }

    // ---- 组合契约（Jerry-Xin round-3 B2）：模拟 LogInterceptor 作为外层调 body().string()
    //      时，BodyLimit 作为内层要能在外层读 body 之前 short-circuit（否则外层 OOM）。 ----

    @Test
    public void oversized_body_short_circuits_before_outer_reads_body() {
        // 模拟：LogInterceptor 是 outer（外层，先被调用），它 chain.proceed() 后
        // 会 response.body().string() 读全 body。BodyLimit 是 inner（内层，后被调用），
        // 它先拿到 network response，检查 size 超限 → 抛 IOException →
        // IOException 沿 chain 传给 outer 的 chain.proceed() 调用点 → 外层永远走不到
        // body().string()，OOM 被避免。
        byte[] oversized = new byte[(int) EmojiManifestBodyLimitInterceptor.MAX_MANIFEST_BODY_BYTES + 10];
        Request request = req("https://example.com/v1/common/emojis");
        Response networkResp = ok(request, bodyWith(oversized, oversized.length));
        Interceptor bodyLimit = new EmojiManifestBodyLimitInterceptor();

        boolean outerReachedBodyRead = false;
        try {
            // 模拟 outer(LogInterceptor 风格) 的 chain.proceed() —— 内部就是 bodyLimit.intercept
            Response fromInner = bodyLimit.intercept(new FakeChain(request, networkResp));
            // 若 bodyLimit 没抛，outer 会来这一步做 body().string()——OOM 高危
            fromInner.body().string();
            outerReachedBodyRead = true;
        } catch (IOException e) {
            // 期望：bodyLimit 抛，outer 走不到 body 读——OOM 已被避免
        }
        assertEquals("outer 不该走到 body 读取（应被 bodyLimit 抛异常 short-circuit）",
                false, outerReachedBodyRead);
    }
}
