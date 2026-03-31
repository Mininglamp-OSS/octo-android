package com.xinbida.wukongim.entity;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 2020-05-10 22:26
 * 消息搜索结果
 */
public class WKMessageSearchResult implements Parcelable {
    //消息对应的频道信息
    public WKChannel wkChannel;
    //包含关键字的信息
    public String searchableWord;
    //条数
    public int messageCount;

    public WKMessageSearchResult() {
    }

    protected WKMessageSearchResult(Parcel in) {
        wkChannel = in.readParcelable(WKChannel.class.getClassLoader());
        searchableWord = in.readString();
        messageCount = in.readInt();
    }

    public static final Creator<WKMessageSearchResult> CREATOR = new Creator<WKMessageSearchResult>() {
        @Override
        public WKMessageSearchResult createFromParcel(Parcel in) {
            return new WKMessageSearchResult(in);
        }

        @Override
        public WKMessageSearchResult[] newArray(int size) {
            return new WKMessageSearchResult[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(wkChannel, flags);
        dest.writeString(searchableWord);
        dest.writeInt(messageCount);
    }
}
