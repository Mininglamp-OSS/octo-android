package com.xinbida.wukongim.interfaces;


import com.xinbida.wukongim.entity.WKChannelMember;

/**
 * 2019-12-01 15:54
 * 频道成员
 */
public interface IChannelMemberInfoListener {
    void onResult(WKChannelMember channelMember);
}
