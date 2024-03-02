package com.chat.uikit.chat.manager;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.Vibrator;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.chat.base.WKBaseApplication;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.db.ApplyDB;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.entity.NewFriendEntity;
import com.chat.base.entity.UserInfoSetting;
import com.chat.base.msg.IConversationContext;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.msgitem.WKUIChatMsgItemEntity;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.utils.ActManagerUtils;
import com.chat.base.utils.NotificationCompatUtil;
import com.chat.base.utils.WKCommonUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKTimeUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.base.views.pwdview.NumPwdDialog;
import com.chat.uikit.R;
import com.chat.uikit.TabActivity;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.chat.ChatActivity;
import com.chat.uikit.contacts.service.FriendModel;
import com.chat.uikit.db.WKContactsDB;
import com.chat.uikit.enity.ProhibitWord;
import com.chat.uikit.group.service.GroupModel;
import com.chat.uikit.message.MsgModel;
import com.chat.uikit.message.ProhibitWordModel;
import com.chat.uikit.search.SearchUserActivity;
import com.chat.uikit.user.UserDetailActivity;
import com.chat.uikit.utils.PushNotificationHelper;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKCMDKeys;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelExtras;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKConversationMsg;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKUIConversationMsg;
import com.xinbida.wukongim.message.type.WKSendMsgResult;
import com.xinbida.wukongim.msgmodel.WKTextContent;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 2019-11-18 11:30
 * im监听相关处理
 */
public class WKIMUtils {

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
                AudioAttributes audioAttributes = null;
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
        WKIM.getInstance().getConnectionManager().addOnGetIpAndPortListener(andPortListener -> {
            MsgModel.getInstance().getChatIp((code, ip, port) -> andPortListener.onGetSocketIpAndPort(ip, Integer.parseInt(port)));
        });
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
        //监听同步会话
        WKIM.getInstance().getConversationManager().addOnSyncConversationListener((s, i, l, iSyncConvChatBack) -> MsgModel.getInstance().syncChat(s, i, l, iSyncConvChatBack));
        //监听同步频道会话
        WKIM.getInstance().getMsgManager().addOnSyncChannelMsgListener((channelID, channelType, startMessageSeq, endMessageSeq, limit, pullMode, iSyncChannelMsgBack) -> MsgModel.getInstance().syncChannelMsg(channelID, channelType, startMessageSeq, endMessageSeq, limit, pullMode, iSyncChannelMsgBack));
        //新消息监听
        WKIM.getInstance().getMsgManager().addOnNewMsgListener("system", msgList -> {
            boolean isAlertMsg = false;
            String channelID = "";
            byte channelType = WKChannelType.PERSONAL;
            WKMsg sensitiveWordsMsg = null;
            String loginUID = WKConfig.getInstance().getUid();
            if (msgList != null && msgList.size() > 0) {
                channelID = msgList.get(msgList.size() - 1).channelID;
                channelType = msgList.get(msgList.size() - 1).channelType;
                for (int i = 0, size = msgList.size(); i < size; i++) {
                    if (msgList.get(i).type == WKContentType.setNewGroupAdmin) {
                        GroupModel.getInstance().groupMembersSync(msgList.get(i).channelID, null);
                    } else if (msgList.get(i).type == WKContentType.groupSystemInfo) {
                        WKCommonModel.getInstance().getChannel(msgList.get(i).channelID, WKChannelType.GROUP, null);
                        GroupModel.getInstance().groupMembersSync(msgList.get(i).channelID, null);
                    } else if (msgList.get(i).type == WKContentType.addGroupMembersMsg || msgList.get(i).type == WKContentType.removeGroupMembersMsg) {
                        //同步信息
                        GroupModel.getInstance().groupMembersSync(msgList.get(i).channelID, null);
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
                    if (msgList.get(i).type == WKContentType.WK_TEXT) {
                        boolean isContains = false;
                        WKTextContent textContent = (WKTextContent) msgList.get(i).baseContentMsgModel;
                        // 判断是否包含敏感词
                        if (WKUIKitApplication.getInstance().sensitiveWords != null
                                && WKUIKitApplication.getInstance().sensitiveWords.list != null
                                && WKUIKitApplication.getInstance().sensitiveWords.list.size() > 0
                                && textContent != null && !TextUtils.isEmpty(textContent.getDisplayContent())) {
                            for (String word : WKUIKitApplication.getInstance().sensitiveWords.list) {
                                if (textContent.getDisplayContent().contains(word)) {
                                    isContains = true;
                                    break;
                                }
                            }
                        }
                        if (isContains) {
                            sensitiveWordsMsg = new WKMsg();
                            sensitiveWordsMsg.channelID = msgList.get(i).channelID;
                            sensitiveWordsMsg.channelType = msgList.get(i).channelType;
                            JSONObject jsonObject = new JSONObject();
                            try {
                                jsonObject.put("content", WKUIKitApplication.getInstance().sensitiveWords.tips);
                                jsonObject.put("type", WKContentType.sensitiveWordsTips);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            WKChannel channel = new WKChannel(msgList.get(i).channelID, msgList.get(i).channelType);
                            sensitiveWordsMsg.setChannelInfo(channel);
                            sensitiveWordsMsg.content = jsonObject.toString();
                            sensitiveWordsMsg.type = WKContentType.sensitiveWordsTips;
                            long tempOrderSeq = WKIM.getInstance().getMsgManager().getMessageOrderSeq(0, msgList.get(i).channelID, msgList.get(i).channelType);
                            sensitiveWordsMsg.orderSeq = tempOrderSeq + 1;
                            sensitiveWordsMsg.status = WKSendMsgResult.send_success;

                        }
                    }
                }
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
            if (newMsgNotice && isAlertMsg && (TextUtils.isEmpty(WKUIKitApplication.getInstance().chattingChannelID) || !WKUIKitApplication.getInstance().chattingChannelID.equals(channelID))) {
                WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelID, channelType);
                if (channel != null && channel.mute == 0) {
                    showNotification(msgList.get(msgList.size() - 1), msgShowDetail, channel, playNewMsgMedia, isVibrate);
                }
            }

            assert msgList != null;

            if (sensitiveWordsMsg != null) {
                WKMsg finalSensitiveWordsMsg = sensitiveWordsMsg;
                new Handler(Objects.requireNonNull(Looper.myLooper())).postDelayed(() -> WKIM.getInstance().getMsgManager().saveAndUpdateConversationMsg(finalSensitiveWordsMsg, false), 1000 * 2);
            }
        });
        WKIM.getInstance().getMsgManager().addOnUploadMsgExtraListener(msgExtra -> {
            WKMsg msg = WKIM.getInstance().getMsgManager().getWithMessageID(msgExtra.messageID);
            int msgSeq = 0;
            if (msg != null) {
                msgSeq = msg.messageSeq;
            }
            MsgModel.getInstance().editMsg(msgExtra.messageID, msgSeq, msgExtra.channelID, msgExtra.channelType, msgExtra.contentEdit, null);
        });
        // 监听同步消息回应
        WKIM.getInstance().getMsgManager().addOnSyncMsgReactionListener((s, b, l) -> MsgModel.getInstance().syncReaction(s, b, l));

        /*
         * 设置获取频道信息的监听
         */
        WKIM.getInstance().getChannelManager().addOnGetChannelInfoListener((channelId, channelType, iChannelInfoListener) -> {
            WKCommonModel.getInstance().getChannel(channelId, channelType, null);
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
            if (!TextUtils.isEmpty(channelID) && channelType == WKChannelType.GROUP) {
                GroupModel.getInstance().groupMembersSync(channelID, null);
            }
        });

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
                        if (cmd.paramJsonObject == null) {
                            return;
                        }
                        String channelID = cmd.paramJsonObject.optString("channel_id");
                        byte channelType = (byte) cmd.paramJsonObject.optInt("channel_type");
                        if (TextUtils.isEmpty(channelID)) {
                            return;
                        }
                        MsgModel.getInstance().syncExtraMsg(channelID, channelType);
                    }
                    case WKCMDKeys.wk_sync_reminders -> {
                        MsgModel.getInstance().syncReminder();
                    }
                    case WKCMDKeys.wk_sync_conversation_extra -> {
                        MsgModel.getInstance().syncCoverExtra();
                    }
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
        if (list.size() > 0) {
            String content = getContent(msg);
            for (ProhibitWord word : list) {
                if (content.contains(word.content)) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < word.content.length(); i++) {
                        sb.append("*");
                    }
                    content = content.replaceAll(word.content, sb.toString());
                }
            }

            if (msg.remoteExtra.contentEditMsgModel != null && !TextUtils.isEmpty(msg.remoteExtra.contentEditMsgModel.getDisplayContent())) {
                msg.remoteExtra.contentEditMsgModel.content = content;
                msg.remoteExtra.contentEditMsgModel.displayContent = content;
            } else {
                msg.baseContentMsgModel.content = content;
                msg.baseContentMsgModel.displayContent = content;
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
                //  String client_msg_no = jsonObject.optString("client_msg_no");
                String channelID = jsonObject.optString("channel_id");
                byte channelType = (byte) jsonObject.optInt("channel_type");
                WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelID, channelType);
                //是否撤回提醒
                int revokeRemind = 0;
                if (channel != null && channel.remoteExtraMap != null && channel.remoteExtraMap.containsKey(WKChannelExtras.revokeRemind)) {
                    Object object = channel.remoteExtraMap.get(WKChannelExtras.revokeRemind);
                    if (object != null) {
                        revokeRemind = (int) object;
                    }
                }
                if (revokeRemind == 1) {
                    // todo 同步消息接口
                    MsgModel.getInstance().syncExtraMsg(channelID, channelType);
//                    WKIM.getInstance().getMsgManager().updateMsgRevokeWithMessageID(messageId, 1);
                } else {
                    // todo 删除服务器消息
                    WKMsg wkMsg = WKIM.getInstance().getMsgManager().getWithMessageID(messageId);
                    if (wkMsg != null) {
                        List<WKMsg> list = new ArrayList<>();
                        list.add(wkMsg);
                        MsgModel.getInstance().deleteMsg(list, null);
                    }

                    int rowNo = WKIM.getInstance().getMsgManager().getRowNoWithMessageID(channelID, channelType, messageId);
                    //要先删除
                    WKIM.getInstance().getMsgManager().deleteWithMessageID(messageId);
                    WKConversationMsg msg = WKIM.getInstance().getConversationManager().getWithChannel(channelID, channelType);
                    if (msg != null) {
                        if (rowNo < msg.unreadCount) {
                            msg.unreadCount--;
                        }
                        WKIM.getInstance().getConversationManager().updateWithMsg(msg);
                    }
                }

            }
        }
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
        if (WKTimeUtils.isFastDoubleClick()) {
            return;
        }
        MsgModel.getInstance().deleteFlameMsg();
        Intent intent = new Intent(chatViewMenu.activity, ChatActivity.class);
        intent.putExtra("channelId", chatViewMenu.channelID);
        intent.putExtra("channelType", chatViewMenu.channelType);
        WKConversationMsg conversationMsg = WKIM.getInstance().getConversationManager().getWithChannel(chatViewMenu.channelID, chatViewMenu.channelType);
        WKMsg msg = null;
        int redDot = 0;
        long aroundMsgSeq = 0;
        if (conversationMsg != null) {
            redDot = conversationMsg.unreadCount;
            msg = WKIM.getInstance().getMsgManager().getWithClientMsgNO(conversationMsg.lastClientMsgNO);
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
            } else {
                WKUIConversationMsg uiMsg = WKIM.getInstance().getConversationManager().getUIConversationMsg(chatViewMenu.channelID, chatViewMenu.channelType);
                if (uiMsg != null && uiMsg.getRemoteMsgExtra() != null && uiMsg.getRemoteMsgExtra().keepMessageSeq != 0) {
                    long lastPreviewMsgOrderSeq = WKIM.getInstance().getMsgManager().getMessageOrderSeq(uiMsg.getRemoteMsgExtra().keepMessageSeq, chatViewMenu.channelID, chatViewMenu.channelType);
                    intent.putExtra("lastPreviewMsgOrderSeq", lastPreviewMsgOrderSeq);
                    intent.putExtra("keepOffsetY", uiMsg.getRemoteMsgExtra().keepOffsetY);
                }
            }
        }
        if (chatViewMenu.isNewTask) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        if (chatViewMenu.forwardMsgList != null && chatViewMenu.forwardMsgList.size() > 0) {
            intent.putParcelableArrayListExtra("msgContentList", (ArrayList<? extends Parcelable>) chatViewMenu.forwardMsgList);
        }
        intent.putExtra("aroundMsgSeq", aroundMsgSeq);
        chatViewMenu.activity.startActivity(intent);
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

    private void getChannelLogo(String url, Activity activity, final IGetChannelLogo iGetChannelLogo) {
        Glide.with(activity)
                .asBitmap()
                .override(200, 200)
                .load(url)
                .into(new CustomTarget<Bitmap>() {

                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        iGetChannelLogo.onSuccess(resource);
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        iGetChannelLogo.onSuccess(null);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {

                    }

                });
    }

    interface IGetChannelLogo {
        // 获取成功
        void onSuccess(Bitmap logo);
    }

    private void showNotification(WKMsg msg, int msgShowDetail, WKChannel channel, boolean playNewMsgMedia, boolean isVibrate) {
        int msgNotice = WKConfig.getInstance().getUserInfo().setting.new_msg_notice;
        if (msgNotice == 0) {
            return;
        }
        Activity activity = ActManagerUtils.getInstance().getCurrentActivity();
        if (activity == null || activity.getComponentName().getClassName().equals(TabActivity.class.getName())) {
            if (playNewMsgMedia) {
                defaultMediaPlayer();
            }
            if (isVibrate) {
                vibrate();
            }
//            return;
        }
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
        if (isVibrate) {
            PushNotificationHelper.INSTANCE.notifyMention(WKUIKitApplication.getInstance().getContext(), 1, showTitle, showContent);
        } else {
            PushNotificationHelper.INSTANCE.notifyMessage(WKUIKitApplication.getInstance().getContext(), 1, showTitle, showContent);
        }
//        showNotice(showTitle, finalShowContent, null, isVibrate);
//        getChannelLogo(url, activity, logo -> showNotice(showTitle, finalShowContent, logo, isVibrate));
    }

    private void showNotice(String showTitle, String showContent, Bitmap logo, boolean isVibrate) {
        //1.获取消息服务
        NotificationManager manager = (NotificationManager) WKUIKitApplication.getInstance().getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        //默认通道是default
        String channelId = WKConstants.newMsgChannelID;
//        String channelId = "default";
        //2.如果是android8.0以上的系统，则新建一个消息通道
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            channelId = WKConstants.newMsgChannelID;
//        /*
//         通道优先级别：
//         * IMPORTANCE_NONE 关闭通知
//         * IMPORTANCE_MIN 开启通知，不会弹出，但没有提示音，状态栏中无显示
//         * IMPORTANCE_LOW 开启通知，不会弹出，不发出提示音，状态栏中显示
//         * IMPORTANCE_DEFAULT 开启通知，不会弹出，发出提示音，状态栏中显示
//         * IMPORTANCE_HIGH 开启通知，会弹出，发出提示音，状态栏中显示
//         */
//            NotificationChannel channel = new NotificationChannel(channelId, "消息提醒", NotificationManager.IMPORTANCE_HIGH);
//            //设置该通道的描述（可以不写）
//            channel.setDescription("重要消息，请不要关闭这个通知。");
//            //是否绕过勿打扰模式
//            channel.setBypassDnd(true);
//            //是否允许呼吸灯闪烁
//            channel.enableLights(true);
//            //闪关灯的灯光颜色
//            channel.setLightColor(Color.RED);
//            //桌面launcher的消息角标
//            channel.canShowBadge();
//            //设置是否应在锁定屏幕上显示此频道的通知
//            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
//            if (isVibrate) {
//                //是否允许震动
//                channel.enableVibration(true);
//                //先震动1秒，然后停止0.5秒，再震动2秒则可设置数组为：new long[]{1000, 500, 2000}
//                channel.setVibrationPattern(new long[]{1000, 500, 2000});
//            } else {
//                channel.enableVibration(false);
//                channel.setVibrationPattern(new long[]{0});
//            }
//            //创建消息通道
//            manager.createNotificationChannel(channel);
//        }
        //3.实例化通知
        NotificationCompat.Builder nc = new NotificationCompat.Builder(WKUIKitApplication.getInstance().getContext(), channelId);
        //通知默认的声音 震动 呼吸灯
//        nc.setDefaults(NotificationCompat.DEFAULT_ALL);
        //通知标题
        nc.setContentTitle(showTitle);
        //通知内容
        nc.setContentText(showContent);
        //设置通知的小图标
        nc.setSmallIcon(R.mipmap.ic_logo);
        //设置通知的大图标
        if (logo != null) {
            nc.setLargeIcon(logo);
        }
//        nc.setLargeIcon(BitmapFactory.decodeResource(WKUIKitApplication.getInstance().getContext().getResources(), R.mipmap.icon_approve));
        //设定通知显示的时间
        nc.setWhen(System.currentTimeMillis());
        //设置通知的优先级
        nc.setPriority(NotificationCompat.PRIORITY_MAX);
        //设置点击通知之后通知是否消失
        nc.setAutoCancel(true);
        //点击通知打开软件
        Context application = WKUIKitApplication.getInstance().getContext();
        Intent resultIntent = new Intent(application, TabActivity.class);
        resultIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            pendingIntent = PendingIntent.getActivity(application, 0, resultIntent, PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntent = PendingIntent.getActivity(application, 0, resultIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_MUTABLE);
        }
        nc.setContentIntent(pendingIntent);
        //4.创建通知，得到build
        Notification notification = nc.build();
        //5.发送通知
        manager.notify(1, notification);
    }

    private void showRTCNotice(String showTitle, String showContent) {
        //1.获取消息服务
        NotificationManager manager = (NotificationManager) WKUIKitApplication.getInstance().getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        //默认通道是default
        String channelId = "application_notification";
//        String channelId = WKConstants.newMsgChannelID;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "音视频通知", NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }
        //3.实例化通知
        NotificationCompat.Builder nc = new NotificationCompat.Builder(WKUIKitApplication.getInstance().getContext(), channelId);
        nc.setVibrate(new long[]{0, 500, 1000});
        //通知默认的声音 震动 呼吸灯
        //通知标题
        nc.setContentTitle(showTitle);
        //通知内容
        nc.setContentText(showContent);
        //设置通知的小图标
        nc.setSmallIcon(R.mipmap.ic_logo);
        //设置通知的大图标
        //设定通知显示的时间
        nc.setWhen(System.currentTimeMillis());
        //设置通知的优先级
        nc.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        //设置点击通知之后通知是否消失
        nc.setAutoCancel(true);
        //点击通知打开软件
        Context application = WKUIKitApplication.getInstance().getContext();
        Intent resultIntent = new Intent(application, TabActivity.class);
        resultIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            pendingIntent = PendingIntent.getActivity(application, 0, resultIntent, PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntent = PendingIntent.getActivity(application, 0, resultIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_MUTABLE);
        }
        nc.setContentIntent(pendingIntent);
        //4.创建通知，得到build
        Notification notification = nc.build();
        //5.发送通知
        manager.notify(2, notification);
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


}
