package com.xinbida.wukongim.interfaces;

/**
 * 4/16/21 3:00 PM
 * 同步消息回应
 */
public interface ISyncMsgReaction {
    void onSyncMsgReaction(String channelID, byte channelType, long maxSeq);
}
