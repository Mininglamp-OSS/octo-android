package com.chat.uikit.chat.search.member;

import android.text.TextUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.entity.GlobalMessage;
import com.chat.base.entity.GlobalSearchReq;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.search.GlobalSearchModel;
import com.chat.base.utils.WKReader;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActCommonRefreshListLayoutBinding;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;


public class SearchWithMemberActivity extends WKBaseActivity<ActCommonRefreshListLayoutBinding> {
    SearchWithMemberAdapter adapter;
    private String channelID;
    private String fromUID;
    private int page = 1;

    @Override
    protected ActCommonRefreshListLayoutBinding getViewBinding() {
        return ActCommonRefreshListLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.uikit_search_with_member);
    }

    @Override
    protected void initPresenter() {
        channelID = getIntent().getStringExtra("channelID");
        fromUID = getIntent().getStringExtra("fromUID");
    }

    @Override
    protected void initView() {
        String name = "";
        String avatarKey = "";
        WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(channelID, WKChannelType.GROUP, fromUID);
        if (member != null) {
            name = TextUtils.isEmpty(member.memberRemark) ? member.memberName : member.memberRemark;
        }
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(fromUID, WKChannelType.PERSONAL);
        if (channel != null) {
            if (!TextUtils.isEmpty(channel.channelRemark))
                name = channel.channelRemark;
            avatarKey = channel.avatarCacheKey;
        }
        adapter = new SearchWithMemberAdapter(name, avatarKey);
        initAdapter(wkVBinding.recyclerView, adapter);
    }

    @Override
    protected void initListener() {
        wkVBinding.refreshLayout.setEnableRefresh(false);
        wkVBinding.refreshLayout.setOnRefreshLoadMoreListener(new OnRefreshLoadMoreListener() {
            @Override
            public void onLoadMore(@NonNull RefreshLayout refreshLayout) {
                page ++;
                getData();
            }

            @Override
            public void onRefresh(@NonNull RefreshLayout refreshLayout) {

            }
        });
        adapter.setOnItemClickListener((adapter, view, position) -> {
            GlobalMessage msg = (GlobalMessage) adapter.getData().get(position);
            if (msg != null) {
                long orderSeq = WKIM.getInstance().getMsgManager().getMessageOrderSeq(
                        msg.getMessage_seq(),
                        msg.getChannel().getChannel_id(),
                        msg.getChannel().getChannel_type()
                );
                EndpointManager.getInstance().invoke(EndpointSID.chatView, new ChatViewMenu(SearchWithMemberActivity.this, channelID, WKChannelType.GROUP, orderSeq, false));
            }
        });
        getData();
    }

    private void getData() {
        ArrayList<Integer> contentType = new ArrayList<>();
        contentType.add(WKContentType.WK_TEXT);
        contentType.add(WKContentType.WK_FILE);
        GlobalSearchReq req = new GlobalSearchReq(1, "", channelID, WKChannelType.GROUP, fromUID, "", contentType, page, 20, 0, 0);
        GlobalSearchModel.INSTANCE.search(req, (code, s, globalSearch) -> {
            wkVBinding.refreshLayout.finishLoadMore();
            wkVBinding.refreshLayout.finishRefresh();
            if (code != HttpResponseCode.success) {
                showToast(s);
                return null;
            }
            if (globalSearch == null || WKReader.isEmpty(globalSearch.messages)) {
                if (page != 1) {
                    wkVBinding.refreshLayout.setEnableLoadMore(false);
                }
                return null;
            }
            if (page == 1) {
                adapter.setList(globalSearch.messages);
            } else {
                adapter.addData(globalSearch.messages);
            }
            return null;
        });
    }
}
