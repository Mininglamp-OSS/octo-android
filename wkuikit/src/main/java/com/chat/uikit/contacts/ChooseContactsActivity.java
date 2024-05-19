package com.chat.uikit.contacts;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.chat.base.config.WKSystemAccount;
import com.chat.base.entity.WKAPPConfig;
import com.chat.base.views.CommonAnim;
import com.github.promeg.pinyinhelper.Pinyin;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.endpoint.entity.SaveLabelMenu;
import com.chat.base.msgitem.WKChannelMemberRole;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKToastUtils;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.UserUtils;
import com.chat.base.views.sidebar.listener.OnQuickSideBarTouchListener;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.R;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.chat.uikit.databinding.ActChooseContactsLayoutBinding;
import com.chat.uikit.group.service.GroupModel;
import com.chat.uikit.utils.CharacterParser;
import com.chat.uikit.utils.PyingUtils;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 2019-12-04 09:47
 * 选择联系人
 */
public class ChooseContactsActivity extends WKBaseActivity<ActChooseContactsLayoutBinding> implements OnQuickSideBarTouchListener {

    ChooseUserSelectedAdapter selectedAdapter;
    ContactsAdapter contactsAdapter;
    private Button rightBtn;
    //单选
    private boolean singleChoose;
    //选择后返回
    private boolean chooseBack;
    //业务操作是否包含传递的uids
    private boolean isIncludeUids;
    //不能选择的uids
    private String unSelectUids;
    // 不显示的uids
    private String unVisibleUIDs;
    //所有用户
    private List<FriendUIEntity> allList;
    //默认选中的用户
    private List<WKChannel> defaultSelected;
    private int type;//0：创建群聊1：群聊加人2：选择用户
    private String groupId;
    private List<WKMessageContent> msgContentList;
    private int maxSelectCount = -1;
    private boolean isShowSaveLabelDialog = false;

    @Override
    protected ActChooseContactsLayoutBinding getViewBinding() {
        return ActChooseContactsLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        if (type == 1) {
            titleTv.setText(R.string.add_group_members);
        } else if (type == 2) {
            titleTv.setText(R.string.choose_contacts);
        } else
            titleTv.setText(R.string.choose_contacts);
    }

    @Override
    protected void initPresenter() {

        if (getIntent().hasExtra("type")) type = getIntent().getIntExtra("type", 0);
        if (getIntent().hasExtra("groupId")) groupId = getIntent().getStringExtra("groupId");
        if (getIntent().hasExtra("isIncludeUids"))
            isIncludeUids = getIntent().getBooleanExtra("isIncludeUids", false);
        if (getIntent().hasExtra("unSelectUids")) {
            unSelectUids = getIntent().getStringExtra("unSelectUids");
        }
        if (getIntent().hasExtra("chooseBack"))
            chooseBack = getIntent().getBooleanExtra("chooseBack", false);
        if (getIntent().hasExtra("singleChoose"))
            singleChoose = getIntent().getBooleanExtra("singleChoose", false);
        if (getIntent().hasExtra("msgContentList")) {
            msgContentList = getIntent().getParcelableArrayListExtra("msgContentList");
        }
        if (getIntent().hasExtra("defaultSelected")) {
            defaultSelected = getIntent().getParcelableArrayListExtra("defaultSelected");
        }
        if (getIntent().hasExtra("maxSelectCount"))
            maxSelectCount = getIntent().getIntExtra("maxSelectCount", -1);
        if (getIntent().hasExtra("unVisibleUIDs")) {
            unVisibleUIDs = getIntent().getStringExtra("unVisibleUIDs");
        }

        if (getIntent().hasExtra("isShowSaveLabelDialog"))
            isShowSaveLabelDialog = getIntent().getBooleanExtra("isShowSaveLabelDialog", false);
    }

    @Override
    protected void initView() {
        wkVBinding.quickSideBarView.setTextChooseColor(Theme.colorAccount);
        wkVBinding.quickSideBarTipsView.setBackgroundColor(Theme.colorAccount);
        contactsAdapter = new ContactsAdapter();
        initAdapter(wkVBinding.recyclerView, contactsAdapter);
        selectedAdapter = new ChooseUserSelectedAdapter(new ChooseUserSelectedAdapter.IGetEdit() {
            @Override
            public void onDeleted(String uid) {
                for (int i = 0, size = contactsAdapter.getData().size(); i < size; i++) {
                    if (contactsAdapter.getData().get(i).channel.channelID.equals(uid) && contactsAdapter.getData().get(i).isCanCheck) {
                        contactsAdapter.getData().get(i).check = !contactsAdapter.getData().get(i).check;
                        contactsAdapter.notifyItemChanged(i, contactsAdapter.getData().get(i));
                        break;
                    }
                }
                new Handler().postDelayed(() -> checkSelect(), 300);
            }

            @Override
            public void searchUser(String key) {
                ChooseContactsActivity.this.searchUser(key);
            }
        });
        FriendUIEntity ui = new FriendUIEntity(new WKChannel("", WKChannelType.PERSONAL));
        ui.itemType = 1;
        selectedAdapter.addData(ui);
        wkVBinding.selectUserRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        wkVBinding.selectUserRecyclerView.setAdapter(selectedAdapter);
    }

    @Override
    protected String getRightBtnText(Button titleRightBtn) {
        this.rightBtn = titleRightBtn;
        CommonAnim.getInstance().showOrHide(this.rightBtn, false,false,true);
        return getString(R.string.sure);
    }

    @Override
    protected void rightButtonClick() {
        super.rightButtonClick();

//        List<FriendUIEntity> list = contactsAdapter.getData();
        List<FriendUIEntity> list = allList;
        if (type == 0 || type == 1) {

            List<String> ids = new ArrayList<>();
            List<String> names = new ArrayList<>();
            StringBuilder name = new StringBuilder();
            int count = 0;
            for (int i = 0, size = list.size(); i < size; i++) {
                if (list.get(i).check || (!list.get(i).isCanCheck && isIncludeUids)) {
                    ids.add(list.get(i).channel.channelID);
                    names.add(list.get(i).channel.channelName);
                    if (count < 3) {
                        if (!TextUtils.isEmpty(name)) {
                            name.append("、");
                        }
                        name.append(list.get(i).channel.channelName);
                    }
                    count++;
                }
            }
            showTitleRightLoading();
            rightBtn.setVisibility(View.GONE);
            CommonAnim.getInstance().showOrHide(rightBtn, false,false,true);
            if (type == 0) {
                if (ids.size() == 1) {
                    WKIMUtils.getInstance().startChatActivity(new ChatViewMenu(this, ids.get(0), WKChannelType.PERSONAL, 0, true, msgContentList));
                    finish();
                } else {
                    GroupModel.getInstance().createGroup(name.toString(), ids, names, (code, msg, groupEntity) -> {
                        hideTitleRightLoading();
                        if (code == HttpResponseCode.success && groupEntity != null) {
                            WKIMUtils.getInstance().startChatActivity(new ChatViewMenu(this, groupEntity.group_no, WKChannelType.GROUP, 0, true, msgContentList));
                            finish();
                        } else {
                            CommonAnim.getInstance().showOrHide(rightBtn, true,true,false);
                            hideTitleRightLoading();
                            showToast(msg);
                        }
                    });
                }
            } else {
                //判断群是否开启邀请模式或判断当前登录用户的身份
                WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(groupId, WKChannelType.GROUP);
                int addType = 1;//添加类型
                if (channel != null) {
                    if (channel.invite == 1) {
                        //邀请确认
                        WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(groupId, WKChannelType.GROUP, WKConfig.getInstance().getUid());
                        if (member == null || member.isDeleted == 1) return;
                        addType = member.role == WKChannelMemberRole.normal ? 2 : 1;
                    }
                }

                if (addType == 1) {
                    GroupModel.getInstance().addGroupMembers(groupId, ids, names, (code, msg) -> {
                        if (code == HttpResponseCode.success) {
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            hideTitleRightLoading();
                            rightBtn.setVisibility(View.VISIBLE);
                            showToast(msg);
                        }
                    });
                } else {
                    GroupModel.getInstance().inviteGroupMembers(groupId, ids, (code, msg) -> {
                        if (code == HttpResponseCode.success) {
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            hideTitleRightLoading();
                            CommonAnim.getInstance().showOrHide(rightBtn, true,false,true);
                            showToast(msg);
                        }
                    });
                }
            }
        } else {
            List<WKChannel> selectedList = new ArrayList<>();
            for (int i = 0, size = list.size(); i < size; i++) {
                if (list.get(i).check) {
                    selectedList.add(list.get(i).channel);
                }
            }
            if (WKReader.isNotEmpty(selectedList) && isShowSaveLabelDialog) {
                WKDialogUtils.getInstance().showDialog(this, getString(R.string.message_tips), getString(R.string.save_label_tips), true, getString(R.string.cancel), getString(R.string.save_label_sure), 0, Theme.colorAccount, index -> {
                    if (index == 1) {
                        EndpointManager.getInstance().invoke("save_label", new SaveLabelMenu(ChooseContactsActivity.this, selectedList));
                        finish();
                    } else {
                        WKUIKitApplication.getInstance().setChooseContactsBack(selectedList);
                        finish();
                    }
                });
            } else {
                WKUIKitApplication.getInstance().setChooseContactsBack(selectedList);
                finish();
            }
        }

    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void initListener() {
        wkVBinding.quickSideBarView.setLetters(CharacterParser.getInstance().getList());
        wkVBinding.quickSideBarView.setOnQuickSideBarTouchListener(this);
        contactsAdapter.setOnItemClickListener((adapter, view1, position) -> {
            FriendUIEntity friendEntity = (FriendUIEntity) adapter.getItem(position);
            if (friendEntity != null) {
                if (singleChoose && chooseBack) {
                    Intent intent = new Intent();
                    intent.putExtra("uid", friendEntity.channel.channelID);
                    setResult(RESULT_OK, intent);
                    finish();
                } else {
                    //是否可以勾选判断、群内黑名单 不能勾选
                    if (!TextUtils.isEmpty(groupId) && UserUtils.getInstance().checkGroupBlacklist(groupId, friendEntity.channel.channelID)) {
                        WKToastUtils.getInstance().showToastFail(WKUIKitApplication.getInstance().getContext().getString(R.string.no_group_blacklist_noselect));
                        return;
                    }
                    if (maxSelectCount != -1 && selectedAdapter.getItemCount() >= maxSelectCount) {
                        String content = String.format(getString(R.string.max_select_count), maxSelectCount);
                        showDialog(content, null);
                        return;
                    }
                    if (friendEntity.isCanCheck) {
                        friendEntity.check = !friendEntity.check;
                        contactsAdapter.notifyItemChanged(position, friendEntity);
                        if (friendEntity.check) {
                            friendEntity.isSetDelete = false;
                            selectedAdapter.addData(selectedAdapter.getData().size() - 1, friendEntity);
                            wkVBinding.selectUserRecyclerView.scrollToPosition(selectedAdapter.getData().size() - 1);
                        } else {
                            for (int i = 0, size = selectedAdapter.getData().size(); i < size; i++) {
                                if (selectedAdapter.getData().get(i).channel.channelID.equalsIgnoreCase(friendEntity.channel.channelID)) {
                                    selectedAdapter.removeAt(i);
                                    break;
                                }
                            }
                        }
                        selectedAdapter.notifyItemChanged(selectedAdapter.getData().size() - 1, selectedAdapter.getData().get(selectedAdapter.getData().size() - 1));
                        SoftKeyboardUtils.getInstance().hideSoftKeyboard(this);
                    }
                    checkSelect();
                }
            }
        });

        wkVBinding.selectUserRecyclerView.setOnTouchListener((view, motionEvent) -> {
            View childView = wkVBinding.selectUserRecyclerView.getChildAt(selectedAdapter.getData().size() - 1);
            if (childView != null) {
                EditText editText = childView.findViewById(R.id.searchEt);
                SoftKeyboardUtils.getInstance().showSoftKeyBoard(ChooseContactsActivity.this, editText);
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
                for (int i = 0, size = contactsAdapter.getData().size(); i < size; i++) {
                    if (contactsAdapter.getData().get(i).channel.channelID.equalsIgnoreCase(userEntity.channel.channelID) && contactsAdapter.getData().get(i).isCanCheck) {
                        contactsAdapter.getData().get(i).check = !contactsAdapter.getData().get(i).check;
                        contactsAdapter.notifyItemChanged(i, contactsAdapter.getData().get(i));
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

    }

    @Override
    protected void initData() {
        super.initData();
        WKAPPConfig wkappConfig = WKConfig.getInstance().getAppConfig();
        int inviteSystemAccountJoinGroupOn = 0;
        if (wkappConfig != null) {
            inviteSystemAccountJoinGroupOn = wkappConfig.invite_system_account_join_group_on;
        }
        List<WKChannel> tempList = WKIM.getInstance().getChannelManager().getWithFollowAndStatus(WKChannelType.PERSONAL, 1, 1);
        List<FriendUIEntity> list = new ArrayList<>();
        for (int i = 0, size = tempList.size(); i < size; i++) {
            if (!TextUtils.isEmpty(unVisibleUIDs) && unVisibleUIDs.contains(tempList.get(i).channelID))
                continue;
            if (inviteSystemAccountJoinGroupOn == 0 && tempList.get(i).channelID.equals(WKSystemAccount.system_file_helper)) {
                continue;
            }
            FriendUIEntity friendUIEntity = new FriendUIEntity(tempList.get(i));
            if (!TextUtils.isEmpty(unSelectUids) && unSelectUids.contains(tempList.get(i).channelID)) {
                friendUIEntity.isCanCheck = false;
            }
            boolean isCheck = false;
            if (type == 2 && WKReader.isNotEmpty(defaultSelected)) {
                for (int j = 0, len = defaultSelected.size(); j < len; j++) {
                    if (friendUIEntity.channel.channelID.equals(defaultSelected.get(j).channelID)
                            && friendUIEntity.channel.channelType == defaultSelected.get(j).channelType) {
                        isCheck = true;
                        break;
                    }
                }
            }
            friendUIEntity.check = isCheck;

            list.add(friendUIEntity);
        }
        List<FriendUIEntity> otherList = new ArrayList<>();
        List<FriendUIEntity> letterList = new ArrayList<>();
        List<FriendUIEntity> numList = new ArrayList<>();
        for (int i = 0, size = list.size(); i < size; i++) {
            String showName = list.get(i).channel.channelRemark;
            if (TextUtils.isEmpty(showName))
                showName = list.get(i).channel.channelName;
            if (!TextUtils.isEmpty(showName)) {
                if (PyingUtils.getInstance().isStartNum(showName)) {
                    list.get(i).pying = "#";
                } else
                    list.get(i).pying = Pinyin.toPinyin(showName, "").toUpperCase();
            } else list.get(i).pying = "#";

        }
        PyingUtils.getInstance().sortListBasic(list);

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
        contactsAdapter.setList(allList);
        hideTitleRightView();
    }

    private void searchUser(String content) {
        if (TextUtils.isEmpty(content)) {
            contactsAdapter.setList(allList);
            return;
        }
        List<FriendUIEntity> tempList = new ArrayList<>();
        for (int i = 0, size = allList.size(); i < size; i++) {
            if ((!TextUtils.isEmpty(allList.get(i).channel.channelName) && allList.get(i).channel.channelName.toLowerCase(Locale.getDefault())
                    .contains(content.toLowerCase(Locale.getDefault())))
                    || (!TextUtils.isEmpty(allList.get(i).channel.channelRemark) && allList.get(i).channel.channelRemark.toLowerCase(Locale.getDefault())
                    .contains(content.toLowerCase(Locale.getDefault())))
                    || content.contains(allList.get(i).pying.toLowerCase(
                    Locale.getDefault()))) {
                tempList.add(allList.get(i));
            }
        }
        contactsAdapter.setList(tempList);
    }

    @Override
    public void onLetterChanged(String letter, int position, float y) {
        wkVBinding.quickSideBarTipsView.setText(letter, position, y);
        //有此key则获取位置并滚动到该位置
        List<FriendUIEntity> list = contactsAdapter.getData();
        if (WKReader.isNotEmpty(list)) {
            for (int i = 0, size = list.size(); i < size; i++) {
                if (list.get(i).pying.startsWith(letter)) {
                    wkVBinding.recyclerView.smoothScrollToPosition(i);
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

    private void checkSelect() {
        int count = selectedAdapter.getData().size() - 1;
        if (count > 0 || type == 2) {
            CommonAnim.getInstance().showOrHide(rightBtn, true,true,false);
            if (count > 0)
                rightBtn.setText(String.format("%s(%s)", getString(R.string.sure), count));
            else rightBtn.setText(R.string.sure);
        } else {
            rightBtn.setText(R.string.sure);
            CommonAnim.getInstance().showOrHide(rightBtn, false,true,true);
        }
    }
}
