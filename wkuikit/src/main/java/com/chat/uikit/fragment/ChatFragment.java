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

package com.chat.uikit.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Trace;
import android.text.TextUtils;
import android.util.Log;
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

import androidx.annotation.Nullable;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

//  phase2 perf: Glide pause/resume on RecyclerView scroll (A3)
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;

import com.chat.base.base.WKBaseFragment;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.foldable.PaneMetrics;
import com.chat.base.net.OkHttpUtils;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.ICommonListener;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.BottomSheet;
import com.chat.base.ui.components.SegmentTabView;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKTimeUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.BuildConfig;
import com.chat.uikit.R;
import com.chat.uikit.TabActivity;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.chat.adapter.ChatConversationAdapter;
import com.chat.uikit.chat.adapter.ConversationPagerAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.chat.uikit.chat.SpaceSyncCoordinator;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import com.chat.uikit.sidebar.FollowedKeysStore;
import com.chat.uikit.sidebar.FollowModel;
import com.chat.uikit.sidebar.SidebarItemEntity;

/**
 * 2019-11-12 14:55
 * 会话
 */
public class ChatFragment extends WKBaseFragment<FragChatConversationLayoutBinding> {

    private ChatConversationAdapter chatConversationAdapter;
    private ChatConversationAdapter followAdapter;
    private ChatConversationAdapter recentAdapter;
    private ConversationPagerAdapter pagerAdapter;
    private Disposable disposable;
    private final List<Integer> refreshIds = new ArrayList<>();
    private Timer connectTimer;
    private TabActivity tabActivity;
    private String currentSpaceName;

    // 分段 Tab 切换：0=群聊, 1=私聊
    private int currentTab = 0;
    private SegmentTabView segmentTabView;
    private final List<ChatConversationMsg> allConversations = new ArrayList<>();
    // 子区会话内存快照：最近 Tab 子区独立行的数据源，和 allConversations 同级
    // 初始从 DB 加载，增量通过 onRefreshMsgListListener 更新
    private final List<ChatConversationMsg> allThreadConversations = new ArrayList<>();
    //  · key-based 去重索引：和 {@link #allConversations} 一一对应，
    // key = channelKey(channelID, channelType)。所有对 allConversations 的
    // 新增 / 删除 / 清空 / 批量替换都必须走 {@link #upsertConversation(ChatConversationMsg)} /
    // {@link #removeConversationByKey(String)} / {@link #clearAllConversations()} /
    // {@link #rebuildConversationIndex()}，否则会出现同一账号多 Space 下 SystemBot
    // (u_10000 / botfather / fileHelper) 重复条目的 UI 回归。
    // 对齐 iOS {@code WKConversationListVM.channelIndex} 语义。
    private final Map<String, ChatConversationMsg> conversationIndex = new HashMap<>();
    private List<CategoryEntity> categoryList = new ArrayList<>();

    // Space 会话过滤：记录当前 Space 下已确认的会话 channel key，
    // 用于过滤实时消息推送中不属于当前 Space 的会话
    private final Set<String> spaceConversationKeys = new HashSet<>();
    private boolean pendingSpaceResync = false;
    // 对齐 iOS：仅在首次会话同步完成后调用一次 syncReminder，避免重复网络请求
    private boolean hasInitialReminderSynced = false;
    // DB 异步查询是否已完成，防止连接成功回调误判 allConversations 为空
    private boolean dbQueryCompleted = false;

    // 对齐 iOS 2 秒节流：onResume 返回时如果距上次刷新不到 2 秒，跳过重载
    private long lastFullRefreshTime = 0;
    private static final long RESUME_THROTTLE_MS = 2000L;
    // Fragment 不可见时标记需要刷新，onResume 时再执行
    private boolean pendingFilterAndDisplay = false;

    //  · filterAndDisplay 的 50ms 合并刷新：消息到达 / reminder / typing / calling
    // 等会在短时间内触发多次，DiffUtil 仍然是全量遍历，合并后仅执行一次。
    private final Handler filterDebounceHandler = new Handler(Looper.getMainLooper());
    private final Runnable filterRunnable = this::filterAndDisplayInternal;
    private static final long FILTER_DEBOUNCE_MS = 50L;
    // 诊断：每 10s 汇总一次 filterAndDisplay 触发次数（Fix 6），便于 Yu / ReviewBot 观察
    // debounce 合并效果。
    private long lastFilterLogMs = 0;
    private int filterCallCount = 0;
    private static final String TAG_FILTER = "ChatFragment.filter";


    /**
     *  · DiffUtil callback。
     * - areItemsTheSame: section 用 sectionId，普通行用 channelID+channelType 作稳定 id
     * - areContentsTheSame: 走 {@link ChatConversationMsg#contentHash()}，覆盖所有 UI 字段
     * - getChangePayload: 本期返回 null（让 BRVAH rebind 变化行，而非整页 notifyDataSetChanged），
     *   未变化的 ViewHolder 不被 invalidate，onTouch 期间不会触发 ACTION_CANCEL。
     */
    private static final DiffUtil.ItemCallback<ChatConversationMsg> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ChatConversationMsg>() {
                @Override
                public boolean areItemsTheSame(@androidx.annotation.NonNull ChatConversationMsg a,
                                               @androidx.annotation.NonNull ChatConversationMsg b) {
                    if (a.isSectionHeader && b.isSectionHeader) {
                        return TextUtils.equals(a.sectionId, b.sectionId);
                    }
                    if (a.isSectionHeader != b.isSectionHeader) return false;
                    if (a.uiConversationMsg == null || b.uiConversationMsg == null) return false;
                    return TextUtils.equals(a.uiConversationMsg.channelID, b.uiConversationMsg.channelID)
                            && a.uiConversationMsg.channelType == b.uiConversationMsg.channelType;
                }

                @Override
                public boolean areContentsTheSame(@androidx.annotation.NonNull ChatConversationMsg a,
                                                  @androidx.annotation.NonNull ChatConversationMsg b) {
                    return a.contentHash() == b.contentHash();
                }

                @Override
                public Object getChangePayload(@androidx.annotation.NonNull ChatConversationMsg oldItem,
                                               @androidx.annotation.NonNull ChatConversationMsg newItem) {
                    // Section headers: only badges/counts change between rebuilds,
                    // skip full rebind when title and collapse state are the same
                    if (oldItem.isSectionHeader && newItem.isSectionHeader) {
                        if (TextUtils.equals(oldItem.sectionTitle, newItem.sectionTitle)
                                && oldItem.sectionGroupCount == newItem.sectionGroupCount) {
                            // title/count same → only badge changed
                            return ChatConversationAdapter.PAYLOAD_SECTION_BADGE;
                        }
                    }
                    // Normal rows: side-channel (notifyRecycler + isResetXxx flags) handles
                    // partial updates. DiffUtil path returns null → full rebind as fallback.
                    return null;
                }
            };

    private String channelKey(String channelID, byte channelType) {
        return channelID + "_" + channelType;
    }

    /**
     *  · 以 key 插入会话（幂等）：如果 {@link #conversationIndex} 已有同 key
     * entry，直接返回现有 entry，不插入重复；否则追加到 {@link #allConversations}
     * 末尾并写入索引。字段级 merge（unreadCount / lastMsgTimestamp / 刷新 flag 等）
     * 由调用方在调用前完成，本方法只保证 key 级别的唯一性。
     *
     * @return 现有 entry 或新插入的 entry；{@code msg == null} 时返回 null
     */
    private ChatConversationMsg upsertConversation(ChatConversationMsg msg) {
        return ConversationIndexOps.upsert(allConversations, conversationIndex, msg);
    }

    /**
     *  · 按 channel key 移除会话：同步清 {@link #allConversations} 和
     * {@link #conversationIndex}。调用方仍需自行 notifyItemRemoved / setAllCount 等 UI 动作。
     */
    private boolean removeConversationByKey(String key) {
        return ConversationIndexOps.removeByKey(allConversations, conversationIndex, key);
    }

    /**  · 清空列表 + 索引。供 Space 切换 / resync / cold-start 清空路径统一调用。 */
    private void clearAllConversations() {
        ConversationIndexOps.clearAll(allConversations, conversationIndex);
    }

    /**
     *  · 列表批量替换后根据当前 {@link #allConversations} 重建索引。
     *
     * <p>适用于 {@code sortMsg} 的 {@code clear + addAll} 语义以及
     * {@code ensureSystemBotsVisible(allConversations)} 直接向列表追加的场景。
     */
    private void rebuildConversationIndex() {
        ConversationIndexOps.rebuildIndex(allConversations, conversationIndex);
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
        followAdapter = new ChatConversationAdapter(new ArrayList<>());
        followAdapter.setDiffCallback(DIFF_CALLBACK);
        followAdapter.setAnimationEnable(false);
        followAdapter.restoreExpandedState(requireContext());
        recentAdapter = new ChatConversationAdapter(new ArrayList<>());
        recentAdapter.setDiffCallback(DIFF_CALLBACK);
        recentAdapter.setAnimationEnable(false);
        recentAdapter.setRecentTabContext(true);
        chatConversationAdapter = followAdapter;

        pagerAdapter = new ConversationPagerAdapter();
        wkVBinding.conversationPager.setAdapter(pagerAdapter);
        wkVBinding.conversationPager.setOffscreenPageLimit(1);
        final int savedTab = Math.max(0, Math.min(1,
                requireContext().getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
                        .getInt("last_tab", 0)));
        if (savedTab != 0) {
            wkVBinding.conversationPager.setCurrentItem(savedTab, false);
            currentTab = savedTab;
        }
        final boolean[] pagerInitialized = {false};
        wkVBinding.conversationPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrollStateChanged(int state) {
                pageSwiping = (state != ViewPager2.SCROLL_STATE_IDLE);
            }

            @Override
            public void onPageSelected(int position) {
                currentTab = position;
                if (pagerInitialized[0] && isAdded()) {
                    requireContext().getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
                            .edit().putInt("last_tab", position).apply();
                }
                chatConversationAdapter = (position == 0) ? followAdapter : recentAdapter;
                if (segmentTabView != null) segmentTabView.selectTabWithoutCallback(position);
                if (chatConversationAdapter != null) {
                    chatConversationAdapter.clearSelected();
                }
                filterAndDisplayForTabSwitch();
                updateFollowEmptyVisibility();
            }
        });
        wkVBinding.conversationPager.post(() -> {
            RecyclerView groupRv = pagerAdapter.getPageRecyclerView(0);
            RecyclerView personalRv = pagerAdapter.getPageRecyclerView(1);
            if (groupRv != null) {
                groupRv.setAdapter(followAdapter);
                addGlideScrollListener(groupRv);
                addSwipeGuard(groupRv);
                addVerticalScrollPriority(groupRv);
                groupRv.addOnScrollListener(scrollIdleWatcher);
            }
            if (personalRv != null) {
                personalRv.setAdapter(recentAdapter);
                addGlideScrollListener(personalRv);
                addSwipeGuard(personalRv);
                addVerticalScrollPriority(personalRv);
                personalRv.addOnScrollListener(scrollIdleWatcher);
            }
            initFollowEmptyView();
            pagerInitialized[0] = true;
            chatConversationAdapter = (currentTab == 0) ? followAdapter : recentAdapter;
        });

        Theme.setPressedBackground(wkVBinding.deviceLayout);
        Theme.setPressedBackground(wkVBinding.rightIv);

        segmentTabView = new SegmentTabView(requireContext(),
                new String[]{getString(R.string.str_group_chat), getString(R.string.str_private_chat)});
        wkVBinding.segmentTabContainer.addView(segmentTabView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT));
        segmentTabView.setOnTabSelectedListener(index -> {
            wkVBinding.conversationPager.setCurrentItem(index, true);
        });
        if (savedTab != 0) {
            segmentTabView.selectTabWithoutCallback(savedTab);
        }
    }

    private void addGlideScrollListener(RecyclerView rv) {
        if (rv == null) return;
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (!isAdded() || isDetached()) return;
                try {
                    RequestManager glideMgr = Glide.with(ChatFragment.this);
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING || newState == RecyclerView.SCROLL_STATE_SETTLING) {
                        glideMgr.pauseRequests();
                    } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        glideMgr.resumeRequests();
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        });
    }

    private boolean pageSwiping = false;

    /** ViewPager2 滑动时拦截页面 RecyclerView 的触摸，防止误触长按。 */
    private void addSwipeGuard(RecyclerView rv) {
        if (rv == null) return;
        rv.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView recyclerView, @NonNull android.view.MotionEvent e) {
                return pageSwiping;
            }
        });
    }

    private void addVerticalScrollPriority(RecyclerView rv) {
        rv.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            private float startX, startY;
            private boolean decided;

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView recyclerView, @NonNull android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        startX = e.getX();
                        startY = e.getY();
                        decided = false;
                        recyclerView.getParent().requestDisallowInterceptTouchEvent(true);
                        break;
                    case android.view.MotionEvent.ACTION_MOVE:
                        if (!decided) {
                            float dx = Math.abs(e.getX() - startX);
                            float dy = Math.abs(e.getY() - startY);
                            int slop = android.view.ViewConfiguration.get(recyclerView.getContext()).getScaledTouchSlop();
                            if (dx > slop || dy > slop) {
                                decided = true;
                                if (dx > dy * 1.5f) {
                                    recyclerView.getParent().requestDisallowInterceptTouchEvent(false);
                                }
                            }
                        }
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        recyclerView.getParent().requestDisallowInterceptTouchEvent(false);
                        break;
                }
                return false;
            }
        });
    }

    private RecyclerView getActiveRecyclerView() {
        if (pagerAdapter == null) return null;
        return pagerAdapter.getPageRecyclerView(currentTab);
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
                    } else {
                        //  · Fix A：分屏态下先立即更新选中态让用户看到反馈，
                        // 再走 startChatActivity（后者走 IO 组装 Intent，存在几十到
                        // 几百 ms 的延迟，没有选中态 UI 会显得卡）。
                        chatConversationAdapter.setSelected(
                                uiConversationMsg.uiConversationMsg.channelID,
                                uiConversationMsg.uiConversationMsg.channelType);
                        WKIMUtils.getInstance().startChatActivity(new ChatViewMenu(getActivity(), uiConversationMsg.uiConversationMsg.channelID, uiConversationMsg.uiConversationMsg.channelType, 0, false));
                    }
                }
            }
        }));
        // recentAdapter 同样设置点击事件
        recentAdapter.addChildClickViewIds(R.id.contentLayout);
        recentAdapter.setOnItemChildClickListener((adapter, view, position) -> SingleClickUtil.determineTriggerSingleClick(view, v -> {
            ChatConversationMsg uiConversationMsg = (ChatConversationMsg) adapter.getItem(position);
            if (uiConversationMsg != null && uiConversationMsg.uiConversationMsg != null) {
                if (view.getId() == R.id.contentLayout) {
                    recentAdapter.setSelected(
                            uiConversationMsg.uiConversationMsg.channelID,
                            uiConversationMsg.uiConversationMsg.channelType);
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
                handleMuteToggle(item.channelID, item.channelType);
            } else if (menu == ChatConversationAdapter.ItemMenu.moveToCategory) {
                showMoveToCategoryDialog(item.channelID, item.channelType);
            } else if (menu == ChatConversationAdapter.ItemMenu.follow) {
                handleFollowToggle(item);
            }
        });
        // recentAdapter 同样需要 listener（私聊长按菜单）
        recentAdapter.addListener((menu, item) -> {
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
                boolean top = item.getWkChannel() != null && item.getWkChannel().top == 1;
                updateTop(item.channelID, item.channelType, top ? 0 : 1);
            } else if (menu == ChatConversationAdapter.ItemMenu.mute) {
                handleMuteToggle(item.channelID, item.channelType);
            } else if (menu == ChatConversationAdapter.ItemMenu.follow) {
                handleFollowToggle(item);
            }
        });
        // 子区预览点击监听
        chatConversationAdapter.setThreadPreviewClickListener(new ChatConversationAdapter.IThreadPreviewClickListener() {
            @Override
            public void onThreadClick(String channelId, String groupNo, String shortId, int isJoined) {
                //  · Fix A：子区点击瞬间更新选中态（分屏态下），再 joinThread / navigate。
                chatConversationAdapter.setSelectedThread(channelId);
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
                // 子区改名同步：channelInfo 更新时清除 threadName 缓存
                if (channel.channelType == WKChannelType.COMMUNITY_TOPIC) {
                    for (ChatConversationMsg threadMsg : allThreadConversations) {
                        if (threadMsg.uiConversationMsg != null
                                && channel.channelID.equals(threadMsg.uiConversationMsg.channelID)) {
                            threadMsg.threadName = !TextUtils.isEmpty(channel.channelName)
                                    ? channel.channelName : null;
                            break;
                        }
                    }
                }
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
                    if (currentTab == 0) {
                        filterAndDisplay();
                    } else {
                        sortMsg(allConversations);
                    }
                    setAllCount();
                } else {
                    refreshChannelInAdapter(followAdapter, channel);
                    refreshChannelInAdapter(recentAdapter, channel);
                    setAllCount();
                }
            }
        });
        //监听移除最近会话
        WKIM.getInstance().getConversationManager().addOnDeleteMsgListener("chat_fragment", (s, b) -> {
            if (!TextUtils.isEmpty(s)) {
                //  · 从 allConversations + conversationIndex 同步移除
                removeConversationByKey(channelKey(s, b));
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
            if (BuildConfig.DEBUG) {
                StringBuilder sb = new StringBuilder("[ConvSync] UI callback: count=" + list.size()
                        + " allConvEmpty=" + allConversations.isEmpty() + " items=");
                for (int di = 0, ds = Math.min(list.size(), 20); di < ds; di++) {
                    if (di > 0) sb.append(", ");
                    sb.append(list.get(di).channelID).append(":").append(list.get(di).channelType);
                }
                android.util.Log.d("ConvSync", sb.toString());
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
                        if (!isChannelInCurrentSpace(parsed[0], WKChannelType.GROUP)) continue;
                        threadParentGroups.add(parsed[0]);
                    }
                    upsertThreadConversation(msg);
                }
            }
            for (String parentGroupNo : threadParentGroups) {
                followAdapter.refreshThreadPreviews(parentGroupNo);
            }
            list = filteredList;
            if (WKReader.isEmpty(list)) {
                if (!threadParentGroups.isEmpty()) {
                    filterAndDisplay();
                    setAllCount();
                }
                return;
            }

            if (allConversations.isEmpty()) {
                // allConversations 为空，说明是首次加载或 Space 切换后的首次同步结果
                //  Phase 2 · T8 埋点：主线程重建列表（逐条 SpaceFilter + per-channel DB 读）。
                // 这个分支 sync 完成后会走一次，成本 ≈ O(N × getMsgExtraWithChannel)。
                long yuj312T8Start = BuildConfig.DEBUG ? SystemClock.elapsedRealtime() : 0L;
                int yuj312T8InputSize = BuildConfig.DEBUG ? list.size() : 0;
                if (BuildConfig.DEBUG) {
                    Trace.beginSection("YUJ312-onRefreshList-rebuild");
                }
                spaceConversationKeys.clear();
                List<ChatConversationMsg> uiList = new ArrayList<>();
                for (WKUIConversationMsg uiConversationMsg : list) {
                    // 私聊 Space 未读数适配：跨 Space 消息不计入未读（参考 iOS）
                    adjustPersonalForSpace(uiConversationMsg);
                    //  Fix A（对齐 iOS PR#95 Defense-in-Depth）：
                    // 冷启动 sync 结果必须先过 SpaceFilter 再回填白名单。
                    // 否则服务端返回的跨 Space 会话（尤其是 botfather 之类的私聊系统 Bot 或
                    // 时序 race 下的外部群）会被无差别写入白名单，后续新消息路径即使走 Fix B
                    // 的 filter 也会因「白名单已含」而被误放行。此分层在 iOS PR#95 等价于
                    // `filterConversationsBySpace FailOpen 分支先 strip 非白名单残留`。
                    String key = channelKey(uiConversationMsg.channelID, uiConversationMsg.channelType);
                    boolean reject;
                    if (uiConversationMsg.channelType == WKChannelType.GROUP) {
                        reject = !isChannelInCurrentSpace(uiConversationMsg.channelID, uiConversationMsg.channelType);
                    } else {
                        // 私聊：用消息 payload space_id 判定；系统 Bot（SYSTEM_BOTS）
                        // 跨 Space 共享——即使消息 space 不匹配也保留展示条目，避免
                        // botfather 在某 Space 彻底消失。
                        reject = isMessageFromOtherSpace(uiConversationMsg.getWkMsg())
                                && !com.chat.base.space.SystemBotsFallback.isSystemBot(uiConversationMsg.channelID);
                    }
                    if (BuildConfig.DEBUG) {
                        android.util.Log.d("ConvSync", "[ConvSync] rebuild: "
                                + uiConversationMsg.channelID + ":" + uiConversationMsg.channelType
                                + " reject=" + reject);
                    }
                    if (reject) {
                        continue;
                    }
                    // sync 结果不含 conversation_extra（草稿等），从本地 DB 补充
                    WKConversationMsgExtra extra = WKIM.getInstance().getConversationManager()
                            .getMsgExtraWithChannel(uiConversationMsg.channelID, uiConversationMsg.channelType);
                    if (extra != null) {
                        uiConversationMsg.setRemoteMsgExtra(extra);
                    }
                    ChatConversationMsg msg = new ChatConversationMsg(uiConversationMsg);
                    uiList.add(msg);
                    spaceConversationKeys.add(key);
                }
                // Fix C：冷启动 sync 后确保 botfather 等系统 Bot 本地可见（SYSTEM_BOTS 兜底）
                ensureSystemBotsVisible(uiList);
                sortMsg(uiList);
                setAllCount();
                if (BuildConfig.DEBUG) {
                    Trace.endSection();
                    Log.d("YUJ312", "onRefreshList-rebuild done inputSize=" + yuj312T8InputSize
                            + " outputSize=" + uiList.size()
                            + " +" + (SystemClock.elapsedRealtime() - yuj312T8Start) + "ms");
                }
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
                    //  Fix B（对齐 iOS PR#95 Defense-in-Depth）：
                    // 不再以 `spaceConversationKeys.contains(key)` 作为短路放行条件。
                    // 白名单可能被冷启动 race / Fix A 前历史污染留下残留 entry，信任其
                    // 短路会让来自其他 Space 的新消息错挂当前 Space。始终过一遍 filter，
                    // 由 SpaceFilter 自身的 fail-open 兜底保证非 Space 模式 / race 窗口不误杀。
                    boolean reject;
                    if (uiConversationMsg.channelType == WKChannelType.GROUP) {
                        reject = !isChannelInCurrentSpace(uiConversationMsg.channelID, uiConversationMsg.channelType);
                    } else {
                        // 私聊：消息 payload space_id；系统 Bot 跨 Space 共享，不做 channel 级剔除
                        reject = isMessageFromOtherSpace(uiConversationMsg.getWkMsg())
                                && !com.chat.base.space.SystemBotsFallback.isSystemBot(uiConversationMsg.channelID);
                    }
                    if (BuildConfig.DEBUG) {
                        android.util.Log.d("ConvSync", "[ConvSync] incremental isAdd: "
                                + uiConversationMsg.channelID + ":" + uiConversationMsg.channelType
                                + " reject=" + reject);
                    }
                    if (reject) {
                        // 同步清理白名单残留（对齐 iOS Skip 清残留 pattern）
                        spaceConversationKeys.remove(key);
                        continue;
                    }
                    uiList.add(new ChatConversationMsg(uiConversationMsg));
                    spaceConversationKeys.add(key);
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
                    //  · 连接成功补偿路径也走 coordinator。同路径 debounce 避免
                    // 连接抖动反复触发；全局守卫避免与 performSpaceSwitch / resync 并发。
                    if (SpaceSyncCoordinator.getInstance().tryBegin("connectSuccessCompensate")) {
                        Schedulers.io().scheduleDirect(() -> {
                            WKIM.getInstance().getConversationManager().clearAll();
                            // setSyncConversationListener 内部有 DB 查询，必须在 IO 线程执行，
                            // 放主线程会和 sync 写入争抢数据库锁导致 ANR
                            WKIM.getInstance().getConversationManager().setSyncConversationListener(result -> {
                                SpaceSyncCoordinator.getInstance().complete();
                            });
                        });
                    }
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
        // 但必须确认网络可用，否则断网冷启动会错误显示在线状态
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        android.net.NetworkInfo netInfo = cm != null ? cm.getActiveNetworkInfo() : null;
        boolean networkAvailable = netInfo != null && netInfo.isConnected();
        if (networkAvailable) {
            if (connectedAtMs == 0) {
                connectedAtMs = System.currentTimeMillis();
            }
            setSpaceSwitcherText(getDisplayTitle());
            wkVBinding.spaceChevronIv.setVisibility(View.VISIBLE);
            startPingTimer();
        } else {
            // 断网冷启动：立即显示网络异常状态，并注册网络恢复监听
            wkVBinding.textSwitcher.setText(getString(R.string.network_error_tips));
            wkVBinding.spaceChevronIv.setVisibility(View.GONE);
            wkVBinding.signalLayout.setVisibility(View.GONE);
            registerNetworkRecoveryCallback();
        }
        EndpointManager.getInstance().setMethod("chat_fragment_exit_chat", EndpointCategory.wkExitChat, object -> {
            if (object != null) {
                WKChannel channel = (WKChannel) object;
                //  · 从 allConversations + conversationIndex 同步移除
                removeConversationByKey(channelKey(channel.channelID, channel.channelType));
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
        FollowedKeysStore.getInstance().addListener(followedKeysChangeListener);
        FollowedKeysStore.getInstance().reload();
    }

    private final FollowedKeysStore.IFollowedKeysChangeListener followedKeysChangeListener = () -> {
        if (getActivity() == null || !isAdded()) return;
        AndroidUtilities.runOnUIThread(() -> filterAndDisplay());
    };

    private void getData() {
        getChatMsg();
    }


    private void getChatMsg() {
        String currentSpaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (!TextUtils.isEmpty(currentSpaceId)) {
            // 从本地 DB 加载全量会话 → Space 过滤在 sortMsg 内完成 → 立即显示。
            // 后台增量 sync 由 WKConnection 连接成功后自动触发，
            // 通过 onRefreshMsgListListener 回调增量更新列表。
            WKIM.getInstance().getConversationManager().getAll(list -> {
                List<ChatConversationMsg> tempList = new ArrayList<>();
                List<ChatConversationMsg> threadList = new ArrayList<>();
                Set<String> keys = new HashSet<>();
                if (WKReader.isNotEmpty(list)) {
                    for (WKUIConversationMsg conv : list) {
                        if (conv.channelType == WKChannelType.COMMUNITY_TOPIC) {
                            int sep = conv.channelID.indexOf("____");
                            if (sep > 0) {
                                String parentGroupNo = conv.channelID.substring(0, sep);
                                if (!isChannelInCurrentSpace(parentGroupNo, WKChannelType.GROUP)) continue;
                            }
                            ChatConversationMsg threadMsg = new ChatConversationMsg(conv);
                            if (sep > 0) {
                                threadMsg.threadParentGroupNo = conv.channelID.substring(0, sep);
                            }
                            threadList.add(threadMsg);
                            continue;
                        }
                        if (conv.channelType == WKChannelType.PERSONAL
                                && isMessageFromOtherSpace(conv.getWkMsg())
                                && !com.chat.base.space.SystemBotsFallback.isSystemBot(conv.channelID)) {
                            continue;
                        }
                        tempList.add(new ChatConversationMsg(conv));
                        keys.add(channelKey(conv.channelID, conv.channelType));
                    }
                }
                dbQueryCompleted = true;
                AndroidUtilities.runOnUIThread(() -> {
                    allThreadConversations.clear();
                    allThreadConversations.addAll(threadList);
                    spaceConversationKeys.clear();
                    spaceConversationKeys.addAll(keys);
                    syncSpaceKeysToGlobal();
                    sortMsg(tempList);
                });
            });
            return;
        }
        // 无 Space 模式：直接加载本地所有会话
        WKIM.getInstance().getConversationManager().getAll(list -> {
            List<ChatConversationMsg> tempList = new ArrayList<>();
            List<ChatConversationMsg> threadList = new ArrayList<>();
            if (WKReader.isNotEmpty(list)) {
                for (int i = 0, size = list.size(); i < size; i++) {
                    WKUIConversationMsg conv = list.get(i);
                    if (conv.channelType == WKChannelType.COMMUNITY_TOPIC) {
                        ChatConversationMsg threadMsg = new ChatConversationMsg(conv);
                        int sep = conv.channelID.indexOf("____");
                        if (sep > 0) {
                            threadMsg.threadParentGroupNo = conv.channelID.substring(0, sep);
                        }
                        threadList.add(threadMsg);
                        continue;
                    }
                    tempList.add(new ChatConversationMsg(conv));
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                allThreadConversations.clear();
                allThreadConversations.addAll(threadList);
                sortMsg(tempList);
            });
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
        int recentUnread = 0;
        List<ChatConversationMsg> source = allConversations.isEmpty()
                ? chatConversationAdapter.getData() : allConversations;
        for (int i = 0, size = source.size(); i < size; i++) {
            ChatConversationMsg item = source.get(i);
            if (item.isSectionHeader) continue;
            if (item.uiConversationMsg == null) continue;
            if (item.uiConversationMsg.getWkChannel() != null && item.uiConversationMsg.getWkChannel().mute == 1)
                continue;
            byte type = item.uiConversationMsg.channelType;
            if (type == WKChannelType.PERSONAL) {
                recentUnread += item.uiConversationMsg.unreadCount;
            } else if (type == WKChannelType.GROUP) {
                if (!isInactiveGroup(item)) {
                    recentUnread += item.uiConversationMsg.unreadCount;
                }
            } else if (type == WKChannelType.COMMUNITY_TOPIC) {
                recentUnread += item.uiConversationMsg.unreadCount;
            }
        }
        // 子区未读（allThreadConversations 独立于 allConversations）
        long threeDaysAgoForBadge = System.currentTimeMillis() / 1000 - 3L * 24 * 60 * 60;
        for (ChatConversationMsg threadMsg : allThreadConversations) {
            if (threadMsg.uiConversationMsg == null) continue;
            if (threadMsg.uiConversationMsg.getWkChannel() != null && threadMsg.uiConversationMsg.getWkChannel().mute == 1)
                continue;
            long ts = threadMsg.uiConversationMsg.lastMsgTimestamp;
            if (ts <= 0 || ts <= threeDaysAgoForBadge) continue;
            recentUnread += threadMsg.uiConversationMsg.unreadCount;
        }
        // 关注 Tab 未读
        int followUnread = computeFollowUnread();
        if (segmentTabView != null) {
            segmentTabView.setBadge(0, followUnread);
            segmentTabView.setBadge(1, recentUnread);
        }
        if (tabActivity != null) {
            tabActivity.setMsgCount(0);
        }
        updateGroupMentionBadge();
    }

    private int computeFollowUnread() {
        int count = 0;
        FollowedKeysStore store = FollowedKeysStore.getInstance();
        List<ChatConversationMsg> source = allConversations.isEmpty()
                ? chatConversationAdapter.getData() : allConversations;
        for (ChatConversationMsg item : source) {
            if (item.isSectionHeader || item.uiConversationMsg == null) continue;
            if (item.uiConversationMsg.getWkChannel() != null && item.uiConversationMsg.getWkChannel().mute == 1)
                continue;
            String channelID = item.uiConversationMsg.channelID;
            byte channelType = item.uiConversationMsg.channelType;
            boolean isFollowed = false;
            if (channelType == WKChannelType.GROUP) {
                isFollowed = store.isFollowed(SidebarItemEntity.TARGET_TYPE_CHANNEL, channelID);
            } else if (channelType == WKChannelType.PERSONAL) {
                isFollowed = store.isFollowed(SidebarItemEntity.TARGET_TYPE_DM, channelID);
            }
            if (isFollowed) {
                count += item.uiConversationMsg.unreadCount;
            }
        }
        // 关注的子区未读
        long threeDaysAgoForFollow = System.currentTimeMillis() / 1000 - 3L * 24 * 60 * 60;
        for (ChatConversationMsg threadMsg : allThreadConversations) {
            if (threadMsg.uiConversationMsg == null) continue;
            if (threadMsg.uiConversationMsg.getWkChannel() != null && threadMsg.uiConversationMsg.getWkChannel().mute == 1)
                continue;
            long ts = threadMsg.uiConversationMsg.lastMsgTimestamp;
            if (ts <= 0 || ts <= threeDaysAgoForFollow) continue;
            String channelID = threadMsg.uiConversationMsg.channelID;
            if (store.isFollowed(SidebarItemEntity.TARGET_TYPE_THREAD, channelID)) {
                count += threadMsg.uiConversationMsg.unreadCount;
            }
        }
        return count;
    }

    private void updateGroupMentionBadge() {
        if (segmentTabView == null || !isAdded()) return;
        FollowedKeysStore store = FollowedKeysStore.getInstance();
        boolean hasMention = false;
        List<ChatConversationMsg> source = allConversations.isEmpty()
                ? chatConversationAdapter.getData() : allConversations;
        for (ChatConversationMsg item : source) {
            if (item.isSectionHeader || item.uiConversationMsg == null) continue;
            String channelID = item.uiConversationMsg.channelID;
            byte channelType = item.uiConversationMsg.channelType;
            boolean isFollowed = false;
            if (channelType == WKChannelType.GROUP) {
                isFollowed = store.isFollowed(SidebarItemEntity.TARGET_TYPE_CHANNEL, channelID);
            } else if (channelType == WKChannelType.PERSONAL) {
                isFollowed = store.isFollowed(SidebarItemEntity.TARGET_TYPE_DM, channelID);
            } else if (channelType == WKChannelType.COMMUNITY_TOPIC) {
                isFollowed = store.isFollowed(SidebarItemEntity.TARGET_TYPE_THREAD, channelID);
            }
            if (!isFollowed) continue;

            List<WKReminder> reminders = item.getReminders();
            if (WKReader.isNotEmpty(reminders)) {
                for (WKReminder r : reminders) {
                    if (r.type == WKMentionType.WKReminderTypeMentionMe && r.done == 0) {
                        hasMention = true;
                        break;
                    }
                }
            }
            if (!hasMention && channelType == WKChannelType.GROUP) {
                hasMention = ReminderDBManager.getInstance().hasUndoneReminderWithChannelPrefix(
                        channelID, WKMentionType.WKReminderTypeMentionMe);
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
            if (parsed != null && !isChannelInCurrentSpace(parsed[0], WKChannelType.GROUP)) {
                return;
            }
            upsertThreadConversation(uiConversationMsg);
            if (parsed != null) {
                followAdapter.refreshThreadPreviews(parsed[0]);
            }
            if (isEnd) {
                filterAndDisplay();
                setAllCount();
            }
            return;
        }
        // 群聊收到子区创建系统消息时，刷新子区预览
        if (uiConversationMsg.channelType == WKChannelType.GROUP
                && uiConversationMsg.getWkMsg() != null
                && uiConversationMsg.getWkMsg().type == WKContentType.threadCreated) {
            followAdapter.refreshThreadPreviews(uiConversationMsg.channelID);
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

        // -B · Layer 4½ gate（push 路径单条更新的跨 Space 兜底）。
        //
        // 批量路径（L693）已有 isMessageFromOtherSpace 检查，但单条路径 L1193 第一个
        // 循环直接裸更新 allConversations（共享对象 → adapter 同步被污染 → 随后的
        // sortMsg 按污染后 timestamp 冒顶）。resetData 被 size==1 分支直接调用，是
        // "其它 Space 群/DM/子区新消息串到当前 Space" 的唯一入口。
        //
        // 这里在写入 allConversations 之前做一次前置 gate，命中即直接 return：
        //   - 不 bump lastMsgTimestamp / unreadCount
        //   - 不加入 adapter，不排序
        //   - 不改 SpaceFilter 纯函数；不触碰 WKSDK 持久化
        //   - 与批量路径 L693 / 新增路径 L1313 语义对齐（含 COMMUNITY_TOPIC 新分支）
        //
        // 严格按任务要求：gate 命中 → return，不写 allConversations、不 bump、不 sort。
        if (isCrossSpaceRealtimePush(uiConversationMsg)) {
            if (BuildConfig.DEBUG) Log.d("SpaceDebug", "[resetData] BLOCKED crossSpace ch=" + uiConversationMsg.channelID
                    + " type=" + uiConversationMsg.channelType);
            return;
        }
        if (BuildConfig.DEBUG) Log.d("SpaceDebug", "[resetData] PASS ch=" + uiConversationMsg.channelID
                + " type=" + uiConversationMsg.channelType
                + " currentSpace=" + MsgModel.getInstance().getCurrentSpaceId());

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
            //  Fix B（对齐 iOS PR#95 Defense-in-Depth）：
            // 不再以 `spaceConversationKeys.contains(key)` 作为短路条件——新消息路径始终过 filter。
            // 白名单可能被冷启动 race / Fix A 前历史污染留下残留 entry；信任短路会让
            // 来自其他 Space 的新消息错挂当前 Space（/209/215 同源 bug）。
            // SpaceFilter 自身带 fail-open 兜底，非 Space 模式 / race 窗口不会误杀。
            if (uiConversationMsg.channelType == WKChannelType.GROUP) {
                if (!isChannelInCurrentSpace(uiConversationMsg.channelID, uiConversationMsg.channelType)) {
                    // 对齐 iOS Skip 清残留 pattern：白名单同步剔除，避免 sort/refresh 重新浮现
                    spaceConversationKeys.remove(key);
                    syncSpaceKeysToGlobal();
                    return;
                }
            } else {
                // 私聊：用消息 payload 中的 space_id 判断；系统 Bot（botfather）跨 Space 共享
                if (isMessageFromOtherSpace(uiConversationMsg.getWkMsg())
                        && !com.chat.base.space.SystemBotsFallback.isSystemBot(uiConversationMsg.channelID)) {
                    spaceConversationKeys.remove(key);
                    syncSpaceKeysToGlobal();
                    return;
                }
            }
            spaceConversationKeys.add(key);
            syncSpaceKeysToGlobal();
            isSort = true;
            ChatConversationMsg newMsg = new ChatConversationMsg(uiConversationMsg);
            //  · key-based 去重：若已有同 (channelID, channelType) 的
            // allConversations entry（比如前面 L687 批量路径 / L1218 本方法前置循环
            // 都漏 match，但索引里还有），upsert 直接返回现有 entry 不再插入重复。
            // 对齐 iOS channelIndex 语义。
            ChatConversationMsg inserted = upsertConversation(newMsg);
            if (inserted != newMsg) {
                // 索引命中（防御分支，正常 flow 应该在 L1218 / L1266 循环里已被 match）：
                // 不再把 newMsg 塞进 adapter，否则会出现「UI 里有 newMsg，但 allConversations
                // 持有另一个 existing 引用」的双飞实例。直接走整页刷新，让 filterAndDisplay
                // 从 allConversations（里面是 existing）重建 adapter 数据。
                if (matchesCurrentTab) {
                    filterAndDisplay();
                }
                setAllCount();
            } else {
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
     * <p>EP3 · ：委托给 {@link com.chat.base.space.SpaceFilter#shouldSkipMessageForSpace(WKMsg)}
     * 以便与 web shouldSkipMessageForSpace 判定对齐。
     */
    private boolean isMessageFromOtherSpace(WKMsg msg) {
        return com.chat.base.space.SpaceFilter.shouldSkipMessageForSpace(msg);
    }

    /**
     * 判断频道是否属于当前 Space。
     * 用于群聊过滤：群消息 payload 不含 space_id，无法用 isMessageFromOtherSpace 判断。
     *
     * <p>EP3 · ：委托给 {@link com.chat.base.space.SpaceFilter#shouldSkipChannelForSpace(String, byte)}
     * 实现 web 双路径判定（含外部群兜底：我以当前 Space 身份加入外部群时放行）。
     */
    private boolean isChannelInCurrentSpace(String channelID, byte channelType) {
        return !com.chat.base.space.SpaceFilter.shouldSkipChannelForSpace(channelID, channelType);
    }

    // 系统 Bot（如 BotFather / u_10000 / fileHelper）：跨 Space 共享，白名单由
    // appconfig system_bot_uids 下发（-A3），客户端统一走
    // {@link com.chat.base.space.SystemBotsFallback#getSystemBotIds()} /
    // {@link com.chat.base.space.SystemBotsFallback#isSystemBot(String)}，消除三端硬编码漂移。

    /**
     * -B · Layer 4½ gate · 判断这条 push 是不是应该被拒绝的跨 Space 污染。
     *
     * <p>与批量路径（L693）/ 新增路径（L1313）的 reject 语义对齐，集中在一处判定，
     * 避免「单条路径漏检 → 冒顶 + 切换后消失」的回归。
     *
     * <p>决策（跨端统一口径）：
     * <ol>
     *     <li>非 Space 模式（currentSpaceId 为空）→ 放行</li>
     *     <li>SystemBot（botfather / u_10000 / fileHelper）→ 委托
     *         {@link #isSystemBotCrossSpaceBump(WKUIConversationMsg, String)}
     *         判定当前 Space 是否需要抑制 bump（conversation entry 跨 Space 共享
     *         是设计，但「Space A 看到 Space B 的 botfather 消息冒顶 + 红点」是 bug）</li>
     *     <li>GROUP → {@code !isChannelInCurrentSpace}</li>
     *     <li>COMMUNITY_TOPIC → 按父群 space 判定，对齐后端 {@code filterThreadConv}；
     *         解析失败 fail-open（由 ThreadModel 后续补救）</li>
     *     <li>PERSONAL → {@code isMessageFromOtherSpace && !isSystemBot}</li>
     * </ol>
     *
     * <p>gate 为 true 时调用方必须 {@code return}：不写 allConversations、不 bump、不 sort。
     */
    private boolean isCrossSpaceRealtimePush(WKUIConversationMsg uc) {
        if (uc == null) return false;
        String currentSpaceId = com.chat.base.space.SpaceFilter.getCurrentSpaceId();
        if (TextUtils.isEmpty(currentSpaceId)) {
            return false;
        }

        // SystemBot 跨 Space 共享 entry，但需要防止别 Space 消息 bump 当前 Space
        if (com.chat.base.space.SystemBotsFallback.isSystemBot(uc.channelID)) {
            boolean reject = isSystemBotCrossSpaceBump(uc, currentSpaceId);
            if (BuildConfig.DEBUG && reject) Log.d("SpaceDebug", "[CrossSpace] REJECT systemBot ch=" + uc.channelID);
            return reject;
        }

        if (uc.channelType == WKChannelType.GROUP) {
            boolean reject = !isChannelInCurrentSpace(uc.channelID, uc.channelType);
            if (BuildConfig.DEBUG) Log.d("SpaceDebug", "[CrossSpace] GROUP ch=" + uc.channelID
                    + " inCurrentSpace=" + !reject + " currentSpaceId=" + currentSpaceId);
            return reject;
        }

        if (uc.channelType == WKChannelType.COMMUNITY_TOPIC) {
            String[] parsed = ThreadModel.getInstance().parseChannelId(uc.channelID);
            if (parsed == null || parsed.length == 0 || TextUtils.isEmpty(parsed[0])) {
                return false;
            }
            boolean reject = !isChannelInCurrentSpace(parsed[0], WKChannelType.GROUP);
            if (BuildConfig.DEBUG && reject) Log.d("SpaceDebug", "[CrossSpace] REJECT thread ch=" + uc.channelID
                    + " parentGroup=" + parsed[0]);
            return reject;
        }

        // PERSONAL（非 SystemBot）
        boolean reject = isMessageFromOtherSpace(uc.getWkMsg())
                && !com.chat.base.space.SystemBotsFallback.isSystemBot(uc.channelID);
        if (BuildConfig.DEBUG && reject) Log.d("SpaceDebug", "[CrossSpace] REJECT personal ch=" + uc.channelID);
        return reject;
    }

    /**
     * -B · 系统 Bot 跨 Space bump 保护。
     *
     * <p>SystemBot 的 conversation entry 跨 Space 共享是<b>设计</b>（否则 botfather
     * 在非默认 Space 下会彻底消失）；但「在 Space A 看到 Space B 的 botfather
     * 消息冒顶 + 红点」是 bug。
     *
     * <p>判定规则（与 ChatActivity#filterSystemBotMessages 的隐藏口径一致）：
     * <ul>
     *     <li>{@code msg.payload.space_id == currentSpaceId} → 允许 bump（return false）</li>
     *     <li>{@code msg.payload.space_id != currentSpaceId} → 污染，抑制 bump（return true）</li>
     *     <li>{@code msg.payload.space_id == null/空}（SystemBot 老消息）→ 视为污染，
     *         对齐 {@code filterSystemBotMessages} 的"系统 Bot 无 space_id 在 Space
     *         模式下隐藏"口径 → return true</li>
     * </ul>
     */
    private boolean isSystemBotCrossSpaceBump(WKUIConversationMsg uc, String currentSpaceId) {
        if (uc == null || TextUtils.isEmpty(currentSpaceId)) {
            return false;
        }
        WKMsg msg = uc.getWkMsg();
        if (msg == null) {
            // 没有 wkMsg（比如 SystemBotsFallback 的占位）→ 无法判定 → 放行
            return false;
        }
        String msgSpaceId = com.chat.base.space.SpaceFilter.extractSpaceIdFromMsg(msg);
        if (TextUtils.isEmpty(msgSpaceId)) {
            // SystemBot 无 space_id 消息 → 视为跨 Space 污染（对齐隐藏口径）
            return true;
        }
        return !currentSpaceId.equals(msgSpaceId);
    }

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
        //  · 同步清 allConversations + conversationIndex
        clearAllConversations();
        allThreadConversations.clear();
        followAdapter.setList(new ArrayList<>());
        recentAdapter.setList(new ArrayList<>());
        //  · resync 也接入 coordinator 去重
        if (!SpaceSyncCoordinator.getInstance().tryBegin("spaceResync")) {
            return;
        }
        Schedulers.io().scheduleDirect(() -> {
            WKIM.getInstance().getConversationManager().clearAll();
            // setSyncConversationListener 内部有 DB 查询，必须在 IO 线程执行，
            // 放主线程会和 sync 写入争抢数据库锁导致 ANR
            WKIM.getInstance().getConversationManager().setSyncConversationListener(result -> {
                SpaceSyncCoordinator.getInstance().complete();
            });
        });
    };

    private void scheduleSpaceResync() {
        if (pendingSpaceResync) return;
        pendingSpaceResync = true;
        pingHandler.postDelayed(spaceResyncRunnable, 500);
    }

    // ============================================================================
    //  · Space 切换 Loading Overlay
    // ============================================================================

    /**
     *  · {@link #showSpaceSwitchOverlay()} 的 5s fallback 文案触发 runnable。
     * sync / 首帧 sortMsg 在 5s 内回来会在 {@link #hideSpaceSwitchOverlay()} 取消这个。
     */
    private final Runnable spaceSwitchOverlayFallbackRunnable = () -> {
        if (wkVBinding == null) return;
        if (wkVBinding.spaceSwitchOverlay.getVisibility() == View.VISIBLE) {
            wkVBinding.spaceSwitchOverlayFallbackTv.setVisibility(View.VISIBLE);
        }
    };

    /**
     * （fixing  ReviewBot P0-#1）· Overlay 10s 硬超时。网络失败 / 后端
     * 不返回 / sync listener 未注册等情况下，原本 {@link #hideSpaceSwitchOverlay()} 永不
     * 触发→ overlay 永久显示 + {@code clickable=true} 阻断所有点击，比不修还糟。
     *
     * <p>这里做兜底：10s 后无论如何强制隐藏，并 toast 提示用户「同步较慢，请稍候」，
     * 同时释放 {@link SpaceSyncCoordinator} 的 permit，避免协调器也一起卡住导致后续切换
     * 无法触发（协调器自己也有 STUCK_RESET_MS=10_000L，时序对齐）。
     */
    private final Runnable spaceSwitchOverlayHardTimeoutRunnable = () -> {
        if (wkVBinding == null) return;
        if (wkVBinding.spaceSwitchOverlay.getVisibility() != View.VISIBLE) return;
        hideSpaceSwitchOverlay();
        // 兜底释放 coordinator permit——正常路径 sync 回调会 complete，但走到 10s
        // 说明 callback 没回来，这里主动放行，避免下次 performSpaceSwitch 也被挡。
        SpaceSyncCoordinator.getInstance().complete();
        try {
            WKToastUtils.getInstance().showToastNormal(
                    getString(R.string.yuj_321_space_switch_timeout_toast));
        } catch (Throwable ignored) {
            // Fragment 已 detach / Context 丢失时 getString 可能抛；静默吞掉
        }
    };

    /**
     *  · 显示 Space 切换 Loading Overlay。
     *
     * <p>只由 {@link #performSpaceSwitch(SpaceEntity)} 调用。其他 sync 路径（连接成功
     * 补偿 / spaceResync / initialSpaceLoad）不显示 overlay，避免后台路径扰动用户视觉。
     *
     * <p>5s 仍未 {@link #hideSpaceSwitchOverlay()} 时显示 fallback 文案，提示用户不是
     * App 卡死，而是网络 / 后端同步慢。
     *
     * <p> Phase 2 · overlay 主文案显示「正在同步「{spaceName}」…」，让用户立刻
     * 知道切换已触发并在同步该 Space；空 spaceName 时降级为不显示主文案，仅 spinner。
     *
     * @param spaceName 目标 Space 名，主文案占位；传 null/空不渲染主文案
     */
    private void showSpaceSwitchOverlay(@Nullable String spaceName) {
        if (wkVBinding == null) return;
        wkVBinding.spaceSwitchOverlay.setVisibility(View.VISIBLE);
        wkVBinding.spaceSwitchOverlayFallbackTv.setVisibility(View.GONE);
        if (wkVBinding.spaceSwitchOverlayPrimaryTv != null) {
            if (TextUtils.isEmpty(spaceName)) {
                wkVBinding.spaceSwitchOverlayPrimaryTv.setVisibility(View.GONE);
            } else {
                wkVBinding.spaceSwitchOverlayPrimaryTv.setText(
                        getString(R.string.yuj_312_space_switch_syncing_fmt, spaceName));
                wkVBinding.spaceSwitchOverlayPrimaryTv.setVisibility(View.VISIBLE);
            }
        }
        if (pingHandler != null) {
            pingHandler.removeCallbacks(spaceSwitchOverlayFallbackRunnable);
            pingHandler.postDelayed(spaceSwitchOverlayFallbackRunnable, 5_000L);
            //  P0-#1 · 10s 硬超时兜底：无论 sync listener 是否触发，10s 后强制
            // hide + toast，避免 overlay 永久卡住 + clickable=true 阻断交互。
            pingHandler.removeCallbacks(spaceSwitchOverlayHardTimeoutRunnable);
            pingHandler.postDelayed(spaceSwitchOverlayHardTimeoutRunnable, 10_000L);
        }
    }

    /**
     *  · 隐藏 Loading Overlay。幂等：重复调用安全。
     *
     * <p>调用点：
     * <ul>
     *   <li>sync 回调（{@code setSyncConversationListener} 完成）</li>
     *   <li>{@link #sortMsg(List)} 首帧 runOnUIThread 内（data 真的回来了）</li>
     *   <li>performSpaceSwitch 因 coordinator debounce 放弃 sync 时的早退路径</li>
     *   <li> · {@link #spaceSwitchOverlayHardTimeoutRunnable} 10s 硬超时兜底</li>
     * </ul>
     */
    private void hideSpaceSwitchOverlay() {
        if (wkVBinding == null) return;
        wkVBinding.spaceSwitchOverlay.setVisibility(View.GONE);
        wkVBinding.spaceSwitchOverlayFallbackTv.setVisibility(View.GONE);
        if (wkVBinding.spaceSwitchOverlayPrimaryTv != null) {
            wkVBinding.spaceSwitchOverlayPrimaryTv.setVisibility(View.GONE);
        }
        if (pingHandler != null) {
            pingHandler.removeCallbacks(spaceSwitchOverlayFallbackRunnable);
            //  · 同时取消 10s 硬超时，避免正常完成后仍弹 toast
            pingHandler.removeCallbacks(spaceSwitchOverlayHardTimeoutRunnable);
        }
    }

    /**
     *  · ChannelInfoCache 批量预热（对照 iOS sync 完成后 `cacheDict` 已填满）。
     *
     * <p>从 sortMsg 即将 display 的 list 中抽出 (channelID, channelType) 组合，一次性
     * 通过 SDK 的 {@code getChannel} 触发 DB → 回填 cache。原 adapter.convert 每行
     * getWkChannel 被改为首次 miss 进 DB 打脸 36 次 × N 行；这里预热后 bind 路径都是
     * O(1) ConcurrentHashMap.get 命中，主线程不再等 IO。
     *
     * <p>线程：调用方保证在 IO 线程（sortMsg 主线程 UI callback 之外，或本方法内部
     * Schedulers.io 包装）。
     */
    private void prewarmChannelInfoCache(@NonNull List<ChatConversationMsg> list) {
        if (list.isEmpty()) return;
        Schedulers.io().scheduleDirect(() -> {
            try {
                for (int i = 0, size = list.size(); i < size; i++) {
                    ChatConversationMsg m = list.get(i);
                    if (m == null || m.isSectionHeader || m.uiConversationMsg == null) continue;
                    String id = m.uiConversationMsg.channelID;
                    if (TextUtils.isEmpty(id)) continue;
                    // 首次调用：SDK 慢路径 DB 查 + 回填 cache。
                    // 后续 adapter bind 调 getWkChannel 都会命中 ConcurrentHashMap fast path。
                    WKIM.getInstance().getChannelManager()
                            .getChannel(id, m.uiConversationMsg.channelType);
                }
            } catch (Throwable t) {
                // 预热失败不影响列表显示，adapter 仍会走原 getWkChannel 路径。
                android.util.Log.w("YUJ318-prewarm", "prewarmChannelInfoCache failed", t);
            }
        });
    }

    /**
     *  Fix C · 本地兜底合成缺失的系统 Bot（botfather）占位会话。
     *
     * <p>背景：botfather 跨 Space 共享，但后端 sync 在某些 Space 下不返回其 conversation
     * entry（时序 / 索引问题），导致 botfather 在该 Space 彻底不显示。
     *
     * <p>硬约束（与 iOS 对齐）：只影响 in-memory 展示，不调 WKSDK 写入 API，不污染
     * 真正的后端同步结果。一旦后端下发真实 entry，后续 RefreshMsgList 路径会用真实
     * 数据覆盖占位（channelID 匹配，时间戳更新）。
     *
     * @param list 当前正要送入 sortMsg/allConversations 的列表；会 <b>in-place</b> 追加缺失的占位项
     */
    private void ensureSystemBotsVisible(@NonNull List<ChatConversationMsg> list) {
        // 收集现有 channelID，供 SystemBotsFallback 判断缺失哪些 Bot
        Set<String> existingIds = new HashSet<>();
        for (ChatConversationMsg m : list) {
            if (m == null || m.uiConversationMsg == null) continue;
            if (m.isSectionHeader) continue;
            if (!TextUtils.isEmpty(m.uiConversationMsg.channelID)) {
                existingIds.add(m.uiConversationMsg.channelID);
            }
        }
        Set<String> missing = com.chat.base.space.SystemBotsFallback.findMissingBotIds(existingIds);
        if (missing.isEmpty()) return;
        for (String botId : missing) {
            WKUIConversationMsg placeholder = com.chat.base.space.SystemBotsFallback.buildPlaceholder(botId);
            list.add(new ChatConversationMsg(placeholder));
            // 白名单同步：让后续新消息路径不再重复判定（botfather 是 SYSTEM_BOTS，本身
            // 也会被 Fix B 的 isSystemBot 分支放行，这里主要是保持状态一致）
            spaceConversationKeys.add(channelKey(botId, WKChannelType.PERSONAL));
        }
    }

    /**
     *  Fix D · 全量兜底清扫：从 {@link #allConversations} 和 {@link #spaceConversationKeys}
     * 中剔除所有不属于当前 Space 的 GROUP 会话。
     *
     * <p>对齐 iOS PR#95 {@code pruneNonCurrentSpaceGroups}：在 Space 切换 / 连接后 sync /
     * DB 回放等状态变化点调用，作为冷启动 race / 白名单污染的最后一道防线。
     *
     * <p>硬约束：不触碰 WKSDK 持久化，只裁 in-memory 数据结构；PERSONAL 不 prune
     * （系统 Bot 跨 Space 共享，由消息级过滤 + SYSTEM_BOTS 兜底处理）。
     *
     * @return 被剔除的会话数量，供诊断 / 日志使用
     */
    private int pruneNonCurrentSpaceGroups() {
        int removed = pruneNonCurrentSpaceGroupsInMemoryOnly();
        if (removed > 0) {
            syncSpaceKeysToGlobal();
            filterAndDisplay();
            setAllCount();
        }
        return removed;
    }

    /**
     * 裸内存版本：只更新 {@link #allConversations} 和 {@link #spaceConversationKeys}，
     * 不触发 UI 刷新。供 {@link #sortMsg(List)} 在进 adapter 之前做一次最终一致性兜底用。
     */
    private int pruneNonCurrentSpaceGroupsInMemoryOnly() {
        String currentSpaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (TextUtils.isEmpty(currentSpaceId)) return 0;
        int removed = 0;
        for (int i = allConversations.size() - 1; i >= 0; i--) {
            ChatConversationMsg msg = allConversations.get(i);
            if (msg == null || msg.uiConversationMsg == null || msg.isSectionHeader) continue;
            String cid = msg.uiConversationMsg.channelID;
            byte ct = msg.uiConversationMsg.channelType;
            if (com.chat.base.space.SpaceConversationPruner.shouldPrune(cid, ct)) {
                //  · 在向后逐个 remove 的循环里直接用 list.remove(i) + index.remove：
                // 不用 removeConversationByKey 的批量清 key 残留语义——那里面自带的
                // 倒序扫列表会和当前循环的 index 重叠，产生 off-by-one。
                // 历史污染留下的多条同 key duplicate 交给 sortMsg() 结尾的
                // rebuildConversationIndex() 做最终收敛（它会按首次出现保留）。
                allConversations.remove(i);
                conversationIndex.remove(channelKey(cid, ct));
                spaceConversationKeys.remove(channelKey(cid, ct));
                removed++;
            }
        }
        // 清理白名单中 allConversations 里没有但 key 还在的污染项（冷启动 race 残留）
        com.chat.base.space.SpaceConversationPruner.pruneWhitelist(
                spaceConversationKeys, currentSpaceId, SpaceFilter_DefaultProviderAdapter.INSTANCE);
        return removed;
    }

    /**
     * 生产默认 provider 的简易适配器——SpaceFilter 的 DEFAULT_PROVIDER 是 package-private，
     * 这里通过调用其对外封装好的静态 API 重建等价实现，保持 Fragment 不直接依赖内部字段。
     */
    private static final class SpaceFilter_DefaultProviderAdapter
            implements com.chat.base.space.SpaceFilter.ChannelInfoProvider {
        static final SpaceFilter_DefaultProviderAdapter INSTANCE = new SpaceFilter_DefaultProviderAdapter();

        @Override
        public String getChannelSpaceId(String channelID, byte channelType) {
            return com.chat.base.space.SpaceFilter.getChannelSpaceId(channelID, channelType);
        }

        @Override
        public String getMyMembershipSourceSpaceId(String channelID, byte channelType) {
            return com.chat.base.space.SpaceFilter.getMyMembershipSourceSpaceId(channelID, channelType);
        }

        @Override
        public boolean isMyMembershipCached(String channelID, byte channelType) {
            // SpaceFilter 的 DEFAULT_PROVIDER.isMyMembershipCached 没有对外静态方法暴露；
            // 这里复用 getMyMembershipSourceSpaceId 的查询路径：能查到 source_space_id 说明 my row 已缓存。
            // 查不到时退化为 fail-open 语义（与 shouldSkipChannelForSpace 主路径的「my-row 未缓存 fail-open」对齐）。
            try {
                String myUid = com.chat.base.config.WKConfig.getInstance().getUid();
                if (android.text.TextUtils.isEmpty(myUid)) return false;
                com.xinbida.wukongim.entity.WKChannelMember me =
                        com.xinbida.wukongim.WKIM.getInstance().getChannelMembersManager()
                                .getMember(channelID, channelType, myUid);
                return me != null;
            } catch (Throwable ignored) {
                return false;
            }
        }

        @Override
        public String getConvSyncMySourceSpaceId(String channelID, byte channelType) {
            // GH dmwork-android#251 / octo-server PR #154：把 conv sync 预填的
            // my_source_space_id 透传给 SpaceFilter，让 my-row sync 未就绪时也能给出权威判定。
            return com.chat.base.space.SpaceFilter.getConvSyncMySourceSpaceId(channelID, channelType);
        }
    }

    /**
     * 执行 Space 切换（用户手动点 Space 列表 / 跨 Space 加群 Dialog「切换过去」共享路径）。
     *
     * <p>与 iOS 的 serial DB queue 对齐：先立即清空 UI 给用户即时反馈，再把 DB 清理 + sync
     * 放到 IO 线程，避免与主线程的 adapter 刷新争抢 SDK 数据库锁（历史 ANR 源）。
     *
     * <p>对齐 #1068：跨 Space 加群「切换过去」按钮复用这条切换路径，
     * 保证手动切换与加群后切换的 UI/数据行为完全一致。
     */
    private void performSpaceSwitch(@NotNull SpaceEntity space) {
        if (space == null || space.space_id == null) return;
        //  Phase 2 · 埋点：从方法入口开始；所有 beginSection 对应 endSection 在方法末尾。
        // 守 BuildConfig.DEBUG 确保 release 零开销。Trace 对应 Android Studio / Perfetto / systrace
        // 工具中 "YUJ312-" 前缀高亮段，便于真机抓图对齐 Issue 中 T1–T10 调用链表格。
        final long yuj312StartMs = BuildConfig.DEBUG ? SystemClock.elapsedRealtime() : 0L;
        if (BuildConfig.DEBUG) {
            Trace.beginSection("YUJ312-performSpaceSwitch");
            Log.d("YUJ312", "performSpaceSwitch START space_id=" + space.space_id
                    + " name=" + space.name);
        }
        //  · 切 Space 清掉选中态（Space 维度不同，旧选中 channel 可能已不在新列表）。
        // 必须在调用链最顶层做，避免 adapter.setList(empty) 之后再清（先后顺序不重要但一次调用即可）。
        if (chatConversationAdapter != null) {
            chatConversationAdapter.clearSelected();
        }

        currentSpaceName = space.name;
        MsgModel.getInstance().setCurrentSpaceId(space.space_id, space.name);
        setSpaceSwitcherText(space.name);
        updateSpaceAvatar(space.name);

        SpaceModel.getInstance().invalidateMembersCache();
        CategoryModel.getInstance().invalidateCache();
        categoryList = new ArrayList<>();
        FollowedKeysStore.getInstance().reset();
        loadCategories();
        FollowedKeysStore.getInstance().reload();

        // （fixing  ReviewBot P1-#2）· 顺序修复：coordinator.tryBegin()
        // 必须在 clearAllConversations() 之前。历史 bug：原实现先清 UI 再 check，快速
        // A→B→A 时 B→A 被 debounce 但 UI 已清空，数据再也填不回来。
        // 放行规则：
        //   - tryBegin 通过 → 下面按原顺序清 UI / 清 DB / resync
        //   - tryBegin 被 debounce → 直接 return，保留已有 UI 列表（Space
        //     name/avatar 等 UI 反馈已在上面更新，用户知道点击生效；数据不清就不会
        //     出现「切过去空白 5 秒」）
        if (!SpaceSyncCoordinator.getInstance().tryBegin("performSpaceSwitch")) {
            if (BuildConfig.DEBUG) {
                Log.d("YUJ312", "performSpaceSwitch END debounced +"
                        + (SystemClock.elapsedRealtime() - yuj312StartMs) + "ms");
                Trace.endSection();
            }
            return;
        }

        //  Phase 2 · T3 埋点：主线程清 UI（clear memory + setList(empty)）。
        // review 2026-05-04 · 统一 DEBUG 守卫样式：每个 section 的 begin + end
        // 都各自用 `if (BuildConfig.DEBUG) { ... }` 包裹，避免原先 T3→T3b 的链式
        // «前段 endSection + 下段 beginSection 塞在同一个 DEBUG 块» 结构。
        long yuj312T3Start = BuildConfig.DEBUG ? SystemClock.elapsedRealtime() : 0L;
        if (BuildConfig.DEBUG) {
            Trace.beginSection("YUJ312-clearAllConversations");
        }
        spaceConversationKeys.clear();
        //  · 同步清 allConversations + conversationIndex
        clearAllConversations();
        allThreadConversations.clear();
        if (BuildConfig.DEBUG) {
            Trace.endSection();
            Log.d("YUJ312", "clearAllConversations done +"
                    + (SystemClock.elapsedRealtime() - yuj312T3Start) + "ms");
        }

        long yuj312T3bStart = BuildConfig.DEBUG ? SystemClock.elapsedRealtime() : 0L;
        if (BuildConfig.DEBUG) {
            Trace.beginSection("YUJ312-adapter-setList-empty");
        }
        followAdapter.setList(new ArrayList<>());
        recentAdapter.setList(new ArrayList<>());
        if (BuildConfig.DEBUG) {
            Trace.endSection();
            Log.d("YUJ312", "adapter.setList(empty) done +"
                    + (SystemClock.elapsedRealtime() - yuj312T3bStart) + "ms");
        }

        //  · 切 Space 立刻显示 loading overlay，避免用户看到空列表 + 「收取中」一直转
        // 搞不清是否点击生效。sync 完成 / 首帧 sortMsg 会调 hideSpaceSwitchOverlay 隐藏；
        // 10s 硬超时兜底见 spaceSwitchOverlayHardTimeoutRunnable（ P0-#1）。
        //  Phase 2 · 传入 space.name 渲染主文案「正在同步「xxx」…」（Yu 授权文案）。
        showSpaceSwitchOverlay(space.name);

        Schedulers.io().scheduleDirect(() -> {
            //  Phase 2 · T4 埋点：IO 线程 WKIM.clearAll（DB DELETE FROM conversation）。
            // section 名称包含 "WKIM-clearAll"，与 SDK 侧 saveSyncChat 区分。
            // 注意：beginSection + endSection 都在同一个 IO 线程内闭合（非跨线程），
            // per-thread stack 配对正常——不存在 syncChat 的跨线程失效问题。
            long yuj312T4Start = BuildConfig.DEBUG ? SystemClock.elapsedRealtime() : 0L;
            if (BuildConfig.DEBUG) {
                Trace.beginSection("YUJ312-WKIM-clearAll");
            }
            WKIM.getInstance().getConversationManager().clearAll();
            if (BuildConfig.DEBUG) {
                Trace.endSection();
                Log.d("YUJ312", "WKIM.clearAll done +"
                        + (SystemClock.elapsedRealtime() - yuj312T4Start) + "ms");
            }
            WKIM.getInstance().getConversationManager().setSyncConversationListener(result -> {
                //  · sync 完成回调：释放 coordinator permit + 隐藏 overlay
                if (BuildConfig.DEBUG) {
                    int convCount = (result == null || result.conversations == null)
                            ? 0 : result.conversations.size();
                    Log.d("YUJ312", "sync-listener onBack conversations=" + convCount
                            + " totalElapsed=" + (SystemClock.elapsedRealtime() - yuj312StartMs) + "ms");
                }
                SpaceSyncCoordinator.getInstance().complete();
                AndroidUtilities.runOnUIThread(this::hideSpaceSwitchOverlay);
            });
            new Handler(Looper.getMainLooper()).post(() ->
                    EndpointManager.getInstance().invoke(WKConstants.refreshContacts, null)
            );
        });
        if (BuildConfig.DEBUG) {
            // 注意：T4/T6/T7 发生在异步线程，这里 endSection 只对应「主线程入口段」的同步部分。
            // 真机 trace 中 YUJ312-performSpaceSwitch 段时长 = 同步 UI 清理耗时（T1-T3）。
            Log.d("YUJ312", "performSpaceSwitch RETURN sync-portion +"
                    + (SystemClock.elapsedRealtime() - yuj312StartMs) + "ms");
            Trace.endSection();
        }
    }

    /**
     *  · Fix A：同步 splitMode 并从 {@link WKUIKitApplication#chattingChannelID}
     * 恢复选中态。时机：onResume（正常生命周期）、Space/tab 切换完成后。
     *
     * <p>splitMode 判定：{@link PaneMetrics#maxWidthPx}（device 最大窗口，不受 Embedding
     * pane 拆分影响）≥ 600dp。与 main_split_config.xml 的 splitMinWidthDp=600 对齐，保证
     * 左右两栏同屏时才显示选中态，普通手机 portrait / 折叠关闭都不显示。
     *
     * <p>历史坑（ P0-1）：初版用 {@code PaneMetrics.widthPx}（pane 宽度），在
     * Embedding 下 primary pane 约 336-480dp，恒 {@code < 600dp}，{@code splitMode} 永远
     * false，选中背景永不渲染。此版本改用 device 整机宽度。
     */
    private void syncSplitModeAndSelection() {
        if (chatConversationAdapter == null || getContext() == null) return;
        boolean split = isSplitModeNow();
        followAdapter.setSplitMode(split);
        recentAdapter.setSplitMode(split);
        if (!split) return;
        String chattingId = WKUIKitApplication.getInstance().chattingChannelID;
        if (TextUtils.isEmpty(chattingId)) {
            followAdapter.clearSelected();
            recentAdapter.clearSelected();
            return;
        }
        // 子区选中 → 只可能在群聊 adapter
        if (chattingId.contains("____")) {
            followAdapter.setSelectedThread(chattingId);
            recentAdapter.clearSelected();
        } else {
            byte type = WKChannelType.PERSONAL;
            WKChannel ch = WKIM.getInstance().getChannelManager().getChannel(chattingId, WKChannelType.GROUP);
            if (ch != null) {
                type = WKChannelType.GROUP;
            } else {
                WKChannel ch2 = WKIM.getInstance().getChannelManager().getChannel(chattingId, WKChannelType.COMMUNITY);
                if (ch2 != null) type = WKChannelType.COMMUNITY;
            }
            // 根据频道类型路由到正确的 adapter
            if (type == WKChannelType.GROUP || type == WKChannelType.COMMUNITY) {
                followAdapter.setSelected(chattingId, type);
                recentAdapter.clearSelected();
            } else {
                recentAdapter.setSelected(chattingId, type);
                followAdapter.clearSelected();
            }
        }
    }

    /**
     * 判断当前是否处于分屏态（设备最大窗口宽度 ≥ 600dp）。
     *
     * <p><b>为什么用 {@link PaneMetrics#maxWidthPx}？</b>（ P0-1）
     * Activity Embedding 激活后 {@link PaneMetrics#widthPx} 返回 <b>primary pane</b> 宽度（
     * 左栏），约 336-480dp（Fold5 884dp × splitRatio 0.4 ≈ 354dp；1200dp 平板 ≈ 480dp）。
     * 与 {@code dp(600)} 比永远 false → {@code splitMode=false} → 选中背景永不渲染。
     *
     * <p>这里需要的是 <b>device / display 最大窗口</b>（由 {@code main_split_config.xml}
     * 的 {@code splitMinWidthDp="600"} 语义决定），用 {@code computeMaximumWindowMetrics}
     * 读整机可用宽度，与 Embedding 库自身的分屏门槛对齐。
     */
    private boolean isSplitModeNow() {
        Context ctx = getContext();
        if (ctx == null) return false;
        int maxWidthPx = PaneMetrics.maxWidthPx(ctx);
        int dp600 = AndroidUtilities.dp(600);
        boolean split = maxWidthPx >= dp600;
        //  P0-1 · 真机 smoke 证据日志：PR description 需贴这条 logcat 输出，
        // 证明分屏展开态下 maxWidthPx ≥ dp600（grep 关键词：YUJ270-splitMode）。
        android.util.Log.d("YUJ270-splitMode",
                "isSplitModeNow maxWidthPx=" + maxWidthPx + " dp600=" + dp600 + " split=" + split);
        return split;
    }

    /**
     * 消费「加群成功」通知（，对齐 #1068）。
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
        //  · 列表组装好就异步批量预热 ChannelInfoCache，避免后面 adapter.convert
        // 每行首次 bind 时到主线程 DB 查询（H3 根因）。
        prewarmChannelInfoCache(tempList);
        AndroidUtilities.runOnUIThread(() -> {
            //  Phase 2 · T10 埋点：首帧最终 adapter rebuild（含 filterAndDisplay / setAllCount）。
            // 这是用户感知 Space 切换「完成」的关键节点；真机 trace 中 YUJ312-adapter-setList-final
            // 段 end 到 hideSpaceSwitchOverlay 之间的距离 ≈ 首帧 render 延迟。
            long yuj312T10Start = BuildConfig.DEBUG ? SystemClock.elapsedRealtime() : 0L;
            int yuj312T10Size = BuildConfig.DEBUG ? tempList.size() : 0;
            if (BuildConfig.DEBUG) {
                Trace.beginSection("YUJ312-adapter-setList-final");
            }
            //  · 先清 allConversations + conversationIndex，再 addAll 写入，
            // 最后重建索引 —— 批量替换路径统一口径，保证索引不漏新 entry 也不留陈旧残留。
            clearAllConversations();
            allConversations.addAll(tempList);
            //  Fix D · 状态变化回扫：每次进 allConversations 前扫一遍，剔除
            // 不属于当前 Space 的 GROUP 会话。对齐 iOS PR#95 pruneNonCurrentSpaceGroups。
            // O(n) 开销，n 通常 < 100；但保证 Fix A/B 留下的 edge case（比如 resetData
            // 单 msg 路径在白名单被污染时仍可能写入 allConversations）有最终一致性兜底。
            pruneNonCurrentSpaceGroupsInMemoryOnly();
            //  Fix C · SYSTEM_BOTS 本地兜底：确保 botfather 等跨 Space 共享 Bot 可见
            ensureSystemBotsVisible(allConversations);
            //  · 在所有对 allConversations 的 mutation 完成之后一次性重建索引，
            // 同时收敛可能的历史 duplicate entry（rebuildIndex 会倒序去重、保留最先出现的）。
            rebuildConversationIndex();
            filterAndDisplay();
            chatConversationAdapter.preloadAllThreadData();
            setAllCount();
            syncSpaceKeysToGlobal();
            // 对齐 iOS：仅首次会话同步完成后调用一次 syncReminder
            if (!hasInitialReminderSynced) {
                hasInitialReminderSynced = true;
                MsgModel.getInstance().syncReminder();
            }
            //  · 首帧 sortMsg 完成：如果 Space 切换 overlay 还在（sync 回调可能
            // 稍后到，或用户侧首次 DB 命中先于 sync），立刻隐藏给用户即时反馈。
            hideSpaceSwitchOverlay();
            if (BuildConfig.DEBUG) {
                Trace.endSection();
                Log.d("YUJ312", "adapter-setList-final done size=" + yuj312T10Size
                        + " +" + (SystemClock.elapsedRealtime() - yuj312T10Start) + "ms");
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
    private int getUnreadCountInCategory(CategoryEntity category, HashMap<String, ChatConversationMsg> channelMap, List<WKConversationMsg> topicConvs) {
        if (category.groups == null) return 0;
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
     * <p>：未分组 section 包含服务端 defaultCategory 的群 + 客户端兜底的
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
     * <p>：未分组 section orphan 合并路径的对应版本。保持与
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
     *
     *  · 外层封装为 50ms debounce：同一 UI 帧内的多次触发（消息到达 + reminder +
     * channel 刷新 + typing/calling 变化）合并为一次 DiffUtil 遍历，避免 ACTION_DOWN
     * 和 ACTION_UP 之间发生多次 notifyDataSetChanged 导致 ViewHolder detach → touch cancel。
     */
    private void filterAndDisplay() {
        filterDebounceHandler.removeCallbacks(filterRunnable);
        filterAndDisplayInternal();
    }

    /**
     * Tab 切换专用：跳过 debounce，立即替换数据并恢复滚动位置。
     */
    private void filterAndDisplayForTabSwitch() {
        // ViewPager2 切换时数据已预填充，无需重新构建列表
    }

    private boolean pendingScrollIdleRefresh = false;
    private final RecyclerView.OnScrollListener scrollIdleWatcher = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE && pendingScrollIdleRefresh) {
                pendingScrollIdleRefresh = false;
                filterAndDisplayInternal();
            }
        }
    };

    private void filterAndDisplayInternal() {
        if (followAdapter == null || getActivity() == null || !isAdded()) return;
        if (!isResumed()) {
            pendingFilterAndDisplay = true;
            return;
        }
        RecyclerView rv = getActiveRecyclerView();
        if (rv != null && rv.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
            pendingScrollIdleRefresh = true;
            return;
        }
        // 同时更新两个 adapter，确保 tab 切换时数据已就绪
        int savedTab = currentTab;
        currentTab = 0;
        List<ChatConversationMsg> followList = buildDisplayListForCurrentTab();
        currentTab = 1;
        List<ChatConversationMsg> recentList = buildDisplayListForCurrentTab();
        currentTab = savedTab;
        followAdapter.setList(followList);
        recentAdapter.setList(recentList);
        updateFollowEmptyState(followList);
        lastFullRefreshTime = System.currentTimeMillis();
        pendingFilterAndDisplay = false;
        pendingScrollIdleRefresh = false;
    }

    private List<ChatConversationMsg> buildDisplayListForCurrentTab() {
        if (currentTab == 0) {
            return buildFollowDisplayList();
        } else {
            return buildRecentDisplayList();
        }
    }

    private List<ChatConversationMsg> buildFollowDisplayList() {
        FollowedKeysStore store = FollowedKeysStore.getInstance();
        Map<String, List<SidebarItemEntity>> itemsByCategory = store.getItemsByCategory();

        HashMap<String, ChatConversationMsg> channelMap = new HashMap<>();
        List<ChatConversationMsg> snapshot = new ArrayList<>(allConversations);
        for (ChatConversationMsg msg : snapshot) {
            if (msg == null || msg.uiConversationMsg == null) continue;
            channelMap.put(msg.uiConversationMsg.channelID, msg);
        }
        long threeDaysAgo = System.currentTimeMillis() / 1000 - 3L * 24 * 60 * 60;
        for (ChatConversationMsg threadMsg : allThreadConversations) {
            if (threadMsg == null || threadMsg.uiConversationMsg == null) continue;
            long ts = threadMsg.uiConversationMsg.lastMsgTimestamp;
            if (ts <= 0 || ts <= threeDaysAgo) continue;
            channelMap.put(threadMsg.uiConversationMsg.channelID, threadMsg);
        }

        List<ChatConversationMsg> displayList = new ArrayList<>();

        List<CategoryEntity> categories = new ArrayList<>(categoryList);
        for (CategoryEntity category : categories) {
            if (category == null || category.is_default) continue;

            List<SidebarItemEntity> sidebarItems = itemsByCategory.get(category.category_id);
            if (sidebarItems == null || sidebarItems.isEmpty()) {
                ChatConversationMsg emptyHeader = new ChatConversationMsg(category.category_id, category.name);
                emptyHeader.sectionGroupCount = 0;
                displayList.add(emptyHeader);
                continue;
            }

            List<ChatConversationMsg> sectionItems = new ArrayList<>();
            for (SidebarItemEntity item : sidebarItems) {
                if (item.target_type == SidebarItemEntity.TARGET_TYPE_THREAD) continue;
                String key = !TextUtils.isEmpty(item.channel_id) ? item.channel_id : item.target_id;
                ChatConversationMsg msg = channelMap.get(key);
                if (msg != null) {
                    sectionItems.add(msg);
                }
            }

            ChatConversationMsg sectionHeader = new ChatConversationMsg(category.category_id, category.name);
            sectionHeader.sectionGroupCount = sectionItems.size();
            sectionHeader.sectionUnreadCount = computeUnreadCountForItems(sectionItems);
            sectionHeader.sectionHasMention = computeHasMentionForItems(sectionItems);
            displayList.add(sectionHeader);

            if (!followAdapter.isSectionCollapsed(category.category_id)) {
                displayList.addAll(sectionItems);
            }
        }

        // 兜底：category_id 为空的已关注项（服务端未分配分组时不丢数据）
        List<SidebarItemEntity> uncategorized = itemsByCategory.get("");
        if (uncategorized != null) {
            for (SidebarItemEntity item : uncategorized) {
                if (item.target_type == SidebarItemEntity.TARGET_TYPE_THREAD) continue;
                String key = !TextUtils.isEmpty(item.channel_id) ? item.channel_id : item.target_id;
                ChatConversationMsg msg = channelMap.get(key);
                if (msg != null) {
                    displayList.add(msg);
                }
            }
        }

        return displayList;
    }

    private static boolean isInactiveGroup(ChatConversationMsg msg) {
        if (msg.uiConversationMsg.channelType != WKChannelType.GROUP) return false;
        long ts = msg.uiConversationMsg.lastMsgTimestamp;
        if (ts <= 0) return true;
        long now = System.currentTimeMillis() / 1000;
        return (now - ts) >= 3 * 86400;
    }

    private List<ChatConversationMsg> buildRecentDisplayList() {
        List<ChatConversationMsg> filtered = new ArrayList<>();
        for (ChatConversationMsg msg : allConversations) {
            if (msg == null || msg.uiConversationMsg == null) continue;
            byte type = msg.uiConversationMsg.channelType;
            if (type == WKChannelType.PERSONAL) {
                filtered.add(msg);
            } else if (type == WKChannelType.GROUP) {
                if (!isInactiveGroup(msg)) {
                    filtered.add(msg);
                }
            } else {
                filtered.add(msg);
            }
        }

        long threeDaysAgoSec = System.currentTimeMillis() / 1000 - 3L * 24 * 60 * 60;
        Map<String, List<com.chat.uikit.thread.service.entity.ThreadEntity>> threadCache =
                followAdapter.getThreadDataCache();
        for (ChatConversationMsg threadMsg : allThreadConversations) {
            if (threadMsg.uiConversationMsg == null) continue;
            long ts = threadMsg.uiConversationMsg.lastMsgTimestamp;
            if (ts <= 0 || ts <= threeDaysAgoSec) continue;

            if (threadMsg.threadName == null || threadMsg.threadName.isEmpty()) {
                String channelId = threadMsg.uiConversationMsg.channelID;
                int sep = channelId.indexOf("____");
                if (sep > 0) {
                    String groupNo = channelId.substring(0, sep);
                    String shortId = channelId.substring(sep + 4);
                    List<com.chat.uikit.thread.service.entity.ThreadEntity> threads = threadCache.get(groupNo);
                    if (threads != null) {
                        for (com.chat.uikit.thread.service.entity.ThreadEntity te : threads) {
                            if (shortId.equals(te.short_id)) {
                                threadMsg.threadName = te.name;
                                break;
                            }
                        }
                    }
                    if (threadMsg.threadName == null || threadMsg.threadName.isEmpty()) {
                        WKChannel ch = WKIM.getInstance().getChannelManager()
                                .getChannel(channelId, WKChannelType.COMMUNITY_TOPIC);
                        if (ch != null && ch.channelName != null && !ch.channelName.isEmpty()) {
                            threadMsg.threadName = ch.channelName;
                        }
                    }
                }
            }
            filtered.add(threadMsg);
        }

        filtered.sort((a, b) -> {
            int topA = (a.uiConversationMsg.getWkChannel() != null && a.uiConversationMsg.getWkChannel().top == 1) ? 1 : 0;
            int topB = (b.uiConversationMsg.getWkChannel() != null && b.uiConversationMsg.getWkChannel().top == 1) ? 1 : 0;
            if (topA != topB) return topB - topA;
            return Long.compare(b.uiConversationMsg.lastMsgTimestamp, a.uiConversationMsg.lastMsgTimestamp);
        });
        return filtered;
    }

    private void upsertThreadConversation(WKUIConversationMsg msg) {
        for (int i = 0; i < allThreadConversations.size(); i++) {
            ChatConversationMsg existing = allThreadConversations.get(i);
            if (existing.uiConversationMsg != null
                    && msg.channelID.equals(existing.uiConversationMsg.channelID)) {
                existing.uiConversationMsg.lastMsgTimestamp = msg.lastMsgTimestamp;
                existing.uiConversationMsg.lastMsgSeq = msg.lastMsgSeq;
                existing.uiConversationMsg.unreadCount = msg.unreadCount;
                existing.uiConversationMsg.clientMsgNo = msg.clientMsgNo;
                existing.uiConversationMsg.setWkMsg(msg.getWkMsg());
                existing.uiConversationMsg.setReminderList(msg.getReminderList());
                return;
            }
        }
        ChatConversationMsg threadMsg = new ChatConversationMsg(msg);
        int sep = msg.channelID.indexOf("____");
        if (sep > 0) {
            threadMsg.threadParentGroupNo = msg.channelID.substring(0, sep);
        }
        allThreadConversations.add(threadMsg);
    }

    private View followEmptyView;

    private void initFollowEmptyView() {
        FrameLayout container = pagerAdapter.getPageContainer(0);
        if (container == null || getContext() == null) return;
        followEmptyView = getLayoutInflater().inflate(R.layout.layout_follow_empty, container, false);
        followEmptyView.setVisibility(View.GONE);
        container.addView(followEmptyView);
        View btn = followEmptyView.findViewById(R.id.btnCreateCategory);
        if (btn != null) {
            btn.setOnClickListener(v -> showCreateCategoryDialog());
        }
    }

    private void updateFollowEmptyState(List<ChatConversationMsg> followList) {
        if (followEmptyView == null) return;
        boolean showEmpty = followList.isEmpty() && FollowedKeysStore.getInstance().isLoaded();
        followEmptyView.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
    }

    private void updateFollowEmptyVisibility() {
        if (followEmptyView == null) return;
        if (currentTab != 0) {
            followEmptyView.setVisibility(View.GONE);
            return;
        }
        boolean showEmpty = followAdapter.getData().isEmpty();
        followEmptyView.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
    }

    private void handleFollowToggle(WKUIConversationMsg item) {
        FollowedKeysStore store = FollowedKeysStore.getInstance();
        int targetType;
        if (item.channelType == WKChannelType.PERSONAL) {
            targetType = SidebarItemEntity.TARGET_TYPE_DM;
        } else if (item.channelType == WKChannelType.COMMUNITY_TOPIC) {
            targetType = SidebarItemEntity.TARGET_TYPE_THREAD;
        } else {
            targetType = SidebarItemEntity.TARGET_TYPE_CHANNEL;
        }
        boolean isFollowed = store.isFollowed(targetType, item.channelID);
        if (isFollowed) {
            performUnfollow(item, targetType);
        } else {
            showAddToFollowDialog(item, targetType);
        }
    }

    private void performUnfollow(WKUIConversationMsg item, int targetType) {
        String displayName = getChannelDisplayName(item.channelID, item.channelType);
        ICommonListener listener = (code, msg) -> {
            if (code == HttpResponseCode.success) {
                WKToastUtils.getInstance().showToastNormal(displayName + " 已取消关注");
            } else {
                WKToastUtils.getInstance().showToastNormal(msg != null ? msg : "取消关注失败");
            }
        };
        if (targetType == SidebarItemEntity.TARGET_TYPE_DM) {
            FollowModel.getInstance().unfollowDM(item.channelID, listener);
        } else if (targetType == SidebarItemEntity.TARGET_TYPE_THREAD) {
            FollowModel.getInstance().unfollowThread(item.channelID, listener);
        } else {
            FollowModel.getInstance().unfollowChannel(item.channelID, listener);
        }
    }

    private void showAddToFollowDialog(WKUIConversationMsg item, int targetType) {
        if (getActivity() == null) return;

        if (targetType == SidebarItemEntity.TARGET_TYPE_THREAD) {
            String[] parsed = ThreadModel.getInstance().parseChannelId(item.channelID);
            if (parsed == null) return;
            String parentGroupNo = parsed[0];
            boolean parentFollowed = FollowedKeysStore.getInstance()
                    .isFollowed(SidebarItemEntity.TARGET_TYPE_CHANNEL, parentGroupNo);
            if (parentFollowed) {
                performFollowThread(item.channelID, parentGroupNo, null, null);
            } else {
                showFollowCategoryPicker(categoryId -> {
                    String categoryName = getCategoryNameById(categoryId);
                    performFollowThread(item.channelID, parentGroupNo, categoryId, categoryName);
                });
            }
            return;
        }

        showFollowCategoryPicker(categoryId -> {
            String categoryName = getCategoryNameById(categoryId);
            if (targetType == SidebarItemEntity.TARGET_TYPE_DM) {
                performFollowDM(item.channelID, categoryId, categoryName);
            } else {
                performFollowGroup(item.channelID, categoryId, categoryName);
            }
        });
    }

    private void performFollowDM(String peerUid, String categoryId, String categoryName) {
        String displayName = getChannelDisplayName(peerUid, WKChannelType.PERSONAL);
        FollowModel.getInstance().followDM(peerUid, categoryId, (code, msg) -> {
            if (code == HttpResponseCode.success) {
                WKToastUtils.getInstance().showToastNormal(displayName + " 已添加到 " + categoryName + " 分组");
            } else {
                WKToastUtils.getInstance().showToastNormal(msg != null ? msg : "添加到关注失败");
            }
        });
    }

    private void performFollowGroup(String groupNo, String categoryId, String categoryName) {
        String displayName = getChannelDisplayName(groupNo, WKChannelType.GROUP);
        FollowModel.getInstance().refollowChannel(groupNo, (code, msg) -> {
            if (code == HttpResponseCode.success) {
                CategoryModel.getInstance().moveGroup(groupNo, categoryId, (code2, msg2) -> {
                    if (code2 == HttpResponseCode.success) {
                        loadCategories();
                        WKToastUtils.getInstance().showToastNormal(displayName + " 已添加到 " + categoryName + " 分组");
                    } else {
                        WKToastUtils.getInstance().showToastNormal(displayName + " 已添加到关注");
                    }
                });
            } else {
                WKToastUtils.getInstance().showToastNormal(msg != null ? msg : "添加到关注失败");
            }
        });
    }

    private void performFollowThread(String threadChannelId, String parentGroupNo, String categoryId, String categoryName) {
        String displayName = getChannelDisplayName(threadChannelId, WKChannelType.COMMUNITY_TOPIC);
        if (categoryId != null) {
            FollowModel.getInstance().refollowChannel(parentGroupNo, (code, msg) -> {
                if (code == HttpResponseCode.success) {
                    CategoryModel.getInstance().moveGroup(parentGroupNo, categoryId, (code2, msg2) -> {
                        FollowModel.getInstance().followThread(threadChannelId, (code3, msg3) -> {
                            if (code3 == HttpResponseCode.success) {
                                loadCategories();
                                String catName = categoryName != null ? categoryName : "";
                                WKToastUtils.getInstance().showToastNormal(displayName + " 已添加到 " + catName + " 分组");
                            } else {
                                WKToastUtils.getInstance().showToastNormal(msg3 != null ? msg3 : "添加到关注失败");
                            }
                        });
                    });
                } else {
                    WKToastUtils.getInstance().showToastNormal(msg != null ? msg : "添加到关注失败");
                }
            });
        } else {
            FollowModel.getInstance().followThread(threadChannelId, (code, msg) -> {
                if (code == HttpResponseCode.success) {
                    WKToastUtils.getInstance().showToastNormal(displayName + " 已添加到关注");
                } else {
                    WKToastUtils.getInstance().showToastNormal(msg != null ? msg : "添加到关注失败");
                }
            });
        }
    }

    private void showFollowCategoryPicker(IFollowCategoryPickListener onPick) {
        if (getActivity() == null) return;
        List<CategoryEntity> userCategories = new ArrayList<>();
        for (CategoryEntity cat : categoryList) {
            if (cat.is_default || cat.category_id == null) continue;
            userCategories.add(cat);
        }
        if (userCategories.isEmpty()) {
            showCreateCategoryDialog();
            return;
        }

        Context ctx = requireContext();
        BottomSheet.Builder builder = new BottomSheet.Builder(ctx, false);
        builder.setApplyBottomPadding(false);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(ctx, R.color.screen_bg));

        TextView titleTv = new TextView(ctx);
        titleTv.setText(R.string.follow_conversation);
        titleTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 17);
        titleTv.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTv.setTextColor(ContextCompat.getColor(ctx, R.color.colorDark));
        titleTv.setGravity(Gravity.CENTER);
        titleTv.setPadding(0, AndroidUtilities.dp(18), 0, AndroidUtilities.dp(14));
        root.addView(titleTv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        View divider = new View(ctx);
        divider.setBackgroundColor(ContextCompat.getColor(ctx, R.color.colorE8E7E7));
        root.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        for (CategoryEntity cat : userCategories) {
            FrameLayout row = new FrameLayout(ctx);
            row.setBackground(ContextCompat.getDrawable(ctx, R.drawable.layout_bg));
            row.setMinimumHeight(AndroidUtilities.dp(52));

            TextView nameTv = new TextView(ctx);
            nameTv.setText(cat.name);
            nameTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
            nameTv.setTextColor(ContextCompat.getColor(ctx, R.color.colorDark));
            FrameLayout.LayoutParams nameParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            nameParams.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
            nameParams.leftMargin = AndroidUtilities.dp(20);
            row.addView(nameTv, nameParams);

            row.setOnClickListener(v -> {
                builder.getDismissRunnable().run();
                onPick.onCategoryPicked(cat.category_id);
            });

            root.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        View div2 = new View(ctx);
        div2.setBackgroundColor(ContextCompat.getColor(ctx, R.color.colorE8E7E7));
        root.addView(div2, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        FrameLayout createRow = new FrameLayout(ctx);
        createRow.setBackground(ContextCompat.getDrawable(ctx, R.drawable.layout_bg));
        createRow.setMinimumHeight(AndroidUtilities.dp(52));
        TextView createTv = new TextView(ctx);
        createTv.setText(R.string.follow_empty_create_btn);
        createTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        createTv.setTextColor(ContextCompat.getColor(ctx, R.color.colorAccent));
        FrameLayout.LayoutParams createParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        createParams.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        createParams.leftMargin = AndroidUtilities.dp(20);
        createRow.addView(createTv, createParams);
        createRow.setOnClickListener(v -> {
            builder.getDismissRunnable().run();
            showCreateCategoryDialog();
        });
        root.addView(createRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        builder.setCustomView(root);
        builder.show();
    }

    private interface IFollowCategoryPickListener {
        void onCategoryPicked(String categoryId);
    }

    private String getCategoryNameById(String categoryId) {
        if (categoryId == null) return "";
        for (CategoryEntity cat : categoryList) {
            if (categoryId.equals(cat.category_id)) {
                return cat.name != null ? cat.name : "";
            }
        }
        return "";
    }

    private String getChannelDisplayName(String channelId, byte channelType) {
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);
        if (channel != null && channel.channelName != null && !channel.channelName.isEmpty()) {
            return channel.channelName;
        }
        return channelId;
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

        // 分组内排列顺序
        items.add(new PopupMenuItem(getString(R.string.reorder_items), R.mipmap.msg_reorder, () -> {
            showReorderItemsSheet(sectionId, sectionTitle);
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
                                    FollowedKeysStore.getInstance().reload();
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

    private void showReorderItemsSheet(String categoryId, String categoryTitle) {
        if (getActivity() == null) return;

        FollowedKeysStore store = FollowedKeysStore.getInstance();
        List<SidebarItemEntity> sidebarItems = store.getItemsByCategory().get(categoryId);
        if (sidebarItems == null || sidebarItems.isEmpty()) return;

        List<SidebarItemEntity> reorderList = new ArrayList<>(sidebarItems);

        Context ctx = requireContext();
        BottomSheet.Builder builder = new BottomSheet.Builder(ctx, false);
        builder.setApplyBottomPadding(false);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(ctx, R.color.screen_bg));

        FrameLayout header = new FrameLayout(ctx);
        header.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16),
                AndroidUtilities.dp(20), AndroidUtilities.dp(14));

        TextView titleTv = new TextView(ctx);
        titleTv.setText(categoryTitle);
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

        View divider = new View(ctx);
        divider.setBackgroundColor(ContextCompat.getColor(ctx, R.color.colorE8E7E7));
        root.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        RecyclerView recyclerView = new RecyclerView(ctx);
        recyclerView.setLayoutManager(new LinearLayoutManager(ctx));

        final androidx.recyclerview.widget.ItemTouchHelper[] itemTouchHelper = new androidx.recyclerview.widget.ItemTouchHelper[1];

        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new RecyclerView.Adapter<>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                FrameLayout row = new FrameLayout(ctx);
                row.setBackground(ContextCompat.getDrawable(ctx, R.drawable.layout_bg));
                row.setMinimumHeight(AndroidUtilities.dp(52));
                row.setLayoutParams(new RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));

                TextView nameTv = new TextView(ctx);
                nameTv.setTag("name");
                nameTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
                nameTv.setTextColor(ContextCompat.getColor(ctx, R.color.colorDark));
                nameTv.setSingleLine();
                nameTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                FrameLayout.LayoutParams nameParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                nameParams.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
                nameParams.leftMargin = AndroidUtilities.dp(20);
                nameParams.rightMargin = AndroidUtilities.dp(56);
                row.addView(nameTv, nameParams);

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

                View rowDivider = new View(ctx);
                rowDivider.setBackgroundColor(ContextCompat.getColor(ctx, R.color.colorE8E7E7));
                FrameLayout.LayoutParams divParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, 1);
                divParams.gravity = Gravity.BOTTOM;
                divParams.leftMargin = AndroidUtilities.dp(20);
                row.addView(rowDivider, divParams);

                return new RecyclerView.ViewHolder(row) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                SidebarItemEntity item = reorderList.get(position);
                TextView nameTv = holder.itemView.findViewWithTag("name");
                String displayName = getItemDisplayName(item);
                nameTv.setText(displayName);

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

        View bottomPad = new View(ctx);
        root.addView(bottomPad, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(8)));

        builder.setCustomView(root);
        BottomSheet sheet = builder.show();
        sheet.setBackgroundColor(ContextCompat.getColor(ctx, R.color.screen_bg));

        doneTv.setOnClickListener(v -> {
            List<FollowModel.FollowSortItem> sortItems = new ArrayList<>();
            for (int i = 0; i < reorderList.size(); i++) {
                SidebarItemEntity item = reorderList.get(i);
                sortItems.add(new FollowModel.FollowSortItem(item.target_type, item.target_id, i));
            }
            int version = FollowedKeysStore.getInstance().getFollowVersion();
            FollowModel.getInstance().sort(sortItems, version, (code, msg) -> {
                if (code == HttpResponseCode.success) {
                    sheet.dismiss();
                } else if (FollowModel.isVersionConflictError(msg)) {
                    WKToastUtils.getInstance().showToastNormal(getString(R.string.reorder_version_conflict));
                    sheet.dismiss();
                } else {
                    WKToastUtils.getInstance().showToastNormal(msg);
                }
            });
        });
    }

    private String getItemDisplayName(SidebarItemEntity item) {
        String key = !TextUtils.isEmpty(item.channel_id) ? item.channel_id : item.target_id;
        if (TextUtils.isEmpty(key)) return "";
        byte channelType = (byte) item.channel_type;
        if (channelType == 0) {
            if (item.target_type == SidebarItemEntity.TARGET_TYPE_DM) channelType = WKChannelType.PERSONAL;
            else if (item.target_type == SidebarItemEntity.TARGET_TYPE_CHANNEL) channelType = WKChannelType.GROUP;
            else if (item.target_type == SidebarItemEntity.TARGET_TYPE_THREAD) channelType = WKChannelType.COMMUNITY_TOPIC;
        }
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(key, channelType);
        if (channel != null && !TextUtils.isEmpty(channel.channelRemark)) return channel.channelRemark;
        if (channel != null && !TextUtils.isEmpty(channel.channelName)) return channel.channelName;
        return key;
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

    private void showMoveToCategoryDialog(String channelId) {
        showMoveToCategoryDialog(channelId, WKChannelType.GROUP);
    }

    private void showMoveToCategoryDialog(String channelId, byte channelType) {
        if (getActivity() == null || categoryList.isEmpty()) {
            WKToastUtils.getInstance().showToastNormal(getString(R.string.create_category_hint));
            return;
        }
        Context ctx = requireContext();

        String currentCategoryId = null;
        if (channelType == WKChannelType.PERSONAL) {
            int targetType = SidebarItemEntity.TARGET_TYPE_DM;
            Map<String, List<SidebarItemEntity>> itemsByCategory = FollowedKeysStore.getInstance().getItemsByCategory();
            for (Map.Entry<String, List<SidebarItemEntity>> entry : itemsByCategory.entrySet()) {
                for (SidebarItemEntity item : entry.getValue()) {
                    if (item.target_type == targetType && channelId.equals(item.target_id)) {
                        currentCategoryId = entry.getKey();
                        break;
                    }
                }
                if (currentCategoryId != null) break;
            }
        } else {
            for (CategoryEntity cat : categoryList) {
                if (cat.category_id != null && cat.groups != null) {
                    for (CategoryEntity.CategoryGroup cg : cat.groups) {
                        if (channelId.equals(cg.group_no)) {
                            currentCategoryId = cat.category_id;
                            break;
                        }
                    }
                }
                if (currentCategoryId != null) break;
            }
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
                if (channelType == WKChannelType.PERSONAL) {
                    FollowModel.getInstance().followDM(channelId, cat.category_id, (code, msg) -> {
                        if (code == HttpResponseCode.success) {
                            loadCategories();
                            FollowedKeysStore.getInstance().reload();
                        } else {
                            WKToastUtils.getInstance().showToastNormal(msg);
                        }
                    });
                } else {
                    CategoryModel.getInstance().moveGroup(channelId, cat.category_id, (code, msg) -> {
                        if (code == HttpResponseCode.success) {
                            loadCategories();
                            FollowedKeysStore.getInstance().reload();
                        } else {
                            WKToastUtils.getInstance().showToastNormal(msg);
                        }
                    });
                }
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
                if (channelType == WKChannelType.PERSONAL) {
                    FollowModel.getInstance().followDM(channelId, null, (code, msg) -> {
                        if (code == HttpResponseCode.success) {
                            loadCategories();
                            FollowedKeysStore.getInstance().reload();
                        } else {
                            WKToastUtils.getInstance().showToastNormal(msg);
                        }
                    });
                } else {
                    CategoryModel.getInstance().moveGroup(channelId, null, (code, msg) -> {
                        if (code == HttpResponseCode.success) {
                            loadCategories();
                            FollowedKeysStore.getInstance().reload();
                        } else {
                            WKToastUtils.getInstance().showToastNormal(msg);
                        }
                    });
                }
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
        unregisterNetworkRecoveryCallback();
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
        EndpointManager.getInstance().remove("chat_fragment_exit_chat");
        EndpointManager.getInstance().remove("chat_cover");
        EndpointManager.getInstance().remove("refresh_conversation_extras");
        EndpointManager.getInstance().remove("refresh_conversation_calling");
        EndpointManager.getInstance().remove("scroll_to_unread_channel");
        pingHandler.removeCallbacks(spaceResyncRunnable);
        //  · 清理 filterAndDisplay debounce handler，防止 Fragment 销毁后 Runnable 回调。
        filterDebounceHandler.removeCallbacks(filterRunnable);
        stopPingTimer();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        filterDebounceHandler.removeCallbacks(filterRunnable);
        FollowedKeysStore.getInstance().removeListener(followedKeysChangeListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            Glide.with(this).resumeRequests();
        } catch (IllegalArgumentException ignored) {
        }
        syncSplitModeAndSelection();
        FollowedKeysStore.getInstance().reload();
        // Fragment 不可见期间有数据变化，返回时延迟到下一帧刷新，不阻塞返回动画
        if (pendingFilterAndDisplay) {
            pendingFilterAndDisplay = false;
            filterDebounceHandler.post(filterRunnable);
        }
        long now = System.currentTimeMillis();
        long elapsed = now - lastFullRefreshTime;
        if (elapsed < RESUME_THROTTLE_MS) {
            // 仅刷新子区未读（轻量，不触发全量 setList），延迟到下一帧避免阻塞返回动画
            filterDebounceHandler.post(() -> {
                chatConversationAdapter.clearAndReloadThreadData();
                refreshExtrasIfNeeded();
            });
            consumeJoinSuccessNoticeIfAny();
            int pcOnline = WKSharedPreferencesUtil.getInstance().getInt(WKConfig.getInstance().getUid() + "_pc_online");
            wkVBinding.deviceLayout.setVisibility(pcOnline == 1 ? View.VISIBLE : View.GONE);
            return;
        }
        // 子区数据缓存清除并重新加载，延迟到下一帧避免阻塞返回动画
        filterDebounceHandler.post(() -> {
            chatConversationAdapter.clearAndReloadThreadData();
            refreshExtrasIfNeeded();
            loadCategories();
        });
        //  · 跨 Space 加群 Toast：消费上个界面（扫码 / 邀请加群）留下的 notice
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

    /**
     *  · 折叠屏回归：非折叠态启动 → 展开时 splitMode 不刷新。
     *
     * <p>TabActivity 声明 {@code configChanges=screenSize|screenLayout|smallestScreenSize}
     * 屏蔽了 unfold 引发的 recreate，所以 {@link #onResume()} 不会再被触发，
     * {@link #syncSplitModeAndSelection()} 停留在初次 portrait 窄屏时的
     * {@code splitMode=false} —— 选中态永远灰、detail pane 不再响应。
     *
     * <p>Fragment 的 {@code onConfigurationChanged} 由 FragmentManager 在
     * Activity 的 {@code super.onConfigurationChanged} 中派发，配合
     * {@link com.chat.uikit.TabActivity#onConfigurationChanged} 的 super
     * 调用即可收到。此处 read-at-use 刷新一次 splitMode + selection 即可修复。
     */
    @Override
    public void onConfigurationChanged(@androidx.annotation.NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        try {
            syncSplitModeAndSelection();
        } catch (Throwable ignored) {
            // 配置变更回调不允许把进程带走
        }
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

    private android.net.ConnectivityManager.NetworkCallback networkRecoveryCallback;

    /** 断网冷启动时注册：网络恢复后触发 SDK 重连，仅触发一次后自动注销 */
    private void registerNetworkRecoveryCallback() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return;
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        networkRecoveryCallback = new android.net.ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull android.net.Network network) {
                AndroidUtilities.runOnUIThread(() -> {
                    WKIM.getInstance().getConnectionManager().connection();
                    unregisterNetworkRecoveryCallback();
                });
            }
        };
        android.net.NetworkRequest request = new android.net.NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        cm.registerNetworkCallback(request, networkRecoveryCallback);
    }

    private void unregisterNetworkRecoveryCallback() {
        if (networkRecoveryCallback == null) return;
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                cm.unregisterNetworkCallback(networkRecoveryCallback);
            }
        } catch (Exception ignored) {
        }
        networkRecoveryCallback = null;
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
        RecyclerView rv = getActiveRecyclerView();
        if (rv == null) return;
        LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
        if (lm == null) return;
        int firstVisible = lm.findFirstVisibleItemPosition();
        if (firstVisible <= insertIndex + 3) {
            rv.post(() -> lm.scrollToPositionWithOffset(insertIndex, 0));
        }
    }

    private void notifyRecycler(int index, ChatConversationMsg msg) {
        RecyclerView rv = getActiveRecyclerView();
        if (rv == null || (rv.getScrollState() == RecyclerView.SCROLL_STATE_IDLE && !rv.isComputingLayout())) {
            chatConversationAdapter.notifyItemChanged(index, msg);
        } else {
            pendingScrollIdleRefresh = true;
        }
    }

    private void refreshChannelInAdapter(ChatConversationAdapter adapter, WKChannel channel) {
        if (adapter == null || channel == null) return;
        for (int i = 0, size = adapter.getData().size(); i < size; i++) {
            ChatConversationMsg item = adapter.getData().get(i);
            if (item.isSectionHeader) continue;
            if (item.uiConversationMsg != null
                    && channel.channelID.equals(item.uiConversationMsg.channelID)
                    && channel.channelType == item.uiConversationMsg.channelType) {
                item.uiConversationMsg.setWkChannel(channel);
                item.isRefreshChannelInfo = true;
                item.isResetCounter = true;
                adapter.notifyItemChanged(i, item);
                break;
            }
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
                // 冷启动直接赋值，不走 TextSwitcher 动画，避免布局跳动
                setSpaceSwitcherTextImmediate(cachedName);
                updateSpaceAvatar(cachedName);
            } else {
                setSpaceSwitcherTextImmediate(getString(R.string.app_name));
                updateSpaceAvatar(getString(R.string.app_name));
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
            setSpaceSwitcherTextImmediate(getString(R.string.app_name));
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

    /** 冷启动时直接设置文字，跳过动画和 requestLayout，避免布局跳动 */
    private void setSpaceSwitcherTextImmediate(String text) {
        // TextSwitcher factory 可能尚未创建子 View，手动触发
        if (wkVBinding.textSwitcher.getChildCount() == 0) {
            wkVBinding.textSwitcher.setCurrentText(text);
        } else {
            for (int i = 0; i < wkVBinding.textSwitcher.getChildCount(); i++) {
                View child = wkVBinding.textSwitcher.getChildAt(i);
                if (child instanceof TextView) {
                    ((TextView) child).setText(text);
                }
            }
        }
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
        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) (getActiveRecyclerView() != null ? getActiveRecyclerView().getLayoutManager() : null);
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
                && uiMsg.channelType != WKChannelType.COMMUNITY_TOPIC
                && uiMsg.channelType != WKChannelType.PERSONAL) return;

        WKMsg wkMsg = uiMsg.getWkMsg();
        if (wkMsg == null) return;

        String loginUid = WKConfig.getInstance().getUid();
        if (!TextUtils.isEmpty(wkMsg.fromUID) && wkMsg.fromUID.equals(loginUid)) return;

        if (wkMsg.type >= 15) return;

        if (!TextUtils.isEmpty(wkMsg.fromUID)) {
            WKChannel sender = WKIM.getInstance().getChannelManager()
                    .getChannel(wkMsg.fromUID, WKChannelType.PERSONAL);
            if (sender != null && sender.robot == 1) return;
        }

        if (TextUtils.isEmpty(wkMsg.messageID) || "0".equals(wkMsg.messageID)) return;
        String msgIdStr = wkMsg.messageID;
        if (shownHintMsgIds.contains(msgIdStr)) return;
        shownHintMsgIds.add(msgIdStr);
        if (shownHintMsgIds.size() > 500) {
            shownHintMsgIds.clear();
            shownHintMsgIds.add(msgIdStr);
        }

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
        } else if (uiMsg.channelType == WKChannelType.PERSONAL) {
            if (channel != null) {
                name = !TextUtils.isEmpty(channel.channelRemark)
                        ? channel.channelRemark : channel.channelName;
            }
            avatarUrl = WKApiConfig.getShowAvatar(uiMsg.channelID, uiMsg.channelType);
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

    private void handleThreadMuteToggle(String threadChannelId, String threadName) {
        handleMuteToggle(threadChannelId, WKChannelType.COMMUNITY_TOPIC);
    }

    private void handleMuteToggle(String channelId, byte channelType) {
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);
        boolean isMuted = channel != null && channel.mute == 1;
        int newMute = isMuted ? 0 : 1;

        if (channel != null) {
            channel.mute = newMute;
            WKIM.getInstance().getChannelManager().saveOrUpdateChannel(channel);
        } else {
            channel = new WKChannel(channelId, channelType);
            channel.mute = newMute;
            WKIM.getInstance().getChannelManager().saveOrUpdateChannel(channel);
        }

        filterAndDisplay();
        setAllCount();

        if (channelType == WKChannelType.GROUP || channelType == WKChannelType.COMMUNITY_TOPIC) {
            GroupModel.getInstance().updateGroupSetting(channelId, "mute", newMute, (code, msg) -> {});
        } else if (channelType == WKChannelType.PERSONAL) {
            FriendModel.getInstance().updateUserSetting(channelId, "mute", newMute, (code, msg) -> {});
        }
    }

    private void showThreadMuteMenu(String threadChannelId, String threadName, View anchor) {
        handleMuteToggle(threadChannelId, WKChannelType.COMMUNITY_TOPIC);
    }

}
