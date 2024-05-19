package com.chat.uikit.group;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.chat.base.utils.WKReader;
import com.github.promeg.pinyinhelper.Pinyin;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.msgitem.WKChannelMemberRole;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.views.sidebar.listener.OnQuickSideBarTouchListener;
import com.chat.uikit.R;
import com.chat.uikit.contacts.ChooseUserSelectedAdapter;
import com.chat.uikit.contacts.FriendUIEntity;
import com.chat.uikit.databinding.ActChooseContactsLayoutBinding;
import com.chat.uikit.group.adapter.DeleteGroupMemberAdapter;
import com.chat.uikit.group.service.GroupModel;
import com.chat.uikit.utils.CharacterParser;
import com.chat.uikit.utils.PyingUtils;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 2020-01-31 14:15
 * 删除群成员
 */
public class DeleteGroupMemberActivity extends WKBaseActivity<ActChooseContactsLayoutBinding> implements OnQuickSideBarTouchListener {
    DeleteGroupMemberAdapter groupMemberAdapter;
    ChooseUserSelectedAdapter selectedAdapter;
    private String groupId;
    private List<GroupMemberEntity> allList;
    private TextView textView;

    @Override
    protected ActChooseContactsLayoutBinding getViewBinding() {
        return ActChooseContactsLayoutBinding.inflate(getLayoutInflater());
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
                DeleteGroupMemberActivity.this.searchUser(key);
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
        wkVBinding.quickSideBarView.setLetters(CharacterParser.getInstance().getList());
        wkVBinding.quickSideBarView.setOnQuickSideBarTouchListener(this);
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
        String loginUID = WKConfig.getInstance().getUid();
        groupId = getIntent().getStringExtra("groupId");
        List<GroupMemberEntity> list = new ArrayList<>();
        int loginMemberRole = 0;
        WKChannelMember loginMember = WKIM.getInstance().getChannelMembersManager().getMember(groupId, WKChannelType.GROUP, loginUID);
        if (loginMember != null) {
            loginMemberRole = loginMember.role;
        }
        List<WKChannelMember> channelMembers = WKIM.getInstance().getChannelMembersManager().getMembers(groupId, WKChannelType.GROUP);
        for (int i = 0, size = channelMembers.size(); i < size; i++) {
            boolean isAdd = channelMembers.get(i).role == WKChannelMemberRole.manager && loginMemberRole == WKChannelMemberRole.admin || channelMembers.get(i).role == WKChannelMemberRole.normal;
            if (isAdd) {
                GroupMemberEntity entity = new GroupMemberEntity();
                entity.member = channelMembers.get(i);
                list.add(entity);
            }
        }

        List<GroupMemberEntity> otherList = new ArrayList<>();
        List<GroupMemberEntity> letterList = new ArrayList<>();
        List<GroupMemberEntity> numList = new ArrayList<>();
        for (int i = 0, size = list.size(); i < size; i++) {
            String showName = list.get(i).member.memberRemark;
            if (TextUtils.isEmpty(showName))
                showName = list.get(i).member.memberName;
            if (!TextUtils.isEmpty(showName)) {
                if (PyingUtils.getInstance().isStartNum(showName)) {
                    list.get(i).pying = "#";
                } else
                    list.get(i).pying = Pinyin.toPinyin(showName, "").toUpperCase();
            } else list.get(i).pying = "#";

        }
        PyingUtils.getInstance().sortListGroupMember(list);

        for (int i = 0, size = list.size(); i < size; i++) {
            if (PyingUtils.getInstance().isStartLetter(list.get(i).pying)) {
                //字母
                letterList.add(list.get(i));
            } else if (PyingUtils.getInstance().isStartNum(list.get(i).pying)) {
                //数字
                numList.add(list.get(i));
            } else otherList.add(list.get(i));
        }
        allList = new ArrayList<>();
        allList.addAll(letterList);
        allList.addAll(numList);
        allList.addAll(otherList);
        groupMemberAdapter.setList(allList);
    }


    private void searchUser(String content) {
        if (TextUtils.isEmpty(content)) {
            groupMemberAdapter.setList(allList);
            return;
        }
        List<GroupMemberEntity> tempList = new ArrayList<>();
        for (int i = 0, size = allList.size(); i < size; i++) {
            if ((!TextUtils.isEmpty(allList.get(i).member.memberName) && allList.get(i).member.memberName.toLowerCase(Locale.getDefault())
                    .contains(content.toLowerCase(Locale.getDefault())))
                    || (!TextUtils.isEmpty(allList.get(i).member.memberRemark) && allList.get(i).member.memberRemark.toLowerCase(Locale.getDefault())
                    .contains(content.toLowerCase(Locale.getDefault())))
                    || (!TextUtils.isEmpty(allList.get(i).member.remark) && allList.get(i).member.remark.toLowerCase(Locale.getDefault())
                    .contains(content.toLowerCase(Locale.getDefault())))
                    || content.contains(allList.get(i).pying.toLowerCase(
                    Locale.getDefault()))) {
                tempList.add(allList.get(i));
            }
        }
        groupMemberAdapter.setList(tempList);
    }


    @Override
    public void onLetterChanged(String letter, int position, float y) {
        wkVBinding.quickSideBarTipsView.setText(letter, position, y);
        //有此key则获取位置并滚动到该位置
        List<GroupMemberEntity> list = groupMemberAdapter.getData();
        if (WKReader.isNotEmpty(list)) {
            for (int i = 0, size = list.size(); i < size; i++) {
                if (list.get(i).pying.startsWith(letter)) {
                    wkVBinding.recyclerView.scrollToPosition(i);
                    break;
                }
            }
        }
    }

    @Override
    public void onLetterTouching(boolean touching) {
        //可以自己加入动画效果渐显渐隐
        wkVBinding.quickSideBarTipsView.setVisibility(touching ? View.VISIBLE : View.INVISIBLE);
    }
}
