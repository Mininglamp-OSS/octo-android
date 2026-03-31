package com.xinbida.wukongim.interfaces;

/**
 * 2020-10-10 15:16
 * 同步频道消息
 */
public interface ISyncChannelMsgListener {
    void syncChannelMsgs(String channelID, byte channelType, long startMessageSeq, long endMessageSeq, int limit, int pullMode, ISyncChannelMsgBack iSyncChannelMsgBack);
}
