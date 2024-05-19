package com.chat.uikit.group;

import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.entity.ChannelInfoEntity;
import com.chat.base.utils.WKReader;
import com.chat.uikit.R;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.chat.uikit.contacts.ChooseContactsActivity;
import com.chat.uikit.databinding.ActCommonListLayoutBinding;
import com.chat.uikit.group.adapter.SavedGroupAdapter;
import com.chat.uikit.group.service.GroupContract;
import com.chat.uikit.group.service.GroupPresenter;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;

/**
 * 2020-01-30 19:18
 * 我保存的群组
 */
public class SavedGroupsActivity extends WKBaseActivity<ActCommonListLayoutBinding> implements GroupContract.GroupView {
    private SavedGroupAdapter groupAdapter;
    private GroupPresenter presenter;

    @Override
    protected ActCommonListLayoutBinding getViewBinding() {
        return ActCommonListLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.saved_groups);
    }

    @Override
    protected void initPresenter() {
        presenter = new GroupPresenter(this);
    }

    @Override
    protected void initView() {
        groupAdapter = new SavedGroupAdapter();
        initAdapter(wkVBinding.recyclerView, groupAdapter);
    }

    @Override
    protected String getRightTvText(TextView textView) {
        return getString(R.string.create_new_group);
    }

    @Override
    protected void rightLayoutClick() {
        super.rightLayoutClick();
        Intent intent = new Intent(this, ChooseContactsActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        presenter.getMyGroups();
    }

    @Override
    protected void initListener() {
        groupAdapter.addChildClickViewIds(R.id.contentLayout);
        groupAdapter.setOnItemChildClickListener((adapter, view1, position) -> {
            GroupEntity channel = groupAdapter.getItem(position);
            if (channel != null) {
                WKIMUtils.getInstance().startChatActivity(new ChatViewMenu(this, channel.group_no, WKChannelType.GROUP, 0, true));
            }
        });
    }

    @Override
    protected void initData() {
        super.initData();
    }

    @Override
    public void onGroupInfo(ChannelInfoEntity channelInfoEntity) {

    }

    @Override
    public void onRefreshGroupSetting(String key, int value) {

    }

    @Override
    public void setQrData(int day, String qrCode, String expire) {

    }

    @Override
    public void setMyGroups(List<GroupEntity> list) {
        if (WKReader.isEmpty(list)) {
            wkVBinding.nodataTv.setVisibility(View.VISIBLE);
        } else {
            List<String> channelIds = new ArrayList<>();
            for (int i = 0, size = list.size(); i < size; i++) {
                channelIds.add(list.get(i).group_no);
            }
            List<WKChannel> channels = WKIM.getInstance().getChannelManager().getWithChannelIdsAndChannelType(channelIds, WKChannelType.GROUP);
            for (int i = 0, size = list.size(); i < size; i++) {
                for (WKChannel channel : channels) {
                    if (channel != null && !TextUtils.isEmpty(channel.channelID) && channel.channelID.equals(list.get(i).group_no)) {
                        list.get(i).avatar = channel.avatarCacheKey;
                        break;
                    }
                }
            }
            groupAdapter.setList(list);
        }

    }

    @Override
    public void showError(String msg) {

    }

    @Override
    public void hideLoading() {

    }
}
