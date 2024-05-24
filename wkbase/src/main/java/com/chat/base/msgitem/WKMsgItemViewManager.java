package com.chat.base.msgitem;

import com.chad.library.adapter.base.provider.BaseItemProvider;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 2020-08-05 17:41
 * 各类消息itemView管理
 */
public class WKMsgItemViewManager {

    private WKMsgItemViewManager() {
    }

    private static class MsgItemViewManagerBinder {
        final static WKMsgItemViewManager itemView = new WKMsgItemViewManager();
    }

    public static WKMsgItemViewManager getInstance() {
        return MsgItemViewManagerBinder.itemView;
    }

    private ConcurrentHashMap<Integer, BaseItemProvider<WKUIChatMsgItemEntity>> chatItemProviderList;
    private ConcurrentHashMap<Integer, BaseItemProvider<WKUIChatMsgItemEntity>> pinnedChatItemProviderList;


    public void addChatItemViewProvider(int type, BaseItemProvider<WKUIChatMsgItemEntity> itemProvider) {
        if (chatItemProviderList == null) {
            chatItemProviderList = new ConcurrentHashMap<>();
            chatItemProviderList.put(WKContentType.WK_SIGNAL_DECRYPT_ERROR, new WKSignalDecryptErrorProvider());
            chatItemProviderList.put(WKContentType.WK_CONTENT_FORMAT_ERROR, new WKChatFormatErrorProvider());
            chatItemProviderList.put(WKContentType.unknown_msg, new WKUnknownProvider());
            chatItemProviderList.put(WKContentType.typing, new WKTypingProvider());
            chatItemProviderList.put(WKContentType.revoke, new WKRevokeProvider());
            chatItemProviderList.put(WKContentType.systemMsg, new WKSystemProvider(WKContentType.systemMsg));
            chatItemProviderList.put(WKContentType.msgPromptTime, new WKSystemProvider(WKContentType.msgPromptTime));
            for (int i = 1000; i <= 2000; i++) {
                chatItemProviderList.put(i, new WKSystemProvider(i));
            }
        }
        chatItemProviderList.put(type, itemProvider);
        // 置顶消息的itemProvider
        if (pinnedChatItemProviderList == null) {
            pinnedChatItemProviderList = new ConcurrentHashMap<>();
            pinnedChatItemProviderList.put(WKContentType.WK_SIGNAL_DECRYPT_ERROR, new WKSignalDecryptErrorProvider());
            pinnedChatItemProviderList.put(WKContentType.WK_CONTENT_FORMAT_ERROR, new WKChatFormatErrorProvider());
            pinnedChatItemProviderList.put(WKContentType.unknown_msg, new WKUnknownProvider());
            pinnedChatItemProviderList.put(WKContentType.typing, new WKTypingProvider());
            pinnedChatItemProviderList.put(WKContentType.revoke, new WKRevokeProvider());
            pinnedChatItemProviderList.put(WKContentType.systemMsg, new WKSystemProvider(WKContentType.systemMsg));
            pinnedChatItemProviderList.put(WKContentType.msgPromptTime, new WKSystemProvider(WKContentType.msgPromptTime));
            for (int i = 1000; i <= 2000; i++) {
                pinnedChatItemProviderList.put(i, new WKSystemProvider(i));
            }
        }
        try {
            Object myObject = itemProvider.getClass().newInstance();
            pinnedChatItemProviderList.put(type, (BaseItemProvider<WKUIChatMsgItemEntity>) myObject);
        } catch (IllegalAccessException | InstantiationException e) {
            throw new RuntimeException(e);
        }

    }

    public ConcurrentHashMap<Integer, BaseItemProvider<WKUIChatMsgItemEntity>> getChatItemProviderList() {
        return chatItemProviderList;
    }

    public ConcurrentHashMap<Integer, BaseItemProvider<WKUIChatMsgItemEntity>> getPinnedChatItemProviderList() {
        return pinnedChatItemProviderList;
    }

    public BaseItemProvider<WKUIChatMsgItemEntity> getItemProvider(Integer type) {
        if (chatItemProviderList != null) {
            return chatItemProviderList.get(type);
        }
        return null;
    }

    public BaseItemProvider<WKUIChatMsgItemEntity> getPinnedItemProvider(Integer type) {
        if (pinnedChatItemProviderList != null) {
            return pinnedChatItemProviderList.get(type);
        }
        return null;
    }
}
