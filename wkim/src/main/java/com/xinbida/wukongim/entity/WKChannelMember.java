package com.xinbida.wukongim.entity;

import android.os.Parcel;
import android.os.Parcelable;

import com.xinbida.wukongim.utils.DateUtils;
import com.xinbida.wukongim.utils.WKCommonUtils;

import org.json.JSONObject;

import java.util.HashMap;

/**
 * 2019-11-09 18:07
 * 频道成员实体
 */
public class WKChannelMember implements Parcelable {
    //自增ID
    public long id;
    //频道id
    public String channelID;
    //频道类型
    public byte channelType;
    //成员id
    public String memberUID;
    //成员名称
    public String memberName;
    //成员备注
    public String memberRemark;
    //成员头像
    public String memberAvatar;
    //成员角色
    public int role;
    //成员状态黑名单等1：正常2：黑名单
    public int status;
    //是否删除
    public int isDeleted;
    //创建时间
    public String createdAt;
    //修改时间
    public String updatedAt;
    //版本
    public long version;
    // 机器人0否1是
    public int robot;
    //扩展字段
    public HashMap extraMap;
    // 用户备注
    public String remark;
    // 邀请者uid
    public String memberInviteUID;
    // 被禁言到期时间
    public long forbiddenExpirationTime;
    public String memberAvatarCacheKey;

    public WKChannelMember() {
        createdAt = DateUtils.getInstance().time2DateStr(DateUtils.getInstance().getCurrentSeconds());
        updatedAt = DateUtils.getInstance().time2DateStr(DateUtils.getInstance().getCurrentSeconds());
    }

    protected WKChannelMember(Parcel in) {
        id = in.readLong();
        status = in.readInt();
        channelID = in.readString();
        channelType = in.readByte();
        memberUID = in.readString();
        memberName = in.readString();
        memberRemark = in.readString();
        memberAvatar = in.readString();
        role = in.readInt();
        isDeleted = in.readInt();
        createdAt = in.readString();
        updatedAt = in.readString();
        version = in.readLong();
        remark = in.readString();
        memberInviteUID = in.readString();
        robot = in.readInt();
        forbiddenExpirationTime = in.readLong();
        memberAvatarCacheKey = in.readString();
        String extraStr = in.readString();
        extraMap = WKCommonUtils.str2HashMap(extraStr);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeInt(status);
        dest.writeString(channelID);
        dest.writeByte(channelType);
        dest.writeString(memberUID);
        dest.writeString(memberName);
        dest.writeString(memberRemark);
        dest.writeString(memberAvatar);
        dest.writeInt(role);
        dest.writeInt(isDeleted);
        dest.writeString(createdAt);
        dest.writeString(updatedAt);
        dest.writeLong(version);
        dest.writeString(remark);
        dest.writeString(memberInviteUID);
        dest.writeInt(robot);
        dest.writeLong(forbiddenExpirationTime);
        dest.writeString(memberAvatarCacheKey);
        String extraStr = "";
        if (extraMap != null && !extraMap.isEmpty()) {
            JSONObject jsonObject = new JSONObject(extraMap);
            extraStr = jsonObject.toString();
        }
        dest.writeString(extraStr);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<WKChannelMember> CREATOR = new Creator<WKChannelMember>() {
        @Override
        public WKChannelMember createFromParcel(Parcel in) {
            return new WKChannelMember(in);
        }

        @Override
        public WKChannelMember[] newArray(int size) {
            return new WKChannelMember[size];
        }
    };
}
