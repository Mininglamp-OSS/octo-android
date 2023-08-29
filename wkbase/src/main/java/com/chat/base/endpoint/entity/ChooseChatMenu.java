package com.chat.base.endpoint.entity;



import com.xinbida.wukongim.msgmodel.WKMessageContent;

import java.util.ArrayList;
import java.util.List;

/**
 * 2020-09-25 18:34
 * 选择会话
 */
public class ChooseChatMenu {
    public ChatChooseContacts mChatChooseContacts;
    public List<WKMessageContent> list;

    public ChooseChatMenu(ChatChooseContacts mChatChooseContacts, WKMessageContent messageContent) {
        this.mChatChooseContacts = mChatChooseContacts;
        list = new ArrayList<>();
        list.add(messageContent);
    }

    public ChooseChatMenu(ChatChooseContacts mChatChooseContacts, List<WKMessageContent> list) {
        this.mChatChooseContacts = mChatChooseContacts;
        this.list = list;
    }
}
