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

package com.chat.uikit.chat.manager;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.SystemClock;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;

import com.chat.base.WKBaseApplication;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKBinder;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.db.ApplyDB;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.entity.NewFriendEntity;
import com.chat.base.entity.UserInfoSetting;
import com.chat.base.entity.WKGroupType;
import com.chat.base.msg.IConversationContext;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.msgitem.WKUIChatMsgItemEntity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.utils.NotificationCompatUtil;
import com.chat.base.utils.WKCommonUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKLogUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKTimeUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.base.views.pwdview.NumPwdDialog;
import com.chat.uikit.R;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.chat.ChatActivity;
import com.chat.uikit.chat.SpaceSyncCoordinator;
import com.chat.uikit.contacts.service.FriendModel;
import com.chat.uikit.db.WKContactsDB;
import com.chat.uikit.enity.ProhibitWord;
import com.chat.uikit.group.service.GroupModel;
import com.chat.uikit.message.MsgModel;
import com.chat.uikit.thread.service.ThreadModel;
import com.chat.uikit.message.ProhibitWordModel;
import com.chat.uikit.search.SearchUserActivity;
import com.chat.uikit.user.UserDetailActivity;
import com.chat.uikit.user.service.UserModel;
import com.chat.uikit.utils.PushNotificationHelper;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKCMDKeys;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelExtras;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelStatus;
import com.xinbida.wukongim.entity.WKSyncExtraMsg;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKConversationMsg;
import com.xinbida.wukongim.entity.WKMsg;

import org.json.JSONObject;
import com.xinbida.wukongim.entity.WKUIConversationMsg;
import com.xinbida.wukongim.msgmodel.WKTextContent;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import com.chat.uikit.BuildConfig;

/**
 * 2019-11-18 11:30
 * im监听相关处理
 */
public class WKIMUtils {

    private static final String TAG = "WKIMUtils";

    //  P1-4: short global debounce guarding the startChat entry only.
    // Per-view SingleClickUtil (300ms) protects the SAME row but cross-row
    // fast taps (A → B within 300ms) fall through because each row has its
    // own throttle window. Without a small global gate, Fix 3's async Intent
    // assembly lets two Observables race and both startActivity() complete,
    // stacking Activities on the back stack ([tabs, A, B]). 250ms is short
    // enough to not steal legitimate taps but long enough to collapse the
    // typical double-tap / two-row flurry into a single chat open.
    private static final long START_CHAT_DEBOUNCE_MS = 250L;
    private final AtomicLong lastStartChatMs = new AtomicLong(0L);

    // : debug-only trace tag for narrow-screen chat-open breakdown.
    // Grep logcat for "YUJ276-trace" to line up:
    //   [click]→[intent-build]→[startActivity]→[ChatActivity.onCreate]→[onStart]
    //   →[onResume]. Gated behind WKBinder.isDebug (= BuildConfig.DEBUG) so
    // release APKs never emit these entries.
    public static final String TRACE_TAG = "YUJ276-trace";

    //  P1-1：Fix D 已下沉到 ChatActivity.onCreate + finish()（见
    // com.chat.base.foldable.NarrowTransition）。这里不再持有 NARROW_MODE_DP /
    // applyFastTransitionIfNarrow。保留 TRACE_TAG 供 trace log 串联。

    private WKIMUtils() {
    }

    private static class IMUtilsBinder {
        private final static WKIMUtils util = new WKIMUtils();
    }

    public static WKIMUtils getInstance() {
        return IMUtilsBinder.util;
    }

    /**
     * 初始化事件
     */
    public void initIMListener() {
        EndpointManager.getInstance().setMethod("show_rtc_notification", object -> {
            if (object instanceof String fromUID) {
                WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(fromUID, WKChannelType.PERSONAL);
                var fromName = "";
                if (channel != null) {
                    if (TextUtils.isEmpty(channel.channelRemark)) {
                        fromName = channel.channelName;
                    } else fromName = channel.channelRemark;
                }

                Vibrator mVibrator = (Vibrator) WKBaseApplication.getInstance().getContext().getSystemService(Context.VIBRATOR_SERVICE);
                long[] pattern = {0, 1000, 1000};
                AudioAttributes audioAttributes;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioAttributes = new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION) //key
                            .build();
                    mVibrator.vibrate(pattern, 0, audioAttributes);
                } else {
                    mVibrator.vibrate(pattern, 0);
                }
                PushNotificationHelper.INSTANCE.notifyCall(WKUIKitApplication.getInstance().getContext(), 2, fromName, WKBaseApplication.getInstance().getContext().getString(R.string.invite_call));
            }
            return null;
        });
        EndpointManager.getInstance().setMethod("cancel_rtc_notification", object -> {
            Vibrator vibrator = (Vibrator) WKBaseApplication.getInstance().getContext().getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.cancel();
            NotificationCompatUtil.Companion.cancel(WKUIKitApplication.getInstance().getContext(), 2);
            return null;
        });
        // 获取用户密钥
//        WKIM.getInstance().getSignalProtocolManager().addOnCryptoSignalDataListener((channelID, channelTyp, iCryptoSignalDataResult) -> {
//            if (channelTyp == WKChannelType.PERSONAL) {
//                WKCryptoModel.getInstance().getUserKey(channelID, (code, msg, data) -> {
//                    if (code == HttpResponseCode.success && data != null) {
//                        WKSignalKey signalKey = new WKSignalKey();
//                        signalKey.UID = data.uid;
//                        signalKey.registrationID = data.registration_id;
//                        signalKey.identityKey = data.identity_key;
//                        signalKey.signedPubKey = data.signed_pubkey;
//                        signalKey.signedSignature = data.signed_signature;
//                        signalKey.signedPreKeyID = data.signed_prekey_id;
//                        WKOneTimePreKey oneTimePreKey = new WKOneTimePreKey();
//                        oneTimePreKey.pubKey = data.onetime_prekey.pubkey;
//                        oneTimePreKey.keyID = data.onetime_prekey.key_id;
//                        signalKey.oneTimePreKey = oneTimePreKey;
//                        iCryptoSignalDataResult.onResult(signalKey);
//                    } else {
//                        iCryptoSignalDataResult.onResult(null);
//                    }
//                });
//            }
//        });

        //监听sdk获取IP和port
        WKIM.getInstance().getConnectionManager().addOnGetIpAndPortListener(andPortListener -> MsgModel.getInstance().getChatIp((code, ip, port) -> andPortListener.onGetSocketIpAndPort(ip, Integer.parseInt(port))));
        //消息存库拦截器监听
        WKIM.getInstance().getMsgManager().addMessageStoreBeforeIntercept(msg -> {
            if (msg != null && msg.type == WKContentType.screenshot) {
                WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(msg.channelID, msg.channelType);
                if (channel != null && channel.remoteExtraMap != null && channel.remoteExtraMap.containsKey(WKChannelExtras.screenshot)) {
                    Object object = channel.remoteExtraMap.get(WKChannelExtras.screenshot);
                    int screenshot = 0;
                    if (object != null) {
                        screenshot = (int) object;
                    }
                    return screenshot != 0;
                } else {
                    return true;
                }
            }
            return true;
        });
        //监听聊天附件上传
        WKIM.getInstance().getMsgManager().addOnUploadAttachListener((msg, listener) -> WKSendMsgUtils.getInstance().uploadChatAttachment(msg, listener));
        //监听同步会话（msg_count=30，与 iOS 保持一致）
        // BotFather 跨 Space 可见性问题参考 iOS 用 per-space 标记解决，不再通过加大 msg_count 暴力解决
        WKIM.getInstance().getConversationManager().addOnSyncConversationListener((s, i, l, iSyncConvChatBack) -> MsgModel.getInstance().syncChat(s, Math.max(i, 30), l, iSyncConvChatBack));
        //监听同步频道会话
        WKIM.getInstance().getMsgManager().addOnSyncChannelMsgListener((channelID, channelType, startMessageSeq, endMessageSeq, limit, pullMode, iSyncChannelMsgBack) -> MsgModel.getInstance().syncChannelMsg(channelID, channelType, startMessageSeq, endMessageSeq, limit, pullMode, iSyncChannelMsgBack));
        //新消息监听
        WKIM.getInstance().getMsgManager().addOnNewMsgListener("system", msgList -> {
            boolean isAlertMsg = false;
            String channelID = "";
            byte channelType = WKChannelType.PERSONAL;
            String loginUID = WKConfig.getInstance().getUid();
            // 方案 B (跨 Space 串台兜底): 本批次内若有任一群成员关系变更事件,
            // 循环结束后触发一次 conv/sync 增量刷新 space_memberships,
            // 让 SpaceFilter 在后续 realtime 消息到达时能给出权威判定,
            // 避免新加入群的首条消息撞 fail-open 串到当前 Space.
            // 详见 triggerSpaceMembershipsRefresh() javadoc.
            boolean needsMembershipRefresh = false;
            if (WKReader.isNotEmpty(msgList)) {
                channelID = msgList.get(msgList.size() - 1).channelID;
                channelType = msgList.get(msgList.size() - 1).channelType;
                for (int i = 0, size = msgList.size(); i < size; i++) {
                    inferSpaceIdForBotMessage(msgList.get(i));
                    if (msgList.get(i).type == WKContentType.setNewGroupAdmin) {
                        // admin 角色变更不影响 my memberships, 不触发 sync
                        GroupModel.getInstance().groupMembersSync(msgList.get(i).channelID, null);
                    } else if (msgList.get(i).type == WKContentType.groupSystemInfo) {
                        // 群属性变更 (名称/公告/禁言); 也可能是 group create 通知
                        // (server SendGroupCreate 走 octo-lib, 客户端无法区分子类型). 保守触发 sync.
                        WKCommonModel.getInstance().getChannel(msgList.get(i).channelID, WKChannelType.GROUP, null);
                        GroupModel.getInstance().groupMembersSync(msgList.get(i).channelID, null);
                        needsMembershipRefresh = true;
                    } else if (msgList.get(i).type == WKContentType.addGroupMembersMsg) {
                        // 群加人. server 发给所有群成员包括被加者本人 (service.go:1506,
                        // payload: {type:1002, content, extra:[{uid,name},...]}, iOS 解析参考
                        // WKSystemMessageCell.m:80-102). 只有 my uid 在 extra 列表里时才触发 sync,
                        // 否则是其他成员被加, 我的 memberships 没变.
                        //
                        // C+ 兜底 (bot invite 服务端 version 分配 bug):
                        // 服务端给 bot invite 分配的 channel_member.version 会低于群内真人
                        // 当前 max (实测 low ~1000, 稳定复现于多个群), 走
                        // `WHERE version > local_max` 的增量 sync 永远拉不到该 row.
                        // iOS 通过打开群设置时调 `/groups/xx/members` (无 version 过滤)
                        // upsert 自愈, Android SDK 只对 super group 走这条路径 (
                        // ChannelMembersManager.getWithPageOrSearch 里 `if (groupType==1)`).
                        // 兜底: 走 groupMembersSyncAndVerify — 增量拉完后校验 1002 extra
                        // 里被邀请的 uid 是否落盘, 缺任何一个即触发 v=0 全量 upsert.
                        // extra 解析失败传 null, 保守走全量 (对齐 iOS 群设置页自愈语义).
                        final com.xinbida.wukongim.entity.WKMsg addMsg = msgList.get(i);
                        final java.util.List<String> invitedUids = parseGroupMemberAddExtraUids(addMsg);
                        GroupModel.getInstance().groupMembersSyncAndVerify(
                                addMsg.channelID,
                                invitedUids.isEmpty() ? null : invitedUids);
                        if (isMyUidInGroupMemberMsgExtra(addMsg)) {
                            needsMembershipRefresh = true;
                        }
                    } else if (msgList.get(i).type == WKContentType.removeGroupMembersMsg) {
                        // 群减人. server 先 IMRemoveSubscriber 把被踢用户从频道踢掉
                        // (service.go:1700-1704), 再发 1003 通知 (1707-1713). 被踢用户已经
                        // 不在订阅列表 → 收不到 1003. 我收到的 1003 永远是别人被踢, 我的
                        // memberships 没变, 不触发 sync.
                        GroupModel.getInstance().groupMembersSync(msgList.get(i).channelID, null);
                        // 防御性兜底: 如果未来 server 改了 IMRemoveSubscriber/发通知的顺序,
                        // 或出现 race 让我自己也收到了 1003, isMyUidInGroupMemberMsgExtra
                        // 命中即触发 refresh, 避免 stale group 在 SpaceFilter 缓存里串台.
                        // 当前 server 实现下这条分支永远不会进, 触发也只会被
                        // SpaceSyncCoordinator debounce 掉, 零副作用.
                        if (isMyUidInGroupMemberMsgExtra(msgList.get(i))) {
                            needsMembershipRefresh = true;
                        }
                    } else {
                        if (msgList.get(i).type != WKContentType.WK_INSIDE_MSG) {
                            isAlertMsg = true;
                        }
                    }

                    if (msgList.get(i).header.noPersist || !msgList.get(i).header.redDot || !WKContentType.isSupportNotification(msgList.get(i).type)) {
                        isAlertMsg = false;
                    }
                    if (!TextUtils.isEmpty(loginUID) && !TextUtils.isEmpty(msgList.get(i).fromUID) && msgList.get(i).fromUID.equals(loginUID)) {
                        isAlertMsg = false;
                    }
                }
            }
            // 方案 B: 本批次有任一群成员关系变更 → 触发一次 memberships 增量刷新.
            // 放在 for 循环外, 多个事件汇聚成一次 sync; SpaceSyncCoordinator 进一步
            // 做 500ms per-path debounce, 突发场景 (一次拉进多个群) 不会发多次请求.
            if (needsMembershipRefresh) {
                triggerSpaceMembershipsRefresh();
            }
            boolean isVibrate = true;
            boolean playNewMsgMedia = true;
            boolean newMsgNotice = true;
            UserInfoSetting setting = WKConfig.getInstance().getUserInfo().setting;
            int msgShowDetail = 1;
            if (setting != null) {
                msgShowDetail = setting.msg_show_detail;
                if (setting.new_msg_notice == 0) {
                    newMsgNotice = false;
                    playNewMsgMedia = false;
                    isVibrate = false;
                } else {
                    if (setting.voice_on == 0) {
                        playNewMsgMedia = false;
                    }
                    if (setting.shock_on == 0) {
                        isVibrate = false;
                    }
                }
            }
            // Space 过滤：消息不属于当前 Space 时不触发通知
            String currentSpaceId = MsgModel.getInstance().getCurrentSpaceId();
            if (isAlertMsg && !TextUtils.isEmpty(currentSpaceId)) {
                // 群聊：通过 Space 会话白名单判断（群消息通常不携带 space_id）
                if (channelType == WKChannelType.GROUP
                        && !WKUIKitApplication.getInstance().isInCurrentSpace(channelID, channelType)) {
                    isAlertMsg = false;
                }
                // 子区：检查父群是否在当前 Space（对齐 iOS isMessageInCurrentSpace）
                if (isAlertMsg && channelType == WKChannelType.COMMUNITY_TOPIC) {
                    String[] parsed = com.chat.uikit.thread.service.ThreadModel.getInstance().parseChannelId(channelID);
                    if (parsed != null && !WKUIKitApplication.getInstance().isInCurrentSpace(parsed[0], WKChannelType.GROUP)) {
                        isAlertMsg = false;
                    }
                }
                // 私聊：通过消息内容中的 space_id 判断
                if (isAlertMsg && channelType == WKChannelType.PERSONAL) {
                    WKMsg lastMsg = msgList.get(msgList.size() - 1);
                    String msgSpaceId = extractSpaceId(lastMsg);
                    if (msgSpaceId != null && !msgSpaceId.equals(currentSpaceId)) {
                        isAlertMsg = false;
                    }
                }
            }

            if (newMsgNotice && isAlertMsg && (TextUtils.isEmpty(WKUIKitApplication.getInstance().chattingChannelID) || !WKUIKitApplication.getInstance().chattingChannelID.equals(channelID))) {
                WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelID, channelType);
                if (channel != null && channel.mute == 0) {
                    showNotification(msgList.get(msgList.size() - 1), msgShowDetail, channel, playNewMsgMedia, isVibrate);
                }
            }

            assert msgList != null;
        });
        WKIM.getInstance().getMsgManager().addOnUploadMsgExtraListener(msgExtra -> {
            WKMsg msg = WKIM.getInstance().getMsgManager().getWithMessageID(msgExtra.messageID);
            int msgSeq = 0;
            if (msg != null) {
                msgSeq = msg.messageSeq;
            }
            MsgModel.getInstance().editMsg(msgExtra.messageID, msgSeq, msgExtra.channelID, msgExtra.channelType, msgExtra.contentEdit, null);
        });

        /*
         * 设置获取频道信息的监听
         */
        WKIM.getInstance().getChannelManager().addOnGetChannelInfoListener((channelId, channelType, iChannelInfoListener) -> {
            if (channelType == WKChannelType.COMMUNITY_TOPIC) {
                String[] parsed = ThreadModel.getInstance().parseChannelId(channelId);
                if (parsed != null) {
                    ThreadModel.getInstance().getThreadDetail(parsed[0], parsed[1], (code, msg, entity) -> {
                        if (code == HttpResponseCode.success && entity != null) {
                            WKChannel channel = new WKChannel();
                            channel.channelID = channelId;
                            channel.channelType = WKChannelType.COMMUNITY_TOPIC;
                            channel.channelName = entity.name;
                            channel.status = WKChannelStatus.statusNormal;
                            HashMap<String, Object> extraMap = new HashMap<>();
                            extraMap.put("parentGroupNo", entity.group_no);
                            extraMap.put("threadStatus", entity.status);
                            extraMap.put("creatorUid", entity.creator_uid);
                            extraMap.put("shortId", entity.short_id);
                            extraMap.put("has_thread_md", entity.has_thread_md);
                            extraMap.put("thread_md_version", entity.thread_md_version);
                            channel.remoteExtraMap = extraMap;
                            WKIM.getInstance().getChannelManager().saveOrUpdateChannel(channel);
                        }
                    });
                }
            } else {
                WKCommonModel.getInstance().getChannel(channelId, channelType, null);
            }
            return null;
        });
        WKIM.getInstance().getChannelMembersManager().addOnGetChannelMembersListener((channelID, b, keyword, page, limit, iChannelMemberListResult) -> GroupModel.getInstance().getChannelMembers(channelID, keyword, page, limit, iChannelMemberListResult));
        /*
         * 获取频道成员
         */
        WKIM.getInstance().getChannelMembersManager().addOnGetChannelMemberListener((channelId, channelType, uid, iChannelMemberInfoListener) -> {
            WKCommonModel.getInstance().getChannel(uid, WKChannelType.PERSONAL, (code, msg, entity) -> {
                WKChannelMember channelMember = new WKChannelMember();
                channelMember.memberName = entity.name;
                channelMember.memberUID = entity.channel.channel_id;
                channelMember.channelID = channelId;
                channelMember.channelType = channelType;
                WKIM.getInstance().getChannelMembersManager().refreshChannelMemberCache(channelMember);
                iChannelMemberInfoListener.onResult(channelMember);
            });
            return null;
        });

        //监听频道修改头像
        WKIM.getInstance().getChannelManager().addOnRefreshChannelAvatar((s, b) -> {
            // 头像需要本地修改
            String key = UUID.randomUUID().toString().replace("-", "");
            AvatarView.clearCache(s, b);
            WKIM.getInstance().getChannelManager().updateAvatarCacheKey(s, b, key);
        });
        //刷新群成员
        WKIM.getInstance().getChannelMembersManager().addOnSyncChannelMembers((channelID, channelType) -> {
            if (!TextUtils.isEmpty(channelID)) {
                if (channelType == WKChannelType.GROUP) {
                    GroupModel.getInstance().groupMembersSync(channelID, null);
                } else if (channelType == WKChannelType.COMMUNITY_TOPIC) {
                    ThreadModel.getInstance().syncThreadMembers(channelID, null);
                }
            }
        });

        //  · Fix B Step 2：老用户外部群 extra 字段回填迁移。
        // 安装了本修复 APK 的老用户首次启动时跑一次，把所有 GROUP 会话的 member
        // sync 重跑一遍（Step 1 判定内部会决定是走增量还是强制全量）。幂等 —— SharedPreferences
        // per-uid 标记完成后再次启动立即返回。新用户冷启动时 groupNos 为空，直接 mark done。
        GroupModel.getInstance().runExternalFieldsMigrationIfNeeded();

        WKIM.getInstance().getCMDManager().addCmdListener("system", cmd -> {
            if (!TextUtils.isEmpty(cmd.cmdKey)) {
                switch (cmd.cmdKey) {
                    case WKCMDKeys.wk_messageRevoke -> revokeMsg(cmd.paramJsonObject);
                    case WKCMDKeys.wk_friendRequest ->
                            FriendModel.getInstance().saveNewFriendsMsg(cmd.paramJsonObject.toString());
                    case WKCMDKeys.wk_friendDeleted, WKCMDKeys.wk_friendAccept -> {
                        FriendModel.getInstance().syncFriends(null);
                        if (cmd.cmdKey.equals(WKCMDKeys.wk_friendAccept)
                                && cmd.paramJsonObject != null && cmd.paramJsonObject.has("to_uid")) {
                            String uid = cmd.paramJsonObject.optString("to_uid");
                            WKContactsDB.getInstance().updateFriendStatus(uid, 1);
                            NewFriendEntity entity = ApplyDB.getInstance().query(uid);
                            if (entity != null && entity.status == 0) {
                                entity.status = 1;
                                ApplyDB.getInstance().update(entity);
                            }
                        }
                    }
                    case WKCMDKeys.wk_sync_message_extra -> {
                        if (BuildConfig.DEBUG) android.util.Log.d("CardFrameDebug",
                                "[CMD recv] wk_sync_message_extra param=" + (cmd.paramJsonObject == null ? "null" : cmd.paramJsonObject.toString()));
                        if (cmd.paramJsonObject == null) {
                            return;
                        }
                        String channelID = cmd.paramJsonObject.optString("channel_id");
                        byte channelType = (byte) cmd.paramJsonObject.optInt("channel_type");
                        if (TextUtils.isEmpty(channelID)) {
                            return;
                        }
                        MsgModel.getInstance().syncExtraMsg(channelID, channelType);
                        // type=17 交互卡终态帧补偿：extra/sync 增量游标对非单调 version 会永久跳过
                        // 低版本终态帧（已实测），故对待补偿卡片额外走游标免疫的 message/channel/sync
                        // 精确补拉。仅作用于已登记的处理中卡片，不波及其它消息；节流 + 上限见 MsgModel。
                        MsgModel.getInstance().refreshPendingCards(channelID, channelType);
                    }
                    case WKCMDKeys.wk_memberUpdate -> {
                        if (cmd.paramJsonObject == null) {
                            return;
                        }
                        String groupNo = cmd.paramJsonObject.optString("group_no");
                        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(groupNo, WKChannelType.GROUP);
                        if (channel == null || channel.remoteExtraMap == null) {
                            return;
                        }
                        Object groupTypeObject = channel.remoteExtraMap.get(WKChannelExtras.groupType);
                        if (groupTypeObject instanceof Integer) {
                            int groupType = (int) groupTypeObject;
                            if (groupType == WKGroupType.superGroup) {
                                String uid = cmd.paramJsonObject.optString("uid");
                                if (!TextUtils.isEmpty(uid)) {
                                    UserModel.getInstance().getUserInfo(uid,groupNo,null);
                                }
                            }
                        }
                    }
                    case WKCMDKeys.wk_sync_reminders -> MsgModel.getInstance().syncReminder();
                    case WKCMDKeys.wk_sync_conversation_extra ->
                            MsgModel.getInstance().syncCoverExtra();
                }
            }
        });
    }

    public WKUIChatMsgItemEntity msg2UiMsg(IConversationContext context, WKMsg msg, int memberCount, boolean showNickName, boolean isChoose) {
        if (msg.remoteExtra.readedCount == 0) {
            msg.remoteExtra.unreadCount = memberCount - 1;
        }
        if (msg.type == WKContentType.WK_TEXT) {
//            WKTextContent textContent = (WKTextContent) msg.baseContentMsgModel;
//            if (textContent != null && !TextUtils.isEmpty(textContent.getDisplayContent())) {
//                List<String> urls = StringUtils.getStrUrls(textContent.getDisplayContent());
//                if (urls.size() > 0) {
//                    String url = urls.get(urls.size() - 1);
//                    String contentJson = WKSharedPreferencesUtil.getInstance().getSP(url);
//                    if (!TextUtils.isEmpty(contentJson)) {
//                        try {
//                            JSONObject jsonObject = new JSONObject(contentJson);
//                            long expirationTime = jsonObject.optLong("expirationTime");
//                            long tempTime = WKTimeUtils.getInstance().getCurrentSeconds() - expirationTime;
//                            if (tempTime >= 60 * 60 * 24 * 360) {
//                                WKJsoupUtils.getInstance().getURLContent(url, msg.clientMsgNO);
//                            }
//                        } catch (JSONException e) {
//                            e.printStackTrace();
//                        }
//                    } else {
//                        WKJsoupUtils.getInstance().getURLContent(url, msg.clientMsgNO);
//                    }
//                }
//
//            }
            resetMsgProhibitWord(msg);
        }
        WKUIChatMsgItemEntity uiChatMsgItemEntity = new WKUIChatMsgItemEntity(context, msg, new WKUIChatMsgItemEntity.ILinkClick() {
            @Override
            public void onShowUserDetail(String uid, String groupNo) {
                Intent intent = new Intent(context.getChatActivity(), UserDetailActivity.class);
                intent.putExtra("uid", uid);
                if (!TextUtils.isEmpty(groupNo)) {
                    intent.putExtra("groupID", groupNo);
                }
                context.getChatActivity().startActivity(intent);
            }

            @Override
            public void onShowSearchUser(String phone) {
                Intent intent = new Intent(context.getChatActivity(), SearchUserActivity.class);
                intent.putExtra("phone", phone);
                context.getChatActivity().startActivity(intent);
            }
        });
        uiChatMsgItemEntity.wkMsg = msg;
        uiChatMsgItemEntity.isChoose = isChoose;
        uiChatMsgItemEntity.showNickName = showNickName;

        // 计算气泡类型
        return uiChatMsgItemEntity;
    }

    public void resetMsgProhibitWord(WKMsg msg) {
        if (msg == null || msg.type != WKContentType.WK_TEXT) {
            return;
        }
        List<ProhibitWord> list = ProhibitWordModel.Companion.getInstance().getAll();
        if (WKReader.isNotEmpty(list)) {
            String content = getContent(msg);
            for (ProhibitWord word : list) {
                if (content.contains(word.content)) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < word.content.length(); i++) {
                        sb.append("*");
                    }
                    content = content.replace(word.content, sb.toString());
                }
            }

            if (msg.remoteExtra.contentEditMsgModel != null && !TextUtils.isEmpty(msg.remoteExtra.contentEditMsgModel.getDisplayContent())) {
                msg.remoteExtra.contentEditMsgModel.content = content;
            } else {
                msg.baseContentMsgModel.content = content;
            }
        }
    }

    private String getContent(WKMsg msg) {
        String showContent = msg.baseContentMsgModel.getDisplayContent();
        if (msg.remoteExtra.contentEditMsgModel != null && !TextUtils.isEmpty(msg.remoteExtra.contentEditMsgModel.getDisplayContent())) {
            showContent = msg.remoteExtra.contentEditMsgModel.getDisplayContent();
        }
        return showContent;
    }


    public void revokeMsg(JSONObject jsonObject) {
        //撤回消息
        if (jsonObject != null) {
            if (jsonObject.has("message_id")) {
                String messageId = jsonObject.optString("message_id");
                String channelID = jsonObject.optString("channel_id");
                byte channelType = (byte) jsonObject.optInt("channel_type");
                if (BuildConfig.DEBUG) android.util.Log.d("RevokeDebug", "[revokeMsg] CMD received: messageId=" + messageId + " channelID=" + channelID + " channelType=" + channelType);
                // 撤回消息涉及多次 DB 读写操作，放 IO 线程避免和 sync 争抢数据库锁导致 ANR
                com.chat.base.utils.WKDbScheduler.submit(() -> {
                    WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelID, channelType);
                    //是否撤回提醒
                    int revokeRemind = 1;
                    if (channel != null && channel.remoteExtraMap != null && channel.remoteExtraMap.containsKey(WKChannelExtras.revokeRemind)) {
                        Object object = channel.remoteExtraMap.get(WKChannelExtras.revokeRemind);
                        if (object != null) {
                            revokeRemind = (int) object;
                        }
                    }
                    if (BuildConfig.DEBUG) android.util.Log.d("RevokeDebug", "[revokeMsg] revokeRemind=" + revokeRemind + " channelID=" + channelID);
                    if (revokeRemind == 1) {
                        if (BuildConfig.DEBUG) android.util.Log.d("RevokeDebug", "[revokeMsg] calling syncExtraMsg for channelID=" + channelID);
                        MsgModel.getInstance().syncExtraMsg(channelID, channelType);
                        // 竞态修复：服务端撤回操作 + 写 extra 增量 + 推 CMD 三步不是严格原子的，
                        // CMD 常常先于 extra 增量写入到达客户端。此时立即调 syncExtraMsg 会拿到
                        // resultSize=0（服务端还没有增量可给），本地永远不知道消息已撤回 →
                        // 表现为"撤回没反应 / 要撤好几次才成功"（碰运气：偶尔 extra 已就绪则成功）。
                        // 分别在 500ms / 1500ms 再拉一次，每次先查本地 msg.remoteExtra.revoke
                        // 是否已 = 1（前一次拉已生效），已生效则跳过（幂等，避免多余请求）。
                        scheduleRevokeRetry(messageId, channelID, channelType, 1, 500);
                        scheduleRevokeRetry(messageId, channelID, channelType, 2, 1500);
                    } else {
                        WKMsg wkMsg = WKIM.getInstance().getMsgManager().getWithMessageID(messageId);
                        if (wkMsg != null) {
                            List<WKMsg> list = new ArrayList<>();
                            list.add(wkMsg);
                            MsgModel.getInstance().deleteMsg(list, null);
                        }

                        int rowNo = WKIM.getInstance().getMsgManager().getRowNoWithMessageID(channelID, channelType, messageId);
                        WKIM.getInstance().getMsgManager().deleteWithMessageID(messageId);
                        WKConversationMsg msg = WKIM.getInstance().getConversationManager().getWithChannel(channelID, channelType);
                        if (msg != null) {
                            if (rowNo < msg.unreadCount) {
                                msg.unreadCount--;
                            }
                            WKIM.getInstance().getConversationManager().updateWithMsg(msg);
                        }
                    }
                });
            }
        }
    }

    /**
     * 撤回 CMD 时序竞态兜底：延迟 {@code delayMs} 后如果本地目标 msg 尚未 mark 撤回，
     * 再调一次 {@link MsgModel#syncExtraMsg}。查本地状态确保幂等，避免无谓请求。
     */
    private void scheduleRevokeRetry(String messageId, String channelID, byte channelType, int attempt, long delayMs) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            com.chat.base.utils.WKDbScheduler.submit(() -> {
                WKMsg local = WKIM.getInstance().getMsgManager().getWithMessageID(messageId);
                boolean alreadyRevoked = local != null && local.remoteExtra != null && local.remoteExtra.revoke == 1;
                if (BuildConfig.DEBUG) android.util.Log.d("RevokeDebug",
                        "[revokeMsg] retry attempt=" + attempt + " messageId=" + messageId
                                + " alreadyRevoked=" + alreadyRevoked);
                if (!alreadyRevoked) {
                    MsgModel.getInstance().syncExtraMsg(channelID, channelType);
                }
            });
        }, delayMs);
    }

    /**
     * 对齐 iOS 自己撤回架构：{@code POST /message/revoke} HTTP 200 后立即本地 mark
     * 目标消息 {@code remoteExtra.revoke=1, revoker=self.uid}，然后触发 UI 刷新。
     *
     * <p>为什么不"欺骗自己"：服务端 HTTP 200 已确认撤回成功，服务端也会推 CMD
     * 给其它设备 / 其它用户，全局状态一致。本地 mark 只是**同步这个已知事实**到
     * 当前设备 UI —— 对齐 iOS WKMessageManagerDelegateImp.m:137-138 的做法。
     *
     * <p>为什么必须做：服务端 syncMessageExtra 的 cache 逻辑（api.go:1258-1268）
     * 可能返回 resultSize=0（用户实测 6 次撤回 4 次踩坑），只依赖 CMD → sync 路径
     * 会永久卡住。iOS 通过"HTTP 200 立即本地 mark"绕过整个 sync 依赖。
     *
     * <p>用 clientMsgNo 查本地 msg（比传入 messageId 更稳，处理 messageID 尚未
     * 从 IM ack 回填的场景）。若本地 msgID 仍为空则跳过（此消息扩展表按 messageID
     * 索引，无法写入）—— 交给后续 IM ack + CMD 兜底。
     *
     * <p>他人撤回场景仍走 CMD → syncExtraMsg 路径（保留 [scheduleRevokeRetry] 兜底）。
     */
    public void markMsgRevokedLocallyByClientMsgNO(String clientMsgNo, String revoker) {
        if (android.text.TextUtils.isEmpty(clientMsgNo)) return;
        com.chat.base.utils.WKDbScheduler.submit(() -> {
            WKMsg wkMsg = WKIM.getInstance().getMsgManager().getWithClientMsgNO(clientMsgNo);
            if (wkMsg == null) {
                if (BuildConfig.DEBUG) android.util.Log.w("RevokeDebug",
                        "[markLocal] msg not found for clientMsgNo=" + clientMsgNo);
                return;
            }
            String messageId = wkMsg.messageID;
            if (android.text.TextUtils.isEmpty(messageId) || "0".equals(messageId)) {
                // 已知边界（非主 66% miss 场景）：仅"消息刚发出、IM ack 还没回填 messageID"
                // 的极小窗口才会命中。此分支跳过本地 mark（extra 表按 messageID 索引，
                // 没有 messageID 就没法写），实际结果 = 只走服务端 CMD → syncExtraMsg
                // 兜底。因为 HTTP revoke 本身已用 clientMsgNO 成功（服务端反查），全局
                // 撤回一致；只是当前设备的 UI 会退回到"等 CMD"这条老路径。
                // 需要覆盖到这个窗口，需要引入"按 clientMsgNO 索引的 pending revoke 表"，
                // 待后续独立优化。
                if (BuildConfig.DEBUG) android.util.Log.w("RevokeDebug",
                        "[markLocal] messageID not yet filled (=" + messageId + ") for clientMsgNo=" + clientMsgNo
                                + " — skip local mark, rely on CMD retry");
                return;
            }

            WKSyncExtraMsg syncExtra = new WKSyncExtraMsg();
            syncExtra.message_id = messageId;
            syncExtra.revoke = 1;
            syncExtra.revoker = revoker;
            // 保留其它 remoteExtra 字段避免 insertOrReplace 覆盖丢失 (readed/pinned/editedAt 等)。
            if (wkMsg.remoteExtra != null) {
                syncExtra.readed = wkMsg.remoteExtra.readed;
                syncExtra.readed_count = wkMsg.remoteExtra.readedCount;
                syncExtra.unread_count = wkMsg.remoteExtra.unreadCount;
                syncExtra.is_mutual_deleted = wkMsg.remoteExtra.isMutualDeleted;
                syncExtra.is_pinned = wkMsg.remoteExtra.isPinned;
                syncExtra.extra_version = wkMsg.remoteExtra.extraVersion;
                syncExtra.edited_at = wkMsg.remoteExtra.editedAt;
            }
            List<WKSyncExtraMsg> list = new ArrayList<>();
            list.add(syncExtra);
            WKChannel channel = new WKChannel();
            channel.channelID = wkMsg.channelID;
            channel.channelType = wkMsg.channelType;
            WKIM.getInstance().getMsgManager().saveRemoteExtraMsg(channel, list);

            if (BuildConfig.DEBUG) android.util.Log.d("RevokeDebug",
                    "[markLocal] revoke=1 marked messageID=" + messageId
                            + " clientMsgNo=" + clientMsgNo + " revoker=" + revoker);
        });
    }


    /**
     * 显示聊天
     *
     * @param chatViewMenu 参数
     */
    public void startChatActivity(ChatViewMenu chatViewMenu) {
        if (chatViewMenu == null || chatViewMenu.activity == null || TextUtils.isEmpty(chatViewMenu.channelID)) {
            return;
        }
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(chatViewMenu.channelID, chatViewMenu.channelType);
        int chatPwdON = 0;
        if (channel != null && channel.remoteExtraMap != null && channel.remoteExtraMap.containsKey(WKChannelExtras.chatPwdOn)) {
            Object object = channel.remoteExtraMap.get(WKChannelExtras.chatPwdOn);
            if (object instanceof Integer) {
                chatPwdON = (int) object;
            }
        }
        if (chatPwdON == 1) {
            showChatPwdDialog(chatViewMenu, channel);
            return;
        }
        startChat(chatViewMenu);
    }

    private void startChat(ChatViewMenu chatViewMenu) {
        // : removed WKTimeUtils.isFastDoubleClick() pre-check. The global
        // static throttle on top of the per-view SingleClickUtil throttle caused
        // first-tap-eaten bugs when switching between chats in TabActivity. View
        // level throttle in SingleClickUtil is sufficient.
        //
        // : moved all DB-heavy work (deleteFlameMsg, getWithChannel,
        // getWithClientMsgNO, findLatestMsgForSpace, getMessageOrderSeq) off the
        // main thread. startChat now assembles the Intent on an IO worker and
        // switches back to the main thread only for startActivity. This keeps
        // the click handler non-blocking (target <20ms) so the UI thread does
        // not freeze when a DB transaction happens to hold the write lock.
        if (chatViewMenu == null || chatViewMenu.activity == null
                || TextUtils.isEmpty(chatViewMenu.channelID)) {
            return;
        }
        //  P1-4: narrow global debounce at the startChat entry. Per-view
        // SingleClickUtil (300ms) gates the same row but does not protect
        // cross-row taps (row A → row B in <300ms). Without this, the Fix 3
        // async Observables stage in parallel and both startActivity() run,
        // stacking the back stack as [tabs, A, B]. CAS ensures only one
        // concurrent caller wins the window.
        long now = SystemClock.uptimeMillis();
        long prev = lastStartChatMs.get();
        if (now - prev < START_CHAT_DEBOUNCE_MS) {
            return;
        }
        if (!lastStartChatMs.compareAndSet(prev, now)) {
            return;
        }
        //  P2-4：T_CLICK 必须打在 debounce **之后**。打在 debounce 前的话，
        // 跨行快点（A→B <250ms）场景会多出一条被 debounce 丢弃的 T_CLICK，没
        // 对应的 T_START_ACTIVITY，统计时分母偏大、P50/P90 被拉低，误导选型。
        // 这里 tClickMs 作为「真正进入 startChat 流程」的 t0，所有后续阶段
        // 的 delta 都以它为基准。
        final long tClickMs = SystemClock.uptimeMillis();
        if (WKBinder.isDebug) {
            Log.d(TRACE_TAG, "[T_CLICK] startChat enter channel=" + chatViewMenu.channelID
                    + " type=" + chatViewMenu.channelType);
        }
        //  · Fix C：点击瞬间（debounce 通过后）就把全局 chattingChannelID 切
        // 到目标 channel，不等 onResume。对齐 push 通知去重 / Space 上下文等依赖该
        // 字段的逻辑——即使 Fix B 的 Activity 复用/新建还在走 IO 组装 Intent，
        // 状态已经切过去。放在 debounce 之后避免「B 被 debounce 挡住 → chatting
        // 仍指 A 生效开启 → chattingChannelID 却被误改成 B」的不一致。
        WKUIKitApplication.getInstance().chattingChannelID = chatViewMenu.channelID;
        // Fire-and-forget: deleteFlameMsg is a DB write and is independent from
        // the Intent assembly below. No need to block chat open on it.
        Observable.fromCallable(() -> {
                    MsgModel.getInstance().deleteFlameMsg();
                    return true;
                })
                .subscribeOn(Schedulers.io())
                .subscribe(v -> { },
                        err -> WKLogUtils.e(TAG, "startChat deleteFlameMsg failed: " + err));

        final ChatViewMenu menu = chatViewMenu;
        Observable.fromCallable(() -> {
                    long ioStart = SystemClock.uptimeMillis();
                    Intent i = buildStartChatIntent(menu);
                    if (WKBinder.isDebug) {
                        Log.d(TRACE_TAG, "[T_INTENT_BUILT] io=" + (SystemClock.uptimeMillis() - ioStart)
                                + "ms sinceClick=" + (SystemClock.uptimeMillis() - tClickMs) + "ms");
                    }
                    return i;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(intent -> {
                    if (intent == null) return;
                    if (menu.activity == null || menu.activity.isFinishing()
                            || menu.activity.isDestroyed()) {
                        return;
                    }
                    if (WKBinder.isDebug) {
                        Log.d(TRACE_TAG, "[T_START_ACTIVITY] sinceClick="
                                + (SystemClock.uptimeMillis() - tClickMs) + "ms");
                    }
                    //  · Fix A：窄屏路径上走 ChatReuseNavigator，把
                    // FLAG_ACTIVITY_REORDER_TO_FRONT | FLAG_ACTIVITY_SINGLE_TOP 合并进
                    // Intent。任务栈里若已有 ChatActivity 实例（被 goBackToList 留在
                    // 栈中），AMS 会 reorder 到栈顶并走 onNewIntent 热路径（~50-100ms
                    // 切频道），没有实例则正常新建（和 PR#195 冷启动一致）。
                    // 分屏态 isNarrow=false，此处不加 flag，由 Activity Embedding 的
                    // onNewIntent（）负责。
                    //  P1-1：Fix D 的 overridePendingTransition 调用已下沉到
                    // ChatActivity.onCreate（见 com.chat.base.foldable.NarrowTransition.
                    // applyFastOpen）。这样子区卡片、SearchAllActivity、CreateThreadActivity
                    // 等直接 startActivity(ChatActivity) 的路径也能吃到 120ms 快过渡，
                    // 不再和此处 helper 耦合。
                    com.chat.uikit.chat.ChatReuseNavigator.launchChat(
                            menu.activity, intent, menu.activity);
                }, err -> {
                    //  P2-1: surface startChat errors instead of
                    // silently swallowing them — a dropped Intent build used
                    // to look like a first-tap-eaten UX bug.
                    WKLogUtils.e(TAG, "startChat buildIntent failed: " + err);
                });
    }

    /**
     * Assembles the Intent for ChatActivity on a worker thread. All DB reads
     * (conversation, latest msg, message-seq → order-seq lookups) happen here
     * so they do not block the UI thread that just handled the click.
     */
    private Intent buildStartChatIntent(ChatViewMenu chatViewMenu) {
        Intent intent = new Intent(chatViewMenu.activity, ChatActivity.class);
        intent.putExtra("channelId", chatViewMenu.channelID);
        intent.putExtra("channelType", chatViewMenu.channelType);
        WKConversationMsg conversationMsg = WKIM.getInstance().getConversationManager()
                .getWithChannel(chatViewMenu.channelID, chatViewMenu.channelType);
        WKMsg msg = null;
        int redDot = 0;
        long aroundMsgSeq = 0;
        if (conversationMsg != null) {
            redDot = conversationMsg.unreadCount;
            msg = WKIM.getInstance().getMsgManager().getWithClientMsgNO(conversationMsg.lastClientMsgNO);
            // Space 过滤：系统 Bot 最后一条消息不属于当前 Space 时的处理
            String currentSpaceId = MsgModel.getInstance().getCurrentSpaceId();
            if (!TextUtils.isEmpty(currentSpaceId) && msg != null) {
                String msgSpaceId = extractSpaceId(msg);
                if (msgSpaceId != null && !msgSpaceId.equals(currentSpaceId)) {
                    redDot = 0;
                    // 系统 Bot（如 BotFather）：找到当前 Space 的最新消息作为起始位置
                    if (com.chat.base.space.SystemBotsFallback.isSystemBot(chatViewMenu.channelID)) {
                        WKMsg spaceMsg = findLatestMsgForSpace(chatViewMenu.channelID, chatViewMenu.channelType, currentSpaceId);
                        if (spaceMsg != null) {
                            msg = spaceMsg;
                        }
                    }
                }
            }
            if (msg != null) {
                aroundMsgSeq = msg.orderSeq;
            }
        }
        if (chatViewMenu.tipMsgOrderSeq != 0) {
            // 强提醒某条消息
            intent.putExtra("tipsOrderSeq", chatViewMenu.tipMsgOrderSeq);
        } else {
            if (redDot > 0) {
                long orderSeq;
                int messageSeq = 0;
                if (msg != null) {
                    if (msg.messageSeq == 0) {
                        int maxMsgSeq = WKIM.getInstance().getMsgManager().getMaxMessageSeqWithChannel(chatViewMenu.channelID, chatViewMenu.channelType);
                        messageSeq = maxMsgSeq - redDot + 1;
                    } else {
                        messageSeq = msg.messageSeq - redDot + 1;
                    }
                    if (messageSeq <= 0) {
                        messageSeq = WKIM.getInstance().getMsgManager().getMinMessageSeqWithChannel(chatViewMenu.channelID, chatViewMenu.channelType);
                    }
                }
                orderSeq = WKIM.getInstance().getMsgManager().getMessageOrderSeq(messageSeq, chatViewMenu.channelID, chatViewMenu.channelType);
                intent.putExtra("unreadStartMsgOrderSeq", orderSeq);
                intent.putExtra("redDot", redDot);
            }
        }
        if (chatViewMenu.isNewTask) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        if (WKReader.isNotEmpty(chatViewMenu.forwardMsgList)) {
            intent.putParcelableArrayListExtra("msgContentList", (ArrayList<? extends Parcelable>) chatViewMenu.forwardMsgList);
        }
        intent.putExtra("aroundMsgSeq", aroundMsgSeq);
        return intent;
    }

    private void showChatPwdDialog(ChatViewMenu chatViewMenu, WKChannel channel) {
        NumPwdDialog.getInstance().showNumPwdDialog(chatViewMenu.activity, chatViewMenu.activity.getString(R.string.chat_pwd), chatViewMenu.activity.getString(R.string.input_chat_pwd), channel.channelName, new NumPwdDialog.IPwdInputResult() {
            @Override
            public void onResult(String numPwd) {

                if (!WKCommonUtils.digest(numPwd + WKConfig.getInstance().getUid()).equals(WKConfig.getInstance().getUserInfo().chat_pwd)) {
                    int chatPwdCount = WKSharedPreferencesUtil.getInstance().getInt("wk_chat_pwd_count", 3);
                    if (chatPwdCount == 0) {
                        // 清空聊天记录
                        WKSharedPreferencesUtil.getInstance().putInt("wk_chat_pwd_count", 0);
                        WKIM.getInstance().getMsgManager().clearWithChannel(channel.channelID, channel.channelType);
                        WKToastUtils.getInstance().showToastNormal(chatViewMenu.activity.getString(R.string.chat_msg_is_cleard));
                        return;
                    }

                    String content = String.format(chatViewMenu.activity.getString(R.string.forget_chat_pwd), chatPwdCount, chatPwdCount);
                    WKDialogUtils.getInstance().showDialog(chatViewMenu.activity, chatViewMenu.activity.getString(R.string.chat_pwd_error), content, false, chatViewMenu.activity.getString(R.string.cancel), chatViewMenu.activity.getString(R.string.chat_pwd_reset_pwd), 0, Theme.colorAccount, index -> {
                        if (index == 1) {
                            EndpointManager.getInstance().invoke("show_set_chat_pwd", null);
                        }
                    });
                    WKSharedPreferencesUtil.getInstance().putInt("wk_chat_pwd_count", --chatPwdCount);
                } else {
                    WKSharedPreferencesUtil.getInstance().putInt("wk_chat_pwd_count", 3);
                    startChat(chatViewMenu);
                }

            }

            @Override
            public void forgetPwd() {
                EndpointManager.getInstance().invoke("show_set_chat_pwd", null);
            }
        });

    }


    private void showNotification(WKMsg msg, int msgShowDetail, WKChannel channel, boolean playNewMsgMedia, boolean isVibrate) {
        int msgNotice = WKConfig.getInstance().getUserInfo().setting.new_msg_notice;
        if (msgNotice == 0) {
            return;
        }
//        Activity activity = ActManagerUtils.getInstance().getCurrentActivity();
//        if (activity == null || activity.getComponentName().getClassName().equals(TabActivity.class.getName())) {
        if (playNewMsgMedia) {
            defaultMediaPlayer();
        }
        if (isVibrate) {
            vibrate();
        }
//            return;
//        }
        String showTitle = TextUtils.isEmpty(channel.channelRemark) ? channel.channelName : channel.channelRemark;
        String showContent = WKBaseApplication.getInstance().getContext().getString(R.string.default_new_msg);
        if (msgShowDetail == 1 && msg.baseContentMsgModel != null && !TextUtils.isEmpty(msg.baseContentMsgModel.getDisplayContent())) {
            showContent = msg.baseContentMsgModel.getDisplayContent();
        }
//        String url;
//        if (!TextUtils.isEmpty(channel.avatar) && channel.avatar.contains("/")) {
//            url = WKApiConfig.getShowUrl(channel.avatar);
//        } else {
//            url = WKApiConfig.getShowAvatar(channel.channelID, channel.channelType);
//        }
//        String finalShowContent = showContent;
//        if (isVibrate) {
//            PushNotificationHelper.INSTANCE.notifyMention(WKUIKitApplication.getInstance().getContext(), 1, showTitle, showContent);
//        } else {
        PushNotificationHelper.INSTANCE.notifyMessage(WKUIKitApplication.getInstance().getContext(), 1, showTitle, showContent);
//        }
//        showNotice(showTitle, finalShowContent, null, isVibrate);
//        getChannelLogo(url, activity, logo -> showNotice(showTitle, finalShowContent, logo, isVibrate));
    }


    private void defaultMediaPlayer() {
        EndpointManager.getInstance().invoke("play_new_msg_Media", null);
    }

    private void vibrate() {
        Vibrator vibrator = (Vibrator) WKUIKitApplication.getInstance().getContext().getSystemService(Service.VIBRATOR_SERVICE);
        long[] pattern = {100, 200};
        vibrator.vibrate(pattern, -1);
    }

    public void removeListener() {
        WKIM.getInstance().getCMDManager().removeCmdListener("system");
        WKIM.getInstance().getMsgManager().removeNewMsgListener("system");
    }

    /**
     * 在本地 DB 中搜索指定 Space 的最新消息。
     * 用于系统 Bot（BotFather）跨 Space 共享场景，确保聊天窗口从正确的位置加载。
     */
    private WKMsg findLatestMsgForSpace(String channelID, byte channelType, String spaceId) {
        try {
            List<WKMsg> msgs = WKIM.getInstance().getMsgManager()
                    .searchMsgWithChannelAndContentTypes(channelID, channelType, 0, 500, null);
            if (msgs != null) {
                for (WKMsg m : msgs) {
                    String sid = extractSpaceId(m);
                    if (sid == null || spaceId.equals(sid)) {
                        return m;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 方案 B · 判定 1002/1003 群成员变更消息中 {@code extra} 字段是否包含登录用户的 uid.
     *
     * <p>JSON 结构 (server modules/group/event.go:645-651 构造, iOS WKSystemMessageCell.m:80-102 解析):
     * <pre>{@code
     * {
     *   "type": 1002,
     *   "content": "...",
     *   "extra": [{"uid": "u1", "name": "n1"}, ...]
     * }
     * }</pre>
     *
     * <p>用于判定 "群加人事件是不是把我自己加进去". 命中即说明我的 memberships
     * 发生变化, 需触发 conv/sync 刷新. 不命中说明是别的成员被加, 跳过 sync.
     *
     * <p>容错: msg/content/json 任一环节异常都返回 false, fallback 到不刷新——
     * 比 fail-open 漏过更糟的是误触发额外 sync, 但 false 路径下一次 1005 / cold-start
     * sync 也能补齐, 实际影响有限.
     */
    private boolean isMyUidInGroupMemberMsgExtra(WKMsg msg) {
        if (msg == null || TextUtils.isEmpty(msg.content)) return false;
        String myUid = WKConfig.getInstance().getUid();
        if (TextUtils.isEmpty(myUid)) return false;
        try {
            JSONObject json = new JSONObject(msg.content);
            JSONArray extra = json.optJSONArray("extra");
            if (extra == null) return false;
            for (int i = 0, n = extra.length(); i < n; i++) {
                JSONObject item = extra.optJSONObject(i);
                if (item == null) continue;
                if (myUid.equals(item.optString("uid"))) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // JSON 异常: payload 格式与预期不符, 保守返回 false.
            // 实际生产中 server 格式稳定, 走这条路径的概率近 0.
        }
        return false;
    }

    /**
     * 解析 1002 群加人消息 {@code content.extra[].uid} 列表.
     * 用于 C+ 兜底: 增量 sync 完成后校验这些 uid 是否落盘, 缺失即触发 v=0 全量.
     *
     * <p>返回空列表 (而不是 null) 便于调用方直接 {@code isEmpty()} 判定.
     * JSON 解析异常时同样返回空列表, 调用方应当把 "空" 视作 "无法确认已落盘"
     * 从而保守触发全量 (对齐 iOS 群设置页自愈的语义, 宁可多拉一次).
     */
    private java.util.List<String> parseGroupMemberAddExtraUids(WKMsg msg) {
        java.util.List<String> uids = new java.util.ArrayList<>();
        if (msg == null || TextUtils.isEmpty(msg.content)) return uids;
        try {
            JSONObject json = new JSONObject(msg.content);
            JSONArray extra = json.optJSONArray("extra");
            if (extra == null) return uids;
            for (int i = 0, n = extra.length(); i < n; i++) {
                JSONObject item = extra.optJSONObject(i);
                if (item == null) continue;
                String uid = item.optString("uid");
                if (!TextUtils.isEmpty(uid)) uids.add(uid);
            }
        } catch (Throwable ignored) {
        }
        return uids;
    }

    /**
     * 方案 B · 群成员关系变更后, 触发一次 conv/sync 增量刷新 space_memberships,
     * 让 SpaceFilter 在后续 realtime 消息到达时能给出权威空间归属判定,
     * 避免新加入群的首条消息撞 SpaceFilter fail-open 分支被错挂到当前 Space.
     *
     * <p>背景: server PR #154 把 conversation/sync 响应的 {@code space_memberships}
     * 设为 SpaceFilter 的权威源。客户端现有 3 个 conv/sync 触发点:
     * <ol>
     *   <li>冷启动 — WKConnection 连接成功后</li>
     *   <li>Space 切换 — {@code performSpaceSwitch}</li>
     *   <li>resync — {@code spaceResyncRunnable}</li>
     * </ol>
     * 但 "用户被加入新群" 不在任何一个触发点上 — 服务端发 {@code groupSystemInfo}
     * 后, 现有逻辑只触发 {@code groupMembersSync} (member 级), 不刷新 memberships
     * (space 级)。下一条该群消息到达时, SpaceFilter 4 个数据源全空 → fail-open →
     * 串台。
     *
     * <p>本方法补齐第 4 个触发点: "本地已知 membership 发生变化时"。
     *
     * <p>线程: 走 IO + SpaceSyncCoordinator (500ms per-path debounce, 与其他
     * sync 路径共享全局重入守卫), 一次拉多群的突发场景只发 1 次 sync.
     *
     * <p>过渡性: 若 server 后续在 realtime msg payload 里直接带 space_id, SDK
     * 收到消息时同步写 convSyncSpaceMap, SpaceFilter 直接拿到权威值, 本方法
     * 可整体删除. 在那之前, 这是客户端关闭 race window 的最经济方案.
     */
    private void triggerSpaceMembershipsRefresh() {
        if (!SpaceSyncCoordinator.getInstance().tryBegin("groupMembershipChange")) {
            // 已有 sync 在路上 / 500ms 内已触发过 → drop, 让前一次 sync 的结果覆盖
            return;
        }
        Schedulers.io().scheduleDirect(() -> {
            WKIM.getInstance().getConversationManager().setSyncConversationListener(result -> {
                SpaceSyncCoordinator.getInstance().complete();
            });
        });
    }

    /**
     * 为缺少 space_id 的系统 Bot 回复消息推断并填充当前 Space ID。
     * 对齐 iOS WKSystemMessageHandler.inferSpaceIdForBotMessage:
     * Bot 回复不带 space_id → 后向兼容逻辑导致所有 Space 可见 → 新 Space 看到旧消息。
     */
    private void inferSpaceIdForBotMessage(WKMsg msg) {
        if (msg == null) return;
        // 仅处理个人频道中的系统 Bot
        if (msg.channelType != WKChannelType.PERSONAL) return;
        if (!com.chat.base.space.SystemBotsFallback.isSystemBot(msg.channelID)) return;
        // 仅处理非自己发送的消息（Bot 回复）
        String loginUID = WKConfig.getInstance().getUid();
        if (!TextUtils.isEmpty(loginUID) && loginUID.equals(msg.fromUID)) return;
        // 已有 space_id 不覆盖
        if (extractSpaceId(msg) != null) return;
        // 用当前 Space ID 填充
        String currentSpaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (TextUtils.isEmpty(currentSpaceId)) return;
        try {
            // 填充到 msg.content JSON
            JSONObject json;
            if (!TextUtils.isEmpty(msg.content)) {
                json = new JSONObject(msg.content);
            } else {
                json = new JSONObject();
            }
            json.put("space_id", currentSpaceId);
            msg.content = json.toString();
            // 填充到 baseContentMsgModel
            if (msg.baseContentMsgModel != null) {
                msg.baseContentMsgModel.spaceId = currentSpaceId;
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 从消息中提取 space_id
     */
    private String extractSpaceId(WKMsg msg) {
        if (msg == null) return null;
        if (!TextUtils.isEmpty(msg.content)) {
            try {
                JSONObject json = new JSONObject(msg.content);
                String sid = json.optString("space_id", "");
                if (!sid.isEmpty()) return sid;
            } catch (Exception ignored) {
            }
        }
        if (msg.baseContentMsgModel != null) {
            try {
                JSONObject json = msg.baseContentMsgModel.encodeMsg();
                if (json != null) {
                    String sid = json.optString("space_id", "");
                    if (!sid.isEmpty()) return sid;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
