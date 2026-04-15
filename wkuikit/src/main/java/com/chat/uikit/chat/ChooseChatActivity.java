package com.chat.uikit.chat;

import android.content.Intent;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.msgitem.WKChannelMemberRole;
import com.chat.base.ui.components.SegmentTabView;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.WKReader;
import com.chat.uikit.R;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.category.CategoryEntity;
import com.chat.uikit.category.CategoryModel;
import com.chat.uikit.chat.adapter.ChooseChatAdapter;
import com.chat.uikit.contacts.ChooseContactsActivity;
import com.chat.uikit.databinding.ActChooseChatLayoutBinding;
import com.chat.uikit.message.MsgModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelStatus;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKUIConversationMsg;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * 选择会话页面 — 群聊/私聊 Tab + 群聊按分组展示
 */
public class ChooseChatActivity extends WKBaseActivity<ActChooseChatLayoutBinding> {
    private ChooseChatAdapter chooseChatAdapter;
    private Button rightBtn;
    private boolean isChoose;

    // 全部会话（含群聊+私聊），按 channelType 分开
    private List<ChooseChatEntity> allList;
    private List<ChooseChatEntity> groupList;
    private List<ChooseChatEntity> personalList;

    // Tab 切换：0=群聊, 1=私聊
    private int currentTab = 0;
    private SegmentTabView segmentTabView;
    private List<CategoryEntity> categoryList = new ArrayList<>();

    @Override
    protected ActChooseChatLayoutBinding getViewBinding() {
        return ActChooseChatLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.choose_chat);
    }

    @Override
    protected String getRightBtnText(Button titleRightBtn) {
        rightBtn = titleRightBtn;
        return getString(R.string.sure);
    }

    @Override
    protected void rightButtonClick() {
        super.rightButtonClick();

        List<WKUIConversationMsg> selectedList = new ArrayList<>();
        for (int i = 0, size = chooseChatAdapter.getData().size(); i < size; i++) {
            ChooseChatEntity entity = chooseChatAdapter.getData().get(i);
            if (entity.isSectionHeader) continue;
            if (entity.isCheck)
                selectedList.add(entity.uiConveursationMsg);
        }
        List<WKChannel> list = new ArrayList<>();
        if (WKReader.isNotEmpty(selectedList)) {
            for (int i = 0; i < selectedList.size(); i++) {
                list.add(selectedList.get(i).getWkChannel());
            }
            if (isChoose) {
                if (WKUIKitApplication.getInstance().getMessageContentList() != null) {
                    WKUIKitApplication.getInstance().showChatConfirmDialog(this, list, WKUIKitApplication.getInstance().getMessageContentList(), new WKUIKitApplication.IShowChatConfirm() {
                        @Override
                        public void onBack(@NonNull List<WKChannel> list, @NonNull List<WKMessageContent> messageContentList) {
                            WKUIKitApplication.getInstance().sendChooseChatBack(list);
                            finish();
                        }
                    });
                } else {
                    WKUIKitApplication.getInstance().sendChooseChatBack(list);
                    finish();
                }
            } else {
                Intent intent = new Intent();
                intent.putParcelableArrayListExtra("list", (ArrayList<? extends Parcelable>) list);
                setResult(RESULT_OK, intent);
                finish();
            }
        }
    }

    @Override
    protected void initPresenter() {
        isChoose = getIntent().getBooleanExtra("isChoose", false);
    }

    @Override
    protected void initView() {
        chooseChatAdapter = new ChooseChatAdapter(new ArrayList<>());
        initAdapter(wkVBinding.recyclerView, chooseChatAdapter);

        // 创建新聊天按钮
        wkVBinding.createTv.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChooseContactsActivity.class);
            if (WKUIKitApplication.getInstance().getMessageContentList() != null)
                intent.putParcelableArrayListExtra("msgContentList", (ArrayList<? extends Parcelable>) WKUIKitApplication.getInstance().getMessageContentList());
            startActivity(intent);
        });

        // 分段 Tab 切换控件
        segmentTabView = new SegmentTabView(this,
                new String[]{getString(R.string.str_group_chat), getString(R.string.str_private_chat)});
        wkVBinding.segmentTabContainer.addView(segmentTabView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT));
        segmentTabView.setOnTabSelectedListener(index -> {
            currentTab = index;
            String searchText = wkVBinding.searchEt.getText() != null
                    ? wkVBinding.searchEt.getText().toString() : "";
            if (TextUtils.isEmpty(searchText)) {
                filterAndDisplay();
            } else {
                searchUser(searchText);
            }
        });
    }

    @Override
    protected void rightLayoutClick() {
        super.rightLayoutClick();
    }

    @Override
    protected void initListener() {
        chooseChatAdapter.setOnItemClickListener((adapter, view1, position) -> {
            ChooseChatEntity chooseChatEntity = (ChooseChatEntity) adapter.getItem(position);
            if (chooseChatEntity == null || chooseChatEntity.isSectionHeader) return;

            boolean isSelect = !chooseChatEntity.isBan && !chooseChatEntity.isForbidden;
            if (isSelect) {
                chooseChatEntity.isCheck = !chooseChatEntity.isCheck;
                int selectCount = getSelectedCount();
                if (chooseChatEntity.isCheck && selectCount > 9) {
                    chooseChatEntity.isCheck = false;
                    showSingleBtnDialog(String.format(getString(R.string.max_select_count_chat), 9));
                    adapter.notifyItemChanged(position + adapter.getHeaderLayoutCount());
                    return;
                }
                adapter.notifyItemChanged(position + adapter.getHeaderLayoutCount(), chooseChatEntity);
                updateRightBtn();
            }
        });

        // section header 折叠/展开
        chooseChatAdapter.setSectionToggleListener((sectionId, collapsed) -> filterAndDisplay());

        wkVBinding.searchEt.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        wkVBinding.searchEt.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                SoftKeyboardUtils.getInstance().hideSoftKeyboard(ChooseChatActivity.this);
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
                searchUser(editable.toString());
            }
        });
    }

    private int getSelectedCount() {
        int count = 0;
        for (int i = 0, size = allList.size(); i < size; i++) {
            if (allList.get(i).isCheck) count++;
        }
        return count;
    }

    private void updateRightBtn() {
        int count = getSelectedCount();
        if (count > 0) {
            rightBtn.setVisibility(View.VISIBLE);
            rightBtn.setText(String.format("%s(%s)", getString(R.string.sure), count));
        } else {
            rightBtn.setText(R.string.sure);
            rightBtn.setVisibility(View.INVISIBLE);
        }
    }

    private void searchUser(String content) {
        if (TextUtils.isEmpty(content)) {
            filterAndDisplay();
            return;
        }
        // 在当前 tab 对应的列表中搜索
        List<ChooseChatEntity> source = currentTab == 0 ? groupList : personalList;
        List<ChooseChatEntity> tempList = new ArrayList<>();
        String lowerContent = content.toLowerCase(Locale.getDefault());
        for (int i = 0, size = source.size(); i < size; i++) {
            ChooseChatEntity entity = source.get(i);
            if (entity.uiConveursationMsg == null || entity.uiConveursationMsg.getWkChannel() == null)
                continue;
            String channelName = entity.uiConveursationMsg.getWkChannel().channelName;
            String channelRemark = entity.uiConveursationMsg.getWkChannel().channelRemark;
            if ((!TextUtils.isEmpty(channelName) && channelName.toLowerCase(Locale.getDefault()).contains(lowerContent))
                    || (!TextUtils.isEmpty(channelRemark) && channelRemark.toLowerCase(Locale.getDefault()).contains(lowerContent))) {
                tempList.add(entity);
            }
        }
        chooseChatAdapter.setList(tempList);
    }

    @Override
    protected void initData() {
        super.initData();
        List<WKUIConversationMsg> list = WKIM.getInstance().getConversationManager().getAll();
        allList = new ArrayList<>();
        groupList = new ArrayList<>();
        personalList = new ArrayList<>();

        for (int i = 0, size = list.size(); i < size; i++) {
            if (list.get(i).channelType == WKChannelType.COMMUNITY_TOPIC) continue;
            ChooseChatEntity chooseChatEntity = new ChooseChatEntity(list.get(i));
            if (list.get(i).getWkChannel() != null) {
                WKChannelMember mChannelMember = WKIM.getInstance().getChannelMembersManager().getMember(list.get(i).getWkChannel().channelID, list.get(i).getWkChannel().channelType, WKConfig.getInstance().getUid());
                if (list.get(i).getWkChannel().forbidden == 1) {
                    if (mChannelMember != null) {
                        chooseChatEntity.isForbidden = mChannelMember.role == WKChannelMemberRole.normal;
                    }
                } else {
                    if (mChannelMember != null)
                        chooseChatEntity.isForbidden = mChannelMember.forbiddenExpirationTime > 0;
                    else chooseChatEntity.isForbidden = false;
                }
                chooseChatEntity.isBan = list.get(i).getWkChannel().status == WKChannelStatus.statusDisabled;
            }
            allList.add(chooseChatEntity);

            // 按 channelType 分类
            if (list.get(i).channelType == WKChannelType.GROUP) {
                groupList.add(chooseChatEntity);
            } else if (list.get(i).channelType == WKChannelType.PERSONAL) {
                personalList.add(chooseChatEntity);
            }
        }

        rightBtn.setVisibility(View.GONE);

        // 加载分组数据
        loadCategories();
    }

    private void loadCategories() {
        String spaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (TextUtils.isEmpty(spaceId)) {
            categoryList = new ArrayList<>();
            filterAndDisplay();
            return;
        }
        CategoryModel.getInstance().list(spaceId, new CategoryModel.ICategoryListListener() {
            @Override
            public void onResult(List<CategoryEntity> list) {
                categoryList = list != null ? list : new ArrayList<>();
                filterAndDisplay();
            }

            @Override
            public void onError(int code, String msg) {
                categoryList = new ArrayList<>();
                filterAndDisplay();
            }
        });
    }

    private void filterAndDisplay() {
        if (currentTab == 0) {
            // 群聊 tab：按 category 分组显示
            HashMap<String, ChooseChatEntity> channelMap = new HashMap<>();
            for (ChooseChatEntity entity : groupList) {
                if (entity.uiConveursationMsg != null) {
                    channelMap.put(entity.uiConveursationMsg.channelID, entity);
                }
            }

            List<ChooseChatEntity> displayList = new ArrayList<>();

            // 用户自建分组排在前面，未分组（category_id == null）排在最后
            List<CategoryEntity> userCategories = new ArrayList<>();
            CategoryEntity defaultCategory = null;
            for (CategoryEntity category : categoryList) {
                if (category.groups == null) continue;
                if (category.category_id == null) {
                    defaultCategory = category;
                } else {
                    userCategories.add(category);
                }
            }

            // 用户自建分组
            for (CategoryEntity category : userCategories) {
                displayList.add(new ChooseChatEntity(category.category_id, category.name));
                if (!chooseChatAdapter.isSectionCollapsed(category.category_id)) {
                    List<ChooseChatEntity> sectionItems = new ArrayList<>();
                    for (CategoryEntity.CategoryGroup cg : category.groups) {
                        ChooseChatEntity entity = channelMap.get(cg.group_no);
                        if (entity != null) {
                            sectionItems.add(entity);
                        }
                    }
                    sortByTopAndTime(sectionItems);
                    displayList.addAll(sectionItems);
                }
            }

            // 未分组放在最后
            if (defaultCategory != null && !defaultCategory.groups.isEmpty()) {
                String sectionId = "ungrouped";
                String sectionTitle = defaultCategory.name != null ? defaultCategory.name : getString(R.string.default_group);
                displayList.add(new ChooseChatEntity(sectionId, sectionTitle));
                if (!chooseChatAdapter.isSectionCollapsed(sectionId)) {
                    List<ChooseChatEntity> ungroupedItems = new ArrayList<>();
                    for (CategoryEntity.CategoryGroup cg : defaultCategory.groups) {
                        ChooseChatEntity entity = channelMap.get(cg.group_no);
                        if (entity != null) {
                            ungroupedItems.add(entity);
                        }
                    }
                    sortByTopAndTime(ungroupedItems);
                    displayList.addAll(ungroupedItems);
                }
            }

            // 如果没有分组数据，直接平铺显示所有群聊
            if (categoryList.isEmpty()) {
                sortByTopAndTime(groupList);
                displayList.addAll(groupList);
            }

            chooseChatAdapter.setList(displayList);
        } else {
            // 私聊 tab：扁平列表
            chooseChatAdapter.setList(personalList);
        }
    }

    private void sortByTopAndTime(List<ChooseChatEntity> list) {
        list.sort((a, b) -> {
            if (a.uiConveursationMsg == null || b.uiConveursationMsg == null) return 0;
            int topA = (a.uiConveursationMsg.getWkChannel() != null && a.uiConveursationMsg.getWkChannel().top == 1) ? 1 : 0;
            int topB = (b.uiConveursationMsg.getWkChannel() != null && b.uiConveursationMsg.getWkChannel().top == 1) ? 1 : 0;
            if (topA != topB) return topB - topA;
            return Long.compare(b.uiConveursationMsg.lastMsgTimestamp, a.uiConveursationMsg.lastMsgTimestamp);
        });
    }

    public static class ChooseChatEntity {
        ChooseChatEntity(WKUIConversationMsg uiConveursationMsg) {
            this.uiConveursationMsg = uiConveursationMsg;
        }

        /** Section header 专用构造 */
        ChooseChatEntity(String sectionId, String sectionTitle) {
            this.isSectionHeader = true;
            this.sectionId = sectionId;
            this.sectionTitle = sectionTitle;
            this.uiConveursationMsg = null;
        }

        public WKUIConversationMsg uiConveursationMsg;
        public boolean isCheck;
        // 禁言中
        public boolean isForbidden;
        // 禁用中
        public boolean isBan;

        // Section header 支持
        public boolean isSectionHeader = false;
        public String sectionId;
        public String sectionTitle;
    }
}
