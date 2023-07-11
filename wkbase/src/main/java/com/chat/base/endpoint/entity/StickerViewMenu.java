package com.chat.base.endpoint.entity;

import androidx.annotation.NonNull;

import com.chat.base.msg.IConversationContext;

/**
 * 12/31/20 10:06 AM
 * 表情view
 */
public class StickerViewMenu {
    public IConversationContext conversationContext;
    public IStickerStatusListener iStickerStatusListener;

    public StickerViewMenu(@NonNull IConversationContext conversationContext, IStickerStatusListener iStickerStatusListener) {
        this.conversationContext = conversationContext;
        this.iStickerStatusListener = iStickerStatusListener;
    }

    public interface IStickerStatusListener {
        void onSearchViewShow(boolean isShow);
    }
}
