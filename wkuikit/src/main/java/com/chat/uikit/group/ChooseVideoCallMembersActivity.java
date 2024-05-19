package com.chat.uikit.group;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSystemAccount;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.CreateVideoCallMenu;
import com.chat.base.entity.WKGroupType;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.WKReader;
import com.chat.uikit.R;
import com.chat.uikit.contacts.ChooseUserSelectedAdapter;
import com.chat.uikit.contacts.FriendUIEntity;
import com.chat.uikit.databinding.ActChooseVideoCallMembersLayoutBinding;
import com.chat.uikit.group.adapter.ChooseVideoCallMemberAdapter;
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
 * 5/7/21 4:52 PM
 * 选择通话成员
 */
public class ChooseVideoCallMembersActivity extends WKBaseActivity<ActChooseVideoCallMembersLayoutBinding> {
    private String channelID;
    private byte channelType;
    private int maxSelectCount = 9;
    ChooseUserSelectedAdapter selectedAdapter;
    private ChooseVideoCallMemberAdapter adapter;
    private Button rightBtn;
    private boolean isCreate = false;
    private String searchKey = "";
    private int page = 1;
    private int groupType = 0;

    @Override
    protected ActChooseVideoCallMembersLayoutBinding getViewBinding() {
        return ActChooseVideoCallMembersLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.choose_members);
    }

    @Override
    protected String getRightBtnText(Button titleRightBtn) {
        this.rightBtn = titleRightBtn;
        rightBtn.setVisibility(View.GONE);
        return getString(R.string.sure);
    }

    @Override
    protected void rightButtonClick() {
        super.rightButtonClick();
        if (WKReader.isNotEmpty(selectedAdapter.getData())) {
            List<WKChannel> channels = new ArrayList<>();
            for (int i = 0, size = selectedAdapter.getData().size(); i < size; i++) {
                if (!TextUtils.isEmpty(selectedAdapter.getData().get(i).channel.channelID))
                    channels.add(selectedAdapter.getData().get(i).channel);
            }
            Object isFinish = null;
            if (isCreate) {
                if (channels.size() > maxSelectCount) {
                    String content = String.format(getString(R.string.max_select_count), maxSelectCount);
                    showSingleBtnDialog(content);
                    return;
                }
                isFinish = EndpointManager.getInstance().invoke("create_video_call", new CreateVideoCallMenu(this, channelID, channelType, channels));
            } //else
            //WKKitApplication.getInstance().chooseVideoCallBack(uids);
            if (null == isFinish)
                new Handler(Looper.getMainLooper()).postDelayed(this::finish, 500);
        }

    }


    @Override
    protected void initPresenter() {
        channelID = getIntent().getStringExtra("channelID");
        channelType = getIntent().getByteExtra("channelType", WKChannelType.GROUP);
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelID, WKChannelType.GROUP);
        if (channel != null && channel.remoteExtraMap != null && channel.remoteExtraMap.containsKey(WKChannelExtras.groupType)) {
            Object groupTypeObject = channel.remoteExtraMap.get(WKChannelExtras.groupType);
            if (groupTypeObject instanceof Integer) {
                groupType = (int) groupTypeObject;
            }
        }

        if (getIntent().hasExtra("isCreate")) {
            isCreate = getIntent().getBooleanExtra("isCreate", true);
            Object max = EndpointManager.getInstance().invoke("rtc_max_number", null);
            if (max instanceof Integer) {
                maxSelectCount = (int) max;
            }
        }
    }

    @Override
    protected void initView() {
        adapter = new ChooseVideoCallMemberAdapter();
        initAdapter(wkVBinding.recyclerView, adapter);

        selectedAdapter = new ChooseUserSelectedAdapter(new ChooseUserSelectedAdapter.IGetEdit() {
            @Override
            public void onDeleted(String uid) {
                for (int i = 0, size = adapter.getData().size(); i < size; i++) {
                    if (adapter.getData().get(i).member.memberUID.equalsIgnoreCase(uid) && adapter.getData().get(i).isCanCheck == 1) {
                        adapter.getData().get(i).checked = adapter.getData().get(i).checked == 1 ? 0 : 1;
                        adapter.notifyItemChanged(i, adapter.getData().get(i));
                        break;
                    }
                }
                new Handler().postDelayed(() -> checkSelect(), 300);
            }

            @Override
            public void searchUser(String key) {
                page = 1;
                searchKey = key;
//                adapter.setSearch(searchKey);
                wkVBinding.refreshLayout.setEnableLoadMore(true);
                getData();
            }
        });
        wkVBinding.selectUserRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        wkVBinding.selectUserRecyclerView.setAdapter(selectedAdapter);
        FriendUIEntity ui = new FriendUIEntity(new WKChannel("", WKChannelType.PERSONAL));
        ui.itemType = 1;
        selectedAdapter.addData(ui);
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
        wkVBinding.selectUserRecyclerView.setOnTouchListener((view, motionEvent) -> {
            View childView = wkVBinding.selectUserRecyclerView.getChildAt(selectedAdapter.getData().size() - 1);
            if (childView != null) {
                EditText editText = childView.findViewById(R.id.searchEt);
                SoftKeyboardUtils.getInstance().showSoftKeyBoard(ChooseVideoCallMembersActivity.this, editText);
            }
            return false;
        });
        selectedAdapter.setOnItemClickListener((adapter1, view1, position) -> {
            FriendUIEntity userEntity = selectedAdapter.getItem(position);
            if (userEntity != null && userEntity.itemType == 0) {
                if (!userEntity.isSetDelete) {
                    userEntity.isSetDelete = true;
                    selectedAdapter.notifyItemChanged(position, userEntity);
                    return;
                }
                boolean isRemove = false;
                for (int i = 0, size = adapter.getData().size(); i < size; i++) {
                    if (adapter.getData().get(i).member.memberUID.equalsIgnoreCase(userEntity.channel.channelID) && adapter.getData().get(i).isCanCheck == 1) {
                        adapter.getData().get(i).checked = adapter.getData().get(i).checked == 1 ? 0 : 1;
                        adapter.notifyItemChanged(i, adapter.getData().get(i));
                        isRemove = true;
                        break;
                    }
                }
                if (isRemove) {
                    for (int i = 0, size = selectedAdapter.getData().size(); i < size; i++) {
                        selectedAdapter.getData().get(i).isSetDelete = false;
                    }
                    selectedAdapter.removeAt(position);
                    checkSelect();
                }
            }
        });
        adapter.setOnItemClickListener((adapter, view1, position) -> {
            GroupMemberEntity memberEntity = (GroupMemberEntity) adapter.getItem(position);
            if (memberEntity != null) {
                if (maxSelectCount != -1 && (selectedAdapter.getItemCount() - 1) >= maxSelectCount) {
                    String content = String.format(getString(R.string.max_select_count), maxSelectCount);
                    showSingleBtnDialog(content);
                    return;
                }
                if (memberEntity.isCanCheck == 1) {
                    memberEntity.checked = memberEntity.checked == 1 ? 0 : 1;
                    adapter.notifyItemChanged(position, memberEntity);
                    if (memberEntity.checked == 1) {
                        WKChannel channel = new WKChannel(memberEntity.member.memberUID, WKChannelType.PERSONAL);
                        channel.channelName = memberEntity.member.memberName;
                        channel.channelRemark = memberEntity.member.memberRemark;
                        FriendUIEntity friendEntity = new FriendUIEntity(channel);
                        friendEntity.isSetDelete = false;
                        selectedAdapter.addData(selectedAdapter.getData().size() - 1, friendEntity);
                        wkVBinding.selectUserRecyclerView.scrollToPosition(selectedAdapter.getData().size() - 1);
                    } else {
                        for (int i = 0, size = selectedAdapter.getData().size(); i < size; i++) {
                            if (selectedAdapter.getData().get(i).channel.channelID.equalsIgnoreCase(memberEntity.member.memberUID)) {
                                selectedAdapter.removeAt(i);
                                break;
                            }
                        }
                    }
                    selectedAdapter.notifyItemChanged(selectedAdapter.getData().size() - 1, selectedAdapter.getData().get(selectedAdapter.getData().size() - 1));
                    SoftKeyboardUtils.getInstance().hideSoftKeyboard(this);
                    checkSelect();
                }

            }
        });

    }

    @Override
    protected void initData() {
        super.initData();
        getData();
    }


    private void getData() {
        WKIM.getInstance().getChannelMembersManager().getWithPageOrSearch(channelID, channelType, searchKey, page, 100, (list, b) -> {
            if (groupType == WKGroupType.normalGroup)
                resortData(list);
            else {
                if (b) {
                    resortData(list);
                }
            }
        });
        checkSelect();
    }

    private void resortData(List<WKChannelMember> list) {
        String loginUID = WKConfig.getInstance().getUid();
        List<GroupMemberEntity> tempList = new ArrayList<>();
        for (int i = 0, size = list.size(); i < size; i++) {
            if (list.get(i).memberUID.equals(loginUID) || WKSystemAccount.isSystemAccount(list.get(i).memberUID)) {
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
        wkVBinding.refreshLayout.finishRefresh();
        wkVBinding.refreshLayout.finishLoadMore();
        if (page == 1) {
            adapter.setList(tempList);
        } else {
            adapter.addData(tempList);
        }
        if (WKReader.isEmpty(tempList)) {
            wkVBinding.refreshLayout.setEnableLoadMore(false);
            wkVBinding.refreshLayout.finishLoadMoreWithNoMoreData();
        }
    }

    private void checkSelect() {
        int count = selectedAdapter.getData().size() - 1;
        if (count > 0) {
            rightBtn.setVisibility(View.VISIBLE);
            rightBtn.setText(String.format("%s(%s)", getString(R.string.sure), count));
            showTitleRightView();
        } else {
            rightBtn.setText(R.string.sure);
            rightBtn.setVisibility(View.INVISIBLE);
            hideTitleRightView();
        }
    }
}
