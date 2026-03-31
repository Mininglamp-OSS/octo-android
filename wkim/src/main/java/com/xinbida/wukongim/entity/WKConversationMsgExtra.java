package com.xinbida.wukongim.entity;

import androidx.annotation.NonNull;

public class WKConversationMsgExtra {
    public String channelID;
    public byte channelType;
    public long browseTo;
    public long keepMessageSeq;
    public int keepOffsetY;
    public String draft;
    public long version;
    public long draftUpdatedAt;

    @NonNull
    @Override
    public String toString() {
        return "WKConversationMsgExtra{" +
                "channelID='" + channelID + '\'' +
                ", channelType=" + channelType +
                ", browseTo=" + browseTo +
                ", keepMessageSeq=" + keepMessageSeq +
                ", keepOffsetY=" + keepOffsetY +
                ", draft='" + draft + '\'' +
                ", version=" + version +
                ", draftUpdatedAt=" + draftUpdatedAt +
                '}';
    }
}
