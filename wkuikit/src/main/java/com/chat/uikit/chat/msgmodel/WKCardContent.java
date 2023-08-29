package com.chat.uikit.chat.msgmodel;

import android.os.Parcel;

import androidx.annotation.NonNull;

import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.R;
import com.xinbida.wukongim.message.type.WKMsgContentType;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 2020-03-19 21:17
 * 名片消息
 */
public class WKCardContent extends WKMessageContent {
    public WKCardContent() {
        type = WKMsgContentType.WK_CARD;
    }

    public String uid;
    public String name;
    public String vercode;
    public String avatar;

    @NonNull
    @Override
    public JSONObject encodeMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("uid", uid);
            jsonObject.put("name", name);
            jsonObject.put("avatar", avatar);
            jsonObject.put("vercode", vercode);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    @Override
    public WKMessageContent decodeMsg(JSONObject jsonObject) {
        uid = jsonObject.optString("uid");
        name = jsonObject.optString("name");
        avatar = jsonObject.optString("avatar");
        vercode = jsonObject.optString("vercode");
        return this;
    }

    protected WKCardContent(Parcel in) {
        super(in);
        uid = in.readString();
        name = in.readString();
        avatar = in.readString();
        vercode = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(uid);
        dest.writeString(name);
        dest.writeString(avatar);
        dest.writeString(vercode);
    }


    public static final Creator<WKCardContent> CREATOR = new Creator<WKCardContent>() {
        @Override
        public WKCardContent createFromParcel(Parcel in) {
            return new WKCardContent(in);
        }

        @Override
        public WKCardContent[] newArray(int size) {
            return new WKCardContent[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String getDisplayContent() {
        return WKUIKitApplication.getInstance().getContext().getString(R.string.last_msg_card);
    }

}
