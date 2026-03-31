package com.xinbida.wukongim.interfaces;


import com.xinbida.wukongim.entity.WKChannel;

/**
 * 2020-02-01 14:38
 * 刷新频道
 */
public interface IRefreshChannel {
    void onRefreshChannel(WKChannel channel, boolean isEnd);
}
