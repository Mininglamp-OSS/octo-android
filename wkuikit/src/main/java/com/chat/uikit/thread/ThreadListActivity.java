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

package com.chat.uikit.thread;

import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.msgitem.WKChannelMemberRole;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.components.SegmentTabView;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.chat.uikit.databinding.ActThreadListLayoutBinding;
import com.chat.uikit.sidebar.FollowModel;
import com.chat.uikit.sidebar.FollowedKeysStore;
import com.chat.uikit.sidebar.SidebarItemEntity;
import com.chat.uikit.thread.adapter.ThreadListAdapter;
import com.chat.uikit.thread.msgmodel.WKThreadCreatedContent;
import com.chat.uikit.thread.service.ThreadModel;
import com.chat.uikit.thread.service.entity.ThreadEntity;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.scwang.smart.refresh.layout.listener.OnRefreshListener;
import com.scwang.smart.refresh.layout.listener.OnLoadMoreListener;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;

public class ThreadListActivity extends WKBaseActivity<ActThreadListLayoutBinding> {

    private static final int PAGE_LIMIT = 100;
    private static final int TAB_ACTIVE = 0;
    private static final int TAB_ARCHIVED = 1;

    private String groupNo;
    private ThreadListAdapter adapter;
    private SegmentTabView segmentTabView;
    private int selectedTab = TAB_ACTIVE;
    private int currentPage = 1;
    private final List<ThreadEntity> allLoadedList = new ArrayList<>();

    // 每次 loadData 启动 +1，回调时对比当前值不一致直接丢弃。
    // 防止用户在飞行中切 tab（active→archived→active）时旧请求
    // 把 stale 数据 append 进新 tab。对齐 iOS WKThreadListVC.loadGeneration。
    private int loadGeneration;

    @Override
    protected ActThreadListLayoutBinding getViewBinding() {
        return ActThreadListLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.str_threads);
    }

    @Override
    protected void initView() {
        groupNo = getIntent().getStringExtra("groupNo");
        adapter = new ThreadListAdapter();
        adapter.setGroupNo(groupNo);
        initAdapter(wkVBinding.recyclerView, adapter);

        segmentTabView = new SegmentTabView(this, new String[]{
                getString(R.string.str_thread_tab_active),
                getString(R.string.str_thread_tab_archived)
        });
        wkVBinding.segmentTabContainer.addView(segmentTabView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT));
    }

    @Override
    protected void initListener() {
        wkVBinding.refreshLayout.setEnableLoadMore(true);
        wkVBinding.refreshLayout.setOnRefreshListener(new OnRefreshListener() {
            @Override
            public void onRefresh(@NonNull RefreshLayout refreshLayout) {
                refreshFromFirstPage();
            }
        });
        wkVBinding.refreshLayout.setOnLoadMoreListener(new OnLoadMoreListener() {
            @Override
            public void onLoadMore(@NonNull RefreshLayout refreshLayout) {
                loadData();
            }
        });

        segmentTabView.setOnTabSelectedListener(index -> {
            if (index == selectedTab) return;
            selectedTab = index;
            // 切 tab 与 iOS 对齐: 完全重置状态 + 只拉当前 tab 首页。server 端
            // 按 ?status=active|archived 各自分页, 客户端不再做二次过滤/缓存。
            refreshFromFirstPage();
        });

        SingleClickUtil.onSingleClick(wkVBinding.createThreadLayout, v -> openCreateThread());

        adapter.setOnItemClickListener((adapter1, view, position) ->
                SingleClickUtil.determineTriggerSingleClick(view, v -> {
                    ThreadEntity entity = adapter.getItem(position);
                    if (entity != null) {
                        adapter.markVisited(entity.short_id);
                        openThread(entity);
                    }
                }));

        adapter.setOnItemLongClickListener((adapter1, view, position) -> {
            ThreadEntity entity = adapter.getItem(position);
            if (entity != null) {
                showThreadPopup(view, entity);
            }
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从聊天页返回后刷新列表
        refreshFromFirstPage();
    }

    @Override
    protected void initData() {
        super.initData();
        // loadData() 由 onResume() 触发，避免首次创建时重复请求
    }

    private String currentStatusParam() {
        return selectedTab == TAB_ARCHIVED ? "archived" : "active";
    }

    private void loadData() {
        loadGeneration++;
        final int gen = loadGeneration;
        final int page = currentPage;
        final String requestedStatus = currentStatusParam();

        ThreadModel.getInstance().listThreads(groupNo, requestedStatus, page, PAGE_LIMIT, (code, msg, list) -> {
            if (gen != loadGeneration) {
                // stale: 用户已经切了 tab / 触发了新一次 loadData，本次回包作废
                return;
            }
            wkVBinding.refreshLayout.finishRefresh();
            wkVBinding.refreshLayout.finishLoadMore();
            if (code == HttpResponseCode.success && list != null) {
                if (page == 1) {
                    allLoadedList.clear();
                }
                allLoadedList.addAll(list);
                adapter.setList(new ArrayList<>(allLoadedList));
                updateEmptyState();
                // 判断是否还有更多数据
                if (list.size() < PAGE_LIMIT) {
                    wkVBinding.refreshLayout.setNoMoreData(true);
                } else {
                    wkVBinding.refreshLayout.setNoMoreData(false);
                    currentPage++;
                }
            } else {
                WKToastUtils.getInstance().showToast(msg);
            }
        });
    }

    private void updateEmptyState() {
        if (allLoadedList.isEmpty()) {
            wkVBinding.emptyTv.setText(selectedTab == TAB_ARCHIVED
                    ? R.string.str_no_archived_threads
                    : R.string.str_no_active_threads);
            wkVBinding.emptyTv.setVisibility(View.VISIBLE);
        } else {
            wkVBinding.emptyTv.setVisibility(View.GONE);
        }
    }

    private void openThread(ThreadEntity entity) {
        String channelId = ThreadModel.getInstance().buildChannelId(groupNo, entity.short_id);
        // 乐观 UI: 立即 navigate, join 请求后台异步完成 (对齐 iOS 语义但去掉等待)。
        // - 归档子区: server 会拒 join(慢/超时), 不阻塞进入; 用户仍能看历史消息。
        // - 活跃未加入子区: join 成功后 is_joined=1, 下次点击走快路径。
        // - 已加入子区: 跳过 join, 直接进入。
        if (entity.is_joined == 0) {
            ThreadModel.getInstance().joinThread(groupNo, entity.short_id, (code, msg) -> {
                // fire-and-forget: navigate 已提前触发, join 结果无需处理。
            });
        }
        navigateToChat(channelId);
    }

    private void navigateToChat(String channelId) {
        WKIMUtils.getInstance().startChatActivity(
                new ChatViewMenu(this, channelId, WKChannelType.COMMUNITY_TOPIC, 0, false));
    }

    private void openCreateThread() {
        Intent intent = new Intent(this, CreateThreadActivity.class);
        intent.putExtra("groupNo", groupNo);
        startActivity(intent);
    }

    private void refreshFromFirstPage() {
        currentPage = 1;
        allLoadedList.clear();
        adapter.setList(new ArrayList<>());
        wkVBinding.refreshLayout.setNoMoreData(false);
        loadData();
    }

    /**
     * 是否有权归档/取消归档/删除该子区。对齐 iOS WKThreadListVC.canManageThread:
     * 子区创建者 OR 父群群主/管理员均可操作，与服务端 canOperate 一致。
     */
    private boolean canManageThread(ThreadEntity entity) {
        String currentUid = WKConfig.getInstance().getUid();
        if (currentUid != null && currentUid.equals(entity.creator_uid)) {
            return true;
        }
        if (currentUid == null || groupNo == null) return false;
        WKChannelMember member = WKIM.getInstance().getChannelMembersManager()
                .getMember(groupNo, WKChannelType.GROUP, currentUid);
        if (member == null) return false;
        return member.role == WKChannelMemberRole.admin
                || member.role == WKChannelMemberRole.manager;
    }

    private void showThreadPopup(View anchorView, ThreadEntity entity) {
        String threadChannelId = ThreadModel.getInstance().buildChannelId(groupNo, entity.short_id);
        boolean isFollowed = FollowedKeysStore.getInstance().isFollowed(
                SidebarItemEntity.TARGET_TYPE_THREAD, threadChannelId);

        List<PopupMenuItem> menuItems = new ArrayList<>();
        menuItems.add(new PopupMenuItem(
                getString(isFollowed ? R.string.unfollow_conversation : R.string.follow_conversation),
                isFollowed ? R.drawable.ic_unfollow_star : R.drawable.ic_follow_star,
                () -> {
                    if (isFollowed) {
                        FollowModel.getInstance().unfollowThread(threadChannelId, (code, msg) -> {
                            if (code != HttpResponseCode.success) {
                                WKToastUtils.getInstance().showToast(msg);
                            }
                        });
                    } else {
                        FollowModel.getInstance().followThread(threadChannelId, (code, msg) -> {
                            if (code != HttpResponseCode.success) {
                                WKToastUtils.getInstance().showToast(msg);
                            }
                        });
                    }
                }));

        if (canManageThread(entity)) {
            if (entity.status == 1) {
                // 活跃 → 显示"归档"
                menuItems.add(new PopupMenuItem(
                        getString(R.string.str_archive_thread), R.mipmap.msg_delete,
                        () -> ThreadModel.getInstance().archiveThread(groupNo, entity.short_id, (code, msg) -> {
                            if (code == HttpResponseCode.success) refreshFromFirstPage();
                            else WKToastUtils.getInstance().showToast(msg);
                        })));
            } else if (entity.status == 2) {
                // 已归档 → 显示"取消归档"
                menuItems.add(new PopupMenuItem(
                        getString(R.string.str_unarchive_thread), R.mipmap.msg_delete,
                        () -> ThreadModel.getInstance().unarchiveThread(groupNo, entity.short_id, (code, msg) -> {
                            if (code == HttpResponseCode.success) refreshFromFirstPage();
                            else WKToastUtils.getInstance().showToast(msg);
                        })));
            }
            menuItems.add(new PopupMenuItem(
                    getString(R.string.str_delete_thread), R.mipmap.msg_delete,
                    () -> ThreadModel.getInstance().deleteThread(groupNo, entity.short_id, (code, msg) -> {
                        if (code == HttpResponseCode.success) {
                            // 删除成功后清掉源消息映射, 让源消息长按菜单从"进入子区"切回"创建子区"
                            // (对齐 iOS WKThreadListVC confirmDeleteThread)。
                            WKThreadCreatedContent.markThreadClosedForSourceMessageId(entity.source_message_id);
                            refreshFromFirstPage();
                        } else WKToastUtils.getInstance().showToast(msg);
                    })));
        }

        WKDialogUtils.getInstance().showScreenPopup(anchorView, menuItems);
    }
}
