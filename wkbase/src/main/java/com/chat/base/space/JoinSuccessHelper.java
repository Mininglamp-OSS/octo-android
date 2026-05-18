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
 * 跨 Space 加群提示 — 对齐  #1068（ / ）。
 *
 * <p>场景：用户当前在 Space A，通过邀请链接 / 扫码 / 直接接受邀请加入属于 Space B 的群。
 * 后端加群成功，但前端默认仍停留在 Space A → 新群在当前 Space 会话列表里被 {@link SpaceFilter}
 * 正确过滤掉，用户会以为「加入失败」。
 *
 * <p>解决方案（不自动切 Space，让用户显式点击）：
 * <ol>
 *     <li>加群入口（ScanJoinGroupActivity / InviteLanding / 接受邀请回调）成功后
 *         调用 {@link #computeAndSave(String, String, String, String)}</li>
 *     <li>主页面（如 {@code ChatFragment.onResume}）调 {@link #consumeNotice()} 获取并清空</li>
 *     <li>根据 {@link JoinNotice#crossSpace} 决定显示常规 Toast 还是带「切换过去」按钮的 Dialog</li>
 * </ol>
 *
 * <p>数据通过 {@link WKSharedPreferencesUtil} 以 UID 为 scope 持久化，保证进程被回收后仍可恢复
 * （对齐 web 的 sessionStorage 桥接）。
 */
public final class JoinSuccessHelper {

    /** SharedPreferences key（经 UID scope）。*/
    private static final String SP_KEY = "join_success_notice";

    private JoinSuccessHelper() {
    }

    /** 加群成功的通知数据（纯 POJO，可序列化成 JSON）。*/
    public static final class JoinNotice {
        @NonNull public final String groupNo;
        @NonNull public final String groupName;
        @NonNull public final String targetSpaceId;
        @NonNull public final String targetSpaceName;
        @NonNull public final String viewerSpaceId;
        public final boolean crossSpace;

        public JoinNotice(@Nullable String groupNo,
                          @Nullable String groupName,
                          @Nullable String targetSpaceId,
                          @Nullable String targetSpaceName,
                          @Nullable String viewerSpaceId,
                          boolean crossSpace) {
            this.groupNo = orEmpty(groupNo);
            this.groupName = orEmpty(groupName);
            this.targetSpaceId = orEmpty(targetSpaceId);
            this.targetSpaceName = orEmpty(targetSpaceName);
            this.viewerSpaceId = orEmpty(viewerSpaceId);
            this.crossSpace = crossSpace;
        }

        @NonNull
        JSONObject toJson() {
            JSONObject j = new JSONObject();
            try {
                j.put("group_no", groupNo);
                j.put("group_name", groupName);
                j.put("target_space_id", targetSpaceId);
                j.put("target_space_name", targetSpaceName);
                j.put("viewer_space_id", viewerSpaceId);
                j.put("cross_space", crossSpace);
            } catch (JSONException ignored) {
            }
            return j;
        }

        @Nullable
        static JoinNotice fromJson(@Nullable String s) {
            if (s == null || s.isEmpty()) return null;
            try {
                JSONObject j = new JSONObject(s);
                return new JoinNotice(
                        j.optString("group_no", ""),
                        j.optString("group_name", ""),
                        j.optString("target_space_id", ""),
                        j.optString("target_space_name", ""),
                        j.optString("viewer_space_id", ""),
                        j.optBoolean("cross_space", false)
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
    // 可注入的存储接口 —— 便于 host-side 单元测试（不依赖 Android SharedPreferences）
    // ------------------------------------------------------------------

    public interface NoticeStore {
        @Nullable String read();
        void write(@Nullable String json);
    }

    private static final NoticeStore DEFAULT_STORE = new NoticeStore() {
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
    // 对外 API
    // ------------------------------------------------------------------

    /**
     * 计算 crossSpace 并把通知持久化。{@code targetSpaceId} 空时不保存跨空间通知
     * （只保存常规 notice，以便 UI 可以统一展示「已加入」toast）。
     *
     * @param groupNo         群 ID
     * @param groupName       群名（用于 toast 显示）
     * @param targetSpaceId   被加入群归属的 Space ID（后端返回）
     * @param targetSpaceName 被加入群归属的 Space 名称（用于 toast 显示）
     */
    public static void computeAndSave(@Nullable String groupNo,
                                      @Nullable String groupName,
                                      @Nullable String targetSpaceId,
                                      @Nullable String targetSpaceName) {
        computeAndSave(groupNo, groupName, targetSpaceId, targetSpaceName,
                SpaceFilter.getCurrentSpaceId(), DEFAULT_STORE);
    }

    @VisibleForTesting
    public static void computeAndSave(@Nullable String groupNo,
                                      @Nullable String groupName,
                                      @Nullable String targetSpaceId,
                                      @Nullable String targetSpaceName,
                                      @Nullable String viewerSpaceId,
                                      @NonNull NoticeStore store) {
        // crossSpace 判定（对齐 web InviteLanding 快照比对）：
        // targetSpaceId / viewerSpaceId 任一为空 → 不构成跨空间（非 Space 模式 or 缺字段 fail-open）
        boolean crossSpace = !isBlank(targetSpaceId)
                && !isBlank(viewerSpaceId)
                && !targetSpaceId.equals(viewerSpaceId);

        JoinNotice notice = new JoinNotice(groupNo, groupName, targetSpaceId, targetSpaceName,
                viewerSpaceId, crossSpace);
        store.write(notice.toJson().toString());
    }

    /** 读取并清空通知（一次性消费，对齐 web sessionStorage consume）。*/
    @Nullable
    public static JoinNotice consumeNotice() {
        return consumeNotice(DEFAULT_STORE);
    }

    @VisibleForTesting
    @Nullable
    public static JoinNotice consumeNotice(@NonNull NoticeStore store) {
        String raw = store.read();
        if (isBlank(raw)) return null;
        JoinNotice notice = JoinNotice.fromJson(raw);
        // 不管解析成功与否都清空，避免脏数据反复触发
        store.write("");
        return notice;
    }

    /** 仅读取不清空（测试 / 预览用）。*/
    @Nullable
    public static JoinNotice peekNotice() {
        String raw = DEFAULT_STORE.read();
        if (isBlank(raw)) return null;
        return JoinNotice.fromJson(raw);
    }

    /** 强制清空（登出 / 切换账号时调用）。*/
    public static void clearNotice() {
        DEFAULT_STORE.write("");
    }

    /** 本地空串判断，避免 host-side 单元测试依赖 {@link android.text.TextUtils}。*/
    private static boolean isBlank(@Nullable String s) {
        return s == null || s.isEmpty();
    }
}
