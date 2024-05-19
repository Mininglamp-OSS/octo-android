package com.chat.base.msg.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.chat.base.WKBaseApplication;
import com.chat.base.R;
import com.xinbida.wukongim.message.type.WKMsgContentType;
import com.xinbida.wukongim.msgmodel.WKMediaMessageContent;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 2019-11-22 15:45
 * gif消息
 */
public class WKGifContent extends WKMediaMessageContent implements Parcelable {
    public int width;
    public int height;
    public String category;
    public String placeholder;
    public String format;
    public String title;

    public WKGifContent() {
        type = WKMsgContentType.WK_GIF;
    }

    @Override
    public JSONObject encodeMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("width", width);
            jsonObject.put("height", height);
            jsonObject.put("url", url);
            jsonObject.put("category", category);
            jsonObject.put("title", title);
            jsonObject.put("placeholder", placeholder);
            jsonObject.put("format", format);
            jsonObject.put("localPath", localPath);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    @Override
    public WKMessageContent decodeMsg(JSONObject jsonObject) {
        this.width = jsonObject.optInt("width");
        this.height = jsonObject.optInt("height");
        this.url = jsonObject.optString("url");
        this.category = jsonObject.optString("category");
        this.title = jsonObject.optString("title");
        this.localPath = jsonObject.optString("localPath");
        this.placeholder = jsonObject.optString("placeholder");
        this.format = jsonObject.optString("format");
        return this;
    }

    protected WKGifContent(Parcel in) {
        super(in);
        width = in.readInt();
        height = in.readInt();
        url = in.readString();
        category = in.readString();
        title = in.readString();
        placeholder = in.readString();
        format = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(width);
        dest.writeInt(height);
        dest.writeString(url);
        dest.writeString(category);
        dest.writeString(title);
        dest.writeString(placeholder);
        dest.writeString(format);
    }


    public static final Creator<WKGifContent> CREATOR = new Creator<WKGifContent>() {
        @Override
        public WKGifContent createFromParcel(Parcel in) {
            return new WKGifContent(in);
        }

        @Override
        public WKGifContent[] newArray(int size) {
            return new WKGifContent[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String getDisplayContent() {
        return "[GIF]";
    }
}
