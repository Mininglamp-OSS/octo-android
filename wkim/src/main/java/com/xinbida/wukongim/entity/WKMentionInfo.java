package com.xinbida.wukongim.entity;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

/**
 * 2020-10-22 13:28
 * 提醒对象
 */
public class WKMentionInfo implements Parcelable {

    public boolean isMentionMe;
    public List<String> uids;

    public WKMentionInfo() {
    }

    protected WKMentionInfo(Parcel in) {
        isMentionMe = in.readByte() != 0;
        uids = in.createStringArrayList();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (isMentionMe ? 1 : 0));
        dest.writeStringList(uids);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<WKMentionInfo> CREATOR = new Creator<WKMentionInfo>() {
        @Override
        public WKMentionInfo createFromParcel(Parcel in) {
            return new WKMentionInfo(in);
        }

        @Override
        public WKMentionInfo[] newArray(int size) {
            return new WKMentionInfo[size];
        }
    };
}
