package com.xinbida.wukongim.interfaces;


import com.xinbida.wukongim.entity.WKSyncChat;

/**
 * 2020-10-09 14:43
 * 同步消息返回
 */
public interface ISyncConversationChatBack {
    void onBack(WKSyncChat syncChat);
}
