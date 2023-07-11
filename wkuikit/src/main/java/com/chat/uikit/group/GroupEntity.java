package com.chat.uikit.group;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 2019-12-04 11:30
 * 群信息
 */
public class GroupEntity implements Parcelable {
    public String name;
    public String remark;
    public String group_no;
    public int mute;
    public int forbidden;
    public int invite;
    public int status;
    public int top;
    public int save;
    public int receipt;
    public int show_nick;
    public int forbidden_add_friend;
    public int screenshot;
    public int chat_pwd_on;
    public int allow_view_history_msg;
    public int join_group_remind;
    public int revoke_remind;
    public String notice;
    public String avatar;
    public String created_at;
    public String updated_at;

    public GroupEntity() {
    }

    protected GroupEntity(Parcel in) {
        name = in.readString();
        remark = in.readString();
        group_no = in.readString();
        mute = in.readInt();
        forbidden = in.readInt();
        invite = in.readInt();
        top = in.readInt();
        save = in.readInt();
        show_nick = in.readInt();
        notice = in.readString();
        created_at = in.readString();
        updated_at = in.readString();
        forbidden_add_friend = in.readInt();
        screenshot = in.readInt();
        join_group_remind = in.readInt();
        revoke_remind = in.readInt();
        chat_pwd_on = in.readInt();
        status = in.readInt();
        receipt = in.readInt();
        allow_view_history_msg = in.readInt();
        avatar = in.readString();
    }

    public static final Creator<GroupEntity> CREATOR = new Creator<GroupEntity>() {
        @Override
        public GroupEntity createFromParcel(Parcel in) {
            return new GroupEntity(in);
        }

        @Override
        public GroupEntity[] newArray(int size) {
            return new GroupEntity[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(remark);
        dest.writeString(group_no);
        dest.writeInt(mute);
        dest.writeInt(forbidden);
        dest.writeInt(invite);
        dest.writeInt(top);
        dest.writeInt(save);
        dest.writeInt(show_nick);
        dest.writeString(notice);
        dest.writeString(created_at);
        dest.writeString(updated_at);
        dest.writeInt(forbidden_add_friend);
        dest.writeInt(screenshot);
        dest.writeInt(join_group_remind);
        dest.writeInt(revoke_remind);
        dest.writeInt(chat_pwd_on);
        dest.writeInt(status);
        dest.writeInt(receipt);
        dest.writeInt(allow_view_history_msg);
        dest.writeString(avatar);
    }
}
