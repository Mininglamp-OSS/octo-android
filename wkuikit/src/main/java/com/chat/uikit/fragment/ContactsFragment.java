package com.chat.uikit.fragment;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tencent.bugly.crashreport.CrashReport;

import com.chat.base.base.WKBaseFragment;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.config.WKSystemAccount;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ContactsMenu;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.HanziToPinyin;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.base.views.sidebar.listener.OnQuickSideBarTouchListener;
import com.chat.uikit.R;
import com.chat.uikit.contacts.FriendAdapter;
import com.chat.uikit.contacts.FriendUIEntity;
import com.chat.uikit.contacts.StickyHeaderDecoration;
import com.chat.uikit.databinding.FragContactsLayoutBinding;
import com.chat.uikit.message.MsgModel;
import com.chat.uikit.robot.entity.BotStoreEntity;
import com.chat.uikit.robot.service.WKRobotModel;
import com.chat.uikit.search.remote.GlobalActivity;
import com.chat.uikit.space.SpaceEntity;
import com.chat.uikit.space.SpaceModel;
import com.chat.uikit.user.UserDetailActivity;
import com.chat.uikit.utils.CharacterParser;
import com.chat.uikit.utils.PyingUtils;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 2019-11-12 14:57
 * 联系人
 */
public class ContactsFragment extends WKBaseFragment<FragContactsLayoutBinding> implements OnQuickSideBarTouchListener {

    // 字体缓存，避免每次冷启动重复从 assets 加载
    private static Typeface sCachedBoldTypeface;

    private ContactsHeaderAdapter contactsHeaderAdapter;
    private FriendAdapter friendAdapter;
    private TextView allContactsCountTv;
    private boolean isContactsLoaded = false;
    private String lastLoadedSpaceId = null;

    // 筛选相关
    private static final int FILTER_ALL = 0;
    private static final int FILTER_AI = 1;
    private static final int FILTER_HUMAN = 2;
    private int contactsFilter = FILTER_ALL;
    // 人类联系人（排序后）
    private List<FriendUIEntity> humanContactsList = new ArrayList<>();
    // AI 广场全部机器人（排序后）
    private List<FriendUIEntity> allBotsList = new ArrayList<>();
    // 全部联系人缓存（人类+AI 合并去重排序后），切换 tab 时直接使用
    private List<FriendUIEntity> mergedAllCache = null;
    // bot 数据是否已加载完成，用于控制 AI 数量显示
    private boolean botsLoaded = false;
    // 已添加AI数量（Space成员中 robot==1 的数量）
    private int addedAiCount = 0;
    private final TextView[] filterBtns = new TextView[3];
    private Disposable contactsDisposable;

    @Override
    protected boolean isShowBackLayout() {
        return false;
    }

    @Override
    protected FragContactsLayoutBinding getViewBinding() {
        return FragContactsLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        wkVBinding.textView.setTextSize(22);
        if (sCachedBoldTypeface == null) {
            sCachedBoldTypeface = Typeface.createFromAsset(getResources().getAssets(), "fonts/mw_bold.ttf");
        }
        wkVBinding.textView.setTypeface(sCachedBoldTypeface);
        int sidebarColor = android.graphics.Color.parseColor("#7761F4");
        wkVBinding.quickSideBarView.setTextChooseColor(sidebarColor);
        wkVBinding.quickSideBarTipsView.setBackgroundColor(sidebarColor);
        wkVBinding.refreshLayout.setEnableOverScrollDrag(true);
        wkVBinding.refreshLayout.setEnableLoadMore(false);
        wkVBinding.refreshLayout.setEnableRefresh(false);
        Theme.setPressedBackground(wkVBinding.searchIv);
        Theme.setPressedBackground(wkVBinding.rightIv);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void initListener() {
        Object orgViewObject = EndpointManager.getInstance().invoke("org_contacts_view", requireContext());
        friendAdapter = new FriendAdapter();
        RecyclerView headerRecyclerView = new RecyclerView(requireContext());
        friendAdapter.addHeaderView(headerRecyclerView);
        if (orgViewObject != null) {
            View orgView = (View) orgViewObject;
            friendAdapter.addHeaderView(orgView);
        }
        // 添加筛选区域 header
        friendAdapter.addHeaderView(buildFilterHeaderView());
        friendAdapter.addFooterView(getFooterView());
        initAdapter(wkVBinding.recyclerView, friendAdapter);
        // 关闭 item 动画，避免滑动/刷新时的闪烁
        wkVBinding.recyclerView.setItemAnimator(null);
        friendAdapter.setAnimationEnable(false);
        int stickyHeight = AndroidUtilities.dp(30);
        StickyHeaderDecoration stickyDecoration = StickyHeaderDecoration.forFriendAdapter(
                stickyHeight,
                friendAdapter.getHeaderLayoutCount(),
                dataIndex -> {
                    List<FriendUIEntity> data = friendAdapter.getData();
                    if (dataIndex >= 0 && dataIndex < data.size() && data.get(dataIndex).pying != null) {
                        return data.get(dataIndex).pying;
                    }
                    return "#";
                },
                () -> friendAdapter.getData().size()
        );
        wkVBinding.recyclerView.addItemDecoration(stickyDecoration);
        headerRecyclerView.setNestedScrollingEnabled(false);
        contactsHeaderAdapter = new ContactsHeaderAdapter();
        initAdapter(headerRecyclerView, contactsHeaderAdapter);
        wkVBinding.quickSideBarView.setOnQuickSideBarTouchListener(this);
        wkVBinding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NotNull RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) return;
                int firstVisible = lm.findFirstVisibleItemPosition();
                int dataIndex = firstVisible - friendAdapter.getHeaderLayoutCount();
                List<FriendUIEntity> list = friendAdapter.getData();
                if (dataIndex >= 0 && dataIndex < list.size()) {
                    String pying = list.get(dataIndex).pying;
                    if (pying != null && !pying.isEmpty()) {
                        String letter = pying.substring(0, 1).toUpperCase();
                        wkVBinding.quickSideBarView.setChooseLetter(letter);
                    }
                }
            }
        });
        friendAdapter.addChildClickViewIds(R.id.contentLayout);
        friendAdapter.setOnItemChildClickListener((adapter, view, position) -> SingleClickUtil.determineTriggerSingleClick(view, view1 -> {
            FriendUIEntity friendEntity = (FriendUIEntity) adapter.getItem(position);
            if (friendEntity != null) {
                Intent intent = new Intent(getActivity(), UserDetailActivity.class);
                intent.putExtra("uid", friendEntity.channel.channelID);
                startActivity(intent);
            }
        }));
        contactsHeaderAdapter.setOnItemClickListener((adapter, view, position) -> SingleClickUtil.determineTriggerSingleClick(view, view1 -> {
            ContactsMenu item = (ContactsMenu) adapter.getItem(position);
            if (item == null) return;
            if (item.targetActivity != null && getActivity() != null) {
                Intent intent = new Intent(getActivity(), item.targetActivity);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            } else if (item.iMenuClick != null) {
                item.iMenuClick.onClick();
            }
        }));
        wkVBinding.rightIv.setOnClickListener(view -> {
            List<PopupMenuItem> list = EndpointManager.getInstance().invokes(EndpointCategory.tabMenus, null);
            WKDialogUtils.getInstance().showScreenPopup(view, list);
        });
        //成员刷新监听
        WKIM.getInstance().getChannelManager().addOnRefreshChannelInfo("contacts_fragment_refresh_channel", (channel, isEnd) -> {
            if (channel != null) {
                Observable.create((ObservableOnSubscribe<Integer>) e -> {
                    for (int i = 0, size = friendAdapter.getData().size(); i < size; i++) {
                        if (friendAdapter.getData().get(i).channel != null
                                && friendAdapter.getData().get(i).channel.channelID.equals(channel.channelID)
                                && friendAdapter.getData().get(i).channel.channelType == channel.channelType) {
                            friendAdapter.getData().get(i).channel.channelName = channel.channelName;
                            friendAdapter.getData().get(i).channel.channelRemark = channel.channelRemark;
                            friendAdapter.getData().get(i).channel.mute = channel.mute;
                            friendAdapter.getData().get(i).channel.top = channel.top;
                            friendAdapter.getData().get(i).channel.avatar = channel.avatar;
                            friendAdapter.getData().get(i).channel.remoteExtraMap = channel.remoteExtraMap;
                            friendAdapter.getData().get(i).channel.online = channel.online;
                            friendAdapter.getData().get(i).channel.lastOffline = channel.lastOffline;
                            friendAdapter.getData().get(i).channel.deviceFlag = channel.deviceFlag;
                            e.onNext(i);
                            break;
                        }
                    }
                }).observeOn(AndroidSchedulers.mainThread()).subscribeOn(Schedulers.io()).subscribe(new Observer<>() {
                    @Override
                    public void onSubscribe(@NotNull Disposable d) {
                    }

                    @Override
                    public void onNext(@NotNull Integer index) {
                        friendAdapter.notifyItemChanged(index + friendAdapter.getHeaderLayoutCount());
                    }

                    @Override
                    public void onError(@NotNull Throwable e) {
                    }

                    @Override
                    public void onComplete() {
                    }
                });
            }
        });
        wkVBinding.searchIv.setOnClickListener(view -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                @SuppressWarnings("unchecked") ActivityOptionsCompat activityOptions = ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity(), new Pair<>(wkVBinding.searchIv, "searchView"));
                startActivity(new Intent(getActivity(), GlobalActivity.class), activityOptions.toBundle());
            } else {
                startActivity(new Intent(getActivity(), GlobalActivity.class));
            }
        });
        wkVBinding.searchBarLayout.setOnClickListener(view -> {
            startActivity(new Intent(getActivity(), GlobalActivity.class));
        });
        //监听刷新通讯录
        EndpointManager.getInstance().setMethod("contacts_refresh_mail", EndpointCategory.wkRefreshMailList, object -> {
            resetHeaderData();
            return null;
        });

        EndpointManager.getInstance().setMethod(WKConstants.refreshContacts, object -> {
            // 好友/群组/机器人变更时强制刷新
            isContactsLoaded = false;
            loadContacts(true);
            return null;
        });
    }

    @Override
    protected void initData() {
        wkVBinding.quickSideBarView.setLetters(CharacterParser.getInstance().getList());
        contactsHeaderAdapter.setList(EndpointManager.getInstance().invokes(EndpointCategory.mailList, getActivity()));
        // 冷启动预热：第一次 getInstance() 调用 Collator.getAvailableLocales() 耗时，放后台提前完成
        if (HanziToPinyin.getInstance() == null) {
            Observable.fromCallable(() -> { HanziToPinyin.getInstance(); return null; })
                    .subscribeOn(Schedulers.io()).subscribe(r -> {}, e -> {});
        }
        loadContacts(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        resetHeaderData();
        // 检查 Space 是否切换了，切换了才重新加载
        String currentSpaceId = MsgModel.getInstance().getCurrentSpaceId();
        boolean spaceChanged = !TextUtils.equals(currentSpaceId, lastLoadedSpaceId);
        if (spaceChanged || !isContactsLoaded) {
            loadContacts(false);
        }
    }

    /**
     * @param force true=强制刷新（好友/群组变更时），false=有缓存则跳过
     */
    private void loadContacts(boolean force) {
        if (!force && isContactsLoaded) return;

        String spaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (!TextUtils.isEmpty(spaceId)) {
            getContactsFromSpace(spaceId);
        } else {
            getContactsLocal();
        }
    }

    private void getContactsFromSpace(String spaceId) {
        String myUid = WKConfig.getInstance().getUid();
        SpaceModel.getInstance().getMembers(spaceId, new SpaceModel.IMembersListener() {
            @Override
            public void onResult(List<SpaceEntity.SpaceMember> members) {
                if (contactsDisposable != null) contactsDisposable.dispose();
                contactsDisposable = Observable.fromCallable(() -> {
                    List<FriendUIEntity> list = new ArrayList<>();
                    for (SpaceEntity.SpaceMember member : members) {
                        if (member.uid.equals(myUid)) continue;
                        WKChannel channel = new WKChannel(member.uid, WKChannelType.PERSONAL);
                        channel.channelName = member.name;
                        channel.robot = member.robot;
                        list.add(new FriendUIEntity(channel));
                    }
                    processContacts(list);
                    return Boolean.TRUE;
                }).subscribeOn(Schedulers.io())
                  .observeOn(AndroidSchedulers.mainThread())
                  .subscribe(result -> {
                      if (!isAdded()) return;
                      lastLoadedSpaceId = spaceId;
                      isContactsLoaded = true;
                      rebuildMergedCache();
                      updateHeaderCounts();
                      applyFilterAndDisplay();
                      loadAllBots(spaceId);
                  }, throwable -> {
                      Log.w("ContactsFragment", "processContacts failed", throwable);
                      CrashReport.postCatchedException(throwable);
                  });
            }

            @Override
            public void onError(int code, String msg) {
                getContactsLocal();
            }
        });
    }

    private void getContactsLocal() {
        if (contactsDisposable != null) contactsDisposable.dispose();
        contactsDisposable = Observable.fromCallable(() -> {
            List<WKChannel> allList = WKIM.getInstance().getChannelManager().getWithFollowAndStatus(WKChannelType.PERSONAL, 1, 1);
            List<FriendUIEntity> list = new ArrayList<>(allList.size());
            for (int i = 0, size = allList.size(); i < size; i++) {
                list.add(new FriendUIEntity(allList.get(i)));
            }
            processContacts(list);
            return Boolean.TRUE;
        }).subscribeOn(Schedulers.io())
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(result -> {
              if (!isAdded()) return;
              lastLoadedSpaceId = null;
              isContactsLoaded = true;
              rebuildMergedCache();
              updateHeaderCounts();
              applyFilterAndDisplay();
              String spaceId = MsgModel.getInstance().getCurrentSpaceId();
              if (!TextUtils.isEmpty(spaceId)) {
                  loadAllBots(spaceId);
              }
          }, throwable -> {
                      Log.w("ContactsFragment", "processContacts failed", throwable);
                      CrashReport.postCatchedException(throwable);
                  });
    }

    /**
     * 在后台线程执行：拼音计算 + 排序 + 拆分人类/AI 列表
     */
    private void processContacts(List<FriendUIEntity> list) {
        assignPying(list);
        PyingUtils.getInstance().sortListBasic(list);

        List<FriendUIEntity> humans = new ArrayList<>();
        int aiCount = 0;
        for (FriendUIEntity entity : sortByCategory(list)) {
            if (entity.channel.robot == 1) {
                aiCount++;
            } else {
                humans.add(entity);
            }
        }
        humanContactsList = humans;
        addedAiCount = aiCount;
    }

    /**
     * 从 AI 广场加载全部机器人，加载完成（成功或失败）后统一刷新显示
     */
    private void loadAllBots(String spaceId) {
        botsLoaded = false;
        WKRobotModel.getInstance().getSpaceBots(spaceId, new WKRobotModel.ISpaceBotsListener() {
            @Override
            public void onResult(List<BotStoreEntity> result) {
                Observable.fromCallable(() -> {
                    List<FriendUIEntity> botList = new ArrayList<>();
                    if (WKReader.isNotEmpty(result)) {
                        for (BotStoreEntity bot : result) {
                            WKChannel channel = new WKChannel(bot.uid, WKChannelType.PERSONAL);
                            channel.channelName = bot.name;
                            channel.robot = 1;
                            botList.add(new FriendUIEntity(channel));
                        }
                    }
                    assignPying(botList);
                    PyingUtils.getInstance().sortListBasic(botList);
                    return sortByCategory(botList);
                }).subscribeOn(Schedulers.io())
                  .observeOn(AndroidSchedulers.mainThread())
                  .subscribe(sorted -> {
                      if (!isAdded()) return;
                      allBotsList = sorted;
                      botsLoaded = true;
                      rebuildMergedCache();
                      updateHeaderCounts();
                      applyFilterAndDisplay();
                  }, throwable -> {
                      Log.w("ContactsFragment", "processContacts failed", throwable);
                      CrashReport.postCatchedException(throwable);
                  });
            }

            @Override
            public void onError(int code, String msg) {
                if (!isAdded()) return;
                botsLoaded = true;
                rebuildMergedCache();
                updateHeaderCounts();
                applyFilterAndDisplay();
            }
        });
    }

    /**
     * 为列表中每个 entity 计算拼音
     */
    private void assignPying(List<FriendUIEntity> list) {
        for (int i = 0, size = list.size(); i < size; i++) {
            String showName = list.get(i).channel.channelRemark;
            if (TextUtils.isEmpty(showName))
                showName = list.get(i).channel.channelName;
            if (list.get(i).channel.channelID.equals(WKSystemAccount.system_file_helper)) {
                if (isAdded())
                    showName = getString(R.string.wk_file_helper);
                list.get(i).channel.channelName = showName;
            }
            if (list.get(i).channel.channelID.equals(WKSystemAccount.system_team)) {
                if (isAdded())
                    showName = getString(R.string.wk_system_notice);
                list.get(i).channel.channelName = showName;
            }
            if (!TextUtils.isEmpty(showName)) {
                list.get(i).pying = HanziToPinyin.getInstance().getPY(showName);
            } else list.get(i).pying = "#";
        }
    }

    /**
     * 将列表按 字母 -> 数字 -> 其他 顺序排列
     */
    private List<FriendUIEntity> sortByCategory(List<FriendUIEntity> list) {
        List<FriendUIEntity> letterList = new ArrayList<>();
        List<FriendUIEntity> numList = new ArrayList<>();
        List<FriendUIEntity> otherList = new ArrayList<>();
        for (int i = 0, size = list.size(); i < size; i++) {
            if (TextUtils.isEmpty(list.get(i).pying)) {
                otherList.add(list.get(i));
                continue;
            }
            if (PyingUtils.getInstance().isStartLetter(list.get(i).pying)) {
                letterList.add(list.get(i));
            } else if (PyingUtils.getInstance().isStartNum(list.get(i).pying)) {
                numList.add(list.get(i));
            } else otherList.add(list.get(i));
        }
        List<FriendUIEntity> result = new ArrayList<>();
        result.addAll(letterList);
        result.addAll(numList);
        result.addAll(otherList);
        return result;
    }

    /**
     * 根据当前 contactsFilter 选择对应数据源并显示
     */
    private void applyFilterAndDisplay() {
        List<FriendUIEntity> displayed;
        if (contactsFilter == FILTER_AI) {
            displayed = allBotsList;
        } else if (contactsFilter == FILTER_HUMAN) {
            displayed = humanContactsList;
        } else {
            displayed = mergedAllCache != null ? mergedAllCache : humanContactsList;
        }

        if (!isDataSame(friendAdapter.getData(), displayed)) {
            friendAdapter.setList(displayed);
        }
        if (isAdded()) {
            allContactsCountTv.setText(String.format(getString(R.string.contacts_num), displayed.size()));
            updateFilterUI();
        }
    }

    /**
     * 重建全部联系人缓存（人类+AI 合并去重排序），数据变更时调用
     */
    private void rebuildMergedCache() {
        Set<String> seen = new HashSet<>();
        List<FriendUIEntity> merged = new ArrayList<>();
        for (FriendUIEntity entity : humanContactsList) {
            if (seen.add(entity.channel.channelID)) {
                merged.add(entity);
            }
        }
        for (FriendUIEntity entity : allBotsList) {
            if (seen.add(entity.channel.channelID)) {
                merged.add(entity);
            }
        }
        PyingUtils.getInstance().sortListBasic(merged);
        mergedAllCache = sortByCategory(merged);
    }

    /**
     * 更新 header 中群聊和已添加AI的数量
     */
    private void updateHeaderCounts() {
        if (!isAdded()) return;

        int groupCount = WKIM.getInstance().getConversationManager().getWithChannelType(WKChannelType.GROUP).size();

        List<ContactsMenu> menuList = EndpointManager.getInstance().invokes(EndpointCategory.mailList, getActivity());
        for (ContactsMenu menu : menuList) {
            if ("group_chat".equals(menu.sid)) {
                menu.countValue = "(" + groupCount + ")";
            } else if ("added_ai".equals(menu.sid)) {
                menu.countValue = "(" + addedAiCount + ")";
            } else if ("friend".equals(menu.sid)) {
                menu.badgeNum = WKSharedPreferencesUtil.getInstance().getInt(WKConfig.getInstance().getUid() + "_new_friend_count");
            }
        }
        contactsHeaderAdapter.setList(menuList);
        lastFriendBadgeNum = WKSharedPreferencesUtil.getInstance().getInt(WKConfig.getInstance().getUid() + "_new_friend_count");
    }

    /**
     * 构建筛选区域 View：标题 + 3个筛选按钮
     */
    private View buildFilterHeaderView() {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.homeColor));
        int hPadding = AndroidUtilities.dp(15);
        container.setPadding(hPadding, AndroidUtilities.dp(12), hPadding, AndroidUtilities.dp(8));

        LinearLayout btnRow = new LinearLayout(requireContext());
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setCornerRadius(AndroidUtilities.dp(10));
        pillBg.setColor(Color.parseColor("#F2F2F7"));
        btnRow.setBackground(pillBg);
        btnRow.setPadding(AndroidUtilities.dp(2), AndroidUtilities.dp(2),
                AndroidUtilities.dp(2), AndroidUtilities.dp(2));
        LinearLayout.LayoutParams btnRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        container.addView(btnRow, btnRowParams);

        for (int i = 0; i < 3; i++) {
            TextView btn = new TextView(requireContext());
            btn.setTextSize(13);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
            final int filterIndex = i;
            btn.setOnClickListener(v -> {
                if (contactsFilter != filterIndex) {
                    contactsFilter = filterIndex;
                    applyFilterAndDisplay();
                }
            });
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) btnParams.leftMargin = AndroidUtilities.dp(2);
            btnRow.addView(btn, btnParams);
            filterBtns[i] = btn;
        }

        return container;
    }

    /**
     * 更新筛选按钮的文案和选中状态
     */
    private void updateFilterUI() {
        int humanCount = humanContactsList.size();
        int totalCount = mergedAllCache != null ? mergedAllCache.size() : humanCount;
        // 只有 bot 加载完且有数据时才显示数量，避免先显示 0 再闪变
        String aiLabel = (botsLoaded && !allBotsList.isEmpty())
                ? getString(R.string.contacts_ai) + " · " + allBotsList.size()
                : getString(R.string.contacts_ai);
        String[] labels = {
                getString(R.string.contacts_all) + " · " + totalCount,
                aiLabel,
                getString(R.string.contacts_human) + " · " + humanCount
        };

        for (int i = 0; i < 3; i++) {
            if (filterBtns[i] == null) continue;
            filterBtns[i].setText(labels[i]);

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(AndroidUtilities.dp(8));
            if (contactsFilter == i) {
                bg.setColor(Color.WHITE);
                filterBtns[i].setTextColor(Color.parseColor("#333333"));
                filterBtns[i].setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                bg.setColor(Color.TRANSPARENT);
                filterBtns[i].setTextColor(Color.parseColor("#999999"));
                filterBtns[i].setTypeface(Typeface.DEFAULT);
            }
            filterBtns[i].setBackground(bg);
        }
    }

    /**
     * 比较两个列表的 channelID 序列是否一致，避免相同数据的重复刷新。
     */
    private boolean isDataSame(List<FriendUIEntity> oldList, List<FriendUIEntity> newList) {
        if (oldList.size() != newList.size()) return false;
        for (int i = 0, size = oldList.size(); i < size; i++) {
            if (!oldList.get(i).channel.channelID.equals(newList.get(i).channel.channelID)) {
                return false;
            }
        }
        return true;
    }

    private View getFooterView() {
        allContactsCountTv = new TextView(requireContext());
        allContactsCountTv.setGravity(Gravity.CENTER);
        allContactsCountTv.setTextSize(16);
        allContactsCountTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorDark));
        LinearLayout linearLayout = new LinearLayout(requireContext());
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        linearLayout.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.homeColor));
        linearLayout.addView(allContactsCountTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) allContactsCountTv.getLayoutParams();
        layoutParams.topMargin = AndroidUtilities.dp(15);
        layoutParams.bottomMargin = AndroidUtilities.dp(15);
        return linearLayout;
    }

    @Override
    public void onLetterChanged(String letter, int position, float y) {
        wkVBinding.quickSideBarTipsView.setText(letter, position, y);
        List<FriendUIEntity> list = friendAdapter.getData();
        if (WKReader.isNotEmpty(list)) {
            for (int i = 0, size = list.size(); i < size; i++) {
                if (list.get(i).pying != null && list.get(i).pying.toUpperCase().startsWith(letter.toUpperCase())) {
                    // 先停止正在进行的惯性滚动，再瞬间定位
                    wkVBinding.recyclerView.stopScroll();
                    LinearLayoutManager lm = (LinearLayoutManager) wkVBinding.recyclerView.getLayoutManager();
                    if (lm != null) {
                        lm.scrollToPositionWithOffset(i + friendAdapter.getHeaderLayoutCount(), 0);
                    }
                    return;
                }
            }
        }
    }

    @Override
    public void onLetterTouching(boolean touching) {
        wkVBinding.quickSideBarTipsView.setVisibility(touching ? View.VISIBLE : View.INVISIBLE);
    }

    private int lastFriendBadgeNum = -1;

    private void resetHeaderData() {
        if (isAdded()) {
            int badgeNum = WKSharedPreferencesUtil.getInstance().getInt(WKConfig.getInstance().getUid() + "_new_friend_count");
            if (badgeNum == lastFriendBadgeNum) return;
            lastFriendBadgeNum = badgeNum;
            List<ContactsMenu> list = EndpointManager.getInstance().invokes(EndpointCategory.mailList, getActivity());
            for (int i = 0, size = list.size(); i < size; i++) {
                if (!TextUtils.isEmpty(list.get(i).sid) && list.get(i).sid.equals("friend")) {
                    list.get(i).badgeNum = badgeNum;
                    break;
                }
            }
            contactsHeaderAdapter.setList(list);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (contactsDisposable != null) contactsDisposable.dispose();
        EndpointManager.getInstance().remove("contacts_refresh_mail");
        EndpointManager.getInstance().remove(WKConstants.refreshContacts);
        WKIM.getInstance().getChannelManager().removeRefreshChannelInfo("contacts_fragment_refresh_channel");
    }
}
