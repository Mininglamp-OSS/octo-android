package com.xinbida.wukongim.msgmodel;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.xinbida.wukongim.message.type.WKMsgContentType;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 2020-04-04 10:35
 * 文本消息
 */
public class WKTextContent extends WKMessageContent {

    public WKTextContent(String content) {
        this.content = content;
        this.type = WKMsgContentType.WK_TEXT;
    }

    // 无参构造必须提供
    public WKTextContent() {
        this.type = WKMsgContentType.WK_TEXT;
    }

    @Override
    public JSONObject encodeMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            if (!TextUtils.isEmpty(content))
                jsonObject.put("content", content);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    @Override
    public WKMessageContent decodeMsg(JSONObject jsonObject) {
        if (jsonObject != null) {
            if (jsonObject.has("content"))
                this.content = jsonObject.optString("content");
        }
        return this;
    }

    @Override
    public String getSearchableWord() {
        return content;
    }

    @Override
    public String getDisplayContent() {
        return content;
    }

    protected WKTextContent(Parcel in) {
        super(in);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
    }


    public static final Parcelable.Creator<WKTextContent> CREATOR = new Parcelable.Creator<WKTextContent>() {
        @Override
        public WKTextContent createFromParcel(Parcel in) {
            return new WKTextContent(in);
        }

        @Override
        public WKTextContent[] newArray(int size) {
            return new WKTextContent[size];
        }
    };
}
