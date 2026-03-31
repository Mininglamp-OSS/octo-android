package com.xinbida.wukongim.msgmodel;

import android.os.Parcel;
import android.os.Parcelable;

public class WKMsgEntity implements Parcelable {
    public int offset;
    public int length;
    public String type;
    public String value;

    public WKMsgEntity() {
    }

    protected WKMsgEntity(Parcel in) {
        offset = in.readInt();
        length = in.readInt();
        type = in.readString();
        value = in.readString();
    }

    public static final Creator<WKMsgEntity> CREATOR = new Creator<WKMsgEntity>() {
        @Override
        public WKMsgEntity createFromParcel(Parcel in) {
            return new WKMsgEntity(in);
        }

        @Override
        public WKMsgEntity[] newArray(int size) {
            return new WKMsgEntity[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(offset);
        parcel.writeInt(length);
        parcel.writeString(type);
        parcel.writeString(value);
    }
}
