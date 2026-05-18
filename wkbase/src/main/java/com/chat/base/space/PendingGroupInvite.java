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

package com.chat.base.space;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.chat.base.config.WKSharedPreferencesUtil;

import org.json.JSONException;
import org.json.JSONObject;

/**
 *  Phase 2 · Android 端 {@code need_space} 暂存区（）。
 *
 * <p>当后端 scanjoin / invite-authorize / invite-detail / handleJoinGroup 入口
 * 检测到当前用户 {@code GetUserDefaultSpaceID(uid) == ""} 时，响应
 * {@code {"status":"need_space","msg":"..."}}。Android 端不应继续走
 * {@link JoinSuccessHelper} 分支，而是：
 * <ol>
 *   <li>把当前扫码/邀请上下文（groupNo / authCode / intent extras）以 JSON
 *       序列化到 SharedPreferences 的 {@code pending_group_invite} key；</li>
 *   <li>跳转 {@code SpaceGuideActivity} 让用户先加入一个 Space；</li>
 *   <li>加 Space 成功后读取本 helper，有则重启
 *       {@code ScanJoinGroupActivity} 重新发起入群请求。</li>
 * </ol>
 *
 * <p>数据以 UID 为 scope 持久化，防止账号切换后串号；消费是一次性的（
 * {@link #consume()} 读完即清），防止重复触发。
 *
 * <p>参考：{@link JoinSuccessHelper} — 同样的 NoticeStore 注入模式用于
 * host-side 单测；与 web 端 sessionStorage / iOS 端 NSUserDefaults 桥接一致。
 */
public final class PendingGroupInvite {

    /** SharedPreferences key（经 UID scope）。*/
    public static final String SP_KEY = "pending_group_invite";

    /** 后端 need_space 的 {@code status} 枚举值。*/
    public static final String STATUS_NEED_SPACE = "need_space";

    private PendingGroupInvite() {
    }

    /** 被暂存的入群上下文（纯 POJO，可序列化成 JSON）。*/
    public static final class Pending {
        @NonNull public final String groupNo;
        @NonNull public final String authCode;
        @NonNull public final String groupName;
        @NonNull public final String avatar;
        public final int memberCount;
        public final boolean isMember;
        @NonNull public final String spaceId;
        @NonNull public final String spaceName;

        public Pending(@Nullable String groupNo,
                       @Nullable String authCode,
                       @Nullable String groupName,
                       @Nullable String avatar,
                       int memberCount,
                       boolean isMember,
                       @Nullable String spaceId,
                       @Nullable String spaceName) {
            this.groupNo = orEmpty(groupNo);
            this.authCode = orEmpty(authCode);
            this.groupName = orEmpty(groupName);
            this.avatar = orEmpty(avatar);
            this.memberCount = memberCount;
            this.isMember = isMember;
            this.spaceId = orEmpty(spaceId);
            this.spaceName = orEmpty(spaceName);
        }

        @NonNull
        JSONObject toJson() {
            JSONObject j = new JSONObject();
            try {
                j.put("group_no", groupNo);
                j.put("auth_code", authCode);
                j.put("group_name", groupName);
                j.put("avatar", avatar);
                j.put("member_count", memberCount);
                j.put("is_member", isMember);
                j.put("space_id", spaceId);
                j.put("space_name", spaceName);
            } catch (JSONException ignored) {
            }
            return j;
        }

        @Nullable
        static Pending fromJson(@Nullable String s) {
            if (s == null || s.isEmpty()) return null;
            try {
                JSONObject j = new JSONObject(s);
                String groupNo = j.optString("group_no", "");
                // groupNo 是重试必需的主键；缺失视为无效 payload
                if (groupNo.isEmpty()) return null;
                return new Pending(
                        groupNo,
                        j.optString("auth_code", ""),
                        j.optString("group_name", ""),
                        j.optString("avatar", ""),
                        j.optInt("member_count", 0),
                        j.optBoolean("is_member", false),
                        j.optString("space_id", ""),
                        j.optString("space_name", "")
                );
            } catch (JSONException e) {
                return null;
            }
        }

        private static String orEmpty(@Nullable String s) {
            return s == null ? "" : s;
        }
    }

    // ------------------------------------------------------------------
    // 可注入的存储接口 — 与 JoinSuccessHelper 的 NoticeStore 模式一致，
    // 便于 host-side 单测（不依赖 Android SharedPreferences）。
    // ------------------------------------------------------------------

    public interface Store {
        @Nullable String read();
        void write(@Nullable String json);
    }

    private static final Store DEFAULT_STORE = new Store() {
        @Override
        public String read() {
            try {
                return WKSharedPreferencesUtil.getInstance().getSPWithUID(SP_KEY);
            } catch (Throwable ignored) {
                return null;
            }
        }

        @Override
        public void write(@Nullable String json) {
            try {
                WKSharedPreferencesUtil.getInstance().putSPWithUID(SP_KEY, json == null ? "" : json);
            } catch (Throwable ignored) {
            }
        }
    };

    // ------------------------------------------------------------------
    // need_space 响应识别
    // ------------------------------------------------------------------

    /**
     * 判定一段 JSON 响应是否匹配后端 need_space 契约：
     * {@code {"status":"need_space", "msg":"..."}}。
     *
     * <p>解析失败 / JSON 非对象 / status 字段缺失 → 返回 false（fail-open，
     * 走既有分支，不误拦截正常响应）。
     */
    public static boolean isNeedSpaceResponse(@Nullable String body) {
        if (body == null || body.isEmpty()) return false;
        try {
            JSONObject j = new JSONObject(body);
            return STATUS_NEED_SPACE.equals(j.optString("status", ""));
        } catch (JSONException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 对外 API
    // ------------------------------------------------------------------

    /** 暂存入群上下文。 */
    public static void save(@NonNull Pending pending) {
        save(pending, DEFAULT_STORE);
    }

    @VisibleForTesting
    public static void save(@NonNull Pending pending, @NonNull Store store) {
        store.write(pending.toJson().toString());
    }

    /** 仅读取不清空（加 Space 流程中预检时用）。*/
    @Nullable
    public static Pending peek() {
        return peek(DEFAULT_STORE);
    }

    @VisibleForTesting
    @Nullable
    public static Pending peek(@NonNull Store store) {
        return Pending.fromJson(store.read());
    }

    /**
     * 读取并清空（一次性消费）。加 Space 成功回跳时调用：拿到
     * Pending 就重启 ScanJoinGroupActivity，拿不到就正常进 Tab 页。
     */
    @Nullable
    public static Pending consume() {
        return consume(DEFAULT_STORE);
    }

    @VisibleForTesting
    @Nullable
    public static Pending consume(@NonNull Store store) {
        String raw = store.read();
        if (raw == null || raw.isEmpty()) return null;
        Pending pending = Pending.fromJson(raw);
        // 不论解析成功与否都清空，避免脏数据反复触发
        store.write("");
        return pending;
    }

    /** 强制清空（登出 / 切号 / 明确放弃入群时调用）。*/
    public static void clear() {
        DEFAULT_STORE.write("");
    }
}
