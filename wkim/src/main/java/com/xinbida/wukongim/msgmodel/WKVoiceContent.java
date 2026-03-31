package com.xinbida.wukongim.msgmodel;

import android.os.Parcel;
import android.os.Parcelable;

import com.xinbida.wukongim.message.type.WKMsgContentType;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 2020-04-04 10:45
 * 内置语音消息model
 */
public class WKVoiceContent extends WKMediaMessageContent {
    public int timeTrad;
    public String waveform;

    public WKVoiceContent(String localPath, int timeTrad) {
        this.type = WKMsgContentType.WK_VOICE;
        this.timeTrad = timeTrad;
        this.localPath = localPath;
    }

    // 无参构造必须提供
    public WKVoiceContent() {
        this.type = WKMsgContentType.WK_VOICE;
    }

    @Override
    public JSONObject encodeMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("localPath", localPath);
            jsonObject.put("timeTrad", timeTrad);
            jsonObject.put("url", url);
            if (waveform != null)
                jsonObject.put("waveform", waveform);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    @Override
    public WKMessageContent decodeMsg(JSONObject jsonObject) {
        if (jsonObject.has("timeTrad"))
            timeTrad = jsonObject.optInt("timeTrad");
        if (jsonObject.has("localPath"))
            localPath = jsonObject.optString("localPath");
        if (jsonObject.has("url"))
            url = jsonObject.optString("url");
        if (jsonObject.has("waveform"))
            waveform = jsonObject.optString("waveform");
        return this;
    }


    protected WKVoiceContent(Parcel in) {
        super(in);
        timeTrad = in.readInt();
        url = in.readString();
        localPath = in.readString();
        waveform = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(timeTrad);
        dest.writeString(url);
        dest.writeString(localPath);
        dest.writeString(waveform);
    }


    public static final Parcelable.Creator<WKVoiceContent> CREATOR = new Parcelable.Creator<WKVoiceContent>() {
        @Override
        public WKVoiceContent createFromParcel(Parcel in) {
            return new WKVoiceContent(in);
        }

        @Override
        public WKVoiceContent[] newArray(int size) {
            return new WKVoiceContent[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String getDisplayContent() {
        return "[语音]";
    }

    @Override
    public String getSearchableWord() {
        return "[语音]";
    }
}
