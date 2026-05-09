package com.chat.uikit.chat.adapter;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
import com.chat.base.entity.WKChannelCustomerExtras;
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
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.base.utils.StringUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKTimeUtils;
import com.chat.uikit.R;
import com.chat.uikit.enity.ChatConversationMsg;
import com.chat.uikit.message.MsgModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.message.MessageHandler;
import com.xinbida.wukongim.db.ReminderDBManager;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelExtras;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMentionType;
import com.xinbida.wukongim.entity.WKConversationMsg;
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

    /**
     * YUJ-267 · 选中态 payload：点击群/子区后只刷 contentLayout 背景，不全 rebind，
     * 避免 DiffUtil contentHash 恒等设计引发的卡片替换链路打断。
     */
    public static final int PAYLOAD_SELECTED = 1;
    /** DiffUtil payload: section header badges/counts changed, skip full rebind */
    public static final int PAYLOAD_SECTION_BADGE = 2;
    /** Side-channel payload: refresh channel info (name/mute/forbidden/top) without rebuilding thread previews */
    public static final int PAYLOAD_CHANNEL_INFO_ONLY = 3;

    private IListener iListener;
    private IThreadPreviewClickListener threadPreviewClickListener;
    private ISectionToggleListener sectionToggleListener;
    private ISectionLongClickListener sectionLongClickListener;
    private final Set<String> collapsedSections = new HashSet<>();
    // 缓存：groupNo → 子区列表，空列表 表示已加载但无数据
    private final Map<String, List<ThreadEntity>> threadDataCache = new ConcurrentHashMap<>();
    // 缓存：groupNo → 上次渲染的结构签名，用于跳过不必要的容器重建
    private final Map<String, String> renderedThreadSigs = new ConcurrentHashMap<>();

    // Phase 2: 缓存 GradientDrawable 避免热路径分配
    private static GradientDrawable sBadgeBgNormal;
    private static GradientDrawable sBadgeBgMuted;

    private static GradientDrawable getBadgeDrawable(Context context, boolean muted) {
        if (muted) {
            if (sBadgeBgMuted == null) {
                sBadgeBgMuted = new GradientDrawable();
                sBadgeBgMuted.setCornerRadius(AndroidUtilities.dp(9f));
            }
            sBadgeBgMuted.setColor(ContextCompat.getColor(context, R.color.color999));
            return sBadgeBgMuted;
        } else {
            if (sBadgeBgNormal == null) {
                sBadgeBgNormal = new GradientDrawable();
                sBadgeBgNormal.setCornerRadius(AndroidUtilities.dp(9f));
            }
            sBadgeBgNormal.setColor(ContextCompat.getColor(context, R.color.reminderColor));
            return sBadgeBgNormal;
        }
    }

    public String findThreadName(String threadChannelId) {
        for (List<ThreadEntity> threads : threadDataCache.values()) {
            for (ThreadEntity t : threads) {
                if (threadChannelId.equals(t.channel_id)) {
                    return t.name;
                }
            }
        }
        return null;
    }
    // 标记正在加载的 groupNo，避免重复请求
    private final Set<String> threadLoadingSet = Collections.synchronizedSet(new HashSet<>());
    // 子区展开状态（默认折叠，在此集合中的表示已展开，对齐 iOS expandedThreadGroups）
    private final Set<String> expandedThreadGroups = new HashSet<>();
    // 防抖：延迟调 API，等服务端处理完消息再拉取
    private final android.os.Handler threadRefreshHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Map<String, Runnable> pendingRefreshTasks = new ConcurrentHashMap<>();
    private static final long THREAD_REFRESH_DELAY_MS = 1000;

    // ── YUJ-267 · 折叠屏分屏态选中态 ─────────────────────────────────
    // 仅在 splitMode=true（sw >= 600dp 且 Activity Embedding 激活）时渲染选中背景。
    // 手机形态 splitMode=false → 维持无选中态行为。
    private boolean splitMode;
    private String selectedChannelId;
    private byte selectedChannelType;
    private String selectedThreadChannelId;

    /**
     * 进入/离开折叠屏分屏态时调用；切换时清选中并全量刷新（分屏态下才启用）。
     */
    public void setSplitMode(boolean splitMode) {
        if (this.splitMode == splitMode) return;
        this.splitMode = splitMode;
        if (!splitMode) {
            // 离开分屏态时清掉选中标记，避免 rotate 回手机态仍有选中 bg
            selectedChannelId = null;
            selectedChannelType = 0;
            selectedThreadChannelId = null;
        }
        notifyDataSetChanged();
    }

    public boolean isSplitMode() {
        return splitMode;
    }

    /**
     * 设置群/私聊的选中态；只刷影响到的两行（旧选中 + 新选中），走 PAYLOAD_SELECTED
     * 增量更新，不触碰内容/子区卡片。splitMode=false 时仅记录值但不触发 UI 刷新。
     */
    public void setSelected(String channelId, byte channelType) {
        String oldId = selectedChannelId;
        byte oldType = selectedChannelType;
        if (TextUtils.equals(oldId, channelId) && oldType == channelType && selectedThreadChannelId == null) {
            return;
        }
        selectedChannelId = channelId;
        selectedChannelType = channelType;
        // 选中群时若之前选中的是子区，自动清掉子区选中
        selectedThreadChannelId = null;
        if (!splitMode) return;
        refreshSelectionRow(oldId, oldType);
        refreshSelectionRow(channelId, channelType);
    }

    /**
     * 设置子区选中态。子区点击时：父群保持不选，只高亮子区行。这个语义对齐 iOS——
     * 分屏右侧是子区时，左侧父群卡片仍是普通背景，子区 rowView 单独标选中。
     */
    public void setSelectedThread(String threadChannelId) {
        if (TextUtils.equals(selectedThreadChannelId, threadChannelId)
                && selectedChannelId == null) {
            return;
        }
        String oldThreadId = selectedThreadChannelId;
        String oldGroupId = selectedChannelId;
        byte oldGroupType = selectedChannelType;
        selectedThreadChannelId = threadChannelId;
        // 子区选中时，若之前整行选中的是群/私聊，也要刷掉那行
        selectedChannelId = null;
        selectedChannelType = 0;
        if (!splitMode) return;
        if (oldGroupId != null) refreshSelectionRow(oldGroupId, oldGroupType);
        // 刷新子区所属父群那一行（子区签名变更需走重建）
        refreshParentGroupForThread(oldThreadId);
        refreshParentGroupForThread(threadChannelId);
    }

    /**
     * 清掉选中态（切 Space / 切 tab / 进入详情页 back 回来不匹配时调用）。
     */
    public void clearSelected() {
        if (selectedChannelId == null && selectedThreadChannelId == null) return;
        String oldId = selectedChannelId;
        byte oldType = selectedChannelType;
        String oldThreadId = selectedThreadChannelId;
        selectedChannelId = null;
        selectedChannelType = 0;
        selectedThreadChannelId = null;
        if (!splitMode) return;
        if (oldId != null) refreshSelectionRow(oldId, oldType);
        if (oldThreadId != null) refreshParentGroupForThread(oldThreadId);
    }

    public boolean isRowSelected(String channelId, byte channelType) {
        return splitMode && channelId != null
                && channelId.equals(selectedChannelId)
                && channelType == selectedChannelType;
    }

    public boolean isThreadRowSelected(String threadChannelId) {
        return splitMode && threadChannelId != null
                && threadChannelId.equals(selectedThreadChannelId);
    }

    /** 定向刷新选中行（走 PAYLOAD_SELECTED，只改 background）。 */
    private void refreshSelectionRow(String channelId, byte channelType) {
        if (channelId == null) return;
        for (int i = 0, size = getData().size(); i < size; i++) {
            ChatConversationMsg item = getData().get(i);
            if (item == null || item.isSectionHeader || item.uiConversationMsg == null) continue;
            WKUIConversationMsg ui = item.uiConversationMsg;
            if (channelId.equals(ui.channelID) && channelType == ui.channelType) {
                notifyItemChanged(i + getHeaderLayoutCount(), PAYLOAD_SELECTED);
                break;
            }
        }
    }

    /** 子区选中/取消时刷其父群 compact 行（子区卡片依赖 signature 重建）。 */
    private void refreshParentGroupForThread(String threadChannelId) {
        if (threadChannelId == null) return;
        // threadChannelId 形如 "{groupNo}____{shortId}"，父群号即前缀
        int idx = threadChannelId.indexOf("____");
        String parentGroupNo = idx > 0 ? threadChannelId.substring(0, idx) : null;
        if (parentGroupNo == null) return;
        for (int i = 0, size = getData().size(); i < size; i++) {
            ChatConversationMsg item = getData().get(i);
            if (item == null || item.isSectionHeader || item.uiConversationMsg == null) continue;
            WKUIConversationMsg ui = item.uiConversationMsg;
            if (parentGroupNo.equals(ui.channelID) && ui.channelType == WKChannelType.GROUP) {
                // 清掉本 ViewHolder 缓存签名，强制下一次 convertCompact 重建子区 card
                // （因为选中态影响 rowView background，但不在现有 signature 里）
                notifyItemChanged(i + getHeaderLayoutCount(), PAYLOAD_SELECTED);
                break;
            }
        }
    }

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
        // YUJ-267 · showChannel 已根据 top 覆盖过 contentLayout 背景；最后再走选中态
        // 覆写，保证 selected > top > normal 的优先级。
        applySelectedBackground(helper, item);
    }

    private void convertCompact(@NonNull BaseViewHolder helper, ChatConversationMsg conversationMsg) {
        WKUIConversationMsg item = conversationMsg.uiConversationMsg;

        // 群聊头像
        AvatarView compactAvatar = helper.getView(R.id.compactAvatarView);
        compactAvatar.setSize(52);
        if (item.getWkChannel() != null) {
            compactAvatar.showAvatar(item.getWkChannel());
        } else {
            compactAvatar.showAvatar(item.channelID, item.channelType);
        }

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
        applyExternalGroupTag(helper, item);

        // 未读数
        setUnreadCount(helper, conversationMsg, false);

        // 置顶背景
        boolean isTop = item.getWkChannel() != null && item.getWkChannel().top == 1;
        helper.setBackgroundResource(R.id.contentLayout, isTop ? R.drawable.home_bg : R.drawable.layout_bg);
        // YUJ-267 · 分屏态下被选中的群覆盖为 selected_bg
        applySelectedBackground(helper, item);

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

        // 子区：默认折叠，点击图标展开/折叠（对齐 iOS）
        ImageView threadToggleIv = helper.getView(R.id.threadToggleIv);
        FrameLayout container = helper.getView(R.id.threadPreviewContainer);
        boolean hasThreads = hasActiveThreads(item.channelID);
        if (hasThreads) {
            threadToggleIv.setVisibility(View.VISIBLE);
            threadToggleIv.setColorFilter(new PorterDuffColorFilter(
                    getThreadToggleColor(item), PorterDuff.Mode.SRC_IN));
            threadToggleIv.setOnClickListener(v -> {
                boolean wasExpanded = isThreadExpanded(item.channelID);
                toggleThreadExpanded(item.channelID);
                int pos = helper.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    notifyItemChanged(pos);
                }
            });
            if (isThreadExpanded(item.channelID)) {
                showThreadPreviews(helper, item);
            } else {
                container.removeAllViews();
                container.setVisibility(View.GONE);
                renderedThreadSigs.remove(item.channelID);
            }
        } else {
            threadToggleIv.setVisibility(View.GONE);
            threadToggleIv.setOnClickListener(null);
            showThreadPreviews(helper, item);
        }
    }

    public void addListener(IListener iItemMenuClick) {
        this.iListener = iItemMenuClick;
    }

    public void setThreadPreviewClickListener(IThreadPreviewClickListener listener) {
        this.threadPreviewClickListener = listener;
    }

    private boolean isThreadExpanded(String channelId) {
        return expandedThreadGroups.contains(channelId);
    }

    private void toggleThreadExpanded(String channelId) {
        if (expandedThreadGroups.contains(channelId)) {
            expandedThreadGroups.remove(channelId);
        } else {
            expandedThreadGroups.add(channelId);
        }
        saveExpandedState();
    }

    private void saveExpandedState() {
        String uid = WKConfig.getInstance().getUid();
        String key = uid + "_expanded_thread_groups";
        String value = TextUtils.join(",", expandedThreadGroups);
        getContext().getSharedPreferences("thread_prefs", Context.MODE_PRIVATE)
                .edit().putString(key, value).apply();
    }

    public void restoreExpandedState(Context context) {
        String uid = WKConfig.getInstance().getUid();
        String key = uid + "_expanded_thread_groups";
        String value = context.getSharedPreferences("thread_prefs", Context.MODE_PRIVATE)
                .getString(key, "");
        expandedThreadGroups.clear();
        if (!TextUtils.isEmpty(value)) {
            for (String id : value.split(",")) {
                if (!TextUtils.isEmpty(id)) expandedThreadGroups.add(id);
            }
        }
    }

    @Override
    protected void convert(@NotNull BaseViewHolder baseViewHolder, ChatConversationMsg uiConversationMsg, @NotNull List<?> payloads) {
        // Section header payload: 只刷新 badge/count，不重建整个 header
        if (baseViewHolder.getItemViewType() == TYPE_SECTION_HEADER) {
            for (Object p : payloads) {
                if (p instanceof Integer && ((Integer) p) == PAYLOAD_SECTION_BADGE) {
                    refreshSectionBadges(baseViewHolder, uiConversationMsg);
                    return;
                }
            }
            return;
        }
        // YUJ-267 · 选中态 payload：只刷背景，不整 rebind（DiffUtil contentHash
        // 对普通行恒等设计要求选中变化必须靠显式 payload 驱动）。
        for (Object p : payloads) {
            if (p instanceof Integer && ((Integer) p) == PAYLOAD_SELECTED) {
                WKUIConversationMsg ui = uiConversationMsg != null ? uiConversationMsg.uiConversationMsg : null;
                if (ui != null) {
                    boolean isTop = ui.getWkChannel() != null && ui.getWkChannel().top == 1;
                    baseViewHolder.setBackgroundResource(R.id.contentLayout,
                            isTop ? R.drawable.home_bg : R.drawable.layout_bg);
                    applySelectedBackground(baseViewHolder, ui);
                    if (baseViewHolder.getItemViewType() == TYPE_COMPACT) {
                        FrameLayout threadContainer = baseViewHolder.getView(R.id.threadPreviewContainer);
                        if (threadContainer != null) {
                            renderedThreadSigs.remove(ui.channelID);
                            if (isThreadExpanded(ui.channelID)) {
                                showThreadPreviews(baseViewHolder, ui);
                            }
                        }
                    }
                }
                return;
            }
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

    /**
     * Section header 增量刷新：只更新未读角标、@提醒、分组计数，不重建整个 header。
     */
    private void refreshSectionBadges(@NonNull BaseViewHolder helper, ChatConversationMsg msg) {
        if (msg == null) return;
        boolean collapsed = collapsedSections.contains(msg.sectionId);

        TextView countTv = helper.getView(R.id.sectionCount);
        if (msg.sectionGroupCount > 0 && collapsed) {
            countTv.setText("(" + msg.sectionGroupCount + ")");
            countTv.setVisibility(View.VISIBLE);
        } else {
            countTv.setVisibility(View.GONE);
        }

        TextView mentionTv = helper.getView(R.id.sectionMentionTv);
        if (collapsed && msg.sectionHasMention) {
            mentionTv.setText(getContext().getString(R.string.last_msg_remind));
            mentionTv.setVisibility(View.VISIBLE);
        } else {
            mentionTv.setVisibility(View.GONE);
        }

        TextView unreadBadge = helper.getView(R.id.sectionUnreadBadge);
        if (collapsed && msg.sectionUnreadCount > 0) {
            unreadBadge.setText(msg.sectionUnreadCount > 99 ? "99+" : String.valueOf(msg.sectionUnreadCount));
            unreadBadge.setVisibility(View.VISIBLE);
        } else {
            unreadBadge.setVisibility(View.GONE);
        }
    }

    /**
     * YUJ-267 · 根据分屏态 + 当前选中 channel 覆盖 contentLayout 背景。
     * 优先级：selected > top > normal。splitMode=false 时此方法无副作用。
     */
    private void applySelectedBackground(@NonNull BaseViewHolder helper, WKUIConversationMsg item) {
        if (!splitMode || item == null) return;
        if (isRowSelected(item.channelID, item.channelType)) {
            helper.setBackgroundResource(R.id.contentLayout, R.drawable.chat_conv_selected_bg);
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
            refreshCompactChannelInfo(baseViewHolder, chatConversationMsg);
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

    /**
     * 仅刷新 compact 行的频道基本信息（名称、免打扰、禁言、置顶背景、头像），
     * 不重建子区预览容器。避免 channel info 更新时触发昂贵的 showThreadPreviews。
     */
    private void refreshCompactChannelInfo(@NotNull BaseViewHolder helper, ChatConversationMsg conversationMsg) {
        WKUIConversationMsg item = conversationMsg.uiConversationMsg;
        if (item == null) return;

        // 头像
        AvatarView compactAvatar = helper.getView(R.id.compactAvatarView);
        compactAvatar.setSize(52);
        if (item.getWkChannel() != null) {
            compactAvatar.showAvatar(item.getWkChannel());
        } else {
            compactAvatar.showAvatar(item.channelID, item.channelType);
        }

        // 频道名
        String showName = "";
        if (item.getWkChannel() != null) {
            showName = TextUtils.isEmpty(item.getWkChannel().channelRemark)
                    ? item.getWkChannel().channelName
                    : item.getWkChannel().channelRemark;
            if (TextUtils.isEmpty(showName)) {
                showName = getContext().getString(R.string.chat);
            }
        } else {
            showName = getContext().getString(R.string.chat);
        }
        helper.setText(R.id.nameTv, showName);
        applyExternalGroupTag(helper, item);

        // 置顶背景
        boolean isTop = item.getWkChannel() != null && item.getWkChannel().top == 1;
        helper.setBackgroundResource(R.id.contentLayout, isTop ? R.drawable.home_bg : R.drawable.layout_bg);
        applySelectedBackground(helper, item);

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

        // 重建长按菜单（mute/top 状态变化后菜单文案需更新）
        addEvent(helper, item);
    }    public interface IListener {
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
        // 链接卡片消息：显示友好预览
        if (content != null && content.startsWith("[链接]")) {
            content = parseLinkPreview(content);
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

    private String parseLinkPreview(String content) {
        try {
            String jsonStr = content.substring("[链接]".length());
            org.json.JSONObject json = new org.json.JSONObject(jsonStr);
            String title = json.optString("title", "");
            if (!TextUtils.isEmpty(title)) {
                return "[链接] " + title;
            }
            String url = json.optString("url", "");
            if (!TextUtils.isEmpty(url)) {
                android.net.Uri uri = android.net.Uri.parse(url);
                String host = uri.getHost();
                return "[链接] " + (host != null ? host : url);
            }
        } catch (Exception ignored) {
        }
        return "[链接]";
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
        // YUJ-219-B · Layer B · 渲染层 Space 过滤兜底（对齐 Web Model.tsx :: unread getter
        // + iOS WKConversationWrapModel.spaceFilteredLastMessage 的 unread 清零语义）：
        // 即使 push-path Layer A gate 挡住了 bump，WKSDK 本地 DB 在冷启动回放时仍可能
        // 给 allConversations 带进跨 Space 的 unreadCount。这里做最后一层擦除。
        int unread = getRenderUnreadCount(item);
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
        // YUJ-219-B · Layer B · 渲染层时间戳 Space 过滤。跨 Space 消息污染时返回 0
        // （即不显示最近时间），避免列表因被跨 Space push bump 而冒顶排序。
        long msgTimestamp;
        if (com.chat.base.space.ConversationPreviewFilter.isMessageCrossSpace(item)) {
            msgTimestamp = 0;
        } else {
            msgTimestamp = item.lastMsgTimestamp;
            if (item.getWkMsg() != null) {
                if (item.getWkMsg().remoteExtra.editedAt != 0) {
                    msgTimestamp = item.getWkMsg().remoteExtra.editedAt;
                }
            }
        }
        String chatTime = msgTimestamp > 0
                ? WKTimeUtils.getInstance().getNewChatTime(msgTimestamp * 1000)
                : "";
        helper.setText(R.id.timeTv, chatTime);
    }

    // 系统 Bot：会话列表预览需按 Space 过滤（YUJ-219-A3 / YUJ-219-B：集合改走
    // {@link com.chat.base.space.SystemBotsFallback#isSystemBot} 消除三端硬编码漂移，
    // 动态白名单见 SystemBotsFallback.getSystemBotIds）

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
        if (!com.chat.base.space.SystemBotsFallback.isSystemBot(item.channelID)) return null;
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
     *
     * <p>YUJ-219-B：委托给 {@link com.chat.base.space.ConversationPreviewFilter#getSpaceFilteredUnread(WKUIConversationMsg)}
     * 以覆盖 SystemBot + 无 space_id 隐藏口径 / GROUP 跨 Space 兜底 / TOPIC 分支。
     */
    public int getEffectiveUnreadCount(ChatConversationMsg item) {
        if (item == null || item.uiConversationMsg == null) return 0;
        return com.chat.base.space.ConversationPreviewFilter
                .getSpaceFilteredUnread(item.uiConversationMsg);
    }

    /**
     * YUJ-219-B · Layer B · 渲染层未读数：集合子区 + Space 过滤双兜底。
     *
     * <p>{@link ChatConversationMsg#getUnReadCount()} 现有实现会把 childList（子区）
     * 的 unread 汇总到父群；但裸读 {@code uiConversationMsg.unreadCount} 不过 Space
     * 过滤。此处对顶层 entry 再过一次 {@link com.chat.base.space.ConversationPreviewFilter}：
     * 顶层（父群 / DM / SystemBot）若判定为跨 Space 污染 → 直接返回 0；
     * 否则维持现有（含子区）汇总语义。
     */
    private int getRenderUnreadCount(ChatConversationMsg item) {
        if (item == null || item.uiConversationMsg == null) return 0;
        if (com.chat.base.space.ConversationPreviewFilter.isMessageCrossSpace(item.uiConversationMsg)) {
            return 0;
        }
        return item.getUnReadCount();
    }

    /**
     * YUJ-219-B · Layer B · 渲染层 wkMsg 获取：跨 Space 污染时返回 null，避免
     * {@link #getContent(WKMsg)} 直接渲染出另一 Space 的原文。
     * 对齐 iOS {@code spaceFilteredLastMessage} / Web {@code getSpaceFilteredLastMessage}。
     */
    @Nullable
    private WKMsg getRenderWkMsg(@Nullable WKUIConversationMsg item) {
        return com.chat.base.space.ConversationPreviewFilter.getSpaceFilteredWkMsg(item);
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
            contentTv.setTextColor(ContextCompat.getColor(getContext(), R.color.color999));
            contentTv.setTypeface(null, Typeface.NORMAL);
            contentTv.setVisibility(View.GONE);
            return;
        }
        contentTv.setVisibility(View.VISIBLE);
        String mentionTag = getContext().getString(R.string.last_msg_remind);
        WKUIConversationMsg item = conversationMsg.uiConversationMsg;
        // YUJ-219-B · Layer B · 跨 Space 污染时用 null wkMsg，getContent 返回空串
        WKMsg renderMsg = getRenderWkMsg(item);
        String msgContent = getContent(renderMsg);
        String fromName = getFromName(item.channelType, renderMsg);
        String preview = TextUtils.isEmpty(fromName) ? msgContent : fromName + "：" + msgContent;
        // 整行高亮（对齐子区 threadMentionTv 样式）
        contentTv.setTextColor(ContextCompat.getColor(getContext(), R.color.reminderColor));
        contentTv.setTypeface(null, Typeface.BOLD);
        contentTv.setText(mentionTag + " " + preview);
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
            // YUJ-219-B · Layer B · 非 SystemBot 分支也过 render-time Space 过滤兜底。
            // SystemBot 走 findSystemBotSpaceContent（含 DB 回查当前 Space 历史）；
            // 其它频道类型若判定为跨 Space 污染（冷启动 race / DB 回放），
            // getRenderWkMsg 返回 null 后 getContent 返回空串 → preview 为空。
            WKMsg renderMsg = getRenderWkMsg(item);
            content = getContent(renderMsg);
            String fromName = getFromName(item.channelType, renderMsg);
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
                    mention = true;
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
        if (isSetChatPwd) {
            if (!TextUtils.isEmpty(draft))
                draft = "❊❊❊❊❊❊❊❊❊❊❊❊❊";
        }

        // 复用 XML 中预定义的 TextView，避免每次 bind 分配新 View
        TextView mentionTv = helper.getView(R.id.mentionTv);
        TextView draftTv = helper.getView(R.id.draftTv);
        TextView approveTv = helper.getView(R.id.approveTv);

        if (mention) {
            mentionTv.setText(R.string.last_msg_remind);
            mentionTv.setVisibility(View.VISIBLE);
        } else {
            mentionTv.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(draft)) {
            draftTv.setText(R.string.last_msg_draft);
            draftTv.setVisibility(View.VISIBLE);
            MoonUtil.identifyFaceExpression(getContext(), contentTv, draft, MoonUtil.SMALL_SCALE);
        } else {
            draftTv.setVisibility(View.GONE);
            showContent(helper, item.uiConversationMsg);
        }

        if (!TextUtils.isEmpty(approveContent)) {
            approveTv.setText(approveContent);
            approveTv.setVisibility(View.VISIBLE);
        } else {
            approveTv.setVisibility(View.GONE);
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
        avatarView.setSize(52);
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
        applyExternalGroupTag(helper, item);
    }

    /**
     * 设置「外部」Tag 显隐（EP5 · YUJ-90）。
     *
     * 规则（对齐 dmwork-web PR #980）：
     * - 仅 ChannelTypeGroup 生效；私聊 / 子区 永远隐藏
     * - 读取 channel.remoteExtraMap[is_external_group]，Number==1 或 Boolean==true → 显示
     * - channel 或 extraMap 为 null 时隐藏
     *
     * 风险提示（R1）：单元测试必须覆盖字段透传，防止模型层缺失导致 UI 静默失败。
     */
    private void applyExternalGroupTag(@NotNull BaseViewHolder helper, WKUIConversationMsg item) {
        View tag = helper.getView(R.id.externalGroupTagTv);
        if (tag == null) return;
        boolean show = item != null
                && item.channelType == WKChannelType.GROUP
                && isExternalGroup(item.getWkChannel());
        tag.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    static boolean isExternalGroup(WKChannel channel) {
        if (channel == null || channel.remoteExtraMap == null) return false;
        Object v = channel.remoteExtraMap.get(WKChannelCustomerExtras.isExternalGroup);
        if (v instanceof Number) return ((Number) v).intValue() == 1;
        if (v instanceof Boolean) return (Boolean) v;
        return false;
    }

    private boolean hasActiveThreads(String groupNo) {
        List<ThreadEntity> cached = threadDataCache.get(groupNo);
        if (cached == null) {
            loadThreadPreviewsSilent(groupNo);
            return false;
        }
        for (ThreadEntity e : cached) {
            if (e.status == 1) return true;
        }
        return false;
    }

    private void loadThreadPreviewsSilent(String groupNo) {
        if (threadLoadingSet.contains(groupNo)) return;
        loadThreadPreviews(groupNo);
    }

    /**
     * 展示子区预览（最多 2 个最近活跃子区 + "+N 个子区" 折叠行）
     * 参考 iOS WKConversationGroupThreadCell：带分支线 + 圆角卡片容器 + 未读数气泡
     *
     * YUJ-261 · 数据签名 skip-rebuild：对当前 groupNo 的子区活跃集 + 未读 + mute + mention
     * 组合生成签名，若 container 已渲染过相同签名则直接跳过 removeAllViews() + inflate，
     * 避免 notifyItemChanged 时 rowView 被替换导致 touch 链路被 cancel。
     */
    private void showThreadPreviews(@NotNull BaseViewHolder helper, WKUIConversationMsg item) {
        FrameLayout container = helper.getView(R.id.threadPreviewContainer);

        if (item.channelType != WKChannelType.GROUP
                || WKConfig.getInstance().getAppConfig().thread_on != 1) {
            container.removeAllViews();
            container.setVisibility(View.GONE);
            return;
        }

        String groupNo = item.channelID;
        List<ThreadEntity> cachedList = threadDataCache.get(groupNo);
        if (cachedList == null) {
            container.removeAllViews();
            container.setVisibility(View.GONE);
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
            container.removeAllViews();
            container.setVisibility(View.GONE);
            renderedThreadSigs.remove(groupNo);
            return;
        }
        // updated_at 是 ISO 格式字符串，可直接用字符串比较排序
        Collections.sort(activeList, (a, b) -> {
            String ua = a.updated_at != null ? a.updated_at : "";
            String ub = b.updated_at != null ? b.updated_at : "";
            return ub.compareTo(ua);
        });

        // 3天内活跃的子区全部展示，其余折叠（对齐 iOS: conv.lastMsgTimestamp）
        long threeDaysAgoSec = System.currentTimeMillis() / 1000 - 3L * 24 * 60 * 60;
        List<ThreadEntity> recentList = new ArrayList<>();
        List<ThreadEntity> inactiveList = new ArrayList<>();
        for (ThreadEntity te : activeList) {
            String tcId = ThreadModel.getInstance().buildChannelId(groupNo, te.short_id);
            WKUIConversationMsg conv = WKIM.getInstance().getConversationManager()
                    .getUIConversationMsg(tcId, WKChannelType.COMMUNITY_TOPIC);
            long lastTs = conv != null ? conv.lastMsgTimestamp : 0;
            if (lastTs > threeDaysAgoSec) {
                recentList.add(te);
            } else {
                inactiveList.add(te);
            }
        }
        int showCount = recentList.size();
        if (showCount == 0) showCount = Math.min(activeList.size(), 2);

        // YUJ-261 · 生成数据签名；若同一 ViewHolder 的 container 已渲染过该签名，
        // 则跳过所有 removeAllViews() + inflate，避免 onClickListener 持有的 rowView
        // 被替换导致 ACTION_DOWN → CANCEL。
        String newSig = buildThreadPreviewSignature(groupNo, activeList, inactiveList, showCount);
        String existingSig = renderedThreadSigs.get(groupNo);
        Object renderedGroupNo = container.getTag(R.id.threadPreviewContainer);
        if (groupNo.equals(renderedGroupNo) && newSig.equals(existingSig) && container.getChildCount() > 0) {
            container.setVisibility(View.VISIBLE);
            updateThreadBadgesInPlace(container, groupNo, activeList, showCount);
            return;
        }

        // 数据变化或首次渲染 → 重建内容（保留 ThreadBranchView 复用）。
        removeContentViews(container);
        container.setVisibility(View.VISIBLE);
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

            // 子区独立 mute 状态
            WKChannel threadChannel = WKIM.getInstance().getChannelManager()
                    .getChannel(threadChannelId, WKChannelType.COMMUNITY_TOPIC);
            boolean threadMute = threadChannel != null && threadChannel.mute == 1;
            ImageView muteIv = rowView.findViewById(R.id.threadMuteIv);
            if (threadMute) {
                muteIv.setVisibility(View.VISIBLE);
                Theme.setColorFilter(muteIv, ContextCompat.getColor(getContext(), R.color.popupTextColor));
            } else {
                muteIv.setVisibility(View.GONE);
            }

            if (unread > 0) {
                unreadBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
                unreadBadge.setBackground(getBadgeDrawable(getContext(), threadMute).getConstantState().newDrawable().mutate());
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

            // YUJ-261 · 子区行点击走 SingleClickUtil（per-view 300ms，PR#187 已修为默认模式）。
            // 与 filterAndDisplay debounce 叠加彻底消除「点一次没反应」。
            final String finalThreadChannelId = threadChannelId;
            final ThreadEntity finalEntity = entity;
            SingleClickUtil.onSingleClick(rowView, v -> {
                if (threadPreviewClickListener != null) {
                    threadPreviewClickListener.onThreadClick(finalThreadChannelId, groupNo,
                            finalEntity.short_id, finalEntity.is_joined);
                }
            });
            // 长按弹出通知开关菜单
            boolean finalThreadMute = threadMute;
            List<PopupMenuItem> menuItems = new ArrayList<>();
            menuItems.add(new PopupMenuItem(
                    getContext().getString(finalThreadMute ? R.string.open_channel_notice : R.string.close_channel_notice),
                    finalThreadMute ? R.mipmap.msg_unmute : R.mipmap.msg_mute,
                    () -> {
                        if (threadPreviewClickListener != null) {
                            threadPreviewClickListener.onThreadLongPress(finalThreadChannelId, finalEntity.name, rowView);
                        }
                    }));
            WKDialogUtils.getInstance().setViewLongClickPopup(rowView, menuItems);

            // YUJ-267 · 子区行选中态：当前右侧正在看此子区时高亮。
            // 仅分屏态生效；手机态 isThreadRowSelected 恒为 false。
            if (isThreadRowSelected(threadChannelId)) {
                rowView.setBackgroundResource(R.drawable.chat_conv_selected_bg);
            } else {
                rowView.setBackground(null);
            }

            cardContainer.addView(rowView);
            rowViews.add(rowView);
        }

        contentWrapper.addView(cardContainer, cardLp);

        // "+N 个子区" 放在卡片外部
        if (!inactiveList.isEmpty()) {
            int moreCount = inactiveList.size();
            // 检查未展示子区是否有 @mention，并汇总未读数
            boolean moreMention = false;
            int moreUnread = 0;
            for (int i = 0; i < inactiveList.size(); i++) {
                ThreadEntity te = inactiveList.get(i);
                String tcId = ThreadModel.getInstance().buildChannelId(groupNo, te.short_id);
                if (hasThreadMentionForChannel(tcId)) {
                    moreMention = true;
                }
                int u = te.unread_count;
                WKUIConversationMsg tc = WKIM.getInstance().getConversationManager()
                        .getUIConversationMsg(tcId, WKChannelType.COMMUNITY_TOPIC);
                if (tc != null) {
                    u = tc.unreadCount;
                }
                moreUnread += u;
            }

            // 水平容器："+N 个子区" 文字 + 未读气泡
            LinearLayout moreRow = new LinearLayout(getContext());
            moreRow.setOrientation(LinearLayout.HORIZONTAL);
            moreRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams moreLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            moreLp.setMarginStart(AndroidUtilities.dp(74));
            moreLp.setMarginEnd(AndroidUtilities.dp(15));
            moreLp.topMargin = AndroidUtilities.dp(4);

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
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            moreRow.addView(moreTv, tvLp);

            // 未读数气泡（与子区行样式一致）
            if (moreUnread > 0) {
                TextView unreadBadge = new TextView(getContext());
                unreadBadge.setText(moreUnread > 99 ? "99+" : String.valueOf(moreUnread));
                unreadBadge.setTextSize(11);
                unreadBadge.setTextColor(ContextCompat.getColor(getContext(), R.color.white));
                unreadBadge.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                unreadBadge.setGravity(Gravity.CENTER);
                unreadBadge.setBackgroundResource(R.drawable.thread_unread_badge_bg);
                unreadBadge.setMinWidth(AndroidUtilities.dp(18));
                unreadBadge.setHeight(AndroidUtilities.dp(18));
                unreadBadge.setPadding(AndroidUtilities.dp(5), 0, AndroidUtilities.dp(5), 0);
                LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(18));
                badgeLp.setMarginStart(AndroidUtilities.dp(5));
                moreRow.addView(unreadBadge, badgeLp);
            }

            SingleClickUtil.onSingleClick(moreRow, v -> {
                if (threadPreviewClickListener != null) {
                    threadPreviewClickListener.onMoreThreadsClick(groupNo);
                }
            });
            contentWrapper.addView(moreRow, moreLp);
        }

        container.addView(contentWrapper);

        // 2. 分支线视图：复用已有实例或新建（从头像区域弯曲到每一行，放在最底层）
        ThreadBranchView branchView = findBranchView(container);
        if (branchView != null) {
            branchView.setRowCount(showCount);
        } else {
            branchView = new ThreadBranchView(getContext(), showCount);
            FrameLayout.LayoutParams branchLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            container.addView(branchView, 0, branchLp);
        }

        // 记录本次签名到 adapter 级缓存 + ViewHolder tag 标识当前群
        renderedThreadSigs.put(groupNo, newSig);
        container.setTag(R.id.threadPreviewContainer, groupNo);
    }

    /** 从 container 中查找已有的 ThreadBranchView（复用避免重建）。 */
    private ThreadBranchView findBranchView(ViewGroup container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            if (container.getChildAt(i) instanceof ThreadBranchView) {
                return (ThreadBranchView) container.getChildAt(i);
            }
        }
        return null;
    }

    /** 移除 container 中除 ThreadBranchView 以外的所有子 View（保留分支线复用）。 */
    private void removeContentViews(FrameLayout container) {
        for (int i = container.getChildCount() - 1; i >= 0; i--) {
            if (!(container.getChildAt(i) instanceof ThreadBranchView)) {
                container.removeViewAt(i);
            }
        }
    }

    /**
     * YUJ-261 · 子区预览数据签名：覆盖所有影响 UI 展示的字段（子区列表 + 未读 + mute +
     * mention + showCount + inactiveList 聚合），用于 skip-rebuild 对比。
     */
    private String buildThreadPreviewSignature(String groupNo,
                                               List<ThreadEntity> activeList,
                                               List<ThreadEntity> inactiveList,
                                               int showCount) {
        StringBuilder sb = new StringBuilder(64);
        sb.append(groupNo).append('#').append(showCount).append('|');
        for (int i = 0; i < showCount && i < activeList.size(); i++) {
            ThreadEntity t = activeList.get(i);
            sb.append(t.short_id).append(':')
              .append(t.name != null ? t.name : "").append(':')
              .append(t.is_joined).append('|');
        }
        sb.append("+").append(inactiveList.size());
        return sb.toString();
    }

    private void updateThreadBadgesInPlace(FrameLayout container, String groupNo,
                                           List<ThreadEntity> activeList, int showCount) {
        if (container.getChildCount() < 2) return;
        View contentWrapperView = container.getChildAt(1);
        if (!(contentWrapperView instanceof LinearLayout)) return;
        LinearLayout contentWrapper = (LinearLayout) contentWrapperView;
        if (contentWrapper.getChildCount() == 0) return;
        View cardContainerView = contentWrapper.getChildAt(0);
        if (!(cardContainerView instanceof LinearLayout)) return;
        LinearLayout cardContainer = (LinearLayout) cardContainerView;

        int rowIndex = 0;
        for (int i = 0; i < cardContainer.getChildCount() && rowIndex < showCount; i++) {
            View child = cardContainer.getChildAt(i);
            if (child.findViewById(R.id.threadUnreadBadge) == null) continue;

            if (rowIndex >= activeList.size()) break;
            ThreadEntity entity = activeList.get(rowIndex);
            String threadChannelId = ThreadModel.getInstance().buildChannelId(groupNo, entity.short_id);
            WKUIConversationMsg threadConv = WKIM.getInstance().getConversationManager()
                    .getUIConversationMsg(threadChannelId, WKChannelType.COMMUNITY_TOPIC);
            int unread = threadConv != null ? threadConv.unreadCount : entity.unread_count;

            WKChannel threadChannel = WKIM.getInstance().getChannelManager()
                    .getChannel(threadChannelId, WKChannelType.COMMUNITY_TOPIC);
            boolean threadMute = threadChannel != null && threadChannel.mute == 1;

            TextView unreadBadge = child.findViewById(R.id.threadUnreadBadge);
            if (unread > 0) {
                unreadBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
                unreadBadge.setBackground(getBadgeDrawable(getContext(), threadMute).getConstantState().newDrawable().mutate());
                unreadBadge.setVisibility(View.VISIBLE);
            } else {
                unreadBadge.setVisibility(View.GONE);
            }

            ImageView muteIv = child.findViewById(R.id.threadMuteIv);
            if (muteIv != null) {
                if (threadMute) {
                    muteIv.setVisibility(View.VISIBLE);
                    Theme.setColorFilter(muteIv, ContextCompat.getColor(getContext(), R.color.popupTextColor));
                } else {
                    muteIv.setVisibility(View.GONE);
                }
            }

            TextView mentionTv = child.findViewById(R.id.threadMentionTv);
            if (mentionTv != null) {
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
            }

            rowIndex++;
        }
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
            // 对齐 iOS：子区加载后检查 @所有人消息，补创建本地 reminder
            // saveOrUpdateReminders 会自动触发 addOnNewReminderListener → filterAndDisplay
            checkThreadMentionAll(groupNo, result);
            AndroidUtilities.runOnUIThread(() -> updateThreadPreviewDirectly(groupNo));
        });
    }

    /**
     * 子区加载后检查 @所有人：遍历有未读的子区，查本地会话最后一条消息的 mentionAll，
     * 如果有且无对应 reminder，则创建本地 reminder（对齐 iOS 客户端补偿逻辑）。
     * @return 是否创建了新的 reminder
     */
    private boolean checkThreadMentionAll(String groupNo, List<ThreadEntity> threads) {
        String loginUID = WKConfig.getInstance().getUid();
        boolean created = false;
        for (ThreadEntity entity : threads) {
            if (entity.status != 1 || entity.unread_count <= 0) continue;
            String threadChannelId = ThreadModel.getInstance().buildChannelId(groupNo, entity.short_id);
            // 已有 reminder 就跳过
            if (hasThreadMentionForChannel(threadChannelId)) continue;
            // 查本地会话的最后一条消息
            WKConversationMsg conv = WKIM.getInstance().getConversationManager()
                    .getWithChannel(threadChannelId, WKChannelType.COMMUNITY_TOPIC);
            if (conv == null || TextUtils.isEmpty(conv.lastClientMsgNO)) continue;
            WKMsg lastMsg = WKIM.getInstance().getMsgManager().getWithClientMsgNO(conv.lastClientMsgNO);
            if (lastMsg == null) continue;
            // 解析消息内容（DB 取出的消息可能未解析 baseContentMsgModel）
            lastMsg = MessageHandler.getInstance().parsingMsg(lastMsg);
            if (lastMsg.baseContentMsgModel != null && lastMsg.baseContentMsgModel.mentionAll == 1
                    && !TextUtils.isEmpty(lastMsg.fromUID) && !lastMsg.fromUID.equals(loginUID)) {
                WKReminder reminder = new WKReminder();
                reminder.reminderID = lastMsg.messageSeq;
                reminder.messageID = lastMsg.messageID;
                reminder.messageSeq = lastMsg.messageSeq;
                reminder.channelID = threadChannelId;
                reminder.channelType = WKChannelType.COMMUNITY_TOPIC;
                reminder.type = WKMentionType.WKReminderTypeMentionMe;
                reminder.publisher = lastMsg.fromUID;
                reminder.isLocate = 1;
                reminder.done = 0;
                reminder.version = 0;
                WKIM.getInstance().getReminderManager().saveOrUpdateReminders(
                        java.util.Collections.singletonList(reminder));
                created = true;
            }
        }
        return created;
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

    private void updateThreadPreviewDirectly(String groupNo) {
        if (getRecyclerView() == null) return;
        for (int i = 0; i < getData().size(); i++) {
            if (getData().get(i).isSectionHeader) continue;
            WKUIConversationMsg convMsg = getData().get(i).uiConversationMsg;
            if (convMsg == null) continue;
            if (convMsg.channelID.equals(groupNo)) {
                notifyItemChanged(i + getHeaderLayoutCount());
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
    private int getThreadToggleColor(WKUIConversationMsg item) {
        if (hasThreadMention(item.channelID)) {
            return 0xFFFF9500; // 橙色 — 有@mention
        }
        int unread = getThreadUnreadCount(item.channelID);
        if (unread > 0) {
            boolean muted = item.getWkChannel() != null && item.getWkChannel().mute == 1;
            return muted ? 0xFFA3D6ED : 0xFFFF0000; // 静音浅蓝 / 未静音红色
        }
        return com.chat.base.ui.Theme.colorAccount; // 默认主题色
    }

    private int getThreadUnreadCount(String groupNo) {
        List<ThreadEntity> cachedList = threadDataCache.get(groupNo);
        if (cachedList == null || cachedList.isEmpty()) return 0;
        int total = 0;
        for (ThreadEntity entity : cachedList) {
            if (entity.status != 1) continue;
            String threadChannelId = ThreadModel.getInstance().buildChannelId(groupNo, entity.short_id);
            WKUIConversationMsg threadConv = WKIM.getInstance().getConversationManager()
                    .getUIConversationMsg(threadChannelId, WKChannelType.COMMUNITY_TOPIC);
            if (threadConv != null) {
                total += threadConv.unreadCount;
            }
        }
        return total;
    }

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

    public void preloadAllThreadData() {
        if (WKConfig.getInstance().getAppConfig().thread_on != 1) return;
        for (int i = 0; i < getData().size(); i++) {
            if (getData().get(i).isSectionHeader) continue;
            WKUIConversationMsg msg = getData().get(i).uiConversationMsg;
            if (msg.channelType == WKChannelType.GROUP
                    && !threadDataCache.containsKey(msg.channelID)) {
                loadThreadPreviews(msg.channelID);
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
        TextView mentionTv = helper.getView(R.id.sectionMentionTv);
        TextView unreadBadge = helper.getView(R.id.sectionUnreadBadge);
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

        // 折叠时显示 @mention 提醒（对齐 iOS [有人@我]）
        if (collapsed && msg.sectionHasMention) {
            mentionTv.setText(getContext().getString(R.string.last_msg_remind));
            mentionTv.setVisibility(View.VISIBLE);
        } else {
            mentionTv.setVisibility(View.GONE);
        }

        // 对齐 iOS WKCategorySectionCell：折叠时显示未读总数气泡
        if (collapsed && msg.sectionUnreadCount > 0) {
            unreadBadge.setText(msg.sectionUnreadCount > 99 ? "99+" : String.valueOf(msg.sectionUnreadCount));
            unreadBadge.setVisibility(View.VISIBLE);
        } else {
            unreadBadge.setVisibility(View.GONE);
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
        default void onThreadLongPress(String threadChannelId, String threadName, View anchor) {}
    }

    public enum ItemMenu {
        delete, top, mute, moveToCategory
    }
}
