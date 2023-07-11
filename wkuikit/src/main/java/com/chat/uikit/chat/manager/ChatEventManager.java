package com.chat.uikit.chat.manager;

/**
 * 2020-01-05 11:35
 * 聊天事件处理
 */
public class ChatEventManager {
    private ChatEventManager() {
    }

    private static class ChatEventManagerBinder {
        private final static ChatEventManager instance = new ChatEventManager();
    }

    public static ChatEventManager getInstance() {
        return ChatEventManagerBinder.instance;
    }

    private boolean isShowImg = false;

    public void setShowImg(boolean isShowImg) {
        this.isShowImg = isShowImg;
    }

    public boolean getShowImg() {
        return isShowImg;
    }

}
