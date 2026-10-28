package com.xinbida.wukongim.msgmodel;

/** Optional hook for attachments whose complete local source was explicitly trusted by the UI. */
public interface WKLocalAttachmentContent {
    interface PrepareCallback { void onPrepared(String stablePath, boolean success); }
    boolean hasTrustedLocalSource();
    long getTrustedLocalAttachmentSize();
    void prepareTrustedLocalAttachment(String uid, String channelId,
                                       byte channelType, String clientMsgNo, long expectedSize,
                                       PrepareCallback callback);
}
