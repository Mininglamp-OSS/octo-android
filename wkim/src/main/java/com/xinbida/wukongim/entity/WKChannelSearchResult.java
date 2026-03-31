package com.xinbida.wukongim.entity;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 2020-05-10 19:16
 * 频道搜索结果
 */
public class WKChannelSearchResult implements Parcelable {
    //频道信息
    public WKChannel wkChannel;
    //包含的成员名称
    public String containMemberName;

    public WKChannelSearchResult() {
    }

    protected WKChannelSearchResult(Parcel in) {
        wkChannel = in.readParcelable(WKChannel.class.getClassLoader());
        containMemberName = in.readString();
    }

    public static final Creator<WKChannelSearchResult> CREATOR = new Creator<WKChannelSearchResult>() {
        @Override
        public WKChannelSearchResult createFromParcel(Parcel in) {
            return new WKChannelSearchResult(in);
        }

        @Override
        public WKChannelSearchResult[] newArray(int size) {
            return new WKChannelSearchResult[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(wkChannel, flags);
        dest.writeString(containMemberName);
    }
}
