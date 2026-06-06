package com.xinbida.wukongim.manager;

import android.content.ContentValues;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.db.WKDBHelper;
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
import com.xinbida.wukongim.entity.WKSpaceMembership;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 5/21/21 12:12 PM
 * 最近会话管理
 */
public class ConversationManager extends BaseManager {
    private final DispatchQueuePool dispatchQueuePool = new DispatchQueuePool(3);

    private final String TAG = "ConversationManager";

    /**
     *  C · SDK 层 sync 去重守卫。Phase 1 诊断识别出 5 条 sync 触发路径
     * （{@code performSpaceSwitch} / {@code getChatMsg} / {@code connectSuccessCompensate}
     * / {@code spaceResyncRunnable} / {@code WKConnection.wkConnectionSync}）。
     * 上层 {@code SpaceSyncCoordinator}（/321）已在 UI 层做了 debounce + 全局重入
     * 守卫，但 SDK 仍需要自己的 in-flight 防线：其它 SDK 使用方或未来新加的触发点不一定会
     * 走上层 coordinator，SDK 层 AtomicBoolean 保证任何路径都只能有一条 sync 进行中。
     *
     * <p>释放时机：sync 真正完成回调里（成功 / 失败 / 空 syncChat）统一 release。若上游
     * 网络回调永远不 fire，{@link #SYNC_STUCK_RESET_MS} 超时后允许下次 sync 强制抢占
     * permit，避免永久卡死。
     */
    private final AtomicBoolean syncInFlight = new AtomicBoolean(false);
    private volatile long syncInFlightSince = 0L;
    private static final long SYNC_STUCK_RESET_MS = 15_000L;

    /**
     * GH dmwork-android#251 Round-2：conversation sync 响应里 resolved 的 group
     * {@code space_id}（channelID → space_id）独立缓存。
     *
     * <p><b>为什么不写 {@link com.xinbida.wukongim.entity.WKChannel#remoteExtraMap}：</b>
     * {@code ChannelManager.updateChannel(WKChannel)} 在 channelInfo 异步水合时会整体
     * 替换 {@code wkChannelList[i].remoteExtraMap = channel.remoteExtraMap}（见
     * {@code ChannelManager.java#updateChannel}），channelInfo 自带的 remoteExtraMap
     * 通常只有 {@code space_id}（不含 {@code my_source_space_id}）→ conv sync 预填的
     * 两个键会被一次性抹掉，竞态又回来了。改为独立内存缓存后，channelInfo 水合不会
     * 触碰它，{@link com.chat.base.space.SpaceFilter} 在
     * {@code channel.remoteExtraMap} 没有权威值时仍能读到 conv sync 给的兜底。
     *
     * <p><b>优先级语义</b>：{@code SpaceFilter.DEFAULT_PROVIDER.getChannelSpaceId} /
     * {@code getMyMembershipSourceSpaceId} 先查权威源（{@code channel.remoteExtraMap}
     * / member DB），再 fallback 到这两张 map——所以一旦 channelInfo / member sync
     * 给出真实值，本缓存就自然被"压低优先级"，不会污染最终判定。
     *
     * <p>键不带 channelType（{@link #prefillSpaceExtrasFromConvSync} 只对
     * {@link com.xinbida.wukongim.entity.WKChannelType#GROUP} 写入，读侧也只在 GROUP
     * 路径上查；保持简洁）。
     */
    static final class SpaceCacheSnapshot {
        final ConcurrentHashMap<String, String> spaceMap;
        final ConcurrentHashMap<String, String> externalMap;
        final boolean authoritative;

        SpaceCacheSnapshot(ConcurrentHashMap<String, String> spaceMap,
                           ConcurrentHashMap<String, String> externalMap,
                           boolean authoritative) {
            this.spaceMap = spaceMap;
            this.externalMap = externalMap;
            this.authoritative = authoritative;
        }
    }

    private volatile SpaceCacheSnapshot spaceCacheSnapshot =
            new SpaceCacheSnapshot(new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), false);

    private static final String SPACE_PREFS_NAME = "wk_space_memberships";
    private static final String PREF_KEY_SPACE_MAP = "space_map";
    private static final String PREF_KEY_EXTERNAL_MAP = "external_map";
    private static final String PREF_KEY_UID = "cache_uid";
    private static final String PREF_KEY_AUTHORITATIVE = "authoritative";
    private volatile boolean spaceCacheLoadedFromDisk = false;
    private volatile boolean coldStartSyncDone = false;

    private ConversationManager() {
    }

    private void ensureSpaceCacheLoaded() {
        if (!spaceCacheLoadedFromDisk) {
            spaceCacheLoadedFromDisk = true;
            loadSpaceCacheFromDisk();
        }
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

    private ConcurrentHashMap<String, Runnable> spaceCacheUpdateListeners;

    public void addOnSpaceCacheUpdateListener(String key, Runnable listener) {
        if (TextUtils.isEmpty(key) || listener == null) return;
        if (spaceCacheUpdateListeners == null) {
            spaceCacheUpdateListeners = new ConcurrentHashMap<>();
        }
        spaceCacheUpdateListeners.put(key, listener);
    }

    public void removeOnSpaceCacheUpdateListener(String key) {
        if (TextUtils.isEmpty(key) || spaceCacheUpdateListeners == null) return;
        spaceCacheUpdateListeners.remove(key);
    }

    private void notifySpaceCacheUpdated() {
        if (spaceCacheUpdateListeners == null) return;
        for (Runnable listener : spaceCacheUpdateListeners.values()) {
            try {
                listener.run();
            } catch (Throwable ignored) {
            }
        }
    }

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

    public void updateLastMsgTimestamp(String channelId, byte channelType, long timestamp) {
        ConversationDbManager.getInstance().updateLastMsgTimestamp(channelId, channelType, timestamp);
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
        //  H3 · 在当前（后台）线程预加载 wkMsg + wkChannel，避免主线程回调中懒加载
        // 触发 DB 查询导致 ANR。adapter.convert 每 item 调 getWkChannel 36 次 / getWkMsg 15 次，
        // Space 切换冷启动时 ChannelManager 缓存为空 → 撞上 saveSyncChat 写事务 → 主线程
        // 卡死 30s → 系统杀进程（用户感知为闪退）。getWkChannel 单独预加载即可消除该窗口。
        for (int i = 0, size = list.size(); i < size; i++) {
            list.get(i).getWkMsg();
            list.get(i).getWkChannel();
        }
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
            if (msg != null) {
                setOnRefreshMsg(msg, "updateRedDot");
            }
        }
    }

    public WKConversationMsgExtra getMsgExtraWithChannel(String channelID, byte channelType) {
        return ConversationDbManager.getInstance().queryMsgExtraWithChannel(channelID, channelType);
    }

    /**
     * 批量查询会话 extras（草稿等），一次 DB 查询替代 N 次单条查询。
     * 返回 Map 以 channelID:channelType 为复合 key。
     */
    public java.util.Map<String, WKConversationMsgExtra> getMsgExtrasForChannels(java.util.List<String> channelIds) {
        java.util.Map<String, WKConversationMsgExtra> map = new java.util.HashMap<>();
        java.util.List<WKConversationMsgExtra> list = ConversationDbManager.getInstance().queryMsgExtrasForChannelIds(channelIds);
        for (WKConversationMsgExtra extra : list) {
            map.put(extra.channelID + ":" + extra.channelType, extra);
        }
        return map;
    }

    public void updateMsgExtra(WKConversationMsgExtra extra) {
        boolean result = ConversationDbManager.getInstance().insertOrUpdateMsgExtra(extra);
        if (result) {
            WKUIConversationMsg msg = getUIConversationMsg(extra.channelID, extra.channelType);
            if (msg == null) return;
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
        if (iSyncConversationChat == null) {
            WKLoggerUtils.getInstance().e("未设置同步最近会话事件");
            if (com.xinbida.wukongim.BuildConfig.DEBUG) android.util.Log.e("MsgDebug", "[SYNC_BUG] iSyncConversationChat is NULL! callback will NOT fire, connectStatus will stay syncMsg forever");
            return;
        }
        //  C · sync 去重：CAS 抢 permit。5 条触发路径并发打进来时只有 1 条会真正
        // 发起 syncConversationChat，其余立即短路——但仍调用 onBack(null)，让上层
        // SpaceSyncCoordinator / SyncGate 的 complete() 有机会触发，避免协调器状态卡住。
        final long now = SystemClock.elapsedRealtime();
        if (!syncInFlight.compareAndSet(false, true)) {
            long startedAt = syncInFlightSince;
            if (startedAt > 0 && now - startedAt > SYNC_STUCK_RESET_MS) {
                // 兜底：上一次 sync 回调从未触发（网络超时 / 崩溃路径），15s 后强制抢占。
                WKLoggerUtils.getInstance().e(TAG,
                        "setSyncConversationListener stuck > " + SYNC_STUCK_RESET_MS
                                + "ms, force reset inFlight");
                syncInFlight.set(false);
                if (!syncInFlight.compareAndSet(false, true)) {
                    // 被其它线程抢先了，按正常 drop 处理
                    WKLoggerUtils.getInstance().e(TAG,
                            "setSyncConversationListener drop (lost race after stuck-reset)");
                    if (iSyncConversationChatBack != null) iSyncConversationChatBack.onBack(null);
                    return;
                }
            } else {
                WKLoggerUtils.getInstance().e(TAG,
                        "setSyncConversationListener drop (sync already in-flight, age="
                                + (startedAt == 0 ? -1 : now - startedAt) + "ms)");
                if (iSyncConversationChatBack != null) iSyncConversationChatBack.onBack(null);
                return;
            }
        }
        syncInFlightSince = now;
        final long dbVersion = ConversationDbManager.getInstance().queryMaxVersion();
        final long version = coldStartSyncDone ? dbVersion : 0;
        final String lastMsgSeqStr = ConversationDbManager.getInstance().queryLastMsgSeqs();
        WKLoggerUtils.getInstance().e(TAG,
                "setSyncConversationListener begin (version=" + version
                        + ", lastMsgSeq=" + lastMsgSeqStr + ")");
        if (com.xinbida.wukongim.BuildConfig.DEBUG) {
            android.util.Log.d("ConvSync", "[ConvSync] sync request: version=" + version);
        }
        runOnMainThread(() -> iSyncConversationChat.syncConversationChat(lastMsgSeqStr, 10, version, syncChat -> {
            dispatchQueuePool.execute(() -> saveSyncChat(syncChat, () -> {
                try {
                    if (syncChat != null) {
                        coldStartSyncDone = true;
                    }
                    if (iSyncConversationChatBack != null) {
                        iSyncConversationChatBack.onBack(syncChat);
                    }
                } finally {
                    syncInFlightSince = 0L;
                    syncInFlight.set(false);
                }
            }));
        }));
    }


    interface ISaveSyncChatBack {
        void onBack();
    }


    private void saveSyncChat(WKSyncChat syncChat, final ISaveSyncChatBack iSaveSyncChatBack) {
        //  Phase 2 · T7 埋点：SDK 批量落盘段（conversations + recents + reactions + extras）。
        // 发生在 dispatchQueuePool（非主线程）。守 BuildConfig.DEBUG 确保 release 无开销。
        final long yuj312T7Start = com.xinbida.wukongim.BuildConfig.DEBUG
                ? android.os.SystemClock.elapsedRealtime() : 0L;
        final int yuj312T7ConvCount = (com.xinbida.wukongim.BuildConfig.DEBUG && syncChat != null
                && syncChat.conversations != null) ? syncChat.conversations.size() : 0;
        if (com.xinbida.wukongim.BuildConfig.DEBUG) {
            android.os.Trace.beginSection("YUJ312-saveSyncChat");
            android.util.Log.d("YUJ312", "saveSyncChat START convCount=" + yuj312T7ConvCount);
        }
        try {
            saveSyncChatImpl(syncChat, iSaveSyncChatBack);
        } finally {
            if (com.xinbida.wukongim.BuildConfig.DEBUG) {
                android.os.Trace.endSection();
                android.util.Log.d("YUJ312", "saveSyncChat END convCount=" + yuj312T7ConvCount
                        + " +" + (android.os.SystemClock.elapsedRealtime() - yuj312T7Start) + "ms");
            }
        }
    }

    private void saveSyncChatImpl(WKSyncChat syncChat, final ISaveSyncChatBack iSaveSyncChatBack) {
        if (syncChat == null) {
            iSaveSyncChatBack.onBack();
            return;
        }
        if (com.xinbida.wukongim.BuildConfig.DEBUG && syncChat.conversations != null) {
            StringBuilder sb = new StringBuilder("[ConvSync] saveSyncChat: ");
            for (int di = 0; di < syncChat.conversations.size(); di++) {
                if (di > 0) sb.append(", ");
                sb.append(syncChat.conversations.get(di).channel_id)
                  .append(":").append(syncChat.conversations.get(di).channel_type);
            }
            android.util.Log.d("ConvSync", sb.toString());
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
                // octo-server PR #154 / GH dmwork-android#251：conv sync 响应里 resolved 的
                // space_id / my_source_space_id 在 GROUP 类型上预填到独立缓存。
                // 有 space_memberships 全量数据时跳过逐条 prefill，由末尾统一覆盖。
                if (channelType == WKChannelType.GROUP && syncChat.space_memberships == null) {
                    String _spaceId = syncChat.conversations.get(i).space_id;
                    String _mySource = syncChat.conversations.get(i).my_source_space_id;
                    if (com.xinbida.wukongim.BuildConfig.DEBUG) {
                        WKLoggerUtils.getInstance().e(TAG, "convSync prefill: ch=" + channelID
                                + " space_id=" + _spaceId + " my_source=" + _mySource);
                    }
                    prefillSpaceExtrasFromConvSync(channelID, channelType, _spaceId, _mySource);
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
        // 在 UI 通知之前应用全量 space 缓存，确保主线程过滤时数据已就绪
        applySpaceMemberships(syncChat.space_memberships);
        List<WKUIConversationMsg> uiMsgList = new ArrayList<>();
        WKDBHelper txHelper = WKIMApplication.getInstance().getDbHelper();
        if (WKCommonUtils.isNotEmpty(conversationMsgList)) {
            if (WKCommonUtils.isNotEmpty(msgList)) {
                MsgDbManager.getInstance().insertMsgs(msgList);
            }
            try {
                if (WKCommonUtils.isNotEmpty(conversationMsgList) && txHelper != null && !txHelper.isClosed()) {
                    List<ContentValues> cvList = new ArrayList<>();
                    for (int i = 0, size = conversationMsgList.size(); i < size; i++) {
                        ContentValues cv = ConversationDbManager.getInstance().getInsertSyncCV(conversationMsgList.get(i));
                        cvList.add(cv);
                        WKUIConversationMsg uiMsg = ConversationDbManager.getInstance().getUIMsg(conversationMsgList.get(i));
                        if (uiMsg != null) {
                            uiMsgList.add(uiMsg);
                        }
                    }
                    txHelper.beginTransaction();
                    for (ContentValues cv : cvList) {
                        ConversationDbManager.getInstance().insertSyncMsg(cv);
                    }
                    txHelper.setTransactionSuccessful();
                }
            } catch (Exception ignored) {
                WKLoggerUtils.getInstance().e(TAG, "Save synchronization session message exception");
            } finally {
                if (txHelper != null) {
                    txHelper.endTransaction();
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

    /**
     * 老后端 fallback：把 conversation sync 响应里逐条的 space_id / my_source_space_id
     * 写入 {@link #spaceCacheSnapshot} 的当前 map。仅在 space_memberships 字段缺失时使用。
     *
     * <p>新后端返回 space_memberships 时由 {@link #applySpaceMemberships} 原子替换整个快照。
     */
    private void prefillSpaceExtrasFromConvSync(String channelID,
                                                byte channelType,
                                                String spaceId,
                                                String mySourceSpaceId) {
        // 注意：这里刻意不走 android.text.TextUtils#isEmpty —— 它在 host unit test
        // （returnDefaultValues=true）下被 stub 成始终返回 false，会让 stale-cache 路径
        // 失去测试覆盖。用纯 Java 判定保持语义在 stub 环境下也可断言。
        if (channelID == null || channelID.isEmpty()) return;
        boolean hasSpaceId = spaceId != null && !spaceId.isEmpty();
        boolean hasMySource = mySourceSpaceId != null && !mySourceSpaceId.isEmpty();

        try {
            SpaceCacheSnapshot snapshot = spaceCacheSnapshot;
            if (hasSpaceId) {
                snapshot.spaceMap.put(channelID, spaceId);
            } else {
                snapshot.spaceMap.remove(channelID);
            }
            if (hasMySource) {
                snapshot.externalMap.put(channelID, mySourceSpaceId);
            } else {
                snapshot.externalMap.remove(channelID);
            }
        } catch (Throwable t) {
            // 单条 conv 的预填失败不应阻断批量落盘；下一次 sync 或 channelInfo 异步路径会补齐。
            WKLoggerUtils.getInstance().e(TAG, "prefillSpaceExtrasFromConvSync failed: " + t.getMessage());
        }
    }

    /**
     * 用服务端返回的全量 space_memberships 覆盖内存缓存，彻底消除 Space 消息串问题。
     * memberships 为 null 时表示老后端未部署此字段，跳过不处理（保持向后兼容）。
     */
    public void applySpaceMemberships(List<WKSpaceMembership> memberships) {
        if (memberships == null) return;
        if (memberships.isEmpty()) {
            spaceCacheSnapshot = new SpaceCacheSnapshot(
                    new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), false);
            saveSpaceCacheToDisk(new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
            notifySpaceCacheUpdated();
            return;
        }
        try {
            ConcurrentHashMap<String, String> newSpaceMap = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, String> newExternalMap = new ConcurrentHashMap<>();
            for (WKSpaceMembership m : memberships) {
                if (m == null || m.channel_id == null || m.channel_id.isEmpty()) continue;
                if (m.space_id != null && !m.space_id.isEmpty()) {
                    newSpaceMap.put(m.channel_id, m.space_id);
                }
                if (m.my_source_space_id != null && !m.my_source_space_id.isEmpty()) {
                    newExternalMap.put(m.channel_id, m.my_source_space_id);
                }
            }
            spaceCacheSnapshot = new SpaceCacheSnapshot(newSpaceMap, newExternalMap, true);
            saveSpaceCacheToDisk(newSpaceMap, newExternalMap);
            if (com.xinbida.wukongim.BuildConfig.DEBUG) {
                String caller = Thread.currentThread().getStackTrace().length > 3
                        ? Thread.currentThread().getStackTrace()[3].toString() : "unknown";
                android.util.Log.d("ConvSync", "[SpaceMemberships] applied by " + caller
                        + ": " + memberships.size() + " entries, spaceMap=" + newSpaceMap.size()
                        + " externalMap=" + newExternalMap.size());
                for (WKSpaceMembership m : memberships) {
                    if (m != null && m.channel_id != null) {
                        android.util.Log.d("ConvSync", "[SpaceMemberships] channel_id=" + m.channel_id
                                + " space_id=" + m.space_id
                                + " my_source_space_id=" + m.my_source_space_id);
                    }
                }
            }
        } catch (Throwable t) {
            WKLoggerUtils.getInstance().e(TAG, "applySpaceMemberships failed: " + t.getMessage());
        }
        notifySpaceCacheUpdated();
    }

    /**
     * GH dmwork-android#251 Round-3：清空 conv sync 预填的 space 缓存。
     *
     * <p>这两张 map 是进程内单例缓存，跨用户必须显式清空，否则 logout 后再 init 另一个
     * 账号时，SpaceFilter 会读到上个用户的 {@code my_source_space_id} 做错判（典型表现：
     * 切号后某个群在新账号的 Space 视图里被错误地隐藏 / 显示）。
     *
     * <p>调用点：
     * <ul>
     *   <li>{@code ConnectionManager.logoutChat()} —— 与 {@code ChannelManager.clearARMCache()}
     *       同级，确保退出登录时进程内所有用户态缓存全清。</li>
     *   <li>{@code WKIM.init(context, uid, token)} —— 二次 init 通常意味着切号，
     *       容错性地再清一次，避免 logout 路径被绕过时（如崩溃恢复）的残留。</li>
     * </ul>
     */
    public void clearConvSyncSpaceCache() {
        try {
            spaceCacheSnapshot = new SpaceCacheSnapshot(new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), false);
            spaceCacheLoadedFromDisk = false;
            coldStartSyncDone = false;
        } catch (Throwable t) {
            WKLoggerUtils.getInstance().e(TAG, "clearConvSyncSpaceCache failed: " + t.getMessage());
        }
    }

    private void loadSpaceCacheFromDisk() {
        try {
            android.content.Context ctx = WKIMApplication.getInstance().getContext();
            if (ctx == null) return;
            android.content.SharedPreferences prefs = ctx.getSharedPreferences(SPACE_PREFS_NAME, android.content.Context.MODE_PRIVATE);
            String cachedUid = prefs.getString(PREF_KEY_UID, null);
            String currentUid = WKIMApplication.getInstance().getUid();
            if (cachedUid == null || !cachedUid.equals(currentUid)) {
                clearSpaceCacheFromDisk();
                return;
            }
            String spaceJson = prefs.getString(PREF_KEY_SPACE_MAP, null);
            String extJson = prefs.getString(PREF_KEY_EXTERNAL_MAP, null);
            if (spaceJson == null && extJson == null) return;
            ConcurrentHashMap<String, String> spaceMap = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, String> extMap = new ConcurrentHashMap<>();
            if (spaceJson != null) {
                org.json.JSONObject obj = new org.json.JSONObject(spaceJson);
                java.util.Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    spaceMap.put(key, obj.getString(key));
                }
            }
            if (extJson != null) {
                org.json.JSONObject obj = new org.json.JSONObject(extJson);
                java.util.Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    extMap.put(key, obj.getString(key));
                }
            }
            spaceCacheSnapshot = new SpaceCacheSnapshot(spaceMap, extMap,
                    prefs.getBoolean(PREF_KEY_AUTHORITATIVE, false));
            if (com.xinbida.wukongim.BuildConfig.DEBUG) {
                android.util.Log.d("ConvSync", "[loadSpaceCacheFromDisk] spaceMap=" + spaceMap.size()
                        + " externalMap=" + extMap.size());
            }
        } catch (Throwable t) {
            WKLoggerUtils.getInstance().e(TAG, "loadSpaceCacheFromDisk failed: " + t.getMessage());
        }
    }

    private void saveSpaceCacheToDisk(ConcurrentHashMap<String, String> spaceMap,
                                      ConcurrentHashMap<String, String> extMap) {
        try {
            android.content.Context ctx = WKIMApplication.getInstance().getContext();
            if (ctx == null) return;
            org.json.JSONObject spaceObj = new org.json.JSONObject();
            for (java.util.Map.Entry<String, String> e : spaceMap.entrySet()) {
                spaceObj.put(e.getKey(), e.getValue());
            }
            org.json.JSONObject extObj = new org.json.JSONObject();
            for (java.util.Map.Entry<String, String> e : extMap.entrySet()) {
                extObj.put(e.getKey(), e.getValue());
            }
            String uid = WKIMApplication.getInstance().getUid();
            boolean auth = spaceCacheSnapshot.authoritative;
            ctx.getSharedPreferences(SPACE_PREFS_NAME, android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString(PREF_KEY_UID, uid)
                    .putBoolean(PREF_KEY_AUTHORITATIVE, auth)
                    .putString(PREF_KEY_SPACE_MAP, spaceObj.toString())
                    .putString(PREF_KEY_EXTERNAL_MAP, extObj.toString())
                    .apply();
        } catch (Throwable t) {
            WKLoggerUtils.getInstance().e(TAG, "saveSpaceCacheToDisk failed: " + t.getMessage());
        }
    }

    private void clearSpaceCacheFromDisk() {
        try {
            android.content.Context ctx = WKIMApplication.getInstance().getContext();
            if (ctx == null) return;
            ctx.getSharedPreferences(SPACE_PREFS_NAME, android.content.Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply();
        } catch (Throwable t) {
            WKLoggerUtils.getInstance().e(TAG, "clearSpaceCacheFromDisk failed: " + t.getMessage());
        }
    }

    /**
     * GH dmwork-android#251 Round-2：读 conv sync 写入的 group {@code space_id}。
     *
     * <p>{@code SpaceFilter.DEFAULT_PROVIDER.getChannelSpaceId} 在
     * {@code channel.remoteExtraMap[space_id]} 没有值（channelInfo 还没水合 / 没回此字段）
     * 时调用本方法做 fallback。channelInfo 水合后 remoteExtraMap 已含权威 space_id，
     * 优先级自然回正。
     *
     * @return conv sync 缓存的 space_id，缺失时返回 {@code null}
     */
    @androidx.annotation.Nullable
    public String getConvSyncSpaceId(String channelID) {
        if (TextUtils.isEmpty(channelID)) return null;
        ensureSpaceCacheLoaded();
        SpaceCacheSnapshot snapshot = spaceCacheSnapshot;
        String result = snapshot.spaceMap.get(channelID);
        if (com.xinbida.wukongim.BuildConfig.DEBUG) {
            android.util.Log.d("ConvSync", "[getConvSyncSpaceId] channelID=" + channelID
                    + " result=" + result
                    + " spaceMap.size=" + snapshot.spaceMap.size());
        }
        return result;
    }

    /**
     * GH dmwork-android#251 Round-2：读 conv sync 写入的当前用户
     * {@code my_source_space_id}。
     *
     * <p>{@code SpaceFilter.DEFAULT_PROVIDER.getMyMembershipSourceSpaceId} 在 member DB
     * 还没 sync 到本地（{@code my-row-not-cached}）时调用本方法做 fallback；
     * member DB 一旦补齐，优先读 member DB。
     *
     * @return conv sync 缓存的 my_source_space_id，缺失时返回 {@code null}
     */
    @androidx.annotation.Nullable
    public String getConvSyncMySourceSpaceId(String channelID) {
        if (TextUtils.isEmpty(channelID)) return null;
        ensureSpaceCacheLoaded();
        SpaceCacheSnapshot snapshot = spaceCacheSnapshot;
        String result = snapshot.externalMap.get(channelID);
        if (com.xinbida.wukongim.BuildConfig.DEBUG) {
            android.util.Log.d("ConvSync", "[getConvSyncMySourceSpaceId] channelID=" + channelID
                    + " result=" + result
                    + " externalMap.size=" + snapshot.externalMap.size()
                    + " spaceMap.contains=" + snapshot.spaceMap.containsKey(channelID));
        }
        return result;
    }

    public boolean isSpaceCacheAuthoritative() {
        ensureSpaceCacheLoaded();
        return spaceCacheSnapshot.authoritative;
    }
}
