package com.xinbida.wukongim.entity;

import android.os.Parcel;
import android.os.Parcelable;

public class WKMsgSetting implements Parcelable {
    // 消息是否回执
    public int receipt;
    // 是否开启top
    public int topic;
    // 是否未流消息
    public int stream;

    public WKMsgSetting() {
    }

    protected WKMsgSetting(Parcel in) {
        receipt = in.readInt();
        topic = in.readInt();
        stream = in.readInt();
    }

    public static final Creator<WKMsgSetting> CREATOR = new Creator<WKMsgSetting>() {
        @Override
        public WKMsgSetting createFromParcel(Parcel in) {
            return new WKMsgSetting(in);
        }

        @Override
        public WKMsgSetting[] newArray(int size) {
            return new WKMsgSetting[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(receipt);
        dest.writeInt(topic);
        dest.writeInt(stream);
    }
}
