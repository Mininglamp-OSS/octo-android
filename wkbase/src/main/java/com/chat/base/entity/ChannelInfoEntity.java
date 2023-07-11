package com.chat.base.entity;

import java.util.Map;

public class ChannelInfoEntity {
    public ChannelIDEntity channel;
    public ParentChannelEntity parent_channel;
    public String name;
    public String logo;
    public String remark;
    public int status;
    public int online;
    public long last_offline;
    public int receipt;
    public int robot;
    public String category;
    public int stick;
    public int mute;
    public int show_nick;
    public int follow;
    public int be_deleted;
    public int be_blacklist;
    public String notice;
    public int group_type;
    public int save;
    public int forbidden;
    public int invite;
    public int flame;
    public int flame_second;
    public int device_flag;
    public Map extra;


    public static class ChannelIDEntity {
        public String channel_id;
        public byte channel_type;
    }

    public static class ParentChannelEntity {
        public String channel_id;
        public byte channel_type;
    }

}
