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

import android.text.TextUtils;

import com.alibaba.fastjson.JSON;
import com.chat.base.config.WKSharedPreferencesUtil;

/**
 * emoji 清单本地持久化。写 SP、读 SP、序列化都做浅封装，让下次冷启动能"首屏立即可用"
 * ——manifest 网络请求在 {@code AppStartup.postPhaseC} 后 fire-and-forget，
 * 但用户可能在网络返回前就打开表情面板或读消息。
 *
 * <p>非 UID 隔离：清单是全局公共数据（服务端公开端点也无鉴权），账号切换不需要清空。
 *
 * <p>{@link #serialize(EmojiManifestResp)} / {@link #deserialize(String)} 是无 Android
 * 依赖的纯函数，JVM 单测直接覆盖。{@link #save(EmojiManifestResp)} / {@link #load()}
 * 是薄壳，包装 SP IO——SP 层线程安全由 {@link WKSharedPreferencesUtil} 承担。
 *
 * <p>Key 后缀 {@code _v1}：DTO shape 一旦改变（比如加了 category 字段），bump 到 {@code _v2}
 * 让老缓存自然失效，避免 FastJson 解出畸形对象。
 */
public final class EmojiManifestCache {

    static final String CACHE_KEY = "emoji_manifest_v1";

    private EmojiManifestCache() {}

    /**
     * 把 manifest 序列化为可持久化的 JSON 字符串。
     *
     * @return JSON 字符串；null/list null 时返回空串（呼叫方判断跳过写盘）
     */
    public static String serialize(EmojiManifestResp resp) {
        if (resp == null || resp.list == null) return "";
        return JSON.toJSONString(resp);
    }

    /**
     * 反序列化持久化字符串。空串 / null / 损坏 JSON 都返回 null，呼叫方走内置兜底。
     */
    public static EmojiManifestResp deserialize(String raw) {
        if (TextUtils.isEmpty(raw)) return null;
        try {
            return JSON.parseObject(raw, EmojiManifestResp.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 写入 SP。async apply——延迟落盘对本用途足够，且不阻塞主线程。 */
    public static void save(EmojiManifestResp resp) {
        String json = serialize(resp);
        if (TextUtils.isEmpty(json)) return;
        WKSharedPreferencesUtil.getInstance().putSP(CACHE_KEY, json);
    }

    /** 从 SP 读取。返回 null 表示无缓存或解析失败——呼叫方走内置兜底。 */
    public static EmojiManifestResp load() {
        return deserialize(WKSharedPreferencesUtil.getInstance().getSP(CACHE_KEY));
    }
}
