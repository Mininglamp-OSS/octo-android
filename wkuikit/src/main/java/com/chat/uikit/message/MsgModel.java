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
import com.xinbida.wukongim.interfaces.ISyncChannelMsgBack;
import com.xinbida.wukongim.interfaces.ISyncConversationChatBack;
import com.xinbida.wukongim.message.type.WKMsgContentType;
import com.xinbida.wukongim.message.type.WKSendMsgResult;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

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

    private Timer timer;

    public void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    public synchronized void startCheckFlameMsgTimer() {
        if (timer == null) {
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    deleteFlameMsg();
                }
            }, 100, 1000);
        }
    }

    public void deleteFlameMsg() {
        if (!WKConstants.isLogin()) return;
        // getWithFlame + deleteWithClientMsgNos 有 DB 操作，放 IO 线程避免 ANR（onPause 等主线程调用场景）
        WKDbScheduler.get().scheduleDirect(() -> {
            List<WKMsg> list = WKIM.getInstance().getMsgManager().getWithFlame();
            if (WKReader.isEmpty(list)) return;
            List<String> deleteClientMsgNoList = new ArrayList<>();
            List<WKMsg> deleteMsgList = new ArrayList<>();
            boolean isStopTimer = true;
            for (WKMsg msg : list) {
                if (msg.flame == 1 && msg.viewed == 1) {
                    long time = WKTimeUtils.getInstance().getCurrentMills() - msg.viewedAt;
                    if (time / 1000 > msg.flameSecond || msg.flameSecond == 0) {
                        deleteClientMsgNoList.add(msg.clientMsgNO);
                        deleteMsgList.add(msg);
                    }
                    isStopTimer = false;
                }
            }
            if (isStopTimer && timer != null) {
                timer.cancel();
                timer.purge();
                timer = null;
            }
            deleteMsg(deleteMsgList, null);
            WKIM.getInstance().getMsgManager().deleteWithClientMsgNos(deleteClientMsgNoList);
        });
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
                if (result != null && !TextUtils.isEmpty(result.tcp_addr)) {
                    String[] strings = result.tcp_addr.split(":");
                    iChatIp.onResult(HttpResponseCode.success, strings[0], strings[1]);
                }
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

    public void syncChat(String last_msg_seqs, int msg_count, long version, ISyncConversationChatBack iSyncConversationChatBack) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("last_msg_seqs", last_msg_seqs);
        jsonObject.put("msg_count", msg_count);
        jsonObject.put("version", version);
        jsonObject.put("device_uuid", WKConstants.getDeviceUUID());
        String spaceId = currentSpaceId.isEmpty() ? null : currentSpaceId;
        //  Phase 2 · T6 埋点：sync request-out / response-in。
        //
        // review（2026-05-04）指出：原版用 Trace.beginSection 在 IO 线程 begin，
        // onSuccess/onFail 在主线程 end，但 Android `android.os.Trace` API 是
        // per-thread stack——跨线程 begin/end 无法配对：
        //   1) IO 线程 begin 永远不 end → perfetto 图里段悬挂
        //   2) 主线程 endSection 可能错误关闭主线程上当时最外层的其他段（例如
        //      YUJ312-onRefreshList-rebuild）。
        //
        // 修复方案（推荐 · 拍板）：syncChat 是唯一跨线程段，直接去掉
        // beginSection/endSection 调用，只保留 Log.d 时间戳。Debug only 性能分析
        // 场景下，Perfetto 的 logcat view 可对齐 "YUJ312" 标签 + elapsedRealtime
        // 戳；其余 7 段（都在主线程 OR 同一后台线程内闭合）配对不受影响。
        final long yuj312SyncStartMs = BuildConfig.DEBUG ? SystemClock.elapsedRealtime() : 0L;
        if (BuildConfig.DEBUG) {
            android.util.Log.d("YUJ312", "sync-request-out space_id=" + spaceId
                    + " version=" + version + " msg_count=" + msg_count
                    + " ts=" + yuj312SyncStartMs);
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
                            + " rtt=" + (SystemClock.elapsedRealtime() - yuj312SyncStartMs) + "ms");
                }
                iSyncConversationChatBack.onBack(null);
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
                ackDeviceUUID();
            }

            @Override
            public void onFail(int code, String msg) {
                iSyncChannelMsgBack.onBack(null);
            }
        });
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
            request(createService(MsgService.class).syncExtraMsg(jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(List<WKSyncExtraMsg> result) {
                if (WKReader.isNotEmpty(result)) {
                    // saveRemoteExtraMsg 内部有 DB 操作，必须在 IO 线程执行，
                    // 放主线程会和 sync 写入争抢数据库锁导致 ANR
                    WKDbScheduler.get().scheduleDirect(() -> {
                        WKIM.getInstance().getMsgManager().saveRemoteExtraMsg(new WKChannel(channelID, channelType), result);
                        new Handler(Looper.getMainLooper()).postDelayed(() -> syncExtraMsg(channelID, channelType), 500);
                    });
                }
            }

            @Override
            public void onFail(int code, String msg) {

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
