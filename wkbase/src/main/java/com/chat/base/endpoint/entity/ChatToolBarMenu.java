package com.chat.base.endpoint.entity;

import android.view.View;

import com.chat.base.msg.IConversationContext;

/**
 * 1/1/21 2:43 PM
 * 聊天工具栏
 */
public class ChatToolBarMenu {
    public String sid;
    public int toolBarImageRecourseID;
    public int toolBarImageSelectedRecourseID;
    public boolean isSelected;
    public View bottomView;
    public boolean isDisable;

    public IChatToolBarListener iChatToolBarListener;

    public ChatToolBarMenu(String sid, int toolBarImageRecourseID, int toolBarImageSelectedRecourseID, View bottomView, IChatToolBarListener iChatToolBarListener) {
        this.sid = sid;
        this.toolBarImageRecourseID = toolBarImageRecourseID;
        this.toolBarImageSelectedRecourseID = toolBarImageSelectedRecourseID;
        this.bottomView = bottomView;
        this.iChatToolBarListener = iChatToolBarListener;
    }

    public interface IChatToolBarListener {
        void onChecked(boolean isSelected, IConversationContext iConversationContext);
    }

}
