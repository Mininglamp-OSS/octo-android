package com.xinbida.wukongim.entity;

import android.os.Parcel;
import android.os.Parcelable;

public class WKMsgHeader implements Parcelable {
    //是否持久化[是否不保存在数据库]
    public boolean noPersist;
    //对方是否显示红点
    public boolean redDot = true;
    //消息是否只同步一次
    public boolean syncOnce;

    WKMsgHeader() {

    }

    protected WKMsgHeader(Parcel in) {
        noPersist = in.readByte() != 0;
        redDot = in.readByte() != 0;
        syncOnce = in.readByte() != 0;
    }

    public static final Creator<WKMsgHeader> CREATOR = new Creator<WKMsgHeader>() {
        @Override
        public WKMsgHeader createFromParcel(Parcel in) {
            return new WKMsgHeader(in);
        }

        @Override
        public WKMsgHeader[] newArray(int size) {
            return new WKMsgHeader[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeByte((byte) (noPersist ? 1 : 0));
        parcel.writeByte((byte) (redDot ? 1 : 0));
        parcel.writeByte((byte) (syncOnce ? 1 : 0));
    }
}
