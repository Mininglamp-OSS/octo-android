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
    /**
     * 三态 mention：@所有人（新协议 mention.humans=1，与 legacy mention.all=1 并存）。
     * humans=true 时人类客户端应当被视为 @ 到（同 @所有人 触发 reminder）。
     */
    public boolean humans;
    /**
     * 三态 mention：@所有AI（mention.ais=1）。
     * ais=true 单独命中时人类客户端不算被 @ 到（不触发 [有人@我] reminder），
     * 仅 bot 客户端通过 mention.ais=1 路由消费。
     */
    public boolean ais;

    public WKMentionInfo() {
    }

    protected WKMentionInfo(Parcel in) {
        isMentionMe = in.readByte() != 0;
        uids = in.createStringArrayList();
        humans = in.readByte() != 0;
        ais = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (isMentionMe ? 1 : 0));
        dest.writeStringList(uids);
        dest.writeByte((byte) (humans ? 1 : 0));
        dest.writeByte((byte) (ais ? 1 : 0));
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
