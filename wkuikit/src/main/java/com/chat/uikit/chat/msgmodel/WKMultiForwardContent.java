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

package com.chat.uikit.chat.msgmodel;

import android.os.Parcel;
import android.text.TextUtils;

import com.chat.base.msgitem.WKContentType;
import com.chat.base.utils.WKLogUtils;
import com.chat.base.utils.WKReader;
import com.chat.uikit.R;
import com.chat.uikit.WKUIKitApplication;
import com.chat.base.external.ExternalMsgExtras;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMemberExtras;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 2020-09-22 10:01
 * 合并转发消息
 */
public class WKMultiForwardContent extends WKMessageContent {
    public byte channelType;
    public List<WKChannel> userList;
    public List<WKMsg> msgList;

    public WKMultiForwardContent() {
        type = WKContentType.WK_MULTIPLE_FORWARD;
    }

    @Override
    public WKMessageContent decodeMsg(JSONObject jsonObject) {
        channelType = (byte) jsonObject.optInt("channel_type");
        JSONArray msgArr = jsonObject.optJSONArray("msgs");
        if (msgArr != null && msgArr.length() > 0) {
            msgList = new ArrayList<>();
            for (int i = 0, size = msgArr.length(); i < size; i++) {
                JSONObject msgJson = msgArr.optJSONObject(i);
                WKMsg msg = new WKMsg();
                JSONObject contentJson = msgJson.optJSONObject("payload");
                if (contentJson != null) {
                    msg.content = contentJson.toString();
                    msg.baseContentMsgModel = WKIM.getInstance().getMsgManager().getMsgContentModel(contentJson);
                    if (msg.baseContentMsgModel != null) {
                        msg.type = msg.baseContentMsgModel.type;
                    }
                } else {
                    msg.baseContentMsgModel = new WKMessageContent();
                    msg.type = WKContentType.unknown_msg;
                }
                msg.timestamp = msgJson.optLong("timestamp");
                msg.messageID = msgJson.optString("message_id");
                if (msgJson.has("from_uid")) {
                    msg.fromUID = msgJson.optString("from_uid");
//                    if (msg.baseContentMsgModel != null) {
//                        msg.baseContentMsgModel.fromUID = msg.fromUID;
//                    }
                }
                // Inherit the origin channel type from the wrapper so downstream
                // consumers (notably ExternalSourceResolver's group guard) can
                // reason about the forwarded msg. decodeMsg previously left
                // channelType == 0, which silently disabled msg-level
                // external-source resolution in the merge-forward detail view
                // (caught by /codex review on ).
                msg.channelType = channelType;
                //  / web PR #981: per-message external source fields inside merge-forward.
                copyExternalFieldsToLocalExtra(msg, msgJson);
                msgList.add(msg);
            }
        }
        JSONArray userArr = jsonObject.optJSONArray("users");
        if (userArr != null && userArr.length() > 0) {
            userList = new ArrayList<>();
            for (int i = 0, size = userArr.length(); i < size; i++) {
                JSONObject userJson = userArr.optJSONObject(i);
                WKChannel channel = new WKChannel();
                if (userJson.has("uid"))
                    channel.channelID = userJson.optString("uid");
                if (userJson.has("name"))
                    channel.channelName = userJson.optString("name");
                if (userJson.has("avatar"))
                    channel.avatar = userJson.optString("avatar");
                // 外部群 Phase 1 — 合并转发消息 users 透传外部字段（ EP1 / web #981）。
                // 用户头/气泡在合并转发详情页渲染时需要知道发送者是否外部、来自哪个
                // Space。WKChannel 没有直接字段，统一写进 remoteExtraMap，复用频道
                // 级 extra 读取方式。
                //
                // 注 1：空值判空故意用纯 Java（!= null && !isEmpty()）而不是 TextUtils，
                // 为让 JVM 单测在 unitTests.returnDefaultValues=true 下也能跑通
                // （TextUtils.isEmpty stub 始终返回 false，会把空值当非空）。
                // 参见  EP1 codex review P1。
                //
                // 注 2：每个 optString 之前都先 isNull() 守卫。AOSP 的 JSONObject.optString
                // 对 JSON null 返回字符串 "null"（而 json.org 返回 ""），不守卫就会把
                // 字面量 "null" 写进 remoteExtraMap，UI 侧会展示一个叫 "null" 的 Space。
                // 参见  EP1 claude review P1。
                HashMap<String, Object> extras = null;
                boolean hasSourceSpaceId = userJson.has("source_space_id") && !userJson.isNull("source_space_id");
                boolean hasSourceSpaceName = userJson.has("source_space_name") && !userJson.isNull("source_space_name");
                boolean hasHomeSpaceId = userJson.has("home_space_id") && !userJson.isNull("home_space_id");
                boolean hasHomeSpaceName = userJson.has("home_space_name") && !userJson.isNull("home_space_name");
                boolean hasIsExternal = userJson.has("is_external") && !userJson.isNull("is_external");
                if (hasIsExternal || hasSourceSpaceId || hasSourceSpaceName || hasHomeSpaceId || hasHomeSpaceName) {
                    extras = new HashMap<>();
                    if (hasIsExternal) {
                        extras.put(WKChannelMemberExtras.isExternal, userJson.optInt("is_external"));
                    }
                    if (hasSourceSpaceId) {
                        String v = userJson.optString("source_space_id");
                        if (v != null && !v.isEmpty()) {
                            extras.put(WKChannelMemberExtras.sourceSpaceID, v);
                        }
                    }
                    if (hasSourceSpaceName) {
                        String v = userJson.optString("source_space_name");
                        if (v != null && !v.isEmpty()) {
                            extras.put(WKChannelMemberExtras.sourceSpaceName, v);
                        }
                    }
                    if (hasHomeSpaceId) {
                        String v = userJson.optString("home_space_id");
                        if (v != null && !v.isEmpty()) {
                            extras.put(WKChannelMemberExtras.homeSpaceID, v);
                        }
                    }
                    if (hasHomeSpaceName) {
                        String v = userJson.optString("home_space_name");
                        if (v != null && !v.isEmpty()) {
                            extras.put(WKChannelMemberExtras.homeSpaceName, v);
                        }
                    }
                }
                if (extras != null && !extras.isEmpty()) {
                    channel.remoteExtraMap = extras;
                }
                userList.add(channel);
            }
        }
        return this;
    }

    @Override
    public JSONObject encodeMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("channel_type", channelType);
            JSONArray jsonArray = new JSONArray();
            for (int i = 0, size = msgList.size(); i < size; i++) {
                JSONObject json = new JSONObject();
                if (!TextUtils.isEmpty(msgList.get(i).content)) {
                    json.put("payload", new JSONObject(msgList.get(i).content));
                }
                json.put("timestamp", msgList.get(i).timestamp);
                json.put("message_id", msgList.get(i).messageID);
                json.put("from_uid", msgList.get(i).fromUID);
                writeExternalFieldsFromLocalExtra(json, msgList.get(i).localExtraMap);
                jsonArray.put(json);
            }
            jsonObject.put("msgs", jsonArray);
            if (WKReader.isNotEmpty(userList)) {
                JSONArray userArr = new JSONArray();
                for (int i = 0, size = userList.size(); i < size; i++) {
                    JSONObject json = new JSONObject();
                    json.put("uid", userList.get(i).channelID);
                    json.put("name", userList.get(i).channelName);
                    json.put("avatar", userList.get(i).avatar);
                    // 外部群 Phase 1 — 转发编码侧也要把外部字段写回（ EP1 / web #981），
                    // 否则二次转发时下游会丢掉 is_external / source_space_id /
                    // source_space_name / home_space_*。
                    //
                    // 每个 String 字段都做非空守卫，对齐 decodeMsg 的 "has() + !isNull()
                    // + !isEmpty()" 三重门 —— 否则 WKCommonUtils.str2HashMap 路径写进来
                    // 的空串会在 encode 时序列化为 "source_space_name":""，污染下游（参见
                    //  EP1 claude review round-2 P2）。
                    HashMap<?, ?> extras = userList.get(i).remoteExtraMap;
                    if (extras != null) {
                        Object isExternal = extras.get(WKChannelMemberExtras.isExternal);
                        if (isExternal != null) {
                            int v;
                            if (isExternal instanceof Integer) {
                                v = (Integer) isExternal;
                            } else if (isExternal instanceof Number) {
                                v = ((Number) isExternal).intValue();
                            } else {
                                try {
                                    v = Integer.parseInt(String.valueOf(isExternal));
                                } catch (NumberFormatException e) {
                                    v = 0;
                                }
                            }
                            json.put("is_external", v);
                        }
                        putNonEmptyString(json, "source_space_id",
                                extras.get(WKChannelMemberExtras.sourceSpaceID));
                        putNonEmptyString(json, "source_space_name",
                                extras.get(WKChannelMemberExtras.sourceSpaceName));
                        putNonEmptyString(json, "home_space_id",
                                extras.get(WKChannelMemberExtras.homeSpaceID));
                        putNonEmptyString(json, "home_space_name",
                                extras.get(WKChannelMemberExtras.homeSpaceName));
                    }
                    userArr.put(json);
                }
                jsonObject.put("users", userArr);
            }
        } catch (JSONException e) {
            WKLogUtils.e("编码合并转发消息错误");
        }
        return jsonObject;
    }

    /**
     *  external-source passthrough: copy the five viewer-relative fields
     * from a merge-forward JSON entry into {@code msg.localExtraMap} so the
     * downstream bubble renderer can resolve the "@SpaceName" suffix without
     * another round trip. Visible for testing.
     *
     * <p>Wire keys have no {@code from_} prefix, matching web PR #981.
     */
    static void copyExternalFieldsToLocalExtra(WKMsg msg, JSONObject msgJson) {
        if (msg == null || msgJson == null) return;
        HashMap<String, Object> extras = null;
        String[] keys = new String[]{
                ExternalMsgExtras.IS_EXTERNAL,
                ExternalMsgExtras.SOURCE_SPACE_ID,
                ExternalMsgExtras.SOURCE_SPACE_NAME,
                ExternalMsgExtras.HOME_SPACE_ID,
                ExternalMsgExtras.HOME_SPACE_NAME,
        };
        for (String key : keys) {
            if (msgJson.has(key) && !msgJson.isNull(key)) {
                if (extras == null) {
                    extras = msg.localExtraMap != null ? new HashMap<>(msg.localExtraMap) : new HashMap<>();
                }
                extras.put(key, msgJson.opt(key));
            }
        }
        if (extras != null) {
            msg.localExtraMap = extras;
        }
    }

    private static void writeExternalFieldsFromLocalExtra(JSONObject json, java.util.Map<?, ?> extras) throws JSONException {
        if (json == null || extras == null || extras.isEmpty()) return;
        String[] keys = new String[]{
                ExternalMsgExtras.IS_EXTERNAL,
                ExternalMsgExtras.SOURCE_SPACE_ID,
                ExternalMsgExtras.SOURCE_SPACE_NAME,
                ExternalMsgExtras.HOME_SPACE_ID,
                ExternalMsgExtras.HOME_SPACE_NAME,
        };
        for (String key : keys) {
            Object v = extras.get(key);
            if (v != null) {
                json.put(key, v);
            }
        }
    }

    @Override
    public String getDisplayContent() {
        return WKUIKitApplication.getInstance().getContext().getString(R.string.last_msg_chat_record);
    }

    @Override
    public String getSearchableWord() {
        return WKUIKitApplication.getInstance().getContext().getString(R.string.last_msg_chat_record);
    }

    /**
     * 把 remoteExtraMap 里可能为空 / null 的字符串字段写进 JSON，空串 / null 直接跳过。
     * 对齐 decodeMsg 的 "has() + !isNull() + !isEmpty()" 三重门，防止 round-trip
     * 把 "" 写到线上（参见  EP1 claude review round-2 P2）。
     */
    private static void putNonEmptyString(JSONObject json, String key, Object value)
            throws JSONException {
        if (value == null) return;
        String s = String.valueOf(value);
        if (s.isEmpty()) return;
        json.put(key, s);
    }

    public WKMultiForwardContent(Parcel in) {
        super(in);
        channelType = in.readByte();
        userList = in.createTypedArrayList(WKChannel.CREATOR);
        msgList = in.createTypedArrayList(WKMsg.CREATOR);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeByte(channelType);
        dest.writeTypedList(userList);
        dest.writeTypedList(msgList);
    }

    public static final Creator<WKMultiForwardContent> CREATOR = new Creator<WKMultiForwardContent>() {
        @Override
        public WKMultiForwardContent createFromParcel(Parcel in) {
            return new WKMultiForwardContent(in);
        }

        @Override
        public WKMultiForwardContent[] newArray(int size) {
            return new WKMultiForwardContent[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

}
