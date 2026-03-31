package com.xinbida.wukongim.interfaces;

import com.xinbida.wukongim.entity.WKUIConversationMsg;

import java.util.List;

public interface IRefreshConversationMsgList {
    void onRefresh(List<WKUIConversationMsg> list);
}
