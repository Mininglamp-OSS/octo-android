package com.xinbida.wukongim.interfaces;


/**
 * 2019-12-01 15:52
 * 获取频道成员
 */
public interface IGetChannelMemberList {
    void request(String channelId, byte channelType, String searchKey, int page, int limit, IChannelMemberListResult listResult);
}
