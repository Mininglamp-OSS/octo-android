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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
import com.chat.uikit.contacts.service.FriendModel;
import com.chat.uikit.databinding.FragChatConversationLayoutBinding;
import com.chat.uikit.enity.ChatConversationMsg;
import com.chat.uikit.group.service.GroupModel;
import com.chat.uikit.message.MsgModel;
import com.chat.uikit.search.remote.GlobalActivity;
import com.chat.uikit.space.SpaceEntity;
import com.chat.uikit.space.SpaceModel;
import com.chat.uikit.space.SpacePopupWindow;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKCMDKeys;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelState;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKConversationMsgExtra;
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

    // Space 会话过滤：记录当前 Space 下已确认的会话 channel key，
    // 用于过滤实时消息推送中不属于当前 Space 的会话
    private final Set<String> spaceConversationKeys = new HashSet<>();
    private boolean pendingSpaceResync = false;

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

    @Override
    protected void initView() {
        wkVBinding.textSwitcher.setTag(-1);
        wkVBinding.textSwitcher.setFactory(() -> {
            TextView textView = new TextView(getActivity());
            textView.setTextSize(18);
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
        chatConversationAdapter.setAnimationEnable(false);
        wkVBinding.refreshLayout.setEnableOverScrollDrag(true);
        wkVBinding.refreshLayout.setEnableLoadMore(false);
        wkVBinding.refreshLayout.setEnableRefresh(false);

        Theme.setPressedBackground(wkVBinding.deviceIv);
        Theme.setPressedBackground(wkVBinding.searchIv);
        Theme.setPressedBackground(wkVBinding.rightIv);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void initListener() {
        wkVBinding.rightIv.setOnClickListener(view -> {
            List<PopupMenuItem> list = EndpointManager.getInstance().invokes(EndpointCategory.tabMenus, null);
            WKDialogUtils.getInstance().showScreenPopup(view, list);
        });

        wkVBinding.deviceIv.setOnClickListener(v -> EndpointManager.getInstance().invoke("show_pc_login_view", getActivity()));
        wkVBinding.searchIv.setOnClickListener(view1 -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                @SuppressWarnings("unchecked") ActivityOptionsCompat activityOptions = ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity(), new Pair<>(wkVBinding.searchIv, "searchView"));
                startActivity(new Intent(getActivity(), GlobalActivity.class), activityOptions.toBundle());
            } else {
                startActivity(new Intent(getActivity(), GlobalActivity.class));
            }
        });
        wkVBinding.signalLayout.setOnClickListener(v -> showNetworkTooltip(v));

        wkVBinding.spaceHeaderLayout.setOnClickListener(v -> {
            SpacePopupWindow popup = new SpacePopupWindow(requireContext());
            popup.setOnSpaceSelectedListener(space -> {
                currentSpaceName = space.name;
                MsgModel.getInstance().setCurrentSpaceId(space.space_id);
                wkVBinding.textSwitcher.setText(space.name);

                // 清除成员缓存
                SpaceModel.getInstance().invalidateMembersCache();

                // 参考 iOS：先立即清空 UI（给用户即时反馈），再异步清 DB
                spaceConversationKeys.clear();
                chatConversationAdapter.setList(new ArrayList<>());

                // DB 清理 + 会话同步放到后台线程（iOS 用 serial DB queue，不阻塞主线程）
                Schedulers.io().scheduleDirect(() -> {
                    WKIM.getInstance().getConversationManager().clearAll();

                    // 回到主线程触发同步和联系人刷新
                    new Handler(Looper.getMainLooper()).post(() -> {
                        WKIM.getInstance().getConversationManager().setSyncConversationListener(result -> {
                            // WKIM 内部会将同步结果保存到本地 DB 并通知 RefreshMsgListListener 刷新 UI
                        });
                        // 通知联系人列表刷新
                        EndpointManager.getInstance().invoke(WKConstants.refreshContacts, null);
                    });
                });
            });
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
                        MsgModel.getInstance().clearUnread(item.channelID, item.channelType, 0, null);
                        boolean result = WKIM.getInstance().getConversationManager().deleteWitchChannel(item.channelID, item.channelType);
                        if (result) {
                            if (item.getWkChannel() != null && item.getWkChannel().top == 1) {
                                updateTop(item.channelID, item.channelType, 0);
                            }
                            WKIM.getInstance().getMsgManager().clearWithChannel(item.channelID, item.channelType);
                        }
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
            }
        });
        //频道刷新监听
        WKIM.getInstance().getChannelManager().addOnRefreshChannelInfo("chat_fragment_refresh_channel", (channel, isEnd) -> {
            if (channel != null) {
                for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                    if (!TextUtils.isEmpty(chatConversationAdapter.getData().get(i).uiConversationMsg.channelID) && !TextUtils.isEmpty(channel.channelID) && chatConversationAdapter.getData().get(i).uiConversationMsg.channelID.equals(channel.channelID) && chatConversationAdapter.getData().get(i).uiConversationMsg.channelType == channel.channelType) {

                        chatConversationAdapter.getData().get(i).uiConversationMsg.setWkChannel(channel);
                        // fixme 不能强制刷新整个列表，导致重新获取channel 频繁刷新UI卡顿
                        if (chatConversationAdapter.getData().get(i).isTop != channel.top) {
                            chatConversationAdapter.getData().get(i).isTop = channel.top;
                            sortMsg(chatConversationAdapter.getData());
                        } else {
                            chatConversationAdapter.getData().get(i).isRefreshChannelInfo = true;
                            chatConversationAdapter.getData().get(i).isResetCounter = true;
                            notifyRecycler(i, chatConversationAdapter.getData().get(i));
                        }
                        setAllCount();
                        break;
                    }
                }
            }
        });
        //监听移除最近会话
        WKIM.getInstance().getConversationManager().addOnDeleteMsgListener("chat_fragment", (s, b) -> {
            if (!TextUtils.isEmpty(s)) {
                for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
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
                            wkVBinding.deviceIv.setVisibility(online == 1 ? View.VISIBLE : View.GONE);
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
            for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
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
            if (WKReader.isEmpty(list) || WKReader.isEmpty(chatConversationAdapter.getData()))
                return;
            for (WKReminder reader : list) {
                for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                    if (reader.done == 0
                            && !TextUtils.isEmpty(reader.messageID)
                            && chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg() != null
                            && !TextUtils.isEmpty(chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().messageID)
                            && chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg() != null
                            && reader.messageID.equals(chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().messageID)) {
                        chatConversationAdapter.getData().get(i).isResetReminders = true;
                        notifyRecycler(i, chatConversationAdapter.getData().get(i));
                        break;
                    }
                }
            }
        });
        // 监听刷新最近列表
        WKIM.getInstance().getConversationManager().addOnRefreshMsgListListener("chat_fragment", list -> {
            if (WKReader.isEmpty(list)) {
                return;
            }
            if (list.size() == 1) {
                resetData(list.get(0), true);
                return;
            }

            if (chatConversationAdapter.getData().isEmpty()) {
                // 适配器为空，说明是 Space 切换后的首次同步结果，记录有效会话 key
                spaceConversationKeys.clear();
                List<ChatConversationMsg> uiList = new ArrayList<>();
                for (WKUIConversationMsg uiConversationMsg : list) {
                    // 系统 Bot（BotFather）：跨 Space 未读数清零（参考 iOS）
                    adjustSystemBotForSpace(uiConversationMsg);
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
                boolean isAdd = true;
                for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                    if (!TextUtils.isEmpty(chatConversationAdapter.getData().get(i).uiConversationMsg.channelID) && !TextUtils.isEmpty(uiConversationMsg.channelID) && chatConversationAdapter.getData().get(i).uiConversationMsg.channelID.equals(uiConversationMsg.channelID) && chatConversationAdapter.getData().get(i).uiConversationMsg.channelType == uiConversationMsg.channelType) {
                        // Space 过滤：消息来自其他 Space 时，跳过所有视觉更新
                        if (isMessageFromOtherSpace(uiConversationMsg.getWkMsg())) {
                            isAdd = false;
                            break;
                        }
                        isAdd = false;
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
                        // 保留已有的 extras（草稿等），sync 结果不含 extras 时不覆盖
                        if (uiConversationMsg.getRemoteMsgExtra() != null) {
                            chatConversationAdapter.getData().get(i).uiConversationMsg.setRemoteMsgExtra(uiConversationMsg.getRemoteMsgExtra());
                        }

                        chatConversationAdapter.getData().get(i).uiConversationMsg.setReminderList(uiConversationMsg.getReminderList());
                        chatConversationAdapter.getData().get(i).uiConversationMsg.localExtraMap = null;
                        notifyRecycler(i, chatConversationAdapter.getData().get(i));
                        setAllCount();
                        break;
                    }
                }
                if (isAdd) {
                    // Space 过滤：只添加属于当前 Space 的会话
                    String key = channelKey(uiConversationMsg.channelID, uiConversationMsg.channelType);
                    if (spaceConversationKeys.isEmpty() || spaceConversationKeys.contains(key)) {
                        uiList.add(new ChatConversationMsg(uiConversationMsg));
                        spaceConversationKeys.add(key);
                    } else {
                        // 不在白名单中：群聊一定属于某个 Space，不在白名单则直接丢弃
                        if (uiConversationMsg.channelType == WKChannelType.GROUP) {
                            continue;
                        }
                        // 私聊：新好友的首条消息不会出现在 sync 结果中，不能仅凭白名单丢弃
                        // 检查消息是否真的来自其他 Space
                        if (isMessageFromOtherSpace(uiConversationMsg.getWkMsg())) {
                            continue;
                        }
                        // 消息不属于其他 Space，放行并加入白名单
                        uiList.add(new ChatConversationMsg(uiConversationMsg));
                        spaceConversationKeys.add(key);
                    }
                }
            }
            uiList.addAll(chatConversationAdapter.getData());
            sortMsg(uiList);
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
                wkVBinding.spaceArrowTv.setVisibility(View.GONE);
            } else if (i == WKConnectStatus.success) {
                wkVBinding.textSwitcher.setText(getDisplayTitle());
                wkVBinding.spaceArrowTv.setVisibility(View.VISIBLE);
                connectedAtMs = System.currentTimeMillis();
                // 立即触发第一次 ping，有真实数据后才显示信号栏
                startPingTimer();
                // 注册流程补偿：SDK 连接成功时如果列表仍为空（getChatMsg 的 sync 因连接未就绪而未触发），补一次 sync
                String spaceId = MsgModel.getInstance().getCurrentSpaceId();
                if (!TextUtils.isEmpty(spaceId) && chatConversationAdapter.getData().isEmpty()) {
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
                wkVBinding.spaceArrowTv.setVisibility(View.GONE);
                stopPingTimer();
                wkVBinding.signalLayout.setVisibility(View.GONE);
            } else if (i == WKConnectStatus.noNetwork) {
                wkVBinding.textSwitcher.setText(getString(R.string.network_error_tips));
                wkVBinding.spaceArrowTv.setVisibility(View.GONE);
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
        wkVBinding.textSwitcher.setText(getDisplayTitle());
        wkVBinding.spaceArrowTv.setVisibility(View.VISIBLE);
        startPingTimer();
        EndpointManager.getInstance().setMethod("", EndpointCategory.wkExitChat, object -> {
            if (object != null) {
                WKChannel channel = (WKChannel) object;
                for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
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
    }

    private void getData() {
        getChatMsg();
    }


    private void getChatMsg() {
        String currentSpaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (!TextUtils.isEmpty(currentSpaceId)) {
            // Space 模式：本地 DB 可能有其他 Space 的旧会话，不能直接使用
            // 清空后让 sync(space_id) 返回的数据作为唯一数据源（参考 iOS spaceChannelKeys 白名单机制）
            spaceConversationKeys.clear();
            Schedulers.io().scheduleDirect(() -> {
                WKIM.getInstance().getConversationManager().clearAll();
                new Handler(Looper.getMainLooper()).post(() ->
                    WKIM.getInstance().getConversationManager().setSyncConversationListener(result -> {
                        // sync 结果由 RefreshMsgListListener 处理，会填充 spaceConversationKeys
                    })
                );
            });
            return;
        }
        // 无 Space 模式：直接加载本地所有会话
        WKIM.getInstance().getConversationManager().getAll(list -> {
            List<ChatConversationMsg> tempList = new ArrayList<>();
            if (WKReader.isNotEmpty(list)) {
                for (int i = 0, size = list.size(); i < size; i++) {
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
        int allCount = 0;
        for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
            if (chatConversationAdapter.getData().get(i).uiConversationMsg.getWkChannel() != null && chatConversationAdapter.getData().get(i).uiConversationMsg.getWkChannel().mute == 0)
                allCount = allCount + chatConversationAdapter.getData().get(i).uiConversationMsg.unreadCount;
        }
        if (tabActivity != null) {
            tabActivity.setMsgCount(allCount);
        }
        // EndpointManager.getInstance().invoke("refresh_chat_unread_count",allCount);
    }

    @Override
    public void onAttach(@NotNull Context context) {
        super.onAttach(context);
        tabActivity = (TabActivity) context;
    }

    private void resetChildData(WKUIConversationMsg uiConversationMsg, boolean isEnd) {
        if (WKReader.isNotEmpty(chatConversationAdapter.getData())) {
            boolean isAdd = true;
            for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
                boolean isBreak = false;
                if (WKReader.isNotEmpty(chatConversationAdapter.getData().get(i).childList)) {
                    for (int j = 0, len = chatConversationAdapter.getData().get(i).childList.size(); j < len; j++) {
                        if (chatConversationAdapter.getData().get(i).childList.get(j).uiConversationMsg.channelID.equals(uiConversationMsg.channelID)) {
                            chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgTimestamp = uiConversationMsg.lastMsgTimestamp;
                            chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgSeq = uiConversationMsg.lastMsgSeq;
                            chatConversationAdapter.getData().get(i).uiConversationMsg.clientMsgNo = uiConversationMsg.clientMsgNo;
                            chatConversationAdapter.getData().get(i).uiConversationMsg.unreadCount += uiConversationMsg.unreadCount;
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
        // || (uiConversationMsg.getWkChannel() != null && uiConversationMsg.getWkChannel().follow == 0 && uiConversationMsg.channelType == WKChannelType.PERSONAL)
        if (uiConversationMsg.isDeleted == 1 || TextUtils.equals(uiConversationMsg.channelID, "0")) {
            if (isEnd) {
                sortMsg(chatConversationAdapter.getData());
            }
            return;
        }
        if (!TextUtils.isEmpty(uiConversationMsg.parentChannelID)) {
            resetChildData(uiConversationMsg, isEnd);
            return;
        }
        boolean isAdd = true;
        int index = -1;
        boolean isSort = false;
        if (WKReader.isNotEmpty(chatConversationAdapter.getData())) {
            for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
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
                    if (chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgSeq != uiConversationMsg.lastMsgSeq || chatConversationAdapter.getData().get(i).uiConversationMsg.lastMsgTimestamp != uiConversationMsg.lastMsgTimestamp || (chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg() != null && uiConversationMsg.getWkMsg() != null && !chatConversationAdapter.getData().get(i).uiConversationMsg.getWkMsg().clientMsgNO.equals(uiConversationMsg.getWkMsg().clientMsgNO))) {
                        isSort = true;
                        chatConversationAdapter.getData().get(i).isResetTyping = true;
                        chatConversationAdapter.getData().get(i).typingUserName = "";
                        chatConversationAdapter.getData().get(i).typingStartTime = 0;
                        chatConversationAdapter.getData().get(i).isRefreshStatus = true;
                        index = i;
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
        if (!isEnd) msgCount++;

        if (isAdd) {
            // 系统 Bot（BotFather）：跨 Space 未读数清零（参考 iOS）
            adjustSystemBotForSpace(uiConversationMsg);
            // Space 过滤：只添加属于当前 Space 的会话
            String key = channelKey(uiConversationMsg.channelID, uiConversationMsg.channelType);
            if (!spaceConversationKeys.isEmpty() && !spaceConversationKeys.contains(key)) {
                // 不在白名单中：群聊一定属于某个 Space，不在白名单则直接丢弃
                if (uiConversationMsg.channelType == WKChannelType.GROUP) {
                    return;
                }
                // 私聊：新好友的首条消息不会出现在 sync 结果中，不能仅凭白名单丢弃
                if (isMessageFromOtherSpace(uiConversationMsg.getWkMsg())) {
                    return;
                }
                // 私聊消息不属于其他 Space（无 space_id 或与当前 Space 匹配），放行并加入白名单
            }
            spaceConversationKeys.add(key);
            syncSpaceKeysToGlobal();
            if (!isEnd) {
                chatConversationAdapter.addData(new ChatConversationMsg(uiConversationMsg));
            } else {
                int insertIndex = getInsertIndex(uiConversationMsg);
                chatConversationAdapter.addData(insertIndex, new ChatConversationMsg(uiConversationMsg));
                scrollToPositionIfNearTop(insertIndex);
            }
            setAllCount();
        }
        if (isEnd) {
            if (isSort && msgCount == 0) {
                int insertIndex = getInsertIndex(uiConversationMsg);
                if (insertIndex != index) {
                    if (index != -1) chatConversationAdapter.removeAt(index);
                    chatConversationAdapter.addData(insertIndex, new ChatConversationMsg(uiConversationMsg));
                    scrollToPositionIfNearTop(insertIndex);
                }
            } else {
                if (msgCount > 0) {
                    msgCount = 0;
                    sortMsg(chatConversationAdapter.getData());
                }
            }
        }
    }

    /**
     * 判断消息是否来自其他 Space（非当前 Space）。
     * 用于过滤跨 Space 的实时消息更新，避免错误的未读计数。
     */
    private boolean isMessageFromOtherSpace(WKMsg msg) {
        if (msg == null) return false;
        String currentSpaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (TextUtils.isEmpty(currentSpaceId)) return false;
        String msgSpaceId = null;
        if (!TextUtils.isEmpty(msg.content)) {
            try {
                org.json.JSONObject json = new org.json.JSONObject(msg.content);
                msgSpaceId = json.optString("space_id", "");
            } catch (Exception ignored) {
            }
        }
        if (TextUtils.isEmpty(msgSpaceId) && msg.baseContentMsgModel != null) {
            try {
                org.json.JSONObject json = msg.baseContentMsgModel.encodeMsg();
                if (json != null) {
                    msgSpaceId = json.optString("space_id", "");
                }
            } catch (Exception ignored) {
            }
        }
        return !TextUtils.isEmpty(msgSpaceId) && !msgSpaceId.equals(currentSpaceId);
    }

    // 系统 Bot（如 BotFather）：跨 Space 共享，需要按消息 space_id 过滤（参考 iOS shouldShowConversation）
    private static final Set<String> SYSTEM_BOTS = new HashSet<>(Arrays.asList("botfather"));

    /**
     * 对系统 Bot（BotFather）的会话进行 Space 适配：
     * 当最后一条消息不属于当前 Space 时，清零未读数，避免跨 Space 未读数串扰。
     * BotFather 始终显示在会话列表中（参考 iOS shouldShowConversation），
     * 显示内容由 ChatConversationAdapter.findSystemBotSpaceContent 处理。
     */
    private void adjustSystemBotForSpace(WKUIConversationMsg uiConversationMsg) {
        if (uiConversationMsg == null) return;
        if (!SYSTEM_BOTS.contains(uiConversationMsg.channelID)) return;
        String currentSpaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (TextUtils.isEmpty(currentSpaceId)) return;

        WKMsg msg = uiConversationMsg.getWkMsg();
        if (msg == null) return;

        String msgSpaceId = null;
        if (!TextUtils.isEmpty(msg.content)) {
            try {
                org.json.JSONObject json = new org.json.JSONObject(msg.content);
                msgSpaceId = json.optString("space_id", "");
            } catch (Exception ignored) {
            }
        }
        if (TextUtils.isEmpty(msgSpaceId) && msg.baseContentMsgModel != null) {
            try {
                org.json.JSONObject json = msg.baseContentMsgModel.encodeMsg();
                if (json != null) {
                    msgSpaceId = json.optString("space_id", "");
                }
            } catch (Exception ignored) {
            }
        }
        // 最后一条消息属于其他 Space 时，清零未读数
        if (!TextUtils.isEmpty(msgSpaceId) && !msgSpaceId.equals(currentSpaceId)) {
            uiConversationMsg.unreadCount = 0;
        }
    }

    /**
     * 检查并补充会话列表中缺失的 extras（草稿等）。
     * 在 onResume 中调用，确保无论 syncCoverExtra 何时完成都能显示草稿。
     */
    private void refreshExtrasIfNeeded() {
        if (chatConversationAdapter.getData().isEmpty()) return;
        for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
            WKUIConversationMsg convMsg = chatConversationAdapter.getData().get(i).uiConversationMsg;
            if (convMsg.getRemoteMsgExtra() == null || TextUtils.isEmpty(convMsg.getRemoteMsgExtra().draft)) {
                WKConversationMsgExtra extra = WKIM.getInstance().getConversationManager()
                        .getMsgExtraWithChannel(convMsg.channelID, convMsg.channelType);
                if (extra != null && !TextUtils.isEmpty(extra.draft)) {
                    convMsg.setRemoteMsgExtra(extra);
                    chatConversationAdapter.getData().get(i).isResetContent = true;
                    notifyRecycler(i, chatConversationAdapter.getData().get(i));
                }
            }
        }
    }

    /**
     * 延迟触发 Space 会话重新同步。
     * 当收到不属于当前 Space 的实时消息时调用，使用防抖避免频繁请求。
     * 同步结果由 RefreshMsgListListener 处理，会重新校准 spaceConversationKeys。
     */
    private void scheduleSpaceResync() {
        if (pendingSpaceResync) return;
        pendingSpaceResync = true;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            pendingSpaceResync = false;
            spaceConversationKeys.clear();
            chatConversationAdapter.setList(new ArrayList<>());
            Schedulers.io().scheduleDirect(() -> {
                WKIM.getInstance().getConversationManager().clearAll();
                new Handler(Looper.getMainLooper()).post(() -> {
                    WKIM.getInstance().getConversationManager().setSyncConversationListener(result -> {
                    });
                });
            });
        }, 500);
    }

    //排序消息
    private void sortMsg(List<ChatConversationMsg> list) {
        // 拷贝一份，避免对 adapter data list 并发修改
        List<ChatConversationMsg> snapshot = new ArrayList<>(list);
        groupMsg(snapshot);
        Collections.sort(snapshot, (conversationMsg, t1) -> (int) (t1.uiConversationMsg.lastMsgTimestamp - conversationMsg.uiConversationMsg.lastMsgTimestamp));
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
            chatConversationAdapter.setList(tempList);
            setAllCount();
            scrollToPositionIfNearTop(0);
            syncSpaceKeysToGlobal();
        });
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
        WKIM.getInstance().getConnectionManager().removeOnConnectionStatusListener("chat_fragment");
        WKIM.getInstance().getMsgManager().removeSendMsgAckListener("chat_fragment");
        WKIM.getInstance().getReminderManager().removeNewReminderListener("chat_fragment");
        stopPingTimer();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 补充草稿等 extras：syncCoverExtra 可能在 Fragment 创建前完成，onResume 时从 DB 补上
        refreshExtrasIfNeeded();
        int pcOnline = WKSharedPreferencesUtil.getInstance().getInt(WKConfig.getInstance().getUid() + "_pc_online");
        wkVBinding.deviceIv.setVisibility(pcOnline == 1 ? View.VISIBLE : View.GONE);
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
            SpaceModel.getInstance().getMySpaces(new SpaceModel.ISpaceListListener() {
                @Override
                public void onResult(List<SpaceEntity> list) {
                    if (WKReader.isNotEmpty(list)) {
                        for (SpaceEntity space : list) {
                            if (spaceId.equals(space.space_id)) {
                                currentSpaceName = space.name;
                                wkVBinding.textSwitcher.setText(space.name);
                                return;
                            }
                        }
                    }
                    wkVBinding.textSwitcher.setText(getString(R.string.app_name));
                }

                @Override
                public void onError(int code, String msg) {
                    wkVBinding.textSwitcher.setText(getString(R.string.app_name));
                }
            });
        } else {
            wkVBinding.textSwitcher.setText(getString(R.string.app_name));
        }
    }

    long lastMessageTime = 0L;

    private void scrollToUnreadChannel() {
        long firstTime = 0L;
        int firstIndex = 0;
        boolean isScrollToFirstIndex = true;
        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) wkVBinding.recyclerView.getLayoutManager();
        for (int i = 0, size = chatConversationAdapter.getData().size(); i < size; i++) {
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

}
