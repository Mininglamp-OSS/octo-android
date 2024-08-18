package com.chat.uikit.group;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.entity.WKChannelCustomerExtras;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKTimeUtils;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActAllMemberLayoutBinding;
import com.chat.uikit.enity.AllGroupMemberEntity;
import com.chat.uikit.enity.OnlineUser;
import com.chat.uikit.group.adapter.AllMembersAdapter;
import com.chat.uikit.user.UserDetailActivity;
import com.chat.uikit.user.service.UserModel;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelExtras;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;

/**
 * 2020-12-11 15:15
 * 所有成员
 */
public class WKAllMembersActivity extends WKBaseActivity<ActAllMemberLayoutBinding> {

    private AllMembersAdapter adapter;
    private int page = 1;
    String channelID;
    byte channelType;
    private String searchKey;
    private TextView titleTv;
    private int groupType = 0;

    @Override
    protected ActAllMemberLayoutBinding getViewBinding() {
        return ActAllMemberLayoutBinding.inflate(getLayoutInflater());
    }

    @SuppressLint("StringFormatMatches")
    @Override
    protected void setTitle(TextView titleTv) {
        this.titleTv = titleTv;
    }

    @Override
    protected void initView() {
        channelID = getIntent().getStringExtra("channelID");
        channelType = getIntent().getByteExtra("channelType", WKChannelType.GROUP);
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
                getData();
            }

            @Override
            public void onRefresh(@NonNull RefreshLayout refreshLayout) {
                page = 1;
                getData();
            }
        });
        wkVBinding.searchEt.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        wkVBinding.searchEt.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                SoftKeyboardUtils.getInstance().hideSoftKeyboard(WKAllMembersActivity.this);
                return true;
            }

            return false;
        });
        wkVBinding.searchEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                searchKey = editable.toString();
                page = 1;
                adapter.setSearchKey(searchKey);
                getData();
            }
        });
        adapter.setOnItemClickListener((adapter1, view1, position) -> SingleClickUtil.determineTriggerSingleClick(view1, view2 -> {
            AllGroupMemberEntity entity = adapter.getItem(position);
            if (entity != null) {
                entity.getChannelMember();
                Intent intent = new Intent(this, UserDetailActivity.class);
                intent.putExtra("uid", entity.getChannelMember().memberUID);
                intent.putExtra("groupID", entity.getChannelMember().channelID);
                startActivity(intent);
            }
        }));
    }

    @Override
    protected void initData() {
        super.initData();
        int count = WKIM.getInstance().getChannelMembersManager().getMemberCount(channelID, channelType);

        titleTv.setText(String.format(getString(R.string.group_members), count + ""));
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelID, channelType);
        if (channel != null && channel.remoteExtraMap != null ) {
            if (channel.remoteExtraMap.containsKey(WKChannelExtras.groupType)) {
                Object groupTypeObject = channel.remoteExtraMap.get(WKChannelExtras.groupType);
                if (groupTypeObject instanceof Integer) {
                    groupType = (int) groupTypeObject;
                }
            }
            Object memberCountObject = channel.remoteExtraMap.get(WKChannelCustomerExtras.memberCount);
            if (memberCountObject instanceof Integer) {
                 count = (int) memberCountObject;
                titleTv.setText(String.format(getString(R.string.group_members), count + ""));
            }
        }
        getData();
    }

    private void getData() {
        WKIM.getInstance().getChannelMembersManager().getWithPageOrSearch(channelID, channelType, searchKey, page, 50, (list, b) -> {
            if (groupType == 0)
                resortData(list);
            else {
                if (b) {
                    resortData(list);
                }
            }
        });
    }

    private void resortData(List<WKChannelMember> list) {
        if (WKReader.isNotEmpty(list)) {
            List<String> uidList = new ArrayList<>();
            for (WKChannelMember member : list) {
                uidList.add(member.memberUID);
            }
            UserModel.getInstance().getOnlineUsers(uidList, (code, msg, onlineUserList) -> {
                wkVBinding.refreshLayout.finishLoadMore();
                if (WKReader.isNotEmpty(list)) {

                    List<AllGroupMemberEntity> allList = new ArrayList<>();
                    for (WKChannelMember member : list) {
                        int online = 0;
                        String lastOfflineTime = "";
                        String lastOnlineTime = "";
                        for (OnlineUser onlineUser : onlineUserList) {
                            if (onlineUser.getUid().equals(member.memberUID)) {
                                online = onlineUser.getOnline();
                                lastOnlineTime =
                                        WKTimeUtils.getInstance().getOnlineTime(onlineUser.getLast_offline());
                                lastOfflineTime = WKTimeUtils.getInstance()
                                        .getShowDateAndMinute(onlineUser.getLast_offline() * 1000L);
                            }
                        }
                        AllGroupMemberEntity entity = new AllGroupMemberEntity(member, online, lastOfflineTime, lastOnlineTime);
                        allList.add(entity);
                    }
                    if (page == 1) {
                        adapter.setList(allList);
                    } else {
                        adapter.addData(allList);
                    }

                }
            });
        } else {
            wkVBinding.refreshLayout.finishLoadMoreWithNoMoreData();
        }
    }

}
