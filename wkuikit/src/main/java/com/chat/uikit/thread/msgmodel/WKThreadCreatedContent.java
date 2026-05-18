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

import androidx.annotation.NonNull;

import com.chat.base.msgitem.WKContentType;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.R;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ConcurrentHashMap;

public class WKThreadCreatedContent extends WKMessageContent {

    // 全局缓存：threadChannelId → 最新消息条数（对齐 iOS messageCountCache）
    private static final ConcurrentHashMap<String, Integer> messageCountCache = new ConcurrentHashMap<>();

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

    public String content;
    public String from_uid;
    public String from_name;
    public String short_id;
    public String channel_id;
    public int channel_type;
    public String thread_name;
    public int message_count;

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
