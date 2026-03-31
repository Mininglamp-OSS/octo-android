package com.xinbida.wukongim.entity;

import java.util.Map;

/**
 * 2020-01-24 13:34
 * 提醒信息
 */
public class WKReminder {
    public long reminderID;
    public String messageID;
    public String channelID;
    public byte channelType;
    public long messageSeq;
    public int type;
    public int isLocate;
    public String uid;
    public String text;
    public Map data;
    public long version;
    public int done;
    public int needUpload;
    public String publisher;
}
