package com.xinbida.wukongim.manager;

import android.content.ContentValues;
import android.text.TextUtils;
import android.util.Log;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.db.ConversationDbManager;
import com.xinbida.wukongim.db.MsgDbManager;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKConversationMsg;
import com.xinbida.wukongim.entity.WKConversationMsgExtra;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKMsgExtra;
import com.xinbida.wukongim.entity.WKMsgReaction;
import com.xinbida.wukongim.entity.WKSyncChat;
import com.xinbida.wukongim.entity.WKSyncConvMsgExtra;
import com.xinbida.wukongim.entity.WKSyncRecent;
import com.xinbida.wukongim.entity.WKUIConversationMsg;
import com.xinbida.wukongim.interfaces.IAllConversations;
import com.xinbida.wukongim.interfaces.IDeleteConversationMsg;
import com.xinbida.wukongim.interfaces.IRefreshConversationMsg;
import com.xinbida.wukongim.interfaces.IRefreshConversationMsgList;
import com.xinbida.wukongim.interfaces.ISyncConversationChat;
import com.xinbida.wukongim.interfaces.ISyncConversationChatBack;
import com.xinbida.wukongim.message.type.WKConnectStatus;
import com.xinbida.wukongim.message.type.WKMsgContentType;
import com.xinbida.wukongim.utils.DispatchQueuePool;
import com.xinbida.wukongim.utils.WKCommonUtils;
import com.xinbida.wukongim.utils.WKLoggerUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 5/21/21 12:12 PM
 * 最近会话管理
 */
public class ConversationManager extends BaseManager {
    private final DispatchQueuePool dispatchQueuePool = new DispatchQueuePool(3);

    private final String TAG = "ConversationManager";

    private ConversationManager() {
    }

    private static class ConversationManagerBinder {
        static final ConversationManager manager = new ConversationManager();
    }

    public static ConversationManager getInstance() {
        return ConversationManagerBinder.manager;
    }

    //监听刷新最近会话
    private ConcurrentHashMap<String, IRefreshConversationMsg> refreshMsgMap;
    private ConcurrentHashMap<String, IRefreshConversationMsgList> refreshMsgListMap;

    //移除某个会话
    private ConcurrentHashMap<String, IDeleteConversationMsg> iDeleteMsgList;
    // 同步最近会话
    private ISyncConversationChat iSyncConversationChat;

    /**
     * 查询会话记录消息
     *
     * @return 最近会话集合
     */
    public List<WKUIConversationMsg> getAll() {
        return ConversationDbManager.getInstance().queryAll();
    }

    public void getAll(IAllConversations iAllConversations) {
        if (iAllConversations == null) {
            return;
        }
        dispatchQueuePool.execute(() -> {
            List<WKUIConversationMsg> list = ConversationDbManager.getInstance().queryAll();
            iAllConversations.onResult(list);
        });
    }

    public List<WKConversationMsg> getWithChannelType(byte channelType) {
        return ConversationDbManager.getInstance().queryWithChannelType(channelType);
    }

    public List<WKUIConversationMsg> getWithChannelIds(List<String> channelIds) {
        return ConversationDbManager.getInstance().queryWithChannelIds(channelIds);
    }

    /**
     * 查询某条消息
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @return WKConversationMsg
     */
    public WKConversationMsg getWithChannel(String channelID, byte channelType) {
        return ConversationDbManager.getInstance().queryWithChannel(channelID, channelType);
    }

    public void updateWithMsg(WKConversationMsg mConversationMsg) {
        WKMsg msg = MsgDbManager.getInstance().queryMaxOrderSeqMsgWithChannel(mConversationMsg.channelID, mConversationMsg.channelType);
        if (msg != null) {
            mConversationMsg.lastClientMsgNO = msg.clientMsgNO;
            mConversationMsg.lastMsgSeq = msg.messageSeq;
        }
        ConversationDbManager.getInstance().updateMsg(mConversationMsg.channelID, mConversationMsg.channelType, mConversationMsg.lastClientMsgNO, mConversationMsg.lastMsgSeq, mConversationMsg.unreadCount);
    }

    /**
     * 删除某个会话记录信息
     *
     * @param channelId   频道ID
     * @param channelType 频道类型
     */
    public boolean deleteWitchChannel(String channelId, byte channelType) {
        return ConversationDbManager.getInstance().deleteWithChannel(channelId, channelType, 1);
    }

    /**
     * 清除所有最近会话
     */
    public boolean clearAll() {
        return ConversationDbManager.getInstance().clearEmpty();
    }

    public void addOnRefreshMsgListListener(String key, IRefreshConversationMsgList listener) {
        if (TextUtils.isEmpty(key) || listener == null) return;
        if (refreshMsgListMap == null) {
            refreshMsgListMap = new ConcurrentHashMap<>();
        }
        refreshMsgListMap.put(key, listener);
    }

    public void removeOnRefreshMsgListListener(String key) {
        if (TextUtils.isEmpty(key) || refreshMsgListMap == null) return;
        refreshMsgListMap.remove(key);
    }

    /**
     * 监听刷新最近会话
     *
     * @param listener 回调
     */
    public void addOnRefreshMsgListener(String key, IRefreshConversationMsg listener) {
        if (TextUtils.isEmpty(key) || listener == null) return;
        if (refreshMsgMap == null)
            refreshMsgMap = new ConcurrentHashMap<>();
        refreshMsgMap.put(key, listener);
    }

    public void removeOnRefreshMsgListener(String key) {
        if (TextUtils.isEmpty(key) || refreshMsgMap == null) return;
        refreshMsgMap.remove(key);
    }

    /**
     * 设置刷新最近会话
     */
//    public void setOnRefreshMsg(WKUIConversationMsg conversationMsg, boolean isEnd, String from) {
//        if (refreshMsgMap != null && !refreshMsgMap.isEmpty() && conversationMsg != null) {
//            runOnMainThread(() -> {
//                for (Map.Entry<String, IRefreshConversationMsg> entry : refreshMsgMap.entrySet()) {
//                    entry.getValue().onRefreshConversationMsg(conversationMsg, isEnd);
//                }
//            });
//        }
//    }
    public void setOnRefreshMsg(WKUIConversationMsg msg, String from) {
        List<WKUIConversationMsg> list = new ArrayList<>();
        list.add(msg);
        this.setOnRefreshMsg(list, from);
    }

    public void setOnRefreshMsg(List<WKUIConversationMsg> list, String from) {
        if (WKCommonUtils.isEmpty(list)) return;
        if (refreshMsgMap != null && !refreshMsgMap.isEmpty()) {
            runOnMainThread(() -> {
                for (int i = 0, size = list.size(); i < size; i++) {
                    for (Map.Entry<String, IRefreshConversationMsg> entry : refreshMsgMap.entrySet()) {
                        entry.getValue().onRefreshConversationMsg(list.get(i), i == list.size() - 1);
                    }
                }
            });
        }
        if (refreshMsgListMap != null && !refreshMsgListMap.isEmpty()) {
            runOnMainThread(() -> {
                for (Map.Entry<String, IRefreshConversationMsgList> entry : refreshMsgListMap.entrySet()) {
                    entry.getValue().onRefresh(list);
                }
            });
        }
    }

    //监听删除最近会话监听
    public void addOnDeleteMsgListener(String key, IDeleteConversationMsg listener) {
        if (TextUtils.isEmpty(key) || listener == null) return;
        if (iDeleteMsgList == null) iDeleteMsgList = new ConcurrentHashMap<>();
        iDeleteMsgList.put(key, listener);
    }

    public void removeOnDeleteMsgListener(String key) {
        if (TextUtils.isEmpty(key) || iDeleteMsgList == null) return;
        iDeleteMsgList.remove(key);
    }

    // 删除某个最近会话
    public void setDeleteMsg(String channelID, byte channelType) {
        if (iDeleteMsgList != null && !iDeleteMsgList.isEmpty()) {
            runOnMainThread(() -> {
                for (Map.Entry<String, IDeleteConversationMsg> entry : iDeleteMsgList.entrySet()) {
                    entry.getValue().onDelete(channelID, channelType);
                }
            });
        }
    }

    public void updateRedDot(String channelID, byte channelType, int redDot) {
        boolean result = ConversationDbManager.getInstance().updateRedDot(channelID, channelType, redDot);
        if (result) {
            WKUIConversationMsg msg = getUIConversationMsg(channelID, channelType);
            setOnRefreshMsg(msg, "updateRedDot");
        }
    }

    public WKConversationMsgExtra getMsgExtraWithChannel(String channelID, byte channelType) {
        return ConversationDbManager.getInstance().queryMsgExtraWithChannel(channelID, channelType);
    }

    public void updateMsgExtra(WKConversationMsgExtra extra) {
        boolean result = ConversationDbManager.getInstance().insertOrUpdateMsgExtra(extra);
        if (result) {
            WKUIConversationMsg msg = getUIConversationMsg(extra.channelID, extra.channelType);
            List<WKUIConversationMsg> list = new ArrayList<>();
            list.add(msg);
            setOnRefreshMsg(list, "updateMsgExtra");
        }
    }

    public WKUIConversationMsg updateWithWKMsg(WKMsg msg) {
        if (msg == null || TextUtils.isEmpty(msg.channelID)) return null;
        return ConversationDbManager.getInstance().insertOrUpdateWithMsg(msg, 0);
    }

    public WKUIConversationMsg getUIConversationMsg(String channelID, byte channelType) {
        WKConversationMsg msg = ConversationDbManager.getInstance().queryWithChannel(channelID, channelType);
        if (msg == null) {
            return null;
        }
        return ConversationDbManager.getInstance().getUIMsg(msg);
    }

    public long getMsgExtraMaxVersion() {
        return ConversationDbManager.getInstance().queryMsgExtraMaxVersion();
    }

    public void saveSyncMsgExtras(List<WKSyncConvMsgExtra> list) {
        List<WKConversationMsgExtra> msgExtraList = new ArrayList<>();
        for (WKSyncConvMsgExtra msg : list) {
            msgExtraList.add(syncConvMsgExtraToConvMsgExtra(msg));
        }
        ConversationDbManager.getInstance().insertMsgExtras(msgExtraList);
    }

    private WKConversationMsgExtra syncConvMsgExtraToConvMsgExtra(WKSyncConvMsgExtra extra) {
        WKConversationMsgExtra msg = new WKConversationMsgExtra();
        msg.channelID = extra.channel_id;
        msg.channelType = extra.channel_type;
        msg.draft = extra.draft;
        msg.keepOffsetY = extra.keep_offset_y;
        msg.keepMessageSeq = extra.keep_message_seq;
        msg.version = extra.version;
        msg.browseTo = extra.browse_to;
        msg.draftUpdatedAt = extra.draft_updated_at;
        return msg;
    }


    public void addOnSyncConversationListener(ISyncConversationChat iSyncConvChatListener) {
        this.iSyncConversationChat = iSyncConvChatListener;
    }

    public void setSyncConversationListener(ISyncConversationChatBack iSyncConversationChatBack) {
        if (iSyncConversationChat != null) {
            long version = ConversationDbManager.getInstance().queryMaxVersion();
            String lastMsgSeqStr = ConversationDbManager.getInstance().queryLastMsgSeqs();
            runOnMainThread(() -> iSyncConversationChat.syncConversationChat(lastMsgSeqStr, 10, version, syncChat -> {
                dispatchQueuePool.execute(() -> saveSyncChat(syncChat, () -> iSyncConversationChatBack.onBack(syncChat)));
            }));
        } else {
            WKLoggerUtils.getInstance().e("未设置同步最近会话事件");
        }
    }


    interface ISaveSyncChatBack {
        void onBack();
    }


    private void saveSyncChat(WKSyncChat syncChat, final ISaveSyncChatBack iSaveSyncChatBack) {
        if (syncChat == null) {
            iSaveSyncChatBack.onBack();
            return;
        }
        List<WKConversationMsg> conversationMsgList = new ArrayList<>();
        List<WKMsg> msgList = new ArrayList<>();
        List<WKMsgReaction> msgReactionList = new ArrayList<>();
        List<WKMsgExtra> msgExtraList = new ArrayList<>();
        if (WKCommonUtils.isNotEmpty(syncChat.conversations)) {
            for (int i = 0, size = syncChat.conversations.size(); i < size; i++) {
                //最近会话消息对象
                WKConversationMsg conversationMsg = new WKConversationMsg();
                byte channelType = syncChat.conversations.get(i).channel_type;
                String channelID = syncChat.conversations.get(i).channel_id;
                if (channelType == WKChannelType.COMMUNITY_TOPIC) {
                    String[] str = channelID.split("@");
                    conversationMsg.parentChannelID = str[0];
                    conversationMsg.parentChannelType = WKChannelType.COMMUNITY;
                }
                conversationMsg.channelID = syncChat.conversations.get(i).channel_id;
                conversationMsg.channelType = syncChat.conversations.get(i).channel_type;
                conversationMsg.lastMsgSeq = syncChat.conversations.get(i).last_msg_seq;
                conversationMsg.lastClientMsgNO = syncChat.conversations.get(i).last_client_msg_no;
                conversationMsg.lastMsgTimestamp = syncChat.conversations.get(i).timestamp;
                conversationMsg.unreadCount = syncChat.conversations.get(i).unread;
                conversationMsg.version = syncChat.conversations.get(i).version;
                //聊天消息对象
                if (syncChat.conversations.get(i).recents != null && WKCommonUtils.isNotEmpty(syncChat.conversations)) {
                    for (WKSyncRecent wkSyncRecent : syncChat.conversations.get(i).recents) {
                        WKMsg msg = MsgManager.getInstance().WKSyncRecent2WKMsg(wkSyncRecent);
                        if (msg.type == WKMsgContentType.WK_INSIDE_MSG) {
                            continue;
                        }
                        if (WKCommonUtils.isNotEmpty(msg.reactionList)) {
                            msgReactionList.addAll(msg.reactionList);
                        }
                        //判断会话列表的fromUID
                        if (conversationMsg.lastClientMsgNO.equals(msg.clientMsgNO)) {
                            conversationMsg.isDeleted = msg.isDeleted;
                        }
                        if (wkSyncRecent.message_extra != null) {
                            WKMsgExtra extra = MsgManager.getInstance().WKSyncExtraMsg2WKMsgExtra(msg.channelID, msg.channelType, wkSyncRecent.message_extra);
                            msgExtraList.add(extra);
                        }
                        msgList.add(msg);
                    }
                }

                conversationMsgList.add(conversationMsg);
            }
        }
        if (WKCommonUtils.isNotEmpty(msgExtraList)) {
            MsgDbManager.getInstance().insertOrReplaceExtra(msgExtraList);
        }
        List<WKUIConversationMsg> uiMsgList = new ArrayList<>();
        if (WKCommonUtils.isNotEmpty(conversationMsgList)) {
            if (WKCommonUtils.isNotEmpty(msgList)) {
                MsgDbManager.getInstance().insertMsgs(msgList);
            }
            try {
                if (WKCommonUtils.isNotEmpty(conversationMsgList)) {
                    List<ContentValues> cvList = new ArrayList<>();
                    for (int i = 0, size = conversationMsgList.size(); i < size; i++) {
                        ContentValues cv = ConversationDbManager.getInstance().getInsertSyncCV(conversationMsgList.get(i));
                        cvList.add(cv);
                        WKUIConversationMsg uiMsg = ConversationDbManager.getInstance().getUIMsg(conversationMsgList.get(i));
                        if (uiMsg != null) {
                            uiMsgList.add(uiMsg);
                        }
                    }
                    WKIMApplication.getInstance().getDbHelper().getDb()
                            .beginTransaction();
                    for (ContentValues cv : cvList) {
                        ConversationDbManager.getInstance().insertSyncMsg(cv);
                    }
                    WKIMApplication.getInstance().getDbHelper().getDb()
                            .setTransactionSuccessful();
                }
            } catch (Exception ignored) {
                WKLoggerUtils.getInstance().e(TAG, "Save synchronization session message exception");
            } finally {
                if (WKIMApplication.getInstance().getDbHelper().getDb().inTransaction()) {
                    WKIMApplication.getInstance().getDbHelper().getDb()
                            .endTransaction();
                }
            }
            if (WKCommonUtils.isNotEmpty(msgReactionList)) {
                MsgManager.getInstance().saveMsgReactions(msgReactionList);
            }
            // fixme 离线消息应该不能push给UI
            if (WKCommonUtils.isNotEmpty(msgList)) {
                HashMap<String, List<WKMsg>> allMsgMap = new HashMap<>();
                for (WKMsg wkMsg : msgList) {
                    if (TextUtils.isEmpty(wkMsg.channelID)) continue;
                    List<WKMsg> list;
                    if (allMsgMap.containsKey(wkMsg.channelID)) {
                        list = allMsgMap.get(wkMsg.channelID);
                        if (list == null) {
                            list = new ArrayList<>();
                        }
                    } else {
                        list = new ArrayList<>();
                    }
                    list.add(wkMsg);
                    allMsgMap.put(wkMsg.channelID, list);
                }

//                for (Map.Entry<String, List<WKMsg>> entry : allMsgMap.entrySet()) {
//                    List<WKMsg> channelMsgList = entry.getValue();
//                    if (channelMsgList != null && channelMsgList.size() < 20) {
//                        Collections.sort(channelMsgList, new Comparator<WKMsg>() {
//                            @Override
//                            public int compare(WKMsg o1, WKMsg o2) {
//                                return Long.compare(o1.messageSeq, o2.messageSeq);
//                            }
//                        });
//                        MsgManager.getInstance().pushNewMsg(channelMsgList);
//                    }
//                }


            }
            if (WKCommonUtils.isNotEmpty(uiMsgList)) {
                setOnRefreshMsg(uiMsgList, "saveSyncChat");
//                for (int i = 0, size = uiMsgList.size(); i < size; i++) {
//                    WKIM.getInstance().getConversationManager().setOnRefreshMsg(uiMsgList.get(i), i == uiMsgList.size() - 1, "saveSyncChat");
//                }
            }
        }

        if (WKCommonUtils.isNotEmpty(syncChat.cmds)) {
            try {
                for (int i = 0, size = syncChat.cmds.size(); i < size; i++) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("cmd", syncChat.cmds.get(i).cmd);
                    JSONObject json = new JSONObject(syncChat.cmds.get(i).param);
                    jsonObject.put("param", json);
                    CMDManager.getInstance().handleCMD(jsonObject);
                }
            } catch (JSONException e) {
                WKLoggerUtils.getInstance().e(TAG, "saveSyncChat cmd not json struct");
            }
        }
        WKIM.getInstance().getConnectionManager().setConnectionStatus(WKConnectStatus.syncCompleted, "");
        iSaveSyncChatBack.onBack();
    }
}
