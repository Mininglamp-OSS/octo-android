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
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.net.HttpResponseCode;
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
import com.chat.uikit.thread.service.ThreadModel;
import com.chat.uikit.thread.service.entity.ThreadEntity;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.scwang.smart.refresh.layout.listener.OnRefreshListener;
import com.scwang.smart.refresh.layout.listener.OnLoadMoreListener;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;

public class ThreadListActivity extends WKBaseActivity<ActThreadListLayoutBinding> {

    private static final int PAGE_LIMIT = 100;

    private String groupNo;
    private ThreadListAdapter adapter;
    private int currentPage = 1;
    private final List<ThreadEntity> allActiveList = new ArrayList<>();

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
        wkVBinding.sectionTitleTv.setVisibility(View.GONE);
    }

    @Override
    protected void initListener() {
        wkVBinding.refreshLayout.setEnableLoadMore(true);
        wkVBinding.refreshLayout.setOnRefreshListener(new OnRefreshListener() {
            @Override
            public void onRefresh(@NonNull RefreshLayout refreshLayout) {
                currentPage = 1;
                loadData();
            }
        });
        wkVBinding.refreshLayout.setOnLoadMoreListener(new OnLoadMoreListener() {
            @Override
            public void onLoadMore(@NonNull RefreshLayout refreshLayout) {
                loadData();
            }
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

    private void loadData() {
        ThreadModel.getInstance().listThreads(groupNo, currentPage, PAGE_LIMIT, (code, msg, list) -> {
            wkVBinding.refreshLayout.finishRefresh();
            wkVBinding.refreshLayout.finishLoadMore();
            if (code == HttpResponseCode.success && list != null) {
                List<ThreadEntity> pageActiveList = new ArrayList<>();
                for (ThreadEntity entity : list) {
                    // status: 1=活跃, 2=归档, 3=删除
                    if (entity.status == 1) {
                        pageActiveList.add(entity);
                    }
                }
                if (currentPage == 1) {
                    allActiveList.clear();
                }
                allActiveList.addAll(pageActiveList);
                adapter.setList(new ArrayList<>(allActiveList));
                if (!allActiveList.isEmpty()) {
                    wkVBinding.sectionTitleTv.setVisibility(View.VISIBLE);
                    wkVBinding.sectionTitleTv.setText(String.format("已加入子区 - %d", allActiveList.size()));
                } else {
                    wkVBinding.sectionTitleTv.setVisibility(View.GONE);
                }
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

    private void openThread(ThreadEntity entity) {
        String channelId = ThreadModel.getInstance().buildChannelId(groupNo, entity.short_id);
        if (entity.is_joined == 0) {
            ThreadModel.getInstance().joinThread(groupNo, entity.short_id, (code, msg) -> {
                if (code == HttpResponseCode.success) {
                    navigateToChat(channelId);
                } else {
                    WKToastUtils.getInstance().showToast(msg);
                }
            });
        } else {
            navigateToChat(channelId);
        }
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
        loadData();
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

        String currentUid = com.chat.base.config.WKConfig.getInstance().getUid();
        if (currentUid.equals(entity.creator_uid)) {
            if (entity.status == 1) {
                menuItems.add(new PopupMenuItem(
                        getString(R.string.str_archive_thread), R.mipmap.msg_delete,
                        () -> ThreadModel.getInstance().archiveThread(groupNo, entity.short_id, (code, msg) -> {
                            if (code == HttpResponseCode.success) refreshFromFirstPage();
                            else WKToastUtils.getInstance().showToast(msg);
                        })));
            }
            menuItems.add(new PopupMenuItem(
                    getString(R.string.str_delete_thread), R.mipmap.msg_delete,
                    () -> ThreadModel.getInstance().deleteThread(groupNo, entity.short_id, (code, msg) -> {
                        if (code == HttpResponseCode.success) refreshFromFirstPage();
                        else WKToastUtils.getInstance().showToast(msg);
                    })));
        }

        WKDialogUtils.getInstance().showScreenPopup(anchorView, menuItems);
    }
}
