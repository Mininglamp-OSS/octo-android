package com.chat.uikit.thread;

import android.content.Intent;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKToastUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActAllMemberLayoutBinding;
import com.chat.uikit.group.adapter.AllMembersAdapter;
import com.chat.uikit.enity.AllGroupMemberEntity;
import com.chat.uikit.thread.service.ThreadModel;
import com.chat.uikit.thread.service.entity.ThreadMember;
import com.chat.uikit.user.UserDetailActivity;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;

public class ThreadMembersActivity extends WKBaseActivity<ActAllMemberLayoutBinding> {

    private String groupNo;
    private String shortId;
    private AllMembersAdapter adapter;
    private int page = 1;

    @Override
    protected ActAllMemberLayoutBinding getViewBinding() {
        return ActAllMemberLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.str_thread_members);
    }

    @Override
    protected void initView() {
        groupNo = getIntent().getStringExtra("groupNo");
        shortId = getIntent().getStringExtra("shortId");
        adapter = new AllMembersAdapter();
        initAdapter(wkVBinding.recyclerView, adapter);
    }

    @Override
    protected void initListener() {
        wkVBinding.refreshLayout.setEnableRefresh(false);
        wkVBinding.refreshLayout.setOnRefreshLoadMoreListener(new OnRefreshLoadMoreListener() {
            @Override
            public void onLoadMore(@NonNull RefreshLayout refreshLayout) {
                page++;
                loadData();
            }

            @Override
            public void onRefresh(@NonNull RefreshLayout refreshLayout) {
                page = 1;
                loadData();
            }
        });

        adapter.setOnItemClickListener((adapter1, view, position) ->
                SingleClickUtil.determineTriggerSingleClick(view, v -> {
                    AllGroupMemberEntity entity = adapter.getItem(position);
                    if (entity != null) {
                        Intent intent = new Intent(this, UserDetailActivity.class);
                        intent.putExtra("uid", entity.getChannelMember().memberUID);
                        startActivity(intent);
                    }
                }));
    }

    @Override
    protected void initData() {
        super.initData();
        loadData();
    }

    private void loadData() {
        ThreadModel.getInstance().getThreadMembers(groupNo, shortId, "", page, 50, (code, msg, list) -> {
            wkVBinding.refreshLayout.finishLoadMore();
            if (code == HttpResponseCode.success && WKReader.isNotEmpty(list)) {
                List<AllGroupMemberEntity> allList = new ArrayList<>();
                String channelId = ThreadModel.getInstance().buildChannelId(groupNo, shortId);
                for (Object item : list) {
                    if (!(item instanceof ThreadMember)) continue;
                    ThreadMember tm = (ThreadMember) item;
                    WKChannelMember member = new WKChannelMember();
                    member.memberUID = tm.uid;
                    member.memberName = tm.name;
                    member.memberRemark = tm.remark;
                    member.channelID = channelId;
                    member.channelType = WKChannelType.COMMUNITY_TOPIC;
                    member.role = tm.role;
                    AllGroupMemberEntity entity = new AllGroupMemberEntity(member, 0, "", "");
                    allList.add(entity);
                }
                if (page == 1) {
                    adapter.setList(allList);
                } else {
                    adapter.addData(allList);
                }
                wkVBinding.refreshLayout.setEnableLoadMore(true);
            } else {
                if (page == 1) {
                    adapter.setList(new ArrayList<>());
                } else {
                    wkVBinding.refreshLayout.setEnableLoadMore(false);
                }
            }
        });
    }
}
