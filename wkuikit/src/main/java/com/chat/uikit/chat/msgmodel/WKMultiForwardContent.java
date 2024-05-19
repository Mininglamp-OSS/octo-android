package com.chat.uikit.chat.msgmodel;

import android.os.Parcel;
import android.text.TextUtils;

import com.chat.base.msgitem.WKContentType;
import com.chat.base.utils.WKReader;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.R;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
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
                }
                msg.timestamp = msgJson.optLong("timestamp");
                msg.messageID = msgJson.optString("message_id");
                if (msgJson.has("from_uid")) {
                    msg.fromUID = msgJson.optString("from_uid");
                    if (msg.baseContentMsgModel != null) {
                        msg.baseContentMsgModel.fromUID = msg.fromUID;
                    }
                }
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
                if (!TextUtils.isEmpty(msgList.get(i).content))
                    json.put("payload", new JSONObject(msgList.get(i).content));
                json.put("timestamp", msgList.get(i).timestamp);
                json.put("message_id", msgList.get(i).messageID);
                json.put("from_uid", msgList.get(i).fromUID);
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
                    userArr.put(json);
                }
                jsonObject.put("users", userArr);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    @Override
    public String getDisplayContent() {
        return WKUIKitApplication.getInstance().getContext().getString(R.string.last_msg_chat_record);
    }

    @Override
    public String getSearchableWord() {
        return WKUIKitApplication.getInstance().getContext().getString(R.string.last_msg_chat_record);
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
