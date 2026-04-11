package com.chat.uikit.thread;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.WKToastUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.chat.ChatActivity;
import com.chat.uikit.databinding.ActThreadListLayoutBinding;
import com.chat.uikit.thread.adapter.ThreadListAdapter;
import com.chat.uikit.thread.service.ThreadModel;
import com.chat.uikit.thread.service.entity.ThreadEntity;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.scwang.smart.refresh.layout.listener.OnRefreshListener;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;

public class ThreadListActivity extends WKBaseActivity<ActThreadListLayoutBinding> {

    private String groupNo;
    private ThreadListAdapter adapter;

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
        initAdapter(wkVBinding.recyclerView, adapter);
        wkVBinding.sectionTitleTv.setVisibility(View.GONE);
    }

    @Override
    protected void initListener() {
        wkVBinding.refreshLayout.setOnRefreshListener(new OnRefreshListener() {
            @Override
            public void onRefresh(@NonNull RefreshLayout refreshLayout) {
                loadData();
            }
        });

        SingleClickUtil.onSingleClick(wkVBinding.createThreadLayout, v -> openCreateThread());

        adapter.setOnItemClickListener((adapter1, view, position) ->
                SingleClickUtil.determineTriggerSingleClick(view, v -> {
                    ThreadEntity entity = adapter.getItem(position);
                    if (entity != null) {
                        openThread(entity);
                    }
                }));

        adapter.setOnItemLongClickListener((adapter1, view, position) -> {
            ThreadEntity entity = adapter.getItem(position);
            if (entity != null) {
                String currentUid = com.chat.base.config.WKConfig.getInstance().getUid();
                if (currentUid.equals(entity.creator_uid)) {
                    showThreadOptions(entity);
                }
            }
            return true;
        });
    }

    @Override
    protected void initData() {
        super.initData();
        loadData();
    }

    private void loadData() {
        ThreadModel.getInstance().listThreads(groupNo, (code, msg, list) -> {
            wkVBinding.refreshLayout.finishRefresh();
            if (code == HttpResponseCode.success && list != null) {
                List<ThreadEntity> activeList = new ArrayList<>();
                for (ThreadEntity entity : list) {
                    // status: 1=活跃, 2=归档, 3=删除
                    if (entity.status == 1) {
                        activeList.add(entity);
                    }
                }
                adapter.setList(activeList);
                if (!activeList.isEmpty()) {
                    wkVBinding.sectionTitleTv.setVisibility(View.VISIBLE);
                    wkVBinding.sectionTitleTv.setText(String.format("已加入子区 - %d", activeList.size()));
                } else {
                    wkVBinding.sectionTitleTv.setVisibility(View.GONE);
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
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("channelId", channelId);
        intent.putExtra("channelType", WKChannelType.COMMUNITY_TOPIC);
        startActivity(intent);
    }

    private void openCreateThread() {
        Intent intent = new Intent(this, CreateThreadActivity.class);
        intent.putExtra("groupNo", groupNo);
        startActivity(intent);
    }

    private void showThreadOptions(ThreadEntity entity) {
        String[] options;
        if (entity.status == 1) {
            options = new String[]{getString(R.string.str_archive_thread), getString(R.string.str_delete_thread)};
        } else {
            options = new String[]{getString(R.string.str_unarchive_thread), getString(R.string.str_delete_thread)};
        }
        new android.app.AlertDialog.Builder(this)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (entity.status == 1) {
                            ThreadModel.getInstance().archiveThread(groupNo, entity.short_id, (code, msg) -> {
                                if (code == HttpResponseCode.success) loadData();
                                else WKToastUtils.getInstance().showToast(msg);
                            });
                        } else {
                            ThreadModel.getInstance().unarchiveThread(groupNo, entity.short_id, (code, msg) -> {
                                if (code == HttpResponseCode.success) loadData();
                                else WKToastUtils.getInstance().showToast(msg);
                            });
                        }
                    } else if (which == 1) {
                        ThreadModel.getInstance().deleteThread(groupNo, entity.short_id, (code, msg) -> {
                            if (code == HttpResponseCode.success) loadData();
                            else WKToastUtils.getInstance().showToast(msg);
                        });
                    }
                }).show();
    }
}
