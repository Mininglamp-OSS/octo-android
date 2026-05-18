package com.xinbida.wukongim.msgmodel;

import android.os.Parcel;
import android.os.Parcelable;

import com.xinbida.wukongim.manager.MsgManager;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 2020-10-13 12:12
 * 消息回复
 */
public class WKReply implements Parcelable {
    public String root_mid;
    public String message_id;
    public long message_seq;
    public String from_uid;
    public String from_name;
    public int revoke;
    public WKMessageContent payload;
    // 编辑后内容
    public String contentEdit;
    // 编辑消息实体
    public WKMessageContent contentEditMsgModel;
    // 编辑时间
    public long editAt;

    // ===== 外部群 Reply 预览 @SpaceName 字段（，对齐 Web PR#1073 / iOS）=====
    // 当被回复消息的发送者来自其他 Space 时，服务端在 reply 对象中附带这四个字段，
    // 客户端据此在引用气泡的发送者名称后追加 " @SourceSpaceName"。
    // 字段全部可选：缺失时保持默认值（0 / null），由 ExternalSourceResolver 走降级链。

    /** 1 = 被回复用户来自外部 Space；0 = 同 Space 或字段缺失。 */
    public int from_is_external;
    /** 绝对视角下被回复用户的源 Space 显示名（次优先级）。 */
    public String from_source_space_name;
    /** 被回复用户 home Space id（最高优先级：viewer-relative 判定依据）。 */
    public String from_home_space_id;
    /** 被回复用户 home Space 显示名（最高优先级：viewer-relative 直出）。 */
    public String from_home_space_name;

    public WKReply() {
    }

    protected WKReply(Parcel in) {
        root_mid = in.readString();
        message_id = in.readString();
        message_seq = in.readLong();
        from_uid = in.readString();
        from_name = in.readString();
        payload = in.readParcelable(WKMessageContent.class.getClassLoader());
        contentEditMsgModel = in.readParcelable(WKMessageContent.class.getClassLoader());
        contentEdit = in.readString();
        editAt = in.readLong();
        revoke = in.readInt();
        from_is_external = in.readInt();
        from_source_space_name = in.readString();
        from_home_space_id = in.readString();
        from_home_space_name = in.readString();
    }

    public static final Creator<WKReply> CREATOR = new Creator<WKReply>() {
        @Override
        public WKReply createFromParcel(Parcel in) {
            return new WKReply(in);
        }

        @Override
        public WKReply[] newArray(int size) {
            return new WKReply[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(root_mid);
        dest.writeString(message_id);
        dest.writeLong(message_seq);
        dest.writeString(from_uid);
        dest.writeString(from_name);
        dest.writeParcelable(payload, flags);
        dest.writeParcelable(contentEditMsgModel, flags);
        dest.writeString(contentEdit);
        dest.writeLong(editAt);
        dest.writeInt(revoke);
        dest.writeInt(from_is_external);
        dest.writeString(from_source_space_name);
        dest.writeString(from_home_space_id);
        dest.writeString(from_home_space_name);
    }

    public JSONObject encodeMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("root_mid", root_mid);
            jsonObject.put("message_id", message_id);
            jsonObject.put("message_seq", message_seq);
            jsonObject.put("from_uid", from_uid);
            jsonObject.put("from_name", from_name);
            JSONObject payloadJson = payload.encodeMsg();
            if (payloadJson != null && !payloadJson.has("type")) {
                payloadJson.put("type", payload.type);
            }
            jsonObject.put("payload", payloadJson);
            // 外部群来源字段 round-trip（）：仅在有信号时写入，避免污染老数据。
            if (from_is_external != 0
                    || from_source_space_name != null
                    || from_home_space_id != null
                    || from_home_space_name != null) {
                jsonObject.put("from_is_external", from_is_external);
                if (from_source_space_name != null) {
                    jsonObject.put("from_source_space_name", from_source_space_name);
                }
                if (from_home_space_id != null) {
                    jsonObject.put("from_home_space_id", from_home_space_id);
                }
                if (from_home_space_name != null) {
                    jsonObject.put("from_home_space_name", from_home_space_name);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    public WKReply decodeMsg(JSONObject jsonObject) {
        this.root_mid = jsonObject.optString("root_mid");
        this.message_id = jsonObject.optString("message_id");
        this.message_seq = jsonObject.optLong("message_seq");
        this.from_uid = jsonObject.optString("from_uid");
        this.from_name = jsonObject.optString("from_name");
        if (jsonObject.has("payload")) {
            JSONObject payloadJson = jsonObject.optJSONObject("payload");
            if (payloadJson != null)
                payload = MsgManager.getInstance().getMsgContentModel(payloadJson);
        }
        // 外部群来源字段解析（ · 对齐 Web PR#1073 / iOS）。全部 optional：
        // 缺失时保持默认值（int=0, String=null），由上层 ExternalSourceResolver 决定是否渲染后缀。
        this.from_is_external = jsonObject.optInt("from_is_external", 0);
        this.from_source_space_name = jsonObject.has("from_source_space_name")
                ? jsonObject.optString("from_source_space_name", null)
                : null;
        this.from_home_space_id = jsonObject.has("from_home_space_id")
                ? jsonObject.optString("from_home_space_id", null)
                : null;
        this.from_home_space_name = jsonObject.has("from_home_space_name")
                ? jsonObject.optString("from_home_space_name", null)
                : null;

        return this;
    }
}
