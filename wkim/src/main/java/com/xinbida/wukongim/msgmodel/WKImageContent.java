package com.xinbida.wukongim.msgmodel;

import android.os.Parcel;
import android.os.Parcelable;

import com.xinbida.wukongim.message.type.WKMsgContentType;
import com.xinbida.wukongim.utils.WKLoggerUtils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 2020-04-04 10:42
 * 图片消息
 */
public class WKImageContent extends WKMediaMessageContent {
    private final String TAG = "WKImageContent";
    public int width;
    public int height;

    public WKImageContent(String localPath) {
        this.localPath = localPath;
        this.type = WKMsgContentType.WK_IMAGE;
    }

    // 无参构造必须提供
    public WKImageContent() {
        this.type = WKMsgContentType.WK_IMAGE;
    }

    @Override
    public JSONObject encodeMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("url", url);
            jsonObject.put("width", width);
            jsonObject.put("height", height);
            jsonObject.put("localPath", localPath);
        } catch (JSONException e) {
            WKLoggerUtils.getInstance().e(TAG, "encodeMsg error");
        }
        return jsonObject;
    }

    @Override
    public WKMessageContent decodeMsg(JSONObject jsonObject) {
        if (jsonObject.has("url"))
            this.url = jsonObject.optString("url");
        if (jsonObject.has("localPath"))
            this.localPath = jsonObject.optString("localPath");
        if (jsonObject.has("height"))
            this.height = jsonObject.optInt("height");
        if (jsonObject.has("width"))
            this.width = jsonObject.optInt("width");
        return this;
    }


    protected WKImageContent(Parcel in) {
        super(in);
        width = in.readInt();
        height = in.readInt();
        url = in.readString();
        localPath = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(width);
        dest.writeInt(height);
        dest.writeString(url);
        dest.writeString(localPath);
    }


    public static final Parcelable.Creator<WKImageContent> CREATOR = new Parcelable.Creator<WKImageContent>() {
        @Override
        public WKImageContent createFromParcel(Parcel in) {
            return new WKImageContent(in);
        }

        @Override
        public WKImageContent[] newArray(int size) {
            return new WKImageContent[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String getDisplayContent() {
        return "[图片]";
    }

    @Override
    public String getSearchableWord() {
        return "[图片]";
    }
}
