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

//            WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.setNewGroupAdmin, new WKSystemProvider(WKContentType.setNewGroupAdmin));
//            WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.createGroupSysMsg, new WKSystemProvider(WKContentType.createGroupSysMsg));
//            WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.addGroupMembersMsg, new WKSystemProvider(WKContentType.addGroupMembersMsg));
//            WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.removeGroupMembersMsg, new WKSystemProvider(WKContentType.removeGroupMembersMsg));
//            WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.groupSystemInfo, new WKSystemProvider(WKContentType.groupSystemInfo));
//            WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.withdrawSystemInfo, new WKSystemProvider(WKContentType.withdrawSystemInfo));
//            WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.approveGroupMember, new WKSystemProvider(WKContentType.approveGroupMember));


        }
        chatItemProviderList.put(type, itemProvider);
    }

    public ConcurrentHashMap<Integer, BaseItemProvider<WKUIChatMsgItemEntity>> getChatItemProviderList() {
        return chatItemProviderList;
    }

    public BaseItemProvider<WKUIChatMsgItemEntity> getItemProvider(Integer type) {
        if (chatItemProviderList != null) {
            return chatItemProviderList.get(type);
        }
        return null;
    }
}
