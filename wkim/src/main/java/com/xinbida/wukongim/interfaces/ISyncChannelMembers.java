package com.xinbida.wukongim.interfaces;

/**
 * 2020-02-21 10:52
 * 同步频道成员的监听
 */
public interface ISyncChannelMembers {
    void onSyncChannelMembers(String channelID, byte channelType);
}
