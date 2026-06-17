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

package com.chat.uikit.thread.msgmodel;

import android.os.Parcel;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chat.base.msgitem.WKContentType;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.R;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WKThreadCreatedContent extends WKMessageContent {

    // 全局缓存：threadChannelId → 最新消息条数（对齐 iOS messageCountCache）
    private static final ConcurrentHashMap<String, Integer> messageCountCache = new ConcurrentHashMap<>();

    /**
     * sourceMessageId → 已创建的 WKThreadCreatedContent 全局映射 (对齐 iOS sourceMessageThreadMap)。
     *
     * <p>填充时机: 每条 threadCreated 系统消息 decodeMsg 时, 若 source_message_id > 0 就 put 一次。
     * 进程级别永不清空 —— 与 iOS 的 dispatch_once 单例语义一致, 重启 app 后会通过重新 decode
     * 群里历史 threadCreated 消息再次填充。
     *
     * <p>用途: 长按一条普通消息时, 用 messageId 在这里查; 命中即说明该消息已被作为子区源,
     * 菜单把"创建子区"改成"进入子区「XX」", 点击直接跳转到对应子区频道。
     */
    private static final ConcurrentHashMap<String, WKThreadCreatedContent> sourceMessageThreadMap =
            new ConcurrentHashMap<>();

    /**
     * sourceMessageId 集合 (对齐 iOS sourceMessageIdSet)。和 sourceMessageThreadMap 同一处填充,
     * 二者一一对应; 主要供 set-only 命中场景做兜底判断 (map 极端情况下没拿到完整 thread 元信息时
     * 隐藏菜单入口避免重复创建)。
     */
    private static final Set<String> sourceMessageIdSet =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * 获取子区最新消息数量：优先从缓存取，fallback 到 content 的值
     */
    public static int getMessageCount(String threadChannelId, int fallback) {
        Integer cached = messageCountCache.get(threadChannelId);
        return cached != null ? cached : fallback;
    }

    public static void setMessageCount(String threadChannelId, int count) {
        if (threadChannelId != null) {
            messageCountCache.put(threadChannelId, count);
        }
    }

    public static void incrementMessageCount(String threadChannelId) {
        if (threadChannelId != null) {
            messageCountCache.merge(threadChannelId, 1, Integer::sum);
        }
    }

    public static void clearCache() {
        messageCountCache.clear();
    }

    /** 已知的 sourceMessageId 集合; 调用方仅用作只读判定。 */
    public static Set<String> getSourceMessageIdSet() {
        return sourceMessageIdSet;
    }

    /**
     * 通过普通消息的 messageId 查它是否已被作为子区源消息。命中返回对应 thread 元信息
     * (含 thread_name / channel_id / channel_type), miss 返回 null。
     */
    @Nullable
    public static WKThreadCreatedContent getThreadBySourceMessageId(String sourceMessageId) {
        if (TextUtils.isEmpty(sourceMessageId)) return null;
        return sourceMessageThreadMap.get(sourceMessageId);
    }

    public String content;
    public String from_uid;
    public String from_name;
    public String short_id;
    public String channel_id;
    public int channel_type;
    public String thread_name;
    public int message_count;
    /**
     * 该子区由哪条消息创建。后端透传, 仅在 decodeMsg 时读, 客户端不主动构造。
     * 与 iOS WKThreadCreatedContent.sourceMessageId 对齐。
     */
    public String source_message_id;

    public WKThreadCreatedContent() {
        type = WKContentType.threadCreated;
    }

    @NonNull
    @Override
    public JSONObject encodeMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("content", content);
            jsonObject.put("from_uid", from_uid);
            jsonObject.put("from_name", from_name);
            jsonObject.put("short_id", short_id);
            jsonObject.put("channel_id", channel_id);
            jsonObject.put("channel_type", channel_type);
            jsonObject.put("thread_name", thread_name);
            // 二次转发 / 持久化场景下也要把 source_message_id 写回, 与 iOS encodeWithJSON 一致。
            if (!TextUtils.isEmpty(source_message_id)) {
                try {
                    long srcLong = Long.parseLong(source_message_id);
                    jsonObject.put("source_message_id", srcLong);
                } catch (NumberFormatException ignore) {
                    jsonObject.put("source_message_id", source_message_id);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    @Override
    public WKMessageContent decodeMsg(JSONObject jsonObject) {
        content = jsonObject.optString("content");
        from_uid = jsonObject.optString("from_uid");
        from_name = jsonObject.optString("from_name");
        short_id = jsonObject.optString("short_id");
        channel_id = jsonObject.optString("channel_id");
        channel_type = jsonObject.optInt("channel_type");
        thread_name = jsonObject.optString("thread_name");
        message_count = jsonObject.optInt("message_count");
        // 后端透传 source_message_id, 兼容 number / string 两种格式 (与 iOS longLongValue 同口径)。
        // 命中即把 (sourceMessageId → this) 加进全局 map / set, 长按菜单后续会查 map 把
        // "创建子区"改成"进入子区「XX」"。
        long srcIdLong = jsonObject.optLong("source_message_id", 0L);
        String srcIdStr = null;
        if (srcIdLong > 0L) {
            srcIdStr = String.valueOf(srcIdLong);
        } else {
            String maybeStr = jsonObject.optString("source_message_id");
            if (!TextUtils.isEmpty(maybeStr) && !"0".equals(maybeStr)) {
                srcIdStr = maybeStr;
            }
        }
        if (!TextUtils.isEmpty(srcIdStr)) {
            source_message_id = srcIdStr;
            sourceMessageIdSet.add(srcIdStr);
            sourceMessageThreadMap.put(srcIdStr, this);
        }
        return this;
    }

    protected WKThreadCreatedContent(Parcel in) {
        super(in);
        content = in.readString();
        from_uid = in.readString();
        from_name = in.readString();
        short_id = in.readString();
        channel_id = in.readString();
        channel_type = in.readInt();
        thread_name = in.readString();
        message_count = in.readInt();
        source_message_id = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(content);
        dest.writeString(from_uid);
        dest.writeString(from_name);
        dest.writeString(short_id);
        dest.writeString(channel_id);
        dest.writeInt(channel_type);
        dest.writeString(thread_name);
        dest.writeInt(message_count);
        dest.writeString(source_message_id);
    }

    public static final Creator<WKThreadCreatedContent> CREATOR = new Creator<WKThreadCreatedContent>() {
        @Override
        public WKThreadCreatedContent createFromParcel(Parcel in) {
            return new WKThreadCreatedContent(in);
        }

        @Override
        public WKThreadCreatedContent[] newArray(int size) {
            return new WKThreadCreatedContent[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String getDisplayContent() {
        return content;
    }
}
