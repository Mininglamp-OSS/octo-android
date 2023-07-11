package com.chat.uikit.contacts;

import android.os.Parcel;
import android.os.Parcelable;

import com.chad.library.adapter.base.entity.MultiItemEntity;
import com.xinbida.wukongim.entity.WKChannel;

/**
 * 2020-07-07 15:12
 * 好友UI显示实例
 */
public class FriendUIEntity implements Parcelable, MultiItemEntity {
    public WKChannel channel;
    public String pying;
    public boolean check;
    public boolean isCanCheck = true;
    public int itemType;
    public boolean isSetDelete;
    public FriendUIEntity(WKChannel channel) {
        this.channel = channel;
    }

    protected FriendUIEntity(Parcel in) {
        channel = in.readParcelable(WKChannel.class.getClassLoader());
        pying = in.readString();
        check = in.readByte() != 0;
        isCanCheck = in.readByte() != 0;
        isSetDelete = in.readByte() != 0;
    }

    public static final Creator<FriendUIEntity> CREATOR = new Creator<FriendUIEntity>() {
        @Override
        public FriendUIEntity createFromParcel(Parcel in) {
            return new FriendUIEntity(in);
        }

        @Override
        public FriendUIEntity[] newArray(int size) {
            return new FriendUIEntity[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeParcelable(channel, i);
        parcel.writeString(pying);
        parcel.writeByte((byte) (check ? 1 : 0));
        parcel.writeByte((byte) (isCanCheck ? 1 : 0));
        parcel.writeByte((byte) (isSetDelete ? 1 : 0));
    }

    @Override
    public int getItemType() {
        return itemType;
    }
}
