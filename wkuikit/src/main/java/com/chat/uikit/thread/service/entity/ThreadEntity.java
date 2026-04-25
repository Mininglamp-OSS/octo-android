package com.chat.uikit.thread.service.entity;

import android.os.Parcel;
import android.os.Parcelable;

public class ThreadEntity implements Parcelable {
    public String short_id;
    public String group_no;
    public String name;
    public String creator_uid;
    public String creator_name;
    public int status; // 0=active, 1=archived, 2=deleted
    public int member_count;
    public int message_count;
    public String source_message_id;
    public String channel_id;
    public int channel_type;
    public String last_message_content;
    public String last_message_sender_name;
    public long last_message_time;
    public int unread_count;
    public int is_joined;
    public String created_at;
    public String updated_at;
    public boolean has_thread_md;
    public int thread_md_version;

    public ThreadEntity() {
    }

    protected ThreadEntity(Parcel in) {
        short_id = in.readString();
        group_no = in.readString();
        name = in.readString();
        creator_uid = in.readString();
        creator_name = in.readString();
        status = in.readInt();
        member_count = in.readInt();
        message_count = in.readInt();
        source_message_id = in.readString();
        channel_id = in.readString();
        channel_type = in.readInt();
        last_message_content = in.readString();
        last_message_time = in.readLong();
        unread_count = in.readInt();
        is_joined = in.readInt();
        created_at = in.readString();
        updated_at = in.readString();
    }

    public static final Creator<ThreadEntity> CREATOR = new Creator<ThreadEntity>() {
        @Override
        public ThreadEntity createFromParcel(Parcel in) {
            return new ThreadEntity(in);
        }

        @Override
        public ThreadEntity[] newArray(int size) {
            return new ThreadEntity[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(short_id);
        dest.writeString(group_no);
        dest.writeString(name);
        dest.writeString(creator_uid);
        dest.writeString(creator_name);
        dest.writeInt(status);
        dest.writeInt(member_count);
        dest.writeInt(message_count);
        dest.writeString(source_message_id);
        dest.writeString(channel_id);
        dest.writeInt(channel_type);
        dest.writeString(last_message_content);
        dest.writeLong(last_message_time);
        dest.writeInt(unread_count);
        dest.writeInt(is_joined);
        dest.writeString(created_at);
        dest.writeString(updated_at);
    }
}
