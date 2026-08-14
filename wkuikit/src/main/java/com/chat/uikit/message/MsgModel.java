/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.uikit.message;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.Nullable;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.db.WKBaseCMD;
import com.chat.base.db.WKBaseCMDManager;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.ICommonListener;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.base.space.SpaceChangedBroadcaster;
import com.chat.uikit.BuildConfig;
import com.chat.base.external.ExternalMsgExtras;
import com.xinbida.wukongim.db.ReminderDBManager;
import com.chat.base.net.ud.WKDownloader;
import com.chat.base.net.ud.WKProgressManager;
import com.chat.base.net.ud.WKUploader;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.WKLogUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKTimeUtils;

import com.chat.base.utils.WKDbScheduler;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.enity.SensitiveWords;
import com.chat.uikit.enity.WKSyncReminder;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelState;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKConversationMsg;
import com.xinbida.wukongim.entity.WKConversationMsgExtra;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKReminder;
import com.xinbida.wukongim.entity.WKUIConversationMsg;
import com.xinbida.wukongim.entity.WKSyncChannelMsg;
import com.xinbida.wukongim.entity.WKSyncChat;
import com.xinbida.wukongim.entity.WKSyncConvMsgExtra;
import com.xinbida.wukongim.entity.WKSyncExtraMsg;
import com.xinbida.wukongim.entity.WKSyncMsg;
import com.xinbida.wukongim.entity.WKSyncRecent;
import com.xinbida.wukongim.interfaces.ISyncChannelMsgBack;
import com.xinbida.wukongim.interfaces.ISyncConversationChatBack;
import com.xinbida.wukongim.message.type.WKMsgContentType;
import com.xinbida.wukongim.message.type.WKSendMsgResult;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.rxjava3.disposables.Disposable;

/**
 * 2019-11-24 14:18
 * 消息管理
 */
public class MsgModel extends WKBaseModel {
    private MsgModel() {

    }
   public List<WKChannelState> channelStatus;
    private int last_message_seq;

    private static class MsgModelBinder {
        final static MsgModel msgModel = new MsgModel();
    }

    public static MsgModel getInstance() {
        return MsgModelBinder.msgModel;
    }

    /**
     * 阅后即焚轮询。原来用 java.util.Timer 固定每秒触发，而 TimerTask 只把任务丢进单线程的
     * {@link WKDbScheduler} 就立刻返回 —— 队列每秒进 1 个、每次扫描却要几十秒，无界积压把 DB
     * 线程永久占满，连接被长时间独占后主线程任何查询都 park（Bugly ANRWatchdog 那批 ANR）。
     * 改为自排程：上一轮跑完才排下一轮，且没有待过期消息时直接停。
     */
    private final AtomicBoolean flameLoopRunning = new AtomicBoolean(false);
    private volatile Disposable flameLoopTask;
    /** 仅 debug：数轮询跑了几轮，用来确认"没有阅后即焚消息时会停"。 */
    private final AtomicInteger flameSweepCount = new AtomicInteger();

    private static final long FLAME_FIRST_DELAY_MS = 100;
    private static final long FLAME_INTERVAL_MS = 1000;
    /** 连续失败到这个次数就停止轮询（下次进聊天页 startCheckFlameMsgTimer 会重新起）。 */
    private static final int FLAME_MAX_FAILURE_STREAK = 3;
    /** 只在 {@link WKDbScheduler} 单线程上读写，无需同步。 */
    private int flameFailureStreak;

    public void stopTimer() {
        flameLoopRunning.set(false);
        Disposable task = flameLoopTask;
        if (task != null && !task.isDisposed()) {
            task.dispose();
        }
        flameLoopTask = null;
    }

    public void startCheckFlameMsgTimer() {
        // CAS 兼作并发保护：原来 startCheckFlameMsgTimer 是 synchronized 而 DB 线程上的
        // timer.cancel()/timer=null 没有同步，两边竞争可能留下两个 timer。
        if (!flameLoopRunning.compareAndSet(false, true)) return;
        scheduleFlameSweep(FLAME_FIRST_DELAY_MS);
    }

    private void scheduleFlameSweep(long delayMs) {
        if (!flameLoopRunning.get()) return;
        flameLoopTask = WKDbScheduler.get().scheduleDirect(() -> {
            if (!flameLoopRunning.get()) return;
            // sweepFlameMsg 会查库 / 写库，抛出时若不接住，递归排程不会发生而 flameLoopRunning
            // 仍是 true —— startCheckFlameMsgTimer 的 CAS 从此永久短路，清理进程内静默停摆。
            // 不变量：这个 runnable 退出时，要么已排下一轮，要么 flameLoopRunning 已置回 false。
            boolean keepGoing;
            try {
                keepGoing = sweepFlameMsg();
                flameFailureStreak = 0;
            } catch (Throwable t) {
                // 瞬时故障（同步高峰的 SQLiteException / OOM）下一轮重试，连续失败到上限才收摊，
                // 避免把一个必然失败的 DB 操作变成每秒一次的热循环。
                keepGoing = ++flameFailureStreak < FLAME_MAX_FAILURE_STREAK;
                if (BuildConfig.DEBUG) {
                    Log.w("ANRFix", "[flame] sweep failed #" + flameFailureStreak
                            + " keepGoing=" + keepGoing, t);
                }
            }
            if (keepGoing) {
                scheduleFlameSweep(FLAME_INTERVAL_MS);
            } else {
                flameLoopRunning.set(false);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    public void deleteFlameMsg() {
        if (!WKConstants.isLogin()) return;
        // 一次性清理（退出聊天页 / 前后台切换等时机调用），不参与轮询。
        WKDbScheduler.get().scheduleDirect(this::sweepFlameMsg);
    }

    /**
     * 扫一轮阅后即焚消息，删掉已到期的。必须在 {@link WKDbScheduler} 线程上调用。
     *
     * @return 是否还存在"已读的"阅后即焚消息 —— 有才需要继续轮询。返回 false 时轮询停止，
     * 这正是原实现缺的那一步：原来 {@code if (isEmpty(list)) return;} 在 cancel 之前，
     * 导致从没用过阅后即焚的用户（绝大多数）永远停不下来。
     */
    private boolean sweepFlameMsg() {
        if (!WKConstants.isLogin()) return false;
        List<WKMsg> list = WKIM.getInstance().getMsgManager().getWithFlame();
        if (BuildConfig.DEBUG) {
            Log.d("ANRFix", "[flame] sweep #" + flameSweepCount.incrementAndGet()
                    + " rows=" + (list == null ? 0 : list.size()));
        }
        if (WKReader.isEmpty(list)) return false;
        List<String> deleteClientMsgNoList = new ArrayList<>();
        List<WKMsg> deleteMsgList = new ArrayList<>();
        boolean hasViewedFlameMsg = false;
        for (WKMsg msg : list) {
            if (msg.flame != 1 || msg.viewed != 1) continue;
            hasViewedFlameMsg = true;
            long elapsedSecond = (WKTimeUtils.getInstance().getCurrentMills() - msg.viewedAt) / 1000;
            if (elapsedSecond > msg.flameSecond || msg.flameSecond == 0) {
                deleteClientMsgNoList.add(msg.clientMsgNO);
                deleteMsgList.add(msg);
            }
        }
        if (!deleteMsgList.isEmpty()) {
            deleteMsg(deleteMsgList, null);
            WKIM.getInstance().getMsgManager().deleteWithClientMsgNos(deleteClientMsgNoList);
        }
        return hasViewedFlameMsg;
    }

    private void ackMsg() {
        request(createService(MsgService.class).ackMsg(last_message_seq), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {

            }

            @Override
            public void onFail(int code, String msg) {

            }
        });
    }

    /**
     * 删除消息
     */
    public void deleteMsg(List<WKMsg> list, final ICommonListener iCommonListener) {
        if (WKReader.isEmpty(list)) return;
        JSONArray jsonArray = new JSONArray();
        for (WKMsg msg : list) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("message_id", msg.messageID);
            jsonObject.put("channel_id", msg.channelID);
            jsonObject.put("channel_type", msg.channelType);
            jsonObject.put("message_seq", msg.messageSeq);
            jsonArray.add(jsonObject);
        }
        request(createService(MsgService.class).deleteMsg(jsonArray), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                if (iCommonListener != null)
                    iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                if (iCommonListener != null)
                    iCommonListener.onResult(code, msg);
            }
        });
    }

    public void offsetMsg(String channelID, byte channelType, ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("channel_id", channelID);
        jsonObject.put("channel_type", channelType);
        int msgSeq = WKIM.getInstance().getMsgManager().getMaxMessageSeqWithChannel(channelID, channelType);
        jsonObject.put("message_seq", msgSeq);
        request(createService(MsgService.class).offsetMsg(jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                if (iCommonListener != null)
                    iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                if (iCommonListener != null)
                    iCommonListener.onResult(code, msg);
            }
        });
    }

    /**
     * 撤回消息
     *
     * @param msgId           消息ID
     * @param channelID       频道ID
     * @param channelType     频道类型
     * @param iCommonListener 返回
     */
    public void revokeMsg(String msgId, String channelID, byte channelType, String clientMsgNo, final ICommonListener iCommonListener) {
        request(createService(MsgService.class).revokeMsg(msgId, channelID, channelType, clientMsgNo), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    /**
     * 同步红点
     *
     * @param channelId   频道ID
     * @param channelType 频道类型
     */
    public void clearUnread(String channelId, byte channelType, int unreadCount, ICommonListener iCommonListener) {
        final int count = Math.max(unreadCount, 0);
        // updateRedDot 内部有 DB 操作，放 IO 线程避免和 sync 争抢数据库锁导致 ANR
        WKDbScheduler.get().scheduleDirect(() ->
            WKIM.getInstance().getConversationManager().updateRedDot(channelId, channelType, count)
        );
        com.alibaba.fastjson.JSONObject jsonObject = new com.alibaba.fastjson.JSONObject();
        jsonObject.put("channel_id", channelId);
        jsonObject.put("channel_type", channelType);
        jsonObject.put("unread", count);
        request(createService(MsgService.class).clearUnread(jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                if (iCommonListener != null)
                    iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
            }
        });
    }

    /**
     * 修改语音已读
     *
     * @param messageID 服务器消息ID
     */
    public void updateVoiceStatus(String messageID, String channel_id, byte channel_type, int message_seq) {
        if (TextUtils.isEmpty(messageID)) {
            return;
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("message_id", messageID);
        jsonObject.put("channel_id", channel_id);
        jsonObject.put("channel_type", channel_type);
        jsonObject.put("message_seq", message_seq);
        request(createService(MsgService.class).updateVoiceStatus(jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
            }

            @Override
            public void onFail(int code, String msg) {
            }
        });
    }

    public void getChatIp(IChatIp iChatIp) {
        request(createService(MsgService.class).getImIp(WKConfig.getInstance().getUid()), new IRequestResultListener<>() {
            @Override
            public void onSuccess(Ipentity result) {
                if (result == null) {
                    iChatIp.onResult(HttpResponseCode.error, "", "0");
                    return;
                }
                // YUJ-2245: WebSocket-only 客户端，地址优先级 wss_addr → ws_addr。
                // wss / ws 完整 URL 作为 ip 字段透传给 SDK，port 给一个占位值
                // （WKConnection 走 OkHttp WebSocket）。
                if (!TextUtils.isEmpty(result.wss_addr)) {
                    iChatIp.onResult(HttpResponseCode.success, result.wss_addr, "443");
                    return;
                }
                if (!TextUtils.isEmpty(result.ws_addr)) {
                    iChatIp.onResult(HttpResponseCode.success, result.ws_addr, "80");
                    return;
                }
                iChatIp.onResult(HttpResponseCode.error, "", "0");
            }

            @Override
            public void onFail(int code, String msg) {
                iChatIp.onResult(code, "", "0");
            }
        });
    }

    public interface IChatIp {
        void onResult(int code, String ip, String port);
    }

    public void typing(String channelID, byte channelType) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("channel_id", channelID);
        jsonObject.put("channel_type", channelType);
        request(createService(MsgService.class).typing(jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {

            }

            @Override
            public void onFail(int code, String msg) {

            }
        });
    }

    private WKSyncMsg getWKSyncMsg(SyncMsg syncMsg) {
        WKMsg msg = new WKMsg();
        WKSyncMsg WKSyncMsg = new WKSyncMsg();
        msg.status = WKSendMsgResult.send_success;
        msg.messageID = syncMsg.message_id;
        msg.messageSeq = syncMsg.message_seq;
        msg.clientMsgNO = syncMsg.client_msg_no;
        msg.fromUID = syncMsg.from_uid;
        msg.channelID = syncMsg.channel_id;
        msg.channelType = syncMsg.channel_type;
        msg.voiceStatus = syncMsg.voice_status;
        msg.timestamp = syncMsg.timestamp;
        msg.isDeleted = syncMsg.is_delete;
        msg.remoteExtra.unreadCount = syncMsg.unread_count;
        msg.remoteExtra.readedCount = syncMsg.readed_count;
        msg.remoteExtra.extraVersion = syncMsg.extra_version;
        if (syncMsg.payload != null)
            msg.content = JSONObject.toJSONString(syncMsg.payload);
        if (syncMsg.payload != null && syncMsg.payload.containsKey("type")) {
            Object typeObject = syncMsg.payload.get("type");
            if (typeObject != null)
                msg.type = (int) typeObject;
        }
        //  / EP1: 透传消息级外部来源字段到 localExtraMap，供消息气泡
        // 和合并转发渲染 "@SpaceName" 后缀（见 ExternalSourceResolver）。
        // 字段缺失时不写入 map，保持降级链语义。
        copyExternalSourceExtras(msg, syncMsg);
        WKSyncMsg.wkMsg = msg;
        WKSyncMsg.red_dot = syncMsg.header.red_dot;
        WKSyncMsg.sync_once = syncMsg.header.sync_once;
        WKSyncMsg.no_persist = syncMsg.header.no_persist;
        return WKSyncMsg;
    }

    /**
     * Copy the external-source fields from the sync DTO into the msg's
     * {@code localExtraMap}. Package-private for unit testing the passthrough
     * —  was caused by a silent passthrough failure on web, so this hop
     * is explicitly covered by {@code MsgModelExternalPassthroughTest}.
     *
     * <p>Uses plain Java null/empty checks (not {@code TextUtils.isEmpty}) so
     * the method can execute under the standard JVM unit-test runtime without
     * Robolectric.
     */
    static void copyExternalSourceExtras(WKMsg msg, SyncMsg syncMsg) {
        if (msg == null || syncMsg == null) return;
        if (syncMsg.is_external == null
                && isBlank(syncMsg.source_space_id)
                && isBlank(syncMsg.source_space_name)
                && isBlank(syncMsg.home_space_id)
                && isBlank(syncMsg.home_space_name)) {
            return;
        }
        if (msg.localExtraMap == null) {
            msg.localExtraMap = new HashMap();
        }
        if (syncMsg.is_external != null) {
            msg.localExtraMap.put(ExternalMsgExtras.IS_EXTERNAL, syncMsg.is_external);
        }
        if (!isBlank(syncMsg.source_space_id)) {
            msg.localExtraMap.put(ExternalMsgExtras.SOURCE_SPACE_ID, syncMsg.source_space_id);
        }
        if (!isBlank(syncMsg.source_space_name)) {
            msg.localExtraMap.put(ExternalMsgExtras.SOURCE_SPACE_NAME, syncMsg.source_space_name);
        }
        if (!isBlank(syncMsg.home_space_id)) {
            msg.localExtraMap.put(ExternalMsgExtras.HOME_SPACE_ID, syncMsg.home_space_id);
        }
        if (!isBlank(syncMsg.home_space_name)) {
            msg.localExtraMap.put(ExternalMsgExtras.HOME_SPACE_NAME, syncMsg.home_space_name);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }

    /**
     * 同步会话
     *
     * @param last_msg_seqs 最后一条消息的msgseq数组
     * @param msg_count     同步消息条数
     * @param version       最大版本号
     */
    private String currentSpaceId = "";
    private String currentSpaceName = "";

    public void setCurrentSpaceId(String spaceId) {
        setCurrentSpaceId(spaceId, "");
    }

    public void setCurrentSpaceId(String spaceId, String spaceName) {
        //  · 保存旧值，待 SP 落地后广播给 ChatActivity 等保活页面，
        // 让它们在 Space 变化时主动 finish 自己，避免跨 Space 串内容（P0 数据隔离）。
        // 广播必须在 SP 写入之后发出 —— 监听方常调 SpaceFilter.getCurrentSpaceId()
        // 作为"当前 Space"判定依据，顺序反了会读到旧值。
        String oldSpaceId = this.currentSpaceId;
        this.currentSpaceId = spaceId != null ? spaceId : "";
        this.currentSpaceName = spaceName != null ? spaceName : "";
        WKSharedPreferencesUtil.getInstance().putSPWithUID("current_space_id", this.currentSpaceId);
        WKSharedPreferencesUtil.getInstance().putSPWithUID("current_space_name", this.currentSpaceName);
        SpaceChangedBroadcaster.notifyChanged(oldSpaceId, this.currentSpaceId);
    }

    public void loadCurrentSpaceId() {
        this.currentSpaceId = WKSharedPreferencesUtil.getInstance().getSPWithUID("current_space_id");
        if (this.currentSpaceId == null) this.currentSpaceId = "";
        this.currentSpaceName = WKSharedPreferencesUtil.getInstance().getSPWithUID("current_space_name");
        if (this.currentSpaceName == null) this.currentSpaceName = "";
    }

    public String getCurrentSpaceId() {
        return currentSpaceId;
    }

    public String getCurrentSpaceName() {
        return currentSpaceName;
    }

    private static final int SYNC_CHAT_MAX_RETRY = 2;
    private static final long SYNC_CHAT_RETRY_BASE_DELAY_MS = 1000L;

    public void syncChat(String last_msg_seqs, int msg_count, long version, ISyncConversationChatBack iSyncConversationChatBack) {
        String spaceId = currentSpaceId.isEmpty() ? null : currentSpaceId;
        doSyncChat(last_msg_seqs, msg_count, version, spaceId, iSyncConversationChatBack, 0);
    }

    private void doSyncChat(String last_msg_seqs, int msg_count, long version,
                            String spaceId, ISyncConversationChatBack iSyncConversationChatBack, int attempt) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("last_msg_seqs", last_msg_seqs);
        jsonObject.put("msg_count", msg_count);
        jsonObject.put("version", version);
        jsonObject.put("device_uuid", WKConstants.getDeviceUUID());
        final long yuj312SyncStartMs = BuildConfig.DEBUG ? SystemClock.elapsedRealtime() : 0L;
        if (BuildConfig.DEBUG) {
            android.util.Log.d("YUJ312", "sync-request-out space_id=" + spaceId
                    + " version=" + version + " msg_count=" + msg_count
                    + " attempt=" + attempt + " ts=" + yuj312SyncStartMs);
        }
        request(createService(MsgService.class).syncChat(jsonObject, spaceId), new IRequestResultListener<>() {
            @Override
            public void onSuccess(WKSyncChat result) {
                if (BuildConfig.DEBUG) {
                    int convCount = (result == null || result.conversations == null)
                            ? 0 : result.conversations.size();
                    android.util.Log.d("YUJ312", "sync-response-in convCount=" + convCount
                            + " rtt=" + (SystemClock.elapsedRealtime() - yuj312SyncStartMs) + "ms");
                    if (result != null && result.conversations != null) {
                        StringBuilder sb = new StringBuilder("[ConvSync] server returned: ");
                        for (int ci = 0; ci < result.conversations.size(); ci++) {
                            if (ci > 0) sb.append(", ");
                            sb.append(result.conversations.get(ci).channel_id)
                              .append(":").append(result.conversations.get(ci).channel_type);
                        }
                        android.util.Log.d("ConvSync", sb.toString());
                    }
                }
                if (result != null && !TextUtils.isEmpty(result.uid) && result.uid.equals(WKConfig.getInstance().getUid())) {
                    if (WKReader.isNotEmpty(result.conversations)) {
                        WKUIKitApplication.getInstance().isRefreshChatActivityMessage = true;
                    }
                    channelStatus = result.channel_status;
                    iSyncConversationChatBack.onBack(result);
                    last_message_seq = 0;
                    syncCmdMsgs(0);
                    ackDeviceUUID();
                    syncCoverExtra();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> EndpointManager.getInstance().invoke("refresh_conversation_calling",null),300);
                } else {
                    iSyncConversationChatBack.onBack(null);
                }
            }

            @Override
            public void onFail(int code, String msg) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.d("YUJ312", "sync-response-fail code=" + code + " msg=" + msg
                            + " attempt=" + attempt + " rtt=" + (SystemClock.elapsedRealtime() - yuj312SyncStartMs) + "ms");
                }
                if (code >= 500 && attempt < SYNC_CHAT_MAX_RETRY) {
                    long delay = SYNC_CHAT_RETRY_BASE_DELAY_MS * (1L << attempt);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        String currentSpace = currentSpaceId.isEmpty() ? null : currentSpaceId;
                        boolean spaceChanged = (spaceId == null && currentSpace != null)
                                || (spaceId != null && !spaceId.equals(currentSpace));
                        if (spaceChanged) {
                            iSyncConversationChatBack.onBack(null);
                            return;
                        }
                        doSyncChat(last_msg_seqs, msg_count, version, spaceId, iSyncConversationChatBack, attempt + 1);
                    }, delay);
                } else {
                    iSyncConversationChatBack.onBack(null);
                }
            }
        });
    }

    public void ackDeviceUUID() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("device_uuid", WKConstants.getDeviceUUID());
        request(createService(MsgService.class).ackCoverMsg(jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {

            }

            @Override
            public void onFail(int code, String msg) {

            }
        });
    }

    /**
     * 同步某个频道的消息
     *
     * @param channelID           频道ID
     * @param channelType         频道类型
     * @param startMessageSeq     最小messageSeq
     * @param endMessageSeq       最大messageSeq
     * @param limit               获取条数
     * @param pullMode            拉取模式 0:向下拉取 1:向上拉取
     * @param iSyncChannelMsgBack 返回
     */
    public void syncChannelMsg(String channelID, byte channelType, long startMessageSeq, long endMessageSeq, int limit, int pullMode, final ISyncChannelMsgBack iSyncChannelMsgBack) {
        syncChannelMsg(channelID, channelType, startMessageSeq, endMessageSeq, limit, pullMode, true, iSyncChannelMsgBack);
    }

    /**
     * @param ackCover 成功后是否顺带 {@link #ackDeviceUUID()}（推进设备 cover-message ack）。
     *                 常规拉取传 true；type=17 卡片终态帧的**定向补拉**传 false —— 那是一次与
     *                 cover-message 无关的单条重拉，不应把 ack 状态往前推、更不该在一个 CMD 里
     *                 对 N 张待补偿卡各打一次 ackCoverMsg。
     */
    public void syncChannelMsg(String channelID, byte channelType, long startMessageSeq, long endMessageSeq, int limit, int pullMode, boolean ackCover, final ISyncChannelMsgBack iSyncChannelMsgBack) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("channel_id", channelID);
        jsonObject.put("channel_type", channelType);
        jsonObject.put("start_message_seq", startMessageSeq);
        jsonObject.put("end_message_seq", endMessageSeq);
        jsonObject.put("limit", limit);
        jsonObject.put("pull_mode", pullMode);
        jsonObject.put("device_uuid", WKConstants.getDeviceUUID());
        request(createService(MsgService.class).syncChannelMsg(jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(WKSyncChannelMsg result) {
                iSyncChannelMsgBack.onBack(result);
                if (ackCover) ackDeviceUUID();
            }

            @Override
            public void onFail(int code, String msg) {
                iSyncChannelMsgBack.onBack(null);
            }
        });
    }

    /**
     * 补偿拉取一条交互卡消息的最新内容（含 content_edit / 终态帧）。
     *
     * <p>背景：{@code message/extra/sync} 是频道级增量游标（extra_version），客户端一旦漏接某条
     * 消息的终态帧，游标又被其它更新的消息顶过去后，该帧就永久落在游标下方、再也拉不回来，
     * 推理卡因此永久卡在"正在处理"。web/iOS 是从消息本身拿到权威 content_edit（全新拉取 / 实时接住）。
     *
     * <p>这里按 messageSeq 单条重拉 {@code message/channel/sync} —— 它内联返回该消息**当前**的
     * message_extra（含最新 content_edit），绕开坏掉的增量游标，落库后自动重渲成终态。
     */
    public void refreshCardMessage(String channelID, byte channelType, long messageSeq) {
        if (TextUtils.isEmpty(channelID) || messageSeq <= 0) return;
        if (BuildConfig.DEBUG) Log.d("CardFrameDebug", "[req] channelID=" + channelID
                + " type=" + channelType + " targetSeq=" + messageSeq
                + " start=" + messageSeq + " end=" + (messageSeq + 1) + " limit=1 pullMode=1");
        // start_message_seq 服务端语义是**包含**（pullMode=1 上拉：从 start 起含 start 向上取，
        // 参见 SDK MsgDbManager#queryOrSyncHistoryMessages 的 contain 分支）。因此锚点直接取
        // messageSeq 本身，limit=1 上拉命中的第一条即目标；end 给 messageSeq+1 收窄窗口，
        // 不波及相邻消息。
        //（早期误当成"排他锚点"传了 messageSeq-1，导致只拉回相邻的 seq-1、目标卡终态帧永远拉不到。）
        // ackCover=false：定向补拉是一次与 cover-message 无关的单条重拉，不推进 ack（见重载注释）。
        syncChannelMsg(channelID, channelType, messageSeq, messageSeq + 1, 1, 1, false, result -> {
            if (BuildConfig.DEBUG) logRefreshCardResult(messageSeq, result);
            onCardRefreshResult(channelID, channelType, messageSeq, result);
        });
    }

    /**
     * 补拉响应的诊断日志。用 {@code CardFrameDebug} tag 一次性区分三种卡住根因：
     * (a) 命中目标 seq 但 message_extra=null → 服务端没内联终态帧；
     * (b) 命中且有 content_edit 但内容仍是"处理中" → 拉的时候终态帧还没到服务端；
     * (c) returnedSeqs 不含目标 seq → 分页参数拉错了消息，目标卡 extra 根本没刷新。
     */
    private void logRefreshCardResult(long targetSeq, WKSyncChannelMsg result) {
        if (!BuildConfig.DEBUG) return;
        if (result == null) {
            Log.d("CardFrameDebug", "[resp] targetSeq=" + targetSeq + " result=null (onFail/空返回)");
            return;
        }
        StringBuilder seqs = new StringBuilder();
        boolean hitTarget = false;
        if (WKReader.isNotEmpty(result.messages)) {
            for (WKSyncRecent m : result.messages) {
                seqs.append(m.message_seq).append(",");
                if (m.message_seq == targetSeq) {
                    hitTarget = true;
                    WKSyncExtraMsg ex = m.message_extra;
                    String ce = (ex != null && ex.content_edit != null) ? ex.content_edit.toString() : null;
                    Log.d("CardFrameDebug", "[resp] HIT targetSeq=" + targetSeq
                            + " hasExtra=" + (ex != null)
                            + " extraVersion=" + (ex != null ? ex.extra_version : -1)
                            + " hasContentEdit=" + (ce != null)
                            + " contentEditLen=" + (ce != null ? ce.length() : 0)
                            + " contentEdit=" + (ce == null ? "null" : (ce.length() > 400 ? ce.substring(0, 400) : ce)));
                }
            }
        }
        Log.d("CardFrameDebug", "[resp] targetSeq=" + targetSeq
                + " min=" + result.min_message_seq + " max=" + result.max_message_seq
                + " count=" + (result.messages == null ? 0 : result.messages.size())
                + " returnedSeqs=[" + seqs + "] hitTarget=" + hitTarget);
    }

    // ── 交互卡(type=17)终态帧补偿：游标免疫 + CMD 驱动 ──
    // message/extra/sync 是频道级增量游标（version > 游标），而服务端改卡的 version 由进程内
    // HiLo 分配器发号（bot_api/send.go GenSeq），**多副本部署下跨副本非单调**：低号段副本可能
    // 在高 version 提交后才写入更低的 version（服务端注释坐实），于是终态帧的 version 反而更低。
    // 一旦游标被同频道其它高 version extra 顶上去，低版本终态帧就永久落在游标下方、拉不回来
    //（叠加服务端 Redis per-user 游标 floor，客户端连退回游标都做不到）→ 卡片卡在"处理中"。
    // 这里对"尚无**非 transient 权威终态帧**"的 type=17 卡片登记待补偿，用**游标免疫**的
    // message/channel/sync 按 seq 精确补拉（见 refreshCardMessage）。触发点：卡片渲染(re-entry 存量)
    // + wk_sync_message_extra CMD（实时）+ 重连 syncCompleted（对齐 iOS）。
    //
    // 退出机制（关键）：计数是**连续无进展补拉次数**，不是总次数——
    //  · 补拉回来 content_edit 内容有变化(进展) → 计数清零、继续补（长推理卡不会被 transient 帧饿死）；
    //  · 无进展（纯展示卡永远无 content_edit）→ 计数 +1，连续 MAX_UNPRODUCTIVE 次即收敛注销；
    //    · transient 中间帧 = 卡片仍在活跃流式 → 不计入收敛（重置），继续等终态（修 P1-B）；
    //    · 请求失败（result==null）不计入收敛（不是"卡片已完成"的证据，修 P1-B）；
    //    · 墙钟兜底：登记超过 MAX_COMPENSATION_WINDOW_MS 仍未终态 → 注销，防"卡在 transient 帧的
    //      死流"在活跃频道被无关 CMD 无限补拉（不按次数上限，避免饿死长流，修 P1-B）。
    // 用 content_edit **内容哈希**判进展（不用 extra_version：多副本下 version 非单调，终态帧 version
    // 可能反而更低，用它判会把终态帧误当"无进展"漏掉）。收到非 transient 权威帧渲染时也会立即注销。
    // 节流命中不丢弃、改挂一个合并的延迟补拉（修 P1-A：终态帧的 CMD 恰落在节流窗口内被丢 → 永久卡住）。
    private final Map<String, Map<Long, Integer>> pendingCardSeqs = new ConcurrentHashMap<>();
    private final Map<String, Long> lastCardRefreshAt = new ConcurrentHashMap<>();
    private final Map<String, Integer> lastCardContentHash = new ConcurrentHashMap<>();
    private final Map<String, Long> cardRegisteredAt = new ConcurrentHashMap<>();
    // 注意：不能用 ConcurrentHashMap.newKeySet() —— 它是 Java8/API24 API，本工程 minSdk=23 且未开
    // coreLibraryDesugaring，而 MsgModel 是静态单例、getInstance 被大量非卡片路径调用，放这里会让
    // API23 设备核心消息路径 class-init 崩溃（sticky）。用 newSetFromMap 全 API 可用、语义一致（P0-1）。
    private final java.util.Set<String> cardTrailingScheduled =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    // 已落库过非 transient 终态帧的卡（tk）。用于拦截乱序补拉里"陈 transient 帧覆盖已应用终态帧"（P1-2）。
    private final java.util.Set<String> cardTerminalApplied =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Handler cardMainHandler = new Handler(Looper.getMainLooper());
    private static final int MAX_UNPRODUCTIVE_CARD_REFRESH = 5;
    private static final long CARD_REFRESH_MIN_INTERVAL_MS = 2000L;
    /** 补偿墙钟窗口：登记后最多补这么久（覆盖绝大多数推理卡时长，又能兜住死流）。 */
    private static final long MAX_COMPENSATION_WINDOW_MS = 5 * 60 * 1000L;

    private String cardKey(String channelID, byte channelType) {
        return channelID + "#" + channelType;
    }

    /**
     * 卡片渲染时调用。{@code hasTerminalFrame} = 已收到**非 transient 的权威终态帧**
     * （由 Provider 判 {@code editedContent != null && !editedContent.transient}）→ 注销；
     * 否则（尚无终态帧，或当前只是 transient 中间流式帧）登记待补偿。type=17 专用，不波及其它消息。
     *
     * <p>补拉只在**首次登记时触发一次**（覆盖 re-entry 存量帧——服务端已有终态的情况）；
     * 后续 re-bind / 滚动不再补拉，实时更新交给 wk_sync_message_extra CMD 与重连驱动。
     */
    public void onCardRendered(String channelID, byte channelType, long messageSeq, boolean hasTerminalFrame) {
        if (TextUtils.isEmpty(channelID) || messageSeq <= 0) return;
        String k = cardKey(channelID, channelType);
        if (hasTerminalFrame) {
            deregisterCard(channelID, channelType, messageSeq, "收到权威终态帧");
            return;
        }
        // 用 get+put 而非 putIfAbsent/computeIfAbsent：后者在静态类型 Map 上是 API24 方法，minSdk=23 未开
        // desugar 会被 lint 判 NewApi（P0-1）。onCardRendered 只在主线程 bind 调用，无并发建 map 之虞。
        Map<Long, Integer> m = pendingCardSeqs.get(k);
        if (m == null) {
            m = new ConcurrentHashMap<>();
            pendingCardSeqs.put(k, m);
        }
        boolean firstSeen = m.get(messageSeq) == null;
        if (firstSeen) {
            m.put(messageSeq, 0);
            cardRegisteredAt.put(k + "#" + messageSeq, SystemClock.elapsedRealtime());
            if (BuildConfig.DEBUG) {
                Log.d("CardFrameDebug", "[pending] 登记(尚无终态帧) seq=" + messageSeq + " channelID=" + channelID);
            }
            // 仅首次登记补一次，覆盖 re-entry 存量帧；后续靠 CMD / 重连驱动。
            maybeRefreshCard(channelID, channelType, messageSeq);
        }
    }

    /**
     * 收到 wk_sync_message_extra CMD 时调用：对该频道所有待补偿卡片各补拉一次（节流）。
     * CMD 投递可靠，用它做实时触发；补拉走游标免疫路径，因此能捞回游标下方的低版本终态帧。
     */
    public void refreshPendingCards(String channelID, byte channelType) {
        Map<Long, Integer> m = pendingCardSeqs.get(cardKey(channelID, channelType));
        if (m == null || m.isEmpty()) return;
        for (Long seq : m.keySet()) {
            maybeRefreshCard(channelID, channelType, seq);
        }
    }

    /**
     * 退出频道时调用：清掉该频道的待补偿登记、节流时间戳与内容哈希。避免登记项在单例上长期留存
     * + 限制单个 CMD 的 fan-out 规模。仅清本频道，不动其它频道。
     */
    public void clearPendingCards(String channelID, byte channelType) {
        String k = cardKey(channelID, channelType);
        Map<Long, Integer> removed = pendingCardSeqs.remove(k);
        String prefix = k + "#";
        // 显式 Iterator 而非 keySet().removeIf(...)：removeIf 是 API24 default 方法，本方法在频道切换/退出
        // 时无条件调用（不受卡片 API-26 门控），minSdk=23 未开 desugar 时会崩（P0-1）。
        removeKeysByPrefix(lastCardRefreshAt, prefix);
        removeKeysByPrefix(lastCardContentHash, prefix);
        removeKeysByPrefix(cardRegisteredAt, prefix);
        removeByPrefix(cardTrailingScheduled, prefix);
        removeByPrefix(cardTerminalApplied, prefix);
        if (BuildConfig.DEBUG && removed != null && !removed.isEmpty()) {
            Log.d("CardFrameDebug", "[pending] 清理频道待补偿 channelID=" + channelID + " 清掉=" + removed.size() + " 张");
        }
    }

    /** 删除 map 里所有以 prefix 开头的 key（不用 API24 的 removeIf，兼容 minSdk 23；P0-1）。 */
    private static void removeKeysByPrefix(Map<String, ?> map, String prefix) {
        java.util.Iterator<String> it = map.keySet().iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(prefix)) it.remove();
        }
    }

    /** 删除 set 里所有以 prefix 开头的元素（不用 API24 的 removeIf，兼容 minSdk 23；P0-1）。 */
    private static void removeByPrefix(java.util.Set<String> set, String prefix) {
        java.util.Iterator<String> it = set.iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(prefix)) it.remove();
        }
    }

    /** 注销一张待补偿卡：登记 + 节流 + 内容哈希一并清除。 */
    private void deregisterCard(String channelID, byte channelType, long messageSeq, String reason) {
        String k = cardKey(channelID, channelType);
        Map<Long, Integer> m = pendingCardSeqs.get(k);
        boolean removed = m != null && m.remove(messageSeq) != null;
        String tk = k + "#" + messageSeq;
        lastCardRefreshAt.remove(tk);
        lastCardContentHash.remove(tk);
        cardRegisteredAt.remove(tk);
        cardTrailingScheduled.remove(tk);
        cardTerminalApplied.remove(tk);
        if (removed && BuildConfig.DEBUG) {
            Log.d("CardFrameDebug", "[pending] 注销(" + reason + ") seq=" + messageSeq + " channelID=" + channelID);
        }
    }

    private void maybeRefreshCard(String channelID, byte channelType, long messageSeq) {
        String k = cardKey(channelID, channelType);
        Map<Long, Integer> m = pendingCardSeqs.get(k);
        if (m == null || !m.containsKey(messageSeq)) return;   // 未登记 / 已注销
        String tk = k + "#" + messageSeq;
        long now = SystemClock.elapsedRealtime();
        Long last = lastCardRefreshAt.get(tk);
        switch (CardCompensationEvaluator.throttle(now, cardRegisteredAt.get(tk), MAX_COMPENSATION_WINDOW_MS,
                last, CARD_REFRESH_MIN_INTERVAL_MS)) {
            case DEREGISTER_STALE:
                // 墙钟兜底：登记超过补偿窗口仍未终态 → 注销，防"卡在 transient 帧的死流"在活跃频道被
                // 无关 CMD 无限补拉（不按次数上限，避免饿死长流；P1-B）。
                if (BuildConfig.DEBUG) Log.d("CardFrameDebug", "[pending] 超补偿窗口→注销 seq=" + messageSeq);
                deregisterCard(channelID, channelType, messageSeq, "超过补偿窗口");
                return;
            case THROTTLED:
                // 不丢弃：合并挂一个延迟补拉，节流窗口结束再补一次。避免终态帧的 CMD 恰落在窗口内
                // 被丢弃、之后再无触发 → 永久卡住（P1-A）。同一 seq 只挂一个 trailing（cardTrailingScheduled.add 原子去重）。
                if (cardTrailingScheduled.add(tk)) {
                    long delay = CARD_REFRESH_MIN_INTERVAL_MS - (now - last);
                    cardMainHandler.postDelayed(() -> {
                        cardTrailingScheduled.remove(tk);
                        maybeRefreshCard(channelID, channelType, messageSeq);
                    }, delay);
                    if (BuildConfig.DEBUG) Log.d("CardFrameDebug", "[pending] 节流→挂延迟补拉 " + delay + "ms seq=" + messageSeq);
                }
                return;
            case PULL_NOW:
                lastCardRefreshAt.put(tk, now);
                if (BuildConfig.DEBUG) Log.d("CardFrameDebug", "[pending] 补拉 seq=" + messageSeq + " channelID=" + channelID);
                refreshCardMessage(channelID, channelType, messageSeq);
                return;
        }
    }

    /**
     * 补拉回调：按"content_edit 内容是否有进展"更新连续无进展计数。
     * <ul>
     *   <li>取不到目标 seq 的 content_edit（不在返回 / 在但还没 content_edit）→ 与空返回同属
     *       "非信号"，直接返回、不计入收敛预算（P1-3）。纯展示卡在 {@link #onCardRendered} 已不登记，
     *       不会走到这里；此路径服务的是 octo/v2 流式/交互卡首帧到达前的补拉。</li>
     *   <li>已落库终态帧后又来 transient 帧 → 非信号丢弃，防陈帧覆盖终态（P1-2，见 evaluator）。</li>
     *   <li>有进展（content_edit 哈希变化）→ 计数清零 + 只落 extra（{@code saveCardMsgExtra}，不插
     *       message 行）+ 刷新 UI；若是非 transient 终态帧则锁存 tk。</li>
     *   <li>拿回真实 content_edit 却无进展且非 transient（bot 停在同一非流式帧）→ 计数 +1，连续
     *       {@link #MAX_UNPRODUCTIVE_CARD_REFRESH} 次即注销。</li>
     * </ul>
     * 用内容哈希而非 extra_version 判进展：多副本下 version 非单调，终态帧 version 可能更低，
     * 用它判会把终态帧误当"无进展"漏保存。卡已注销时（顶部 containsKey / prior==null）直接跳过，避免复活。
     */
    private void onCardRefreshResult(String channelID, byte channelType, long messageSeq, WKSyncChannelMsg result) {
        String k = cardKey(channelID, channelType);
        Map<Long, Integer> m = pendingCardSeqs.get(k);
        if (m == null || !m.containsKey(messageSeq)) return;   // 已注销（如终态渲染）
        String tk = k + "#" + messageSeq;
        Integer priorCount = m.get(messageSeq);
        // 派生逻辑（content_edit 提取 / transient 判定 / 进展 / 乱序守卫 / 决策）抽到纯类
        // CardCompensationEvaluator，以便用真 fastjson DTO 做 JVM 覆盖——P1-1/P1-2/P1-3 的根因都在这一步。
        CardCompensationEvaluator.Outcome outcome = CardCompensationEvaluator.evaluate(
                result, messageSeq, lastCardContentHash.get(tk), priorCount == null ? 0 : priorCount,
                MAX_UNPRODUCTIVE_CARD_REFRESH, cardTerminalApplied.contains(tk));
        // 空返回（onFail，P1-B①）/ 目标 seq 不在返回 / 在但还没 content_edit（P1-3）/ 终态后又来 transient
        //（P1-2）→ 非信号：不计入收敛预算、不写库。
        if (outcome.nonSignal) {
            if (BuildConfig.DEBUG) Log.d("CardFrameDebug", "[pending] 非信号(空/无 content_edit/陈 transient)不计数 seq=" + messageSeq);
            return;
        }
        // prior==null 检查必须在 lastCardContentHash.put 之前（P2-2）：顶部 containsKey 之后卡可能被并发注销
        //（refreshPendingCards 在 CMD 投递线程触发墙钟注销），此时 deregisterCard 已清 tk 的哈希；若先 put
        // 会复活一条陈哈希，让重登记后同 payload 被误判"无进展"漏落库。
        Integer prior = m.get(messageSeq);
        if (prior == null) return;
        if (outcome.newContentHash != null) lastCardContentHash.put(tk, outcome.newContentHash);
        // 计数用 get+put 而非 computeIfPresent（API24；P0-1）。onCardRefreshResult 跑在 RxJava 主线程 observer，
        // 唯一可能的并发注销是 CMD 线程的墙钟分支，那时卡已是 5min 死流、写入无害（上面 prior 守卫已挡多数）。
        switch (outcome.decision) {
            case PROGRESS_RESET:
                m.put(messageSeq, 0);   // 有进展→重置无进展计数
                if (outcome.appliedTerminal) cardTerminalApplied.add(tk);   // 锁存终态帧,拦后续陈 transient(P1-2)
                if (BuildConfig.DEBUG) Log.d("CardFrameDebug", "[pending] 有进展→落 extra 刷新 seq=" + messageSeq + " terminal=" + outcome.appliedTerminal);
                WKDbScheduler.get().scheduleDirect(() ->
                        WKIM.getInstance().getMsgManager().saveCardMsgExtra(result.messages));
                break;
            case MIDSTREAM_RESET:
                m.put(messageSeq, 0);   // 仍在流式→重置,继续等终态
                if (BuildConfig.DEBUG) Log.d("CardFrameDebug", "[pending] transient 中间帧,仍流式,继续等 seq=" + messageSeq);
                break;
            case UNPRODUCTIVE_CONTINUE:
                int n = prior + 1;
                m.put(messageSeq, n);
                if (BuildConfig.DEBUG) Log.d("CardFrameDebug", "[pending] 无进展 #" + n + "/" + MAX_UNPRODUCTIVE_CARD_REFRESH + " seq=" + messageSeq);
                break;
            case UNPRODUCTIVE_DEREGISTER:
                deregisterCard(channelID, channelType, messageSeq, "连续" + MAX_UNPRODUCTIVE_CARD_REFRESH + "次无进展");
                break;
        }
    }

    /**
     * 同步cmd消息
     *
     * @param max_message_seq 最大消息编号
     */
    private void syncCmdMsgs(long max_message_seq) {

        JSONObject jsonObject1 = new JSONObject();
        jsonObject1.put("limit", 500);
        jsonObject1.put("max_message_seq", max_message_seq);
        request(createService(MsgService.class).syncMsg(jsonObject1), new IRequestResultListener<>() {
            @Override
            public void onSuccess(List<SyncMsg> list) {
                if (WKReader.isNotEmpty(list)) {
                    List<WKBaseCMD> cmdList = new ArrayList<>();
                    for (int i = 0, size = list.size(); i < size; i++) {
                        WKSyncMsg WKSyncMsg = getWKSyncMsg(list.get(i));
                        WKBaseCMD WKBaseCmd = new WKBaseCMD();
                        if (WKSyncMsg.wkMsg.type == WKMsgContentType.WK_INSIDE_MSG) {
                            WKBaseCmd.client_msg_no = WKSyncMsg.wkMsg.clientMsgNO;
                            WKBaseCmd.created_at = WKSyncMsg.wkMsg.createdAt;
                            WKBaseCmd.message_id = WKSyncMsg.wkMsg.messageID;
                            WKBaseCmd.message_seq = WKSyncMsg.wkMsg.messageSeq;
                            WKBaseCmd.timestamp = WKSyncMsg.wkMsg.timestamp;
                            try {
                                org.json.JSONObject jsonObject = new org.json.JSONObject(WKSyncMsg.wkMsg.content);
                                if (jsonObject.has("cmd")) {
                                    WKBaseCmd.cmd = jsonObject.optString("cmd");
                                }
                                if (jsonObject.has("sign")) {
                                    WKBaseCmd.sign = jsonObject.optString("sign");
                                }
                                if (jsonObject.has("param")) {
                                    org.json.JSONObject paramJson = jsonObject.optJSONObject("param");
                                    if (paramJson != null) {
                                        if (!paramJson.has("channel_id") && !TextUtils.isEmpty(WKSyncMsg.wkMsg.channelID)) {
                                            paramJson.put("channel_id", WKSyncMsg.wkMsg.channelID);
                                        }
                                        if (!paramJson.has("channel_type")) {
                                            paramJson.put("channel_type", WKSyncMsg.wkMsg.channelType);
                                        }
                                        WKBaseCmd.param = paramJson.toString();
                                    }
                                }
                            } catch (JSONException e) {
                                WKLogUtils.e("MsgModel", "cmd messages not json struct");
                            }
                            cmdList.add(WKBaseCmd);
                        }
                        if (WKSyncMsg.wkMsg.messageSeq > last_message_seq) {
                            last_message_seq = WKSyncMsg.wkMsg.messageSeq;
                        }
                    }
                    // addCmd 内部有 DB 事务操作，放 IO 线程避免 ANR
                    WKDbScheduler.get().scheduleDirect(() -> {
                        WKBaseCMDManager.getInstance().addCmd(cmdList);
                        if (last_message_seq != 0) {
                            ackMsg();
                        }
                        new Handler(Looper.getMainLooper()).postDelayed(() -> syncCmdMsgs(last_message_seq), 1000);
                    });

                } else {
                    if (last_message_seq != 0) {
                        ackMsg();
                    }
                    // handleCmd 内部有大量 DB 读写操作，放 IO 线程避免 ANR
                    WKDbScheduler.get().scheduleDirect(() -> WKBaseCMDManager.getInstance().handleCmd());
                }
            }

            @Override
            public void onFail(int code, String msg) {
                if (last_message_seq != 0) {
                    ackMsg();
                    WKBaseCMDManager.getInstance().handleCmd();
                }
            }
        });
    }

    /**
     * 同步某个会话的扩展消息
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     */
    public void syncExtraMsg(String channelID, byte channelType) {
        // getMsgExtraMaxVersionWithChannel 有 DB 查询，整体放 IO 线程避免 ANR
        WKDbScheduler.get().scheduleDirect(() -> {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("channel_id", channelID);
            jsonObject.put("channel_type", channelType);
            long maxExtraVersion = WKIM.getInstance().getMsgManager().getMsgExtraMaxVersionWithChannel(channelID, channelType);
            jsonObject.put("extra_version", maxExtraVersion);
            jsonObject.put("limit", 100);
            String deviceUUID = WKConstants.getDeviceUUID();
            jsonObject.put("source", deviceUUID);
            if (BuildConfig.DEBUG) Log.d("CardFrameDebug", "[extraSync req] channelID=" + channelID
                    + " sentVersion=" + maxExtraVersion + " limit=100");
            request(createService(MsgService.class).syncExtraMsg(jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(List<WKSyncExtraMsg> result) {
                if (BuildConfig.DEBUG) {
                    StringBuilder sb = new StringBuilder();
                    if (WKReader.isNotEmpty(result)) {
                        for (WKSyncExtraMsg e : result) {
                            sb.append(e.message_id).append("@v").append(e.extra_version)
                                    .append(e.content_edit != null ? "(edit)" : "").append(" ");
                        }
                    }
                    Log.d("CardFrameDebug", "[extraSync resp] channelID=" + channelID
                            + " sentVersion=" + maxExtraVersion
                            + " count=" + (result == null ? 0 : result.size())
                            + " items=[" + sb + "]");
                }
                if (BuildConfig.DEBUG) android.util.Log.d("RevokeDebug", "[syncExtraMsg] onSuccess: channelID=" + channelID + " resultSize=" + (result == null ? "null" : result.size()) + " extraVersion=" + maxExtraVersion);
                if (WKReader.isNotEmpty(result)) {
                    for (WKSyncExtraMsg extra : result) {
                        if (BuildConfig.DEBUG) android.util.Log.d("RevokeDebug", "[syncExtraMsg] item: msgID=" + extra.message_id + " revoke=" + extra.revoke + " revoker=" + extra.revoker);
                    }
                    // saveRemoteExtraMsg 内部有 DB 操作，必须在 IO 线程执行，
                    // 放主线程会和 sync 写入争抢数据库锁导致 ANR
                    WKDbScheduler.get().scheduleDirect(() -> {
                        WKIM.getInstance().getMsgManager().saveRemoteExtraMsg(new WKChannel(channelID, channelType), result);
                        new Handler(Looper.getMainLooper()).postDelayed(() -> syncExtraMsg(channelID, channelType), 500);
                    });
                } else if (maxExtraVersion > 10_000_000_000L) {
                    if (BuildConfig.DEBUG) android.util.Log.w("RevokeDebug", "[syncExtraMsg] extraVersion abnormally high (" + maxExtraVersion + "), channelID=" + channelID + " — needs investigation");
                }
            }

            @Override
            public void onFail(int code, String msg) {
                if (BuildConfig.DEBUG) android.util.Log.e("RevokeDebug", "[syncExtraMsg] onFail: channelID=" + channelID + " code=" + code + " msg=" + msg);
            }
        });
        }); // end Schedulers.io
    }


    // 同步敏感词
    public void syncSensitiveWords() {
        if (TextUtils.isEmpty(WKConfig.getInstance().getToken())) return;
        long version = WKSharedPreferencesUtil.getInstance().getLong("wk_sensitive_words_version");
        request(createService(MsgService.class).syncSensitiveWords(version), new IRequestResultListener<>() {
            @Override
            public void onSuccess(SensitiveWords result) {
                WKSharedPreferencesUtil.getInstance().putLong("wk_sensitive_words_version", result.version);
                if (!TextUtils.isEmpty(result.tips)) {
                    WKUIKitApplication.getInstance().sensitiveWords = result;
                    String json = JSON.toJSONString(result);
                    WKSharedPreferencesUtil.getInstance().putSP("wk_sensitive_words", json);
                }
            }

            @Override
            public void onFail(int code, String msg) {

            }
        });
    }

    public void editMsg(String msgID, int msgSeq, String channelID, byte channelType, String content, ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("message_id", msgID);
        jsonObject.put("message_seq", msgSeq);
        jsonObject.put("channel_id", channelID);
        jsonObject.put("channel_type", channelType);
        jsonObject.put("content_edit", content);
        request(createService(MsgService.class).editMsg(jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                if (iCommonListener != null)
                    iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                if (iCommonListener != null)
                    iCommonListener.onResult(code, msg);
            }
        });
    }

    public void syncReminder() {
        // getMaxVersion / getWithChannelType 有 DB 查询，整体放 IO 线程避免 ANR
        WKDbScheduler.get().scheduleDirect(() -> {
            long version = WKIM.getInstance().getReminderManager().getMaxVersion();
            List<String> channelIDs = new ArrayList<>();
            List<WKConversationMsg> list = WKIM.getInstance().getConversationManager().getWithChannelType(WKChannelType.GROUP);
            for (WKConversationMsg mConversationMsg : list) {
                channelIDs.add(mConversationMsg.channelID);
            }
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("version", version);
            jsonObject.put("limit", 200);
            jsonObject.put("channel_ids", channelIDs);
            request(createService(MsgService.class).syncReminder(jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(List<WKSyncReminder> result) {
                if (WKReader.isNotEmpty(result)) {
                    String loginUID = WKConfig.getInstance().getUid();
                    List<WKReminder> list = new ArrayList<>();
                    for (WKSyncReminder reminder : result) {
                        // 对齐 iOS：只保留属于当前用户的提醒，过滤掉同名用户（如 "张乾" vs "张乾1"）
                        if (!TextUtils.isEmpty(reminder.uid) && !reminder.uid.equals(loginUID)) {
                            continue;
                        }
                        WKReminder WKReminder = syncReminderToReminder(reminder);
                        if (!TextUtils.isEmpty(reminder.publisher) && reminder.publisher.equals(loginUID)) {
                            WKReminder.done = 1;
                        }
                        list.add(WKReminder);
                    }
                    // saveOrUpdateReminders 内部有 DB 操作，必须在 IO 线程执行，
                    // 放主线程会和 sync 写入争抢数据库锁导致 ANR
                    WKDbScheduler.get().scheduleDirect(() ->
                        WKIM.getInstance().getReminderManager().saveOrUpdateReminders(list)
                    );
                }

            }

            @Override
            public void onFail(int code, String msg) {

            }
        });
        }); // end Schedulers.io
    }

    public void doneReminder(List<Long> list) {
        if (WKReader.isEmpty(list)) return;

        // DB 操作放 IO 线程，避免 onPause 主线程阻塞
        List<Long> idsCopy = new ArrayList<>(list);
        WKDbScheduler.get().scheduleDirect(() -> {
            // Step 1: 查出受影响的 channelID
            List<WKReminder> affected = ReminderDBManager.getInstance().queryWithIds(idsCopy);
            List<String> channelIds = new ArrayList<>();
            for (WKReminder r : affected) {
                if (!TextUtils.isEmpty(r.channelID) && !channelIds.contains(r.channelID)) {
                    channelIds.add(r.channelID);
                }
            }

            // Step 2: 更新 DB (done=1)
            ReminderDBManager.getInstance().doneWithReminderIds(idsCopy);

            // Step 3-4: 回主线程清缓存 + 刷新会话列表
            AndroidUtilities.runOnUIThread(() -> {
                WKIM.getInstance().getReminderManager().clearAllCache();
                if (WKReader.isNotEmpty(channelIds)) {
                    List<WKUIConversationMsg> uiMsgList = WKIM.getInstance().getConversationManager()
                            .getWithChannelIds(channelIds);
                    for (WKUIConversationMsg msg : uiMsgList) {
                        msg.setReminderList(null);
                    }
                    WKIM.getInstance().getConversationManager().setOnRefreshMsg(uiMsgList, "doneReminders");
                }
            });
        });

        // Step 5: 异步通知服务端，失败重试一次
        request(createService(MsgService.class).doneReminder(list), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
            }

            @Override
            public void onFail(int code, String msg) {
                request(createService(MsgService.class).doneReminder(list), new IRequestResultListener<>() {
                    @Override
                    public void onSuccess(CommonResponse result) {
                    }

                    @Override
                    public void onFail(int code, String msg) {
                    }
                });
            }
        });
    }

    public void updateCoverExtra(String channelID, byte channelType, long browseTo, long keepMsgSeq, int keepOffsetY, String draft) {
        WKConversationMsgExtra extra = new WKConversationMsgExtra();
        extra.draft = draft;
        extra.keepOffsetY = keepOffsetY;
        extra.keepMessageSeq = keepMsgSeq;
        extra.channelID = channelID;
        extra.channelType = channelType;
        extra.browseTo = browseTo;
        if (!TextUtils.isEmpty(draft)) {
            extra.draftUpdatedAt = WKTimeUtils.getInstance().getCurrentSeconds();
        }
        // updateMsgExtra 内部有 DB 操作，放 IO 线程避免 onPause 时和 sync 争抢数据库锁导致 ANR
        WKDbScheduler.get().scheduleDirect(() ->
            WKIM.getInstance().getConversationManager().updateMsgExtra(extra)
        );

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("browse_to", browseTo);
        jsonObject.put("keep_message_seq", keepMsgSeq);
        jsonObject.put("keep_offset_y", keepOffsetY);
        jsonObject.put("draft", draft);
        request(createService(MsgService.class).updateCoverExtra(channelID, channelType, jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {

            }

            @Override
            public void onFail(int code, String msg) {

            }
        });
    }

    public void syncCoverExtra() {
        // getMsgExtraMaxVersion 有 DB 查询，整体放 IO 线程避免 ANR
        WKDbScheduler.get().scheduleDirect(() -> {
            long version = WKIM.getInstance().getConversationManager().getMsgExtraMaxVersion();
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("version", version);
            request(createService(MsgService.class).syncCoverExtra(jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(List<WKSyncConvMsgExtra> result) {
                // saveSyncMsgExtras 内部有 DB 操作，必须在 IO 线程执行，
                // 放主线程会和 sync 写入争抢数据库锁导致 ANR
                WKDbScheduler.get().scheduleDirect(() -> {
                    WKIM.getInstance().getConversationManager().saveSyncMsgExtras(result);
                    if (WKReader.isNotEmpty(result)) {
                        new Handler(Looper.getMainLooper()).post(() ->
                            EndpointManager.getInstance().invoke("refresh_conversation_extras", null)
                        );
                    }
                });
            }

            @Override
            public void onFail(int code, String msg) {

            }
        });
        }); // end Schedulers.io
    }

    private WKReminder syncReminderToReminder(WKSyncReminder syncReminder) {
        WKReminder reminder = new WKReminder();
        reminder.reminderID = syncReminder.id;
        reminder.channelID = syncReminder.channel_id;
        reminder.channelType = syncReminder.channel_type;
        reminder.messageSeq = syncReminder.message_seq;
        reminder.type = syncReminder.reminder_type;
        reminder.isLocate = syncReminder.is_locate;
        reminder.text = syncReminder.text;
        reminder.version = syncReminder.version;
        reminder.messageID = syncReminder.message_id;
        reminder.uid = syncReminder.uid;
        reminder.done = syncReminder.done;
        reminder.data = syncReminder.data;
        reminder.publisher = syncReminder.publisher;
        return reminder;
    }

    public void backupMsg(String filePath, ICommonListener iCommonListener) {
        String url = WKApiConfig.baseUrl + "message/backup";
        WKUploader.getInstance().upload(url, filePath, new WKUploader.IUploadBack() {
            @Override
            public void onSuccess(String url) {
                iCommonListener.onResult(HttpResponseCode.success, "");
            }

            @Override
            public void onError() {
                iCommonListener.onResult(HttpResponseCode.error, "");
            }
        });
    }

    public void recovery(final IRecovery iRecovery) {
        String uid = WKConfig.getInstance().getUid();
        String url = WKApiConfig.baseUrl + "message/recovery";
        String path = WKConstants.messageBackupDir + uid + "_recovery.json";
        WKDownloader.Companion.getInstance().download(url, path, new WKProgressManager.IProgress() {
            @Override
            public void onProgress(@Nullable Object tag, int progress) {

            }

            @Override
            public void onSuccess(@Nullable Object tag, @Nullable String path) {
                iRecovery.onSuccess(path);
            }

            @Override
            public void onFail(@Nullable Object tag, @Nullable String msg) {
                iRecovery.onFail();
            }
        });
    }

    public interface IRecovery {
        void onSuccess(String path);

        void onFail();
    }
}
