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

package com.chat.uikit.group.webhook.service;

import android.text.TextUtils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 群入站 Webhook 数据源 + 测试推送串行/冷却状态机。
 * 与 iOS WKIncomingWebhookManager 1:1 对齐：
 * <ul>
 *   <li>6 个接口与 octo-server `/v1/groups/{group_no}/incoming-webhooks*` 一一对应；</li>
 *   <li>测试推送会向群内发真实消息，连点会刷屏 —— 状态机保证：
 *     <ol>
 *       <li>全局同一时刻仅一条测试在飞（任一在飞，所有 webhook 的"测试"按钮都置灰）；</li>
 *       <li>单条 webhook 测试后进入 3s 冷却。</li>
 *     </ol>
 *   </li>
 * </ul>
 */
public class IncomingWebhookManager extends WKBaseModel {

    /** 单条 webhook 测试后冷却毫秒，避免连点刷屏。 */
    private static final long TEST_COOLDOWN_MS = 3000L;

    private boolean hasTestInFlight;
    /** webhookId -> 冷却失效时间戳（System.currentTimeMillis()）。 */
    private final HashMap<String, Long> cooldownExpire = new HashMap<>();

    private IncomingWebhookManager() {
    }

    private static class Holder {
        private static final IncomingWebhookManager INSTANCE = new IncomingWebhookManager();
    }

    public static IncomingWebhookManager getInstance() {
        return Holder.INSTANCE;
    }

    public interface IListListener {
        void onResult(int code, String msg, List<IncomingWebhook> list);
    }

    public interface IWebhookListener {
        void onResult(int code, String msg, IncomingWebhook webhook);
    }

    public interface ITestListener {
        /**
         * @param sent 是否真正发出了测试请求（false = 命中守卫被静默忽略）
         * @param code success/failure
         * @param msg  错误描述
         */
        void onResult(boolean sent, int code, String msg);
    }

    /** 列表。 */
    public void list(String groupNo, IListListener listener) {
        request(createService(IncomingWebhookService.class).list(groupNo), new IRequestResultListener<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                List<IncomingWebhook> out = new ArrayList<>();
                JSONArray arr = result == null ? null : result.getJSONArray("list");
                if (arr != null) {
                    for (int i = 0, size = arr.size(); i < size; i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        if (obj != null) out.add(IncomingWebhook.fromJson(obj));
                    }
                }
                if (listener != null) listener.onResult(HttpResponseCode.success, "", out);
            }

            @Override
            public void onFail(int code, String msg) {
                if (listener != null) listener.onResult(code, msg, new ArrayList<>());
            }
        });
    }

    /** 新建 — token / url / urls 仅此一次随响应返回。 */
    public void create(String groupNo, String name, String avatar, IWebhookListener listener) {
        JSONObject body = buildUpsertBody(name, avatar, null);
        request(createService(IncomingWebhookService.class).create(groupNo, body), new IRequestResultListener<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                if (result == null) {
                    if (listener != null) listener.onResult(HttpResponseCode.error, "响应格式异常", null);
                    return;
                }
                if (listener != null) {
                    listener.onResult(HttpResponseCode.success, "", IncomingWebhook.fromJson(result));
                }
            }

            @Override
            public void onFail(int code, String msg) {
                if (listener != null) listener.onResult(code, msg, null);
            }
        });
    }

    /**
     * 编辑（只发变化字段）。
     * <p>name / avatar / status 任一非 null 都会被纳入请求体，全 null 时发空对象（与 iOS 一致 ）。
     *
     * <p>注：与 iOS 一致 — onSuccess 直接当成功。原因：webhook 端点服务端响应可能是
     * 空 body 或不含 `status` 字段，{@link CommonResponse#status} 反序列化后为 0，
     * 直接回 result.status 会被调用方误判为失败。BaseObserver 已经把非 2xx HTTP
     * 映射到 onError，到 onSuccess 就证明请求成功。
     */
    public void update(String groupNo, String webhookId, String name, String avatar, Integer status,
                       com.chat.base.net.ICommonListener listener) {
        JSONObject body = buildUpsertBody(name, avatar, status);
        request(createService(IncomingWebhookService.class).update(groupNo, webhookId, body),
                new IRequestResultListener<CommonResponse>() {
                    @Override
                    public void onSuccess(CommonResponse result) {
                        if (listener != null) listener.onResult(HttpResponseCode.success, "");
                    }

                    @Override
                    public void onFail(int code, String msg) {
                        if (listener != null) listener.onResult(code, msg);
                    }
                });
    }

    /** 删除（软删）。 */
    public void delete(String groupNo, String webhookId, com.chat.base.net.ICommonListener listener) {
        request(createService(IncomingWebhookService.class).delete(groupNo, webhookId),
                new IRequestResultListener<CommonResponse>() {
                    @Override
                    public void onSuccess(CommonResponse result) {
                        if (listener != null) listener.onResult(HttpResponseCode.success, "");
                    }

                    @Override
                    public void onFail(int code, String msg) {
                        if (listener != null) listener.onResult(code, msg);
                    }
                });
    }

    /** 重置 token（同 create 一次性返回）。 */
    public void regenerate(String groupNo, String webhookId, IWebhookListener listener) {
        request(createService(IncomingWebhookService.class).regenerate(groupNo, webhookId),
                new IRequestResultListener<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject result) {
                        if (result == null) {
                            if (listener != null) listener.onResult(HttpResponseCode.error, "响应格式异常", null);
                            return;
                        }
                        if (listener != null) {
                            listener.onResult(HttpResponseCode.success, "", IncomingWebhook.fromJson(result));
                        }
                    }

                    @Override
                    public void onFail(int code, String msg) {
                        if (listener != null) listener.onResult(code, msg, null);
                    }
                });
    }

    /** 任一 webhook 的测试请求是否在飞。 */
    public boolean hasTestInFlight() {
        return hasTestInFlight;
    }

    /** 该 webhook 是否正处于 3s 冷却。 */
    public boolean isWebhookOnTestCooldown(String webhookId) {
        if (TextUtils.isEmpty(webhookId)) return false;
        Long expire = cooldownExpire.get(webhookId);
        if (expire == null) return false;
        long now = System.currentTimeMillis();
        if (now >= expire) {
            cooldownExpire.remove(webhookId);
            return false;
        }
        return true;
    }

    /**
     * 测试发送 — 走串行 + 冷却守卫；命中守卫直接 onResult(false, ...) 不打接口。
     * <p>失败也进入冷却 —— 失败常因 401/429/网络抖动，不冷却会被瞬间再点产生雪崩。
     */
    public void test(String groupNo, String webhookId, ITestListener listener) {
        if (hasTestInFlight || isWebhookOnTestCooldown(webhookId)) {
            if (listener != null) listener.onResult(false, HttpResponseCode.success, "");
            return;
        }
        hasTestInFlight = true;
        // 兜底 subscribe 之前的<em>同步异常</em>: 常规路径 onSuccess / onFail 都会复位
        // hasTestInFlight, 框架层 HTTP 错误 (401/429/超时/断网) 走 BaseObserver.onError → onFail.
        // 但若 createService(...) / .test(...) 表达式本身同步抛 (Retrofit 配置异常 / NPE 等,
        // 概率低但非零), callback 永远没机会注册, 标志会永久卡 true 致此账号本 session
        // 内所有 webhook 测试都被前置闸门拒掉。在此 catch 复位 + 立刻上报 listener, 避免静默。
        try {
            request(createService(IncomingWebhookService.class).test(groupNo, webhookId),
                    new IRequestResultListener<CommonResponse>() {
                        @Override
                        public void onSuccess(CommonResponse result) {
                            hasTestInFlight = false;
                            markCooldown(webhookId);
                            // 与 update/delete 同理：onSuccess 直接当成功，不读 body status
                            if (listener != null) listener.onResult(true, HttpResponseCode.success, "");
                        }

                        @Override
                        public void onFail(int code, String msg) {
                            hasTestInFlight = false;
                            markCooldown(webhookId);
                            if (listener != null) listener.onResult(true, code, msg);
                        }
                    });
        } catch (Throwable t) {
            hasTestInFlight = false;
            if (listener != null) listener.onResult(true, HttpResponseCode.error, t.getMessage());
        }
    }

    private void markCooldown(String webhookId) {
        if (TextUtils.isEmpty(webhookId)) return;
        cooldownExpire.put(webhookId, System.currentTimeMillis() + TEST_COOLDOWN_MS);
    }

    private JSONObject buildUpsertBody(String name, String avatar, Integer status) {
        JSONObject body = new JSONObject();
        if (name != null) body.put("name", name);
        if (avatar != null) body.put("avatar", avatar);
        if (status != null) body.put("status", status);
        return body;
    }
}
