package com.chat.base.net;

import android.text.TextUtils;

import com.chat.base.config.WKBinder;
import com.chat.base.utils.WKLogUtils;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Locale;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.internal.Util;
import okio.Buffer;

/**
 * 2020-07-20 11:50
 * 网络请求日志监听
 */
public class LogInterceptor implements Interceptor {

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {

        if (WKBinder.isDebug) {
            Request request = chain.request();
            //获取request内容
            RequestBody requestBody = request.body();
            String requestParams = "";

            if (requestBody != null && !requestBody.isOneShot()) {
                boolean oneShort = requestBody.isOneShot();
                WKLogUtils.e("判断执行次数"+oneShort);
                MediaType type = requestBody.contentType();
                Buffer source = new Buffer();
                requestBody.writeTo(source);
                try {
                    Charset charset = type == null ? Charset.defaultCharset() : type.charset(Charset.defaultCharset());
                    Util.readBomAsCharset(source, charset);
//                    Util.readBomAsCharset()
                    requestParams = source.readString(charset);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    Util.closeQuietly(source);
                }
                // request = request.newBuilder().post(requestBody).build();
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
                        .append(formatJson(requestParams)).append("\n");
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
                        .append(formatJson(requestParams)).append("\n");
            }
            sb.append("*************Response**************\n")
                    .append(response.request().url()).append("\n")
                    .append(String.format(Locale.getDefault(), "本次请求响应时间: %.1f ms", (t2 - t1) / 1e6d)).append("\n")
                    .append("=============Headers=============\n")
                    .append(response.headers()).append("\n");
            String content = response.body().string();
            sb.append("==============Body==============\n");
            if (response.isSuccessful()) {
                sb.append(formatJson(content)).append("\n");
            } else {
                sb.append("http请求失败，").append(response.networkResponse()).append("\n");
            }
            sb.append("*************Response**************");
            WKLogUtils.d("wkHttpLog", "   " + sb);
            MediaType mediaType = response.body().contentType();
            return response.newBuilder()
                    .body(okhttp3.ResponseBody.create(content, mediaType))
                    .build();
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
}
