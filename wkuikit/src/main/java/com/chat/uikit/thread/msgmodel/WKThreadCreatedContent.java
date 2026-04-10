package com.chat.uikit.thread.msgmodel;

import android.os.Parcel;

import androidx.annotation.NonNull;

import com.chat.base.msgitem.WKContentType;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.R;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import org.json.JSONException;
import org.json.JSONObject;

public class WKThreadCreatedContent extends WKMessageContent {

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
