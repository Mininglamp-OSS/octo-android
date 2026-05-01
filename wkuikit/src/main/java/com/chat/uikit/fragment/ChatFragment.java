package com.chat.uikit.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.base.WKBaseFragment;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.net.OkHttpUtils;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.BottomSheet;
import com.chat.base.ui.components.SegmentTabView;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKTimeUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.TabActivity;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.chat.adapter.ChatConversationAdapter;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.chat.uikit.contacts.ChooseContactsActivity;
import com.chat.uikit.contacts.service.FriendModel;
import com.chat.uikit.category.CategoryEntity;
import com.chat.uikit.category.CategoryModel;
import com.chat.uikit.databinding.FragChatConversationLayoutBinding;
import com.chat.uikit.enity.ChatConversationMsg;
import com.chat.uikit.group.service.GroupModel;
import com.chat.uikit.message.MsgModel;
import com.chat.uikit.chat.ChatActivity;
import com.chat.uikit.search.remote.GlobalActivity;
import com.chat.uikit.space.SpaceEntity;
import com.chat.uikit.thread.service.ThreadModel;
import com.chat.uikit.space.SpaceModel;
import com.chat.uikit.space.SpacePopupWindow;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.db.ReminderDBManager;
import com.xinbida.wukongim.entity.WKCMDKeys;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelState;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKConversationMsg;
import com.xinbida.wukongim.entity.WKConversationMsgExtra;
import com.xinbida.wukongim.entity.WKMentionType;
import com.xinbida.wukongim.entity.WKReminder;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKUIConversationMsg;
import com.xinbida.wukongim.message.type.WKConnectReason;
import com.xinbida.wukongim.message.type.WKConnectStatus;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import com.chat.uikit.view.PixelParticleHintView;

/**
 * 2019-11-12 14:55
 * 会话
 */
public class ChatFragment extends WKBaseFragment<FragChatConversationLayoutBinding> {

    private ChatConversationAdapter chatConversationAdapter;
    private Disposable disposable;
    private final List<Integer> refreshIds = new ArrayList<>();
    private Timer connectTimer;
    private TabActivity tabActivity;
    private String currentSpaceName;

    // 分段 Tab 切换：0=群聊, 1=私聊
    private int currentTab = 0;
    private SegmentTabView segmentTabView;
    private final List<ChatConversationMsg> allConversations = new ArrayList<>();
    private List<CategoryEntity> categoryList = new ArrayList<>();

    // Space 会话过滤：记录当前 Space 下已确认的会话 channel key，
    // 用于过滤实时消息推送中不属于当前 Space 的会话
    private final Set<String> spaceConversationKeys = new HashSet<>();
    private boolean pendingSpaceResync = false;
    // 对齐 iOS：仅在首次会话同步完成后调用一次 syncReminder，避免重复网络请求
    private boolean hasInitialReminderSynced = false;
    // DB 异步查询是否已完成，防止连接成功回调误判 allConversations 为空
    private boolean dbQueryCompleted = false;

    private String channelKey(String channelID, byte channelType) {
        return channelID + "_" + channelType;
    }

    /**
     * 将本地 spaceConversationKeys 同步到 WKUIKitApplication，
     * 供 WKIMUtils 在消息通知过滤时使用。
     */
    private void syncSpaceKeysToGlobal() {
        WKUIKitApplication.getInstance().setSpaceConversationKeys(spaceConversationKeys);
    }

    // 网络状态指示器
    private long connectedAtMs = 0;
    private long currentLatencyMs = -1;
    private final Handler pingHandler = new Handler(Looper.getMainLooper());
    private PopupWindow networkTooltip;
    private static final int PING_INTERVAL_MS = 30_000;

    @Override
    protected boolean isShowBackLayout() {
        return false;
    }

    @Override
    protected FragChatConversationLayoutBinding getViewBinding() {
        return FragChatConversationLayoutBinding.inflate(getLayoutInflater());
    }

    private long fragCreateTime;

    @Override
    protected void initView() {
        MsgModel.getInstance().loadCurrentSpaceId();
        fragCreateTime = android.os.SystemClock.elapsedRealtime();
        wkVBinding.textSwitcher.setTag(-1);
        wkVBinding.textSwitcher.setFactory(() -> {
            TextView textView = new TextView(getActivity());
            textView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            textView.setTextSize(15);
            Typeface face = Typeface.createFromAsset(getResources().getAssets(),
                    "fonts/mw_bold.ttf");
            textView.setTypeface(face);
            textView.setTextColor(ContextCompat.getColor(requireActivity(), R.color.colorDark));
            return textView;
        });
        loadCurrentSpaceName();
        //去除刷新条目闪动动画
        ((DefaultItemAnimator) Objects.requireNonNull(wkVBinding.recyclerView.getItemAnimator())).setSupportsChangeAnimations(false);
        chatConversationAdapter = new ChatConversationAdapter(new ArrayList<>());
        initAdapter(wkVBinding.recyclerView, chatConversationAdapter);
        chatConversationAdapter.restoreExpandedState();
        chatConversationAdapter.setAnimationEnable(false);
        wkVBinding.refreshLayout.setEnableOverScrollDrag(true);
        wkVBinding.refreshLayout.setEnableLoadMore(false);
        wkVBinding.refreshLayout.setEnableRefresh(false);

        Theme.setPressedBackground(wkVBinding.deviceLayout);
        Theme.setPressedBackground(wkVBinding.rightIv);

        // 分段 Tab 切换控件
        segmentTabView = new SegmentTabView(requireContext(),
                new String[]{getString(R.string.str_group_chat), getString(R.string.str_private_chat)});
        wkVBinding.segmentTabContainer.addView(segmentTabView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT));
        segmentTabView.setOnTabSelectedListener(index -> {
            currentTab = index;
            filterAndDisplay();
        });

        // 左右滑动切换群聊/私聊
        GestureDetector tabSwipeDetector = new GestureDetector(requireContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    private static final int SWIPE_THRESHOLD = 80;
                    private static final int SWIPE_VELOCITY_THRESHOLD = 200;

                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2,
                                           float velocityX, float velocityY) {
                        if (e1 == null || e2 == null) return false;
                        float diffX = e2.getX() - e1.getX();
                        float diffY = e2.getY() - e1.getY();
                        if (Math.abs(diffX) > Math.abs(diffY)
                                && Math.abs(diffX) > SWIPE_THRESHOLD
                                && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                            if (diffX < 0 && currentTab == 0) {
                                segmentTabView.selectTab(1);
                            } else if (diffX > 0 && currentTab == 1) {
                                segmentTabView.selectTab(0);
                            }
                            return true;
                        }
                        return false;
                    }
                });
        wkVBinding.recyclerView.addOnItemTouchListener(
                new RecyclerView.SimpleOnItemTouchListener() {
                    @Override
                    public boolean onInterceptTouchEvent(@NonNull RecyclerView rv,
                                                        @NonNull MotionEvent e) {
                        tabSwipeDetector.onTouchEvent(e);
                        return false;
                    }
                });
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void initListener() {
        wkVBinding.rightIv.setOnClickListener(view -> {
            List<PopupMenuItem> list = EndpointManager.getInstance().invokes(EndpointCategory.tabMenus, null);
            WKDialogUtils.getInstance().showScreenPopup(view, list);
        });

        wkVBinding.deviceLayout.setOnClickListener(v -> EndpointManager.getInstance().invoke("show_pc_login_view", getActivity()));
        wkVBinding.chatSearchBarLayout.setOnClickListener(view1 -> {
            startActivity(new Intent(getActivity(), GlobalActivity.class));
        });
        wkVBinding.signalLayout.setOnClickListener(v -> showNetworkTooltip(v));

        wkVBinding.spaceHeaderLayout.setOnClickListener(v -> {
            SpacePopupWindow popup = new SpacePopupWindow(requireContext());
            popup.setOnSpaceSelectedListener(this::performSpaceSwitch);
            popup.show(wkVBinding.spaceHeaderLayout);
        });
        chatConversationAdapter.addChildClickViewIds(R.id.contentLayout);
        chatConversationAdapter.setOnItemChildClickListener((adapter, view, position) -> SingleClickUtil.determineTriggerSingleClick(view, v -> {
            ChatConversationMsg uiConversationMsg = (ChatConversationMsg) adapter.getItem(position);
            if (uiConversationMsg != null && uiConversationMsg.uiConversationMsg != null) {
                if (view.getId() == R.id.contentLayout) {
                    if (uiConversationMsg.uiConversationMsg.channelType == WKChannelType.COMMUNITY) {
                        EndpointManager.getInstance().invoke("show_community", uiConversationMsg.uiConversationMsg.channelID);
                    } else
                        WKIMUtils.getInstance().startChatActivity(new ChatViewMenu(getActivity(), uiConversationMsg.uiConversationMsg.channelID, uiConversationMsg.uiConversationMsg.channelType, 0, false));
                }
            }
        }));
        chatConversationAdapter.addListener((menu, item) -> {
            if (menu == ChatConversationAdapter.ItemMenu.delete) {
                WKDialogUtils.getInstance().showDialog(getActivity(), getString(R.string.delete_chat), getString(R.string.delete_conver_msg_tips), true, "", getString(R.string.base_delete), 0, ContextCompat.getColor(requireActivity(), R.color.red), index -> {
                    if (index == 1) {
                        List<WKReminder> list = WKIM.getInstance().getReminderManager().getReminders(item.channelID, item.channelType);
                        if (WKReader.isNotEmpty(list)) {
                            List<Long> reminderIds = new ArrayList<>();
                            for (WKReminder reminder : list) {
                                if (reminder.done == 0) {
                                    reminder.done = 1;
                                    reminderIds.add(reminder.reminderID);
                                }
                            }
                            if (WKReader.isNotEmpty(reminderIds))
                                MsgModel.getInstance().doneReminder(reminderIds);
                        }
                        MsgModel.getInstance().offsetMsg(item.channelID, item.channelType, null);
                        WKIM.getInstance().getReminderManager().saveOrUpdateReminders(list);
                        // 先删除会话和消息，再清未读
                        // clearUnread 中的 updateRedDot 是异步的，如果在 delete 之前执行，
                        // 异步回调可能触发 SDK 刷新事件导致已删除的会话重新出现
                        boolean result = WKIM.getInstance().getConversationManager().deleteWitchChannel(item.channelID, item.channelType);
                        if (result) {
                            if (item.getWkChannel() != null && item.getWkChannel().top == 1) {
                                updateTop(item.channelID, item.channelType, 0);
                            }
                            WKIM.getInstance().getMsgManager().clearWithChannel(item.channelID, item.channelType);
                        }
                        MsgModel.getInstance().clearUnread(item.channelID, item.channelType, 0, null);
                    }
                });
            } else if (menu == ChatConversationAdapter.ItemMenu.top) {
                boolean top = false;
                if (item.getWkChannel() != null) {
                    top = item.getWkChannel().top == 1;
                }
                updateTop(item.channelID, item.channelType, top ? 0 : 1);
            } else if (menu == ChatConversationAdapter.ItemMenu.mute) {
                boolean mute = false;
                if (item.getWkChannel() != null) {
                    mute = item.getWkChannel().mute == 1;
                }
                //免打扰
                if (item.channelType == WKChannelType.GROUP) {
                    GroupModel.getInstance().updateGroupSetting(item.channelID, "mute", mute ? 0 : 1, (code, msg) -> {
                        if (code != HttpResponseCode.success) {
                            WKToastUtils.getInstance().showToastNormal(msg);
                        }
                    });
                } else {
                    FriendModel.getInstance().updateUserSetting(item.channelID, "mute", mute ? 0 : 1, (code, msg) -> {
                        if (code != HttpResponseCode.success) {
                            WKToastUtils.getInstance().showToastNormal(msg);
                        }
                    });
                }
            } else if (menu == ChatConversationAdapter.ItemMenu.moveToCategory) {
                showMoveToCategoryDialog(item.channelID);
            }
        });
        // 子区预览点击监听
        chatConversationAdapter.setThreadPreviewClickListener(new ChatConversationAdapter.IThreadPreviewClickListener() {
            @Override
            public void onThreadClick(String channelId, String groupNo, String shortId, int isJoined) {
                if (isJoined == 0) {
                    ThreadModel.getInstance().joinThread(groupNo, shortId, (code, msg) -> {
                        if (code == HttpResponseCode.success) {
                            navigateToThreadChat(channelId);
                        } else {
                            WKToastUtils.getInstance().showToast(msg);
                        }
                    });
                } else {
                    navigateToThreadChat(channelId);
                }
            }

            @Override
            public void onMoreThreadsClick(String groupNo) {
                Intent intent = new Intent(getActivity(), com.chat.uikit.thread.ThreadListActivity.class);
                intent.putExtra("groupNo", groupNo);
                startActivity(intent);
            }

            @Override
            public void onThreadLongPress(String threadChannelId, String threadName, View anchor) {
                showThreadMuteMenu(threadChannelId, threadName, anchor);
            }
        });
        // 恢复用户折叠状态
        restoreCollapsedSections();
        // Section header 折叠/展开回调 → 重建列表 + 持久化
        chatConversationAdapter.setSectionToggleListener((sectionId, collapsed) -> {
            filterAndDisplay();
            saveCollapsedSections();
        });
        // Section header 长按 → 分组管理菜单（移到最前 / 删除分组）
        chatConversationAdapter.setSectionLongClickListener((sectionId, sectionTitle, anchor) ->
                showSectionManagePopup(sectionId, sectionTitle, anchor));

        // "创建分组" 弹窗入口
        EndpointManager.getInstance().setMethod("show_create_category_dialog", object -> {
            showCreateCategoryDialog();
            return null;
        });

        //频道刷新监听
        WKIM.getInstance().getChannelManager().addOnRefreshChannelInfo("chat_fragment_refresh_channel", (channel, isEnd) -> {
            if (channel != null) {
                // 先在共享对象更新前比较 isTop 是否变化
                boolean topChanged = false;
                for (ChatConversationMsg allMsg : allConversations) {
                    if (!TextUtils.isEmpty(allMsg.uiConversationMsg.channelID)
                            && allMsg.uiConversationMsg.channelID.equals(channel.channelID)
                            && allMsg.uiConversationMsg.channelType == channel.channelType) {
                        topChanged = allMsg.isTop != channel.top;
                        allMsg.uiConversationMsg.setWkChannel(channel);
                        allMsg.isTop = channel.top;
                        break;
                    }
                }
                if (topChanged) {
                    // 置顶状态变化：重建列表
                    if (currentTab == 0) {
                        filterAndDisplay();
                    } else {
                        sortMsg(allConversations);
                    }
                    setAllCount();
                } else {
                    // 非置顶变化：局部刷新
                    for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                        if (chatConversationAdapter.getData().get(i).isSectionHeader) continue;
                        if (!TextUtils.isEmpty(chatConversationAdapter.getData().get(i).uiConversationMsg.channelID) && !TextUtils.isEmpty(channel.channelID) && chatConversationAdapter.getData().get(i).uiConversationMsg.channelID.equals(channel.channelID) && chatConversationAdapter.getData().get(i).uiConversationMsg.channelType == channel.channelType) {
                            chatConversationAdapter.getData().get(i).isRefreshChannelInfo = true;
                            chatConversationAdapter.getData().get(i).isResetCounter = true;
                            notifyRecycler(i, chatConversationAdapter.getData().get(i));
                            setAllCount();
                            break;
                        }
                    }
                }
            }
        });
        //监听移除最近会话
        WKIM.getInstance().getConversationManager().addOnDeleteMsgListener("chat_fragment", (s, b) -> {
            if (!TextUtils.isEmpty(s)) {
                // 从 allConversations 移除
                allConversations.removeIf(msg ->
                        msg.uiConversationMsg != null && msg.uiConversationMsg.channelID.equals(s) && msg.uiConversationMsg.channelType == b);
                for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                    if (chatConversationAdapter.getData().get(i).isSectionHeader) continue;
                    if (chatConversationAdapter.getData().get(i).uiConversationMsg.channelID.equals(s) && chatConversationAdapter.getData().get(i).uiConversationMsg.channelType == b) {
                        boolean isResetCount = chatConversationAdapter.getData().get(i).uiConversationMsg.unreadCount > 0;
                        chatConversationAdapter.removeAt(i);
                        if (isResetCount) setAllCount();
                        break;
                    }
                }
            }
        });

        WKIM.getInstance().getCMDManager().addCmdListener("chat_fragment_cmd", wkCmd -> {
            if (wkCmd == null || TextUtils.isEmpty(wkCmd.cmdKey)) return;
            //监听正在输入
            switch (wkCmd.cmdKey) {
                case WKCMDKeys.wk_typing -> {
                    String channelID = wkCmd.paramJsonObject.optString("channel_id");
                    byte channelType = (byte) wkCmd.paramJsonObject.optInt("channel_type");
                    String from_uid = wkCmd.paramJsonObject.optString("from_uid");
                    String from_name = wkCmd.paramJsonObject.optString("from_name");
                    WKChannel channel = new WKChannel(from_uid, WKChannelType.PERSONAL);
                    channel.channelName = from_name;
                    if (TextUtils.isEmpty(from_name)) {
                        WKChannel tempChannel = WKIM.getInstance().getChannelManager().getChannel(from_uid, WKChannelType.PERSONAL);
                        if (tempChannel != null) {
                            channel.channelName = tempChannel.channelName;
                            channel.channelRemark = tempChannel.channelRemark;
                        }
                    }
                    if (from_uid.equals(WKConfig.getInstance().getUid())) return;
                    for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                        if (chatConversationAdapter.getData().get(i).isSectionHeader) continue;
                        if (chatConversationAdapter.getData().get(i).uiConversationMsg.channelID.equals(channelID) && chatConversationAdapter.getData().get(i).uiConversationMsg.channelType == channelType) {
                            chatConversationAdapter.getData().get(i).isResetTyping = true;
                            chatConversationAdapter.getData().get(i).typingUserName = TextUtils.isEmpty(channel.channelRemark) ? channel.channelName : channel.channelRemark;
                            chatConversationAdapter.getData().get(i).typingStartTime = WKTimeUtils.getInstance().getCurrentSeconds();
                            notifyRecycler(i, chatConversationAdapter.getData().get(i));
                            if (disposable == null) {
                                startTimer();
                            }
                        }
                    }
                }
                case WKCMDKeys.wk_onlineStatus -> {
                    if (wkCmd.paramJsonObject != null) {
                        int device_flag = wkCmd.paramJsonObject.optInt("device_flag");
                        int online = wkCmd.paramJsonObject.optInt("online");
                        String uid = wkCmd.paramJsonObject.optString("uid");
                        if (uid.equals(WKConfig.getInstance().getUid()) && device_flag == 1) {
                            wkVBinding.deviceLayout.setVisibility(online == 1 ? View.VISIBLE : View.GONE);
                            WKSharedPreferencesUtil.getInstance().putInt(WKConfig.getInstance().getUid() + "_pc_online", online);
                        }
                    }
                }
                case "sync_channel_state" -> {
                    String fromUID = wkCmd.paramJsonObject.optString("from_uid");
                    String channelId = wkCmd.paramJsonObject.optString("channel_id");
                    int channelType = wkCmd.paramJsonObject.optInt("channel_type");
                    if (channelId.equals(WKConfig.getInstance().getUid())) {
                        channelId = fromUID;
                    }
                    String finalChannelId = channelId;
                    WKCommonModel.getInstance().getChannelState(channelId, (byte) channelType, channelState -> {
                        if (channelState != null) {
                            int isCalling = 0;
                            if (WKReader.isNotEmpty(channelState.call_info.getCalling_participants())) {
                                isCalling = 1;
                            }
                            for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                                if (chatConversationAdapter.getData().get(i).uiConversationMsg != null
                                        && !TextUtils.isEmpty(chatConversationAdapter.getData().get(i).uiConversationMsg.channelID)
                                        && finalChannelId.equals(chatConversationAdapter.getData().get(i).uiConversationMsg.channelID)) {
                                    chatConversationAdapter.getData().get(i).isCalling = isCalling;
                                    chatConversationAdapter.notifyItemChanged(i);
                                    break;
                                }
                            }
                        }
                    });
                }
            }
        });
        // 监听刷新消息
        WKIM.getInstance().getMsgManager().addOnRefreshMsgListener("chat_fragment", (msg, left) -> {
            if (msg == null) return;
            // 子区消息到达时，刷新父群聊的子区预览
            if (msg.channelType == WKChannelType.COMMUNITY_TOPIC) {
                String[] parsed = ThreadModel.getInstance().parseChannelId(msg.channelID);
                if (parsed != null) {
                    chatConversationAdapter.refreshThreadPreviews(parsed[0]);
                }
                return;
            }
            for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                if (chatConversationAdapter.getData().get(i).isSectionHeader) continue;
                if (chatConversationAdapter.getData().get(i).uiConversationMsg.channelID.equals(msg.channelID) && chatConversationAdapter.getData().get(i).uiConversationMsg.channelType == msg.channelType && chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg() != null && (chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().clientSeq == msg.clientSeq || chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().clientMsgNO.equals(msg.clientMsgNO))) {
                    if (chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().status != msg.status || chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().remoteExtra.readedCount != msg.remoteExtra.readedCount) {
                        chatConversationAdapter.getData().get(i).isRefreshStatus = true;
                    }
                    if (chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().remoteExtra.revoke != msg.remoteExtra.revoke) {
                        chatConversationAdapter.getData().get(i).isResetContent = true;
                    }
                    chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().status = msg.status;
                    if (chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().remoteExtra.editedAt != msg.remoteExtra.editedAt) {
                        chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().remoteExtra.editedAt = msg.remoteExtra.editedAt;
                        chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().remoteExtra.contentEdit = msg.remoteExtra.contentEdit;
                        WKIMUtils.getInstance().resetMsgProhibitWord(chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg());
                    }
                    chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().remoteExtra.revoker = msg.remoteExtra.revoker;
                    chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().remoteExtra.revoke = msg.remoteExtra.revoke;
                    chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().remoteExtra.unreadCount = msg.remoteExtra.unreadCount;
                    chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().remoteExtra.readedCount = msg.remoteExtra.readedCount;
                    chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().messageID = msg.messageID;
                    refreshIds.add(i);
                    break;
                }
            }
            if (left && WKReader.isNotEmpty(refreshIds)) {
                for (int i = 0, size = refreshIds.size(); i < size; i++) {
                    notifyRecycler(refreshIds.get(i), chatConversationAdapter.getData().get(refreshIds.get(i)));
//                    chatConversationAdapter.notifyItemChanged(refreshIds.get(i), chatConversationAdapter.getData().get(refreshIds.get(i)));
                }
                refreshIds.clear();
            }
        });
        WKIM.getInstance().getMsgManager().addOnClearMsgListener("chat_fragment", (channelID, channelType, fromUID) -> {
            if (TextUtils.isEmpty(fromUID))
                for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                    if (chatConversationAdapter.getData().get(i).isSectionHeader) continue;
                    if (chatConversationAdapter.getData().get(i).uiConversationMsg.channelID.equals(channelID) && chatConversationAdapter.getData().get(i).uiConversationMsg.channelType == channelType) {
                        chatConversationAdapter.getData().get(i).uiConversationMsg.setWkMsg(null);
                        chatConversationAdapter.getData().get(i).isResetContent = true;
                        notifyRecycler(i, chatConversationAdapter.getData().get(i));
//                        chatConversationAdapter.notifyItemChanged(i, chatConversationAdapter.getData().get(i));
                        break;
                    }
                }
        });
        WKIM.getInstance().getReminderManager().addOnNewReminderListener("chat_fragment", list -> {
            if (WKReader.isEmpty(list)) return;
            // 收集受影响的 channelID + 子区对应的父群 channelID
            Set<String> affectedChannels = new HashSet<>();
            Set<String> affectedParentGroups = new HashSet<>();
            for (WKReminder r : list) {
                if (!TextUtils.isEmpty(r.channelID)) {
                    affectedChannels.add(r.channelID);
                    // 子区提醒：提取父群 ID，确保父群的线程预览也刷新
                    String[] parsed = ThreadModel.getInstance().parseChannelId(r.channelID);
                    if (parsed != null) {
                        affectedParentGroups.add(parsed[0]);
                    }
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                // 重置受影响会话的 reminderList 为 null，下次 getReminderList() 会从 DB 重新读取
                for (ChatConversationMsg msg : allConversations) {
                    if (msg.isSectionHeader || msg.uiConversationMsg == null) continue;
                    if (affectedChannels.contains(msg.uiConversationMsg.channelID)) {
                        msg.uiConversationMsg.setReminderList(null);
                        msg.isResetReminders = true;
                    }
                    // 子区提醒到达时，刷新父群的线程预览
                    if (affectedParentGroups.contains(msg.uiConversationMsg.channelID)) {
                        msg.isResetReminders = true;
                        chatConversationAdapter.refreshThreadPreviews(msg.uiConversationMsg.channelID);
                    }
                }
                // 刷新 UI
                updateGroupMentionBadge();
                filterAndDisplay();
            });
        });
        // 监听刷新最近列表
        WKIM.getInstance().getConversationManager().addOnRefreshMsgListListener("chat_fragment", list -> {

            if (WKReader.isEmpty(list)) {
                return;
            }
            if (list.size() == 1) {
                showPixelHintIfNeeded(list.get(0));
                resetData(list.get(0), true);
                return;
            }

            // 过滤子区会话，同时提取父群组 ID 用于刷新子区预览
            List<WKUIConversationMsg> filteredList = new ArrayList<>();
            Set<String> threadParentGroups = new HashSet<>();
            for (WKUIConversationMsg msg : list) {
                if (msg.channelType != WKChannelType.COMMUNITY_TOPIC) {
                    filteredList.add(msg);
                } else {
                    showPixelHintIfNeeded(msg);
                    String[] parsed = ThreadModel.getInstance().parseChannelId(msg.channelID);
                    if (parsed != null) {
                        threadParentGroups.add(parsed[0]);
                    }
                }
            }
            // 刷新有子区消息更新的父群聊的子区预览
            for (String parentGroupNo : threadParentGroups) {
                chatConversationAdapter.refreshThreadPreviews(parentGroupNo);
            }
            list = filteredList;
            if (WKReader.isEmpty(list)) {
                return;
            }

            if (allConversations.isEmpty()) {
                // allConversations 为空，说明是首次加载或 Space 切换后的首次同步结果
                spaceConversationKeys.clear();
                List<ChatConversationMsg> uiList = new ArrayList<>();
                for (WKUIConversationMsg uiConversationMsg : list) {
                    // 私聊 Space 未读数适配：跨 Space 消息不计入未读（参考 iOS）
                    adjustPersonalForSpace(uiConversationMsg);
                    // sync 结果不含 conversation_extra（草稿等），从本地 DB 补充
                    WKConversationMsgExtra extra = WKIM.getInstance().getConversationManager()
                            .getMsgExtraWithChannel(uiConversationMsg.channelID, uiConversationMsg.channelType);
                    if (extra != null) {
                        uiConversationMsg.setRemoteMsgExtra(extra);
                    }
                    ChatConversationMsg msg = new ChatConversationMsg(uiConversationMsg);
                    uiList.add(msg);
                    spaceConversationKeys.add(channelKey(uiConversationMsg.channelID, uiConversationMsg.channelType));
                }
                sortMsg(uiList);
                setAllCount();
                return;
            }
            List<ChatConversationMsg> uiList = new ArrayList<>();
            // 多条
            for (WKUIConversationMsg uiConversationMsg : list) {
                if (uiConversationMsg.channelType == WKChannelType.GROUP) {
                    showPixelHintIfNeeded(uiConversationMsg);
                }
                boolean isAdd = true;
                // 先检查 allConversations（含未在当前 tab 显示的会话）
                for (ChatConversationMsg allMsg : allConversations) {
                    if (!TextUtils.isEmpty(allMsg.uiConversationMsg.channelID)
                            && allMsg.uiConversationMsg.channelID.equals(uiConversationMsg.channelID)
                            && allMsg.uiConversationMsg.channelType == uiConversationMsg.channelType) {
                        // Space 过滤
                        if (isMessageFromOtherSpace(uiConversationMsg.getWkMsg())) {
                            isAdd = false;
                            break;
                        }
                        isAdd = false;
                        // 先设置刷新标志（对象与 adapter 共享，必须在更新数据之前比较）
                        if (allMsg.uiConversationMsg.unreadCount != uiConversationMsg.unreadCount) {
                            allMsg.isResetCounter = true;
                        }
                        if (allMsg.uiConversationMsg.lastMsgTimestamp != uiConversationMsg.lastMsgTimestamp) {
                            allMsg.isResetTime = true;
                        }
                        if (!allMsg.uiConversationMsg.clientMsgNo.equals(uiConversationMsg.clientMsgNo)) {
                            allMsg.isResetContent = true;
                        }
                        if (allMsg.uiConversationMsg.lastMsgSeq != uiConversationMsg.lastMsgSeq
                                || allMsg.uiConversationMsg.lastMsgTimestamp != uiConversationMsg.lastMsgTimestamp
                                || (allMsg.uiConversationMsg.getWkMsg() != null && uiConversationMsg.getWkMsg() != null
                                    && !allMsg.uiConversationMsg.getWkMsg().clientMsgNO.equals(uiConversationMsg.getWkMsg().clientMsgNO))) {
                            allMsg.isResetTyping = true;
                            allMsg.typingUserName = "";
                            allMsg.typingStartTime = 0;
                            allMsg.isRefreshStatus = true;
                        }
                        allMsg.isResetReminders = true;
                        // 更新数据
                        allMsg.uiConversationMsg.setWkMsg(uiConversationMsg.getWkMsg());
                        allMsg.uiConversationMsg.lastMsgSeq = uiConversationMsg.lastMsgSeq;
                        allMsg.uiConversationMsg.clientMsgNo = uiConversationMsg.clientMsgNo;
                        allMsg.uiConversationMsg.unreadCount = uiConversationMsg.unreadCount;
                        allMsg.uiConversationMsg.lastMsgTimestamp = uiConversationMsg.lastMsgTimestamp;
                        if (uiConversationMsg.getRemoteMsgExtra() != null) {
                            allMsg.uiConversationMsg.setRemoteMsgExtra(uiConversationMsg.getRemoteMsgExtra());
                        }
                        allMsg.uiConversationMsg.setReminderList(uiConversationMsg.getReminderList());
                        allMsg.uiConversationMsg.localExtraMap = null;
                        WKIMUtils.getInstance().resetMsgProhibitWord(allMsg.uiConversationMsg.getWkMsg());
                        break;
                    }
                }
                // 在当前 tab 的 adapter 中查找并更新 UI 标志
                if (!isAdd) {
                    for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                        if (chatConversationAdapter.getData().get(i).isSectionHeader) continue;
                        if (!TextUtils.isEmpty(chatConversationAdapter.getData().get(i).uiConversationMsg.channelID) && !TextUtils.isEmpty(uiConversationMsg.channelID) && chatConversationAdapter.getData().get(i).uiConversationMsg.channelID.equals(uiConversationMsg.channelID) && chatConversationAdapter.getData().get(i).uiConversationMsg.channelType == uiConversationMsg.channelType) {
                            if (chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgSeq != uiConversationMsg.lastMsgSeq || chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgTimestamp != uiConversationMsg.lastMsgTimestamp || (chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg() != null && uiConversationMsg.getWkMsg() != null && !chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().clientMsgNO.equals(uiConversationMsg.getWkMsg().clientMsgNO))) {
                                chatConversationAdapter.getData().get(i).isResetTyping = true;
                                chatConversationAdapter.getData().get(i).typingUserName = "";
                                chatConversationAdapter.getData().get(i).typingStartTime = 0;
                                chatConversationAdapter.getData().get(i).isRefreshStatus = true;
                            }
                            if (chatConversationAdapter.getData().get(i).uiConversationMsg.unreadCount != uiConversationMsg.unreadCount) {
                                chatConversationAdapter.getData().get(i).isResetCounter = true;
                            }
                            if (chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgTimestamp != uiConversationMsg.lastMsgTimestamp) {
                                chatConversationAdapter.getData().get(i).isResetTime = true;
                            }
                            if (!chatConversationAdapter.getData().get(i).uiConversationMsg.clientMsgNo.equals(uiConversationMsg.clientMsgNo)) {
                                chatConversationAdapter.getData().get(i).isResetContent = true;
                            }
                            chatConversationAdapter.getData().get(i).isResetReminders = true;
                            notifyRecycler(i, chatConversationAdapter.getData().get(i));
                            break;
                        }
                    }
                    setAllCount();
                }
                if (isAdd) {
                    // 私聊 Space 未读数适配（参考 iOS WKConversationWrapModel.unreadCount）
                    adjustPersonalForSpace(uiConversationMsg);
                    // Space 过滤：只添加属于当前 Space 的会话
                    String key = channelKey(uiConversationMsg.channelID, uiConversationMsg.channelType);
                    if (spaceConversationKeys.isEmpty() || spaceConversationKeys.contains(key)) {
                        uiList.add(new ChatConversationMsg(uiConversationMsg));
                        spaceConversationKeys.add(key);
                    } else {
                        // 不在白名单中：精确判断是否属于当前 Space
                        boolean reject;
                        if (uiConversationMsg.channelType == WKChannelType.GROUP) {
                            // 群聊：用 channel 级别的 space_id 判断
                            reject = !isChannelInCurrentSpace(uiConversationMsg.channelID, uiConversationMsg.channelType);
                        } else {
                            // 私聊：用消息 payload 中的 space_id 判断
                            reject = isMessageFromOtherSpace(uiConversationMsg.getWkMsg());
                        }
                        if (reject) {
                            continue;
                        }
                        // 属于当前 Space，放行并加入白名单
                        uiList.add(new ChatConversationMsg(uiConversationMsg));
                        spaceConversationKeys.add(key);
                    }
                }
            }
            if (WKReader.isNotEmpty(uiList)) {
                // 有新会话加入，合并到 allConversations 后重新排序
                uiList.addAll(allConversations);
                sortMsg(uiList);
            }
            // 仅已有会话更新时，notifyRecycler 已处理局部刷新，不需要 sortMsg 全量重绘
            setAllCount();
        });
//        WKIM.getInstance().getConversationManager().addOnRefreshMsgListener("chat_fragment", this::resetData);
        // 监听连接状态
        WKIM.getInstance().getConnectionManager().addOnConnectionStatusListener("chat_fragment", (i, reason) -> {
            if (wkVBinding.textSwitcher.getTag() != null) {
                Object tag = wkVBinding.textSwitcher.getTag();
                if (tag instanceof Integer) {
                    int tag1 = (int) tag;
                    if (tag1 == i) {
                        return;
                    }
                }
            }
            if (i == WKConnectStatus.syncMsg) {
                wkVBinding.textSwitcher.setText(getString(R.string.sync_msg));
                wkVBinding.spaceChevronIv.setVisibility(View.GONE);
            } else if (i == WKConnectStatus.success) {
                setSpaceSwitcherText(getDisplayTitle());
                wkVBinding.spaceChevronIv.setVisibility(View.VISIBLE);
                connectedAtMs = System.currentTimeMillis();
                // 立即触发第一次 ping，有真实数据后才显示信号栏
                startPingTimer();
                // 注册流程补偿：SDK 连接成功时如果列表仍为空（getChatMsg 的 sync 因连接未就绪而未触发），补一次 sync
                // 必须等 DB 查询完成后再判断，否则查询还在飞行中会误判为空并 clearAll
                String spaceId = MsgModel.getInstance().getCurrentSpaceId();
                if (!TextUtils.isEmpty(spaceId) && allConversations.isEmpty() && dbQueryCompleted) {
                    spaceConversationKeys.clear();
                    Schedulers.io().scheduleDirect(() -> {
                        WKIM.getInstance().getConversationManager().clearAll();
                        // setSyncConversationListener 内部有 DB 查询，必须在 IO 线程执行，
                        // 放主线程会和 sync 写入争抢数据库锁导致 ANR
                        WKIM.getInstance().getConversationManager().setSyncConversationListener(result -> {
                        });
                    });
                }
            } else if (i == WKConnectStatus.connecting) {
                wkVBinding.textSwitcher.setText(getString(R.string.connecting));
                wkVBinding.spaceChevronIv.setVisibility(View.GONE);
                stopPingTimer();
                wkVBinding.signalLayout.setVisibility(View.GONE);
            } else if (i == WKConnectStatus.noNetwork) {
                wkVBinding.textSwitcher.setText(getString(R.string.network_error_tips));
                wkVBinding.spaceChevronIv.setVisibility(View.GONE);
                stopPingTimer();
                wkVBinding.signalLayout.setVisibility(View.GONE);
            } else if (i == WKConnectStatus.kicked) {
                int from = 0;
                if (reason.equals(WKConnectReason.ReasonConnectKick)) {
                    from = 1;
                }
                WKUIKitApplication.getInstance().exitLogin(from);
            }
            wkVBinding.textSwitcher.setTag(i);
            if (i != WKConnectStatus.success && i != WKConnectStatus.syncMsg) {
                startConnectTimer();
            } else {
                EndpointManager.getInstance().invoke("wk_close_disconnect_screen", null);
                stopConnectTimer();
            }
        });
        // 如果 IM 在 Fragment 创建前已连接成功，listener 会错过回调，主动补齐状态
        if (connectedAtMs == 0) {
            connectedAtMs = System.currentTimeMillis();
        }
        setSpaceSwitcherText(getDisplayTitle());
        wkVBinding.spaceChevronIv.setVisibility(View.VISIBLE);
        startPingTimer();
        EndpointManager.getInstance().setMethod("", EndpointCategory.wkExitChat, object -> {
            if (object != null) {
                WKChannel channel = (WKChannel) object;
                // 从 allConversations 移除
                allConversations.removeIf(msg ->
                        msg.uiConversationMsg != null
                                && msg.uiConversationMsg.channelID.equals(channel.channelID)
                                && msg.uiConversationMsg.channelType == channel.channelType);
                for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                    if (chatConversationAdapter.getData().get(i).isSectionHeader) continue;
                    if (chatConversationAdapter.getData().get(i).uiConversationMsg.channelID.equals(channel.channelID) && chatConversationAdapter.getData().get(i).uiConversationMsg.channelType == channel.channelType) {
                        boolean isResetCount = chatConversationAdapter.getData().get(i).uiConversationMsg.unreadCount > 0;
                        chatConversationAdapter.removeAt(i);
                        if (isResetCount) setAllCount();
                        break;
                    }
                }

            }
            return null;
        });

        EndpointManager.getInstance().setMethod("chat_cover", EndpointCategory.refreshProhibitWord, object -> {
            if (WKReader.isEmpty(chatConversationAdapter.getData())) {
                return 1;
            }
            for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                if (chatConversationAdapter.getData().get(i).uiConversationMsg != null && chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg() != null && chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().type == WKContentType.WK_TEXT) {
                    WKIMUtils.getInstance().resetMsgProhibitWord(chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg());
                    chatConversationAdapter.notifyItemChanged(i);
                }
            }
            return 1;
        });

        // syncCoverExtra 完成后刷新草稿等 extra 信息
        EndpointManager.getInstance().setMethod("refresh_conversation_extras", object -> {
            for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                if (chatConversationAdapter.getData().get(i).isSectionHeader) continue;
                WKUIConversationMsg convMsg = chatConversationAdapter.getData().get(i).uiConversationMsg;
                WKConversationMsgExtra extra = WKIM.getInstance().getConversationManager()
                        .getMsgExtraWithChannel(convMsg.channelID, convMsg.channelType);
                if (extra != null) {
                    convMsg.setRemoteMsgExtra(extra);
                    chatConversationAdapter.getData().get(i).isResetContent = true;
                    notifyRecycler(i, chatConversationAdapter.getData().get(i));
                }
            }
            return null;
        });

        EndpointManager.getInstance().setMethod("refresh_conversation_calling", object -> {
            if (WKReader.isNotEmpty(MsgModel.getInstance().channelStatus)) {
                for (WKChannelState state : MsgModel.getInstance().channelStatus) {
                    for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                        if (chatConversationAdapter.getData().get(i).uiConversationMsg != null
                                && !TextUtils.isEmpty(chatConversationAdapter.getData().get(i).uiConversationMsg.channelID)
                                && state.channel_id.equals(chatConversationAdapter.getData().get(i).uiConversationMsg.channelID)) {
                            chatConversationAdapter.getData().get(i).isCalling = state.calling;
                            chatConversationAdapter.notifyItemChanged(i);
                        }
                    }
                }
                return null;
            }
            for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                if (chatConversationAdapter.getData().get(i).isCalling == 1) {
                    chatConversationAdapter.getData().get(i).isCalling = 0;
                    chatConversationAdapter.notifyItemChanged(i);
                }
            }
            return null;
        });
    }


    @Override
    protected void initData() {
        getData();
        loadCategories();
    }

    private void getData() {
        getChatMsg();
    }


    private void getChatMsg() {
        String currentSpaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (!TextUtils.isEmpty(currentSpaceId)) {
            String lastLoaded = WKSharedPreferencesUtil.getInstance()
                    .getSPWithUID("last_loaded_space_id");
            boolean spaceChanged = !currentSpaceId.equals(lastLoaded);

            if (!spaceChanged) {
                // 同一 Space：从本地 DB 加载缓存，立即显示
                WKIM.getInstance().getConversationManager().getAll(list -> {
                    List<ChatConversationMsg> tempList = new ArrayList<>();
                    spaceConversationKeys.clear();
                    if (WKReader.isNotEmpty(list)) {
                        for (WKUIConversationMsg conv : list) {
                            if (conv.channelType == WKChannelType.COMMUNITY_TOPIC) continue;
                            tempList.add(new ChatConversationMsg(conv));
                            spaceConversationKeys.add(channelKey(conv.channelID, conv.channelType));
                        }
                    }
                    dbQueryCompleted = true;
                    AndroidUtilities.runOnUIThread(() -> {
                        syncSpaceKeysToGlobal();
                        sortMsg(tempList);
                    });
                });
                return;
            }

            // Space 变化或首次加载：清空后让 sync 返回新数据
            dbQueryCompleted = true;
            WKSharedPreferencesUtil.getInstance()
                    .putSPWithUID("last_loaded_space_id", currentSpaceId);
            spaceConversationKeys.clear();
            Schedulers.io().scheduleDirect(() -> {
                WKIM.getInstance().getConversationManager().clearAll();
                WKIM.getInstance().getConversationManager().setSyncConversationListener(result -> {
                });
            });
            return;
        }
        // 无 Space 模式：直接加载本地所有会话
        WKIM.getInstance().getConversationManager().getAll(list -> {
            List<ChatConversationMsg> tempList = new ArrayList<>();
            if (WKReader.isNotEmpty(list)) {
                for (int i = 0, size = list.size(); i < size; i++) {
                    // 子区会话不在主聊天列表显示
                    if (list.get(i).channelType == WKChannelType.COMMUNITY_TOPIC) {
                        continue;
                    }
                    tempList.add(new ChatConversationMsg(list.get(i)));
                }
            }
            AndroidUtilities.runOnUIThread(() -> sortMsg(tempList));
        });

//        List<ChatConversationMsg> list = new ArrayList<>();
//        List<WKUIConversationMsg> tempList = WKIM.getInstance().getConversationManager().getAll();
//        if (WKReader.isNotEmpty(tempList)) {
//            for (int i = 0, size = tempList.size(); i < size; i++) {
//                list.add(new ChatConversationMsg(tempList.get(i)));
//            }
//        }
//        return list;
    }

    private void setAllCount() {
        int groupCount = 0;
        int personalCount = 0;
        // 使用 allConversations（全量数据）计算未读数，不受 tab 过滤影响
        List<ChatConversationMsg> source = allConversations.isEmpty()
                ? chatConversationAdapter.getData() : allConversations;
        for (int i = 0, size = source.size(); i < size; i++) {
            ChatConversationMsg item = source.get(i);
            if (item.isSectionHeader) continue;
            if (item.uiConversationMsg.getWkChannel() != null && item.uiConversationMsg.getWkChannel().mute == 0) {
                if (item.uiConversationMsg.channelType == WKChannelType.PERSONAL) {
                    personalCount += chatConversationAdapter.getEffectiveUnreadCount(item);
                } else {
                    groupCount += item.uiConversationMsg.unreadCount;
                }
            }
        }
        if (segmentTabView != null) {
            segmentTabView.setBadge(1, personalCount);
        }
        if (tabActivity != null) {
            tabActivity.setMsgCount(0);
        }
        updateGroupMentionBadge();
    }

    /**
     * 检查群聊和子区会话中是否有未处理的 @mention 提醒，
     * 有则在群聊 Tab 上显示 @ 角标。
     */
    private void updateGroupMentionBadge() {
        if (segmentTabView == null || !isAdded()) return;
        boolean hasMention = false;
        // 1. 遍历 allConversations 中的 GROUP 会话
        List<ChatConversationMsg> source = allConversations.isEmpty()
                ? chatConversationAdapter.getData() : allConversations;
        for (ChatConversationMsg item : source) {
            if (item.isSectionHeader) continue;
            if (item.uiConversationMsg.channelType == WKChannelType.GROUP) {
                List<WKReminder> reminders = item.getReminders();
                if (WKReader.isNotEmpty(reminders)) {
                    for (WKReminder r : reminders) {
                        if (r.type == WKMentionType.WKReminderTypeMentionMe && r.done == 0) {
                            hasMention = true;
                            break;
                        }
                    }
                }
                // 2. 检查该群下子区的 reminders（直接查 DB，不依赖 threadDataCache）
                if (!hasMention) {
                    hasMention = ReminderDBManager.getInstance().hasUndoneReminderWithChannelPrefix(
                            item.uiConversationMsg.channelID, WKMentionType.WKReminderTypeMentionMe);
                }
            }
            if (hasMention) break;
        }
        segmentTabView.setMentionBadge(0, hasMention, getString(R.string.last_msg_remind));
    }

    @Override
    public void onAttach(@NotNull Context context) {
        super.onAttach(context);
        if (context instanceof TabActivity) {
            tabActivity = (TabActivity) context;
        }
    }

    private void resetChildData(WKUIConversationMsg uiConversationMsg, boolean isEnd) {
        if (WKReader.isNotEmpty(chatConversationAdapter.getData())) {
            boolean isAdd = true;
            for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                if (chatConversationAdapter.getData().get(i).isSectionHeader) continue;
                boolean isBreak = false;
                if (WKReader.isNotEmpty(chatConversationAdapter.getData().get(i).childList)) {
                    for (int j = 0, len = chatConversationAdapter.getData().get(i).childList.size(); j < len; j++) {
                        if (chatConversationAdapter.getData().get(i).childList.get(j).uiConversationMsg.channelID.equals(uiConversationMsg.channelID)) {
                            // 更新匹配的子区数据
                            chatConversationAdapter.getData().get(i).childList.get(j).uiConversationMsg.unreadCount = uiConversationMsg.unreadCount;
                            chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgTimestamp = uiConversationMsg.lastMsgTimestamp;
                            chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgSeq = uiConversationMsg.lastMsgSeq;
                            chatConversationAdapter.getData().get(i).uiConversationMsg.clientMsgNo = uiConversationMsg.clientMsgNo;
                            // 重新计算父群未读数：遍历所有子区求和（避免 += 累加导致虚高）
                            int totalChildUnread = 0;
                            for (ChatConversationMsg child : chatConversationAdapter.getData().get(i).childList) {
                                totalChildUnread += child.uiConversationMsg.unreadCount;
                            }
                            chatConversationAdapter.getData().get(i).uiConversationMsg.unreadCount = totalChildUnread;
                            notifyRecycler(i, chatConversationAdapter.getData().get(i));
                            isBreak = true;
                            isAdd = false;
                        }
                    }
                }
                if (isBreak) break;
            }
            if (isAdd) {
                WKUIConversationMsg msg = new WKUIConversationMsg();
                msg.channelID = uiConversationMsg.parentChannelID;
                msg.channelType = uiConversationMsg.parentChannelType;
                msg.clientMsgNo = uiConversationMsg.clientMsgNo;
                msg.lastMsgSeq = uiConversationMsg.lastMsgSeq;
                msg.lastMsgTimestamp = uiConversationMsg.lastMsgTimestamp;
                msg.unreadCount = uiConversationMsg.unreadCount;
                msg.setReminderList(uiConversationMsg.getReminderList());
                if (uiConversationMsg.getRemoteMsgExtra() != null) {
                    msg.setRemoteMsgExtra(uiConversationMsg.getRemoteMsgExtra());
                }

                ChatConversationMsg chatConversationMsg = new ChatConversationMsg(msg);
                ChatConversationMsg child = new ChatConversationMsg(uiConversationMsg);
                chatConversationMsg.childList = new ArrayList<>();
                chatConversationMsg.childList.add(child);
                if (!isEnd) {
                    chatConversationAdapter.addData(chatConversationMsg);
                } else {
                    int insertIndex = getInsertIndex(msg);
                    chatConversationAdapter.addData(insertIndex, chatConversationMsg);
                }
            }
        }
    }

    private int msgCount = 0;

    private void resetData(WKUIConversationMsg uiConversationMsg, boolean isEnd) {
        if (uiConversationMsg == null) {
            return;
        }
        // 子区会话不在主聊天列表显示，但需要刷新父群聊的子区预览并更新排序
        if (uiConversationMsg.channelType == WKChannelType.COMMUNITY_TOPIC) {
            String[] parsed = ThreadModel.getInstance().parseChannelId(uiConversationMsg.channelID);
            if (parsed != null) {
                chatConversationAdapter.refreshThreadPreviews(parsed[0]);
                // 更新父群的 lastMsgTimestamp，使其在分组内上浮（对齐 iOS addOrUpdateChildren）
                for (ChatConversationMsg msg : allConversations) {
                    if (msg.uiConversationMsg != null
                            && parsed[0].equals(msg.uiConversationMsg.channelID)
                            && msg.uiConversationMsg.channelType == WKChannelType.GROUP) {
                        if (uiConversationMsg.lastMsgTimestamp > msg.uiConversationMsg.lastMsgTimestamp) {
                            msg.uiConversationMsg.lastMsgTimestamp = uiConversationMsg.lastMsgTimestamp;
                        }
                        break;
                    }
                }
            }
            if (isEnd) {
                sortMsg(allConversations);
            }
            return;
        }
        // 群聊收到子区创建系统消息时，刷新子区预览
        if (uiConversationMsg.channelType == WKChannelType.GROUP
                && uiConversationMsg.getWkMsg() != null
                && uiConversationMsg.getWkMsg().type == WKContentType.threadCreated) {
            chatConversationAdapter.refreshThreadPreviews(uiConversationMsg.channelID);
        }
        // || (uiConversationMsg.getWkChannel() != null && uiConversationMsg.getWkChannel().follow == 0 && uiConversationMsg.channelType == WKChannelType.PERSONAL)
        if (uiConversationMsg.isDeleted == 1 || TextUtils.equals(uiConversationMsg.channelID, "0")) {
            if (isEnd) {
                sortMsg(allConversations);
            }
            return;
        }
        if (!TextUtils.isEmpty(uiConversationMsg.parentChannelID)) {
            resetChildData(uiConversationMsg, isEnd);
            return;
        }

        // 判断会话是否属于当前 tab 显示范围
        byte currentTabType = currentTab == 0 ? WKChannelType.GROUP : WKChannelType.PERSONAL;
        boolean matchesCurrentTab = uiConversationMsg.channelType == currentTabType;

        boolean isAdd = true;
        int index = -1;
        boolean isSort = false;

        // 先检查 allConversations 中是否已有该会话（处理不在当前 tab 的情况）
        // 注意：allConversations 与 adapter data 共享同一对象，
        // 必须在更新数据之前设置刷新标志，否则 adapter 循环中的比较永远为 false
        boolean foundInAll = false;
        for (ChatConversationMsg allMsg : allConversations) {
            if (!TextUtils.isEmpty(allMsg.uiConversationMsg.channelID)
                    && allMsg.uiConversationMsg.channelID.equals(uiConversationMsg.channelID)
                    && allMsg.uiConversationMsg.channelType == uiConversationMsg.channelType) {
                foundInAll = true;
                // 先设置刷新标志（在数据更新之前比较）
                if (allMsg.uiConversationMsg.unreadCount != uiConversationMsg.unreadCount) {
                    allMsg.isResetCounter = true;
                }
                if (allMsg.uiConversationMsg.lastMsgTimestamp != uiConversationMsg.lastMsgTimestamp) {
                    allMsg.isResetTime = true;
                }
                if (!allMsg.uiConversationMsg.clientMsgNo.equals(uiConversationMsg.clientMsgNo)) {
                    allMsg.isResetContent = true;
                }
                if (allMsg.uiConversationMsg.lastMsgSeq != uiConversationMsg.lastMsgSeq
                        || allMsg.uiConversationMsg.lastMsgTimestamp != uiConversationMsg.lastMsgTimestamp
                        || (allMsg.uiConversationMsg.getWkMsg() != null && uiConversationMsg.getWkMsg() != null
                            && !allMsg.uiConversationMsg.getWkMsg().clientMsgNO.equals(uiConversationMsg.getWkMsg().clientMsgNO))) {
                    allMsg.isResetTyping = true;
                    allMsg.typingUserName = "";
                    allMsg.typingStartTime = 0;
                    allMsg.isRefreshStatus = true;
                }
                allMsg.isResetReminders = true;
                // 排序标志也在此处提前计算（共享对象，adapter 循环中比较会失败）
                if (allMsg.uiConversationMsg.lastMsgSeq != uiConversationMsg.lastMsgSeq
                        || allMsg.uiConversationMsg.lastMsgTimestamp != uiConversationMsg.lastMsgTimestamp
                        || (allMsg.uiConversationMsg.getWkMsg() != null && uiConversationMsg.getWkMsg() != null
                            && !allMsg.uiConversationMsg.getWkMsg().clientMsgNO.equals(uiConversationMsg.getWkMsg().clientMsgNO))) {
                    isSort = true;
                }
                // 更新数据
                allMsg.uiConversationMsg.setWkMsg(uiConversationMsg.getWkMsg());
                allMsg.uiConversationMsg.lastMsgSeq = uiConversationMsg.lastMsgSeq;
                allMsg.uiConversationMsg.clientMsgNo = uiConversationMsg.clientMsgNo;
                allMsg.uiConversationMsg.unreadCount = uiConversationMsg.unreadCount;
                allMsg.uiConversationMsg.lastMsgTimestamp = uiConversationMsg.lastMsgTimestamp;
                if (uiConversationMsg.getRemoteMsgExtra() != null) {
                    allMsg.uiConversationMsg.setRemoteMsgExtra(uiConversationMsg.getRemoteMsgExtra());
                }
                allMsg.uiConversationMsg.setReminderList(uiConversationMsg.getReminderList());
                allMsg.uiConversationMsg.localExtraMap = null;
                WKIMUtils.getInstance().resetMsgProhibitWord(allMsg.uiConversationMsg.getWkMsg());
                break;
            }
        }

        if (WKReader.isNotEmpty(chatConversationAdapter.getData())) {
            for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                if (chatConversationAdapter.getData().get(i).isSectionHeader) continue;
                if (!TextUtils.isEmpty(chatConversationAdapter.getData().get(i).uiConversationMsg.channelID) && !TextUtils.isEmpty(uiConversationMsg.channelID) && chatConversationAdapter.getData().get(i).uiConversationMsg.channelID.equals(uiConversationMsg.channelID) && chatConversationAdapter.getData().get(i).uiConversationMsg.channelType == uiConversationMsg.channelType) {
                    // Space 过滤：实时消息来自其他 Space 时，跳过所有更新
                    if (isMessageFromOtherSpace(uiConversationMsg.getWkMsg())) {
                        isAdd = false;
                        break;
                    }
                    if (!isEnd) {
                        isAdd = false;
                        chatConversationAdapter.getData().get(i).uiConversationMsg = uiConversationMsg;
                        break;
                    }
                    // 记录 adapter 中的位置（排序时需要先移除旧项）
                    index = i;
                    if (chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgSeq != uiConversationMsg.lastMsgSeq || chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgTimestamp != uiConversationMsg.lastMsgTimestamp || (chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg() != null && uiConversationMsg.getWkMsg() != null && !chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().clientMsgNO.equals(uiConversationMsg.getWkMsg().clientMsgNO))) {
                        isSort = true;
                        chatConversationAdapter.getData().get(i).isResetTyping = true;
                        chatConversationAdapter.getData().get(i).typingUserName = "";
                        chatConversationAdapter.getData().get(i).typingStartTime = 0;
                        chatConversationAdapter.getData().get(i).isRefreshStatus = true;
                    }
                    if (chatConversationAdapter.getData().get(i).uiConversationMsg.unreadCount != uiConversationMsg.unreadCount) {
                        chatConversationAdapter.getData().get(i).isResetCounter = true;
                    }
                    if (chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgTimestamp != uiConversationMsg.lastMsgTimestamp) {
                        chatConversationAdapter.getData().get(i).isResetTime = true;
                    }
                    chatConversationAdapter.getData().get(i).uiConversationMsg.setWkMsg(uiConversationMsg.getWkMsg());
                    if (!chatConversationAdapter.getData().get(i).uiConversationMsg.clientMsgNo.equals(uiConversationMsg.clientMsgNo)) {
                        chatConversationAdapter.getData().get(i).isResetContent = true;
                    }
                    WKIMUtils.getInstance().resetMsgProhibitWord(chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg());
                    // todo 比较是否真的改过提醒内容
                    chatConversationAdapter.getData().get(i).isResetReminders = true;
                    chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgSeq = uiConversationMsg.lastMsgSeq;
                    chatConversationAdapter.getData().get(i).uiConversationMsg.clientMsgNo = uiConversationMsg.clientMsgNo;
                    chatConversationAdapter.getData().get(i).uiConversationMsg.unreadCount = uiConversationMsg.unreadCount;
                    chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgTimestamp = uiConversationMsg.lastMsgTimestamp;
                    if (uiConversationMsg.getRemoteMsgExtra() != null) {
                        chatConversationAdapter.getData().get(i).uiConversationMsg.setRemoteMsgExtra(uiConversationMsg.getRemoteMsgExtra());
                    }

                    chatConversationAdapter.getData().get(i).uiConversationMsg.setReminderList(uiConversationMsg.getReminderList());
                    chatConversationAdapter.getData().get(i).uiConversationMsg.localExtraMap = null;
                    isAdd = false;
                    notifyRecycler(i, chatConversationAdapter.getData().get(i));
                    setAllCount();
                    break;
                }
            }
        }

        // 会话已在 allConversations 中但不在当前 tab 的 adapter 中
        if (isAdd && foundInAll) {
            isAdd = false;
            setAllCount();
        }

        if (!isEnd) msgCount++;

        if (isAdd) {
            // 私聊 Space 未读数适配：跨 Space 消息不计入未读（参考 iOS）
            adjustPersonalForSpace(uiConversationMsg);
            // Space 过滤：只添加属于当前 Space 的会话
            String key = channelKey(uiConversationMsg.channelID, uiConversationMsg.channelType);
            if (!spaceConversationKeys.isEmpty() && !spaceConversationKeys.contains(key)) {
                // 不在白名单中：精确判断是否属于当前 Space
                if (uiConversationMsg.channelType == WKChannelType.GROUP) {
                    // 群聊：用 channel 级别的 space_id 判断（群消息 payload 不含 space_id）
                    if (!isChannelInCurrentSpace(uiConversationMsg.channelID, uiConversationMsg.channelType)) {
                        return;
                    }
                } else {
                    // 私聊：用消息 payload 中的 space_id 判断
                    if (isMessageFromOtherSpace(uiConversationMsg.getWkMsg())) {
                        return;
                    }
                }
            }
            spaceConversationKeys.add(key);
            syncSpaceKeysToGlobal();
            ChatConversationMsg newMsg = new ChatConversationMsg(uiConversationMsg);
            // 始终添加到全量列表
            allConversations.add(newMsg);
            // 仅在匹配当前 tab 时添加到 adapter 显示
            if (matchesCurrentTab) {
                if (currentTab == 0) {
                    // 群聊 tab：有 section header，用 filterAndDisplay 重建列表
                    filterAndDisplay();
                } else if (!isEnd) {
                    chatConversationAdapter.addData(newMsg);
                } else {
                    int insertIndex = getInsertIndex(uiConversationMsg);
                    chatConversationAdapter.addData(insertIndex, newMsg);
                    scrollToPositionIfNearTop(insertIndex);
                }
            }
            setAllCount();
        }
        if (isEnd) {
            if (isSort && msgCount == 0) {
                sortMsg(allConversations);
            } else {
                if (msgCount > 0) {
                    msgCount = 0;
                    sortMsg(allConversations);
                }
            }
        }
    }

    /**
     * 从消息中提取 space_id（优先从 content JSON，其次从 baseContentMsgModel）。
     * 返回 space_id 字符串，未找到时返回 null。
     */
    private String extractSpaceId(WKMsg msg) {
        if (msg == null) return null;
        String msgSpaceId = null;
        // 1. 从 msg.content 原始 JSON 解析
        if (!TextUtils.isEmpty(msg.content)) {
            try {
                org.json.JSONObject json = new org.json.JSONObject(msg.content);
                msgSpaceId = json.optString("space_id", "");
            } catch (Exception ignored) {
            }
        }
        // 2. 从 SDK 解码后的 spaceId 字段读取
        if (TextUtils.isEmpty(msgSpaceId) && msg.baseContentMsgModel != null
                && !TextUtils.isEmpty(msg.baseContentMsgModel.spaceId)) {
            msgSpaceId = msg.baseContentMsgModel.spaceId;
        }
        return TextUtils.isEmpty(msgSpaceId) ? null : msgSpaceId;
    }

    /**
     * 判断消息是否来自其他 Space（非当前 Space）。
     * 用于过滤跨 Space 的实时消息更新，避免错误的未读计数。
     *
     * <p>EP3 · YUJ-88：委托给 {@link com.chat.base.space.SpaceFilter#shouldSkipMessageForSpace(WKMsg)}
     * 以便与 web shouldSkipMessageForSpace 判定对齐。
     */
    private boolean isMessageFromOtherSpace(WKMsg msg) {
        return com.chat.base.space.SpaceFilter.shouldSkipMessageForSpace(msg);
    }

    /**
     * 判断频道是否属于当前 Space。
     * 用于群聊过滤：群消息 payload 不含 space_id，无法用 isMessageFromOtherSpace 判断。
     *
     * <p>EP3 · YUJ-88：委托给 {@link com.chat.base.space.SpaceFilter#shouldSkipChannelForSpace(String, byte)}
     * 实现 web 双路径判定（含外部群兜底：我以当前 Space 身份加入外部群时放行）。
     */
    private boolean isChannelInCurrentSpace(String channelID, byte channelType) {
        return !com.chat.base.space.SpaceFilter.shouldSkipChannelForSpace(channelID, channelType);
    }

    // 系统 Bot（如 BotFather）：跨 Space 共享，需要按消息 space_id 过滤（参考 iOS shouldShowConversation）
    private static final Set<String> SYSTEM_BOTS = new HashSet<>(Arrays.asList("botfather"));

    /**
     * 对所有 PERSONAL 类型会话（包括系统 Bot）进行 Space 适配：
     * 当最后一条消息不属于当前 Space 时，清零未读数，避免跨 Space 未读数串扰。
     * 参考 iOS WKConversationWrapModel.unreadCount 中对 Person 频道使用 space_unread 的逻辑。
     */
    private void adjustPersonalForSpace(WKUIConversationMsg uiConversationMsg) {
        if (uiConversationMsg == null) return;
        if (uiConversationMsg.channelType != WKChannelType.PERSONAL) return;
        String currentSpaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (TextUtils.isEmpty(currentSpaceId)) return;

        WKMsg msg = uiConversationMsg.getWkMsg();
        if (msg == null) return;

        String msgSpaceId = extractSpaceId(msg);
        // 最后一条消息属于其他 Space 时，清零未读数
        if (msgSpaceId != null && !msgSpaceId.equals(currentSpaceId)) {
            uiConversationMsg.unreadCount = 0;
        }
    }

    /**
     * 检查并补充会话列表中缺失的 extras（草稿等）。
     * 在 onResume 中调用，确保无论 syncCoverExtra 何时完成都能显示草稿。
     */
    private void refreshExtrasIfNeeded() {
        // 刷新 allConversations（覆盖所有 tab 的会话）
        for (ChatConversationMsg allMsg : allConversations) {
            WKUIConversationMsg convMsg = allMsg.uiConversationMsg;
            if (convMsg.getRemoteMsgExtra() == null || TextUtils.isEmpty(convMsg.getRemoteMsgExtra().draft)) {
                WKConversationMsgExtra extra = WKIM.getInstance().getConversationManager()
                        .getMsgExtraWithChannel(convMsg.channelID, convMsg.channelType);
                if (extra != null && !TextUtils.isEmpty(extra.draft)) {
                    convMsg.setRemoteMsgExtra(extra);
                    allMsg.isResetContent = true;
                }
            }
        }
        // 刷新当前 tab 可见的 adapter 项
        for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
            if (chatConversationAdapter.getData().get(i).isSectionHeader) continue;
            if (chatConversationAdapter.getData().get(i).isResetContent) {
                notifyRecycler(i, chatConversationAdapter.getData().get(i));
            }
        }
    }

    /**
     * 延迟触发 Space 会话重新同步。
     * 当收到不属于当前 Space 的实时消息时调用，使用防抖避免频繁请求。
     * 同步结果由 RefreshMsgListListener 处理，会重新校准 spaceConversationKeys。
     */
    private final Runnable spaceResyncRunnable = () -> {
        pendingSpaceResync = false;
        spaceConversationKeys.clear();
        allConversations.clear();
        chatConversationAdapter.setList(new ArrayList<>());
        Schedulers.io().scheduleDirect(() -> {
            WKIM.getInstance().getConversationManager().clearAll();
            // setSyncConversationListener 内部有 DB 查询，必须在 IO 线程执行，
            // 放主线程会和 sync 写入争抢数据库锁导致 ANR
            WKIM.getInstance().getConversationManager().setSyncConversationListener(result -> {
            });
        });
    };

    private void scheduleSpaceResync() {
        if (pendingSpaceResync) return;
        pendingSpaceResync = true;
        pingHandler.postDelayed(spaceResyncRunnable, 500);
    }

    /**
     * 执行 Space 切换（用户手动点 Space 列表 / 跨 Space 加群 Dialog「切换过去」共享路径）。
     *
     * <p>与 iOS 的 serial DB queue 对齐：先立即清空 UI 给用户即时反馈，再把 DB 清理 + sync
     * 放到 IO 线程，避免与主线程的 adapter 刷新争抢 SDK 数据库锁（历史 ANR 源）。
     *
     * <p>对齐 dmwork-web PR#1068：跨 Space 加群「切换过去」按钮复用这条切换路径，
     * 保证手动切换与加群后切换的 UI/数据行为完全一致。
     */
    private void performSpaceSwitch(@NotNull SpaceEntity space) {
        if (space == null || space.space_id == null) return;
        currentSpaceName = space.name;
        MsgModel.getInstance().setCurrentSpaceId(space.space_id, space.name);
        setSpaceSwitcherText(space.name);
        updateSpaceAvatar(space.name);

        SpaceModel.getInstance().invalidateMembersCache();
        CategoryModel.getInstance().invalidateCache();
        loadCategories();

        spaceConversationKeys.clear();
        allConversations.clear();
        chatConversationAdapter.setList(new ArrayList<>());

        Schedulers.io().scheduleDirect(() -> {
            WKIM.getInstance().getConversationManager().clearAll();
            WKIM.getInstance().getConversationManager().setSyncConversationListener(result -> {
            });
            new Handler(Looper.getMainLooper()).post(() ->
                    EndpointManager.getInstance().invoke(WKConstants.refreshContacts, null)
            );
        });
    }

    /**
     * 消费「加群成功」通知（YUJ-140，对齐 dmwork-web PR#1068）。
     *
     * <p>在 {@link #onResume()} 调用：如果上个界面（扫码加群 / 邀请落地页）保存了 notice，
     * 根据 {@code crossSpace} 走普通 toast 或弹两行 Dialog；用户点「切换过去」才真正切 Space。
     *
     * <p>防重入：{@link com.chat.base.space.JoinSuccessHelper#consumeNotice()} 读完即清，
     * 多次 resume 或多 Fragment 同时 consume 都只会触发一次。
     */
    private void consumeJoinSuccessNoticeIfAny() {
        final androidx.fragment.app.FragmentActivity activity = getActivity();
        if (activity == null || activity.isFinishing()) return;
        com.chat.base.space.JoinSuccessHelper.JoinNotice notice =
                com.chat.base.space.JoinSuccessHelper.consumeNotice();
        if (notice == null) return;

        com.chat.base.space.JoinSuccessDialog.showFromNotice(activity, notice, switched -> {
            // 点「切换过去」→ 切 Space + 进入目标群
            if (android.text.TextUtils.isEmpty(switched.targetSpaceId)) return;
            SpaceEntity target = new SpaceEntity();
            target.space_id = switched.targetSpaceId;
            target.name = switched.targetSpaceName;
            performSpaceSwitch(target);
            if (!android.text.TextUtils.isEmpty(switched.groupNo)) {
                // 等 Space 切换完成的 UI 清理安顿后再打开群聊，避免 adapter.setList(empty) 抢布局
                pingHandler.postDelayed(() -> {
                    androidx.fragment.app.FragmentActivity act = getActivity();
                    if (act == null || act.isFinishing()) return;
                    WKIMUtils.getInstance().startChatActivity(new ChatViewMenu(
                            act, switched.groupNo, WKChannelType.GROUP, 0, true));
                }, 250);
            }
        });
    }

    //排序消息
    private void sortMsg(List<ChatConversationMsg> list) {
        // 拷贝一份，避免对 adapter data list 并发修改
        List<ChatConversationMsg> snapshot = new ArrayList<>(list);
        groupMsg(snapshot);
        Collections.sort(snapshot, (conversationMsg, t1) -> Long.compare(t1.uiConversationMsg.lastMsgTimestamp, conversationMsg.uiConversationMsg.lastMsgTimestamp));
        List<ChatConversationMsg> topList = new ArrayList<>();
        List<ChatConversationMsg> normalList = new ArrayList<>();
        for (int i = 0, size = snapshot.size(); i < size; i++) {
            if (snapshot.get(i).uiConversationMsg.getWkChannel() != null && snapshot.get(i).uiConversationMsg.getWkChannel().top == 1) {
                topList.add(snapshot.get(i));
            } else {
                normalList.add(snapshot.get(i));
            }
        }
        List<ChatConversationMsg> tempList = new ArrayList<>();
        tempList.addAll(normalList);
        tempList.addAll(0, topList);
        AndroidUtilities.runOnUIThread(() -> {
            allConversations.clear();
            allConversations.addAll(tempList);
            filterAndDisplay();
            setAllCount();
            scrollToPositionIfNearTop(0);
            syncSpaceKeysToGlobal();
            // 对齐 iOS：仅首次会话同步完成后调用一次 syncReminder
            if (!hasInitialReminderSynced) {
                hasInitialReminderSynced = true;
                MsgModel.getInstance().syncReminder();
            }
        });
    }

    /**
     * 检查分组内是否有未处理的 @mention 提醒（含群聊和子区），对齐 iOS WKCategorySectionCell
     */
    /**
     * 对齐 iOS WKCategorySectionCell：计算分组下所有会话的未读消息总数（含子区）
     * 子区会话 (COMMUNITY_TOPIC) 不在 allConversations 中，需从 ConversationManager 单独查询
     */
    private int getUnreadCountInCategory(CategoryEntity category, HashMap<String, ChatConversationMsg> channelMap) {
        if (category.groups == null) return 0;
        // 一次性获取所有子区会话，避免在循环内重复查询
        List<WKConversationMsg> topicConvs = WKIM.getInstance().getConversationManager()
                .getWithChannelType(WKChannelType.COMMUNITY_TOPIC);
        int total = 0;
        for (CategoryEntity.CategoryGroup cg : category.groups) {
            ChatConversationMsg msg = channelMap.get(cg.group_no);
            if (msg != null) {
                if (WKReader.isNotEmpty(msg.childList)) {
                    // 有子区列表时，getUnReadCount() 已包含子区未读，不再重复累加
                    total += msg.getUnReadCount();
                    continue;
                }
                // 无子区列表：加父群自身未读
                total += msg.uiConversationMsg.unreadCount;
            }
            // 从 SDK 查子区未读（仅当 childList 为空时才走到这里，避免双重计算）
            String prefix = cg.group_no + "____";
            for (WKConversationMsg conv : topicConvs) {
                if (conv.channelID != null && conv.channelID.startsWith(prefix)) {
                    total += conv.unreadCount;
                }
            }
        }
        return total;
    }

    private boolean hasMentionInCategory(CategoryEntity category, HashMap<String, ChatConversationMsg> channelMap) {
        if (category.groups == null) return false;
        String loginUID = WKConfig.getInstance().getUid();
        for (CategoryEntity.CategoryGroup cg : category.groups) {
            if (!channelMap.containsKey(cg.group_no)) continue;
            // 从 SDK DB 直接读取提醒（不依赖会话对象的 reminderList，避免时序问题）
            List<WKReminder> reminders = WKIM.getInstance().getReminderManager()
                    .getReminders(cg.group_no, WKChannelType.GROUP);
            if (WKReader.isNotEmpty(reminders)) {
                for (WKReminder r : reminders) {
                    if (r.type == WKMentionType.WKReminderTypeMentionMe && r.done == 0
                            && (TextUtils.isEmpty(r.publisher) || !r.publisher.equals(loginUID))) {
                        return true;
                    }
                }
            }
            // 检查子区：直接查 DB（不依赖 threadDataCache，冷启动时可能为空）
            if (ReminderDBManager.getInstance().hasUndoneReminderWithChannelPrefix(
                    cg.group_no, WKMentionType.WKReminderTypeMentionMe)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 为任意 ChatConversationMsg 列表计算未读总数。
     *
     * <p>YUJ-183：未分组 section 包含服务端 defaultCategory 的群 + 客户端兜底的
     * orphan 群（典型为外部群），无法复用 {@link #getUnreadCountInCategory} 的
     * CategoryEntity 入参版本。与父版本保持一致的口径（有 childList 走
     * {@link ChatConversationMsg#getUnReadCount()}，否则用自身 unreadCount），
     * 但不再为未分组 section 额外查 SDK 的 COMMUNITY_TOPIC 子区（Android 现行
     * 分组行为：子区未读只计入其所属的用户自建 category，默认组不聚合）。
     */
    private int computeUnreadCountForItems(List<ChatConversationMsg> items) {
        int total = 0;
        if (items == null) return 0;
        for (ChatConversationMsg msg : items) {
            if (msg == null || msg.uiConversationMsg == null) continue;
            if (WKReader.isNotEmpty(msg.childList)) {
                total += msg.getUnReadCount();
            } else {
                total += msg.uiConversationMsg.unreadCount;
            }
        }
        return total;
    }

    /**
     * 为任意 ChatConversationMsg 列表判定是否存在未处理的 @mention 提醒。
     *
     * <p>YUJ-183：未分组 section orphan 合并路径的对应版本。保持与
     * {@link #hasMentionInCategory} 一致的判定口径（SDK DB 查提醒 + 子区前缀查）。
     */
    private boolean computeHasMentionForItems(List<ChatConversationMsg> items) {
        if (items == null || items.isEmpty()) return false;
        String loginUID = WKConfig.getInstance().getUid();
        for (ChatConversationMsg msg : items) {
            if (msg == null || msg.uiConversationMsg == null) continue;
            String cid = msg.uiConversationMsg.channelID;
            if (TextUtils.isEmpty(cid)) continue;
            List<WKReminder> reminders = WKIM.getInstance().getReminderManager()
                    .getReminders(cid, WKChannelType.GROUP);
            if (WKReader.isNotEmpty(reminders)) {
                for (WKReminder r : reminders) {
                    if (r.type == WKMentionType.WKReminderTypeMentionMe && r.done == 0
                            && (TextUtils.isEmpty(r.publisher) || !r.publisher.equals(loginUID))) {
                        return true;
                    }
                }
            }
            if (ReminderDBManager.getInstance().hasUndoneReminderWithChannelPrefix(
                    cid, WKMentionType.WKReminderTypeMentionMe)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据当前 tab 过滤 allConversations 并刷新 adapter。
     * 群聊 tab (0): channelType == GROUP，按 category 分组显示
     * 私聊 tab (1): channelType == PERSONAL，无分组
     */
    private void filterAndDisplay() {
        if (chatConversationAdapter == null || getActivity() == null) return;
        if (currentTab == 0) {
            // 群聊 tab：按 category 分组显示
            // 1. 建立 channelId → ChatConversationMsg 映射
            HashMap<String, ChatConversationMsg> channelMap = new HashMap<>();
            List<ChatConversationMsg> snapshot = new ArrayList<>(allConversations);
            for (ChatConversationMsg msg : snapshot) {
                if (msg == null || msg.uiConversationMsg == null || msg.uiConversationMsg.channelType != WKChannelType.GROUP)
                    continue;
                channelMap.put(msg.uiConversationMsg.channelID, msg);
            }

            List<ChatConversationMsg> displayList = new ArrayList<>();

            // 2. 用户自建分组排在前面，未分组（category_id == null）排在最后
            List<CategoryEntity> userCategories = new ArrayList<>();
            CategoryEntity defaultCategory = null;
            List<CategoryEntity> categories = new ArrayList<>(categoryList);
            for (CategoryEntity category : categories) {
                if (category == null || category.groups == null) continue;
                if (category.is_default) {
                    defaultCategory = category;
                } else {
                    userCategories.add(category);
                }
            }

            // 3. 用户自建分组
            for (CategoryEntity category : userCategories) {
                // 先统计实际有会话的群聊数量
                int actualCount = 0;
                if (category.groups != null) {
                    for (CategoryEntity.CategoryGroup cg : category.groups) {
                        if (channelMap.containsKey(cg.group_no)) actualCount++;
                    }
                }
                ChatConversationMsg sectionHeader = new ChatConversationMsg(category.category_id, category.name);
                sectionHeader.sectionGroupCount = actualCount;
                sectionHeader.sectionUnreadCount = getUnreadCountInCategory(category, channelMap);
                sectionHeader.sectionHasMention = hasMentionInCategory(category, channelMap);
                displayList.add(sectionHeader);
                if (!chatConversationAdapter.isSectionCollapsed(category.category_id)) {
                    List<ChatConversationMsg> sectionItems = new ArrayList<>();
                    for (CategoryEntity.CategoryGroup cg : category.groups) {
                        ChatConversationMsg msg = channelMap.get(cg.group_no);
                        if (msg != null) {
                            sectionItems.add(msg);
                        }
                    }
                    sectionItems.sort((a, b) -> {
                        int topA = (a.uiConversationMsg.getWkChannel() != null && a.uiConversationMsg.getWkChannel().top == 1) ? 1 : 0;
                        int topB = (b.uiConversationMsg.getWkChannel() != null && b.uiConversationMsg.getWkChannel().top == 1) ? 1 : 0;
                        if (topA != topB) return topB - topA;
                        return Long.compare(b.uiConversationMsg.lastMsgTimestamp, a.uiConversationMsg.lastMsgTimestamp);
                    });
                    displayList.addAll(sectionItems);
                }
            }

            // 4. 未分组：服务端 defaultCategory.groups + 客户端兜底 orphan 群
            // YUJ-183 P1 修复：外部群（group.space_id != currentSpace 但我以外部成员
            // 身份加入，source_space_id == currentSpace）已通过 SpaceFilter 校验进入
            // allConversations，但不在服务端 /spaces/{spaceId}/categories 任一
            // category.groups 里（后端 categories 仅列 group.space_id == current
            // space 的群）。提交 c215fa5a 删除客户端 orphan 兜底后这类群消失，
            // 这里把未被任何 category 覆盖的群并入「未分组」兜底展示。
            List<ChatConversationMsg> ungroupedItems = new ArrayList<>();
            Set<String> ungroupedAddedIds = new HashSet<>();
            if (defaultCategory != null && defaultCategory.groups != null) {
                for (CategoryEntity.CategoryGroup cg : defaultCategory.groups) {
                    if (cg == null || cg.group_no == null) continue;
                    ChatConversationMsg msg = channelMap.get(cg.group_no);
                    if (msg != null && ungroupedAddedIds.add(cg.group_no)) {
                        ungroupedItems.add(msg);
                    }
                }
            }
            // 客户端兜底：allConversations 中未被任何 category 覆盖的群聊（典型为外部群）。
            List<String> orphanIds = com.chat.uikit.fragment.CategoryDisplayHelper
                    .findOrphanGroupNos(channelMap.keySet(), categories);
            for (String cid : orphanIds) {
                ChatConversationMsg msg = channelMap.get(cid);
                if (msg != null && ungroupedAddedIds.add(cid)) {
                    ungroupedItems.add(msg);
                }
            }

            if (!ungroupedItems.isEmpty()) {
                String sectionId = "ungrouped";
                String sectionTitle = (defaultCategory != null && defaultCategory.name != null)
                        ? defaultCategory.name
                        : getString(R.string.default_group);
                ChatConversationMsg ungroupedHeader = new ChatConversationMsg(sectionId, sectionTitle);
                ungroupedHeader.sectionGroupCount = ungroupedItems.size();
                ungroupedHeader.sectionUnreadCount = computeUnreadCountForItems(ungroupedItems);
                ungroupedHeader.sectionHasMention = computeHasMentionForItems(ungroupedItems);
                displayList.add(ungroupedHeader);
                if (!chatConversationAdapter.isSectionCollapsed(sectionId)) {
                    ungroupedItems.sort((a, b) -> {
                        int topA = (a.uiConversationMsg.getWkChannel() != null && a.uiConversationMsg.getWkChannel().top == 1) ? 1 : 0;
                        int topB = (b.uiConversationMsg.getWkChannel() != null && b.uiConversationMsg.getWkChannel().top == 1) ? 1 : 0;
                        if (topA != topB) return topB - topA;
                        return Long.compare(b.uiConversationMsg.lastMsgTimestamp, a.uiConversationMsg.lastMsgTimestamp);
                    });
                    displayList.addAll(ungroupedItems);
                }
            }

            chatConversationAdapter.setList(displayList);
        } else {
            // 私聊 tab：无分组
            List<ChatConversationMsg> filtered = new ArrayList<>();
            for (ChatConversationMsg msg : allConversations) {
                if (msg.uiConversationMsg != null && msg.uiConversationMsg.channelType == WKChannelType.PERSONAL) {
                    filtered.add(msg);
                }
            }
            chatConversationAdapter.setList(filtered);
        }
    }

    private void showSectionManagePopup(String sectionId, String sectionTitle, View anchor) {
        if (getActivity() == null) return;
        List<PopupMenuItem> items = new ArrayList<>();

        // 新建群聊（自动归入当前分组）
        items.add(new PopupMenuItem(getString(R.string.create_new_group), R.mipmap.msg_contacts, () -> {
            Intent intent = new Intent(getActivity(), ChooseContactsActivity.class);
            intent.putExtra("categoryId", sectionId);
            startActivity(intent);
        }));

        // 重命名
        items.add(new PopupMenuItem(getString(R.string.rename_category), R.mipmap.msg_edit, () -> {
            showRenameCategoryDialog(sectionId, sectionTitle);
        }));

        // 移到最前（已在最前时不显示）
        boolean isFirst = false;
        for (CategoryEntity cat : categoryList) {
            if (cat.category_id != null) {
                isFirst = cat.category_id.equals(sectionId);
                break;
            }
        }
        if (!isFirst) {
            items.add(new PopupMenuItem(getString(R.string.move_to_front), R.mipmap.msg_forward, () -> {
                String spaceId = MsgModel.getInstance().getCurrentSpaceId();
                if (TextUtils.isEmpty(spaceId)) return;

                List<String> newOrder = new ArrayList<>();
                newOrder.add(sectionId);
                for (CategoryEntity cat : categoryList) {
                    if (cat.category_id != null && !cat.category_id.equals(sectionId)) {
                        newOrder.add(cat.category_id);
                    }
                }
                CategoryModel.getInstance().sort(spaceId, newOrder, (code, msg) -> {
                    if (code == HttpResponseCode.success) {
                        loadCategories();
                    } else {
                        WKToastUtils.getInstance().showToastNormal(msg);
                    }
                });
            }));
        }

        // 分组排序（拖拽排序）
        items.add(new PopupMenuItem(getString(R.string.reorder_category), R.mipmap.msg_reorder, () -> {
            showReorderCategorySheet();
        }));

        // 删除分组
        PopupMenuItem deleteItem = new PopupMenuItem(getString(R.string.delete_category), R.mipmap.msg_delete, () -> {
            WKDialogUtils.getInstance().showDialog(getActivity(),
                    getString(R.string.delete_category),
                    getString(R.string.delete_category_confirm),
                    true, "", getString(R.string.base_delete),
                    0, ContextCompat.getColor(requireActivity(), R.color.red),
                    index -> {
                        if (index == 1) {
                            String spaceId = MsgModel.getInstance().getCurrentSpaceId();
                            if (TextUtils.isEmpty(spaceId)) return;
                            CategoryModel.getInstance().delete(spaceId, sectionId, (code, msg) -> {
                                if (code == HttpResponseCode.success) {
                                    loadCategories();
                                } else {
                                    WKToastUtils.getInstance().showToastNormal(msg);
                                }
                            });
                        }
                    });
        });
        deleteItem.setColor(ContextCompat.getColor(requireContext(), R.color.red));
        items.add(deleteItem);

        WKDialogUtils.getInstance().showScreenPopup(anchor, items);
    }

    private void showRenameCategoryDialog(String categoryId, String currentName) {
        if (getActivity() == null) return;
        String spaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (TextUtils.isEmpty(spaceId)) return;

        Context ctx = requireContext();
        int accentColor = ContextCompat.getColor(ctx, R.color.colorAccent);

        // 根布局
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(24);
        root.setPadding(pad, AndroidUtilities.dp(20), pad, AndroidUtilities.dp(16));

        // 标题
        TextView titleTv = new TextView(ctx);
        titleTv.setText(R.string.rename_category_title);
        titleTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 20);
        titleTv.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTv.setTextColor(ContextCompat.getColor(ctx, R.color.colorDark));
        root.addView(titleTv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 输入框
        android.widget.EditText editText = new android.widget.EditText(ctx);
        editText.setHint(R.string.rename_category_hint);
        editText.setText(currentName);
        editText.setSelection(currentName != null ? currentName.length() : 0);
        editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        editText.setTextColor(ContextCompat.getColor(ctx, R.color.colorDark));
        editText.setHintTextColor(ContextCompat.getColor(ctx, R.color.color999));
        editText.setSingleLine();
        editText.setMaxLines(1);
        editText.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(20)});
        editText.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12),
                AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        android.graphics.drawable.GradientDrawable inputBg = new android.graphics.drawable.GradientDrawable();
        inputBg.setCornerRadius(AndroidUtilities.dp(8));
        inputBg.setStroke(AndroidUtilities.dp(1.5f), accentColor);
        inputBg.setColor(ContextCompat.getColor(ctx, R.color.white));
        editText.setBackground(inputBg);
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        editParams.topMargin = AndroidUtilities.dp(14);
        root.addView(editText, editParams);

        // 按钮行
        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams btnRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnRowParams.topMargin = AndroidUtilities.dp(18);

        TextView cancelBtn = new TextView(ctx);
        cancelBtn.setText(R.string.cancel);
        cancelBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        cancelBtn.setTextColor(ContextCompat.getColor(ctx, R.color.colorDark));
        cancelBtn.setGravity(Gravity.CENTER);
        cancelBtn.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(10),
                AndroidUtilities.dp(24), AndroidUtilities.dp(10));
        android.graphics.drawable.GradientDrawable cancelBg = new android.graphics.drawable.GradientDrawable();
        cancelBg.setCornerRadius(AndroidUtilities.dp(8));
        cancelBg.setStroke(AndroidUtilities.dp(1), ContextCompat.getColor(ctx, R.color.colorE8E7E7));
        cancelBg.setColor(ContextCompat.getColor(ctx, R.color.white));
        cancelBtn.setBackground(cancelBg);

        TextView confirmBtn = new TextView(ctx);
        confirmBtn.setText(R.string.sure);
        confirmBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        confirmBtn.setTextColor(accentColor);
        confirmBtn.setGravity(Gravity.CENTER);
        confirmBtn.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(10),
                AndroidUtilities.dp(24), AndroidUtilities.dp(10));
        android.graphics.drawable.GradientDrawable confirmBtnBg = new android.graphics.drawable.GradientDrawable();
        confirmBtnBg.setCornerRadius(AndroidUtilities.dp(8));
        confirmBtnBg.setColor(accentColor & 0x30FFFFFF | 0x30000000);
        confirmBtn.setBackground(confirmBtnBg);

        LinearLayout.LayoutParams confirmBtnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        confirmBtnParams.leftMargin = AndroidUtilities.dp(12);
        btnRow.addView(cancelBtn);
        btnRow.addView(confirmBtn, confirmBtnParams);
        root.addView(btnRow, btnRowParams);

        // Dialog
        android.app.AlertDialog.Builder dialogBuilder = new android.app.AlertDialog.Builder(ctx);
        dialogBuilder.setView(root);
        android.app.AlertDialog dialog = dialogBuilder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            android.graphics.drawable.GradientDrawable windowBg = new android.graphics.drawable.GradientDrawable();
            windowBg.setCornerRadius(AndroidUtilities.dp(16));
            windowBg.setColor(ContextCompat.getColor(ctx, R.color.white));
            dialog.getWindow().setBackgroundDrawable(windowBg);
        }
        dialog.show();

        editText.requestFocus();
        editText.postDelayed(() -> {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(editText, 0);
        }, 200);

        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        confirmBtn.setOnClickListener(v -> {
            String newName = editText.getText().toString().trim();
            if (TextUtils.isEmpty(newName) || newName.equals(currentName)) {
                dialog.dismiss();
                return;
            }
            CategoryModel.getInstance().rename(spaceId, categoryId, newName, (code, msg) -> {
                if (code == HttpResponseCode.success) {
                    loadCategories();
                } else {
                    WKToastUtils.getInstance().showToastNormal(msg);
                }
            });
            dialog.dismiss();
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showReorderCategorySheet() {
        if (getActivity() == null) return;
        String spaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (TextUtils.isEmpty(spaceId)) return;

        // 过滤出用户自建分组
        List<CategoryEntity> reorderList = new ArrayList<>();
        for (CategoryEntity cat : categoryList) {
            if (cat.category_id != null) {
                reorderList.add(cat);
            }
        }
        if (reorderList.isEmpty()) return;

        Context ctx = requireContext();
        BottomSheet.Builder builder = new BottomSheet.Builder(ctx, false);
        builder.setApplyBottomPadding(false);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(ctx, R.color.screen_bg));

        // ── 标题栏 ──
        FrameLayout header = new FrameLayout(ctx);
        header.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16),
                AndroidUtilities.dp(20), AndroidUtilities.dp(14));

        TextView titleTv = new TextView(ctx);
        titleTv.setText(R.string.reorder_category);
        titleTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 17);
        titleTv.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTv.setTextColor(ContextCompat.getColor(ctx, R.color.colorDark));
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        titleParams.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        header.addView(titleTv, titleParams);

        TextView doneTv = new TextView(ctx);
        doneTv.setText(R.string.reorder_done);
        doneTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        doneTv.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        doneTv.setTextColor(ContextCompat.getColor(ctx, R.color.colorAccent));
        FrameLayout.LayoutParams doneParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        doneParams.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        header.addView(doneTv, doneParams);

        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── 分割线 ──
        View divider = new View(ctx);
        divider.setBackgroundColor(ContextCompat.getColor(ctx, R.color.colorE8E7E7));
        root.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        // ── RecyclerView ──
        RecyclerView recyclerView = new RecyclerView(ctx);
        recyclerView.setLayoutManager(new LinearLayoutManager(ctx));

        // ItemTouchHelper（前置声明，adapter 内需引用）
        final androidx.recyclerview.widget.ItemTouchHelper[] itemTouchHelper = new androidx.recyclerview.widget.ItemTouchHelper[1];

        // Adapter
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new RecyclerView.Adapter<>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                FrameLayout row = new FrameLayout(ctx);
                row.setBackground(ContextCompat.getDrawable(ctx, R.drawable.layout_bg));
                row.setMinimumHeight(AndroidUtilities.dp(52));
                row.setLayoutParams(new RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));

                // 分组名
                TextView nameTv = new TextView(ctx);
                nameTv.setTag("name");
                nameTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
                nameTv.setTextColor(ContextCompat.getColor(ctx, R.color.colorDark));
                FrameLayout.LayoutParams nameParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                nameParams.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
                nameParams.leftMargin = AndroidUtilities.dp(20);
                nameParams.rightMargin = AndroidUtilities.dp(56);
                row.addView(nameTv, nameParams);

                // 拖拽手柄
                ImageView handleIv = new ImageView(ctx);
                handleIv.setTag("handle");
                handleIv.setImageResource(R.drawable.ic_drag_handle);
                handleIv.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12),
                        AndroidUtilities.dp(12), AndroidUtilities.dp(12));
                FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(
                        AndroidUtilities.dp(48), AndroidUtilities.dp(48));
                handleParams.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
                handleParams.rightMargin = AndroidUtilities.dp(4);
                row.addView(handleIv, handleParams);

                // 分割线
                View divider = new View(ctx);
                divider.setBackgroundColor(ContextCompat.getColor(ctx, R.color.colorE8E7E7));
                FrameLayout.LayoutParams divParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, 1);
                divParams.gravity = Gravity.BOTTOM;
                divParams.leftMargin = AndroidUtilities.dp(20);
                row.addView(divider, divParams);

                return new RecyclerView.ViewHolder(row) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                CategoryEntity cat = reorderList.get(position);
                TextView nameTv = holder.itemView.findViewWithTag("name");
                nameTv.setText(cat.name);

                ImageView handleIv = holder.itemView.findViewWithTag("handle");
                handleIv.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                        itemTouchHelper[0].startDrag(holder);
                    }
                    return false;
                });
            }

            @Override
            public int getItemCount() {
                return reorderList.size();
            }
        };

        // ItemTouchHelper callback
        androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback callback =
                new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                        androidx.recyclerview.widget.ItemTouchHelper.UP | androidx.recyclerview.widget.ItemTouchHelper.DOWN,
                        0) {
                    @Override
                    public boolean onMove(@NonNull RecyclerView rv,
                                          @NonNull RecyclerView.ViewHolder from,
                                          @NonNull RecyclerView.ViewHolder to) {
                        int fromPos = from.getAdapterPosition();
                        int toPos = to.getAdapterPosition();
                        java.util.Collections.swap(reorderList, fromPos, toPos);
                        adapter.notifyItemMoved(fromPos, toPos);
                        rv.performHapticFeedback(
                                android.view.HapticFeedbackConstants.LONG_PRESS);
                        return true;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                        // no swipe
                    }

                    @Override
                    public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                        super.onSelectedChanged(viewHolder, actionState);
                        if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG
                                && viewHolder != null) {
                            viewHolder.itemView.setAlpha(0.85f);
                            viewHolder.itemView.setElevation(AndroidUtilities.dp(4));
                        }
                    }

                    @Override
                    public void clearView(@NonNull RecyclerView rv,
                                          @NonNull RecyclerView.ViewHolder viewHolder) {
                        super.clearView(rv, viewHolder);
                        viewHolder.itemView.setAlpha(1f);
                        viewHolder.itemView.setElevation(0f);
                    }
                };
        itemTouchHelper[0] = new androidx.recyclerview.widget.ItemTouchHelper(callback);
        itemTouchHelper[0].attachToRecyclerView(recyclerView);
        recyclerView.setAdapter(adapter);

        root.addView(recyclerView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── 底部安全间距 ──
        View bottomPad = new View(ctx);
        root.addView(bottomPad, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(8)));

        builder.setCustomView(root);
        BottomSheet sheet = builder.show();
        sheet.setBackgroundColor(ContextCompat.getColor(ctx, R.color.screen_bg));

        // ── 完成按钮 ──
        doneTv.setOnClickListener(v -> {
            List<String> newOrder = new ArrayList<>();
            for (CategoryEntity cat : reorderList) {
                newOrder.add(cat.category_id);
            }
            CategoryModel.getInstance().sort(spaceId, newOrder, (code, msg) -> {
                if (code == HttpResponseCode.success) {
                    loadCategories();
                    sheet.dismiss();
                } else {
                    WKToastUtils.getInstance().showToastNormal(msg);
                }
            });
        });
    }

    private void loadCategories() {
        String spaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (TextUtils.isEmpty(spaceId)) {
            categoryList = new ArrayList<>();
            return;
        }
        CategoryModel.getInstance().list(spaceId, new CategoryModel.ICategoryListListener() {
            @Override
            public void onResult(List<CategoryEntity> list) {
                categoryList = list != null ? list : new ArrayList<>();
                filterAndDisplay();
            }

            @Override
            public void onError(int code, String msg) {
                // 失败时保留旧列表，不影响 UI
            }
        });
    }

    private void showCreateCategoryDialog() {
        if (getActivity() == null) return;
        String spaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (TextUtils.isEmpty(spaceId)) {
            WKToastUtils.getInstance().showToastNormal("请先选择 Space");
            return;
        }
        Context ctx = requireContext();
        int accentColor = ContextCompat.getColor(ctx, R.color.colorAccent);

        // ── 根布局 ──
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(24);
        root.setPadding(pad, AndroidUtilities.dp(20), pad, AndroidUtilities.dp(16));

        // ── 标题 ──
        TextView titleTv = new TextView(ctx);
        titleTv.setText(R.string.create_category_title);
        titleTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 20);
        titleTv.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTv.setTextColor(ContextCompat.getColor(ctx, R.color.colorDark));
        root.addView(titleTv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── 标签 ──
        TextView labelTv = new TextView(ctx);
        labelTv.setText(R.string.create_category_label);
        labelTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        labelTv.setTextColor(ContextCompat.getColor(ctx, R.color.colorDark));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = AndroidUtilities.dp(18);
        root.addView(labelTv, labelParams);

        // ── 输入框 ──
        android.widget.EditText editText = new android.widget.EditText(ctx);
        editText.setHint(R.string.create_category_hint);
        editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        editText.setTextColor(ContextCompat.getColor(ctx, R.color.colorDark));
        editText.setHintTextColor(ContextCompat.getColor(ctx, R.color.color999));
        editText.setSingleLine();
        editText.setMaxLines(1);
        editText.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(20)});
        editText.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12),
                AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        // 圆角边框
        android.graphics.drawable.GradientDrawable inputBg = new android.graphics.drawable.GradientDrawable();
        inputBg.setCornerRadius(AndroidUtilities.dp(8));
        inputBg.setStroke(AndroidUtilities.dp(1.5f), accentColor);
        inputBg.setColor(ContextCompat.getColor(ctx, R.color.white));
        editText.setBackground(inputBg);
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        editParams.topMargin = AndroidUtilities.dp(8);
        root.addView(editText, editParams);

        // ── 提示文字 ──
        TextView helperTv = new TextView(ctx);
        helperTv.setText(R.string.create_category_helper);
        helperTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12);
        helperTv.setTextColor(ContextCompat.getColor(ctx, R.color.color999));
        LinearLayout.LayoutParams helperParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        helperParams.topMargin = AndroidUtilities.dp(6);
        root.addView(helperTv, helperParams);

        // ── 按钮行 ──
        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams btnRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnRowParams.topMargin = AndroidUtilities.dp(18);

        // 取消按钮
        TextView cancelBtn = new TextView(ctx);
        cancelBtn.setText(R.string.cancel);
        cancelBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        cancelBtn.setTextColor(ContextCompat.getColor(ctx, R.color.colorDark));
        cancelBtn.setGravity(Gravity.CENTER);
        cancelBtn.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(10),
                AndroidUtilities.dp(24), AndroidUtilities.dp(10));
        android.graphics.drawable.GradientDrawable cancelBg = new android.graphics.drawable.GradientDrawable();
        cancelBg.setCornerRadius(AndroidUtilities.dp(8));
        cancelBg.setStroke(AndroidUtilities.dp(1), ContextCompat.getColor(ctx, R.color.colorE8E7E7));
        cancelBg.setColor(ContextCompat.getColor(ctx, R.color.white));
        cancelBtn.setBackground(cancelBg);

        // 创建按钮
        TextView createBtn = new TextView(ctx);
        createBtn.setText(R.string.create_category_btn);
        createBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        createBtn.setTextColor(accentColor);
        createBtn.setGravity(Gravity.CENTER);
        createBtn.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(10),
                AndroidUtilities.dp(24), AndroidUtilities.dp(10));
        android.graphics.drawable.GradientDrawable createBtnBg = new android.graphics.drawable.GradientDrawable();
        createBtnBg.setCornerRadius(AndroidUtilities.dp(8));
        createBtnBg.setColor(accentColor & 0x30FFFFFF | 0x30000000); // 淡紫色背景
        createBtn.setBackground(createBtnBg);

        LinearLayout.LayoutParams createBtnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        createBtnParams.leftMargin = AndroidUtilities.dp(12);
        btnRow.addView(cancelBtn);
        btnRow.addView(createBtn, createBtnParams);
        root.addView(btnRow, btnRowParams);

        // ── Dialog ──
        android.app.AlertDialog.Builder dialogBuilder = new android.app.AlertDialog.Builder(ctx);
        dialogBuilder.setView(root);
        android.app.AlertDialog dialog = dialogBuilder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            android.graphics.drawable.GradientDrawable windowBg = new android.graphics.drawable.GradientDrawable();
            windowBg.setCornerRadius(AndroidUtilities.dp(16));
            windowBg.setColor(ContextCompat.getColor(ctx, R.color.white));
            dialog.getWindow().setBackgroundDrawable(windowBg);
        }
        dialog.show();

        // 自动弹出键盘
        editText.requestFocus();
        editText.postDelayed(() -> {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(editText, 0);
        }, 200);

        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        createBtn.setOnClickListener(v -> {
            String text = editText.getText().toString().trim();
            if (TextUtils.isEmpty(text)) return;
            dialog.dismiss();
            CategoryModel.getInstance().create(spaceId, text, new CategoryModel.ICategoryListener() {
                @Override
                public void onResult(CategoryEntity category) {
                    loadCategories();
                }

                @Override
                public void onError(int code, String msg) {
                    WKToastUtils.getInstance().showToastNormal(msg);
                }
            });
        });
    }

    private void showMoveToCategoryDialog(String groupNo) {
        if (getActivity() == null || categoryList.isEmpty()) {
            WKToastUtils.getInstance().showToastNormal(getString(R.string.create_category_hint));
            return;
        }
        Context ctx = requireContext();

        // 找到当前群聊所在的分组
        String currentCategoryId = null;
        for (CategoryEntity cat : categoryList) {
            if (cat.category_id != null && cat.groups != null) {
                for (CategoryEntity.CategoryGroup cg : cat.groups) {
                    if (groupNo.equals(cg.group_no)) {
                        currentCategoryId = cat.category_id;
                        break;
                    }
                }
            }
            if (currentCategoryId != null) break;
        }

        BottomSheet.Builder builder = new BottomSheet.Builder(ctx, false);
        builder.setApplyBottomPadding(false);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(ctx, R.color.screen_bg));

        // ── 标题 ──
        TextView titleTv = new TextView(ctx);
        titleTv.setText(R.string.move_to_category);
        titleTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 17);
        titleTv.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTv.setTextColor(ContextCompat.getColor(ctx, R.color.colorDark));
        titleTv.setGravity(Gravity.CENTER);
        titleTv.setPadding(0, AndroidUtilities.dp(18), 0, AndroidUtilities.dp(14));
        root.addView(titleTv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── 分割线 ──
        View divider = new View(ctx);
        divider.setBackgroundColor(ContextCompat.getColor(ctx, R.color.colorE8E7E7));
        root.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        // ── 分组列表 ──
        String finalCurrentCategoryId = currentCategoryId;
        for (CategoryEntity cat : categoryList) {
            if (cat.category_id == null) continue;
            boolean isCurrent = cat.category_id.equals(finalCurrentCategoryId);

            FrameLayout row = new FrameLayout(ctx);
            row.setBackground(ContextCompat.getDrawable(ctx, R.drawable.layout_bg));
            row.setMinimumHeight(AndroidUtilities.dp(52));

            // 分组名
            TextView nameTv = new TextView(ctx);
            nameTv.setText(cat.name);
            nameTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
            nameTv.setTextColor(ContextCompat.getColor(ctx,
                    isCurrent ? R.color.colorAccent : R.color.colorDark));
            if (isCurrent) {
                nameTv.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            }
            FrameLayout.LayoutParams nameParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            nameParams.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
            nameParams.leftMargin = AndroidUtilities.dp(20);
            nameParams.rightMargin = AndroidUtilities.dp(48);
            row.addView(nameTv, nameParams);

            // 选中标记
            if (isCurrent) {
                ImageView checkIv = new ImageView(ctx);
                checkIv.setImageResource(R.mipmap.msg_check);
                checkIv.setColorFilter(new android.graphics.PorterDuffColorFilter(
                        ContextCompat.getColor(ctx, R.color.colorAccent),
                        android.graphics.PorterDuff.Mode.SRC_IN));
                FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(
                        AndroidUtilities.dp(20), AndroidUtilities.dp(20));
                checkParams.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
                checkParams.rightMargin = AndroidUtilities.dp(20);
                row.addView(checkIv, checkParams);
            }

            row.setOnClickListener(v -> {
                builder.getDismissRunnable().run();
                if (isCurrent) return;
                CategoryModel.getInstance().moveGroup(groupNo, cat.category_id, (code, msg) -> {
                    if (code == HttpResponseCode.success) {
                        loadCategories();
                    } else {
                        WKToastUtils.getInstance().showToastNormal(msg);
                    }
                });
            });

            root.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        // ── "移出分组"（仅已归组时显示）──
        if (currentCategoryId != null) {
            View div2 = new View(ctx);
            div2.setBackgroundColor(ContextCompat.getColor(ctx, R.color.colorE8E7E7));
            root.addView(div2, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));

            FrameLayout removeRow = new FrameLayout(ctx);
            removeRow.setBackground(ContextCompat.getDrawable(ctx, R.drawable.layout_bg));
            removeRow.setMinimumHeight(AndroidUtilities.dp(52));

            TextView removeTv = new TextView(ctx);
            removeTv.setText(R.string.remove_from_category);
            removeTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
            removeTv.setTextColor(ContextCompat.getColor(ctx, R.color.red));
            FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            removeParams.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
            removeParams.leftMargin = AndroidUtilities.dp(20);
            removeRow.addView(removeTv, removeParams);

            removeRow.setOnClickListener(v -> {
                builder.getDismissRunnable().run();
                CategoryModel.getInstance().moveGroup(groupNo, null, (code, msg) -> {
                    if (code == HttpResponseCode.success) {
                        loadCategories();
                    } else {
                        WKToastUtils.getInstance().showToastNormal(msg);
                    }
                });
            });

            root.addView(removeRow, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        // ── 底部安全间距 ──
        View bottomPad = new View(ctx);
        root.addView(bottomPad, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(8)));

        builder.setCustomView(root);
        BottomSheet sheet = builder.show();
        sheet.setBackgroundColor(ContextCompat.getColor(ctx, R.color.screen_bg));
    }

    //检测正在输入的定时器
    private void startTimer() {
        Observable.interval(0, 1, TimeUnit.SECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<>() {
            @Override
            public void onComplete() {
            }

            @Override
            public void onError(@NonNull Throwable e) {
            }

            @Override
            public void onSubscribe(@NonNull Disposable d) {
                disposable = d;
            }

            @Override
            public void onNext(@NonNull Long value) {
                boolean isCancel = true;
                for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                    if (chatConversationAdapter.getData().get(i).isSectionHeader) continue;
                    if (chatConversationAdapter.getData().get(i).typingStartTime > 0) {
                        long typingStartTime = chatConversationAdapter.getData().get(i).typingStartTime;
                        if (WKTimeUtils.getInstance().getCurrentSeconds() - typingStartTime >= 8) {
                            chatConversationAdapter.getData().get(i).isResetTyping = true;
                            chatConversationAdapter.getData().get(i).typingStartTime = 0;
                            chatConversationAdapter.getData().get(i).typingUserName = "";
                            chatConversationAdapter.getData().get(i).isResetContent = true;
                            notifyRecycler(i, chatConversationAdapter.getData().get(i));
//                                    chatConversationAdapter.notifyItemChanged(i, chatConversationAdapter.getData().get(i));
                        }
                        isCancel = false;
                    }
                }
                if (disposable != null && isCancel) {
                    disposable.dispose();
                    disposable = null;
                }
            }
        });
    }

    private void navigateToThreadChat(String channelId) {
        WKIMUtils.getInstance().startChatActivity(
                new ChatViewMenu(getActivity(), channelId, WKChannelType.COMMUNITY_TOPIC, 0, false));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposable != null) {
            disposable.dispose();
            disposable = null;
        }
        WKIM.getInstance().getConversationManager().removeOnRefreshMsgListListener("chat_fragment");
        WKIM.getInstance().getConversationManager().removeOnRefreshMsgListener("chat_fragment");
        WKIM.getInstance().getConversationManager().removeOnDeleteMsgListener("chat_fragment");
        WKIM.getInstance().getCMDManager().removeCmdListener("chat_fragment_cmd");
        WKIM.getInstance().getMsgManager().removeRefreshMsgListener("chat_fragment");
        WKIM.getInstance().getMsgManager().removeClearMsg("chat_fragment");
        WKIM.getInstance().getConnectionManager().removeOnConnectionStatusListener("chat_fragment");
        WKIM.getInstance().getMsgManager().removeSendMsgAckListener("chat_fragment");
        WKIM.getInstance().getReminderManager().removeNewReminderListener("chat_fragment");
        WKIM.getInstance().getChannelManager().removeRefreshChannelInfo("chat_fragment_refresh_channel");
        EndpointManager.getInstance().remove("show_create_category_dialog");
        EndpointManager.getInstance().remove("");
        EndpointManager.getInstance().remove("chat_cover");
        EndpointManager.getInstance().remove("refresh_conversation_extras");
        EndpointManager.getInstance().remove("refresh_conversation_calling");
        EndpointManager.getInstance().remove("scroll_to_unread_channel");
        pingHandler.removeCallbacks(spaceResyncRunnable);
        stopPingTimer();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 子区数据缓存清除并重新加载，返回时实时更新
        chatConversationAdapter.clearAndReloadThreadData();
        // 补充草稿等 extras：syncCoverExtra 可能在 Fragment 创建前完成，onResume 时从 DB 补上
        refreshExtrasIfNeeded();
        // 刷新分组数据，确保新建群聊等操作后分组列表及时更新
        CategoryModel.getInstance().invalidateCache();
        loadCategories();
        // YUJ-140 · 跨 Space 加群 Toast：消费上个界面（扫码 / 邀请加群）留下的 notice
        consumeJoinSuccessNoticeIfAny();
        int pcOnline = WKSharedPreferencesUtil.getInstance().getInt(WKConfig.getInstance().getUid() + "_pc_online");
        wkVBinding.deviceLayout.setVisibility(pcOnline == 1 ? View.VISIBLE : View.GONE);
//        String appLoginType = String.format(getString(R.string.pc_login), getString(R.string.app_name));
//        int muteForApp = WKSharedPreferencesUtil.getInstance().getInt(WKConfig.getInstance().getUid() + "_mute_of_app");
//        if (muteForApp == 1) {
//            pcLoginTv.setText(String.format("%s %s", appLoginType, getString(R.string.wk_kit_phone_notice_close)));
//        } else pcLoginTv.setText(appLoginType);
        EndpointManager.getInstance().setMethod("scroll_to_unread_channel", object -> {
            scrollToUnreadChannel();
            return null;
        });
    }

    private void startConnectTimer() {
        if (connectTimer == null) {
            connectTimer = new Timer();
        }
        connectTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                EndpointManager.getInstance().invoke("show_disconnect_screen", getContext());
            }
        }, 1000);
    }

    private void stopConnectTimer() {
        if (connectTimer != null) {
            connectTimer.cancel();
            connectTimer = null;
        }
    }

    private void startPingTimer() {
        stopPingTimer();
        // 立即执行第一次，有真实延迟数据后再显示信号栏
        pingHandler.post(pingRunnable);
    }

    private void stopPingTimer() {
        pingHandler.removeCallbacks(pingRunnable);
    }

    private final Runnable pingRunnable = new Runnable() {
        @Override
        public void run() {
            String url = WKApiConfig.baseUrl;
            if (url == null || url.isEmpty()) return;
            long start = System.currentTimeMillis();
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(url)
                    .head()
                    .build();
            OkHttpUtils.getInstance().getOkHttpClient().newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(@androidx.annotation.NonNull okhttp3.Call call, @androidx.annotation.NonNull IOException e) {
                    pingHandler.post(() -> pingHandler.postDelayed(pingRunnable, PING_INTERVAL_MS));
                }

                @Override
                public void onResponse(@androidx.annotation.NonNull okhttp3.Call call, @androidx.annotation.NonNull okhttp3.Response response) throws IOException {
                    response.close();
                    currentLatencyMs = System.currentTimeMillis() - start;
                    pingHandler.post(() -> {
                        updateSignalView();
                        pingHandler.postDelayed(pingRunnable, PING_INTERVAL_MS);
                    });
                }
            });
        }
    };

    private void updateSignalView() {
        if (!isAdded() || wkVBinding == null) return;
        wkVBinding.signalLayout.setVisibility(View.VISIBLE);
        wkVBinding.latencyTv.setText(currentLatencyMs + "ms");
        int color;
        if (currentLatencyMs < 100) {
            color = Color.parseColor("#4CAF50");   // 绿色：网速好
        } else if (currentLatencyMs < 300) {
            color = Color.parseColor("#FF9800");   // 橙色：一般
        } else {
            color = Color.parseColor("#F44336");   // 红色：较差
        }
        wkVBinding.signalIv.setColorFilter(color);
        wkVBinding.latencyTv.setTextColor(color);
    }

    private void showNetworkTooltip(View anchor) {
        // 已显示则关闭（toggle）
        if (networkTooltip != null && networkTooltip.isShowing()) {
            networkTooltip.dismiss();
            return;
        }

        View tooltipView = LayoutInflater.from(requireContext())
                .inflate(R.layout.popup_network_status, null);

        long elapsedSec = (System.currentTimeMillis() - connectedAtMs) / 1000;
        String durationText = elapsedSec < 60
                ? getString(R.string.net_connected_duration_sec, elapsedSec)
                : getString(R.string.net_connected_duration_min, elapsedSec / 60);

        ((TextView) tooltipView.findViewById(R.id.statusTv)).setText(getString(R.string.net_status_connected));
        ((TextView) tooltipView.findViewById(R.id.latencyTv)).setText(getString(R.string.net_latency, currentLatencyMs));
        ((TextView) tooltipView.findViewById(R.id.durationTv)).setText(durationText);

        // 每次都创建新对象，避免复用已 dismiss 的 PopupWindow 导致状态异常
        networkTooltip = new PopupWindow(tooltipView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        networkTooltip.setOutsideTouchable(true);
        networkTooltip.setElevation(12f);
        // dismiss 后清空引用，确保下次点击 isShowing() 判断准确
        networkTooltip.setOnDismissListener(() -> networkTooltip = null);

        // showAsDropDown 自动避免超出屏幕，不依赖 getMeasuredWidth
        networkTooltip.showAsDropDown(anchor, 0, 4);
    }

    private int getTopChatCount() {
        int count = 0;
        for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
            if (chatConversationAdapter.getData().get(i).isSectionHeader) continue;
            if (chatConversationAdapter.getData().get(i).uiConversationMsg.getWkChannel() != null && chatConversationAdapter.getData().get(i).uiConversationMsg.getWkChannel().top == 1)
                count++;
        }
        return count;
    }

    private int getInsertIndex(WKUIConversationMsg msg) {
        if (msg.getWkChannel() != null && msg.getWkChannel().top == 1) return 0;
        return getTopChatCount();
    }

    /**
     * 新会话插入到列表顶部附近时，自动滚动使其可见。
     * 仅当用户当前已处于列表顶部附近时才滚动，避免打断用户浏览。
     */
    private void scrollToPositionIfNearTop(int insertIndex) {
        LinearLayoutManager lm = (LinearLayoutManager) wkVBinding.recyclerView.getLayoutManager();
        if (lm == null) return;
        int firstVisible = lm.findFirstVisibleItemPosition();
        // 用户在列表顶部附近（前 3 项之内），滚动到插入位置
        if (firstVisible <= insertIndex + 3) {
            wkVBinding.recyclerView.post(() -> lm.scrollToPositionWithOffset(insertIndex, 0));
        }
    }

    private void notifyRecycler(int index, ChatConversationMsg msg) {
        if (wkVBinding.recyclerView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE || (!wkVBinding.recyclerView.isComputingLayout())) {
            chatConversationAdapter.notifyItemChanged(index, msg);
        }
    }

    private static final String KEY_COLLAPSED_SECTIONS = "collapsed_sections";

    private void saveCollapsedSections() {
        Set<String> collapsed = new HashSet<>();
        for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
            ChatConversationMsg item = chatConversationAdapter.getData().get(i);
            if (item.isSectionHeader && chatConversationAdapter.isSectionCollapsed(item.sectionId)) {
                collapsed.add(item.sectionId);
            }
        }
        String uid = WKConfig.getInstance().getUid();
        WKSharedPreferencesUtil.getInstance().putSP(uid + "_" + KEY_COLLAPSED_SECTIONS,
                TextUtils.join(",", collapsed));
    }

    private void restoreCollapsedSections() {
        String uid = WKConfig.getInstance().getUid();
        String saved = WKSharedPreferencesUtil.getInstance().getSP(uid + "_" + KEY_COLLAPSED_SECTIONS);
        if (!TextUtils.isEmpty(saved)) {
            for (String sectionId : saved.split(",")) {
                if (!TextUtils.isEmpty(sectionId)) {
                    // 直接操作 adapter 的折叠状态
                    chatConversationAdapter.setCollapsed(sectionId, true);
                }
            }
        }
    }

    private void updateTop(String channelID, byte channelType, int top) {
        if (channelType == WKChannelType.PERSONAL) {
            FriendModel.getInstance().updateUserSetting(channelID, "top", top, (code, msg) -> {
                if (code != HttpResponseCode.success) {
                    WKToastUtils.getInstance().showToastNormal(msg);
                }
            });
        } else {
            GroupModel.getInstance().updateGroupSetting(channelID, "top", top, (code, msg) -> {
                if (code != HttpResponseCode.success) {
                    WKToastUtils.getInstance().showToastNormal(msg);
                }
            });
        }

    }

    private void groupMsg(List<ChatConversationMsg> list) {
        // 将消息分组
        HashMap<String, List<ChatConversationMsg>> msgMap = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            if (!TextUtils.isEmpty(list.get(i).uiConversationMsg.parentChannelID)) {
                String key = list.get(i).uiConversationMsg.parentChannelID + "@" + list.get(i).uiConversationMsg.parentChannelType;
                List<ChatConversationMsg> tempList = null;
                if (msgMap.containsKey(key)) {
                    tempList = msgMap.get(key);
                }
                if (tempList == null) tempList = new ArrayList<>();
                tempList.add(list.get(i));
                msgMap.put(key, tempList);
                list.remove(i);
                i--;
            }
        }

        if (!msgMap.isEmpty()) {
            for (String key : msgMap.keySet()) {
                List<ChatConversationMsg> msgList = msgMap.get(key);
                WKUIConversationMsg lastMsg = new WKUIConversationMsg();
//                if (msgList != null && msgList.size() > 0) {
//                    msg.channelID = msgList.get(0).uiConversationMsg.parentChannelID;
//                    msg.channelType = msgList.get(0).uiConversationMsg.parentChannelType;
//                }
                //   Log.e("消息信息",msg.clientMsgNo+"");
                //  ChatConversationMsg lastMsg = new ChatConversationMsg(msg);
                //lastMsg.childList = msgList;
                ChatConversationMsg lastConvMsg = null;
                if (WKReader.isNotEmpty(msgList)) {
                    lastMsg.channelID = msgList.get(0).uiConversationMsg.parentChannelID;
                    lastMsg.channelType = msgList.get(0).uiConversationMsg.parentChannelType;
                    int unreadCount = 0;
                    List<WKReminder> reminderList = new ArrayList<>();
                    for (int i = 0, size = msgList.size(); i < size; i++) {
                        WKUIConversationMsg msg = msgList.get(i).uiConversationMsg;
                        if (msg.lastMsgSeq > lastMsg.lastMsgSeq) {
                            lastMsg.lastMsgSeq = msg.lastMsgSeq;
                        }
                        if (msg.lastMsgTimestamp > lastMsg.lastMsgTimestamp) {
                            lastMsg.lastMsgTimestamp = msg.lastMsgTimestamp;
                            lastMsg.clientMsgNo = msg.clientMsgNo;
                        }
                        unreadCount += msg.unreadCount;
                        List<WKReminder> tempReminders = msg.getReminderList();
                        if (WKReader.isNotEmpty(tempReminders)) {
                            reminderList.addAll(tempReminders);
                        }
                    }
                    lastMsg.unreadCount = unreadCount;
                    lastMsg.setReminderList(reminderList);

                    lastConvMsg = new ChatConversationMsg(lastMsg);
                    lastConvMsg.childList = msgList;
                }
                if (lastConvMsg != null)
                    list.add(lastConvMsg);
            }
        }
    }

    private String getDisplayTitle() {
        if (!TextUtils.isEmpty(currentSpaceName)) {
            return currentSpaceName;
        }
        return getString(R.string.app_name);
    }

    private void loadCurrentSpaceName() {
        String spaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (!TextUtils.isEmpty(spaceId)) {
            // 先从本地缓存立即恢复 Space 名称，避免显示 app_name "Octo"
            String cachedName = MsgModel.getInstance().getCurrentSpaceName();
            if (!TextUtils.isEmpty(cachedName)) {
                currentSpaceName = cachedName;
                setSpaceSwitcherText(cachedName);
                updateSpaceAvatar(cachedName);
            }
            // 再异步校验最新名称
            SpaceModel.getInstance().getMySpaces(new SpaceModel.ISpaceListListener() {
                @Override
                public void onResult(List<SpaceEntity> list) {
                    if (WKReader.isNotEmpty(list)) {
                        for (SpaceEntity space : list) {
                            if (spaceId.equals(space.space_id)) {
                                if (!space.name.equals(currentSpaceName)) {
                                    currentSpaceName = space.name;
                                    setSpaceSwitcherText(space.name);
                                    updateSpaceAvatar(space.name);
                                    MsgModel.getInstance().setCurrentSpaceId(spaceId, space.name);
                                }
                                return;
                            }
                        }
                    }
                    if (TextUtils.isEmpty(currentSpaceName)) {
                        setSpaceSwitcherText(getString(R.string.app_name));
                        updateSpaceAvatar(getString(R.string.app_name));
                    }
                }

                @Override
                public void onError(int code, String msg) {
                    if (TextUtils.isEmpty(currentSpaceName)) {
                        setSpaceSwitcherText(getString(R.string.app_name));
                        updateSpaceAvatar(getString(R.string.app_name));
                    }
                }
            });
        } else {
            setSpaceSwitcherText(getString(R.string.app_name));
            updateSpaceAvatar(getString(R.string.app_name));
        }
    }

    private void setSpaceSwitcherText(String text) {
        for (int i = 0; i < wkVBinding.textSwitcher.getChildCount(); i++) {
            View child = wkVBinding.textSwitcher.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setText(text);
            }
            child.clearAnimation();
        }
        wkVBinding.textSwitcher.requestLayout();
    }

    private void updateSpaceAvatar(String name) {
        if (name != null && !name.isEmpty()) {
            wkVBinding.spaceAvatarTv.setText(name.substring(0, 1).toUpperCase());
        }
    }

    long lastMessageTime = 0L;

    private void scrollToUnreadChannel() {
        long firstTime = 0L;
        int firstIndex = 0;
        boolean isScrollToFirstIndex = true;
        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) wkVBinding.recyclerView.getLayoutManager();
        for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
            if (chatConversationAdapter.getData().get(i).isSectionHeader) continue;
            if (chatConversationAdapter.getData().get(i).getUnReadCount() > 0 && chatConversationAdapter.getData().get(i).uiConversationMsg.getWkChannel() != null && chatConversationAdapter.getData().get(i).uiConversationMsg.getWkChannel().mute == 0) {
                if (firstTime == 0) {
                    firstTime = chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgTimestamp;
                    firstIndex = i;
                }
                if (lastMessageTime == 0 || lastMessageTime > chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgTimestamp) {
                    lastMessageTime = chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgTimestamp;
                    if (linearLayoutManager != null) {
                        linearLayoutManager.scrollToPositionWithOffset(i, 0);
                    }
                    isScrollToFirstIndex = false;
                    break;
                }
            }

        }
        if (isScrollToFirstIndex) {
            lastMessageTime = firstTime;
            if (linearLayoutManager != null) {
                linearLayoutManager.scrollToPositionWithOffset(firstIndex, 0);
            }
        }
    }

    private final HashSet<String> shownHintMsgIds = new HashSet<>();

    private void showPixelHintIfNeeded(WKUIConversationMsg uiMsg) {
        if (uiMsg.channelType != WKChannelType.GROUP
                && uiMsg.channelType != WKChannelType.COMMUNITY_TOPIC) return;

        WKMsg wkMsg = uiMsg.getWkMsg();
        if (wkMsg == null) return;

        String loginUid = WKConfig.getInstance().getUid();
        if (!TextUtils.isEmpty(wkMsg.fromUID) && wkMsg.fromUID.equals(loginUid)) return;

        if (wkMsg.type >= 15) return;

        if (TextUtils.isEmpty(wkMsg.messageID) || "0".equals(wkMsg.messageID)) return;
        String msgIdStr = wkMsg.messageID;
        if (shownHintMsgIds.contains(msgIdStr)) return;
        shownHintMsgIds.add(msgIdStr);
        if (shownHintMsgIds.size() > 500) {
            shownHintMsgIds.clear();
            shownHintMsgIds.add(msgIdStr);
        }

        if (currentTab != 0) return;
        if (!isResumed() || getView() == null) return;
        if (!isChannelInCurrentSpaceForHint(uiMsg.channelID, uiMsg.channelType)) return;

        long msgTimeMs = uiMsg.lastMsgTimestamp > 9999999999L
                ? uiMsg.lastMsgTimestamp
                : uiMsg.lastMsgTimestamp * 1000;
        if (connectedAtMs > 0 && msgTimeMs < connectedAtMs) return;

        WKChannel channel = WKIM.getInstance().getChannelManager()
                .getChannel(uiMsg.channelID, uiMsg.channelType);

        doShowPixelHint(uiMsg, channel, wkMsg);
    }

    private void doShowPixelHint(WKUIConversationMsg uiMsg, WKChannel channel, WKMsg wkMsg) {
        if (channel != null && channel.mute == 1) return;

        String[] parsedThread = null;
        if (uiMsg.channelType == WKChannelType.COMMUNITY_TOPIC) {
            parsedThread = ThreadModel.getInstance().parseChannelId(uiMsg.channelID);
        }

        String name = null;
        String avatarUrl = null;

        if (uiMsg.channelType == WKChannelType.GROUP) {
            if (channel != null) {
                name = !TextUtils.isEmpty(channel.channelRemark)
                        ? channel.channelRemark : channel.channelName;
            }
            avatarUrl = WKApiConfig.getShowAvatar(uiMsg.channelID, uiMsg.channelType);
        } else if (uiMsg.channelType == WKChannelType.COMMUNITY_TOPIC) {
            // 1) channelInfo
            if (channel != null) {
                name = !TextUtils.isEmpty(channel.channelRemark)
                        ? channel.channelRemark : channel.channelName;
            }
            // 2) threadPreviews 缓存
            if (TextUtils.isEmpty(name)) {
                name = chatConversationAdapter.findThreadName(uiMsg.channelID);
            }
            // 3) 父群名 + /#子区
            if (TextUtils.isEmpty(name) && parsedThread != null) {
                WKChannel parentChannel = WKIM.getInstance().getChannelManager()
                        .getChannel(parsedThread[0], WKChannelType.GROUP);
                if (parentChannel != null) {
                    String parentName = !TextUtils.isEmpty(parentChannel.channelRemark)
                            ? parentChannel.channelRemark : parentChannel.channelName;
                    name = parentName + "/#子区";
                    avatarUrl = WKApiConfig.getShowAvatar(parsedThread[0], WKChannelType.GROUP);
                }
            }
        }
        if (TextUtils.isEmpty(name)) return;

        String digest = wkMsg.baseContentMsgModel != null
                ? wkMsg.baseContentMsgModel.getDisplayContent() : null;
        String content = digest;
        if (!TextUtils.isEmpty(digest) && !TextUtils.isEmpty(wkMsg.fromUID)) {
            WKChannel sender = WKIM.getInstance().getChannelManager()
                    .getChannel(wkMsg.fromUID, WKChannelType.PERSONAL);
            if (sender != null) {
                String senderName = !TextUtils.isEmpty(sender.channelRemark)
                        ? sender.channelRemark : sender.channelName;
                if (!TextUtils.isEmpty(senderName)) {
                    content = senderName + ": " + digest;
                }
            }
        }

        long tipOrderSeq = 0;
        if (wkMsg.messageSeq > 0) {
            tipOrderSeq = WKIM.getInstance().getMsgManager()
                    .getMessageOrderSeq(wkMsg.messageSeq, uiMsg.channelID, uiMsg.channelType);
        }

        String finalContent = content;
        String finalAvatarUrl = avatarUrl;
        String finalName = name;
        long finalTipOrderSeq = tipOrderSeq;
        ViewGroup parent = (ViewGroup) getView();
        PixelParticleHintView.show(parent, finalAvatarUrl, finalName, finalContent, () ->
            WKIMUtils.getInstance().startChatActivity(
                    new ChatViewMenu(getActivity(), uiMsg.channelID, uiMsg.channelType, finalTipOrderSeq, false))
        );
    }

    private boolean isChannelInCurrentSpaceForHint(String channelID, byte channelType) {
        String currentSpaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (TextUtils.isEmpty(currentSpaceId)) return true;

        for (ChatConversationMsg msg : allConversations) {
            if (msg.uiConversationMsg != null
                    && channelID.equals(msg.uiConversationMsg.channelID)
                    && channelType == msg.uiConversationMsg.channelType) {
                return true;
            }
        }

        if (channelType == WKChannelType.COMMUNITY_TOPIC) {
            String[] parsed = ThreadModel.getInstance().parseChannelId(channelID);
            if (parsed != null) {
                for (ChatConversationMsg msg : allConversations) {
                    if (msg.uiConversationMsg != null
                            && parsed[0].equals(msg.uiConversationMsg.channelID)
                            && msg.uiConversationMsg.channelType == WKChannelType.GROUP) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void showThreadMuteMenu(String threadChannelId, String threadName, View anchor) {
        if (getActivity() == null) return;
        WKChannel threadChannel = WKIM.getInstance().getChannelManager()
                .getChannel(threadChannelId, WKChannelType.COMMUNITY_TOPIC);
        boolean isMuted = threadChannel != null && threadChannel.mute == 1;
        int newMute = isMuted ? 0 : 1;

        if (threadChannel != null) {
            threadChannel.mute = newMute;
            WKIM.getInstance().getChannelManager().saveOrUpdateChannel(threadChannel);
        } else {
            WKChannel ch = new WKChannel(threadChannelId, WKChannelType.COMMUNITY_TOPIC);
            ch.channelName = threadName;
            ch.mute = newMute;
            WKIM.getInstance().getChannelManager().saveOrUpdateChannel(ch);
        }
        filterAndDisplay();

        GroupModel.getInstance().updateGroupSetting(threadChannelId, "mute", newMute, (code, msg) -> {
        });
    }

}
