package com.chat.uikit.chat.adapter;

import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSystemAccount;
import com.chat.base.emoji.MoonUtil;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.AvatarOtherViewMenu;
import com.chat.base.endpoint.entity.ShowCommunityAvatarMenu;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.entity.WKChannelState;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.msgitem.WKMsgItemViewManager;
import com.chat.base.msgitem.WKRevokeProvider;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.ui.components.CounterView;
import com.chat.base.ui.components.TypingView;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.utils.StringUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKTimeUtils;
import com.chat.uikit.R;
import com.chat.uikit.enity.ChatConversationMsg;
import com.chat.uikit.message.MsgModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelExtras;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMentionType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKReminder;
import com.xinbida.wukongim.entity.WKUIConversationMsg;
import com.xinbida.wukongim.message.type.WKSendMsgResult;

import org.jetbrains.annotations.NotNull;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;

import org.json.JSONObject;

import com.chat.uikit.thread.service.ThreadModel;
import com.chat.uikit.thread.service.entity.ThreadEntity;

import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2019-11-15 13:46
 * 会话记录适配器
 */
public class ChatConversationAdapter extends BaseQuickAdapter<ChatConversationMsg, BaseViewHolder> {
    private static final int TYPE_NORMAL = 0;   // 私聊：传统 IM 风格
    private static final int TYPE_COMPACT = 1;  // 群聊：紧凑频道列表风格
    private static final int TYPE_SECTION_HEADER = 2; // 分组 header

    private IListener iListener;
    private IThreadPreviewClickListener threadPreviewClickListener;
    private ISectionToggleListener sectionToggleListener;
    private ISectionLongClickListener sectionLongClickListener;
    private final Set<String> collapsedSections = new HashSet<>();
    // 缓存：groupNo → 子区列表，空列表 表示已加载但无数据
    private final Map<String, List<ThreadEntity>> threadDataCache = new ConcurrentHashMap<>();
    // 标记正在加载的 groupNo，避免重复请求
    private final Set<String> threadLoadingSet = Collections.synchronizedSet(new HashSet<>());
    // 防抖：延迟调 API，等服务端处理完消息再拉取
    private final android.os.Handler threadRefreshHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Map<String, Runnable> pendingRefreshTasks = new ConcurrentHashMap<>();
    private static final long THREAD_REFRESH_DELAY_MS = 1000;

    public ChatConversationAdapter(@Nullable List<ChatConversationMsg> data) {
        super(R.layout.item_chat_conv_layout, data);
    }

    @Override
    protected int getDefItemViewType(int position) {
        ChatConversationMsg item = getItem(position);
        if (item != null && item.isSectionHeader) {
            return TYPE_SECTION_HEADER;
        }
        if (item != null && item.uiConversationMsg != null
                && item.uiConversationMsg.channelType == WKChannelType.GROUP) {
            return TYPE_COMPACT;
        }
        return TYPE_NORMAL;
    }

    @NonNull
    @Override
    protected BaseViewHolder onCreateDefViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutRes;
        if (viewType == TYPE_SECTION_HEADER) {
            layoutRes = R.layout.item_chat_section_header;
        } else if (viewType == TYPE_COMPACT) {
            layoutRes = R.layout.item_chat_conv_compact_layout;
        } else {
            layoutRes = R.layout.item_chat_conv_layout;
        }
        return createBaseViewHolder(parent, layoutRes);
    }

    @Override
    protected void convert(@NonNull final BaseViewHolder helper, ChatConversationMsg conversationMsg) {
        if (helper.getItemViewType() == TYPE_SECTION_HEADER) {
            convertSectionHeader(helper, conversationMsg);
            return;
        }
        if (helper.getItemViewType() == TYPE_COMPACT) {
            convertCompact(helper, conversationMsg);
        } else {
            convertNormal(helper, conversationMsg);
        }
    }

    private void convertNormal(@NonNull BaseViewHolder helper, ChatConversationMsg conversationMsg) {
        WKUIConversationMsg item = conversationMsg.uiConversationMsg;
        setUnreadCount(helper, conversationMsg, false);
        showTime(helper, item);
        showChannel(helper, item);
        // showReminders 统一处理内容显示：有草稿显示草稿，否则显示最后一条消息
        showReminders(helper, conversationMsg);
        setStatus(helper, item, false);
        showTyping(helper, conversationMsg);
        showCalling(helper, conversationMsg);
        showThreadPreviews(helper, item);
    }

    private void convertCompact(@NonNull BaseViewHolder helper, ChatConversationMsg conversationMsg) {
        WKUIConversationMsg item = conversationMsg.uiConversationMsg;

        // 频道名
        String showName = "";
        if (item.getWkChannel() != null) {
            showName = TextUtils.isEmpty(item.getWkChannel().channelRemark)
                    ? item.getWkChannel().channelName
                    : item.getWkChannel().channelRemark;
            if (TextUtils.isEmpty(showName)) {
                showName = getContext().getString(R.string.chat);
                WKIM.getInstance().getChannelManager().fetchChannelInfo(item.channelID, item.channelType);
            }
        } else {
            showName = getContext().getString(R.string.chat);
            WKIM.getInstance().getChannelManager().fetchChannelInfo(item.channelID, item.channelType);
        }
        helper.setText(R.id.nameTv, showName);

        // 未读数
        setUnreadCount(helper, conversationMsg, false);

        // 置顶背景
        boolean isTop = item.getWkChannel() != null && item.getWkChannel().top == 1;
        helper.setBackgroundResource(R.id.contentLayout, isTop ? R.drawable.home_bg : R.drawable.layout_bg);

        // 免打扰图标
        ImageView muteIv = helper.getView(R.id.muteIv);
        if (item.getWkChannel() != null && item.getWkChannel().mute == 1) {
            muteIv.setVisibility(View.VISIBLE);
            Theme.setColorFilter(muteIv, ContextCompat.getColor(getContext(), R.color.popupTextColor));
        } else {
            muteIv.setVisibility(View.GONE);
        }

        // 禁言图标
        ImageView forbiddenIv = helper.getView(R.id.forbiddenIv);
        if (item.getWkChannel() != null && item.getWkChannel().forbidden == 1) {
            WKChannelMember mChannelMember = WKIM.getInstance().getChannelMembersManager()
                    .getMember(item.channelID, item.channelType, WKConfig.getInstance().getUid());
            if (mChannelMember != null && mChannelMember.role == 0) {
                forbiddenIv.setVisibility(View.VISIBLE);
                forbiddenIv.setColorFilter(new PorterDuffColorFilter(
                        ContextCompat.getColor(getContext(), R.color.color999), PorterDuff.Mode.MULTIPLY));
            } else {
                forbiddenIv.setVisibility(View.GONE);
            }
        } else {
            forbiddenIv.setVisibility(View.GONE);
        }

        // @mention 提醒预览
        showCompactReminders(helper, conversationMsg);

        // 长按事件
        addEvent(helper, item);

        // 子区预览
        showThreadPreviews(helper, item);
    }

    public void addListener(IListener iItemMenuClick) {
        this.iListener = iItemMenuClick;
    }

    public void setThreadPreviewClickListener(IThreadPreviewClickListener listener) {
        this.threadPreviewClickListener = listener;
    }

    @Override
    protected void convert(@NotNull BaseViewHolder baseViewHolder, ChatConversationMsg uiConversationMsg, @NotNull List<?> payloads) {
        if (baseViewHolder.getItemViewType() == TYPE_SECTION_HEADER) {
            return;
        }
        ChatConversationMsg chatConversationMsg = (ChatConversationMsg) payloads.get(0);
        if (chatConversationMsg != null && chatConversationMsg.uiConversationMsg != null) {
            if (baseViewHolder.getItemViewType() == TYPE_COMPACT) {
                convertCompactPayloads(baseViewHolder, chatConversationMsg);
            } else {
                convertNormalPayloads(baseViewHolder, chatConversationMsg);
            }
        }
    }

    private void convertNormalPayloads(@NotNull BaseViewHolder baseViewHolder, ChatConversationMsg chatConversationMsg) {
        WKUIConversationMsg item = chatConversationMsg.uiConversationMsg;
        if (chatConversationMsg.isResetCounter) {
            setUnreadCount(baseViewHolder, chatConversationMsg, true);
            chatConversationMsg.isResetCounter = false;
        }
        if (chatConversationMsg.isResetTime) {
            showTime(baseViewHolder, item);
            chatConversationMsg.isResetTime = false;
        }
        if (chatConversationMsg.isResetTyping) {
            showTyping(baseViewHolder, chatConversationMsg);
            chatConversationMsg.isResetTyping = false;
        }
        if (chatConversationMsg.isRefreshChannelInfo) {
            showChannel(baseViewHolder, item);
            showThreadPreviews(baseViewHolder, item);
            chatConversationMsg.isRefreshChannelInfo = false;
        }
        if (chatConversationMsg.isRefreshStatus) {
            setStatus(baseViewHolder, item, true);
            chatConversationMsg.isRefreshStatus = false;
        }
        // 内容或提醒变化时统一走 showReminders：有草稿显示草稿，否则显示最后一条消息
        if (chatConversationMsg.isResetContent || chatConversationMsg.isResetReminders) {
            showReminders(baseViewHolder, chatConversationMsg);
            chatConversationMsg.isResetContent = false;
            chatConversationMsg.isResetReminders = false;
        }
        showCalling(baseViewHolder, chatConversationMsg);
    }

    private void convertCompactPayloads(@NotNull BaseViewHolder baseViewHolder, ChatConversationMsg chatConversationMsg) {
        if (chatConversationMsg.isResetCounter) {
            setUnreadCount(baseViewHolder, chatConversationMsg, true);
            chatConversationMsg.isResetCounter = false;
        }
        if (chatConversationMsg.isRefreshChannelInfo) {
            convertCompact(baseViewHolder, chatConversationMsg);
            chatConversationMsg.isRefreshChannelInfo = false;
        }
        // 内容或提醒变化时刷新紧凑行的 @mention 预览
        if (chatConversationMsg.isResetContent || chatConversationMsg.isResetReminders) {
            showCompactReminders(baseViewHolder, chatConversationMsg);
            chatConversationMsg.isResetContent = false;
            chatConversationMsg.isResetReminders = false;
        }
        // 紧凑模式不需要处理 time/typing/status/calling
        chatConversationMsg.isResetTime = false;
        chatConversationMsg.isResetTyping = false;
        chatConversationMsg.isRefreshStatus = false;
    }

    public interface IListener {
        void onClick(ItemMenu menu, WKUIConversationMsg item);
    }


    private String getFromName(byte channelType, WKMsg msg) {
        String fromName = "";
        if (msg != null && (WKContentType.isSystemMsg(msg.type)
                || msg.type == WKContentType.revoke
                || msg.remoteExtra.revoke == 1 || msg.type == WKContentType.screenshot)) {
            return fromName;
        }
        if (channelType == WKChannelType.PERSONAL || channelType == WKChannelType.CUSTOMER_SERVICE || msg == null || TextUtils.isEmpty(msg.fromUID) || msg.fromUID.equals(WKConfig.getInstance().getUid())) {
            return fromName;
        }
        String channelName = "";
        String channelRemark = "";
        String memberRemark = "";
        String memberName = "";
        if (msg.getFrom() != null) {
            channelRemark = msg.getFrom().channelRemark;
            channelName = msg.getFrom().channelName;
        }
        if (!TextUtils.isEmpty(channelRemark)) {
            return channelRemark;
        }
        if (msg.getMemberOfFrom() != null) {
            memberName = msg.getMemberOfFrom().memberName;
            memberRemark = msg.getMemberOfFrom().memberRemark;
        }
        if (!TextUtils.isEmpty(memberRemark)) {
            return memberRemark;
        }
        fromName = TextUtils.isEmpty(channelName) ? memberName : channelName;
        return fromName;
    }

    private String getContent(WKMsg msg) {
        String content = "";
        if (msg == null || msg.isDeleted == 1) return content;
        if (msg.baseContentMsgModel != null) {
            content = msg.baseContentMsgModel.getDisplayContent();
        }

        if (TextUtils.isEmpty(content) || WKContentType.isSystemMsg(msg.type)) {
            content = getShowContent(msg.content);
        }
        // 截屏消息：SDK 不认识 type=20，baseModel.getDisplayContent() 返回"未知消息"，需要覆盖
        if (msg.type == WKContentType.screenshot) {
            String name;
            if (msg.fromUID != null && msg.fromUID.equals(WKConfig.getInstance().getUid())) {
                name = getContext().getString(R.string.str_you);
            } else {
                name = "";
                if (msg.getFrom() != null && !TextUtils.isEmpty(msg.getFrom().channelName)) {
                    name = msg.getFrom().channelName;
                }
                if (TextUtils.isEmpty(name)) {
                    name = getContext().getString(R.string.str_someone);
                }
            }
            content = String.format(getContext().getString(R.string.screenshot_tip), name);
        }
        if (msg.remoteExtra.contentEditMsgModel != null) {
            content = msg.remoteExtra.contentEditMsgModel.getDisplayContent();
        }
        //判断是否被撤回
        if (msg.remoteExtra.revoke == 1)
            content = WKRevokeProvider.Companion.showRevokeMsg(msg);
        else if (msg.type == WKContentType.WK_CONTENT_FORMAT_ERROR) {
            content = getContext().getString(R.string.str_content_format_err);
        } else if (msg.type == WKContentType.WK_SIGNAL_DECRYPT_ERROR) {
            content = getContext().getString(R.string.str_signal_decrypt_err);
        } else if (msg.type == WKContentType.noRelation) {
            String showName = "";
            if (msg.getChannelInfo() != null) {
                if (TextUtils.isEmpty(msg.getChannelInfo().channelRemark)) {
                    showName = msg.getChannelInfo().channelName;
                } else {
                    showName = msg.getChannelInfo().channelRemark;
                }
            }
            content = String.format(getContext().getString(R.string.no_relation_request), showName);
        } else {
            if (!WKMsgItemViewManager.getInstance().getChatItemProviderList().containsKey(msg.type)) {
                if (TextUtils.isEmpty(content)) {
                    content = getContext().getString(R.string.unknow_msg_type);
                }
            }
        }
        return content;
    }

    private String getShowContent(String contentJson) {
        return StringUtils.getShowContent(getContext(), contentJson);
    }

    private void setStatus(BaseViewHolder helper, WKUIConversationMsg item, boolean isPlayAnimation) {
        RLottieImageView sendingMsgIv = helper.getView(R.id.statusIV);
        RLottieDrawable drawable;
        boolean autoRepeat = false;
        int status = WKSendMsgResult.send_success;
        if (item.getWkMsg() != null) {
            status = item.getWkMsg().status;
        }
        boolean isSend = item.getWkMsg() != null && item.getWkMsg().isDeleted == 0 && !TextUtils.isEmpty(item.getWkMsg().fromUID) && item.getWkMsg().fromUID.equals(WKConfig.getInstance().getUid());
        if (isSend) {
            boolean isSingle = true;
            sendingMsgIv.setVisibility(View.VISIBLE);
            boolean isError = false;
            if (status == WKSendMsgResult.send_success) {
                // 自己发送
                if (item.getWkMsg().setting.receipt == 1 && item.getWkMsg().remoteExtra.readedCount > 0) {
                    drawable = new RLottieDrawable(getContext(), R.raw.ticks_double, "ticks_double", AndroidUtilities.dp(22), AndroidUtilities.dp(22));
                    isSingle = false;
                } else {
                    drawable = new RLottieDrawable(getContext(), R.raw.ticks_single, "ticks_single", AndroidUtilities.dp(22), AndroidUtilities.dp(22));
                }
                sendingMsgIv.setColorFilter(new PorterDuffColorFilter(Theme.colorAccount, PorterDuff.Mode.MULTIPLY));
            } else if (status == WKSendMsgResult.send_loading) {
                autoRepeat = true;
                drawable = new RLottieDrawable(getContext(), R.raw.msg_sending, "msg_sending", AndroidUtilities.dp(22), AndroidUtilities.dp(22));
                sendingMsgIv.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(getContext(), R.color.color999), PorterDuff.Mode.MULTIPLY));
            } else {
                isError = true;
                sendingMsgIv.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(getContext(), R.color.white), PorterDuff.Mode.MULTIPLY));
                drawable = new RLottieDrawable(getContext(), R.raw.error, "error", AndroidUtilities.dp(22), AndroidUtilities.dp(22));
            }
            sendingMsgIv.setAutoRepeat(autoRepeat);

            if (autoRepeat || isPlayAnimation) {
                sendingMsgIv.setAnimation(drawable);
                sendingMsgIv.playAnimation();
            } else {
                if (isError) {
                    sendingMsgIv.setAnimation(drawable);
                } else {
                    if (isSingle) {
                        sendingMsgIv.setImageDrawable(Theme.getTicksSingleDrawable());
                    } else sendingMsgIv.setImageDrawable(Theme.getTicksDoubleDrawable());
                }
            }
        } else {
            sendingMsgIv.setVisibility(View.GONE);
        }
        int finalStatus = status;
        sendingMsgIv.setOnClickListener(view -> {
            if (finalStatus != WKSendMsgResult.send_success && finalStatus != WKSendMsgResult.send_loading && item.getWkMsg() != null) {
                String content = getContext().getString(R.string.str_resend_msg_tips);
                if (finalStatus == WKSendMsgResult.no_relation) {
                    content = getContext().getString(R.string.no_relation_group);
                } else if (finalStatus == WKSendMsgResult.black_list) {
                    content =
                            getContext().getString(item.channelType == WKChannelType.GROUP ? R.string.blacklist_group : R.string.blacklist_user);

                } else if (finalStatus == WKSendMsgResult.not_on_white_list) {
                    content = getContext().getString(R.string.no_relation_user);
                }
                WKDialogUtils.getInstance().showDialog(getContext(), getContext().getString(R.string.msg_send_fail), content, true, "", getContext().getString(R.string.msg_send_fail_resend), 0, Theme.colorAccount, index -> {
                    if (index == 1) {
                        WKMsg msg = new WKMsg();
                        msg.channelID = item.channelID;
                        msg.channelType = item.channelType;
                        msg.setting = item.getWkMsg().setting;
                        msg.header = item.getWkMsg().header;
                        msg.type = item.getWkMsg().type;
                        msg.content = item.getWkMsg().content;
                        msg.baseContentMsgModel = item.getWkMsg().baseContentMsgModel;
                        msg.fromUID = WKConfig.getInstance().getUid();
                        WKIM.getInstance().getMsgManager()
                                .deleteWithClientMsgNO(item.getWkMsg().clientMsgNO);
                        WKIM.getInstance().getMsgManager().sendMessage(msg);
                    }
                });
            }
        });
    }

    private void setUnreadCount(@NotNull BaseViewHolder baseViewHolder, ChatConversationMsg item, boolean isAnimated) {
        int unread = item.getUnReadCount();
        View view = baseViewHolder.getView(R.id.msgCountTv);
        if (view instanceof CounterView) {
            // 普通布局（私聊）：CounterView
            CounterView counterView = (CounterView) view;
            boolean isMute = item.uiConversationMsg.getWkChannel() != null
                    && item.uiConversationMsg.getWkChannel().mute == 1;
            counterView.setColors(R.color.white, isMute ? R.color.color999 : R.color.reminderColor);
            counterView.setCount(unread, isAnimated);
            counterView.setGravity(Gravity.END);
            counterView.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
        } else if (view instanceof TextView) {
            // 紧凑布局（群聊）：圆形 TextView
            TextView badge = (TextView) view;
            if (unread > 0) {
                badge.setText(unread > 99 ? "99+" : String.valueOf(unread));
                badge.setVisibility(View.VISIBLE);
            } else {
                badge.setVisibility(View.GONE);
            }
        }
    }

    private void showTime(@NotNull BaseViewHolder helper, WKUIConversationMsg item) {
        long msgTimestamp = item.lastMsgTimestamp;
        if (item.getWkMsg() != null) {
            if (item.getWkMsg().remoteExtra.editedAt != 0) {
                msgTimestamp = item.getWkMsg().remoteExtra.editedAt;
            }
        }
        String chatTime = WKTimeUtils.getInstance().getNewChatTime(msgTimestamp * 1000);
        helper.setText(R.id.timeTv, chatTime);
    }

    // 系统 Bot：会话列表预览需按 Space 过滤
    private static final Set<String> SYSTEM_BOTS = new HashSet<>(Arrays.asList("botfather"));

    /**
     * 从消息中提取 space_id
     */
    private String getSpaceIdFromMsg(WKMsg msg) {
        if (msg == null) return null;
        // 1. 从 content 原始 JSON 解析
        if (!TextUtils.isEmpty(msg.content)) {
            try {
                JSONObject json = new JSONObject(msg.content);
                String sid = json.optString("space_id", "");
                if (!sid.isEmpty()) return sid;
            } catch (Exception ignored) {
            }
        }
        // 2. 从 SDK 解码后的 spaceId 字段读取
        if (msg.baseContentMsgModel != null && !TextUtils.isEmpty(msg.baseContentMsgModel.spaceId)) {
            return msg.baseContentMsgModel.spaceId;
        }
        return null;
    }

    /**
     * 为系统 Bot 查找当前 Space 下的最后一条消息。
     * 返回 null 表示不需要特殊处理（非系统 Bot 或已匹配当前 Space），
     * 返回空字符串表示当前 Space 无消息。
     *
     * 搜索范围 500 条：服务端 BotFather 跨 Space 共享，sync 返回的 recents 不按 space_id 过滤，
     * 近期大量其他 Space 消息可能将当前 Space 消息挤出默认搜索范围。
     */
    private String findSystemBotSpaceContent(WKUIConversationMsg item) {
        if (!SYSTEM_BOTS.contains(item.channelID)) return null;
        String currentSpaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (TextUtils.isEmpty(currentSpaceId)) return null;

        // 最后一条消息已匹配当前 Space，无需特殊处理
        String lastMsgSpaceId = getSpaceIdFromMsg(item.getWkMsg());
        if (lastMsgSpaceId != null && lastMsgSpaceId.equals(currentSpaceId)) return null;
        if (lastMsgSpaceId == null) return null; // 无 space_id 的历史消息，向前兼容

        // 最后一条消息不属于当前 Space，查本地 DB 找当前 Space 的最后一条（扩大搜索范围）
        try {
            List<WKMsg> recentMsgs = WKIM.getInstance().getMsgManager()
                    .searchMsgWithChannelAndContentTypes(
                            item.channelID, item.channelType,
                            0, 500,
                            new int[]{WKContentType.WK_TEXT});
            if (recentMsgs != null) {
                for (WKMsg msg : recentMsgs) {
                    String sid = getSpaceIdFromMsg(msg);
                    if (sid == null || currentSpaceId.equals(sid)) {
                        return getContent(msg);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return ""; // 当前 Space 确实无消息
    }

    /**
     * 返回 Space 感知的未读数。
     * Person 频道在 Space 模式下，若最后一条消息不属于当前 Space，返回 0。
     */
    public int getEffectiveUnreadCount(ChatConversationMsg item) {
        if (item.uiConversationMsg.channelType == WKChannelType.PERSONAL) {
            String currentSpaceId = MsgModel.getInstance().getCurrentSpaceId();
            if (!TextUtils.isEmpty(currentSpaceId)) {
                String msgSpaceId = getSpaceIdFromMsg(item.uiConversationMsg.getWkMsg());
                if (!TextUtils.isEmpty(msgSpaceId) && !msgSpaceId.equals(currentSpaceId)) {
                    return 0;
                }
            }
        }
        return item.uiConversationMsg.unreadCount;
    }

    /**
     * 群聊紧凑行：有 @mention 时显示 [有人@你] + 消息预览，否则隐藏
     */
    private void showCompactReminders(@NotNull BaseViewHolder helper, ChatConversationMsg conversationMsg) {
        TextView contentTv = helper.getView(R.id.compactContentTv);
        List<WKReminder> reminders = conversationMsg.getReminders();
        boolean hasMention = false;
        if (WKReader.isNotEmpty(reminders)) {
            for (WKReminder r : reminders) {
                if (r.type == WKMentionType.WKReminderTypeMentionMe && r.done == 0) {
                    hasMention = true;
                    break;
                }
            }
        }
        if (!hasMention) {
            contentTv.setVisibility(View.GONE);
            return;
        }
        contentTv.setVisibility(View.VISIBLE);
        String mentionTag = getContext().getString(R.string.last_msg_remind);
        WKUIConversationMsg item = conversationMsg.uiConversationMsg;
        String msgContent = getContent(item.getWkMsg());
        String fromName = getFromName(item.channelType, item.getWkMsg());
        String preview = TextUtils.isEmpty(fromName) ? msgContent : fromName + "：" + msgContent;

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        ssb.append(mentionTag);
        ssb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(getContext(), R.color.reminderColor)),
                0, mentionTag.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.append(" ");
        ssb.append(preview);
        contentTv.setText(ssb);
    }

    private void showContent(@NotNull BaseViewHolder helper, WKUIConversationMsg item) {
        String content;
        androidx.emoji2.widget.EmojiTextView contentTv = helper.getView(R.id.contentTv);
        boolean isSetChatPwd = isSetChatPwd(item.getWkChannel());

        // 系统 Bot：显示当前 Space 的最后一条消息
        String spaceContent = findSystemBotSpaceContent(item);
        if (spaceContent != null) {
            content = spaceContent;
        } else if (isSetChatPwd) {
            // 聊天密码
            content = "❊❊❊❊❊❊❊❊❊❊❊❊❊";
        } else {
            content = getContent(item.getWkMsg());
            String fromName = getFromName(item.channelType, item.getWkMsg());
            if (!TextUtils.isEmpty(fromName)) {
                content = fromName + "：" + content;
            }
        }
        //  contentTv.setText(content);
        MoonUtil.identifyFaceExpression(getContext(), contentTv, content, MoonUtil.SMALL_SCALE);
    }

    private void showReminders(@NotNull BaseViewHolder helper, ChatConversationMsg item) {
        TextView contentTv = helper.getView(R.id.contentTv);
        String draft = "";
        String approveContent = "";
        boolean mention = false;
        if (WKReader.isNotEmpty(item.getReminders())) {
            for (int i = 0, size = item.getReminders().size(); i < size; i++) {
                if (!mention && item.getReminders().get(i).type == WKMentionType.WKReminderTypeMentionMe && item.getReminders().get(i).done == 0) {
                    //存在@
                    mention = true;
                    // break;
                }
                if (item.getReminders().get(i).type == WKMentionType.WKApplyJoinGroupApprove && item.getReminders().get(i).done == 0) {
                    approveContent = getContext().getString(R.string.apply_join_group);
                }
            }
        }
        if (item.uiConversationMsg.getRemoteMsgExtra() != null) {
            draft = item.uiConversationMsg.getRemoteMsgExtra().draft;
        }
        boolean isSetChatPwd = isSetChatPwd(item.uiConversationMsg.getWkChannel());
        // 聊天密码
        if (isSetChatPwd) {
            if (!TextUtils.isEmpty(draft))
                draft = "❊❊❊❊❊❊❊❊❊❊❊❊❊";
        }
        LinearLayout remindLayout = helper.getView(R.id.remindLayout);
        remindLayout.removeAllViews();
        if (mention) {
            TextView textView = new TextView(getContext());
            textView.setTypeface(null, Typeface.BOLD);
            textView.setText(R.string.last_msg_remind);
            textView.setTextColor(ContextCompat.getColor(getContext(), R.color.reminderColor));
            textView.setTextSize(13f);
            remindLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 5, 0));
        }
        if (!TextUtils.isEmpty(draft)) {
            TextView textView = new TextView(getContext());
            textView.setText(R.string.last_msg_draft);
            textView.setTypeface(null, Typeface.BOLD);
            textView.setTextColor(ContextCompat.getColor(getContext(), R.color.reminderColor));
            textView.setTextSize(13f);
            remindLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 5, 0));
            MoonUtil.identifyFaceExpression(getContext(), contentTv, draft, MoonUtil.SMALL_SCALE);
        } else {
            showContent(helper, item.uiConversationMsg);
        }
        if (!TextUtils.isEmpty(approveContent)) {
            TextView textView = new TextView(getContext());
            textView.setText(approveContent);
            textView.setTypeface(null, Typeface.BOLD);
            textView.setTextColor(ContextCompat.getColor(getContext(), R.color.reminderColor));
            textView.setTextSize(13f);
            remindLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 5, 0));
        }
    }

    private void showChannel(@NotNull BaseViewHolder helper, WKUIConversationMsg item) {
        addEvent(helper, item);
        String showName = "";
        if (item.channelID.equals(WKSystemAccount.system_file_helper)) {
            showName = getContext().getString(R.string.wk_file_helper);
        } else if (item.channelID.equals(WKSystemAccount.system_team)) {
            showName = getContext().getString(R.string.wk_system_notice);
        }
        helper.setGone(R.id.groupIV, item.channelType != WKChannelType.GROUP);
        boolean isTop;
        AvatarView avatarView = helper.getView(R.id.avatarView);
        avatarView.setSize(50);
        if (item.getWkChannel() != null) {
            if (item.channelType == WKChannelType.COMMUNITY) {
                EndpointManager.getInstance().invoke("show_community_avatar", new ShowCommunityAvatarMenu(getContext(), avatarView, item.getWkChannel()));
            } else {
                avatarView.defaultAvatarTv.setVisibility(View.GONE);
                avatarView.imageView.setVisibility(View.VISIBLE);
                avatarView.showAvatar(item.getWkChannel(), true);
            }
            EndpointManager.getInstance().invoke("show_avatar_other_info", new AvatarOtherViewMenu(helper.getView(R.id.otherLayout), item.getWkChannel(), avatarView, false));
            isTop = item.getWkChannel().top == 1;
            if (TextUtils.isEmpty(showName))
                showName = TextUtils.isEmpty(item.getWkChannel().channelRemark) ? item.getWkChannel().channelName : item.getWkChannel().channelRemark;
            if (TextUtils.isEmpty(showName)) {
                showName = getContext().getString(R.string.chat);
//                if (!isScrolling)
                WKIM.getInstance().getChannelManager().fetchChannelInfo(item.channelID, item.channelType);
            }
            LinearLayout categoryLayout = helper.getView(R.id.categoryLayout);
            categoryLayout.removeAllViews();
            ImageView forbiddenIv = helper.getView(R.id.forbiddenIv);
            forbiddenIv.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(getContext(), R.color.color999), PorterDuff.Mode.MULTIPLY));
            //设置是否置顶
            helper.setBackgroundResource(R.id.contentLayout, isTop ? R.drawable.home_bg : R.drawable.layout_bg);
            if (item.getWkChannel().mute == 1) {
                ImageView muteIV = new ImageView(getContext());
                muteIV.setImageResource(R.mipmap.list_mute);
                Theme.setColorFilter(muteIV, ContextCompat.getColor(getContext(), R.color.popupTextColor));
                categoryLayout.addView(muteIV, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 3, 1, 0, 0));
            }
            if (!TextUtils.isEmpty(item.getWkChannel().category)) {

                if (item.getWkChannel().category.equals(WKSystemAccount.accountCategorySystem)) {
                    categoryLayout.addView(Theme.getChannelCategoryTV(getContext(), getContext().getString(R.string.official), ContextCompat.getColor(getContext(), R.color.transparent), ContextCompat.getColor(getContext(), R.color.reminderColor), ContextCompat.getColor(getContext(), R.color.reminderColor)), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
                }
                if (item.getWkChannel().category.equals(WKSystemAccount.accountCategoryCustomerService)) {
                    categoryLayout.addView(Theme.getChannelCategoryTV(getContext(), getContext().getString(R.string.customer_service), Theme.colorAccount, ContextCompat.getColor(getContext(), R.color.white), Theme.colorAccount), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
                }
                if (item.getWkChannel().category.equals(WKSystemAccount.accountCategoryVisitor)) {
                    categoryLayout.addView(Theme.getChannelCategoryTV(getContext(), getContext().getString(R.string.visitor), ContextCompat.getColor(getContext(), R.color.transparent), ContextCompat.getColor(getContext(), R.color.colorAccent), ContextCompat.getColor(getContext(), R.color.colorAccent)), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
                }
                if (item.getWkChannel().category.equals(WKSystemAccount.channelCategoryOrganization)) {
                    categoryLayout.addView(Theme.getChannelCategoryTV(getContext(), getContext().getString(R.string.all_staff), ContextCompat.getColor(getContext(), R.color.category_org_bg), ContextCompat.getColor(getContext(), R.color.category_org_text), ContextCompat.getColor(getContext(), R.color.transparent)), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
                }
                if (item.getWkChannel().category.equals(WKSystemAccount.channelCategoryDepartment)) {
                    categoryLayout.addView(Theme.getChannelCategoryTV(getContext(), getContext().getString(R.string.department), ContextCompat.getColor(getContext(), R.color.category_org_bg), ContextCompat.getColor(getContext(), R.color.category_org_text), ContextCompat.getColor(getContext(), R.color.transparent)), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
                }
            }
            if (item.channelType == WKChannelType.COMMUNITY) {
                categoryLayout.addView(Theme.getChannelCategoryTV(getContext(), getContext().getString(R.string.community), ContextCompat.getColor(getContext(), R.color.category_community_bg), ContextCompat.getColor(getContext(), R.color.category_community_text), ContextCompat.getColor(getContext(), R.color.transparent)), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
            }
            if (item.getWkChannel().robot == 1)
                categoryLayout.addView(Theme.getChannelCategoryTV(getContext(), getContext().getString(R.string.bot), ContextCompat.getColor(getContext(), R.color.colorAccent), ContextCompat.getColor(getContext(), R.color.white), ContextCompat.getColor(getContext(), R.color.colorAccent)), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
            //判断是否禁言
            if (item.getWkChannel().forbidden == 1) {
                WKChannelMember mChannelMember = WKIM.getInstance().getChannelMembersManager().getMember(item.channelID, item.channelType, WKConfig.getInstance().getUid());
                if (mChannelMember != null && mChannelMember.role == 0) {
                    helper.setGone(R.id.forbiddenIv, false);
                } else helper.setGone(R.id.forbiddenIv, true);
            } else {
                helper.setGone(R.id.forbiddenIv, true);
            }
            //消息头像

//            GlideUtils.getInstance().showAvatarImg(getContext(), item.channelID, item.channelType, item.getWkChannel().avatar, helper.getView(R.id.avatarIv));
        } else {
            if (TextUtils.isEmpty(showName))
                showName = getContext().getString(R.string.chat);
            avatarView.defaultAvatarTv.setVisibility(View.GONE);
            avatarView.imageView.setVisibility(View.VISIBLE);
            avatarView.imageView.setImageResource(R.drawable.default_view_bg);
            //消息头像
//            avatarView.showAvatar(item.channelID, item.channelType);
//            GlideUtils.getInstance().showAvatarImg(getContext(), item.channelID, item.channelType, "", helper.getView(R.id.avatarIv));
            //重新获取频道信息
//            if (!isScrolling)
            WKIM.getInstance().getChannelManager().fetchChannelInfo(item.channelID, item.channelType);
        }
        helper.setText(R.id.nameTv, showName);
    }

    /**
     * 展示子区预览（最多 2 个最近活跃子区 + "+N 个子区" 折叠行）
     * 参考 iOS WKConversationGroupThreadCell：带分支线 + 圆角卡片容器 + 未读数气泡
     */
    private void showThreadPreviews(@NotNull BaseViewHolder helper, WKUIConversationMsg item) {
        FrameLayout container = helper.getView(R.id.threadPreviewContainer);
        container.removeAllViews();
        container.setVisibility(View.GONE);

        if (item.channelType != WKChannelType.GROUP
                || WKConfig.getInstance().getAppConfig().thread_on != 1) {
            return;
        }

        String groupNo = item.channelID;
        List<ThreadEntity> cachedList = threadDataCache.get(groupNo);
        if (cachedList == null) {
            loadThreadPreviews(groupNo);
            return;
        }

        // 过滤活跃子区（status == 1）并按 updated_at 降序（与 iOS 一致）
        List<ThreadEntity> activeList = new ArrayList<>();
        for (ThreadEntity entity : cachedList) {
            if (entity.status == 1) {
                activeList.add(entity);
            }
        }
        if (activeList.isEmpty()) {
            return;
        }
        // updated_at 是 ISO 格式字符串，可直接用字符串比较排序
        Collections.sort(activeList, (a, b) -> {
            String ua = a.updated_at != null ? a.updated_at : "";
            String ub = b.updated_at != null ? b.updated_at : "";
            return ub.compareTo(ua);
        });

        container.setVisibility(View.VISIBLE);
        int showCount = Math.min(activeList.size(), 2);
        LayoutInflater inflater = LayoutInflater.from(getContext());

        // 内容包装层（卡片 + "+N"），放在 FrameLayout 中和分支线分层
        LinearLayout contentWrapper = new LinearLayout(getContext());
        contentWrapper.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams wrapperLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        contentWrapper.setLayoutParams(wrapperLp);

        // 1. 圆角卡片容器（仅含子区行 + 分隔线）
        LinearLayout cardContainer = new LinearLayout(getContext());
        cardContainer.setOrientation(LinearLayout.VERTICAL);
        cardContainer.setBackgroundResource(R.drawable.thread_container_border);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMarginStart(AndroidUtilities.dp(64));
        cardLp.setMarginEnd(AndroidUtilities.dp(15));
        cardLp.topMargin = AndroidUtilities.dp(4);

        // 记录每行 View 用于分支线定位
        List<View> rowViews = new ArrayList<>();

        for (int i = 0; i < showCount; i++) {
            // 添加分隔线（第二行起）
            if (i > 0) {
                View separator = new View(getContext());
                separator.setBackgroundColor(0x26999999);
                LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(0.5f));
                sepLp.setMarginStart(AndroidUtilities.dp(10));
                sepLp.setMarginEnd(AndroidUtilities.dp(10));
                cardContainer.addView(separator, sepLp);
            }

            ThreadEntity entity = activeList.get(i);
            View rowView = inflater.inflate(R.layout.item_thread_preview_row, cardContainer, false);

            TextView nameTv = rowView.findViewById(R.id.threadNameTv);
            TextView unreadBadge = rowView.findViewById(R.id.threadUnreadBadge);

            nameTv.setText(entity.name);

            // 未读数气泡
            int unread = entity.unread_count;
            String threadChannelId = ThreadModel.getInstance().buildChannelId(groupNo, entity.short_id);
            WKUIConversationMsg threadConv = WKIM.getInstance().getConversationManager()
                    .getUIConversationMsg(threadChannelId, WKChannelType.COMMUNITY_TOPIC);
            if (threadConv != null) {
                unread = threadConv.unreadCount;
            }
            if (unread > 0) {
                unreadBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
                unreadBadge.setVisibility(View.VISIBLE);
            } else {
                unreadBadge.setVisibility(View.GONE);
            }

            // @mention 提示
            TextView mentionTv = rowView.findViewById(R.id.threadMentionTv);
            boolean threadHasMention = hasThreadMentionForChannel(threadChannelId);
            if (threadHasMention) {
                String mentionTag = getContext().getString(R.string.last_msg_remind);
                String lastMsg = "";
                if (!TextUtils.isEmpty(entity.last_message_content)) {
                    lastMsg = entity.last_message_content;
                    if (!TextUtils.isEmpty(entity.last_message_sender_name)) {
                        lastMsg = entity.last_message_sender_name + ": " + lastMsg;
                    }
                }
                mentionTv.setText(mentionTag + (TextUtils.isEmpty(lastMsg) ? "" : " " + lastMsg));
                mentionTv.setVisibility(View.VISIBLE);
            } else {
                mentionTv.setVisibility(View.GONE);
            }

            String finalThreadChannelId = threadChannelId;
            rowView.setOnClickListener(v -> {
                if (threadPreviewClickListener != null) {
                    threadPreviewClickListener.onThreadClick(finalThreadChannelId, groupNo, entity.short_id, entity.is_joined);
                }
            });

            cardContainer.addView(rowView);
            rowViews.add(rowView);
        }

        contentWrapper.addView(cardContainer, cardLp);

        // "+N 个子区" 放在卡片外部
        if (activeList.size() > 2) {
            int moreCount = activeList.size() - 2;
            // 检查未展示子区是否有 @mention
            boolean moreMention = false;
            for (int i = 2; i < activeList.size(); i++) {
                String tcId = ThreadModel.getInstance().buildChannelId(groupNo, activeList.get(i).short_id);
                if (hasThreadMentionForChannel(tcId)) {
                    moreMention = true;
                    break;
                }
            }
            TextView moreTv = new TextView(getContext());
            String moreText = "+" + moreCount + " 个子区";
            if (moreMention) {
                String mentionTag = getContext().getString(R.string.last_msg_remind);
                SpannableStringBuilder ssb = new SpannableStringBuilder();
                ssb.append(moreText);
                ssb.append(" ");
                int start = ssb.length();
                ssb.append(mentionTag);
                ssb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(getContext(), R.color.reminderColor)),
                        start, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                moreTv.setText(ssb);
            } else {
                moreTv.setText(moreText);
            }
            moreTv.setTextSize(13);
            moreTv.setTextColor(Theme.colorAccount);
            LinearLayout.LayoutParams moreLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            moreLp.setMarginStart(AndroidUtilities.dp(74));
            moreLp.topMargin = AndroidUtilities.dp(4);
            moreTv.setOnClickListener(v -> {
                if (threadPreviewClickListener != null) {
                    threadPreviewClickListener.onMoreThreadsClick(groupNo);
                }
            });
            contentWrapper.addView(moreTv, moreLp);
        }

        container.addView(contentWrapper);

        // 2. 分支线视图（从头像区域弯曲到每一行，放在最底层）
        ThreadBranchView branchView = new ThreadBranchView(getContext(), showCount);
        FrameLayout.LayoutParams branchLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        container.addView(branchView, 0, branchLp);

        // 等布局完成后计算每行中心 Y 并更新分支线
        container.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                container.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                float[] centerYs = new float[rowViews.size()];
                for (int i = 0; i < rowViews.size(); i++) {
                    View row = rowViews.get(i);
                    centerYs[i] = row.getTop() + cardContainer.getTop()
                            + contentWrapper.getTop() + row.getHeight() / 2f;
                }
                branchView.setRowCenterYs(centerYs);
            }
        });
    }

    /**
     * 首次加载子区数据（cache 为空时触发）
     */
    private void loadThreadPreviews(String groupNo) {
        if (threadLoadingSet.contains(groupNo)) {
            return;
        }
        threadLoadingSet.add(groupNo);
        ThreadModel.getInstance().listThreads(groupNo, (code, msg, list) -> {
            threadLoadingSet.remove(groupNo);
            List<ThreadEntity> result = (list != null) ? list : new ArrayList<>();
            threadDataCache.put(groupNo, result);
            AndroidUtilities.runOnUIThread(() -> updateThreadPreviewDirectly(groupNo));
        });
    }

    /**
     * 重新加载子区数据，保留旧缓存直到新数据到达，避免闪烁。
     * 对比新旧数据，只有数据实际变化时才刷新 UI。
     */
    private void reloadThreadPreviews(String groupNo) {
        if (threadLoadingSet.contains(groupNo)) {
            return;
        }
        threadLoadingSet.add(groupNo);
        ThreadModel.getInstance().listThreads(groupNo, (code, msg, list) -> {
            threadLoadingSet.remove(groupNo);
            List<ThreadEntity> newData = (list != null) ? list : new ArrayList<>();
            List<ThreadEntity> oldData = threadDataCache.get(groupNo);
            threadDataCache.put(groupNo, newData);
            // 只有数据变化时才刷新 UI，避免不必要的重绘
            if (!isThreadDataEqual(oldData, newData)) {
                AndroidUtilities.runOnUIThread(() -> updateThreadPreviewDirectly(groupNo));
            }
        });
    }

    /**
     * 比较两份子区列表是否展示内容一致（前 2 个活跃子区的关键字段 + 总数）
     */
    private boolean isThreadDataEqual(List<ThreadEntity> oldList, List<ThreadEntity> newList) {
        if (oldList == null || newList == null) return oldList == newList;
        return buildThreadFingerprint(oldList).equals(buildThreadFingerprint(newList));
    }

    private String buildThreadFingerprint(List<ThreadEntity> list) {
        List<ThreadEntity> active = new ArrayList<>();
        for (ThreadEntity e : list) {
            if (e.status == 1) active.add(e);
        }
        Collections.sort(active, (a, b) -> {
            String ua = a.updated_at != null ? a.updated_at : "";
            String ub = b.updated_at != null ? b.updated_at : "";
            return ub.compareTo(ua);
        });
        StringBuilder sb = new StringBuilder();
        sb.append(active.size()).append("|");
        int count = Math.min(active.size(), 2);
        for (int i = 0; i < count; i++) {
            ThreadEntity e = active.get(i);
            sb.append(e.short_id).append(":")
              .append(e.updated_at != null ? e.updated_at : "").append(":")
              .append(e.unread_count).append(":")
              .append(e.last_message_content != null ? e.last_message_content : "").append("|");
        }
        return sb.toString();
    }

    /**
     * 直接更新可见 ViewHolder 的子区预览，不触发 notifyItemChanged，避免闪烁。
     */
    private void updateThreadPreviewDirectly(String groupNo) {
        if (getRecyclerView() == null) return;
        for (int i = 0; i < getData().size(); i++) {
            if (getData().get(i).isSectionHeader) continue;
            WKUIConversationMsg convMsg = getData().get(i).uiConversationMsg;
            if (convMsg == null) continue;
            if (convMsg.channelID.equals(groupNo)) {
                int adapterPos = i + getHeaderLayoutCount();
                RecyclerView.ViewHolder vh = getRecyclerView().findViewHolderForAdapterPosition(adapterPos);
                if (vh instanceof BaseViewHolder) {
                    showThreadPreviews((BaseViewHolder) vh, getData().get(i).uiConversationMsg);
                }
                break;
            }
        }
    }

    /**
     * 刷新指定群组的子区预览（防抖：延迟 1 秒再调 API，等服务端处理完消息更新 updated_at）
     */
    public void refreshThreadPreviews(String groupNo) {
        // 取消之前排队的同组刷新
        Runnable old = pendingRefreshTasks.remove(groupNo);
        if (old != null) {
            threadRefreshHandler.removeCallbacks(old);
        }
        Runnable task = () -> {
            pendingRefreshTasks.remove(groupNo);
            threadLoadingSet.remove(groupNo);
            reloadThreadPreviews(groupNo);
        };
        pendingRefreshTasks.put(groupNo, task);
        threadRefreshHandler.postDelayed(task, THREAD_REFRESH_DELAY_MS);
    }

    /**
     * 检查指定群组的子区是否有未处理的 @mention 提醒
     */
    public boolean hasThreadMention(String groupNo) {
        List<ThreadEntity> cachedList = threadDataCache.get(groupNo);
        if (cachedList == null || cachedList.isEmpty()) return false;
        for (ThreadEntity entity : cachedList) {
            if (entity.status != 1) continue;
            String threadChannelId = ThreadModel.getInstance().buildChannelId(groupNo, entity.short_id);
            if (hasThreadMentionForChannel(threadChannelId)) return true;
        }
        return false;
    }

    /**
     * 检查指定子区 channelId 是否有未处理的 @mention 提醒（排除自己发的）
     */
    private boolean hasThreadMentionForChannel(String threadChannelId) {
        String loginUID = WKConfig.getInstance().getUid();
        List<WKReminder> reminders = WKIM.getInstance().getReminderManager()
                .getReminders(threadChannelId, WKChannelType.COMMUNITY_TOPIC);
        if (WKReader.isNotEmpty(reminders)) {
            for (WKReminder r : reminders) {
                if (r.type == WKMentionType.WKReminderTypeMentionMe && r.done == 0
                        && (TextUtils.isEmpty(r.publisher) || !r.publisher.equals(loginUID))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 重新加载所有群组的子区预览（保留旧缓存，新数据到达后原子替换）
     */
    public void clearAndReloadThreadData() {
        // 清除待执行的防抖任务
        for (Runnable r : pendingRefreshTasks.values()) {
            threadRefreshHandler.removeCallbacks(r);
        }
        pendingRefreshTasks.clear();
        threadLoadingSet.clear();
        for (int i = 0; i < getData().size(); i++) {
            if (getData().get(i).isSectionHeader) continue;
            WKUIConversationMsg msg = getData().get(i).uiConversationMsg;
            if (msg.channelType == WKChannelType.GROUP
                    && WKConfig.getInstance().getAppConfig().thread_on == 1) {
                reloadThreadPreviews(msg.channelID);
            }
        }
    }

    private boolean isSetChatPwd(WKChannel channel) {
        if (channel == null || channel.remoteExtraMap == null || !channel.remoteExtraMap.containsKey(WKChannelExtras.chatPwdOn))
            return false;
        boolean isSetChatPwd;
        Object object = channel.remoteExtraMap.get(WKChannelExtras.chatPwdOn);
        if (object != null) {
            isSetChatPwd = (int) object == 1;
        } else {
            isSetChatPwd = false;
        }
        return isSetChatPwd;
    }

    private void showTyping(@NotNull BaseViewHolder helper, ChatConversationMsg item) {
        helper.setGone(R.id.spinKit, item.typingStartTime <= 0);
        if (item.typingStartTime > 0) {
            String content;
            if (item.uiConversationMsg.channelType == WKChannelType.GROUP) {
                String name = item.typingUserName;
                content = String.format(getContext().getString(R.string.user_is_typing), name);
            } else {
                content = getContext().getString(R.string.other_is_typing);
            }
            helper.setText(R.id.contentTv, content);
        }
        TypingView typingView = helper.getView(R.id.spinKit);
        typingView.setDotColor(ContextCompat.getColor(getContext(),R.color.color999));
        typingView.setDotRadius(AndroidUtilities.dp(3f));
        typingView.setDotSpacing(1);
    }

    private void addEvent(@NotNull BaseViewHolder helper, WKUIConversationMsg item) {
        //长按事件
        boolean top;
        boolean mute;
        if (item.getWkChannel() != null) {
            top = item.getWkChannel().top == 1;
            mute = item.getWkChannel().mute == 1;
        } else {
            top = false;
            mute = false;
        }
        List<PopupMenuItem> list = new ArrayList<>();
        if (item.getWkChannel() != null) {
            list.add(new PopupMenuItem(getContext().getString(mute ? R.string.open_channel_notice : R.string.close_channel_notice), mute ? R.mipmap.msg_unmute : R.mipmap.msg_mute, () -> iListener.onClick(ItemMenu.mute, item)));
        }
        //list.add(new ChatLongClickEntity(2, item.unreadCount > 0 ? getContext().getString(R.string.sign_read_msg) : getContext().getString(R.string.sign_unread_msg)));
        list.add(new PopupMenuItem(top ? getContext().getString(R.string.cancel_top) : getContext().getString(R.string.msg_top), top ? R.mipmap.msg_unpin : R.mipmap.msg_pin, () -> iListener.onClick(ItemMenu.top, item)));
        if (item.channelType == WKChannelType.GROUP) {
            list.add(new PopupMenuItem(getContext().getString(R.string.move_to_category), R.mipmap.msg_forward, () -> iListener.onClick(ItemMenu.moveToCategory, item)));
        }
        list.add(new PopupMenuItem(getContext().getString(R.string.delete_msg), R.mipmap.msg_delete, () -> iListener.onClick(ItemMenu.delete, item)));
        WKDialogUtils.getInstance().setViewLongClickPopup(helper.getView(R.id.contentLayout), list);
    }

    private void showCalling(final BaseViewHolder helper, ChatConversationMsg conversationMsg) {
        helper.setGone(R.id.callingIv, conversationMsg.isCalling == 0);
    }

    // ── Section header ────────────────────────────────────────────

    private void convertSectionHeader(@NonNull BaseViewHolder helper, ChatConversationMsg msg) {
        TextView titleTv = helper.getView(R.id.sectionTitle);
        TextView countTv = helper.getView(R.id.sectionCount);
        ImageView arrowIv = helper.getView(R.id.sectionArrow);
        View divider = helper.getView(R.id.sectionDivider);

        int position = helper.getAdapterPosition() - getHeaderLayoutCount();
        divider.setVisibility(position > 0 ? View.VISIBLE : View.GONE);

        titleTv.setText(msg.sectionTitle);

        boolean collapsed = collapsedSections.contains(msg.sectionId);
        arrowIv.setRotation(collapsed ? -90f : 0f);

        // 分组数量显示：折叠且有群聊时显示数字
        if (msg.sectionGroupCount > 0 && collapsed) {
            countTv.setText("(" + msg.sectionGroupCount + ")");
            countTv.setVisibility(View.VISIBLE);
        } else {
            countTv.setVisibility(View.GONE);
        }

        // 空分组点不开
        if (msg.sectionGroupCount == 0) {
            helper.itemView.setOnClickListener(null);
            helper.itemView.setClickable(false);
        } else {
            helper.itemView.setOnClickListener(v -> {
                boolean nowCollapsed = collapsedSections.contains(msg.sectionId);
                if (nowCollapsed) {
                    collapsedSections.remove(msg.sectionId);
                } else {
                    collapsedSections.add(msg.sectionId);
                }
                float to = nowCollapsed ? 0f : -90f;
                arrowIv.animate().rotation(to).setDuration(200)
                        .setInterpolator(new DecelerateInterpolator()).start();

                if (sectionToggleListener != null) {
                    sectionToggleListener.onSectionToggled(msg.sectionId, !nowCollapsed);
                }
            });
        }

        // 长按：仅用户自建分组（排除内置 sectionId）
        boolean isBuiltIn = "ungrouped".equals(msg.sectionId)
                || "channels".equals(msg.sectionId);
        if (!isBuiltIn && sectionLongClickListener != null) {
            helper.itemView.setOnLongClickListener(v -> {
                sectionLongClickListener.onSectionLongClick(msg.sectionId, msg.sectionTitle, v);
                return true;
            });
        } else {
            helper.itemView.setOnLongClickListener(null);
        }
    }

    public boolean isSectionCollapsed(String sectionId) {
        return collapsedSections.contains(sectionId);
    }

    public void setCollapsed(String sectionId, boolean collapsed) {
        if (collapsed) {
            collapsedSections.add(sectionId);
        } else {
            collapsedSections.remove(sectionId);
        }
    }

    public void setSectionToggleListener(ISectionToggleListener listener) {
        this.sectionToggleListener = listener;
    }

    public interface ISectionToggleListener {
        void onSectionToggled(String sectionId, boolean collapsed);
    }

    public void setSectionLongClickListener(ISectionLongClickListener listener) {
        this.sectionLongClickListener = listener;
    }

    public interface ISectionLongClickListener {
        void onSectionLongClick(String sectionId, String sectionTitle, View anchor);
    }

    // ── Thread preview ──────────────────────────────────────────

    public interface IThreadPreviewClickListener {
        void onThreadClick(String channelId, String groupNo, String shortId, int isJoined);
        void onMoreThreadsClick(String groupNo);
    }

    public enum ItemMenu {
        delete, top, mute, moveToCategory
    }
}
