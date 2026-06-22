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

import com.alibaba.fastjson.JSONObject;

/**
 * 群入站 Webhook 数据模型，对齐 iOS WKIncomingWebhook。
 * <p>字段命名沿用服务端 snake_case，便于和 octo-server / octo-web 模型对照。
 * <p>token / url / urls 仅在 create 与 regenerate 响应里返回；list / update 拿不到。
 */
public class IncomingWebhook {

    /** `iwh_` 前缀的 fromUID 永远不是真人成员，渲染层据此识别 webhook 推送的消息。 */
    public static final String UID_PREFIX = "iwh_";

    /** 状态：0 = 禁用，1 = 启用，2 = 已删除（软删除，list 不会返回）。 */
    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_DELETED = 2;

    public String webhookId;
    public String groupNo;
    public String name;
    /** 成员 / bot 创建恒为空字符串；仅群主/管理员可设。 */
    public String avatar;
    public String creatorUid;
    public int status;
    /** Unix 秒；从未推送过为 0。 */
    public long lastUsedAt;
    /** 累计 native 推送次数（test 推送不计）。 */
    public long callCount;
    public long createdAt;

    /** 仅 create / regenerate 响应返回，list / update 拿不到。 */
    public String token;
    /** 相对路径或绝对 URL；调用方走 absoluteURL 拼成可复制的绝对地址。 */
    public String url;
    public String urlNative;
    public String urlGithub;
    public String urlWecom;

    public static IncomingWebhook fromJson(JSONObject obj) {
        IncomingWebhook h = new IncomingWebhook();
        if (obj == null) return h;
        h.webhookId = obj.getString("webhook_id");
        h.groupNo = obj.getString("group_no");
        h.name = obj.getString("name");
        h.avatar = obj.getString("avatar");
        h.creatorUid = obj.getString("creator_uid");
        Integer st = obj.getInteger("status");
        h.status = st == null ? 0 : st;
        Long lastUsed = obj.getLong("last_used_at");
        h.lastUsedAt = lastUsed == null ? 0 : lastUsed;
        Long callCount = obj.getLong("call_count");
        h.callCount = callCount == null ? 0 : callCount;
        Long created = obj.getLong("created_at");
        h.createdAt = created == null ? 0 : created;

        String token = obj.getString("token");
        if (!TextUtils.isEmpty(token)) h.token = token;
        String url = obj.getString("url");
        if (!TextUtils.isEmpty(url)) h.url = url;

        JSONObject urls = obj.getJSONObject("urls");
        if (urls != null) {
            String u = urls.getString("native");
            if (!TextUtils.isEmpty(u)) h.urlNative = u;
            u = urls.getString("github");
            if (!TextUtils.isEmpty(u)) h.urlGithub = u;
            u = urls.getString("wecom");
            if (!TextUtils.isEmpty(u)) h.urlWecom = u;
        }
        // null-safety: 下游 cell 直接读取 name / avatar 等字段，统一兜成空串避免 NPE。
        if (h.webhookId == null) h.webhookId = "";
        if (h.groupNo == null) h.groupNo = "";
        if (h.name == null) h.name = "";
        if (h.avatar == null) h.avatar = "";
        if (h.creatorUid == null) h.creatorUid = "";
        return h;
    }

    /** 当前登录者是否能管理此 webhook：群主/管理员管全部，普通成员仅能管自己创建的。 */
    public boolean canManageByCurrentUser(boolean isManagerOrCreator, String currentUid) {
        if (isManagerOrCreator) return true;
        return !TextUtils.isEmpty(currentUid)
                && !TextUtils.isEmpty(creatorUid)
                && currentUid.equals(creatorUid);
    }

    /** 仅 enabled 才允许测试发送。 */
    public boolean canTest() {
        return status == STATUS_ENABLED;
    }

    /**
     * 服务端返回的相对路径（含 `/v1` 前缀）拼成绝对 URL。
     *
     * <p>apiBaseUrl 形如 `https://im.example.com/api/v1/`，与服务端相对路径里的
     * `/v1` 段会重复，这里先剥掉再拼，避免出现 `/api/v1/v1/...`。
     */
    public static String absoluteURL(String relativeUrl, String apiBaseUrl) {
        if (TextUtils.isEmpty(relativeUrl)) return "";
        String lower = relativeUrl.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return relativeUrl;
        }
        if (TextUtils.isEmpty(apiBaseUrl)) return "";

        // 解析 base URL：scheme + host + port + path（剥掉末尾 /v1）
        java.net.URI baseUri;
        try {
            baseUri = java.net.URI.create(apiBaseUrl);
        } catch (Throwable ignored) {
            return "";
        }
        String scheme = baseUri.getScheme();
        if (TextUtils.isEmpty(scheme)) scheme = "https";
        String host = baseUri.getHost();
        if (host == null) host = "";
        int port = baseUri.getPort();
        String portSeg = (port > 0) ? (":" + port) : "";
        String path = baseUri.getPath();
        if (path == null) path = "";
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (path.endsWith("/v1")) path = path.substring(0, path.length() - 3);
        String rel = relativeUrl.startsWith("/") ? relativeUrl : "/" + relativeUrl;
        return scheme + "://" + host + portSeg + path + rel;
    }
}
