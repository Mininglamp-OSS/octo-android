package com.xinbida.wukongim.interfaces;

/**
 * 2020-10-09 14:41
 * 同步最近会话
 */
public interface ISyncConversationChat {
    /**
     * 同步会话
     *
     * @param last_msg_seqs     最近会话列表msg_seq集合
     * @param msg_count         会话里面消息同步数量
     * @param version           最大版本号
     * @param iSyncConvChatBack 回调
     */
    void syncConversationChat(String last_msg_seqs, int msg_count, long version, ISyncConversationChatBack iSyncConvChatBack);
}
