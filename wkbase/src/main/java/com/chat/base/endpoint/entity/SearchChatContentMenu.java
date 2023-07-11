package com.chat.base.endpoint.entity;

/**
 * 3/22/21 5:01 PM
 * 搜索聊天内容
 */
public class SearchChatContentMenu {

    public IClick iClick;
    public String text;

    public SearchChatContentMenu(String text, IClick iClick) {
        this.text = text;
        this.iClick = iClick;
    }

    public interface IClick {
        void onClick(String channelID, byte channelType);
    }
}
