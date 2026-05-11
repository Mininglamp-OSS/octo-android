package com.chat.uikit.chat;

import static androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewConfiguration;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

// YUJ-236 phase2 perf: Glide pause/resume on RecyclerView scroll (A3)
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;

import com.chat.base.common.WKCommonModel;
import com.chat.uikit.chat.face.WKVoiceViewManager;
import com.chat.base.config.WKBinder;
import com.chat.base.foldable.NarrowTransition;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.config.WKSystemAccount;
import com.chat.base.emoji.MoonUtil;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.AvatarOtherViewMenu;
import com.chat.base.endpoint.entity.CallingViewMenu;
import com.chat.base.endpoint.entity.RTCMenu;
import com.chat.base.endpoint.entity.ReadMsgMenu;
import com.chat.base.endpoint.entity.SetChatBgMenu;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.entity.UserOnlineStatus;
import com.chat.base.entity.WKChannelCustomerExtras;
import com.chat.base.entity.WKGroupType;
import com.chat.base.msg.ChatAdapter;
import com.chat.base.msg.ChatContentSpanType;
import com.chat.base.msg.IConversationContext;
import com.chat.base.msgitem.WKChannelMemberRole;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.msgitem.WKUIChatMsgItemEntity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.space.SpaceChangedBroadcaster;
import com.chat.base.space.SpaceFilter;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.NumberTextView;
import com.chat.base.ui.components.SystemMsgBackgroundColorSpan;
import com.chat.base.utils.ActManagerUtils;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.UserUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKPermissions;
import com.chat.base.utils.WKPlaySound;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKTimeUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.base.utils.systembar.WKStatusBarUtils;
import com.chat.base.views.CommonAnim;
import com.chat.base.views.swipeback.SwipeBackActivity;
import com.chat.base.views.swipeback.SwipeBackLayout;
import com.chat.uikit.R;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.chat.manager.SendMsgEntity;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.chat.uikit.chat.manager.WKSendMsgUtils;
import com.chat.uikit.chat.msgmodel.WKCardContent;
import com.chat.uikit.contacts.ChooseContactsActivity;
import com.chat.uikit.databinding.ActChatLayoutBinding;
import com.chat.uikit.group.ChooseVideoCallMembersActivity;
import com.chat.uikit.group.GroupDetailActivity;
import com.chat.uikit.group.service.GroupModel;
import com.chat.uikit.message.MsgModel;
import com.chat.uikit.thread.ThreadDetailActivity;
import com.chat.uikit.thread.msgmodel.WKThreadCreatedContent;
import com.chat.uikit.thread.service.ThreadModel;
import com.chat.uikit.robot.service.WKRobotModel;
import com.chat.uikit.space.SpaceModel;
import com.chat.uikit.user.service.UserModel;
import com.chat.uikit.view.WKPlayVoiceUtils;
import com.effective.android.panel.PanelSwitchHelper;
import com.effective.android.panel.interfaces.ContentScrollMeasurer;
import com.effective.android.panel.interfaces.listener.OnPanelChangeListener;
import com.effective.android.panel.view.panel.IPanelView;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKCMD;
import com.xinbida.wukongim.entity.WKCMDKeys;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelExtras;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelState;
import com.xinbida.wukongim.entity.WKChannelStatus;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKConversationMsgExtra;
import com.xinbida.wukongim.entity.WKMentionType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKMsgReaction;
import com.xinbida.wukongim.entity.WKReminder;
import com.xinbida.wukongim.entity.WKSendOptions;
import com.xinbida.wukongim.interfaces.IGetOrSyncHistoryMsgBack;
import com.xinbida.wukongim.message.type.WKConnectStatus;
import com.xinbida.wukongim.message.type.WKSendMsgResult;
import com.xinbida.wukongim.msgmodel.WKImageContent;
import com.xinbida.wukongim.msgmodel.WKMediaMessageContent;
import com.xinbida.wukongim.msgmodel.WKMessageContent;
import com.xinbida.wukongim.msgmodel.WKVideoContent;
import com.xinbida.wukongim.msgmodel.WKMsgEntity;
import com.xinbida.wukongim.msgmodel.WKReply;
import com.chat.base.msgcontent.WKFileContent;
import com.chat.base.utils.WKFileUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class ChatActivity extends SwipeBackActivity implements IConversationContext {
    private static final int MAX_ADAPTER_SIZE = 300;
    private static final int TRIM_BATCH_SIZE = 60;
    private RecyclerView.EdgeEffectFactory edgeEffectFactory; // 用于清除 EdgeEffect

    private String channelId = "";
    private byte channelType = WKChannelType.PERSONAL;
    private ChatAdapter chatAdapter;
    private com.chat.base.msgeffect.MessageEffectManager messageEffectManager;
    private com.chat.base.msgeffect.MessageEffectOverlayView messageEffectOverlay;
    //是否在查看历史消息
    private boolean isShowHistory;
    private boolean isSyncLastMsg = false;
    private boolean isToEnd = true;
    private boolean isViewingPicture = false;
    private final boolean showNickName = true; // 是否显示聊天昵称
    private long lastPreviewMsgOrderSeq = 0; //上次浏览消息
    private long unreadStartMsgOrderSeq = 0; //新消息开始位置
    private long tipsOrderSeq = 0; //需要强提示的msg
    // YUJ-256 P1-2: snapshot of the initial positioning intents. The existing
    // fields above (`unreadStartMsgOrderSeq`, `tipsOrderSeq`) are "consumed" —
    // zeroed out after they drive a scroll. YUJ-242's local-first behavior
    // now calls applyDataToAdapter twice (preview + sync), and without these
    // snapshots the second call silently skips the "new messages" divider and
    // unread scroll because the source fields were cleared on the first call.
    private long unreadStartSnapshotOrderSeq = 0;
    private long tipsSnapshotOrderSeq = 0;
    // YUJ-256 P1-3: flag flipped to true after the first applyDataToAdapter
    // preview render. Used to suppress scroll-to-end on the second (post-sync)
    // render so the user's manually-scrolled viewport is not yanked back to
    // the bottom when sync completes.
    private boolean hasRenderedPreview = false;
    // YUJ-256 P1-3: flipped true when the user has actively scrolled the chat
    // RecyclerView (DRAGGING). Also suppresses the second-render scroll-to-end.
    private boolean userHasScrolled = false;
    // YUJ-258 P2-NEW-1: flipped true after the first successful tips highlight
    // so the second applyDataToAdapter (sync-merge) does not replay the
    // `isShowTips = true` animation. Reset alongside tipsSnapshotOrderSeq in
    // every fresh-reload path (initData / clickResult / tipsMsg /
    // newMsgLayout click / reconnect refresh).
    private boolean hasShownTips = false;
    private int keepOffsetY = 0; // 上次浏览消息的偏移量
    private int redDot = 0; // 未读消息数量
    private boolean hasPositionedUnread = false; // 是否已完成未读定位
    private int lastVisibleMsgSeq = 0; // 最后可见消息序号
    private int maxMsgSeq = 0;
    private long maxMsgOrderSeq = 0;
    //回复的消息对象
    private WKMsg replyWKMsg;
    // 编辑对象
    private WKMsg editMsg;
    // 群成员数量
    private int count;
    private int groupType = WKGroupType.normalGroup;
    //已读消息ID
    private final List<String> readMsgIds = new ArrayList<>();
    private Disposable disposable;
    private boolean isUploadReadMsg = true;
    private NumberTextView numberTextView;
    //    boolean isUpdateCoverMsg = false;
    private boolean isCanLoadMore;
    boolean isRefreshLoading = false;
    boolean isMoreLoading = false;
    boolean isCanRefresh = true;
    private boolean isShowChatActivity = true;
    LinearLayoutManager linearLayoutManager;
    private final List<WKReminder> reminderList = new ArrayList<>();
    private final List<WKReminder> groupApproveList = new ArrayList<>();
    private final List<Long> reminderIds = new ArrayList<>();
    private long browseTo = 0;
    private boolean isUpdateRedDot = true;
    private ImageView callIV;
    private ImageView moreIV;
    private long lastShowTimeUpdate = 0;
    private int lastShowTimeIndex = -1;
    //查询聊天数据偏移量
    private final int limit = 30;
    private boolean isShowPinnedView = false;
    private boolean isShowCallingView = false;
    private boolean isTipMessage = false;
    private int hideChannelAllPinnedMessage = 0;
    private PanelSwitchHelper mHelper;
    private ChatPanelManager chatPanelManager;
    private ActChatLayoutBinding wkVBinding;
    private int unfilledHeight = 0;
    private final String loginUID = WKConfig.getInstance().getUid();
    private final int callingViewHeight = AndroidUtilities.dp(40f);
    private final int pinnedViewHeight = AndroidUtilities.dp(50f);
    private boolean hasJoinedThread = false;

    // YUJ-324 · Space 上下文快照：每次 initParam 时记录当前 Space，用于两处防御：
    // (1) SpaceChangedBroadcaster 监听回调里比较"实例快照 != 新 Space"→ finish；
    // (2) onNewIntent 复用路径顶部二次校验"实例快照 != SP 里现值"→ finish 后让
    //     下一次 ChatReuseNavigator.launchChat 走冷启路径（defense in depth：
    //     万一广播因进程切换 / race 没有到达）。
    private String lastKnownSpaceId = "";

    // YUJ-324 · Space 变化监听器；onCreate 注册、onDestroy 反注册。放成实例字段
    // 是为了保留"一个 Activity 实例对应一个 listener"的语义，反注册时能精确定位。
    private SpaceChangedBroadcaster.Listener spaceChangedListener;
    private int getTopPinViewHeight() {
        int totalHeight = 0;
        if (isShowCallingView) {
            totalHeight += callingViewHeight;
        }
        if (isShowPinnedView) {
            totalHeight += pinnedViewHeight;
        }
        return totalHeight;
    }

    private void p2pCall(int callType) {
        EndpointManager.getInstance().invoke("wk_p2p_call", new RTCMenu(this, callType));
    }

    private void toggleStatusBarMode() {
        Window window = getWindow();
        if (window == null) return;
        WKStatusBarUtils.transparentStatusBar(window);
        if (!Theme.getDarkModeStatus(this))
            WKStatusBarUtils.setDarkMode(window);
        else WKStatusBarUtils.setLightMode(window);
    }

    private void initParam() {
        toggleStatusBarMode();
        //频道ID
        channelId = getIntent().getStringExtra("channelId");
        //频道类型
        channelType = getIntent().getByteExtra("channelType", WKChannelType.PERSONAL);
        // YUJ-324 · 每次 initParam 时刷新 Space 快照。冷启动 onCreate → initParam 拿到
        // 真实当前 Space；onNewIntent 复用路径 setIntent + initParam 之后也会走到这里，
        // 保证 lastKnownSpaceId 永远跟当前 Activity 实际渲染的 Space 对齐。
        lastKnownSpaceId = SpaceFilter.getCurrentSpaceId();
        maxMsgOrderSeq = WKIM.getInstance().getMsgManager().getMaxOrderSeqWithChannel(channelId, channelType);
        maxMsgSeq = WKIM.getInstance().getMsgManager().getMaxMessageSeqWithChannel(channelId, channelType);
        resetHideChannelAllPinnedMessage();
        // 是否含有带转发的消息
        if (getIntent().hasExtra("msgContentList")) {
            List<WKMessageContent> msgContentList = getIntent().getParcelableArrayListExtra("msgContentList");
            if (WKReader.isNotEmpty(msgContentList)) {
                List<WKChannel> list = new ArrayList<>();
                WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);
                list.add(channel);
                WKUIKitApplication.getInstance().showChatConfirmDialog(this, list, msgContentList, (list1, messageContentList) -> {
                    List<SendMsgEntity> msgList = new ArrayList<>();
                    WKSendOptions options = new WKSendOptions();
                    options.setting.receipt = getChatChannelInfo().receipt;
                    for (int i = 0, size = msgContentList.size(); i < size; i++) {
                        msgList.add(new SendMsgEntity(msgContentList.get(i), channel, options));
                    }
                    WKSendMsgUtils.getInstance().sendMessages(msgList);
                });

            }
        }

    }

    private void initSwipeBackFinish() {
        SwipeBackLayout mSwipeBackLayout = getSwipeBackLayout();
        if (mSwipeBackLayout != null) {
            mSwipeBackLayout.setEdgeTrackingEnabled(SwipeBackLayout.EDGE_LEFT);
            mSwipeBackLayout.setEnableGesture(true);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // YUJ-276 · diagnostic trace：窄屏冷启 breakdown 最大的一段发生在 onCreate
        // （DataBindingUtil.setContentView 膨胀 act_chat_layout.xml ≈ 80-200ms）+
        // onStart 里的 PanelSwitchHelper.Builder(this).build() ≈ 50-100ms + initData
        // 同步 DB 读。grep "YUJ276-trace" 可以和 WKIMUtils 里的 T_CLICK /
        // T_START_ACTIVITY 串起整条链路。
        long t0 = SystemClock.uptimeMillis();
        super.onCreate(savedInstanceState);
        // YUJ-278 P1-1（Fix D 自我覆盖版）：把窄屏 120ms 快过渡下沉到 ChatActivity
        // 自己注册，不再依赖 WKIMUtils.startChat。这样 WKThreadCreatedProvider
        // （子区卡片点击）/ SearchAllActivity / CreateThreadActivity 这些直接
        // startActivity(ChatActivity) 的调用方也能吃到快动画，和会话列表点击一致。
        // 在 API 34+ 上内部会同时注册 CLOSE override（见 NarrowTransition javadoc），
        // finish() 里的 applyFastClose 只影响 pre-34 设备。
        NarrowTransition.applyFastOpen(this);
        initSwipeBackFinish();
        wkVBinding = DataBindingUtil.setContentView(this, R.layout.act_chat_layout);
        long tInflate = SystemClock.uptimeMillis();
//        setContentView(R.layout.act_chat_layout1);
        // YUJ-252 / GH #182 Bug 1 — In Activity Embedding expanded mode the
        // secondary pane does not reliably receive adjustResize behavior, so the
        // IME can overlap the message input area. Manually translate IME insets
        // into bottom padding on the root view. On phone (single-pane) the IME
        // insets are still dispatched correctly, so this behaves identically to
        // the adjustResize default.
        //
        // YUJ-253 / GH #184 — MUST return `insets` (not WindowInsetsCompat.CONSUMED).
        // Returning CONSUMED interrupts insets propagation to child views and
        // regressed PR#181's pane-aware bubble auto-resize (BubbleLayout /
        // descendants stopped getting layout/insets callbacks when the divider
        // was dragged). IME occlusion is already handled by the setPadding above,
        // so we do not need to consume; just keep the chain alive.
        ViewCompat.setOnApplyWindowInsetsListener(wkVBinding.rootView, (v, insets) -> {
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            // 仅 Activity Embedding 分屏态需要手动补偿 IME bottom padding（副栏收不到
            // adjustResize）。窄屏/普通手机由系统 adjustResize + PanelSwitchHelper 协同
            // 处理，额外 padding 会导致消息列表显示不到底部。
            boolean narrow = NarrowTransition.isNarrow(this);
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    narrow ? 0 : imeInsets.bottom);
            return insets;
        });
        initParam();
        initView();
        initListener();
        //initData();
        ActManagerUtils.getInstance().addActivity(this);
        // YUJ-324 · 注册 Space 变化监听。ChatReuseNavigator.goBackToList 用
        // REORDER_TO_FRONT 让 ChatActivity 常驻任务栈；一旦用户从 TabActivity
        // 切了 Space，本实例必须主动销毁 —— 否则下次 launchChat 走 onNewIntent
        // 复用，Space 上下文（WKIM channel session / WKChannel.remoteExtraMap
        // 缓存 / 未经 Space 校验的消息 DB 读取）不会被重置，会出现"上个 Space
        // 的内容串到新 Space 频道"的数据隔离破坏（P0）。
        //
        // 单实例封闭：lambda 只捕获 this 弱意义上的引用（随 Activity 生命周期
        // 在 onDestroy 反注册），不会产生 process-scope 泄漏。
        spaceChangedListener = (oldSpaceId, newSpaceId) -> {
            if (isFinishing() || isDestroyed()) return;
            if (!TextUtils.equals(lastKnownSpaceId, newSpaceId)) {
                if (WKBinder.isDebug) {
                    Log.w("YUJ324-space-switch",
                            "ChatActivity self-finish on Space switch: channel=" + channelId
                                    + " old=" + oldSpaceId + " new=" + newSpaceId
                                    + " snapshot=" + lastKnownSpaceId);
                }
                // 清掉全局 chattingChannelID：如果分屏态用这个字段恢复选中，这里
                // 主动置空避免新 Space 的 ChatFragment.onResume 读到旧值去 re-select
                // 一个不在新 Space 列表里的 channel。
                if (TextUtils.equals(WKUIKitApplication.getInstance().chattingChannelID, channelId)) {
                    WKUIKitApplication.getInstance().chattingChannelID = "";
                }
                finish();
            }
        };
        SpaceChangedBroadcaster.addListener(spaceChangedListener);
        // YUJ-305 P1-A · 预测性返回（Predictive Back，API 33+）不走 onKeyDown → onBackPressed
        // 分发链，而是走 OnBackInvokedDispatcher / OnBackPressedDispatcher。若不注册回调，
        // 系统手势返回会直接 finish()，绕过 setBackListener() → goBackToList 的 soft-back
        // 优化路径。这里注册一个 OnBackPressedCallback，把预测性返回统一路由回 setBackListener()。
        // AndroidX 的 OnBackPressedDispatcher 在 API 33+ 会自动桥接到 OnBackInvokedDispatcher，
        // 在 33 以下也是 onBackPressed 的标准入口，所以单一注册点覆盖所有版本。
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // setBackListener 已处理：多选态退出 / 面板收起 / soft-back（返回 false 表示
                // 已消费但不 finish；分屏 / 非窄屏走内置的 150ms postDelayed finish）。如果
                // 走到 soft-back 成功分支，ChatActivity 仍留在栈里，回调保持 enabled，下次
                // back 仍会命中本分支。此处不需要再调 finish()。
                //
                // YUJ-311 防御 · OnBackPressedCallback 通过 LifecycleOwner(this) 绑定，
                // 正常情况下在 STARTED 以下会自动 disable；但 Activity Embedding 快速
                // 切副栏 / finish 中的极端时序下 callback 可能在 isFinishing=true
                // 时短暂被调度到。setBackListener 会访问 chatAdapter / chatPanelManager，
                // 后两者在 super.finish() 之后 onDestroy 里被清理，先 guard 避免 NPE。
                if (isFinishing() || isDestroyed()) return;
                setBackListener();
            }
        });
        // YUJ-251 / GH #180 — L2 pane-aware: when the Embedding pane resizes (divider
        // drag, unfold, rotation), re-bind only the currently-visible messages so the
        // bubble max-width cap (driven by PaneMetrics) refreshes without re-creating
        // unrelated ViewHolders. BubbleLayout auto-caps on every measure, so this is
        // belt-and-suspenders — it guarantees TextView wrapping is recomputed for all
        // visible rows immediately on the first layout after a resize.
        setupPaneResizeObserver();
        if (WKBinder.isDebug) {
            long tEnd = SystemClock.uptimeMillis();
            Log.d("YUJ276-trace", "[T_ON_CREATE_END] channel=" + channelId
                    + " inflate=" + (tInflate - t0) + "ms"
                    + " total=" + (tEnd - t0) + "ms");
        }
    }

    /** Last-observed RecyclerView width, used to filter spurious layout passes. */
    private int lastRecyclerViewWidth = 0;

    private void setupPaneResizeObserver() {
        if (wkVBinding == null || wkVBinding.recyclerView == null) return;
        wkVBinding.recyclerView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int newWidth = right - left;
                if (newWidth <= 0 || newWidth == lastRecyclerViewWidth) return;
                int prev = lastRecyclerViewWidth;
                lastRecyclerViewWidth = newWidth;
                if (prev == 0) return; // first layout — nothing to refresh
                if (chatAdapter == null || linearLayoutManager == null) return;
                // Post so we don't mutate the adapter in the middle of the current pass.
                wkVBinding.recyclerView.post(() -> {
                    if (chatAdapter == null || linearLayoutManager == null) return;
                    int first = linearLayoutManager.findFirstVisibleItemPosition();
                    int last = linearLayoutManager.findLastVisibleItemPosition();
                    if (first < 0 || last < 0 || last < first) return;
                    int count = last - first + 1;
                    try {
                        chatAdapter.notifyItemRangeChanged(first, count);
                    } catch (Throwable ignored) {
                        // Defensive — never crash on adapter churn during a resize race.
                    }
                });
            }
        });
    }

    @Override
    protected void onResume() {
        // YUJ-276 · diagnostic trace：onResume 是「用户看到 Activity 首帧」的最后
        // 一个生命周期节点，和 T_CLICK 的 delta 就是感知延迟。真正的像素出现时间还
        // 要加一帧（~16ms）的 measure/layout/draw，但这条 log 足够定位 P90/P99。
        long tOnResume = SystemClock.uptimeMillis();
        super.onResume();
        // YUJ-240 round3 fix (Jerry-Xin B1): 若后台/来电时 RV 停在 DRAGGING/SETTLING，onScrollStateChanged(IDLE) 不会触发，
        // Glide 会永远停住。onResume 主动恢复，消除生命周期死锁。
        try {
            Glide.with(this).resumeRequests();
        } catch (IllegalArgumentException ignored) {
            // Activity 销毁竞态
        }
        isShowChatActivity = true;
        WKUIKitApplication.getInstance().chattingChannelID = channelId;
        isUploadReadMsg = true;
        chatPanelManager.initRefreshListener();
        EndpointManager.getInstance().invoke("start_screen_shot", this);
        // 从子区返回时重新拉取消息数量
        fetchThreadMessageCounts();
        if (WKBinder.isDebug) {
            Log.d("YUJ276-trace", "[T_ON_RESUME_END] channel=" + channelId
                    + " total=" + (SystemClock.uptimeMillis() - tOnResume) + "ms");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // YUJ-276 · diagnostic trace：onStart 负责首次建 PanelSwitchHelper +
        // ChatPanelManager + 首轮 initData。这是 onCreate 之后第二大耗时段。
        long tOnStart = SystemClock.uptimeMillis();
        if (mHelper == null) {
            mHelper = new PanelSwitchHelper.Builder(this)
                    //可选
                    .addKeyboardStateListener((visible, height) -> {
                        if (visible && height > 0) {
                            WKConstants.setKeyboardHeight(height);
                        }
                    })
                    //可选
                    .addPanelChangeListener(new OnPanelChangeListener() {

                        @Override
                        public void onKeyboard() {
                            chatPanelManager.resetToolBar();
                            SoftKeyboardUtils.getInstance().requestFocus(wkVBinding.editText);
                        }

                        @Override
                        public void onNone() {
                        }

                        @Override
                        public void onPanel(IPanelView view) {
                        }


                        @Override
                        public void onPanelSizeChange(IPanelView panelView, boolean portrait, int oldWidth, int oldHeight, int width, int height) {

                        }
                    }).addContentScrollMeasurer(new ContentScrollMeasurer() {
                        @Override
                        public int getScrollDistance(int i) {
                            View bottomView = findViewById(R.id.bottomView);
                            View followView = findViewById(R.id.followScrollView);
                            return i - (bottomView.getTop() - followView.getBottom());
                        }

                        @Override
                        public int getScrollViewId() {
                            return R.id.recyclerViewLayout;
                        }
                    }).addContentScrollMeasurer(new ContentScrollMeasurer() {
                        @Override
                        public int getScrollDistance(int i) {
                            return 0;
                        }

                        @Override
                        public int getScrollViewId() {
                            return R.id.scrollViewLayout;
                        }
                    }).addContentScrollMeasurer(new ContentScrollMeasurer() {
                        @Override
                        public int getScrollDistance(int i) {
                            return 0;
                        }

                        @Override
                        public int getScrollViewId() {
                            return R.id.timeTv;
                        }
                    }).addContentScrollMeasurer(new ContentScrollMeasurer() {
                        @Override
                        public int getScrollDistance(int i) {
                            return 0;
                        }

                        @Override
                        public int getScrollViewId() {
                            return R.id.imageView;
                        }
                    }).addContentScrollMeasurer(new ContentScrollMeasurer() {
                        @Override
                        public int getScrollDistance(int i) {
                            return i - unfilledHeight;
                        }

                        @Override
                        public int getScrollViewId() {
                            return R.id.recyclerView;
                        }
                    })
                    .logTrack(WKBinder.isDebug)
                    .build(false);
        }
        if (chatPanelManager == null) {
            FrameLayout moreView = findViewById(R.id.chatMoreLayout);
            chatPanelManager = new ChatPanelManager(mHelper, findViewById(R.id.bottomView), moreView, findViewById(R.id.followScrollView), this, () -> {
                CommonAnim.getInstance().rotateImage(wkVBinding.topLayout.backIv, 180f, 360f, R.mipmap.ic_ab_back);
                numberTextView.setNumber(0, true);
                CommonAnim.getInstance().showOrHide(numberTextView, false, true);
                CommonAnim.getInstance().showOrHide(callIV, true, true);
                CommonAnim.getInstance().showOrHide(moreIV, true, true);
                return null;
            }, path -> {
                Intent intent = new Intent(ChatActivity.this, PreviewNewImgActivity.class);
                intent.putExtra("path", path);
                previewNewImgResultLac.launch(intent);
                return null;
            });
            long tInitDataStart = SystemClock.uptimeMillis();
            initData();
            if (WKBinder.isDebug) {
                long tEnd = SystemClock.uptimeMillis();
                Log.d("YUJ276-trace", "[T_ON_START_END] channel=" + channelId
                        + " initData=" + (tEnd - tInitDataStart) + "ms"
                        + " total=" + (tEnd - tOnStart) + "ms");
            }
        } else if (WKBinder.isDebug) {
            Log.d("YUJ276-trace", "[T_ON_START_END] channel=" + channelId
                    + " reused=true total=" + (SystemClock.uptimeMillis() - tOnStart) + "ms");
        }
    }

    protected void initView() {
        EndpointManager.getInstance().invoke("set_chat_bg", new SetChatBgMenu(channelId, channelType, wkVBinding.imageView, wkVBinding.rootView, wkVBinding.blurView));
        Object pinnedLayoutView = EndpointManager.getInstance().invoke("get_pinned_message_view", this);
        if (pinnedLayoutView instanceof View) {
            wkVBinding.pinnedLayout.addView((View) pinnedLayoutView);
        }
        wkVBinding.timeTv.setShadowLayer(AndroidUtilities.dp(5f), 0f, 0f, 0);
        CommonAnim.getInstance().showOrHide(wkVBinding.timeTv, false, true);
        Theme.setPressedBackground(wkVBinding.topLayout.backIv);
        wkVBinding.topLayout.backIv.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(this, R.color.titleBarIcon), PorterDuff.Mode.MULTIPLY));
        wkVBinding.topLayout.avatarView.setSize(40);
        wkVBinding.chatUnreadLayout.progress.setSize(40);
        wkVBinding.chatUnreadLayout.progress.setStrokeWidth(1.5f);
        wkVBinding.chatUnreadLayout.progress.setProgressColor(ContextCompat.getColor(this, R.color.popupTextColor));

        wkVBinding.chatUnreadLayout.msgCountTv.setColors(R.color.white, R.color.reminderColor);
        wkVBinding.chatUnreadLayout.remindCountTv.setColors(R.color.white, R.color.reminderColor);
        wkVBinding.chatUnreadLayout.approveCountTv.setColors(R.color.white, R.color.reminderColor);

        numberTextView = new NumberTextView(this);
        numberTextView.setTextSize(18);
        numberTextView.setTextColor(Theme.colorAccount);
        wkVBinding.topLayout.rightView.addView(numberTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.END, 0, 0, 15, 0));

        Object isRegisterRTC = EndpointManager.getInstance().invoke("is_register_rtc", null);

        moreIV = wkVBinding.topLayout.moreIv;
        moreIV.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(this, R.color.colorDark), PorterDuff.Mode.MULTIPLY));
        moreIV.setBackground(Theme.createSelectorDrawable(Theme.getPressedColor()));
        moreIV.setVisibility(View.VISIBLE);

        callIV = new AppCompatImageView(this);
        callIV.setImageResource(R.mipmap.ic_call);
        if (isRegisterRTC instanceof Boolean) {
            boolean isRegister = (boolean) isRegisterRTC;
            if (isRegister) {
                wkVBinding.topLayout.rightView.addView(callIV, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.END, 0, 0, 15, 0));
            }
        }
        callIV.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(this, R.color.popupTextColor), PorterDuff.Mode.MULTIPLY));
        callIV.setBackground(Theme.createSelectorDrawable(Theme.getPressedColor()));

        CommonAnim.getInstance().showOrHide(numberTextView, false, false);

        // YUJ-240 review fix (Jerry-Xin W#2): 旧 DefaultItemAnimator 配置代码已死 — MyItemAnimator 在下面立即替换它，移除以清理死代码与 import。
        chatAdapter = new ChatAdapter(this, ChatAdapter.AdapterType.normalMessage);
        linearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        wkVBinding.recyclerView.setLayoutManager(linearLayoutManager);
        wkVBinding.recyclerView.setAdapter(chatAdapter);
        // YUJ-236 perf: MyItemAnimator 需显式关 change 动画，避免 notify 刷新与 fling 叠加掉帧。
        wkVBinding.recyclerView.setItemAnimator(new MyItemAnimator());
        chatAdapter.setAnimationFirstOnly(true);
        chatAdapter.setAnimationEnable(false);
        // 增大 off-screen ViewHolder 缓存，减少快速滑动时的 ViewHolder 创建开销
        wkVBinding.recyclerView.setItemViewCacheSize(20);

        // YUJ-236 phase2 perf (A4) + YUJ-240 round3 fix (Jerry-Xin W1): Text/Image 两类高频 viewType 回收池上限 5 → 20。
        // 原先还有 richText (14)，但 WKUIKitApplication 未注册该 provider，ChatAdapter.getItemType 不会返回 14，配置池是 no-op，删除。
        RecyclerView.RecycledViewPool msgPool = wkVBinding.recyclerView.getRecycledViewPool();
        msgPool.setMaxRecycledViews(WKContentType.WK_TEXT, 20);
        msgPool.setMaxRecycledViews(WKContentType.WK_IMAGE, 20);

        // Message effect overlay — 添加到 content 根 FrameLayout 顶层，覆盖整个内容区域
        messageEffectOverlay = new com.chat.base.msgeffect.MessageEffectOverlayView(this);
        FrameLayout.LayoutParams effectLP = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        messageEffectOverlay.setVisibility(View.INVISIBLE);
        FrameLayout contentRoot = findViewById(android.R.id.content);
        contentRoot.addView(messageEffectOverlay, effectLP);
        messageEffectManager = new com.chat.base.msgeffect.MessageEffectManager(this, messageEffectOverlay);
        chatAdapter.setOnMessageDisplayedListener((item, itemView) -> {
            if (messageEffectManager != null) {
                messageEffectManager.onMessageVisible(item.wkMsg, itemView);
            }
        });
    }

    private void initListener() {
        // 禁用滑动快捷回复手势：与表格水平滚动冲突，且长按已支持回复
        // ItemTouchHelper helper = new ItemTouchHelper(new MessageSwipeController(this, new SwipeControllerActions() {
        //     @Override
        //     public void showReplyUI(int position) {
        //         if (position < 0 || position >= chatAdapter.getData().size()) return;
        //         showReply(chatAdapter.getData().get(position).wkMsg);
        //     }
        //     @Override
        //     public void hideSoft() {}
        // }));
        // helper.attachToRecyclerView(wkVBinding.recyclerView);
        wkVBinding.topLayout.backIv.setOnClickListener(v -> setBackListener());
        callIV.setOnClickListener(view -> {
            WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, loginUID);
            if (getChatChannelInfo().forbidden == 1 || (member != null && member.forbiddenExpirationTime > 0)) {
                WKToastUtils.getInstance().showToast(getString(R.string.can_not_call_forbidden));
                return;
            }
            String desc = String.format(getString(R.string.microphone_permissions_des), getString(R.string.app_name));
            WKPermissions.getInstance().checkPermissions(new WKPermissions.IPermissionResult() {
                @Override
                public void onResult(boolean result) {
                    if (result) {
                        if (channelType == WKChannelType.PERSONAL) {
                            if (UserUtils.getInstance().checkMyFriendDelete(channelId) || UserUtils.getInstance().checkFriendRelation(channelId)) {
                                showToast(R.string.non_friend_relationship);
                                return;
                            }
                            if (UserUtils.getInstance().checkBlacklist(channelId)) {
                                showToast(R.string.call_be_blacklist);
                                return;
                            }
                            if (getChatChannelInfo().status == WKChannelStatus.statusBlacklist) {
                                showToast(R.string.call_blacklist);
                                return;
                            }
                            List<PopupMenuItem> list = new ArrayList<>();
                            list.add(new PopupMenuItem(getString(R.string.video_call), R.mipmap.chat_calls_video, () -> p2pCall(1)));
                            list.add(new PopupMenuItem(getString(R.string.audio_call), R.mipmap.chat_calls_voice, () -> p2pCall(0)));
                            WKDialogUtils.getInstance().showScreenPopup(view, list);
                        } else {
                            WKChannelMember channelMember = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, loginUID);
                            if (channelMember != null && channelMember.status == WKChannelStatus.statusBlacklist) {
                                showToast(R.string.call_blacklist_group);
                                return;
                            }
                            Intent intent = new Intent(ChatActivity.this, ChooseVideoCallMembersActivity.class);
                            intent.putExtra("channelID", channelId);
                            intent.putExtra("channelType", channelType);
                            intent.putExtra("isCreate", true);
                            startActivity(intent);
                        }
                    }
                }

                @Override
                public void clickResult(boolean isCancel) {
                }
            }, this, desc, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO);
        });

        moreIV.setOnClickListener(view -> {
            WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, loginUID);
            if ((member != null && member.isDeleted == 1) || channelType == WKChannelType.CUSTOMER_SERVICE)
                return;
            Intent intent;
            if (channelType == WKChannelType.COMMUNITY_TOPIC) {
                intent = new Intent(ChatActivity.this, ThreadDetailActivity.class);
                intent.putExtra("channelId", channelId);
            } else {
                intent = new Intent(ChatActivity.this, channelType == WKChannelType.GROUP ? GroupDetailActivity.class : ChatPersonalActivity.class);
                intent.putExtra("channelId", channelId);
            }
            startActivity(intent);
        });

        WKDialogUtils.getInstance().setViewLongClickPopup(wkVBinding.chatUnreadLayout.groupApproveLayout, getGroupApprovePopupItems());
        wkVBinding.chatUnreadLayout.groupApproveLayout.setOnClickListener(view -> {
            if (WKReader.isNotEmpty(groupApproveList)) {
                WKMsg msg = WKIM.getInstance().getMsgManager().getWithMessageID(groupApproveList.get(0).messageID);
                if (msg != null && !TextUtils.isEmpty(msg.clientMsgNO)) {
                    tipsMsg(msg.clientMsgNO);
                }
            }
        });
        WKDialogUtils.getInstance().setViewLongClickPopup(wkVBinding.chatUnreadLayout.remindLayout, getRemindPopupItems());
        wkVBinding.chatUnreadLayout.remindLayout.setOnClickListener(view -> {

            if (WKReader.isNotEmpty(reminderList)) {
                reminderIds.add(reminderList.get(0).reminderID);
                WKMsg msg = WKIM.getInstance().getMsgManager().getWithMessageID(reminderList.get(0).messageID);
                if (msg != null && !TextUtils.isEmpty(msg.clientMsgNO)) {
                    tipsMsg(msg.clientMsgNO);
                } else {
                    long orderSeq = WKIM.getInstance().getMsgManager().getMessageOrderSeq(reminderList.get(0).messageSeq, channelId, channelType);
                    unreadStartMsgOrderSeq = 0;
                    tipsOrderSeq = orderSeq;
                    // YUJ-256 P1-2: sync snapshots + reset render flags for
                    // the fresh data load so the second (post-sync) callback
                    // honours the new tips anchor.
                    unreadStartSnapshotOrderSeq = 0;
                    tipsSnapshotOrderSeq = orderSeq;
                    hasRenderedPreview = false;
                    userHasScrolled = false;
                    hasShownTips = false;
                    getData(1, true, orderSeq, false);
                    isCanLoadMore = true;
                }
            }
        });

        SingleClickUtil.onSingleClick(wkVBinding.topLayout.titleView, view -> {
            WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, loginUID);

            if ((member != null && member.isDeleted == 1) || channelType == WKChannelType.CUSTOMER_SERVICE)
                return;
            Intent intent;
            if (channelType == WKChannelType.COMMUNITY_TOPIC) {
                intent = new Intent(ChatActivity.this, ThreadDetailActivity.class);
                intent.putExtra("channelId", channelId);
            } else {
                intent = new Intent(ChatActivity.this, channelType == WKChannelType.GROUP ? GroupDetailActivity.class : ChatPersonalActivity.class);
                intent.putExtra("channelId", channelId);
            }
            startActivity(intent);
        });

        wkVBinding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (chatAdapter.getData().size() <= 1) return;
                setShowTime();
                int lastItemPosition = linearLayoutManager.findLastVisibleItemPosition();
                if (lastItemPosition < chatAdapter.getItemCount() - 1) {
                    wkVBinding.chatUnreadLayout.newMsgLayout.post(() -> CommonAnim.getInstance().showOrHide(wkVBinding.chatUnreadLayout.newMsgLayout, true, true, false));
                } else {
                    wkVBinding.chatUnreadLayout.newMsgLayout.post(() -> CommonAnim.getInstance().showOrHide(wkVBinding.chatUnreadLayout.newMsgLayout, redDot > 0, true, false));
                }
                resetRemindView();
                resetGroupApproveView();

                View lastChildView = linearLayoutManager.findViewByPosition(lastItemPosition);
                if (lastChildView != null) {
                    int bottom = lastChildView.getBottom();
                    int listHeight = wkVBinding.recyclerView.getHeight() - wkVBinding.recyclerView.getPaddingBottom();
                    unfilledHeight = listHeight - bottom;
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                // YUJ-256 P1-3: record that the user has actively driven the
                // RecyclerView. applyDataToAdapter will use this to suppress
                // its scroll-to-end on the post-sync re-render so we do not
                // yank the user back to the bottom of an unread chat.
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    userHasScrolled = true;
                }
                // YUJ-240 round3 fix (Jerry-Xin R2-Glide/S1): 仅 fling (SETTLING) 时 pause，
                // DRAGGING 保持加载（慢滑手指在屏不应看到占位符）；IDLE 恢复。
                try {
                    RequestManager glideMgr = Glide.with(ChatActivity.this);
                    if (newState == RecyclerView.SCROLL_STATE_SETTLING) {
                        glideMgr.pauseRequests();
                    } else if (newState == SCROLL_STATE_IDLE) {
                        glideMgr.resumeRequests();
                    }
                    // DRAGGING: 不变
                } catch (IllegalArgumentException ignored) {
                    // Activity 已销毁的竞态保护
                }
                // 简化日志：仅在 IDLE 时输出详情
                int lastItemPosition = linearLayoutManager.findLastVisibleItemPosition();
                isShowHistory = lastItemPosition < chatAdapter.getItemCount() - 1;
                if (newState == SCROLL_STATE_IDLE) {
                    boolean canUp = wkVBinding.recyclerView.canScrollVertically(-1);
                    boolean canDown = wkVBinding.recyclerView.canScrollVertically(1);
                    isTipMessage = false;
                    CommonAnim.getInstance().showOrHide(wkVBinding.timeTv, false, true);
                    EndpointManager.getInstance().invoke("stop_reaction_animation", null);
                    if (!canDown) { // 到达底部
                        showMoreLoading();
                    } else if (!canUp) { // 到达顶部

                        showRefreshLoading();
                    }
                } else {
                    MsgModel.getInstance().doneReminder(reminderIds);
                    if (!isUpdateRedDot) return;
                    MsgModel.getInstance().clearUnread(channelId, channelType, redDot, (code, msg) -> {
                        if (code == HttpResponseCode.success && redDot == 0) {
                            isUpdateRedDot = false;
                        }
                    });
                }
            }
        });

        wkVBinding.chatUnreadLayout.newMsgLayout.setOnClickListener(v -> {
            redDot = 0;
            MsgModel.getInstance().clearUnread(channelId, channelType, redDot, (code, msg) -> {
                if (code == HttpResponseCode.success && redDot == 0) {
                    isUpdateRedDot = false;
                }
            });
            if (isCanLoadMore) {
                isSyncLastMsg = true;
                // chatAdapter.setList(new ArrayList<>());
                wkVBinding.chatUnreadLayout.progress.setVisibility(View.VISIBLE);
                wkVBinding.chatUnreadLayout.msgDownIv.setVisibility(View.GONE);
                unreadStartMsgOrderSeq = 0;
                lastPreviewMsgOrderSeq = 0;
                // YUJ-258 P1-NEW-1: reset snapshots + render flags so the
                // fresh reload triggered by the "N new messages" bubble
                // actually scrolls to end and does not reinsert a stale
                // unread divider (YUJ-242 regression path).
                unreadStartSnapshotOrderSeq = 0;
                tipsSnapshotOrderSeq = 0;
                hasRenderedPreview = false;
                userHasScrolled = false;
                hasShownTips = false;
                long maxSeq = WKIM.getInstance().getMsgManager().getMaxOrderSeqWithChannel(channelId, channelType);
                new Handler().postDelayed(() -> {
                    getData(0, true, maxSeq, true);
                    showUnReadCountView();
                }, 500);
            } else {
                scrollToPosition(chatAdapter.getItemCount() - 1);
                showUnReadCountView();
            }

            isShowHistory = false;
            isCanLoadMore = false;
        });

        // YUJ-267 · Fix B：所有以 channelId 为 key 的 SDK 监听 / EndpointManager
        // setMethod 都挪到 attachChannelListeners()，配合 onNewIntent 做 detach →
        // initParam → attach 复用流程。initListener 只负责装配一次性的 UI click /
        // 非 channel-keyed endpoint method。
        attachChannelListeners();
        EndpointManager.getInstance().setMethod("hide_pinned_view", object -> {
            if (!isShowPinnedView) return null;
            isShowPinnedView = false;
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) wkVBinding.timeTv.getLayoutParams();
            lp.topMargin = AndroidUtilities.dp(10) + getTopPinViewHeight();
            wkVBinding.timeTv.setVisibility(View.GONE);
            ObjectAnimator animator = ObjectAnimator.ofFloat(wkVBinding.pinnedLayout, "translationY", 0, -AndroidUtilities.dp(53));
            animator.setDuration(200);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    wkVBinding.pinnedLayout.clearAnimation();
                    wkVBinding.pinnedLayout.setVisibility(View.GONE);
                    if (WKReader.isNotEmpty(chatAdapter.getData()) && chatAdapter.getData().get(0).wkMsg != null && chatAdapter.getData().get(0).wkMsg.type == WKContentType.spanEmptyView) {
                        if (!isShowCallingView) {
                            chatAdapter.getData().remove(0);
                            chatAdapter.notifyItemRemoved(0);
                        }
                        //chatAdapter.notifyDataSetChanged();
                    }
                }

                public void onAnimationStart(Animator animation) {
                    wkVBinding.pinnedLayout.setVisibility(View.VISIBLE);
                }
            });
            wkVBinding.pinnedLayout.setVisibility(View.VISIBLE);
            animator.start();
            return null;
        });
        EndpointManager.getInstance().setMethod("show_pinned_view", object -> {
            if (isShowPinnedView) {
                return null;
            }
            isShowPinnedView = true;

            if (WKReader.isNotEmpty(chatAdapter.getData()) && chatAdapter.getData().get(0).wkMsg != null && chatAdapter.getData().get(0).wkMsg.type != WKContentType.spanEmptyView) {
                WKMsg msg = getSpanEmptyMsg();
                chatAdapter.addData(0, new WKUIChatMsgItemEntity(this, msg, null));
            }
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) wkVBinding.timeTv.getLayoutParams();
            lp.topMargin = AndroidUtilities.dp(10) + getTopPinViewHeight();
            wkVBinding.timeTv.setVisibility(View.GONE);
            ObjectAnimator animator = ObjectAnimator.ofFloat(wkVBinding.pinnedLayout, "translationY", -wkVBinding.pinnedLayout.getHeight(), 0);
            animator.setDuration(200);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // wkVBinding.pinnedLayout.clearAnimation();
                    wkVBinding.pinnedLayout.setVisibility(View.VISIBLE);
                }
            });
            animator.start();
            wkVBinding.pinnedLayout.setVisibility(View.VISIBLE);
            return null;
        });
        EndpointManager.getInstance().setMethod("tip_msg_in_chat", object -> {
            tipsMsg((String) object);
            return null;
        });
        EndpointManager.getInstance().setMethod("reset_channel_all_pinned_msg", object -> {
            resetHideChannelAllPinnedMessage();
            for (int i = 0, size = chatAdapter.getData().size(); i < size; i++) {
                if (hideChannelAllPinnedMessage == 1) {
                    if (chatAdapter.getData().get(i).isPinned == 1) {
                        chatAdapter.getData().get(i).isPinned = 0;
                        chatAdapter.notifyStatus(i);
                    }
                } else {
                    if (chatAdapter.getData().get(i).isPinned == 0) {
                        if (chatAdapter.getData().get(i).wkMsg.remoteExtra != null && chatAdapter.getData().get(i).wkMsg.remoteExtra.isPinned == 1) {
                            chatAdapter.getData().get(i).isPinned = 1;
                            chatAdapter.notifyStatus(i);
                        }
                    }
                }
            }
            return null;
        });
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        // YUJ-276 · diagnostic trace：singleTop 复用路径。只有当 ChatActivity 已经
        // 在栈顶时才会走这里。YUJ-298 Fix A 之后，窄屏从 TabActivity 返回不再 finish，
        // 实例保活在任务栈里 —— 下一次 startActivity 通过 FLAG_ACTIVITY_REORDER_TO_FRONT
        // + FLAG_ACTIVITY_SINGLE_TOP 命中这里，彻底规避 recreate。
        long tNewIntent = SystemClock.uptimeMillis();
        String newChannelId = intent.getStringExtra("channelId");
        // YUJ-324 · 跨 Space 复用防御（defense in depth）。
        //
        // 正常路径：performSpaceSwitch → MsgModel.setCurrentSpaceId → 广播 →
        //   onCreate 注册的 spaceChangedListener 已经 finish 了自己，这里根本
        //   不会再收到 onNewIntent。但万一广播因进程切换 / race / Activity 在
        //   广播之前就被 REORDER 到栈顶（同 task 内事件顺序罕见但不绝对安全）
        //   没有被及时消费，本实例可能带着 Space A 的快照收到 Space B 的
        //   newChannelId。如果直接走常规切频道分支，SDK 内的 channel session /
        //   WKChannel.remoteExtraMap / DB 读取会在新 Space 上下文缺失的情况下
        //   渲染旧 Space 的残留 → 数据隔离破坏（P0）。
        //
        // 这里用"本实例 Space 快照 vs. SP 里现值"做最后一道闸：不一致就 finish，
        // 并用原始 Intent 重新 startActivity 走冷启路径，让 onCreate → initParam
        // 按 Space B 上下文重新加载所有 Space-aware 状态。对齐 Plan B（onNewIntent
        // 自修复），把 Plan C（SpaceChanged 广播）没覆盖到的边界兜住。
        String currentSpaceId = SpaceFilter.getCurrentSpaceId();
        if (!TextUtils.equals(lastKnownSpaceId, currentSpaceId)) {
            if (WKBinder.isDebug) {
                Log.w("YUJ324-space-switch",
                        "onNewIntent detected stale Space snapshot: channel=" + channelId
                                + " newChannel=" + newChannelId
                                + " snapshot=" + lastKnownSpaceId
                                + " current=" + currentSpaceId
                                + " → finish + cold relaunch");
            }
            if (TextUtils.equals(WKUIKitApplication.getInstance().chattingChannelID, channelId)) {
                WKUIKitApplication.getInstance().chattingChannelID = "";
            }
            finish();
            // 用新 Intent 重新打开 —— 剥掉 REORDER_TO_FRONT / SINGLE_TOP 防止
            // 再次命中自己（虽然此刻本实例已 finish，但 AMS 可能尚未回收）。
            Intent relaunch = new Intent(intent);
            relaunch.setFlags(intent.getFlags()
                    & ~Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    & ~Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(relaunch);
            return;
        }
        if (WKBinder.isDebug) {
            Log.d("YUJ276-trace", "[T_ON_NEW_INTENT] previousChannel=" + channelId
                    + " newChannel=" + newChannelId);
        }
        // YUJ-298 · Fix A 同频道短路：窄屏用户常常 back → 回到列表 → 再点同一个会话
        // 进入；此时 oldChannelId == newChannelId，走完整 detach/reset/attach 会造成
        // chatAdapter.setList([]) 闪烁 + 重新读 DB。直接短路掉，保持原画面。
        //
        // YUJ-305 P0-1 · 短路路径必须处理"跳消息 / 转发 / 搜索定位"类 extras，否则
        //   - 点同会话 @ mention 通知 → 不跳消息
        //   - 转发到当前会话 → 不弹确认框
        //   - 搜索结果定位同会话某条消息 → 不滚动高亮
        // 抽出 applyIntentExtrasForReuse()，同时被同频道短路分支和 onDestroy → onCreate
        // 冷路径之间的中间态（可能仍由其它系统事件驱动）复用。
        //
        // YUJ-305 P0-2 · 短路路径必须主动持久化当前 channel 编辑态。Fix A 之前 soft-back
        // 不 finish → onDestroy 不触发 → saveEditContent 不执行 → 草稿 / 浏览位置 / 阅后
        // 即焚清理全部丢失。即使在短路路径（未 back，也未切群）也要 flush 一次，避免
        // 从其他入口（deeplink / 通知）携带新 extras 打断输入时草稿丢失。
        if (newChannelId != null && newChannelId.equals(channelId)) {
            // 先 flush 当前 channel 的编辑态 / 未读 / readMsg 上报，之后的 applyIntentExtras
            // 可能改变可见消息集合，编辑态需要基于"现在的"视图状态落盘。
            persistCurrentChannelEditState();
            // 更新 Intent 以保留新的 extras（tipsOrderSeq / msgContentList / aroundMsgSeq 等）。
            setIntent(intent);
            WKUIKitApplication.getInstance().chattingChannelID = channelId;
            // 分发跳消息 / 转发 payload / 搜索定位 extras 给当前 UI。
            applyIntentExtrasForReuse(intent);
            if (WKBinder.isDebug) {
                Log.d("YUJ276-trace", "[T_ON_NEW_INTENT_END] channel=" + channelId
                        + " sameChannel=true total=" + (SystemClock.uptimeMillis() - tNewIntent) + "ms");
            }
            return;
        }
        // YUJ-267 · Fix B：分屏态 Activity 复用路径。同实例切不同群时走这里而不是
        // onDestroy → onCreate，省掉 XML 膨胀 / PanelSwitchHelper 首建 / Activity
        // transition，目标感知延迟 < 200ms（原 500-800ms）。
        //
        // 流程（YUJ-270 P0-2 修正：persist 必须在 reset 之前！）：
        // 1. 快照 oldChannelId / oldChannelType —— 两者在 initParam 后都会变；
        // 2. 用旧 channelId 做 key 卸所有 channel-keyed 监听；
        // 3. **先 persist 旧 channel 的草稿 / 已读 / 未读**（此时实例字段仍是旧值，
        //    editText.getText / readMsgIds / redDot 未被 reset 清零）；
        // 4. setIntent + initParam 更新 channelId / channelType + maxMsgSeq/OrderSeq；
        // 5. resetPerChannelState 清 per-channel 一次性状态（reminder / redDot /
        //    snapshot / userHasScrolled / disposable / isRefreshLoading 等）；
        // 6. 显式清 chatAdapter，避免新群首帧闪一下旧消息；
        // 7. attachChannelListeners 用新 channelId 重装；
        // 8. initData 读新 channel 的 DB + 同步头像 / title / membership。
        //
        // 历史坑（YUJ-269 ReviewBot P0-2）：R1 版本先 reset 再 persist，editText 被
        // setText("") 后 persist 读到空串 → 草稿被空覆写；readMsgIds.clear() 后 persist
        // 检查 isNotEmpty 失败 → 已读回执永不触发；redDot=0 导致 clearUnread 失效。
        String oldChannelId = channelId;
        byte oldChannelType = channelType;

        detachChannelListeners(oldChannelId);

        // 先持久化旧 channel 编辑态（此时 editText / readMsgIds / redDot 还是旧值）。
        persistOldChannelEditState(oldChannelId, oldChannelType);

        setIntent(intent);
        initParam();
        // 再 reset 实例字段到新 channel 初值。
        resetPerChannelState();
        chatAdapter.setList(new ArrayList<>());
        attachChannelListeners();
        // Space / push 去重字段对齐当前 channel（onResume 仍会再设一次，此处先落地
        // 保证任意在 onResume 前触发的业务都看到新 channel）。
        WKUIKitApplication.getInstance().chattingChannelID = channelId;
        initData();
        if (WKBinder.isDebug) {
            Log.d("YUJ276-trace", "[T_ON_NEW_INTENT_END] channel=" + channelId
                    + " total=" + (SystemClock.uptimeMillis() - tNewIntent) + "ms");
        }
    }

    /**
     * YUJ-267 · Fix B：持久化上一个 channel 的编辑态（草稿 / 阅后即焚 / 浏览位置），
     * 对齐 {@link #onDestroy()}.{@code saveEditContent()} 的行为，避免复用时旧群
     * 的输入内容丢失。与 saveEditContent 的差别：这里不 dispose Activity 级资源，
     * 不 release 语音，只做 per-channel 数据落盘。
     *
     * <p>YUJ-270 P0-2：必须在 {@link #resetPerChannelState()} <b>之前</b>调用 —— 否则
     * editText / readMsgIds / redDot 都已被 reset 清零，persist 读到零值。
     *
     * <p>YUJ-270 P1-1：必须从 {@link #onNewIntent} 顶部把 {@code oldChannelType} 作为
     * 入参传下来 —— 不能在方法内用 {@code WKChannelManager.getChannel()} 探测，否则
     * 子区（{@link WKChannelType#COMMUNITY_TOPIC}）在 PERSONAL/GROUP 都查不到，会兜底
     * 到 {@code this.channelType}（initParam 已改成新值）→ 对旧子区用错类型写
     * coverExtra / clearUnread。
     *
     * @param oldChannelId   旧 channelId（onNewIntent 入口快照）
     * @param oldChannelType 旧 channelType（onNewIntent 入口快照，<b>不要</b>从 SDK 探测）
     */
    private void persistOldChannelEditState(String oldChannelId, byte oldChannelType) {
        if (TextUtils.isEmpty(oldChannelId)) return;

        // 草稿 / 浏览位置需要 adapter 有数据才有意义；没数据就只处理 unread / read-msg。
        long keepMsgSeq = 0;
        int offsetY = 0;
        String content = chatPanelManager != null && chatPanelManager.getEditText() != null
                && chatPanelManager.getEditText().getText() != null
                ? chatPanelManager.getEditText().getText().toString() : "";

        if (chatAdapter != null && WKReader.isNotEmpty(chatAdapter.getData()) && linearLayoutManager != null) {
            int firstItemPosition = linearLayoutManager.findFirstVisibleItemPosition();
            int endItemPosition = linearLayoutManager.findLastVisibleItemPosition();
            if (endItemPosition != chatAdapter.getData().size() - 1) {
                WKMsg msg = chatAdapter.getFirstVisibleItem(firstItemPosition);
                if (msg != null) {
                    keepMsgSeq = msg.messageSeq;
                    int index = chatAdapter.getFirstVisibleItemIndex(firstItemPosition);
                    View view = linearLayoutManager.findViewByPosition(index);
                    if (view != null) {
                        offsetY = view.getTop();
                    }
                }
            }
        }

        // 关键：用入参 oldChannelType，不探测 SDK。子区类型不会被误降成 GROUP/PERSONAL。
        MsgModel.getInstance().clearUnread(oldChannelId, oldChannelType, redDot, null);
        MsgModel.getInstance().updateCoverExtra(oldChannelId, oldChannelType, browseTo, keepMsgSeq, offsetY, content);
        MsgModel.getInstance().deleteFlameMsg();
        // 复用前清 read msg 上报（onDestroy 会做一次，此处提前做一次）。
        if (WKReader.isNotEmpty(readMsgIds)) {
            EndpointManager.getInstance().invoke("read_msg", new ReadMsgMenu(oldChannelId, oldChannelType, readMsgIds));
            readMsgIds.clear();
        }
    }

    /**
     * YUJ-298 / YUJ-305 P0-2：持久化<b>当前</b> channel 的编辑态。与
     * {@link #persistOldChannelEditState(String, byte)} 的区别：这里读的是 Activity
     * 字段（channelId / channelType / readMsgIds / redDot / editText），不接入参 —
     * 调用时必须保证还没切 channel（soft-back / 同频道短路 / finish 前都是这种状态）。
     *
     * <p>触发场景：
     * <ul>
     *   <li>soft-back：{@link #setBackListener()} → goBackToList 之前（不走 onDestroy）。</li>
     *   <li>同频道短路：{@link #onNewIntent(Intent)} 里 oldChannelId == newChannelId 分支，
     *       进入前 flush 一次，避免被新 extras（转发 payload / 跳消息 seq）打断时草稿丢失。</li>
     *   <li>{@link #finish()} 兜底：swipe-back 不走 onBackPressed 分发链会直接 finish，
     *       saveEditContent 放在 super.finish() 之后可能赶不上 onStop 清 chattingChannelID
     *       的竞态；这里在 super.finish() 之前再 flush 一次，双保险。</li>
     * </ul>
     *
     * <p>和 {@link #saveEditContent()} 的区别：
     * <ul>
     *   <li>saveEditContent 只落盘草稿 / 浏览位置 / 未读清理，不处理 readMsgIds。</li>
     *   <li>本方法同时 flush readMsgIds（避免 soft-back 路径阅后上报积压）。</li>
     *   <li>本方法在 chatAdapter 为空时仍会 flush 草稿（没有可见消息但用户在输入框打过字）。</li>
     * </ul>
     *
     * 幂等：多次调用对同一时刻状态无副作用。
     */
    private void persistCurrentChannelEditState() {
        if (TextUtils.isEmpty(channelId)) return;

        // 浏览位置 / 草稿需要 adapter 有数据才能计算 keepMsgSeq；但草稿本身和 readMsgIds
        // 在 adapter 空时也应落盘（用户可能刚开会话、还没消息就打了字）。
        long keepMsgSeq = 0;
        int offsetY = 0;
        String content = chatPanelManager != null && chatPanelManager.getEditText() != null
                && chatPanelManager.getEditText().getText() != null
                ? chatPanelManager.getEditText().getText().toString() : "";

        if (chatAdapter != null && WKReader.isNotEmpty(chatAdapter.getData()) && linearLayoutManager != null) {
            int firstItemPosition = linearLayoutManager.findFirstVisibleItemPosition();
            int endItemPosition = linearLayoutManager.findLastVisibleItemPosition();
            if (endItemPosition != chatAdapter.getData().size() - 1) {
                WKMsg msg = chatAdapter.getFirstVisibleItem(firstItemPosition);
                if (msg != null) {
                    keepMsgSeq = msg.messageSeq;
                    int index = chatAdapter.getFirstVisibleItemIndex(firstItemPosition);
                    View view = linearLayoutManager.findViewByPosition(index);
                    if (view != null) {
                        offsetY = view.getTop();
                    }
                }
            }
        }

        MsgModel.getInstance().clearUnread(channelId, channelType, redDot, null);
        MsgModel.getInstance().updateCoverExtra(channelId, channelType, browseTo, keepMsgSeq, offsetY, content);
        MsgModel.getInstance().deleteFlameMsg();
        if (WKReader.isNotEmpty(readMsgIds)) {
            EndpointManager.getInstance().invoke("read_msg", new ReadMsgMenu(channelId, channelType, readMsgIds));
            readMsgIds.clear();
        }
    }

    /**
     * YUJ-305 P0-1：把 Intent 上挂的"跳消息 / 转发 payload / 搜索定位"类 extras 分发到
     * 当前 UI。仅在 {@link #onNewIntent(Intent)} 的同频道短路路径使用 —— 跨频道路径会
     * 走完整的 resetPerChannelState + initData，extras 被 {@link #initParam()} /
     * {@link #initData()} 自然读走，不需要本方法。
     *
     * <p>覆盖矩阵（与冷路径 {@link #initParam()} / {@link #initData()} 对齐）：
     * <ul>
     *   <li>{@code msgContentList}：转发到当前会话 → 弹 {@code showChatConfirmDialog}。</li>
     *   <li>{@code tipsOrderSeq} / {@code aroundMsgSeq}：通知点击 / 搜索结果定位 →
     *       滚动到目标 orderSeq 并高亮。若目标已在 adapter 内走 scrollToPositionWithOffset；
     *       不在则调 {@link #getData(int, boolean, long, boolean)} 以 aroundMsgSeq 拉取。</li>
     * </ul>
     *
     * <p>处理完后从 Intent 里移除这些 extras（consume 语义），避免后续如果再次走短路
     * 路径（多次打开同会话）时重复弹框 / 重复滚动到陈旧 seq。
     */
    private void applyIntentExtrasForReuse(@NonNull Intent intent) {
        // 1. 转发 payload —— 对齐 initParam() 里的 msgContentList 分支。
        if (intent.hasExtra("msgContentList")) {
            List<WKMessageContent> msgContentList = intent.getParcelableArrayListExtra("msgContentList");
            // YUJ-311 防御 · 消费 extras 必须无条件，否则同频道短路再次触发时会 replay
            // 一个我们本应丢弃的 payload。改成先 consume 再决定是否分发。
            intent.removeExtra("msgContentList");
            if (WKReader.isNotEmpty(msgContentList)) {
                // YUJ-311 防御 · getChannel 可能返回 null（SDK cache 未热 / channel 被
                // evict / 跨 Space 冷启 race）。baseline 直接把 null 塞 channelList
                // 进 showChatConfirmDialog，对话框的 adapter 在 bind 头像 / 名字时
                // 会触发 NPE。短路路径放弃弹框比闪退好——用户再点一次该 extras
                // 已 consume，会重新从业务入口带完整 channel 对象走冷路径。
                WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);
                if (channel != null) {
                    List<WKChannel> channelList = new ArrayList<>();
                    channelList.add(channel);
                    WKUIKitApplication.getInstance().showChatConfirmDialog(this, channelList, msgContentList, (list1, messageContentList) -> {
                        List<SendMsgEntity> msgList = new ArrayList<>();
                        WKSendOptions options = new WKSendOptions();
                        options.setting.receipt = getChatChannelInfo().receipt;
                        for (int i = 0, size = messageContentList.size(); i < size; i++) {
                            msgList.add(new SendMsgEntity(messageContentList.get(i), channel, options));
                        }
                        WKSendMsgUtils.getInstance().sendMessages(msgList);
                    });
                }
            }
        }

        // 2. 跳转 / 定位目标消息 —— tipsOrderSeq 优先，退化到 aroundMsgSeq。
        long targetOrderSeq = 0;
        if (intent.hasExtra("tipsOrderSeq")) {
            targetOrderSeq = intent.getLongExtra("tipsOrderSeq", 0);
        }
        if (targetOrderSeq == 0 && intent.hasExtra("aroundMsgSeq")) {
            targetOrderSeq = intent.getLongExtra("aroundMsgSeq", 0);
        }
        if (targetOrderSeq > 0) {
            scrollToMessageForReuse(targetOrderSeq);
            intent.removeExtra("tipsOrderSeq");
            intent.removeExtra("aroundMsgSeq");
        }
    }

    /**
     * YUJ-305 P0-1：同频道短路路径下"滚动到目标消息"。
     *
     * <p>目标若已在 adapter 缓存内，直接 scrollToPositionWithOffset + 高亮，
     * 不触发 DB 重读。否则按冷路径对齐，更新 tipsOrderSeq snapshot + 清 render
     * flag，让 getData(... aroundMsgSeq ...) 接手拉取并由 applyDataToAdapter
     * 完成滚动和高亮（复用 YUJ-256 snapshot 机制）。
     */
    private void scrollToMessageForReuse(long targetOrderSeq) {
        if (chatAdapter == null || linearLayoutManager == null) return;
        if (mHelper != null) {
            mHelper.hookSystemBackByPanelSwitcher();
        }
        // 直接用 adapter 级别高亮（不受 item 替换影响）+ 滚动到目标
        chatAdapter.setPendingHighlightOrderSeq(targetOrderSeq);
        int index = chatAdapter.findPositionByOrderSeq(targetOrderSeq);
        if (index >= 0) {
            // 消息在 adapter 中：滚动到可见位置，触发 rebind 让高亮生效
            if (index >= chatAdapter.getItemCount() - 3) {
                wkVBinding.recyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
            } else {
                linearLayoutManager.scrollToPositionWithOffset(index, wkVBinding.recyclerView.getHeight() / 3);
            }
            chatAdapter.notifyItemChanged(index);
        } else {
            // 消息不在 adapter 中：重新加载
            chatAdapter.setList(new ArrayList<>());
            unreadStartMsgOrderSeq = 0;
            tipsOrderSeq = targetOrderSeq;
            unreadStartSnapshotOrderSeq = 0;
            tipsSnapshotOrderSeq = targetOrderSeq;
            lastPreviewMsgOrderSeq = 0;
            hasRenderedPreview = false;
            userHasScrolled = false;
            hasShownTips = false;
            getData(0, true, targetOrderSeq, false);
            isCanLoadMore = true;
        }
    }

    /**
     * YUJ-267 · Fix B · reset matrix：per-channel 一次性状态必须在切换时清零。
     * 遗漏会导致跨群串红点 / 串未读分割线 / 串 reminder / 串键盘高度等。
     *
     * 参见 issue / onDestroy 的 saveEditContent 流程 — 所有初始化值来自 onCreate
     * 初值或 initData 从 intent 读入的快照。
     */
    private void resetPerChannelState() {
        // scroll / preview 相关
        isShowHistory = false;
        isSyncLastMsg = false;
        isToEnd = true;
        isViewingPicture = false;
        hasRenderedPreview = false;
        userHasScrolled = false;
        hasShownTips = false;
        hasPositionedUnread = false;
        // unread 位置定位 snapshot（initData 会从 intent 重写；此处先清零避免上一个
        // channel 残留的 snapshot 命中新 channel 的 applyDataToAdapter）
        unreadStartSnapshotOrderSeq = 0;
        tipsSnapshotOrderSeq = 0;
        unreadStartMsgOrderSeq = 0;
        tipsOrderSeq = 0;
        lastPreviewMsgOrderSeq = 0;
        // 红点 / 滚动 / 展示标记
        redDot = 0;
        keepOffsetY = 0;
        lastVisibleMsgSeq = 0;
        isCanLoadMore = false;
        isUpdateRedDot = true;
        // time divider 去重
        lastShowTimeUpdate = 0;
        lastShowTimeIndex = -1;
        // pinned / calling / tip 条幅
        isShowPinnedView = false;
        isShowCallingView = false;
        isTipMessage = false;
        // thread
        hasJoinedThread = false;
        // group meta
        count = 0;
        groupType = WKGroupType.normalGroup;
        // 编辑态
        replyWKMsg = null;
        editMsg = null;
        // per-channel 集合
        reminderList.clear();
        groupApproveList.clear();
        reminderIds.clear();
        readMsgIds.clear();
        // upload gate
        isUploadReadMsg = true;
        // rendering frame state
        isShowChatActivity = true;

        // YUJ-270 P2-1 · SwipeRefresh / LoadMore 忙位：旧 channel 刷新中途切群时必须清，
        // 否则新 channel 的 loadMsgs / loadMoreMsgs 首帧会被 `isRefreshLoading || !isCanRefresh`
        // 门禁挡掉，滚到顶 / 到底不再触发请求。
        isRefreshLoading = false;
        isMoreLoading = false;
        isCanRefresh = true;

        // YUJ-270 P2-2 · RxJava subscription：旧 channel 的 media 下载 / 任务订阅如果不
        // dispose，切到新 channel 后订阅叠加 → 回调错绑（新 channel 收到旧 channel 的
        // onNext）+ 内存 leak。
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
        disposable = null;

        // YUJ-270 P2-3 · 空态补白：unfilledHeight 是按当前 channel 的列表高度 - 消息总高
        // 算出来的，跨 channel 直接残留会让空态滚动距离算错（上下错位几行高）。
        unfilledHeight = 0;

        // 重置红点 UI（彻底清）
        wkVBinding.chatUnreadLayout.newMsgLayout.setVisibility(View.GONE);
        numberTextView.setNumber(0, false);
        CommonAnim.getInstance().showOrHide(numberTextView, false, false);
        // 键盘 / panel：新 channel 不残留旧键盘
        if (chatPanelManager != null) {
            chatPanelManager.resetToolBar();
            if (chatPanelManager.getEditText() != null) {
                chatPanelManager.getEditText().setText("");
            }
        }
    }

    /**
     * YUJ-267 · Fix B · 注册所有以 channelId 为 key 的 SDK 监听 + EndpointManager
     * setMethod。onCreate → initListener() 走一次；onNewIntent 复用路径 detach →
     * attach 走一次。
     */
    private void attachChannelListeners() {
        //监听频道改变通知
        WKIM.getInstance().getChannelManager().addOnRefreshChannelInfo(channelId, (channel, isEnd) -> {
            if (channel == null) return;
            if (channel.channelID.equals(channelId) && channel.channelType == channelType) { //同一个会话
                showChannelName(channel);
                if (channelType == WKChannelType.COMMUNITY_TOPIC) {
                    wkVBinding.topLayout.avatarView.defaultAvatarTv.setVisibility(View.GONE);
                    wkVBinding.topLayout.avatarView.imageView.setVisibility(View.VISIBLE);
                    wkVBinding.topLayout.avatarView.imageView.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
                    wkVBinding.topLayout.avatarView.imageView.setPadding(0, 0, 0, 0);
                    wkVBinding.topLayout.avatarView.imageView.setImageResource(R.mipmap.ic_thread);
                } else {
                    wkVBinding.topLayout.avatarView.showAvatar(channel);
                }
                EndpointManager.getInstance().invoke("show_avatar_other_info", new AvatarOtherViewMenu(wkVBinding.topLayout.otherLayout, channel, wkVBinding.topLayout.avatarView, true));
                //用户在线状态
                if (channel.channelType == WKChannelType.PERSONAL) {
                    setOnlineView(channel);
                } else {
                    if (channel.remoteExtraMap != null) {
                        Object memberCountObject = channel.remoteExtraMap.get(WKChannelCustomerExtras.memberCount);
                        if (memberCountObject instanceof Integer) {
                            int count = (int) memberCountObject;
                            wkVBinding.topLayout.subtitleTv.setText(String.format(getString(R.string.group_member), count));
                        }
                        Object onlineCountObject = channel.remoteExtraMap.get(WKChannelCustomerExtras.onlineCount);
                        if (onlineCountObject instanceof Integer) {
                            int onlineCount = (int) onlineCountObject;
                            if (onlineCount > 0) {
                                wkVBinding.topLayout.subtitleCountTv.setVisibility(View.VISIBLE);
                                wkVBinding.topLayout.subtitleCountTv.setText(String.format(getString(R.string.online_count), onlineCount));
                            }
                        }
                    }
                }
                EndpointManager.getInstance().invoke("set_chat_bg", new SetChatBgMenu(channelId, channelType, wkVBinding.imageView, wkVBinding.rootView, wkVBinding.blurView));
            } else {
                for (int i = 0, size = chatAdapter.getData().size(); i < size; i++) {
                    if (TextUtils.isEmpty(chatAdapter.getData().get(i).wkMsg.fromUID)) continue;
                    boolean isRefresh = false;
                    if (chatAdapter.getData().get(i).wkMsg.fromUID.equals(channel.channelID) && channel.channelType == WKChannelType.PERSONAL) {
                        chatAdapter.getData().get(i).wkMsg.setFrom(channel);
                        isRefresh = true;
                    }
                    if (chatAdapter.getData().get(i).wkMsg.getMemberOfFrom() != null && chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberUID.equals(channel.channelID) && channel.channelType == WKChannelType.PERSONAL) {
                        chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberRemark = channel.channelRemark;
                        chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberName = channel.channelName;
                        chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberAvatar = channel.avatar;
                        chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberAvatarCacheKey = channel.avatarCacheKey;
                        isRefresh = true;
                    }
                    if (chatAdapter.getData().get(i).wkMsg.baseContentMsgModel != null && WKReader.isNotEmpty(chatAdapter.getData().get(i).wkMsg.baseContentMsgModel.entities)) {
                        for (WKMsgEntity entity : chatAdapter.getData().get(i).wkMsg.baseContentMsgModel.entities) {
                            if (entity.type.equals(ChatContentSpanType.getMention()) && !TextUtils.isEmpty(entity.value) && entity.value.equals(channel.channelID)) {
                                isRefresh = true;
                                chatAdapter.getData().get(i).formatSpans(ChatActivity.this, chatAdapter.getData().get(i).wkMsg);
                                break;
                            }
                        }
                    }
                    if (isRefresh) {
                        chatAdapter.getData().get(i).isRefreshAvatarAndName = true;
                        chatAdapter.notifyItemChanged(i, chatAdapter.getData().get(i));
                    }
                }
            }
        });

        //监听频道成员信息改变通知
        WKIM.getInstance().getChannelMembersManager().addOnRefreshChannelMemberInfo(channelId, (channelMember, isEnd) -> {
            if (channelMember != null && !TextUtils.isEmpty(channelMember.channelID)) {
                if (channelMember.channelID.equals(channelId) && channelMember.channelType == channelType) {
                    if (channelMember.channelType == WKChannelType.PERSONAL) {
                        String name = channelMember.memberRemark;
                        if (TextUtils.isEmpty(name)) name = channelMember.memberName;
                        wkVBinding.topLayout.titleCenterTv.setText(name);
                    } else {
                        //成员名字改变
                        for (int i = 0, size = chatAdapter.getData().size(); i < size; i++) {
                            if (chatAdapter.getData().get(i).wkMsg != null && chatAdapter.getData().get(i).wkMsg.getMemberOfFrom() != null && !TextUtils.isEmpty(chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberUID) && chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberUID.equals(channelMember.memberUID)) {
                                chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberName = channelMember.memberName;
                                chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberRemark = channelMember.memberRemark;
                                chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberAvatar = channelMember.memberAvatar;
                                chatAdapter.getData().get(i).isRefreshAvatarAndName = true;
                                chatAdapter.notifyItemChanged(i, chatAdapter.getData().get(i));
                            }
                        }
                    }
                }
            }
            if (isEnd) {
                checkLoginUserInGroupStatus();
            }
        });

        //监听移除频道成员
        WKIM.getInstance().getChannelMembersManager().addOnRemoveChannelMemberListener(channelId, list -> {
            if (WKReader.isNotEmpty(list) && !TextUtils.isEmpty(list.get(0).channelID) && list.get(0).channelID.equals(channelId) && list.get(0).channelType == channelType) {
                if (groupType == WKGroupType.normalGroup) {
                    count = WKIM.getInstance().getChannelMembersManager().getMemberCount(channelId, channelType);
                    wkVBinding.topLayout.subtitleTv.setText(String.format(getString(R.string.group_member), count));
                }
                //查询登录用户是否在本群
                checkLoginUserInGroupStatus();
                WKRobotModel.getInstance().syncRobotData(getChatChannelInfo());
            }
        });
        //监听添加频道成员
        WKIM.getInstance().getChannelMembersManager().addOnAddChannelMemberListener(channelId, list -> {
            if (WKReader.isNotEmpty(list) && !TextUtils.isEmpty(list.get(0).channelID) && list.get(0).channelID.equals(channelId) && list.get(0).channelType == channelType && groupType == WKGroupType.normalGroup) {
                count = WKIM.getInstance().getChannelMembersManager().getMemberCount(channelId, channelType);
                wkVBinding.topLayout.subtitleTv.setText(String.format(getString(R.string.group_member), count));
                WKRobotModel.getInstance().syncRobotData(getChatChannelInfo());
                checkLoginUserInGroupStatus();
            }
        });
        //监听删除消息
        WKIM.getInstance().getMsgManager().addOnDeleteMsgListener(channelId, msg -> {
            if (msg != null) {
                removeMsg(msg);
            }
        });
        // 命令消息监听
        WKIM.getInstance().getCMDManager().addCmdListener(channelId, wkCmd -> {
            if (wkCmd == null || TextUtils.isEmpty(wkCmd.cmdKey)) return;
            // 监听正在输入
            switch (wkCmd.cmdKey) {
                case WKCMDKeys.wk_typing -> typing(wkCmd);
                case WKCMDKeys.wk_unreadClear -> {
                    if (wkCmd.paramJsonObject.has("channel_id") && wkCmd.paramJsonObject.has("channel_type")) {
                        String channelId = wkCmd.paramJsonObject.optString("channel_id");
                        int channelType = wkCmd.paramJsonObject.optInt("channel_type");
                        int unreadCount = wkCmd.paramJsonObject.optInt("unread");
                        if (channelId.equals(this.channelId) && channelType == this.channelType) {
                            if (unreadCount < redDot) {
                                this.redDot = unreadCount;
                                wkVBinding.chatUnreadLayout.newMsgLayout.post(() -> CommonAnim.getInstance().showOrHide(wkVBinding.chatUnreadLayout.newMsgLayout, redDot > 0, true, false));
                            }
                        }
                    }
                }
                case "sync_channel_state" -> {
                    String sourceChannelId = wkCmd.paramJsonObject.optString("channel_id");
                    int sourceChannelType = wkCmd.paramJsonObject.optInt("channel_type");
                    if (sourceChannelId.equals(channelId) && sourceChannelType == channelType) {
                        getChannelState();
                    }
                }
            }
        });

        //监听消息刷新
        WKIM.getInstance().getMsgManager().addOnRefreshMsgListener(channelId, (wkMsg, left) -> {
            if (wkMsg.remoteExtra.isMutualDeleted == 1) {
                removeMsg(wkMsg);
                return;
            }
            refreshMsg(wkMsg);
        });
        //监听发送消息返回
        WKIM.getInstance().getMsgManager().addOnSendMsgCallback(channelId, this::sendMsgInserted);

        //监听新消息
        WKIM.getInstance().getMsgManager().addOnNewMsgListener(channelId, this::receivedMessages);
        //监听清空聊天记录
        WKIM.getInstance().getMsgManager().addOnClearMsgListener(channelId, (channelID, channelType, fromUID) -> {
            if (!TextUtils.isEmpty(channelID) && ChatActivity.this.channelId.equals(channelID) && ChatActivity.this.channelType == channelType) {
                if (TextUtils.isEmpty(fromUID)) {
                    chatAdapter = new ChatAdapter(ChatActivity.this, ChatAdapter.AdapterType.normalMessage);
                    chatAdapter.setOnMessageDisplayedListener((item, itemView) -> {
                        if (messageEffectManager != null) {
                            messageEffectManager.onMessageVisible(item.wkMsg, itemView);
                        }
                    });
                    wkVBinding.recyclerView.setAdapter(chatAdapter);
                } else {
                    for (int i = 0; i < chatAdapter.getData().size(); i++) {
                        if (chatAdapter.getData().get(i).wkMsg != null && !TextUtils.isEmpty(chatAdapter.getData().get(i).wkMsg.fromUID) && chatAdapter.getData().get(i).wkMsg.fromUID.equals(fromUID)) {
                            chatAdapter.removeAt(i);
                            i--;
                        }
                    }
                }
            }

        });

        WKIM.getInstance().getReminderManager().addOnNewReminderListener(channelId, this::resetReminder);
        EndpointManager.getInstance().setMethod(channelId, EndpointCategory.wkExitChat, object -> {
            if (object != null) {
                WKChannel channel = (WKChannel) object;
                if (channelId.equals(channel.channelID) && channel.channelType == channelType) {
                    finish();
                }
            }
            return null;
        });
        WKIM.getInstance().getConnectionManager().addOnConnectionStatusListener(channelId, (i, s) -> {
            if (i == WKConnectStatus.syncCompleted && WKUIKitApplication.getInstance().isRefreshChatActivityMessage) {
                WKUIKitApplication.getInstance().isRefreshChatActivityMessage = false;
                int maxOrderSeq = WKIM.getInstance().getMsgManager().getMaxOrderSeqWithChannel(channelId, channelType);
                long tempMaxOrderSeq = 0;
                if (chatAdapter != null && chatAdapter.getLastMsg() != null) {
                    tempMaxOrderSeq = chatAdapter.getLastMsg().orderSeq;
                }
                if (maxOrderSeq > tempMaxOrderSeq && !hasPositionedUnread) {
                    // YUJ-258 P1-NEW-2: reset snapshots + render flags before
                    // the reconnect refresh so the viewport actually scrolls
                    // to end after airplane/sync-complete, rather than being
                    // pinned to the previous preview position.
                    unreadStartSnapshotOrderSeq = 0;
                    tipsSnapshotOrderSeq = 0;
                    hasRenderedPreview = false;
                    userHasScrolled = false;
                    hasShownTips = false;
                    getData(0, true, maxOrderSeq, true);
                }
            }
        });
        EndpointManager.getInstance().setMethod(channelId, EndpointCategory.refreshProhibitWord, object -> {
            if (WKReader.isEmpty(chatAdapter.getData())) {
                return 1;
            }
            for (int i = 0, size = chatAdapter.getData().size(); i < size; i++) {
                if (chatAdapter.getData().get(i).wkMsg != null && chatAdapter.getData().get(i).wkMsg.type == WKContentType.WK_TEXT) {
                    WKIMUtils.getInstance().resetMsgProhibitWord(chatAdapter.getData().get(i).wkMsg);
                    chatAdapter.getData().get(i).formatSpans(ChatActivity.this, chatAdapter.getData().get(i).wkMsg);
                    chatAdapter.notifyItemChanged(i);
                }
            }
            return 1;
        });
    }

    /**
     * YUJ-267 · Fix B · 用旧 channelId 作 key 卸所有 channel-keyed 监听。与
     * {@link #onDestroy()} 里的 remove 块保持同步——onDestroy 调用的是当前 channelId；
     * onNewIntent 调用此方法时 channelId 尚未更新，传入的 oldChannelId 即当前值。
     */
    private void detachChannelListeners(String oldChannelId) {
        if (TextUtils.isEmpty(oldChannelId)) return;
        WKIM.getInstance().getConnectionManager().removeOnConnectionStatusListener(oldChannelId);
        WKIM.getInstance().getChannelManager().removeRefreshChannelInfo(oldChannelId);
        WKIM.getInstance().getChannelMembersManager().removeRefreshChannelMemberInfo(oldChannelId);
        WKIM.getInstance().getChannelMembersManager().removeRemoveChannelMemberListener(oldChannelId);
        WKIM.getInstance().getChannelMembersManager().removeAddChannelMemberListener(oldChannelId);
        WKIM.getInstance().getMsgManager().removeDeleteMsgListener(oldChannelId);
        WKIM.getInstance().getMsgManager().removeRefreshMsgListener(oldChannelId);
        WKIM.getInstance().getMsgManager().removeSendMsgCallBack(oldChannelId);
        WKIM.getInstance().getMsgManager().removeNewMsgListener(oldChannelId);
        WKIM.getInstance().getMsgManager().removeNewMsgListener("thread_count_" + oldChannelId);
        WKIM.getInstance().getMsgManager().removeClearMsg(oldChannelId);
        WKIM.getInstance().getCMDManager().removeCmdListener(oldChannelId);
        WKIM.getInstance().getReminderManager().removeNewReminderListener(oldChannelId);
        EndpointManager.getInstance().remove(oldChannelId);
    }

    private void initData() {
        startTimer();
        EndpointManager.getInstance().invoke(EndpointSID.openChatPage, getChatChannelInfo());
        //获取网络频道信息
        WKIM.getInstance().getChannelManager().fetchChannelInfo(channelId, channelType);
        MsgModel.getInstance().syncExtraMsg(channelId, channelType);
        WKRobotModel.getInstance().syncRobotData(getChatChannelInfo());
        getChannelState();

        // YUJ-242: do NOT clear the adapter here. The previous setList(empty)
        // caused a visible white-screen flash while getData() is waiting on
        // sync. applyDataToAdapter(isSetNewData=true) replaces data anyway,
        // and on onNewIntent (same Activity reused for a different channel)
        // the new channel's data will overwrite via setNewInstance(list).
        // chatAdapter.setList(new ArrayList<>());
        if (WKSystemAccount.isSystemAccount(channelId) || channelType == WKChannelType.CUSTOMER_SERVICE) {
            CommonAnim.getInstance().showOrHide(callIV, false, false);
        }
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);

        String avatarKey = "";
        if (channel != null) {
            wkVBinding.topLayout.categoryLayout.removeAllViews();
            avatarKey = channel.avatarCacheKey;
            if (channel.remoteExtraMap != null && channel.remoteExtraMap.containsKey(WKChannelExtras.groupType)) {
                Object object = channel.remoteExtraMap.get(WKChannelExtras.groupType);
                if (object instanceof Integer) {
                    groupType = (int) object;
                }
            }
            if (!TextUtils.isEmpty(channel.category)) {
                if (channel.category.equals(WKSystemAccount.accountCategorySystem)) {
                    wkVBinding.topLayout.categoryLayout.addView(Theme.getChannelCategoryTV(this, getString(R.string.official), ContextCompat.getColor(this, R.color.transparent), ContextCompat.getColor(this, R.color.reminderColor), ContextCompat.getColor(this, R.color.reminderColor)), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
                }
                if (channel.category.equals(WKSystemAccount.accountCategoryCustomerService)) {
                    wkVBinding.topLayout.categoryLayout.addView(Theme.getChannelCategoryTV(this, getString(R.string.customer_service), Theme.colorAccount, ContextCompat.getColor(this, R.color.white), Theme.colorAccount), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
                }
                if (channel.category.equals(WKSystemAccount.accountCategoryVisitor)) {
                    wkVBinding.topLayout.categoryLayout.addView(Theme.getChannelCategoryTV(this, getString(R.string.visitor), ContextCompat.getColor(this, R.color.transparent), ContextCompat.getColor(this, R.color.colorAccent), ContextCompat.getColor(this, R.color.colorAccent)), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
                }
                if (channel.category.equals(WKSystemAccount.channelCategoryOrganization)) {
                    wkVBinding.topLayout.categoryLayout.addView(Theme.getChannelCategoryTV(this, getString(R.string.all_staff), ContextCompat.getColor(this, R.color.category_org_bg), ContextCompat.getColor(this, R.color.category_org_text), ContextCompat.getColor(this, R.color.transparent)), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
                }
                if (channel.category.equals(WKSystemAccount.channelCategoryDepartment)) {
                    wkVBinding.topLayout.categoryLayout.addView(Theme.getChannelCategoryTV(this, getString(R.string.department), ContextCompat.getColor(this, R.color.category_org_bg), ContextCompat.getColor(this, R.color.category_org_text), ContextCompat.getColor(this, R.color.transparent)), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
                }
            }
            showChannelName(channel);
            if (channel.robot == 1) {
                wkVBinding.topLayout.categoryLayout.addView(Theme.getChannelCategoryTV(this, getString(R.string.bot), ContextCompat.getColor(this, R.color.colorAccent), ContextCompat.getColor(this, R.color.white), ContextCompat.getColor(this, R.color.colorAccent)), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 1, 0));
            }
            EndpointManager.getInstance().invoke("show_avatar_other_info", new AvatarOtherViewMenu(wkVBinding.topLayout.otherLayout, channel, wkVBinding.topLayout.avatarView, true));
        }
        if (channelType == WKChannelType.COMMUNITY_TOPIC) {
            wkVBinding.topLayout.avatarView.defaultAvatarTv.setVisibility(View.GONE);
            wkVBinding.topLayout.avatarView.imageView.setVisibility(View.VISIBLE);
            wkVBinding.topLayout.avatarView.imageView.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
            wkVBinding.topLayout.avatarView.imageView.setPadding(0, 0, 0, 0);
            wkVBinding.topLayout.avatarView.imageView.setImageResource(R.mipmap.ic_thread);
        } else {
            wkVBinding.topLayout.avatarView.showAvatar(channelId, channelType, avatarKey);
        }

        //如果是群聊就同步群成员信息
        if (channelType == WKChannelType.GROUP) {
            if (groupType == WKGroupType.normalGroup) {
                GroupModel.getInstance().groupMembersSync(channelId, (code, msg) -> {
                    if (code == HttpResponseCode.success) {
                        WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, loginUID);
                        hideOrShowRightView(member == null || member.isDeleted != 1);
                        WKRobotModel.getInstance().syncRobotData(getChatChannelInfo());
                        chatPanelManager.showOrHideForbiddenView();
                    }
                });
            } else {
                UserModel.getInstance().getUserInfo(WKConfig.getInstance().getUid(), channelId, null);
            }
            //获取sdk频道信息
            if (channel != null) {
                count = WKIM.getInstance().getChannelMembersManager().getMemberCount(channelId, channelType);
                showChannelName(channel);
                // showNickName = channel.showNick == 1;
                if (channel.forbidden == 1) {
                    chatPanelManager.showOrHideForbiddenView();
                }
                if (channel.status == WKChannelStatus.statusDisabled) {
                    chatPanelManager.showBan();
                } else {
                    chatPanelManager.hideBan();
                }
            }

            WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, loginUID);
            hideOrShowRightView(member == null || member.isDeleted == 0);
            if (groupType == WKGroupType.normalGroup) {
                wkVBinding.topLayout.subtitleTv.setText(String.format(getString(R.string.group_member), count));
            }
            wkVBinding.topLayout.subtitleView.setVisibility(View.VISIBLE);
            chatPanelManager.showOrHideForbiddenView();
            // 获取群内子区的消息数量，更新缓存（对齐 iOS fetchThreadMessageCounts）
            fetchThreadMessageCounts();
        } else if (channelType == WKChannelType.COMMUNITY_TOPIC) {
            // 子区：同步成员 + 显示子区标题
            ThreadModel.getInstance().syncThreadMembers(channelId, null);
            // 同步父群成员，确保子区 @mention 能查到成员列表
            String[] parsed = ThreadModel.getInstance().parseChannelId(channelId);
            if (parsed != null) {
                GroupModel.getInstance().groupMembersSync(parsed[0], null);
            }
            hideOrShowRightView(true);
            if (channel != null) {
                showChannelName(channel);
                // 子区副标题可显示父群名
                if (channel.remoteExtraMap != null && channel.remoteExtraMap.containsKey("parentGroupNo")) {
                    String parentGroupNo = (String) channel.remoteExtraMap.get("parentGroupNo");
                    WKChannel parentChannel = WKIM.getInstance().getChannelManager().getChannel(parentGroupNo, WKChannelType.GROUP);
                    if (parentChannel != null) {
                        wkVBinding.topLayout.subtitleTv.setText(parentChannel.channelName);
                        wkVBinding.topLayout.subtitleView.setVisibility(View.VISIBLE);
                    }
                }
            }
            // 隐藏通话按钮
            if (callIV != null) {
                callIV.setVisibility(View.GONE);
            }
        } else {
            hideOrShowRightView(true);
            wkVBinding.topLayout.subtitleCountTv.setVisibility(View.GONE);
            if (channel != null) {
                setOnlineView(channel);
                showChannelName(channel);
            }
        }


        //定位消息
        if (getIntent().hasExtra("lastPreviewMsgOrderSeq")) {
            lastPreviewMsgOrderSeq = getIntent().getLongExtra("lastPreviewMsgOrderSeq", 0L);
            isCanLoadMore = lastPreviewMsgOrderSeq > 0;
        }
        if (getIntent().hasExtra("keepOffsetY")) {
            keepOffsetY = getIntent().getIntExtra("keepOffsetY", 0);
        }
        if (getIntent().hasExtra("redDot")) redDot = getIntent().getIntExtra("redDot", 0);
        if (getIntent().hasExtra("tipsOrderSeq")) {
            tipsOrderSeq = getIntent().getLongExtra("tipsOrderSeq", 0);
        }
        if (getIntent().hasExtra("unreadStartMsgOrderSeq")) {
            unreadStartMsgOrderSeq = getIntent().getLongExtra("unreadStartMsgOrderSeq", 0);
        }
        // YUJ-256 P1-2: take a snapshot of the positioning targets so the
        // second applyDataToAdapter (after sync) still inserts the divider
        // and scrolls correctly even though the source fields were zeroed
        // out during the first (preview) render.
        unreadStartSnapshotOrderSeq = unreadStartMsgOrderSeq;
        tipsSnapshotOrderSeq = tipsOrderSeq;
        hasRenderedPreview = false;
        userHasScrolled = false;
        hasShownTips = false;

        List<WKReminder> allReminder = WKIM.getInstance().getReminderManager().getReminders(channelId, channelType);
        if (WKReader.isNotEmpty(allReminder)) {
            for (WKReminder reminder : allReminder) {
                boolean isPublisher = !TextUtils.isEmpty(reminder.publisher) && reminder.publisher.equals(loginUID);
                if (reminder.type == WKMentionType.WKReminderTypeMentionMe && reminder.done == 0 && !isPublisher) {
                    reminderList.add(reminder);
                }
                if (reminder.type == WKMentionType.WKApplyJoinGroupApprove && reminder.done == 0) {
                    groupApproveList.add(reminder);
                }
            }
        }
        // 先获取聊天数据
        boolean isScrollToEnd = unreadStartMsgOrderSeq == 0 && lastPreviewMsgOrderSeq == 0;
        long aroundMsgSeq = 0;
        if (unreadStartMsgOrderSeq != 0) {
            aroundMsgSeq = unreadStartMsgOrderSeq;
            isCanLoadMore = true;
        }
        isUpdateRedDot = unreadStartMsgOrderSeq > 0;
        if (lastPreviewMsgOrderSeq != 0) aroundMsgSeq = lastPreviewMsgOrderSeq;
        if (tipsOrderSeq != 0) {
            aroundMsgSeq = tipsOrderSeq;
            isCanLoadMore = true;
        }
        if (aroundMsgSeq == 0 && getIntent().hasExtra("aroundMsgSeq")) {
            aroundMsgSeq = getIntent().getLongExtra("aroundMsgSeq", 0);
        }
        // tipsOrderSeq 场景用 isSetNewData=true，避免 merge 分支的 scrollToPositionWithOffset 干扰 tips 定位
        boolean isSetNewData = unreadStartMsgOrderSeq > 0 || tipsOrderSeq > 0;
        getData(lastPreviewMsgOrderSeq == 0 ? 0 : 1, isSetNewData, aroundMsgSeq, isScrollToEnd);

        //查询高光内容
        WKConversationMsgExtra extra = WKIM.getInstance().getConversationManager().getMsgExtraWithChannel(channelId, channelType);
        if (extra != null) {
            if (!TextUtils.isEmpty(extra.draft)) {
                chatPanelManager.setEditContent(extra.draft);
            }
            browseTo = extra.browseTo;
        }
        new Handler().postDelayed(() -> {
            resetRemindView();
            resetGroupApproveView();
        }, 150);

    }

    /**
     * 获取群内所有子区的消息数量，更新到全局缓存（对齐 iOS fetchThreadMessageCounts）
     */
    private void fetchThreadMessageCounts() {
        if (channelType != WKChannelType.GROUP) return;
        ThreadModel.getInstance().listThreads(channelId, (code, msg, list) -> {
            if (code == com.chat.base.net.HttpResponseCode.success && list != null) {
                for (com.chat.uikit.thread.service.entity.ThreadEntity t : list) {
                    if (!TextUtils.isEmpty(t.channel_id)) {
                        WKThreadCreatedContent.setMessageCount(t.channel_id, t.message_count);
                    }
                }
                refreshThreadCreatedCards();
            }
        });
        // 用新消息监听代替会话刷新监听，只在真正收到新消息时 +1
        String listenerKey = "thread_count_" + channelId;
        WKIM.getInstance().getMsgManager().addOnNewMsgListener(listenerKey, msgs -> {
            if (msgs == null) return;
            boolean needRefresh = false;
            for (WKMsg m : msgs) {
                if (m.channelType == WKChannelType.COMMUNITY_TOPIC
                        && m.channelID != null
                        && m.channelID.startsWith(channelId + "____")) {
                    WKThreadCreatedContent.incrementMessageCount(m.channelID);
                    needRefresh = true;
                }
            }
            if (needRefresh) {
                refreshThreadCreatedCards();
            }
        });
    }

    /**
     * 刷新聊天列表中所有子区创建卡片的消息数量
     */
    private void refreshThreadCreatedCards() {
        if (chatAdapter == null) return;
        int headerCount = chatAdapter.getHeaderLayoutCount();
        for (int i = 0; i < chatAdapter.getData().size(); i++) {
            WKUIChatMsgItemEntity item = chatAdapter.getData().get(i);
            if (item.wkMsg != null && item.wkMsg.type == WKContentType.threadCreated) {
                chatAdapter.notifyItemChanged(i + headerCount);
            }
        }
    }

    private void getChannelState() {
        WKCommonModel.getInstance().getChannelState(channelId, channelType, channelState -> {
            if (channelState != null) {
                if (channelType == WKChannelType.GROUP && channelState.online_count > 0) {
                    wkVBinding.topLayout.subtitleCountTv.setVisibility(View.VISIBLE);
                    wkVBinding.topLayout.subtitleCountTv.setText(String.format(getString(R.string.online_count), channelState.online_count));
                }
                if (channelType == WKChannelType.PERSONAL) {
                    return;
                }
                if (channelState.call_info == null || WKReader.isEmpty(channelState.call_info.getCalling_participants())) {
                    wkVBinding.callLayout.setVisibility(View.GONE);
                    isShowCallingView = false;
                    if (WKReader.isNotEmpty(chatAdapter.getData()) && chatAdapter.getData().get(0).wkMsg.type == WKContentType.spanEmptyView) {
                        if (!isShowPinnedView) {
                            chatAdapter.getData().remove(0);
                            chatAdapter.notifyItemRemoved(0);
                        } else {
                            chatAdapter.getData().get(0).wkMsg.messageSeq = getTopPinViewHeight();
                            chatAdapter.notifyItemChanged(0);
                        }
                    }
                } else {
                    Object object = EndpointManager.getInstance().invoke("show_calling_participants", new CallingViewMenu(this, channelState.call_info));
                    if (object != null) {
                        View view = (View) object;
                        wkVBinding.callLayout.removeAllViews();
                        wkVBinding.callLayout.addView(view, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                        wkVBinding.callLayout.setVisibility(View.VISIBLE);
                        isShowCallingView = true;
                        if (isAddedSpanEmptyView()) {
                            chatAdapter.getData().get(0).wkMsg.messageSeq = getTopPinViewHeight();
                            chatAdapter.notifyItemChanged(0);
                        } else {
                            WKMsg msg = getSpanEmptyMsg();
                            chatAdapter.addData(0, new WKUIChatMsgItemEntity(this, msg, null));
                        }
                    } else {
                        isShowCallingView = false;
                    }
                }
            }

            if (WKReader.isEmpty(MsgModel.getInstance().channelStatus)) {
                MsgModel.getInstance().channelStatus = new ArrayList<>();
            }
            boolean isAdd = true;
            for (int i = 0; i < MsgModel.getInstance().channelStatus.size(); i++) {
                if (MsgModel.getInstance().channelStatus.get(i).channel_id.equals(channelId)) {
                    MsgModel.getInstance().channelStatus.get(i).calling = isShowCallingView ? 1 : 0;
                    isAdd = false;
                    break;
                }
            }
            if (isAdd) {
                WKChannelState state = new WKChannelState();
                state.channel_id = channelId;
                state.channel_type = channelType;
                state.calling = isShowCallingView ? 1 : 0;
                MsgModel.getInstance().channelStatus.add(state);
            }
            EndpointManager.getInstance().invoke("refresh_conversation_calling", null);
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) wkVBinding.timeTv.getLayoutParams();
            lp.topMargin = AndroidUtilities.dp(10) + getTopPinViewHeight();
            wkVBinding.timeTv.setVisibility(View.GONE);
        });
    }

    // 获取聊天记录
    private void getData(int pullMode, boolean isSetNewData, long aroundMsgOrderSeq, boolean isScrollToEnd) {
        boolean contain = false;
        long oldestOrderSeq;
        if (pullMode == 1) {
            oldestOrderSeq = chatAdapter.getEndMsgOrderSeq();
        } else {
            oldestOrderSeq = chatAdapter.getFirstMsgOrderSeq();
        }
        if (isSyncLastMsg) {
            oldestOrderSeq = 0;
        }
        //定位消息
        if (lastPreviewMsgOrderSeq != 0) {
            contain = true;
            oldestOrderSeq = lastPreviewMsgOrderSeq;
        }
        if (unreadStartMsgOrderSeq != 0) contain = true;
        // 系统 Bot 跨 Space 共享：加大加载量，确保过滤后有足够的当前 Space 消息
        int loadLimit = com.chat.base.space.SystemBotsFallback.isSystemBot(channelId) ? Math.max(limit, 500) : limit;
        WKIM.getInstance().getMsgManager().getOrSyncHistoryMessages(channelId, channelType, oldestOrderSeq, contain, pullMode, loadLimit, aroundMsgOrderSeq, new IGetOrSyncHistoryMsgBack() {
            @Override
            public void onSyncing() {

                if (isShowPinnedView && !isRefreshLoading && !isMoreLoading && !isSyncLastMsg) {
                    EndpointManager.getInstance().invoke("is_syncing_message", 1);
                } else {
                    if (WKReader.isEmpty(chatAdapter.getData())) {
                        WKMsg wkMsg = new WKMsg();
                        wkMsg.type = WKContentType.loading;
                        chatAdapter.addData(new WKUIChatMsgItemEntity(ChatActivity.this, wkMsg, null));
                    }
                }
            }

            @Override
            public void onResult(List<WKMsg> list) {
                if (isShowPinnedView) {
                    EndpointManager.getInstance().invoke("is_syncing_message", 0);
                }
                if (pullMode == 0) {
                    if (WKReader.isEmpty(list))
                        isCanRefresh = false;
                } else {
                    if (WKReader.isEmpty(list)) {
                        isCanLoadMore = false;
                    }
                }
                isSyncLastMsg = false;
                List<WKMsg> filteredList = filterSystemBotMessages(list);

                List<WKMsg> tempList = new ArrayList<>();
                for (WKMsg msg : filteredList) {
                    if (isSetNewData || !chatAdapter.isExist(msg.clientMsgNO, msg.messageID)){
                        tempList.add(msg);
                    }
                }
                // 预处理 msgList（快，主线程）
                boolean msgAddEmptyView = WKReader.isNotEmpty(tempList) && tempList.size() < limit;
                if (msgAddEmptyView) {
                    WKMsg emptyMsg = new WKMsg();
                    emptyMsg.timestamp = 0;
                    emptyMsg.type = WKContentType.emptyView;
                    tempList.add(0, emptyMsg);
                }
                if ((isShowCallingView || isShowPinnedView) && pullMode == 0) {
                    if (WKReader.isNotEmpty(chatAdapter.getData())) {
                        for (int i = 0; i < chatAdapter.getData().size(); i++) {
                            if (chatAdapter.getData().get(i).wkMsg != null && chatAdapter.getData().get(i).wkMsg.type == WKContentType.spanEmptyView) {
                                chatAdapter.removeAt(i);
                                break;
                            }
                        }
                    }
                    tempList.add(0, getSpanEmptyMsg());
                }

                // 捕获 adapter 状态用于后台线程
                final long lastTimeMsg = chatAdapter.getLastTimeMsg();
                final boolean isChoose = chatAdapter.isShowChooseItem();
                final int hidePinned = hideChannelAllPinnedMessage;

                // 重活移到后台线程：构建 UI list（Markwon 渲染、DB 查询、正则匹配）
                Observable.fromCallable(() -> buildUiMsgList(tempList, lastTimeMsg, isChoose, hidePinned))
                        .subscribeOn(Schedulers.computation())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(uiList -> {
                            if (isFinishing() || isDestroyed()) return;
                            applyDataToAdapter(uiList, pullMode, isSetNewData, isScrollToEnd);

                            wkVBinding.chatUnreadLayout.progress.setVisibility(View.GONE);
                            wkVBinding.chatUnreadLayout.msgDownIv.setVisibility(View.VISIBLE);

                            // pullMode==0 的 loading 移除已在 applyDataToAdapter 内部完成（合并到无动画批次）
                            // 其他模式仍需在这里移除
                            if (pullMode != 0) {
                                removeLoadingIndicator();
                            }
                            isRefreshLoading = false;
                            isMoreLoading = false;
                        });
            }
        });


    }

    /**
     * 在后台线程构建 UI 消息列表（重操作：Markwon 渲染、DB 查询、正则匹配）。
     * 调用方需确保 msgList 已完成预处理（emptyView、spanEmptyView）。
     */
    private List<WKUIChatMsgItemEntity> buildUiMsgList(
            List<WKMsg> msgList, long lastTimeMsg, boolean isChoose, int hidePinned) {
        List<WKUIChatMsgItemEntity> list = new ArrayList<>();
        if (WKReader.isNotEmpty(msgList)) {
            long pre_msg_time = lastTimeMsg;
            WKUIChatMsgItemEntity.prepareMentionCache(channelId, channelType);
            for (int i = 0, size = msgList.size(); i < size; i++) {
                // 后台线程保护：Activity 已销毁时立即返回已构建的部分
                if (isFinishing() || isDestroyed()) return list;
                if (!WKTimeUtils.getInstance().isSameDay(msgList.get(i).timestamp, pre_msg_time)
                        && msgList.get(i).type != WKContentType.emptyView
                        && msgList.get(i).type != WKContentType.spanEmptyView) {
                    WKUIChatMsgItemEntity uiChatMsgEntity = new WKUIChatMsgItemEntity(this, new WKMsg(), null);
                    uiChatMsgEntity.wkMsg.type = WKContentType.msgPromptTime;
                    uiChatMsgEntity.wkMsg.content = WKTimeUtils.getInstance().getShowDate(msgList.get(i).timestamp * 1000);
                    uiChatMsgEntity.wkMsg.timestamp = msgList.get(i).timestamp;
                    list.add(uiChatMsgEntity);
                }
                pre_msg_time = msgList.get(i).timestamp;
                WKUIChatMsgItemEntity uiMsg = WKIMUtils.getInstance().msg2UiMsg(
                        this, msgList.get(i), count, showNickName, isChoose);
                if (msgList.get(i).remoteExtra != null) {
                    if (hidePinned == 1) {
                        uiMsg.isPinned = 0;
                    } else {
                        uiMsg.isPinned = msgList.get(i).remoteExtra.isPinned;
                    }
                }
                list.add(uiMsg);
            }
        }
        return list;
    }

    /**
     * 将已构建好的 UI list 应用到 adapter（必须在主线程调用）。
     */
    private void applyDataToAdapter(List<WKUIChatMsgItemEntity> list, int pullMode, boolean isSetNewData, boolean isScrollToEnd) {
        // YUJ-256 P1-3: once we have already rendered the local-first preview,
        // the second (post-sync) render must NOT yank the user's viewport.
        // Same if the user has manually scrolled during the sync window.
        final boolean effectiveScrollToEnd = isScrollToEnd && !hasRenderedPreview && !userHasScrolled;
        if (isSetNewData) {
            int unreadScrollIndex = -1;
            // YUJ-256 P1-2: use the non-zeroed snapshot so the divider is
            // inserted on every applyDataToAdapter call (preview + sync),
            // not only the first one.
            final long unreadAnchor = unreadStartSnapshotOrderSeq;
            if (unreadAnchor != 0) {
                int bestIndex = -1;
                for (int i = 0, size = list.size(); i < size; i++) {
                    if (list.get(i).wkMsg != null && list.get(i).wkMsg.orderSeq > 0
                            && list.get(i).wkMsg.orderSeq >= unreadAnchor) {
                        bestIndex = i;
                        break;
                    }
                }
                if (bestIndex >= 0) {
                    WKUIChatMsgItemEntity uiChatMsgItemEntity = new WKUIChatMsgItemEntity(this, new WKMsg(), null);
                    uiChatMsgItemEntity.wkMsg.type = WKContentType.msgPromptNewMsg;
                    list.add(bestIndex, uiChatMsgItemEntity);
                    unreadScrollIndex = bestIndex;
                }
                // Consume the legacy one-shot field for other consumers that
                // still check it; the snapshot survives so subsequent
                // applyDataToAdapter calls re-insert the divider.
                unreadStartMsgOrderSeq = 0;
            }
            chatAdapter.resetData(list);
            chatAdapter.setNewInstance(list);
            chatAdapter.rebuildIndex();
            // Only scroll to the unread anchor when the user has not taken
            // over the viewport (YUJ-256 P1-3).
            if (unreadScrollIndex >= 0 && !userHasScrolled) {
                final int scrollTarget = unreadScrollIndex;
                linearLayoutManager.scrollToPositionWithOffset(scrollTarget, AndroidUtilities.dp(50));
                hasPositionedUnread = true;
            }
        } else {
            chatAdapter.resetData(list);
            if (pullMode == 1) {
                if (WKReader.isNotEmpty(chatAdapter.getData()) && WKReader.isNotEmpty(list))
                    list.get(0).previousMsg = chatAdapter.getData().get(chatAdapter.getData().size() - 1).wkMsg;
                chatAdapter.addData(list);
                chatAdapter.rebuildIndex();
                trimTopIfNeeded();
            } else {
                if (WKReader.isNotEmpty(list) && WKReader.isNotEmpty(chatAdapter.getData())) {
                    list.get(list.size() - 1).nextMsg = chatAdapter.getData().get(0).wkMsg;
                }
                // 合并新旧数据后通过 setNewInstance 全量刷新。
                // 不使用 addData(0, list) + notifyItemRangeInserted，因为在位置 0 批量插入
                // 会破坏 RecyclerView 的内部滑动状态导致 fling 失效。
                int insertCount = list.size();
                // 构建完整列表：新消息 + 旧消息（去掉重复项和合成消息）
                java.util.Set<String> newMsgIds = new java.util.HashSet<>();
                for (WKUIChatMsgItemEntity item : list) {
                    if (item.wkMsg != null) {
                        if (!TextUtils.isEmpty(item.wkMsg.clientMsgNO)) newMsgIds.add(item.wkMsg.clientMsgNO);
                        if (!TextUtils.isEmpty(item.wkMsg.messageID)) newMsgIds.add("mid_" + item.wkMsg.messageID);
                    }
                }
                List<WKUIChatMsgItemEntity> merged = new ArrayList<>(list);
                for (WKUIChatMsgItemEntity item : chatAdapter.getData()) {
                    if (item.wkMsg == null) continue;
                    // 跳过合成消息（时间分隔符、空白占位等），它们已在新 list 中重建
                    if (item.wkMsg.type == WKContentType.loading
                            || item.wkMsg.type == WKContentType.msgPromptTime
                            || item.wkMsg.type == WKContentType.emptyView
                            || item.wkMsg.type == WKContentType.spanEmptyView) continue;
                    // 跳过与新 list 重复的真实消息
                    if (!TextUtils.isEmpty(item.wkMsg.clientMsgNO) && newMsgIds.contains(item.wkMsg.clientMsgNO)) continue;
                    if (!TextUtils.isEmpty(item.wkMsg.messageID) && newMsgIds.contains("mid_" + item.wkMsg.messageID)) continue;
                    merged.add(item);
                }
                // 在 setNewInstance 之前裁剪，避免 notifyDataSetChanged 后再
                // 调用 notifyItemRangeRemoved 导致 RecyclerView 状态不一致
                if (merged.size() > MAX_ADAPTER_SIZE) {
                    int removeCount = Math.min(TRIM_BATCH_SIZE, merged.size() - MAX_ADAPTER_SIZE);
                    merged.subList(merged.size() - removeCount, merged.size()).clear();
                    if (!merged.isEmpty()) {
                        merged.get(merged.size() - 1).nextMsg = null;
                    }
                    isCanLoadMore = true;
                }
                chatAdapter.resetData(merged);
                chatAdapter.setNewInstance(merged);
                chatAdapter.rebuildIndex();
                // 将 viewport 锚定到旧消息位置，让新消息在上方可滑动到达
                linearLayoutManager.scrollToPositionWithOffset(insertCount, 0);
                clearEdgeEffects();
            }
        }
        // YUJ-256 P1-2: use the tips snapshot so the second applyDataToAdapter
        // call (after sync completes) still scrolls to and highlights the
        // target message, even though `tipsOrderSeq` was zeroed in the first
        // call.
        final long tipsAnchor = tipsSnapshotOrderSeq;
        if (tipsAnchor != 0 || lastPreviewMsgOrderSeq != 0) {
            wkVBinding.recyclerView.setVisibility(View.VISIBLE);
            if (tipsAnchor != 0) {
                int tipsIndex = chatAdapter.findPositionByOrderSeq(tipsAnchor);
                if (tipsIndex >= 0) {
                    // 最后几条消息：滚到底部（聊天窗口自然视角）
                    // 中间消息：居中显示（对齐 iOS UITableViewScrollPositionMiddle）
                    if (tipsIndex >= chatAdapter.getItemCount() - 3) {
                        wkVBinding.recyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
                    } else {
                        linearLayoutManager.scrollToPositionWithOffset(tipsIndex, wkVBinding.recyclerView.getHeight() / 3);
                    }
                    chatAdapter.setPendingHighlightOrderSeq(tipsAnchor);
                    tipsOrderSeq = 0;
                    tipsSnapshotOrderSeq = 0;
                }
            }
            if (lastPreviewMsgOrderSeq != 0 && !userHasScrolled) {
                int previewIndex = chatAdapter.findPositionByOrderSeq(lastPreviewMsgOrderSeq);
                if (previewIndex >= 0) {
                    linearLayoutManager.scrollToPositionWithOffset(previewIndex, keepOffsetY);
                }
            }
        } else {
            if (effectiveScrollToEnd)
                wkVBinding.recyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
            else wkVBinding.recyclerView.setVisibility(View.VISIBLE);
        }
        if (isCanLoadMore && WKReader.isNotEmpty(chatAdapter.getData()) && chatAdapter.getData().get(chatAdapter.getData().size() - 1).wkMsg != null) {
            int maxSeq = WKIM.getInstance().getMsgManager().getMaxMessageSeqWithChannel(channelId, channelType);
            if (chatAdapter.getData().get(chatAdapter.getData().size() - 1).wkMsg.messageSeq == maxSeq) {
                isCanLoadMore = false;
            }
        }

        // YUJ-256 P1-3: mark that the preview/first render has happened so
        // any subsequent applyDataToAdapter call (e.g. sync-complete second
        // onResult) does not yank the user's viewport back to the end.
        if (isSetNewData) {
            hasRenderedPreview = true;
        }

        new Handler().postDelayed(() -> {
            if (isUpdateRedDot) {
                MsgModel.getInstance().clearUnread(channelId, channelType, redDot, (code, msg) -> {
                    if (code == HttpResponseCode.success && redDot == 0) {
                        isUpdateRedDot = false;
                    }
                });
            }
        }, 500);
    }


    private void hideOrShowRightView(boolean isShow) {
        if (((channelId.equals(WKSystemAccount.system_file_helper) || channelId.equals(WKSystemAccount.system_team)) && channelType == WKChannelType.PERSONAL) || channelType == WKChannelType.CUSTOMER_SERVICE) {
            isShow = false;
        }
        WKChannel channel = getChatChannelInfo();
        if (channelType == WKChannelType.PERSONAL && (channel.isDeleted == 1 || UserUtils.getInstance().checkFriendRelation(channelId))) {
            isShow = false;
        }
        CommonAnim.getInstance().showOrHide(callIV, isShow, true);
    }

    private void resetReminder(List<WKReminder> list) {
        if (WKReader.isEmpty(list)) {
            return;
        }
        List<WKUIChatMsgItemEntity> msgList = chatAdapter.getData();
        List<Long> ids = new ArrayList<>();
        for (int i = 0, size = msgList.size(); i < size; i++) {
            for (WKReminder reminder : list) {
                if (msgList.get(i).wkMsg != null && !TextUtils.isEmpty(msgList.get(i).wkMsg.messageID) && msgList.get(i).wkMsg.messageID.equals(reminder.messageID)) {
                    if (msgList.get(i).wkMsg.viewed == 1 && reminder.done == 0) {
                        ids.add(reminder.reminderID);
                    }
                }
            }
        }

        // 先完成提醒项
        MsgModel.getInstance().doneReminder(ids);

        for (WKReminder reminder : list) {
            boolean isPublisher = !TextUtils.isEmpty(reminder.publisher) && reminder.publisher.equals(loginUID);
            if (!reminder.channelID.equals(channelId) || isPublisher) continue;
            if (reminder.done == 0) {
                boolean isAdd = true;
                for (int i = 0, size = reminderList.size(); i < size; i++) {
                    if (reminder.reminderID == reminderList.get(i).reminderID && reminder.type == reminderList.get(i).type) {
                        isAdd = false;
                        reminderList.get(i).done = 0;
                        break;
                    }
                }
                for (int i = 0; i < ids.size(); i++) {
                    if (ids.get(i) == reminder.reminderID) {
                        isAdd = false;
                        break;
                    }
                }
                if (isAdd && reminder.type == WKMentionType.WKReminderTypeMentionMe)
                    reminderList.add(reminder);
                boolean isAddApprove = true;
                for (int i = 0, size = groupApproveList.size(); i < size; i++) {
                    if (reminder.reminderID == groupApproveList.get(i).reminderID && reminder.type == groupApproveList.get(i).type) {
                        isAddApprove = false;
                        groupApproveList.get(i).done = 0;
                        break;
                    }
                }
                if (isAddApprove && reminder.type == WKMentionType.WKApplyJoinGroupApprove)
                    groupApproveList.add(reminder);
            } else {
                if (WKReader.isNotEmpty(reminderList)) {
                    for (int i = 0, size = reminderList.size(); i < size; i++) {
                        if (reminder.messageID.equals(reminderList.get(i).messageID)) {
//                            reminderList.get(i).done = 1;
                            reminderList.remove(i);
                            break;
                        }
                    }
                }
                if (WKReader.isNotEmpty(groupApproveList)) {
                    for (int i = 0, size = groupApproveList.size(); i < size; i++) {
                        if (reminder.messageID.equals(groupApproveList.get(i).messageID)) {
//                            groupApproveList.get(i).done = 1;
                            groupApproveList.remove(i);
                            break;
                        }
                    }
                }
            }
        }
        resetRemindView();
        resetGroupApproveView();

//        if (WKReader.isNotEmpty(list)) {
//            List<WKUIChatMsgItemEntity> msgList = chatAdapter.getData();
//            List<Long> ids = new ArrayList<>();
//            for (int i = 0, size = list.size(); i < size; i++) {
//                if (list.get(i).done == 1) continue;
//                for (int j = 0, len = msgList.size(); j < len; j++) {
//                    if (msgList.get(j).wkMsg != null && !TextUtils.isEmpty(msgList.get(j).wkMsg.messageID) && msgList.get(j).wkMsg.messageID.equals(list.get(i).messageID)) {
//                        if (msgList.get(j).wkMsg.viewed == 1) {
//                            ids.add(list.get(i).reminderID);
//                            list.remove(i);
//                            i--;
//                            size--;
//                            break;
//                        }
//                    }
//                }
//            }
//            MsgModel.getInstance().doneReminder(ids);
//            if (WKReader.isEmpty(list)) {
//                return;
//            }
//            for (WKReminder reminder : list) {
//                boolean isPublisher = !TextUtils.isEmpty(reminder.publisher) && reminder.publisher.equals(loginUID);
//                if (!reminder.channelID.equals(channelId) || isPublisher) continue;
//                if (reminder.done == 0) {
//                    boolean isAdd = true;
//                    for (int i = 0, size = reminderList.size(); i < size; i++) {
//                        if (reminder.reminderID == reminderList.get(i).reminderID && reminder.type == reminderList.get(i).type) {
//                            isAdd = false;
//                            reminderList.get(i).done = 0;
//                            break;
//                        }
//                    }
//                    if (isAdd && reminder.type == WKMentionType.WKReminderTypeMentionMe)
//                        reminderList.add(reminder);
//                    boolean isAddApprove = true;
//                    for (int i = 0, size = groupApproveList.size(); i < size; i++) {
//                        if (reminder.reminderID == groupApproveList.get(i).reminderID && reminder.type == groupApproveList.get(i).type) {
//                            isAddApprove = false;
//                            groupApproveList.get(i).done = 0;
//                            break;
//                        }
//                    }
//                    if (isAddApprove && reminder.type == WKMentionType.WKApplyJoinGroupApprove)
//                        groupApproveList.add(reminder);
//                }
//            }
//            resetRemindView();
//            resetGroupApproveView();
//        }
    }

    private void resetRemindView() {
        wkVBinding.chatUnreadLayout.remindCountTv.setCount(reminderList.size(), true);
        wkVBinding.chatUnreadLayout.remindCountTv.setVisibility(WKReader.isNotEmpty(reminderList) ? View.VISIBLE : View.GONE);
        wkVBinding.chatUnreadLayout.remindLayout.post(() -> CommonAnim.getInstance().showOrHide(wkVBinding.chatUnreadLayout.remindLayout, WKReader.isNotEmpty(reminderList), WKReader.isNotEmpty(reminderList), false));
    }

    private void resetGroupApproveView() {
        wkVBinding.chatUnreadLayout.approveCountTv.setCount(groupApproveList.size(), true);
        wkVBinding.chatUnreadLayout.approveCountTv.setVisibility(WKReader.isNotEmpty(groupApproveList) ? View.VISIBLE : View.GONE);
        wkVBinding.chatUnreadLayout.groupApproveLayout.post(() -> CommonAnim.getInstance().showOrHide(wkVBinding.chatUnreadLayout.groupApproveLayout, WKReader.isNotEmpty(groupApproveList), WKReader.isNotEmpty(reminderList), false));
    }

    private void showUnReadCountView() {
        wkVBinding.chatUnreadLayout.msgCountTv.setCount(redDot, false);
        wkVBinding.chatUnreadLayout.msgCountTv.setVisibility(redDot > 0 ? View.VISIBLE : View.GONE);
        wkVBinding.chatUnreadLayout.newMsgLayout.post(() -> CommonAnim.getInstance().showOrHide(wkVBinding.chatUnreadLayout.newMsgLayout, redDot > 0, redDot > 0, false));
    }

    private void showChannelName(WKChannel channel) {
        if (channelId.equals(WKSystemAccount.system_team)) {
            wkVBinding.topLayout.titleCenterTv.setText(R.string.wk_system_notice);
        } else if (channelId.equals(WKSystemAccount.system_file_helper)) {
            wkVBinding.topLayout.titleCenterTv.setText(R.string.wk_file_helper);
        } else {
            String showName = TextUtils.isEmpty(channel.channelRemark) ? channel.channelName : channel.channelRemark;
            wkVBinding.topLayout.titleCenterTv.setText(showName);
        }
    }

    private void removeMsg(WKMsg msg) {
        EndpointManager.getInstance().invoke("stop_reaction_animation", null);
        int tempIndex = 0;
        for (int i = 0, size = chatAdapter.getData().size(); i < size; i++) {
            if (chatAdapter.getData().get(i).wkMsg != null && (chatAdapter.getData().get(i).wkMsg.clientSeq == msg.clientSeq || chatAdapter.getData().get(i).wkMsg.clientMsgNO.equals(msg.clientMsgNO))) {
                tempIndex = i;
                if (i - 1 >= 0) {
                    if (i + 1 <= chatAdapter.getData().size() - 1) {
                        chatAdapter.getData().get(i - 1).nextMsg = chatAdapter.getData().get(i + 1).wkMsg;
                    } else {
                        chatAdapter.getData().get(i - 1).nextMsg = null;
                    }
                }
                if (i + 1 <= chatAdapter.getData().size() - 1) {
                    if (i - 1 >= 0) {
                        chatAdapter.getData().get(i + 1).previousMsg = chatAdapter.getData().get(i - 1).wkMsg;
                    } else chatAdapter.getData().get(i + 1).previousMsg = null;
                }
                chatAdapter.removeAt(i);
                break;
            }
        }

        int timeIndex = tempIndex - 1;
        if (timeIndex < 0) return;
        //如果是时间也删除
        if (chatAdapter.getData().size() >= timeIndex) {
            if (chatAdapter.getData().get(timeIndex).wkMsg.type == WKContentType.msgPromptTime) {

                if (timeIndex - 1 >= 0) {
                    if (timeIndex + 1 <= chatAdapter.getData().size() - 1) {
                        chatAdapter.getData().get(timeIndex - 1).nextMsg = chatAdapter.getData().get(timeIndex + 1).wkMsg;
                    } else {
                        chatAdapter.getData().get(timeIndex - 1).nextMsg = null;
                    }
                }
                if (timeIndex + 1 <= chatAdapter.getData().size() - 1) {
                    if (timeIndex - 1 >= 0) {
                        chatAdapter.getData().get(timeIndex + 1).previousMsg = chatAdapter.getData().get(timeIndex - 1).wkMsg;
                    } else chatAdapter.getData().get(timeIndex + 1).previousMsg = null;
                }
                chatAdapter.removeAt(timeIndex);
            }
        }
        chatAdapter.rebuildIndex();
    }

    private void showToast(int textId) {
        WKToastUtils.getInstance().showToast(getString(textId));
    }

    private synchronized void setShowTime() {
        int index = linearLayoutManager.findFirstVisibleItemPosition();
        if (index == lastShowTimeIndex) return;
        long now = System.currentTimeMillis();
        if (now - lastShowTimeUpdate < 100) return;
        lastShowTimeIndex = index;
        lastShowTimeUpdate = now;
        String showTime = "";
        if (index > 0 && index < chatAdapter.getData().size()) {
            WKUIChatMsgItemEntity WKUIChatMsgItemEntity = chatAdapter.getData().get(index);
            if (WKUIChatMsgItemEntity.wkMsg != null && WKUIChatMsgItemEntity.wkMsg.timestamp > 0) {
                showTime = WKTimeUtils.getInstance().getShowDate(WKUIChatMsgItemEntity.wkMsg.timestamp * 1000);
            }
        }
        if (!TextUtils.isEmpty(showTime)) {
            SpannableString str = new SpannableString(showTime);
            str.setSpan(new SystemMsgBackgroundColorSpan(ContextCompat.getColor(this, R.color.colorSystemBg), AndroidUtilities.dp(5), AndroidUtilities.dp(2 * 5)), 0, showTime.length(), 0);
            wkVBinding.timeTv.setText(str);
            CommonAnim.getInstance().showOrHide(wkVBinding.timeTv, true, true);
        } else {
            CommonAnim.getInstance().showOrHide(wkVBinding.timeTv, false, false);
        }
    }

    private boolean isRefreshReaction(List<WKMsgReaction> oldList, List<WKMsgReaction> newList) {
        if (WKReader.isEmpty(oldList) && WKReader.isEmpty(newList)) return false;
        if ((WKReader.isEmpty(oldList) && WKReader.isNotEmpty(newList)) || (WKReader.isEmpty(newList) && WKReader.isNotEmpty(oldList)) || (oldList.size() != newList.size())) {
            return true;
        }
        boolean isRefresh = false;
        for (WKMsgReaction reaction : newList) {
            boolean refresh = true;
            for (WKMsgReaction reaction1 : oldList) {
                if (reaction1.messageID.equals(reaction.messageID) && reaction1.emoji.equals(reaction.emoji) && reaction1.isDeleted == reaction.isDeleted) {
                    refresh = false;
                    break;
                }
            }
            if (refresh) {
                isRefresh = true;
                break;
            }
        }
        return isRefresh;
    }

    private void scrollToPosition(int index) {
        linearLayoutManager.scrollToPosition(index);
    }

    /**
     * 清除 RecyclerView 顶部/底部 EdgeEffect 的残留状态。
     * 加载历史消息后 scrollToPositionWithOffset 不会重置 EdgeEffect，
     * 导致后续 fling() 的速度被 mTopGlow.onAbsorb() 吞掉而返回 false。
     *
     * 使用公开 API setEdgeEffectFactory() 触发 invalidateGlows()，
     * 将所有 EdgeEffect 置 null，下次滚动时自动重建。
     */
    private void clearEdgeEffects() {
        if (edgeEffectFactory == null) {
            edgeEffectFactory = wkVBinding.recyclerView.getEdgeEffectFactory();
        }
        wkVBinding.recyclerView.setEdgeEffectFactory(edgeEffectFactory);
    }


    private void showRefreshLoading() {
        if (isRefreshLoading || !isCanRefresh) return;
        isRefreshLoading = true;
        WKMsg wkMsg = new WKMsg();
        wkMsg.type = WKContentType.loading;
        int index = 0;
        if (isShowPinnedView || isShowCallingView) {
            for (int i = 0, size = chatAdapter.getData().size(); i < size; i++) {
                if (chatAdapter.getData().get(i).wkMsg != null && chatAdapter.getData().get(i).wkMsg.type == WKContentType.spanEmptyView) {
                    index = i + 1;
                    break;
                }
            }
        }
        chatAdapter.addData(index, new WKUIChatMsgItemEntity(this, wkMsg, null));
        // 不再强制 scrollToPosition(0)：用户已经在顶部（触发 showRefreshLoading 的前提），
        // loading indicator 在位置 0 自然可见。强制 scroll 会给 LinearLayoutManager 设置
        // pendingScrollPosition，可能干扰后续 fling 计算。
        lastPreviewMsgOrderSeq = 0;
        new Handler().postDelayed(() -> getData(0, false, 0, false), 300);
    }

    private void showMoreLoading() {
        if (isMoreLoading || !isCanLoadMore) return;
        isMoreLoading = true;
        WKMsg wkMsg = new WKMsg();
        wkMsg.type = WKContentType.loading;
        chatAdapter.addData(new WKUIChatMsgItemEntity(this, wkMsg, null));
        wkVBinding.recyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
        lastPreviewMsgOrderSeq = 0;
        unreadStartMsgOrderSeq = 0;
        new Handler().postDelayed(() -> getData(1, false, 0, false), 300);
    }

    private List<PopupMenuItem> getGroupApprovePopupItems() {
        PopupMenuItem item = new PopupMenuItem(getString(R.string.clear_all_remind), R.mipmap.msg_seen, () -> {
            List<WKReminder> list = WKIM.getInstance().getReminderManager().getRemindersWithType(channelId, channelType, WKMentionType.WKApplyJoinGroupApprove);
            List<Long> ids = new ArrayList<>();
            for (WKReminder reminder : list) {
                if (reminder.done == 0) {
                    ids.add(reminder.reminderID);
                }
            }
            groupApproveList.clear();
            resetGroupApproveView();
            MsgModel.getInstance().doneReminder(ids);
        });

        List<PopupMenuItem> list = new ArrayList<>();
        list.add(item);
        return list;
    }

    private List<PopupMenuItem> getRemindPopupItems() {
        PopupMenuItem item = new PopupMenuItem(getString(R.string.clear_all_remind), R.mipmap.msg_seen, () -> {
            List<WKReminder> list = WKIM.getInstance().getReminderManager().getRemindersWithType(channelId, channelType, WKMentionType.WKReminderTypeMentionMe);
            List<Long> ids = new ArrayList<>();
            for (WKReminder reminder : list) {
                if (reminder.done == 0) {
                    ids.add(reminder.reminderID);
                }
            }
            reminderList.clear();
            resetRemindView();
            MsgModel.getInstance().doneReminder(ids);
        });

        List<PopupMenuItem> list = new ArrayList<>();
        list.add(item);
        return list;
    }

    private void checkLoginUserInGroupStatus() {
        if (channelType == WKChannelType.GROUP) {
            WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, loginUID);
            hideOrShowRightView(member == null || member.isDeleted == 0);
        }
    }

    private void scrollToEnd() {
        linearLayoutManager.scrollToPosition(chatAdapter.getItemCount() - 1);
    }

    /**
     * 安全地执行 adapter 修改操作：如果 RecyclerView 正在布局或滚动中，
     * 将操作 post 到下一帧执行，避免 IllegalStateException。
     */
    private void trimBottomIfNeeded() {
        int size = chatAdapter.getData().size();
        if (size <= MAX_ADAPTER_SIZE) return;
        int lastVisible = linearLayoutManager.findLastVisibleItemPosition();
        if (lastVisible >= size - 5) return; // too close to bottom, skip
        int removeCount = Math.min(TRIM_BATCH_SIZE, size - MAX_ADAPTER_SIZE);
        int removeStart = size - removeCount;
        chatAdapter.getData().subList(removeStart, size).clear();
        chatAdapter.notifyItemRangeRemoved(removeStart, removeCount);
        // clean previousMsg/nextMsg at new boundary
        int newSize = chatAdapter.getData().size();
        if (newSize > 0) {
            chatAdapter.getData().get(newSize - 1).nextMsg = null;
        }
        isCanLoadMore = true;
        chatAdapter.rebuildIndex();
    }

    private void trimTopIfNeeded() {
        int size = chatAdapter.getData().size();
        if (size <= MAX_ADAPTER_SIZE) return;
        int firstVisible = linearLayoutManager.findFirstVisibleItemPosition();
        if (firstVisible < 5) return; // too close to top, skip
        int removeCount = Math.min(TRIM_BATCH_SIZE, size - MAX_ADAPTER_SIZE);
        chatAdapter.getData().subList(0, removeCount).clear();
        chatAdapter.notifyItemRangeRemoved(0, removeCount);
        // clean previousMsg/nextMsg at new boundary
        if (!chatAdapter.getData().isEmpty()) {
            chatAdapter.getData().get(0).previousMsg = null;
        }
        isCanRefresh = true;
        chatAdapter.rebuildIndex();
    }

    private void removeLoadingIndicator() {
        if (WKReader.isNotEmpty(chatAdapter.getData())) {
            for (int i = 0, size = chatAdapter.getData().size(); i < size; i++) {
                if (chatAdapter.getData().get(i).wkMsg != null && chatAdapter.getData().get(i).wkMsg.type == WKContentType.loading) {
                    chatAdapter.removeAt(i);
                    break;
                }
            }
        }
    }

    private void safeAdapterAction(Runnable action) {
        if (wkVBinding.recyclerView.isComputingLayout()) {
            wkVBinding.recyclerView.post(action);
        } else {
            action.run();
        }
    }

    // 显示一条时间消息
    private synchronized WKMsg addTimeMsg(long newMsgTime) {
        long lastMsgTime = chatAdapter.getLastTimeMsg();
        WKMsg msg = null;
        if (!WKTimeUtils.getInstance().isSameDay(newMsgTime, lastMsgTime)) {
            int lastIndex = chatAdapter.getData().size() - 1;
            WKUIChatMsgItemEntity uiChatMsgEntity = new WKUIChatMsgItemEntity(this, null, null);
            msg = new WKMsg();
            uiChatMsgEntity.wkMsg = msg;
            uiChatMsgEntity.isChoose = (chatAdapter.getItemCount() > 0 && chatAdapter.getData().get(0).isChoose);
            uiChatMsgEntity.wkMsg.type = WKContentType.msgPromptTime;
            uiChatMsgEntity.wkMsg.content = WKTimeUtils.getInstance().getShowDate(newMsgTime * 1000);
            uiChatMsgEntity.wkMsg.timestamp = WKTimeUtils.getInstance().getCurrentSeconds();
            chatAdapter.addData(uiChatMsgEntity);
            if (lastIndex >= 0) {
                chatAdapter.notifyBackground(lastIndex);
            }
        }
        return msg;
    }

    private boolean setBackListener() {
        if (!isViewingPicture) {

            if (numberTextView.getVisibility() == View.VISIBLE) {
                for (int i = 0, size = chatAdapter.getItemCount(); i < size; i++) {
                    chatAdapter.getItem(i).isChoose = false;
                    chatAdapter.getItem(i).isChecked = false;
                    chatAdapter.notifyItemChanged(i, chatAdapter.getItem(i));
                }
                chatPanelManager.hideMultipleChoice();
                CommonAnim.getInstance().rotateImage(wkVBinding.topLayout.backIv, 180f, 360f, R.mipmap.ic_ab_back);
                numberTextView.setNumber(0, true);
                hideOrShowRightView(true);
                EndpointManager.getInstance().invoke("chat_page_reset", getChatChannelInfo());
                CommonAnim.getInstance().showOrHide(numberTextView, false, true);
            } else {
                if (chatPanelManager.isCanBack()) {
                    persistCurrentChannelEditState();
                    // 子区（COMMUNITY_TOPIC）不走 goBackToList 保活路径：子区是一次性
                    // 浏览，保活会导致折叠屏上反复进出子区时实例堆积，back 时逐个回退
                    // 像死循环。只有群聊/私聊等主会话才适合 reuse 优化。
                    if (channelType != WKChannelType.COMMUNITY_TOPIC
                            && com.chat.uikit.chat.ChatReuseNavigator.goBackToList(this)) {
                        return false;
                    }
                    new Handler(Objects.requireNonNull(Looper.myLooper())).postDelayed(this::finish, 150);
                }
            }
        }
        return false;
    }


    // 定时上报已读消息
    private void startTimer() {
        Observable.interval(0, 3, TimeUnit.SECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<>() {
            @Override
            public void onComplete() {
            }

            @Override
            public void onError(@io.reactivex.rxjava3.annotations.NonNull Throwable e) {
            }

            @Override
            public void onSubscribe(@io.reactivex.rxjava3.annotations.NonNull Disposable d) {
                disposable = d;
            }

            @Override
            public void onNext(@io.reactivex.rxjava3.annotations.NonNull Long value) {
                if (WKReader.isEmpty(readMsgIds) || !isUploadReadMsg) {
                    return;
                }
                List<String> msgIds = new ArrayList<>(readMsgIds);
                EndpointManager.getInstance().invoke("read_msg", new ReadMsgMenu(channelId, channelType, msgIds));
                readMsgIds.clear();
            }
        });
    }

    private void resetHideChannelAllPinnedMessage() {
        String key = String.format("hide_pin_msg_%s_%s", channelId, channelType);
        hideChannelAllPinnedMessage = WKSharedPreferencesUtil.getInstance().getIntWithUID(key);
    }

    private float dispatchDownX, dispatchDownY;
    private long dispatchDownTime;

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dispatchDownX = ev.getRawX();
                dispatchDownY = ev.getRawY();
                dispatchDownTime = ev.getDownTime();
                break;
            case MotionEvent.ACTION_UP:
                int touchSlop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();
                long duration = ev.getEventTime() - dispatchDownTime;
                if (Math.abs(ev.getRawX() - dispatchDownX) < touchSlop
                        && Math.abs(ev.getRawY() - dispatchDownY) < touchSlop
                        && duration < android.view.ViewConfiguration.getLongPressTimeout()) {
                    EndpointManager.getInstance().invoke("chat_activity_touch", null);
                }
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 折叠屏 P0 止血（#174）：加了 configChanges 后本回调会真触发。
        // 刷新一次屏宽/density 缓存供依赖该值的 UI 计算；不再对全量消息做
        // notifyItemRangeChanged，历史消息量大时会明显卡顿（L1/L2 再做精细化刷新）。
        float density = getResources().getDisplayMetrics().density;
        AndroidUtilities.setDensity(density);
        AndroidUtilities.isPORTRAIT = newConfig.orientation != Configuration.ORIENTATION_LANDSCAPE;
        // YUJ-273 · 折叠屏回归：非折叠态启动→展开时右侧消息区自适应失效。
        // setupPaneResizeObserver 依赖 RecyclerView 的 onLayoutChange（width 变化）触发
        // 可见项重绑，但若 ChatActivity 在非折叠态启动时被 Embedding 判为「不分屏」而
        // 全屏化，随后 unfold：Embedding 会改 Activity 容器尺寸，但某些设备上
        // onLayoutChange 的 oldWidth 可能因为 lifecycle 时序落空导致 prev==0 被早退
        // （见 setupPaneResizeObserver 的 prev==0 分支）。这里主动 post 一次可见项重绑，
        // BubbleLayout.onMeasure 会在下一帧拉到新的 PaneMetrics，保证气泡 maxWidth 跟随
        // 新 pane 宽度刷新。与 onLayoutChange 路径是幂等兜底。
        if (wkVBinding != null && wkVBinding.recyclerView != null
                && chatAdapter != null && linearLayoutManager != null) {
            wkVBinding.recyclerView.post(() -> {
                if (chatAdapter == null || linearLayoutManager == null) return;
                int first = linearLayoutManager.findFirstVisibleItemPosition();
                int last = linearLayoutManager.findLastVisibleItemPosition();
                if (first < 0 || last < 0 || last < first) return;
                try {
                    chatAdapter.notifyItemRangeChanged(first, last - first + 1);
                } catch (Throwable ignored) {
                    // 配置变更路径上的 adapter 竞态兜底
                }
            });
        }
    }

    @Override
    public void sendMessage(WKMessageContent messageContent) {

        if (messageContent.type == WKContentType.WK_TEXT && editMsg != null) {
            JSONObject jsonObject = messageContent.encodeMsg();
            if (jsonObject == null) jsonObject = new JSONObject();
            try {
                jsonObject.put("type", messageContent.type);
            } catch (JSONException e) {
                Log.e("消息类型错误", "-->");
            }
            boolean isUpdate = isUpdate(messageContent);
            if (isUpdate) {
                WKIM.getInstance().getMsgManager().updateMsgEdit(editMsg.messageID, channelId, channelType, jsonObject.toString());
            }
            deleteOperationMsg();
            return;
        }
        if (messageContent.type == WKContentType.WK_TEXT && replyWKMsg != null) {
            WKReply wkReply = new WKReply();
            if (replyWKMsg.remoteExtra != null && replyWKMsg.remoteExtra.contentEditMsgModel != null) {
                wkReply.payload = replyWKMsg.remoteExtra.contentEditMsgModel;
            } else {
                wkReply.payload = replyWKMsg.baseContentMsgModel;
            }
            String showName = "";
            if (replyWKMsg.getFrom() != null) {
                showName = replyWKMsg.getFrom().channelName;
            } else {
                WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(replyWKMsg.fromUID, WKChannelType.PERSONAL);
                if (channel != null) showName = channel.channelName;
            }
            wkReply.from_name = showName;
            wkReply.from_uid = replyWKMsg.fromUID;
            wkReply.message_id = replyWKMsg.messageID;
            wkReply.message_seq = replyWKMsg.messageSeq;
            if (replyWKMsg.baseContentMsgModel.reply != null && !TextUtils.isEmpty(replyWKMsg.baseContentMsgModel.reply.root_mid)) {
                wkReply.root_mid = replyWKMsg.baseContentMsgModel.reply.root_mid;
            } else {
                wkReply.root_mid = wkReply.message_id;
            }
            // YUJ-132: 透传被回复消息发送者的 home/source Space 字段，让接收端的 Reply 预览
            // 能够渲染 "@SpaceName"。字段位于 localExtraMap（见 YUJ-89 MsgModel.copyExternalSourceExtras）。
            copyReplyExternalExtras(replyWKMsg, wkReply);
            messageContent.reply = wkReply;
        }
        sendMsg(messageContent);
        replyWKMsg = null;

    }

    private void sendMsg(WKMessageContent messageContent) {
        if (redDot > 0) {
            wkVBinding.chatUnreadLayout.newMsgLayout.performClick();
        }
        // DM 消息注入 space_id，让 BotFather 等系统 Bot 知道用户当前 Space
        // SDK 基类 WKMessageContent 已支持 spaceId 字段，编码时自动写入 JSON（与 iOS 对齐）
        String spaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (!TextUtils.isEmpty(spaceId) && channelType == WKChannelType.PERSONAL) {
            messageContent.spaceId = spaceId;
        }
        WKMsg wkMsg = new WKMsg();
        wkMsg.channelID = channelId;
        wkMsg.channelType = channelType;
        wkMsg.type = messageContent.type;
        wkMsg.baseContentMsgModel = messageContent;
        WKChannel channel = getChatChannelInfo();
        wkMsg.setChannelInfo(channel);
        WKSendMsgUtils.getInstance().sendMessage(wkMsg);

        // 子区首次发消息时自动加入成员列表
        if (channelType == WKChannelType.COMMUNITY_TOPIC && !hasJoinedThread) {
            hasJoinedThread = true;
            String[] parsed = ThreadModel.getInstance().parseChannelId(channelId);
            if (parsed != null) {
                ThreadModel.getInstance().joinThread(parsed[0], parsed[1], (code, msg) -> {});
            }
        }
    }

    private boolean isUpdate(WKMessageContent messageContent) {
        boolean isUpdate = false;
        if (editMsg.remoteExtra != null && editMsg.remoteExtra.contentEditMsgModel != null) {
            if (!editMsg.remoteExtra.contentEditMsgModel.getDisplayContent().equals(messageContent.getDisplayContent())) {
                isUpdate = true;
            }
        }
        if (!editMsg.baseContentMsgModel.getDisplayContent().equals(messageContent.getDisplayContent())) {
            isUpdate = true;
        }
        return isUpdate;
    }

    private void setOnlineView(WKChannel channel) {
        if (channel.online == 1) {
            String device = getString(R.string.phone);
            if (channel.deviceFlag == UserOnlineStatus.Web) device = getString(R.string.web);
            else if (channel.deviceFlag == UserOnlineStatus.PC) device = getString(R.string.pc);
            String content = String.format("%s%s", device, getString(R.string.online));
            wkVBinding.topLayout.subtitleTv.setText(content);
            wkVBinding.topLayout.subtitleView.setVisibility(View.VISIBLE);
        } else {
            if (channel.lastOffline > 0) {
                String showTime = WKTimeUtils.getInstance().getOnlineTime(channel.lastOffline);
                if (TextUtils.isEmpty(showTime)) {
                    wkVBinding.topLayout.subtitleView.setVisibility(View.VISIBLE);
                    String time = WKTimeUtils.getInstance().getShowDateAndMinute(channel.lastOffline * 1000L);
                    String content = String.format("%s%s", getString(R.string.last_seen_time), time);
                    wkVBinding.topLayout.subtitleTv.setText(content);
                } else {
                    wkVBinding.topLayout.subtitleTv.setText(showTime);
                    wkVBinding.topLayout.subtitleView.setVisibility(View.VISIBLE);
                }
            } else wkVBinding.topLayout.subtitleView.setVisibility(View.GONE);
        }
    }

    @Override
    public WKChannel getChatChannelInfo() {
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);
        if (channel == null) {
            channel = new WKChannel(channelId, channelType);
        }
        return channel;
    }

    @Override
    public void showMultipleChoice() {
        chatPanelManager.showMultipleChoice();
        CommonAnim.getInstance().rotateImage(wkVBinding.topLayout.backIv, 180f, 360f, R.mipmap.ic_close_white);
        CommonAnim.getInstance().showOrHide(numberTextView, true, true);
        CommonAnim.getInstance().showOrHide(callIV, false, false);
        CommonAnim.getInstance().showOrHide(moreIV, false, false);
        EndpointManager.getInstance().invoke("hide_pinned_view", null);
    }

    @Override
    public void setTitleRightText(String text) {
        int num = Integer.parseInt(text);
        chatPanelManager.updateForwardView(num);
        numberTextView.setNumber(num, true);
        CommonAnim.getInstance().showOrHide(numberTextView, true, true);
        CommonAnim.getInstance().showOrHide(callIV, false, false);
        CommonAnim.getInstance().showOrHide(moreIV, false, false);
    }

    @Override
    public void showReply(WKMsg wkMsg) {
        this.editMsg = null;
        boolean showDialog = false;
        WKChannelMember mChannelMember = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, loginUID);
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);
        if (channel != null && mChannelMember != null) {
            if ((channel.forbidden == 1 && mChannelMember.role == WKChannelMemberRole.normal) || mChannelMember.forbiddenExpirationTime > 0) {
                //普通成员
                showDialog = true;
            }
        }

        if (showDialog) {
            WKDialogUtils.getInstance().showSingleBtnDialog(this, "", getString(R.string.cannot_reply_msg), "", null);
            return;
        }

        if ((channelType == WKChannelType.GROUP || channelType == WKChannelType.COMMUNITY_TOPIC) && !TextUtils.isEmpty(wkMsg.fromUID) && !wkMsg.fromUID.equals(loginUID)) {
            WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, wkMsg.fromUID);
            if (member != null) {
                chatPanelManager.addSpan(member.memberName, member.memberUID);
            } else {
                WKChannel mChannel = WKIM.getInstance().getChannelManager().getChannel(wkMsg.fromUID, WKChannelType.PERSONAL);
                if (mChannel != null) {
                    chatPanelManager.addSpan(mChannel.channelName, mChannel.channelID);
                }
            }
//            WKVBinding.toolbarView.editText.addAtSpan("@", member.memberName, member.memberUID);
        }
        this.replyWKMsg = wkMsg;
        if (replyWKMsg != null) {
            chatPanelManager.showReplyLayout(replyWKMsg);
        }

    }

    @Override
    public void showEdit(WKMsg wkMsg) {
        boolean showDialog = false;
        WKChannelMember mChannelMember = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, loginUID);
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);
        if (channel != null && mChannelMember != null) {
            if ((channel.forbidden == 1 && mChannelMember.role == WKChannelMemberRole.normal) || mChannelMember.forbiddenExpirationTime > 0) {
                //普通成员
                showDialog = true;
            }
        }

        if (showDialog) {
            WKDialogUtils.getInstance().showSingleBtnDialog(this, "", getString(R.string.cannot_edit_msg), "", null);
            return;
        }
        this.replyWKMsg = null;
        if (wkMsg != null) {
            this.editMsg = wkMsg;
            chatPanelManager.showEditLayout(wkMsg);
        }

    }

    @Override
    public void tipsMsg(String clientMsgNo) {

        isTipMessage = true;
        int index = -1;
        for (int i = 0, size = chatAdapter.getData().size(); i < size; i++) {
            if (chatAdapter.getData().get(i).wkMsg != null && chatAdapter.getData().get(i).wkMsg.clientMsgNO.equals(clientMsgNo)) {
                chatAdapter.getData().get(i).isShowTips = true;
                index = i;
                break;
            }
        }
        if (index != -1) {
            final int targetIndex = index;
            // 先收起键盘，等布局稳定后再滚动定位，避免键盘影响滚动位置计算
            mHelper.hookSystemBackByPanelSwitcher();
            wkVBinding.recyclerView.postDelayed(() -> {
                int lastItemPosition = linearLayoutManager.findLastVisibleItemPosition();
                int firstItemPosition = linearLayoutManager.findFirstVisibleItemPosition();
                if (targetIndex < firstItemPosition || targetIndex > lastItemPosition) {
                    linearLayoutManager.scrollToPositionWithOffset(targetIndex, AndroidUtilities.dp(70));
                }
                chatAdapter.notifyItemChanged(targetIndex);
            }, 250);
        } else {
            mHelper.hookSystemBackByPanelSwitcher();
            WKMsg msg = WKIM.getInstance().getMsgManager().getWithClientMsgNO(clientMsgNo);
            if (msg != null && msg.isDeleted == 0) {
                unreadStartMsgOrderSeq = 0;
                tipsOrderSeq = msg.orderSeq;
                // YUJ-256 P1-2: sync snapshots + reset render flags for the
                // tipsMsg jump so the second (post-sync) callback still
                // positions on the target message.
                unreadStartSnapshotOrderSeq = 0;
                tipsSnapshotOrderSeq = msg.orderSeq;
                hasRenderedPreview = false;
                userHasScrolled = false;
                hasShownTips = false;
                // keepMessageSeq = msg.orderSeq;
                getData(0, true, msg.orderSeq, true);
                isCanLoadMore = true;
            } else {
                showToast(R.string.cannot_tips_msg);
            }
        }

    }

    @Override
    public void setEditContent(String content) {

        int curPosition = chatPanelManager.getEditText().getSelectionStart();
        StringBuilder sb = new StringBuilder(Objects.requireNonNull(chatPanelManager.getEditText().getText()).toString());
        sb.insert(curPosition, content);
        chatPanelManager.getEditText().setText(MoonUtil.getEmotionContent(this, chatPanelManager.getEditText(), sb.toString()));
        // 将光标设置到新增完表情的右侧
        chatPanelManager.getEditText().setSelection(curPosition + content.length());

    }

    @Override
    public AppCompatActivity getChatActivity() {
        return this;
    }

    @Override
    public WKMsg getReplyMsg() {
        return replyWKMsg;
    }

    @Override
    public void hideSoftKeyboard() {
        mHelper.hookSystemBackByPanelSwitcher();
    }

    @Override
    public ChatAdapter getChatAdapter() {
        return chatAdapter;
    }

    @Override
    public void sendCardMsg() {

        Intent intent = new Intent(this, ChooseContactsActivity.class);
        intent.putExtra("chooseBack", true);
        intent.putExtra("singleChoose", true);
        if (channelType == WKChannelType.PERSONAL) {
            intent.putExtra("unVisibleUIDs", channelId);
        }
        chooseCardResultLac.launch(intent);
    }

    @Override
    public void chooseFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        chooseFileResultLac.launch(intent);
    }

    @Override
    public void chatRecyclerViewScrollToEnd() {
        if (isToEnd) {
            scrollToEnd();
        }

    }

    @Override
    public void deleteOperationMsg() {

        this.replyWKMsg = null;
        this.editMsg = null;
    }

    @Override
    public void onChatAvatarClick(String uid, boolean isLongClick) {
        chatPanelManager.chatAvatarClick(uid, isLongClick);
    }

    @Override
    public void onViewPicture(boolean isViewing) {
        isViewingPicture = isViewing;
    }

    @Override
    public void onMsgViewed(WKMsg wkMsg, int position) {
        if (wkMsg == null) return;
        if (!TextUtils.isEmpty(wkMsg.messageID) && !isTipMessage) {
            EndpointManager.getInstance().invoke("tip_pinned_message", wkMsg.messageID);
        }
        if (wkMsg.flame == 1 && wkMsg.viewed == 0 && wkMsg.type != WKContentType.WK_IMAGE && wkMsg.type != WKContentType.WK_VIDEO && wkMsg.type != WKContentType.WK_VOICE) {

            wkMsg.viewed = 1;
            wkMsg.viewedAt = WKTimeUtils.getInstance().getCurrentMills();
            chatAdapter.updateDeleteTimer(position);
            WKIM.getInstance().getMsgManager().updateViewedAt(1, wkMsg.viewedAt, wkMsg.clientMsgNO);
        }
        if (wkMsg.viewed == 0 && wkMsg.type == WKContentType.WK_TEXT) {
            wkMsg.viewed = 1;
        }

        if (wkMsg.remoteExtra.readed == 0 && wkMsg.setting != null && wkMsg.setting.receipt == 1 && !TextUtils.isEmpty(wkMsg.fromUID) && !wkMsg.fromUID.equals(loginUID)) {
            boolean isAdd = true;
            for (int j = 0, size = readMsgIds.size(); j < size; j++) {
                if (readMsgIds.get(j).equals(wkMsg.messageID)) {
                    isAdd = false;
                    break;
                }
            }
            if (isAdd) {
                readMsgIds.add(wkMsg.messageID);
            }
        }
        boolean isResetRemind = false;
        if (WKReader.isNotEmpty(reminderList) && !TextUtils.isEmpty(wkMsg.messageID)) {
            for (int j = 0; j < reminderList.size(); j++) {
                if (reminderList.get(j).messageID.equals(wkMsg.messageID)) {
                    if (reminderList.get(j).done == 0) {
                        reminderIds.add(reminderList.get(j).reminderID);
                    }
                    reminderList.remove(j);
                    j = j - 1;
                    isResetRemind = true;
                }
            }
        }

        boolean isResetGroupApprove = false;
        if (WKReader.isNotEmpty(groupApproveList) && !TextUtils.isEmpty(wkMsg.messageID)) {
            for (int j = 0, size = groupApproveList.size(); j < size; j++) {
                if (groupApproveList.get(j).messageID.equals(wkMsg.messageID) && groupApproveList.get(j).done == 0) {
                    reminderIds.add(groupApproveList.get(j).reminderID);
                    groupApproveList.remove(j);
                    isResetGroupApprove = true;
                    break;
                }
            }
        }

        // 保存最新浏览到的位置
        if (wkMsg.messageSeq > browseTo) {
            browseTo = wkMsg.messageSeq;
        }
        boolean isResetUnread = false;
        if (wkMsg.messageSeq > lastVisibleMsgSeq) {
            lastVisibleMsgSeq = wkMsg.messageSeq;
        }
        if (lastVisibleMsgSeq != 0) {
            long lastVisibleMsgOrderSeq = WKIM.getInstance().getMsgManager().getMessageOrderSeq(lastVisibleMsgSeq, channelId, channelType);
            if (lastVisibleMsgOrderSeq < unreadStartMsgOrderSeq) {
                lastVisibleMsgSeq = (int) WKIM.getInstance().getMsgManager().getReliableMessageSeq(unreadStartMsgOrderSeq);
                lastVisibleMsgSeq = lastVisibleMsgSeq - 1;
            }
        }
        if (redDot > 0) {
            if (lastVisibleMsgSeq != 0) {
                redDot = maxMsgSeq - lastVisibleMsgSeq;
            }
            if (redDot < 0) redDot = 0;
            isResetUnread = true;

        }

        if (isResetGroupApprove) {
            resetGroupApproveView();
        }
        if (isResetUnread) {
            showUnReadCountView();
        }
        if (isResetRemind) {
            resetRemindView();
        }
    }

    @Override
    public View getRecyclerViewLayout() {
        return wkVBinding.recyclerViewLayout;
    }

    @Override
    public boolean isShowChatActivity() {
        return isShowChatActivity;
    }

    @Override
    public void closeActivity() {
        finish();
    }

    @Override
    public void finish() {
        // YUJ-305 P1-A · swipe-back 兜底：系统 / 第三方 SwipeBackLayout 的侧滑返回不走
        // onBackPressed 分发链，会直接调 Activity.finish()，绕过 setBackListener() →
        // persistCurrentChannelEditState() 的主动落盘点。这里在 super.finish() 之前再
        // flush 一次编辑态（幂等），保证草稿 / 浏览位置 / 阅后即焚 / readMsg 上报不丢。
        // 正常的 soft-back / 非 swipe 返回路径已经在 setBackListener 里 flush 过，此处
        // 再调一次也是 no-op（读到空 diff、写一遍 DB，可以接受）。
        persistCurrentChannelEditState();
        if (com.chat.base.space.SystemBotsFallback.isSystemBot(channelId)) {
            SpaceModel.getInstance().invalidateMembersCache();
            EndpointManager.getInstance().invoke(WKConstants.refreshContacts, null);
        }
        super.finish();
        // YUJ-278 P1-3：快进慢出对称。入场 120ms 后，非 swipe 返回（系统 back /
        // 工具栏返回按钮 / finish() 直调）若不覆盖仍走系统默认 ~250-350ms。
        // 这里用反向 slide pair 把返回也压到 120ms。pre-34 走 overridePendingTransition
        // 必须在 super.finish() 之后调；API 34+ 已由 NarrowTransition.applyFastOpen()
        // 在 onCreate 里一次性注册过 CLOSE override，这里是 no-op。
        NarrowTransition.applyFastClose(this);
        SoftKeyboardUtils.getInstance().hideSoftKeyboard(this);
        EndpointManager.getInstance().remove(channelId);
        EndpointManager.getInstance().invoke("stop_screen_shot", this);
        WKIM.getInstance().getMsgManager().removeDeleteMsgListener(channelId);
        WKIM.getInstance().getMsgManager().removeNewMsgListener(channelId);
        WKIM.getInstance().getMsgManager().removeRefreshMsgListener(channelId);
        WKIM.getInstance().getMsgManager().removeSendMsgCallBack(channelId);
        WKIM.getInstance().getChannelManager().removeRefreshChannelInfo(channelId);
        WKIM.getInstance().getChannelMembersManager().removeRefreshChannelMemberInfo(channelId);
        WKIM.getInstance().getChannelMembersManager().removeAddChannelMemberListener(channelId);
        WKIM.getInstance().getChannelMembersManager().removeRemoveChannelMemberListener(channelId);
        WKIM.getInstance().getCMDManager().removeCmdListener(channelId);
        WKIM.getInstance().getMsgManager().removeSendMsgAckListener(channelId);
        WKIM.getInstance().getMsgManager().removeClearMsg(channelId);
        WKIM.getInstance().getRobotManager().removeRefreshRobotMenu(channelId);
        WKIM.getInstance().getReminderManager().removeNewReminderListener(channelId);
        WKIM.getInstance().getMsgManager().removeNewMsgListener("thread_count_" + channelId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messageEffectManager != null) {
            messageEffectManager.destroy();
            messageEffectManager = null;
        }
        if (messageEffectOverlay != null) {
            FrameLayout contentRoot = findViewById(android.R.id.content);
            if (contentRoot != null) {
                contentRoot.removeView(messageEffectOverlay);
            }
            messageEffectOverlay = null;
        }
        chatPanelManager.onDestroy();
        // YUJ-267 · 移除 WKIM 各 Manager 的监听（channel-keyed），防止单例持有 Activity
        // 引用导致泄漏。抽到 detachChannelListeners() 与 onNewIntent 复用路径共用，
        // 避免两条路径清理矩阵漂移。
        detachChannelListeners(channelId);
        // YUJ-324 · 反注册 Space 变化监听，避免 process-scope 列表长期持有本实例
        // 引用（lambda 捕获 this.lastKnownSpaceId / this.channelId 字段访问通过
        // 合成类持有 outer ChatActivity 引用）。
        if (spaceChangedListener != null) {
            SpaceChangedBroadcaster.removeListener(spaceChangedListener);
            spaceChangedListener = null;
        }
        WKVoiceViewManager.getInstance().release();
        EndpointManager.getInstance().remove("hide_pinned_view");
        EndpointManager.getInstance().remove("show_pinned_view");
        EndpointManager.getInstance().remove("tip_msg_in_chat");
        EndpointManager.getInstance().remove("reset_channel_all_pinned_msg");
        ActManagerUtils.getInstance().removeActivity(this);
        if (disposable != null) {
            disposable.dispose();
            disposable = null;
        }
        if (WKReader.isNotEmpty(readMsgIds)) {
            EndpointManager.getInstance().invoke("read_msg", new ReadMsgMenu(channelId, channelType, readMsgIds));
        }
        MsgModel.getInstance().startCheckFlameMsgTimer();
        saveEditContent();
        cleanFilePickCache();
    }

    private void saveEditContent() {
        if (WKReader.isEmpty(chatAdapter.getData())) {
            return;
        }
        //停止语音播放
        //AudioPlaybackManager.getInstance().stopAudio();
        int firstItemPosition = linearLayoutManager.findFirstVisibleItemPosition();
        int endItemPosition = linearLayoutManager.findLastVisibleItemPosition();
        long keepMsgSeq = 0;
        int offsetY = 0;
        if (endItemPosition != chatAdapter.getData().size() - 1) {
            WKMsg msg = chatAdapter.getFirstVisibleItem(firstItemPosition);
            if (msg != null) {
                keepMsgSeq = msg.messageSeq;
                int index = chatAdapter.getFirstVisibleItemIndex(firstItemPosition);
                View view = linearLayoutManager.findViewByPosition(index);
                if (view != null) {
                    offsetY = view.getTop();
                }
            }
        }
//        int unreadCount = wkVBinding.chatUnreadLayout.msgCountTv.getCount();
        MsgModel.getInstance().clearUnread(channelId, channelType, redDot, null);
        String content = Objects.requireNonNull(chatPanelManager.getEditText().getText()).toString();
        MsgModel.getInstance().updateCoverExtra(channelId, channelType, browseTo, keepMsgSeq, offsetY, content);
        MsgModel.getInstance().deleteFlameMsg();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return setBackListener();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 对齐 iOS viewWillDisappear：在父页面 onResume 之前同步标记已读，避免列表闪烁
        MsgModel.getInstance().doneReminder(reminderIds);
    }

    @Override
    protected void onStop() {
        super.onStop();
        isShowChatActivity = false;
        WKUIKitApplication.getInstance().chattingChannelID = "";
        isUploadReadMsg = false;
        WKPlayVoiceUtils.getInstance().stopPlay();
        EndpointManager.getInstance().invoke("stop_screen_shot", this);
    }


    ActivityResultLauncher<Intent> previewNewImgResultLac = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getData() != null && result.getResultCode() == Activity.RESULT_OK) {
            String path = result.getData().getStringExtra("path");
            if (!TextUtils.isEmpty(path)) {
                sendMsg(new WKImageContent(path));
            }
        }
    });
    ActivityResultLauncher<Intent> chooseCardResultLac = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getData() != null && result.getResultCode() == Activity.RESULT_OK) {
            String uid = result.getData().getStringExtra("uid");
            if (!TextUtils.isEmpty(uid)) {
                WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
                WKCardContent WKCardContent = new WKCardContent();
                WKCardContent.name = channel.channelName;
                WKCardContent.uid = channel.channelID;
                if (channel.remoteExtraMap != null && channel.remoteExtraMap.containsKey(WKChannelExtras.vercode))
                    WKCardContent.vercode = (String) channel.remoteExtraMap.get(WKChannelExtras.vercode);
                List<WKMessageContent> messageContentList = new ArrayList<>();
                messageContentList.add(WKCardContent);
                List<WKChannel> list = new ArrayList<>();
                list.add(WKIM.getInstance().getChannelManager().getChannel(channelId, channelType));
                WKUIKitApplication.getInstance().showChatConfirmDialog(ChatActivity.this, list, messageContentList, (list1, messageContentList1) -> sendMsg(WKCardContent));
            }
        }
    });
    ActivityResultLauncher<Intent> chooseFileResultLac = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getData() != null && result.getResultCode() == Activity.RESULT_OK) {
            android.net.Uri uri = result.getData().getData();
            if (uri != null) {
                handleFileResult(uri);
            }
        }
    });

    private void handleFileResult(android.net.Uri uri) {
        try {
            String filePath = WKFileUtils.getInstance().getChooseFileResultPath(this, uri);
            // 验证解析出的路径是否真正可读（Android 10+ Scoped Storage 下可能无权限）
            if (!TextUtils.isEmpty(filePath)) {
                java.io.File checkFile = new java.io.File(filePath);
                if (!checkFile.exists() || !checkFile.canRead() || checkFile.length() == 0) {
                    filePath = null; // 路径不可用，走 ContentResolver 复制
                }
            }
            if (TextUtils.isEmpty(filePath)) {
                // Copy file from content URI to local cache
                String fileName = "file_" + System.currentTimeMillis();
                android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }
                    cursor.close();
                }
                java.io.File cacheDir = new java.io.File(getCacheDir(), "file_pick");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                java.io.File destFile = new java.io.File(cacheDir, fileName);
                java.io.InputStream is = getContentResolver().openInputStream(uri);
                if (is != null) {
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile);
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                    is.close();
                    filePath = destFile.getAbsolutePath();
                }
            }
            if (!TextUtils.isEmpty(filePath)) {
                java.io.File file = new java.io.File(filePath);
                if (file.exists()) {
                    // Check file size limit (100MB)
                    long maxSize = 100L * 1024 * 1024;
                    if (file.length() > maxSize) {
                        WKToastUtils.getInstance().showToastNormal(getString(R.string.str_file_too_large));
                        return;
                    }
                    // Check dangerous file extensions
                    String name = file.getName();
                    int dotIndex = name.lastIndexOf('.');
                    String ext = dotIndex > 0 ? name.substring(dotIndex + 1).toLowerCase(java.util.Locale.getDefault()) : "";
                    java.util.Set<String> dangerousExts = new java.util.HashSet<>(java.util.Arrays.asList(
                            "exe", "bat", "cmd", "sh", "msi", "apk", "dex", "jsp", "cgi", "scr", "com", "pif", "vbs", "ws", "wsf"
                    ));
                    if (dangerousExts.contains(ext)) {
                        WKToastUtils.getInstance().showToastNormal(getString(R.string.str_file_type_dangerous));
                        return;
                    }
                    WKFileContent fileContent = new WKFileContent();
                    fileContent.localPath = filePath;
                    fileContent.name = name;
                    fileContent.extension = ext;
                    fileContent.size = file.length();
                    sendMsg(fileContent);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cleanFilePickCache() {
        try {
            java.io.File cacheDir = new java.io.File(getCacheDir(), "file_pick");
            if (cacheDir.exists() && cacheDir.isDirectory()) {
                java.io.File[] files = cacheDir.listFiles();
                if (files != null) {
                    for (java.io.File f : files) {
                        f.delete();
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private synchronized void sendMsgInserted(WKMsg msg) {
        if (msg.channelType == channelType && msg.channelID.equals(channelId) && msg.isDeleted == 0 && !msg.header.noPersist) {
            safeAdapterAction(() -> doSendMsgInserted(msg));
        }
    }

    private void doSendMsgInserted(WKMsg msg) {
        if (msg.orderSeq > maxMsgOrderSeq) {
            maxMsgOrderSeq = msg.orderSeq;
        }
        WKMsg timeMsg = addTimeMsg(msg.timestamp);
        //判断当前会话是否存在正在输入
        int index = chatAdapter.getData().size() - 1;
        if (chatAdapter.lastMsgIsTyping()) index--;
        if (index < 0) index = 0;
        WKUIChatMsgItemEntity itemEntity = WKIMUtils.getInstance().msg2UiMsg(this, msg, count, showNickName, chatAdapter.isShowChooseItem());
        if (timeMsg == null) {
            if (WKReader.isNotEmpty(chatAdapter.getData())) {
                chatAdapter.getData().get(index).nextMsg = msg;
                itemEntity.previousMsg = chatAdapter.getData().get(index).wkMsg;
            }
        } else {
            chatAdapter.getData().get(index).nextMsg = timeMsg;
            itemEntity.previousMsg = timeMsg;
        }
        chatAdapter.addData(index + 1, itemEntity);
        int type = chatAdapter.getData().get(index).wkMsg.type;
        if (WKContentType.isLocalMsg(type) || WKContentType.isSystemMsg(type)) {
            chatAdapter.notifyItemChanged(index);
        } else {
            chatAdapter.notifyBackground(index);
        }

        if (isToEnd) {
            scrollToEnd();
        }
        isToEnd = true;
    }

    /**
     * 过滤 1:1 私聊消息的 Space 隔离（对齐 iOS filterMessagesBySpace:）
     * 规则：payload 有 space_id 且匹配当前 Space → 显示
     *       payload 有 space_id 且不匹配 → 隐藏
     *       payload 无 space_id + 系统 Bot（BotFather）→ 隐藏（避免旧消息跨 Space 可见）
     *       payload 无 space_id + 普通用户 → 显示（向前兼容）
     */
    private List<WKMsg> filterSystemBotMessages(List<WKMsg> messages) {
        if (channelType != WKChannelType.PERSONAL) {
            return messages;
        }
        String currentSpaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (TextUtils.isEmpty(currentSpaceId)) {
            return messages;
        }
        boolean isSystemBot = com.chat.base.space.SystemBotsFallback.isSystemBot(channelId);
        List<WKMsg> filtered = new ArrayList<>();
        for (WKMsg msg : messages) {
            String msgSpaceId = getSpaceIdFromMsg(msg);
            if (msgSpaceId == null || msgSpaceId.isEmpty()) {
                // 无 space_id：系统 Bot 在空间模式下隐藏，普通聊天向前兼容显示
                if (!isSystemBot) {
                    filtered.add(msg);
                }
            } else if (msgSpaceId.equals(currentSpaceId)) {
                filtered.add(msg); // 匹配当前 Space
            }
        }
        return filtered;
    }

    /**
     * 从消息中提取 space_id，尝试多种途径：
     * 1. msg.content (原始 payload JSON 字符串)
     * 2. msg.baseContentMsgModel.spaceId (SDK 解码时已填充)
     */
    private String getSpaceIdFromMsg(WKMsg msg) {
        // 1. 从 content 原始 JSON 解析
        if (!TextUtils.isEmpty(msg.content)) {
            try {
                org.json.JSONObject json = new org.json.JSONObject(msg.content);
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

    private synchronized void receivedMessages(List<WKMsg> list) {
        list = filterSystemBotMessages(list);
        if (!WKReader.isNotEmpty(list)) return;

        // 收集需要批量添加的 items 和需要刷新背景的索引
        List<WKUIChatMsgItemEntity> pendingItems = new ArrayList<>();
        List<Integer> backgroundRefreshIndices = new ArrayList<>();
        boolean needScrollToEnd = false;
        boolean needPlaySound = false;
        boolean typingRemoved = false;

        for (WKMsg msg : list) {
            // 命令消息和撤回消息不显示在聊天
            if (msg.type == WKContentType.WK_INSIDE_MSG || msg.type == WKContentType.withdrawSystemInfo || msg.isDeleted == 1 || msg.header.noPersist)
                continue;

            if (msg.remoteExtra.readedCount == 0) {
                msg.remoteExtra.unreadCount = count - 1;
            }
            if (!msg.channelID.equals(channelId) || msg.channelType != channelType)
                continue;
            if (chatAdapter.isExist(msg.clientMsgNO, msg.messageID))
                continue;

            if (!isCanLoadMore) {
                // 移除正在输入（只移除一次）
                if (!typingRemoved && chatAdapter.getItemCount() > 0) {
                    WKUIChatMsgItemEntity lastItem = chatAdapter.getData().get(chatAdapter.getItemCount() - 1);
                    if (lastItem.wkMsg != null && lastItem.wkMsg.type == WKContentType.typing) {
                        chatAdapter.getData().remove(chatAdapter.getItemCount() - 1);
                        typingRemoved = true;
                    }
                }

                // 构建时间消息（不直接添加到 adapter，加入 pendingItems）
                WKMsg timeMsg = buildTimeMsg(msg.timestamp, pendingItems);

                WKUIChatMsgItemEntity itemEntity = WKIMUtils.getInstance().msg2UiMsg(this, msg, count, showNickName, chatAdapter.isShowChooseItem());

                // 计算 previousMsg / nextMsg 链接关系
                // 最后一个已有数据 = adapter 现有数据 + 已收集的 pendingItems
                WKUIChatMsgItemEntity lastExisting = getLastItem(pendingItems);
                int previousMsgIndex = -1;

                if (timeMsg != null) {
                    // 时间消息刚加入 pendingItems，更新其前一条的 nextMsg
                    if (pendingItems.size() >= 2) {
                        pendingItems.get(pendingItems.size() - 2).nextMsg = timeMsg;
                    } else if (WKReader.isNotEmpty(chatAdapter.getData())) {
                        chatAdapter.getData().get(chatAdapter.getData().size() - 1).nextMsg = timeMsg;
                    }
                    itemEntity.previousMsg = timeMsg;
                } else {
                    if (lastExisting != null) {
                        itemEntity.previousMsg = lastExisting.wkMsg;
                        lastExisting.nextMsg = itemEntity.wkMsg;
                    }
                }

                // 记录需要刷新背景的索引（adapter 中最后一条现有数据）
                if (pendingItems.isEmpty() && WKReader.isNotEmpty(chatAdapter.getData())) {
                    previousMsgIndex = chatAdapter.getData().size() - 1;
                }

                if (!isShowHistory && redDot == 0 && itemEntity.wkMsg.flame == 1 && itemEntity.wkMsg.type != WKContentType.WK_VOICE && itemEntity.wkMsg.type != WKContentType.WK_IMAGE && itemEntity.wkMsg.type != WKContentType.WK_VIDEO) {
                    itemEntity.wkMsg.viewed = 1;
                    itemEntity.wkMsg.viewedAt = WKTimeUtils.getInstance().getCurrentMills();
                    WKIM.getInstance().getMsgManager().updateViewedAt(1, itemEntity.wkMsg.viewedAt, itemEntity.wkMsg.clientMsgNO);
                }

                needPlaySound = true;
                pendingItems.add(itemEntity);

                if (msg.messageSeq > maxMsgSeq) {
                    maxMsgSeq = msg.messageSeq;
                }
                if (msg.orderSeq > maxMsgOrderSeq) {
                    maxMsgOrderSeq = msg.orderSeq;
                }
                if (previousMsgIndex != -1) {
                    backgroundRefreshIndices.add(previousMsgIndex);
                }
            }

            if (isShowHistory || redDot > 0) {
                redDot += 1;
                showUnReadCountView();
            } else {
                needScrollToEnd = true;
                if (msg.setting.receipt == 1) readMsgIds.add(msg.messageID);
            }
        }

        // 批量提交到 adapter，确保不在 RecyclerView 布局期间执行
        final boolean finalTypingRemoved = typingRemoved;
        final boolean finalNeedPlaySound = needPlaySound;
        final boolean finalNeedScrollToEnd = needScrollToEnd;
        final int finalRedDot = redDot;
        safeAdapterAction(() -> {
            if (finalTypingRemoved) {
                chatAdapter.notifyItemRemoved(chatAdapter.getItemCount());
            }
            if (finalNeedPlaySound) {
                WKPlaySound.getInstance().playInMsg(R.raw.sound_in);
            }
            if (WKReader.isNotEmpty(pendingItems)) {
                chatAdapter.addData(pendingItems);
                chatAdapter.rebuildIndex();
                trimTopIfNeeded();
            }
            for (int idx : backgroundRefreshIndices) {
                if (idx >= 0 && idx < chatAdapter.getData().size()) {
                    chatAdapter.notifyBackground(idx);
                }
            }
            if (finalRedDot > 0) {
                wkVBinding.chatUnreadLayout.newMsgLayout.post(() -> CommonAnim.getInstance().showOrHide(wkVBinding.chatUnreadLayout.newMsgLayout, true, true, false));
            }
            if (finalNeedScrollToEnd) {
                scrollToEnd();
            }
        });
    }

    /**
     * 构建时间消息实体（不直接操作 adapter），加入 pendingItems 列表。
     * 判断是否需要时间分隔基于 adapter 现有数据 + 已收集的 pendingItems。
     */
    private WKMsg buildTimeMsg(long newMsgTime, List<WKUIChatMsgItemEntity> pendingItems) {
        // 取最后一条时间：优先从 pendingItems 找，否则用 adapter 的
        long lastMsgTime;
        if (WKReader.isNotEmpty(pendingItems)) {
            lastMsgTime = pendingItems.get(pendingItems.size() - 1).wkMsg.timestamp;
            // 向前搜索 pendingItems 中的时间消息
            for (int i = pendingItems.size() - 1; i >= 0; i--) {
                if (pendingItems.get(i).wkMsg.type == WKContentType.msgPromptTime) {
                    lastMsgTime = pendingItems.get(i).wkMsg.timestamp;
                    break;
                }
            }
        } else {
            lastMsgTime = chatAdapter.getLastTimeMsg();
        }

        if (!WKTimeUtils.getInstance().isSameDay(newMsgTime, lastMsgTime)) {
            WKUIChatMsgItemEntity uiChatMsgEntity = new WKUIChatMsgItemEntity(this, null, null);
            WKMsg msg = new WKMsg();
            uiChatMsgEntity.wkMsg = msg;
            uiChatMsgEntity.isChoose = (chatAdapter.getItemCount() > 0 && chatAdapter.getData().get(0).isChoose);
            uiChatMsgEntity.wkMsg.type = WKContentType.msgPromptTime;
            uiChatMsgEntity.wkMsg.content = WKTimeUtils.getInstance().getShowDate(newMsgTime * 1000);
            uiChatMsgEntity.wkMsg.timestamp = WKTimeUtils.getInstance().getCurrentSeconds();
            pendingItems.add(uiChatMsgEntity);
            return msg;
        }
        return null;
    }

    /**
     * 获取逻辑上的"最后一条"item：优先从 pendingItems 取，否则从 adapter 取。
     */
    private WKUIChatMsgItemEntity getLastItem(List<WKUIChatMsgItemEntity> pendingItems) {
        if (WKReader.isNotEmpty(pendingItems)) {
            return pendingItems.get(pendingItems.size() - 1);
        }
        if (WKReader.isNotEmpty(chatAdapter.getData())) {
            return chatAdapter.getData().get(chatAdapter.getData().size() - 1);
        }
        return null;
    }

    private synchronized void typing(WKCMD wkCmd) {

        if (redDot > 0) return;
        String channel_id = wkCmd.paramJsonObject.optString("channel_id");
        byte channel_type = (byte) wkCmd.paramJsonObject.optInt("channel_type");
        String from_uid = wkCmd.paramJsonObject.optString("from_uid");
        String from_name = wkCmd.paramJsonObject.optString("from_name");
        int isRobot;
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(from_uid, WKChannelType.PERSONAL);
        if (channel == null) {
            channel = new WKChannel(from_uid, WKChannelType.PERSONAL);
            channel.channelName = from_name;
        }
        isRobot = channel.robot;
        if (channelId.equals(channel_id) && channelType == channel_type && !TextUtils.equals(from_uid, loginUID)) {
            WKChannelMember mChannelMember = null;
            if (channelType == WKChannelType.GROUP && isRobot == 0) {
                // 没在群内的cmd不显示
                mChannelMember = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, from_uid);
                if (mChannelMember == null || mChannelMember.isDeleted == 1) return;
            }
            final WKChannelMember finalMember = mChannelMember;
            final WKChannel finalChannel = channel;
            safeAdapterAction(() -> doTyping(finalChannel, from_uid, finalMember));
        }
    }

    private void doTyping(WKChannel channel, String from_uid, WKChannelMember mChannelMember) {
        if (chatAdapter.getItemCount() > 0 && chatAdapter.getData().get(chatAdapter.getItemCount() - 1).wkMsg.type == WKContentType.typing) {
            chatAdapter.getData().get(chatAdapter.getItemCount() - 1).wkMsg.setFrom(channel);
            chatAdapter.getData().get(chatAdapter.getItemCount() - 1).wkMsg.fromUID = from_uid;
            chatAdapter.getData().get(chatAdapter.getItemCount() - 1).wkMsg.setMemberOfFrom(mChannelMember);
            chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
        } else {
            addTimeMsg(WKTimeUtils.getInstance().getCurrentSeconds());
            int index = chatAdapter.getData().size() - 1;
            if (chatAdapter.lastMsgIsTyping()) index--;
            if (index < 0) index = 0;

            WKUIChatMsgItemEntity msgItemEntity = new WKUIChatMsgItemEntity(this, new WKMsg(), null);
            msgItemEntity.wkMsg.channelType = channelType;
            msgItemEntity.wkMsg.channelID = channelId;
            msgItemEntity.wkMsg.type = WKContentType.typing;
            msgItemEntity.wkMsg.setFrom(channel);
            msgItemEntity.showNickName = showNickName;
            msgItemEntity.wkMsg.fromUID = channel.channelID;
            WKChannelMember member = new WKChannelMember();
            member.memberUID = channel.channelID;
            member.channelID = channelId;
            member.channelType = channelType;
            member.memberName = channel.channelName;
            member.memberRemark = channel.channelRemark;
            msgItemEntity.wkMsg.setMemberOfFrom(member);
            msgItemEntity.previousMsg = chatAdapter.getLastMsg();
            chatAdapter.addData(msgItemEntity);
            chatAdapter.getData().get(index).nextMsg = msgItemEntity.wkMsg;

            int type = chatAdapter.getData().get(index).wkMsg.type;
            if (WKContentType.isLocalMsg(type) || WKContentType.isSystemMsg(type)) {
                chatAdapter.notifyItemChanged(index);
            } else {
                chatAdapter.notifyBackground(index);
            }

            if (!isShowHistory && !isCanLoadMore) {
                scrollToEnd();
            }
        }
    }

    private synchronized void refreshMsg(WKMsg wkMsg) {
        WKIMUtils.getInstance().resetMsgProhibitWord(wkMsg);
        List<WKUIChatMsgItemEntity> list = chatAdapter.getData();
        chatAdapter.refreshReplyMsg(wkMsg);
        int i = chatAdapter.findPositionByMsg(wkMsg);
        if (i >= 0 && i < list.size()) {
            {
                boolean isNotify = false;
                if (wkMsg.messageSeq > maxMsgSeq) {
                    maxMsgSeq = wkMsg.messageSeq;
                }
                if (wkMsg.messageSeq > lastVisibleMsgSeq) {
                    lastVisibleMsgSeq = wkMsg.messageSeq;
                }
                if (list.get(i).wkMsg.remoteExtra.revoke != wkMsg.remoteExtra.revoke) {
                    isNotify = true;
                }
                // 消息撤回
                list.get(i).wkMsg.remoteExtra.revoke = wkMsg.remoteExtra.revoke;
                list.get(i).wkMsg.remoteExtra.revoker = wkMsg.remoteExtra.revoker;
                if (list.get(i).wkMsg.status != WKSendMsgResult.send_success && wkMsg.status == WKSendMsgResult.send_success) {
                    WKPlaySound.getInstance().playOutMsg(R.raw.sound_out);
                }
                boolean isResetStatus = false;
                boolean isResetListener = false;
                boolean isResetData = false;
                boolean isResetReaction = false;
                if (list.get(i).wkMsg.status != wkMsg.status
                        || (list.get(i).wkMsg.remoteExtra.readedCount != wkMsg.remoteExtra.readedCount && list.get(i).wkMsg.remoteExtra.readedCount == 0)
                        || list.get(i).wkMsg.remoteExtra.editedAt != wkMsg.remoteExtra.editedAt
                ) {
                    list.get(i).isUpdateStatus = true;
                    isResetStatus = true;
                }
                if (list.get(i).wkMsg.remoteExtra.isPinned != wkMsg.remoteExtra.isPinned) {
                    isResetStatus = true;
                }
                list.get(i).wkMsg.voiceStatus = wkMsg.voiceStatus;

                if (hideChannelAllPinnedMessage == 0) {
                    list.get(i).isPinned = wkMsg.remoteExtra.isPinned;
                } else {
                    list.get(i).isPinned = 0;
                }
                if (list.get(i).wkMsg.remoteExtra.readedCount != wkMsg.remoteExtra.readedCount && !isResetStatus) {
                    isResetListener = true;
                }
                list.get(i).wkMsg.remoteExtra.isPinned = wkMsg.remoteExtra.isPinned;
                list.get(i).wkMsg.remoteExtra.readed = wkMsg.remoteExtra.readed;
                list.get(i).wkMsg.remoteExtra.readedCount = wkMsg.remoteExtra.readedCount;
                list.get(i).wkMsg.remoteExtra.needUpload = wkMsg.remoteExtra.needUpload;
                if (list.get(i).wkMsg.remoteExtra.readedCount == 0) {
                    list.get(i).wkMsg.remoteExtra.unreadCount = count - 1;
                } else
                    list.get(i).wkMsg.remoteExtra.unreadCount = wkMsg.remoteExtra.unreadCount;
                if ((TextUtils.isEmpty(list.get(i).wkMsg.remoteExtra.contentEdit) && !TextUtils.isEmpty(wkMsg.remoteExtra.contentEdit)) || (!TextUtils.isEmpty(list.get(i).wkMsg.remoteExtra.contentEdit) && !TextUtils.isEmpty(wkMsg.remoteExtra.contentEdit) && !list.get(i).wkMsg.remoteExtra.contentEdit.equals(wkMsg.remoteExtra.contentEdit))) {
                    list.get(i).wkMsg.remoteExtra.editedAt = wkMsg.remoteExtra.editedAt;
                    list.get(i).wkMsg.remoteExtra.contentEdit = wkMsg.remoteExtra.contentEdit;
                    list.get(i).wkMsg.remoteExtra.contentEditMsgModel = wkMsg.remoteExtra.contentEditMsgModel;
                    list.get(i).isUpdateStatus = true;
                    list.get(i).formatSpans(ChatActivity.this, chatAdapter.getData().get(i).wkMsg);
                    isResetData = true;
                }

                list.get(i).wkMsg.isDeleted = wkMsg.isDeleted;
                list.get(i).wkMsg.messageID = wkMsg.messageID;
                list.get(i).wkMsg.messageSeq = wkMsg.messageSeq;
                list.get(i).wkMsg.orderSeq = wkMsg.orderSeq;
                if ((wkMsg.localExtraMap != null && !wkMsg.localExtraMap.isEmpty())) {
                    isNotify = true;
                }
                if (isRefreshReaction(list.get(i).wkMsg.reactionList, wkMsg.reactionList)) {
                    isResetReaction = true;
                }
                list.get(i).wkMsg.localExtraMap = wkMsg.localExtraMap;
                list.get(i).wkMsg.content = wkMsg.content;
                list.get(i).wkMsg.reactionList = wkMsg.reactionList;
                list.get(i).wkMsg.baseContentMsgModel = wkMsg.baseContentMsgModel;
                list.get(i).wkMsg.status = wkMsg.status;
                if (isNotify) {
                    EndpointManager.getInstance().invoke("stop_reaction_animation", null);
                    chatAdapter.notifyItemChanged(i);
                } else {
                    if (isResetStatus) {
                        chatAdapter.notifyStatus(i);
                    }
                    if (isResetListener) {
                        chatAdapter.notifyListener(i);
                    }
                    if (isResetData) {
                        chatAdapter.notifyData(i);
                    }
                    if (isResetReaction) {
                        list.get(i).isRefreshReaction = true;
                        chatAdapter.notifyItemChanged(i, list.get(i));
                        //chatAdapter.notifyReaction(i, wkMsg.reactionList);
                    }
                }

                if (list.get(i).wkMsg.remoteExtra.revoke == 1) {
                    int finalI = i;
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        int previousIndex = finalI - 1;
                        int nextIndex = finalI + 1;
                        if (previousIndex >= 0 && list.get(previousIndex).wkMsg.remoteExtra.revoke == 0) {
                            chatAdapter.notifyItemChanged(previousIndex);
                        }
                        if (nextIndex <= chatAdapter.getData().size() - 1 && list.get(nextIndex).wkMsg.remoteExtra.revoke == 0) {
                            chatAdapter.notifyItemChanged(nextIndex);
                        }
                    }, 200);
                }

                if ((wkMsg.status == WKSendMsgResult.no_relation || wkMsg.status == WKSendMsgResult.not_on_white_list) && channelType == WKChannelType.PERSONAL) {
                    if (UserUtils.getInstance().checkBlacklist(channelId)) {
                        return;
                    }
                    // 不是好友
                    WKMsg noRelationMsg = new WKMsg();
                    noRelationMsg.channelID = channelId;
                    noRelationMsg.channelType = channelType;
                    noRelationMsg.type = WKContentType.noRelation;
                    long tempOrderSeq = WKIM.getInstance().getMsgManager().getMessageOrderSeq(0, wkMsg.channelID, wkMsg.channelType);
                    noRelationMsg.orderSeq = tempOrderSeq + 1;
                    noRelationMsg.status = WKSendMsgResult.send_success;

                    int index = chatAdapter.getData().size() - 1;
                    if (chatAdapter.lastMsgIsTyping()) index--;
                    WKUIChatMsgItemEntity itemEntity = WKIMUtils.getInstance().msg2UiMsg(this, noRelationMsg, count, showNickName, chatAdapter.isShowChooseItem());
                    chatAdapter.getData().get(index).nextMsg = noRelationMsg;
                    itemEntity.previousMsg = chatAdapter.getData().get(index).wkMsg;

                    chatAdapter.notifyItemChanged(index);
                    chatAdapter.addData(index + 1, itemEntity);
                    if (isToEnd) {
                        scrollToEnd();
                    }
                    WKIM.getInstance().getMsgManager().saveAndUpdateConversationMsg(noRelationMsg, false);
                }
            }
        }
    }

    private WKMsg getSpanEmptyMsg() {
        WKMsg msg = new WKMsg();
        msg.timestamp = 0;
        // 为了方便直接用该字段替换
        msg.messageSeq = getTopPinViewHeight();
        msg.type = WKContentType.spanEmptyView;
        return msg;
    }

    private boolean isAddedSpanEmptyView() {
        return WKReader.isNotEmpty(chatAdapter.getData()) && chatAdapter.getData().get(0).wkMsg != null && chatAdapter.getData().get(0).wkMsg.type == WKContentType.spanEmptyView;
    }

    /**
     * YUJ-132: copy the replied-to sender's home/source Space fields from the
     * source {@link WKMsg} onto the outgoing {@link WKReply} so the receivers'
     * reply-preview can render "@SpaceName" via {@link com.chat.base.external.ExternalSourceResolver}.
     *
     * <p>Fields are stored on {@link WKMsg#localExtraMap} per YUJ-89 / EP1
     * ({@code MsgModel.copyExternalSourceExtras}). Keys match
     * {@link com.chat.base.external.ExternalMsgExtras}. All fields are optional:
     * missing values leave the reply defaults untouched.
     */
    private void copyReplyExternalExtras(WKMsg src, WKReply dst) {
        if (src == null || dst == null || src.localExtraMap == null) return;
        Object isExternal = src.localExtraMap.get(com.chat.base.external.ExternalMsgExtras.IS_EXTERNAL);
        if (isExternal instanceof Number) {
            dst.from_is_external = ((Number) isExternal).intValue();
        } else if (isExternal instanceof Boolean) {
            dst.from_is_external = ((Boolean) isExternal) ? 1 : 0;
        } else if (isExternal != null) {
            String s = String.valueOf(isExternal).trim();
            if ("1".equals(s) || "true".equalsIgnoreCase(s)) {
                dst.from_is_external = 1;
            } else {
                try {
                    dst.from_is_external = Integer.parseInt(s);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        Object sourceName = src.localExtraMap.get(com.chat.base.external.ExternalMsgExtras.SOURCE_SPACE_NAME);
        if (sourceName != null) {
            String v = String.valueOf(sourceName);
            if (!v.isEmpty()) dst.from_source_space_name = v;
        }
        Object homeId = src.localExtraMap.get(com.chat.base.external.ExternalMsgExtras.HOME_SPACE_ID);
        if (homeId != null) {
            String v = String.valueOf(homeId);
            if (!v.isEmpty()) dst.from_home_space_id = v;
        }
        Object homeName = src.localExtraMap.get(com.chat.base.external.ExternalMsgExtras.HOME_SPACE_NAME);
        if (homeName != null) {
            String v = String.valueOf(homeName);
            if (!v.isEmpty()) dst.from_home_space_name = v;
        }
    }
}
