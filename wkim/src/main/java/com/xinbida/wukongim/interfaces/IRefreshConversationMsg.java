package com.xinbida.wukongim.interfaces;


import com.xinbida.wukongim.entity.WKUIConversationMsg;

/**
 * 2020-02-21 11:11
 * 刷新最近会话
 */
public interface IRefreshConversationMsg {
    void onRefreshConversationMsg(WKUIConversationMsg wkuiConversationMsg, boolean isEnd);
}
