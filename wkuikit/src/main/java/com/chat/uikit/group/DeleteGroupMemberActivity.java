package com.chat.uikit.group;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.msgitem.WKChannelMemberRole;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.WKReader;
import com.chat.uikit.R;
import com.chat.uikit.contacts.ChooseUserSelectedAdapter;
import com.chat.uikit.contacts.FriendUIEntity;
import com.chat.uikit.databinding.ActDeleteMemberLayoutBinding;
import com.chat.uikit.group.adapter.DeleteGroupMemberAdapter;
import com.chat.uikit.group.service.GroupModel;
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
 * 2020-01-31 14:15
 * 删除群成员
 */
public class DeleteGroupMemberActivity extends WKBaseActivity<ActDeleteMemberLayoutBinding> {
    DeleteGroupMemberAdapter groupMemberAdapter;
    ChooseUserSelectedAdapter selectedAdapter;
    private String groupId;
    private TextView textView;
    private String searchKey;
    private int page = 1;
    private int groupType = 0;

    @Override
    protected ActDeleteMemberLayoutBinding getViewBinding() {
        return ActDeleteMemberLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.delete_group_members);
    }

    @Override
    protected void rightLeftLayoutClick() {
        super.rightLeftLayoutClick();
    }

    @Override
    protected String getRightTvText(TextView textView) {
        this.textView = textView;
        return getString(R.string.delete);
    }

    @Override
    protected void rightLayoutClick() {
        super.rightLayoutClick();
        List<GroupMemberEntity> selectedList = new ArrayList<>();
        for (int i = 0, size = groupMemberAdapter.getData().size(); i < size; i++) {
            if (groupMemberAdapter.getData().get(i).checked == 1)
                selectedList.add(groupMemberAdapter.getData().get(i));
        }

        if (WKReader.isNotEmpty(selectedList)) {
            List<String> uids = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (int i = 0, size = selectedList.size(); i < size; i++) {
                uids.add(selectedList.get(i).member.memberUID);
                names.add(selectedList.get(i).member.memberName);
            }
            showTitleRightLoading();
            GroupModel.getInstance().deleteGroupMembers(groupId, uids, names, (code, msg) -> {
                if (code == HttpResponseCode.success) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        SoftKeyboardUtils.getInstance().hideSoftKeyboard(DeleteGroupMemberActivity.this);
                        setResult(RESULT_OK);
                        finish();
                    }, 500);
                } else {
                    hideTitleRightLoading();
                    showToast(msg);
                }
            });
        }
    }

    @Override
    protected void initView() {
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(groupId, WKChannelType.GROUP);
        if (channel != null && channel.remoteExtraMap != null && channel.remoteExtraMap.containsKey(WKChannelExtras.groupType)) {
            Object groupTypeObject = channel.remoteExtraMap.get(WKChannelExtras.groupType);
            if (groupTypeObject instanceof Integer) {
                groupType = (int) groupTypeObject;
            }
        }
        groupMemberAdapter = new DeleteGroupMemberAdapter(new ArrayList<>());
        initAdapter(wkVBinding.recyclerView, groupMemberAdapter);

        selectedAdapter = new ChooseUserSelectedAdapter(new ChooseUserSelectedAdapter.IGetEdit() {
            @Override
            public void onDeleted(String uid) {
                for (int i = 0, size = groupMemberAdapter.getData().size(); i < size; i++) {
                    if (groupMemberAdapter.getData().get(i).member.memberUID.equals(uid)) {
                        groupMemberAdapter.getData().get(i).checked = 0;
                        groupMemberAdapter.notifyItemChanged(i, groupMemberAdapter.getData().get(i));
                        break;
                    }
                }
                new Handler().postDelayed(() -> setRightTv(), 300);
            }

            @Override
            public void searchUser(String key) {
                page = 1;
                searchKey = key;
                groupMemberAdapter.setSearch(searchKey);
                wkVBinding.refreshLayout.setEnableLoadMore(true);
                getData();
            }
        });
        FriendUIEntity ui = new FriendUIEntity(new WKChannel("", WKChannelType.PERSONAL));
        ui.itemType = 1;
        selectedAdapter.addData(ui);
        wkVBinding.selectUserRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        wkVBinding.selectUserRecyclerView.setAdapter(selectedAdapter);

    }

    private void setRightTv() {
        int count = selectedAdapter.getData().size() - 1;
        if (count > 0) {
            textView.setVisibility(View.VISIBLE);
            textView.setText(String.format("%s(%s)", getString(R.string.delete), count));
            showTitleRightView();
        } else {
            textView.setText(R.string.delete);
            textView.setVisibility(View.INVISIBLE);
            hideTitleRightView();
        }

    }

    @SuppressLint("ClickableViewAccessibility")
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

            }
        });
        selectedAdapter.setOnItemClickListener((adapter, view, position) -> {
            FriendUIEntity userEntity = selectedAdapter.getItem(position);
            if (userEntity != null && userEntity.itemType == 0) {
                if (!userEntity.isSetDelete) {
                    userEntity.isSetDelete = true;
                    selectedAdapter.notifyItemChanged(position, userEntity);
                    return;
                }
                boolean isRemove = false;
                for (int i = 0, size = groupMemberAdapter.getData().size(); i < size; i++) {
                    if (groupMemberAdapter.getData().get(i).member.memberUID.equalsIgnoreCase(userEntity.channel.channelID) && groupMemberAdapter.getData().get(i).isCanCheck == 1) {
                        groupMemberAdapter.getData().get(i).checked = groupMemberAdapter.getData().get(i).checked == 1 ? 0 : 1;
                        groupMemberAdapter.notifyItemChanged(i, groupMemberAdapter.getData().get(i));
                        isRemove = true;
                        break;
                    }
                }
                if (isRemove) {
                    for (int i = 0, size = selectedAdapter.getData().size(); i < size; i++) {
                        selectedAdapter.getData().get(i).isSetDelete = false;
                    }
                    selectedAdapter.removeAt(position);
                    setRightTv();
                }
            }
        });
        groupMemberAdapter.setOnItemClickListener((adapter, view1, position) -> {
            GroupMemberEntity groupMemberEntity = (GroupMemberEntity) adapter.getItem(position);
            if (groupMemberEntity != null) {
                groupMemberEntity.checked = groupMemberEntity.checked == 1 ? 0 : 1;

                if (groupMemberEntity.checked == 1) {
                    groupMemberEntity.isSetDelete = false;
                    WKChannel channel = new WKChannel();
                    channel.channelName = groupMemberEntity.member.memberName;
                    channel.channelRemark = groupMemberEntity.member.memberRemark;
                    channel.channelID = groupMemberEntity.member.memberUID;
                    channel.channelType = WKChannelType.PERSONAL;
                    channel.avatar = groupMemberEntity.member.memberAvatar;
                    channel.avatarCacheKey = groupMemberEntity.member.memberAvatarCacheKey;
                    FriendUIEntity uiEntity = new FriendUIEntity(channel);
                    uiEntity.isSetDelete = false;
                    selectedAdapter.addData(selectedAdapter.getData().size() - 1, uiEntity);
                    wkVBinding.selectUserRecyclerView.scrollToPosition(selectedAdapter.getData().size() - 1);
                } else {
                    for (int i = 0, size = selectedAdapter.getData().size(); i < size; i++) {
                        if (selectedAdapter.getData().get(i).channel.channelID.equalsIgnoreCase(groupMemberEntity.member.memberUID)) {
                            selectedAdapter.removeAt(i);
                            break;
                        }
                    }
                }

            }
            selectedAdapter.notifyItemChanged(selectedAdapter.getData().size() - 1, selectedAdapter.getData().get(selectedAdapter.getData().size() - 1));
            SoftKeyboardUtils.getInstance().hideSoftKeyboard(this);

            adapter.notifyItemChanged(position, groupMemberEntity);
            setRightTv();
        });


        wkVBinding.selectUserRecyclerView.setOnTouchListener((view, motionEvent) -> {
            View childView = wkVBinding.selectUserRecyclerView.getChildAt(selectedAdapter.getData().size() - 1);
            if (childView != null) {
                EditText editText = childView.findViewById(R.id.searchEt);
                SoftKeyboardUtils.getInstance().showSoftKeyBoard(DeleteGroupMemberActivity.this, editText);
            }
            return false;
        });
    }

    @Override
    protected void initData() {
        super.initData();
        groupId = getIntent().getStringExtra("groupId");
        getData();
    }


    private void getData() {
        WKIM.getInstance().getChannelMembersManager().getWithPageOrSearch(groupId, WKChannelType.GROUP, searchKey, page, 20, (list, b) -> {
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
        List<GroupMemberEntity> tempList = new ArrayList<>();
        String loginUID = WKConfig.getInstance().getUid();
        int loginMemberRole = 0;
        WKChannelMember loginUserMember = WKIM.getInstance().getChannelMembersManager().getMember(groupId, WKChannelType.GROUP, loginUID);
        if (loginUserMember != null) {
            loginMemberRole = loginUserMember.role;
        }
        for (int i = 0, size = list.size(); i < size; i++) {
            if (loginUID.equals(list.get(i).memberUID)) continue;
            if (loginMemberRole == WKChannelMemberRole.manager && list.get(i).role != WKChannelMemberRole.normal){
                continue;
            }
            GroupMemberEntity entity = new GroupMemberEntity(list.get(i));
            for (int j = 0, len = selectedAdapter.getData().size(); j < len; j++) {
                if (list.get(i).memberUID.equals(selectedAdapter.getData().get(j).channel.channelID)) {
                    entity.checked = 1;
                    break;
                }
            }
            tempList.add(entity);

        }
        wkVBinding.refreshLayout.finishLoadMore();
        if (page == 1) {
            groupMemberAdapter.setList(tempList);
        } else {
            groupMemberAdapter.addData(tempList);
        }
        if (WKReader.isEmpty(tempList)) {
            wkVBinding.refreshLayout.finishLoadMoreWithNoMoreData();
        }
    }
}
