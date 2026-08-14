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

import com.chat.base.config.WKBinder;
import com.chat.base.utils.WKLogUtils;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ByteString;

/**
 * 2020-07-20 11:50
 * 网络请求日志监听
 */
public class LogInterceptor implements Interceptor {

    /**
     * 只打印小于这个字节数的 body，超过的只打一行大小提示。
     *
     * <p>为什么要有上界：会话同步这类响应有 350 万字符，原实现 {@code body().string()} 整包物化 →
     * {@code formatJson} 再建一遍 JSON DOM 并缩进美化 → StringBuilder 再拼一份 → 最后用整包
     * String 重建 body 回写，同一份数据五六份，debug 包凭空多吃 80MB+，堆水位测量完全失真。
     *
     * <p>而且 {@link WKLogUtils} 按 3900 字符分片输出，350 万字符 = 900+ 条独立 logcat 记录：
     * 人根本拼不回来，还会把 logcat 环形缓冲区冲爆，ANR 堆栈 / 堆探针那些真正要看的日志全被挤掉。
     * 也就是说大 body 这样打等于没打，代价却是实打实的——所以直接不打，只留大小和 URL。
     *
     * <p>小 body（99% 的接口）行为与改动前一致，该看的都还在。
     */
    private static final long MAX_LOG_BODY_BYTES = 256L * 1024L;

    /**
     * 可用堆低于这个数就整段跳过 body 打印。
     *
     * <p>日志工具绝不能是压死进程的最后一根稻草。2026-08-14 压舱实测：堆只剩 9MB 时，
     * 下面那句 {@code peekBody(256KB+1)} 自己 OOM 了，崩溃栈停在
     * {@code LogInterceptor.intercept → PeekSource.read → InflaterSource → okio.Segment.<init>}
     * ——被测代码没问题，是量具把进程压垮了。
     *
     * <p>注意 peek 的开销不止 256KB：gzip 响应要边解压边填 okio Segment，
     * 真实占用是解压后的字节数 + 段链开销。
     */
    private static final long LOW_HEAP_SKIP_BYTES = 32L * 1024L * 1024L;

    /** 当前可用堆（max - used）。 */
    private static long freeHeapBytes() {
        Runtime rt = Runtime.getRuntime();
        return rt.maxMemory() - (rt.totalMemory() - rt.freeMemory());
    }

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {

        if (WKBinder.isDebug) {
            Request request = chain.request();
            //获取request内容
            // 已格式化、可直接打印的请求体；body 过大时是一行提示
            String requestParams = "";
            RequestBody requestBody = request.body();

            if (requestBody != null && !requestBody.isOneShot()) {
                long reqLen = requestBody.contentLength();
                if (reqLen < 0 || reqLen > MAX_LOG_BODY_BYTES) {
                    // 文件上传这类：writeTo 会把整包灌进内存，不能为了打一条日志付这个代价。
                    // 长度未知（-1，流式 body）同样跳过：写完才知道多大，等于没有上界。
                    requestParams = reqLen < 0
                            ? "body 长度未知，已跳过打印"
                            : "body 过大，已跳过打印（" + reqLen + " bytes）";
                } else if (freeHeapBytes() < LOW_HEAP_SKIP_BYTES) {
                    requestParams = "堆吃紧，跳过 body 打印";
                } else {
                    MediaType type = requestBody.contentType();
                    Buffer source = new Buffer();
                    requestBody.writeTo(source);
                    try {
                        Charset charset = type == null ? Charset.defaultCharset() : type.charset(Charset.defaultCharset());
                        readBomAsCharset(source, charset);
                        requestParams = formatJson(source.readString(charset));
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        closeQuietly(source);
                    }
                }
            }
            // 请求日志
            StringBuilder reqSb = new StringBuilder();
            reqSb.append(String.format(Locale.getDefault(), "\n请求地址: %s", request.url())).append("\n")
                    .append("\n**************Request***************\n")
                    .append("==============Method==============\n")
                    .append(request.method()).append("\n")
                    .append("==============Headers=============\n")
                    .append(request.headers());
            if (!TextUtils.isEmpty(requestParams)) {
                reqSb.append("==============Body==============\n")
                        .append(requestParams).append("\n");
            }
            reqSb.append("**************Request***************").append("\n");
            WKLogUtils.d("wkHttpLog", reqSb.toString());

            // 响应日志
            long t1 = System.nanoTime();
            Response response = chain.proceed(request);
            long t2 = System.nanoTime();
            StringBuilder sb = new StringBuilder();

            if (!TextUtils.isEmpty(requestParams)) {
                sb.append(" \n").append("============Request Body============\n")
                        .append(requestParams).append("\n");
            }
            sb.append("*************Response**************\n")
                    .append(response.request().url()).append("\n")
                    .append(String.format(Locale.getDefault(), "本次请求响应时间: %.1f ms", (t2 - t1) / 1e6d)).append("\n")
                    .append("=============Headers=============\n")
                    .append(response.headers()).append("\n");
            sb.append("==============Body==============\n");
            if (!response.isSuccessful()) {
                sb.append("http请求失败，").append(response.networkResponse()).append("\n");
            } else if (freeHeapBytes() < LOW_HEAP_SKIP_BYTES) {
                // 堆已经吃紧，peek 本身就可能 OOM（实测过）。这条日志的价值远不如进程活着。
                sb.append("堆吃紧（剩 ").append(freeHeapBytes() / (1024 * 1024))
                        .append("MB），跳过 body 打印\n");
            } else {
                // peekBody 只缓冲最多 MAX+1 字节，且不消费原 body：小 body 拿到的就是全文，
                // 大 body 拿到 MAX+1 字节即可判定超限。原实现的 body().string() +
                // ResponseBody.create(content) 回写整段删掉了——那一步会把流式解析
                // (FastJsonResponseBodyConverter) 在 debug 包里彻底抵消，因为转换器拿到的
                // body 已经是内存里的整包 String，白流式了。
                ResponseBody peeked = response.peekBody(MAX_LOG_BODY_BYTES + 1);
                if (peeked.contentLength() > MAX_LOG_BODY_BYTES) {
                    sb.append("body 过大，已跳过打印（>")
                            .append(MAX_LOG_BODY_BYTES / 1024).append("KB）\n");
                } else {
                    sb.append(formatJson(peeked.string())).append("\n");
                }
            }
            sb.append("*************Response**************");
            WKLogUtils.d("wkHttpLog", "   " + sb);
            // 原 body 未被消费，原样返回，不再重建
            return response;
        } else {
            return chain.proceed(chain.request());
        }
    }

    private String formatJson(String json) {
        if (json != null && json.startsWith("{") && json.endsWith("}")) {
            try {
                JSONObject object = new JSONObject(json);
                return object.toString(2);
            } catch (Exception e) {
                return "json格式化错误," + json + ", errorMsg:" + e.getMessage();
            }
        } else if (json != null && json.startsWith("[") && json.endsWith("]")) {
            JSONArray jsonArray;
            try {
                jsonArray = new JSONArray(json);
                return jsonArray.toString(2);
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }
        return "";
    }

    /**
     * 格式化post form
     *
     * @param postParam
     * @return
     */
    private String formatPostFormData(String postParam) {
        StringBuilder builder = new StringBuilder();
        String[] split = postParam.split("&");
        for (String aSplit : split) {
            builder.append(aSplit).append("\n");
        }
        return builder.toString();
    }

    /**
     * 安静地关闭资源，忽略异常
     */
    private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // 忽略关闭时的异常
            }
        }
    }

    private Charset readBomAsCharset(BufferedSource source, Charset defaultCharset) {
        try {
            // UTF-8 BOM: EF BB BF
            if (source.rangeEquals(0, ByteString.decodeHex("EFBBBF"))) {
                source.skip(3);
                return StandardCharsets.UTF_8;
            }
            // UTF-16 BE BOM: FE FF
            if (source.rangeEquals(0, ByteString.decodeHex("FEFF"))) {
                source.skip(2);
                return StandardCharsets.UTF_16BE;
            }
            // UTF-16 LE BOM: FF FE
            if (source.rangeEquals(0, ByteString.decodeHex("FFFE"))) {
                source.skip(2);
                return StandardCharsets.UTF_16LE;
            }
        } catch (Exception e) {
            // 忽略异常，使用默认字符集
        }
        return defaultCharset != null ? defaultCharset : StandardCharsets.UTF_8;
    }
}
