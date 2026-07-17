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
 * 覆盖 {@link SearchGlobalBodyLimitInterceptor}：路径匹配 + Content-Length 快路径 +
 * chunked 慢路径 + 严格路径匹配（不误伤子路径）。
 */
public class SearchGlobalBodyLimitInterceptorTest {

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

    private static ResponseBody bodyWith(final byte[] bytes, long declaredContentLength) {
        if (declaredContentLength < 0) {
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

    @Test
    public void non_target_url_passes_through_untouched() throws IOException {
        // 频道内搜索 /messages/_search 不该被拦
        byte[] payload = new byte[(int) SearchGlobalBodyLimitInterceptor.MAX_SEARCH_BODY_BYTES + 100];
        Request request = req("https://example.com/v1/messages/_search");
        Response response = ok(request, bodyWith(payload, payload.length));

        Response out = new SearchGlobalBodyLimitInterceptor().intercept(new FakeChain(request, response));

        assertNotNull(out.body());
        assertEquals(payload.length, out.body().contentLength());
    }

    @Test
    public void l1_oversized_content_length_throws() {
        byte[] payload = new byte[(int) SearchGlobalBodyLimitInterceptor.MAX_SEARCH_BODY_BYTES + 10];
        Request request = req("https://example.com/v1/messages/_search_global_groups");
        Response response = ok(request, bodyWith(payload, payload.length));

        try {
            new SearchGlobalBodyLimitInterceptor().intercept(new FakeChain(request, response));
            fail("expected IOException on oversized Content-Length");
        } catch (IOException e) {
            assertEquals(true, e.getMessage().contains("Content-Length"));
        }
    }

    @Test
    public void l2_oversized_chunked_body_throws() {
        byte[] payload = new byte[(int) SearchGlobalBodyLimitInterceptor.MAX_SEARCH_BODY_BYTES + 10];
        Request request = req("https://example.com/v1/messages/_search_global_messages");
        Response response = ok(request, bodyWith(payload, -1));

        try {
            new SearchGlobalBodyLimitInterceptor().intercept(new FakeChain(request, response));
            fail("expected IOException on oversized chunked body");
        } catch (IOException e) {
            assertEquals(true, e.getMessage().contains("exceeds"));
        }
    }

    @Test
    public void l1_within_limit_passes_through() throws IOException {
        String json = "{\"data\":{\"sequence\":1,\"groups\":[]},\"pagination\":{\"has_more\":false,\"next_cursor\":\"\"}}";
        byte[] payload = json.getBytes();
        Request request = req("https://example.com/v1/messages/_search_global_groups");
        Response response = ok(request, bodyWith(payload, payload.length));

        Response out = new SearchGlobalBodyLimitInterceptor().intercept(new FakeChain(request, response));

        assertNotNull(out.body());
        assertArrayEquals(payload, out.body().bytes());
    }

    @Test
    public void similar_prefix_url_not_matched() throws IOException {
        // 未接入的 _search_global_files 不该被这个拦截器覆盖（本 interceptor 明确不管它）
        byte[] payload = new byte[(int) SearchGlobalBodyLimitInterceptor.MAX_SEARCH_BODY_BYTES + 100];
        Request request = req("https://example.com/v1/messages/_search_global_files");
        Response response = ok(request, bodyWith(payload, payload.length));

        Response out = new SearchGlobalBodyLimitInterceptor().intercept(new FakeChain(request, response));

        // 不该被拦
        assertEquals(payload.length, out.body().contentLength());
    }

    @Test
    public void non_messages_path_not_matched() throws IOException {
        // /other/_search_global_groups 不该被拦（严格要求前一段是 messages）
        byte[] payload = new byte[(int) SearchGlobalBodyLimitInterceptor.MAX_SEARCH_BODY_BYTES + 100];
        Request request = req("https://example.com/v1/other/_search_global_groups");
        Response response = ok(request, bodyWith(payload, payload.length));

        Response out = new SearchGlobalBodyLimitInterceptor().intercept(new FakeChain(request, response));

        assertEquals(payload.length, out.body().contentLength());
    }
}
