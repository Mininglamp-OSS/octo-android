/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ChooseContactsMenu;
import com.chat.base.msgitem.WKChannelMemberRole;
import com.chat.base.ui.components.SegmentTabView;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.WKReader;
import com.chat.uikit.R;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.category.CategoryEntity;
import com.chat.uikit.category.CategoryModel;
import com.chat.uikit.chat.adapter.ChooseChatAdapter;
import com.chat.uikit.databinding.ActChooseChatLayoutBinding;
import com.chat.uikit.message.MsgModel;
import com.chat.uikit.thread.service.ThreadModel;
import com.chat.uikit.thread.service.entity.ThreadEntity;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelStatus;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKUIConversationMsg;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 选择会话页面 — 群聊/私聊 Tab + 群聊按分组展示 + 子区支持
 */
public class ChooseChatActivity extends WKBaseActivity<ActChooseChatLayoutBinding> {
    private ChooseChatAdapter chooseChatAdapter;
    private Button rightBtn;
    private boolean isChoose;
    private boolean singleSelect;

    private List<ChooseChatEntity> allList;
    private List<ChooseChatEntity> groupList;
    private List<ChooseChatEntity> personalList;

    private int currentTab = 0;
    private SegmentTabView segmentTabView;
    private List<CategoryEntity> categoryList = new ArrayList<>();

    // 子区数据
    private final Map<String, List<ThreadEntity>> threadCache = new HashMap<>();
    private final Map<String, List<ChooseChatEntity>> threadEntityCache = new HashMap<>();
    private final Set<String> expandedThreadGroups = new HashSet<>();
    private boolean threadsLoaded = false;

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

        List<WKChannel> list = new ArrayList<>();
        Set<String> addedIds = new HashSet<>();
        for (int i = 0, size = chooseChatAdapter.getData().size(); i < size; i++) {
            ChooseChatEntity entity = chooseChatAdapter.getData().get(i);
            if (entity.isSectionHeader || entity.isThreadToggle) continue;
            if (!entity.isCheck) continue;

            if (entity.isThread) {
                if (addedIds.add(entity.threadChannelId)) {
                    WKChannel channel = new WKChannel(entity.threadChannelId, WKChannelType.COMMUNITY_TOPIC);
                    channel.channelName = entity.threadName;
                    list.add(channel);
                }
            } else if (entity.uiConveursationMsg != null && entity.uiConveursationMsg.getWkChannel() != null) {
                if (addedIds.add(entity.uiConveursationMsg.getWkChannel().channelID)) {
                    list.add(entity.uiConveursationMsg.getWkChannel());
                }
            }
        }

        if (WKReader.isNotEmpty(list)) {
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
        singleSelect = getIntent().getBooleanExtra("singleSelect", false);
    }

    @Override
    protected void initView() {
        chooseChatAdapter = new ChooseChatAdapter(new ArrayList<>());
        initAdapter(wkVBinding.recyclerView, chooseChatAdapter);

        wkVBinding.createTv.setOnClickListener(v -> {
            ChooseContactsMenu contactsMenu = new ChooseContactsMenu(9, true, false, null, selectedList -> {
                if (WKReader.isNotEmpty(selectedList)) {
                    if (isChoose && WKUIKitApplication.getInstance().getMessageContentList() != null) {
                        WKUIKitApplication.getInstance().showChatConfirmDialog(this, selectedList, WKUIKitApplication.getInstance().getMessageContentList(), new WKUIKitApplication.IShowChatConfirm() {
                            @Override
                            public void onBack(@NonNull List<WKChannel> list, @NonNull List<com.xinbida.wukongim.msgmodel.WKMessageContent> messageContentList) {
                                WKUIKitApplication.getInstance().sendChooseChatBack(list);
                                finish();
                            }
                        });
                    } else {
                        WKUIKitApplication.getInstance().sendChooseChatBack(selectedList);
                        finish();
                    }
                }
            });
            EndpointManager.getInstance().invoke("choose_contacts", contactsMenu);
        });

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

            // 子区折叠/展开 toggle
            if (chooseChatEntity.isThreadToggle) {
                String groupNo = chooseChatEntity.parentGroupNo;
                if (expandedThreadGroups.contains(groupNo)) {
                    expandedThreadGroups.remove(groupNo);
                } else {
                    expandedThreadGroups.add(groupNo);
                }
                filterAndDisplay();
                return;
            }

            boolean isSelect = !chooseChatEntity.isBan && !chooseChatEntity.isForbidden;
            if (isSelect) {
                chooseChatEntity.isCheck = !chooseChatEntity.isCheck;
                if (singleSelect && chooseChatEntity.isCheck) {
                    // 单选模式：取消其他已选项
                    for (int j = 0, s = chooseChatAdapter.getData().size(); j < s; j++) {
                        ChooseChatEntity other = chooseChatAdapter.getData().get(j);
                        if (other != chooseChatEntity && other.isCheck) {
                            other.isCheck = false;
                            adapter.notifyItemChanged(j + adapter.getHeaderLayoutCount(), other);
                        }
                    }
                }
                int selectCount = getSelectedCount();
                if (!singleSelect && chooseChatEntity.isCheck && selectCount > 9) {
                    chooseChatEntity.isCheck = false;
                    showSingleBtnDialog(String.format(getString(R.string.max_select_count_chat), 9));
                    adapter.notifyItemChanged(position + adapter.getHeaderLayoutCount());
                    return;
                }
                adapter.notifyItemChanged(position + adapter.getHeaderLayoutCount(), chooseChatEntity);
                updateRightBtn();
            }
        });

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
        for (List<ChooseChatEntity> threadItems : threadEntityCache.values()) {
            for (ChooseChatEntity entity : threadItems) {
                if (entity.isCheck) count++;
            }
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
        List<ChooseChatEntity> source = currentTab == 0 ? groupList : personalList;
        List<ChooseChatEntity> tempList = new ArrayList<>();
        String lowerContent = content.toLowerCase(Locale.getDefault());
        for (int i = 0, size = source.size(); i < size; i++) {
            ChooseChatEntity entity = source.get(i);
            if (entity.uiConveursationMsg == null || entity.uiConveursationMsg.getWkChannel() == null)
                continue;
            String channelName = entity.uiConveursationMsg.getWkChannel().channelName;
            String channelRemark = entity.uiConveursationMsg.getWkChannel().channelRemark;
            if (matchSearch(channelName, lowerContent) || matchSearch(channelRemark, lowerContent)) {
                tempList.add(entity);
            }
        }
        if (currentTab == 0) {
            for (List<ChooseChatEntity> threadItems : threadEntityCache.values()) {
                for (ChooseChatEntity entity : threadItems) {
                    if (!TextUtils.isEmpty(entity.threadName)
                            && entity.threadName.toLowerCase(Locale.getDefault()).contains(lowerContent)) {
                        tempList.add(entity);
                    }
                }
            }
        }
        chooseChatAdapter.setList(tempList);
    }

    private boolean matchSearch(String text, String lowerContent) {
        return !TextUtils.isEmpty(text) && text.toLowerCase(Locale.getDefault()).contains(lowerContent);
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

            if (list.get(i).channelType == WKChannelType.GROUP) {
                groupList.add(chooseChatEntity);
            } else if (list.get(i).channelType == WKChannelType.PERSONAL) {
                personalList.add(chooseChatEntity);
            }
        }

        rightBtn.setVisibility(View.GONE);
        loadCategories();
    }

    private void loadCategories() {
        String spaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (TextUtils.isEmpty(spaceId)) {
            categoryList = new ArrayList<>();
            loadAllThreads();
            return;
        }
        CategoryModel.getInstance().list(spaceId, new CategoryModel.ICategoryListListener() {
            @Override
            public void onResult(List<CategoryEntity> list) {
                categoryList = list != null ? list : new ArrayList<>();
                loadAllThreads();
            }

            @Override
            public void onError(int code, String msg) {
                categoryList = new ArrayList<>();
                loadAllThreads();
            }
        });
    }

    private void loadAllThreads() {
        if (WKConfig.getInstance().getAppConfig().thread_on != 1 || groupList.isEmpty()) {
            threadsLoaded = true;
            filterAndDisplay();
            return;
        }

        final int[] pending = {groupList.size()};
        for (ChooseChatEntity groupEntity : groupList) {
            if (groupEntity.uiConveursationMsg == null) {
                pending[0]--;
                if (pending[0] <= 0) {
                    threadsLoaded = true;
                    filterAndDisplay();
                }
                continue;
            }
            String groupNo = groupEntity.uiConveursationMsg.channelID;
            ThreadModel.getInstance().listThreads(groupNo, (code, msg, result) -> {
                if (result != null) {
                    threadCache.put(groupNo, result);
                    List<ChooseChatEntity> threadItems = new ArrayList<>();
                    List<ThreadEntity> activeList = new ArrayList<>();
                    for (ThreadEntity entity : result) {
                        if (entity.status == 1) {
                            activeList.add(entity);
                        }
                    }
                    Collections.sort(activeList, (a, b) -> {
                        String ua = a.updated_at != null ? a.updated_at : "";
                        String ub = b.updated_at != null ? b.updated_at : "";
                        return ub.compareTo(ua);
                    });
                    for (ThreadEntity te : activeList) {
                        String channelId = ThreadModel.getInstance().buildChannelId(groupNo, te.short_id);
                        threadItems.add(new ChooseChatEntity(channelId, te.name));
                    }
                    threadEntityCache.put(groupNo, threadItems);
                }
                pending[0]--;
                if (pending[0] <= 0) {
                    threadsLoaded = true;
                    filterAndDisplay();
                }
            });
        }
    }

    private void filterAndDisplay() {
        if (currentTab == 0) {
            HashMap<String, ChooseChatEntity> channelMap = new HashMap<>();
            for (ChooseChatEntity entity : groupList) {
                if (entity.uiConveursationMsg != null) {
                    channelMap.put(entity.uiConveursationMsg.channelID, entity);
                }
            }

            List<ChooseChatEntity> displayList = new ArrayList<>();

            List<CategoryEntity> userCategories = new ArrayList<>();
            CategoryEntity defaultCategory = null;
            for (CategoryEntity category : categoryList) {
                if (category.groups == null) continue;
                if (category.is_default) {
                    defaultCategory = category;
                } else {
                    userCategories.add(category);
                }
            }

            for (CategoryEntity category : userCategories) {
                displayList.add(new ChooseChatEntity(category.category_id, category.name, true));
                if (!chooseChatAdapter.isSectionCollapsed(category.category_id)) {
                    List<ChooseChatEntity> sectionItems = new ArrayList<>();
                    for (CategoryEntity.CategoryGroup cg : category.groups) {
                        ChooseChatEntity entity = channelMap.get(cg.group_no);
                        if (entity != null) {
                            sectionItems.add(entity);
                        }
                    }
                    sortByTopAndTime(sectionItems);
                    for (ChooseChatEntity item : sectionItems) {
                        displayList.add(item);
                        appendThreads(displayList, item);
                    }
                }
            }

            if (defaultCategory != null && !defaultCategory.groups.isEmpty()) {
                String sectionId = "ungrouped";
                String sectionTitle = defaultCategory.name != null ? defaultCategory.name : getString(R.string.default_group);
                displayList.add(new ChooseChatEntity(sectionId, sectionTitle, true));
                if (!chooseChatAdapter.isSectionCollapsed(sectionId)) {
                    List<ChooseChatEntity> ungroupedItems = new ArrayList<>();
                    for (CategoryEntity.CategoryGroup cg : defaultCategory.groups) {
                        ChooseChatEntity entity = channelMap.get(cg.group_no);
                        if (entity != null) {
                            ungroupedItems.add(entity);
                        }
                    }
                    sortByTopAndTime(ungroupedItems);
                    for (ChooseChatEntity item : ungroupedItems) {
                        displayList.add(item);
                        appendThreads(displayList, item);
                    }
                }
            }

            if (categoryList.isEmpty()) {
                List<ChooseChatEntity> sorted = new ArrayList<>(groupList);
                sortByTopAndTime(sorted);
                for (ChooseChatEntity item : sorted) {
                    displayList.add(item);
                    appendThreads(displayList, item);
                }
            }

            chooseChatAdapter.setList(displayList);
        } else {
            chooseChatAdapter.setList(personalList);
        }
    }

    private void appendThreads(List<ChooseChatEntity> displayList, ChooseChatEntity groupEntity) {
        if (!threadsLoaded || groupEntity.uiConveursationMsg == null) return;
        String groupNo = groupEntity.uiConveursationMsg.channelID;
        List<ChooseChatEntity> threads = threadEntityCache.get(groupNo);
        if (threads == null || threads.isEmpty()) return;

        boolean expanded = expandedThreadGroups.contains(groupNo);
        // toggle 始终在子区上方，点击切换展开/折叠
        displayList.add(ChooseChatEntity.threadToggle(groupNo, threads.size(), expanded));
        if (expanded) {
            displayList.addAll(threads);
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
        ChooseChatEntity(String sectionId, String sectionTitle, boolean isSectionHeader) {
            this.isSectionHeader = isSectionHeader;
            this.sectionId = sectionId;
            this.sectionTitle = sectionTitle;
            this.uiConveursationMsg = null;
        }

        /** 子区条目专用构造 */
        ChooseChatEntity(String threadChannelId, String threadName) {
            this.isThread = true;
            this.threadChannelId = threadChannelId;
            this.threadName = threadName;
            this.uiConveursationMsg = null;
        }

        /** 子区折叠/展开 toggle */
        static ChooseChatEntity threadToggle(String parentGroupNo, int threadCount, boolean expanded) {
            ChooseChatEntity entity = new ChooseChatEntity((WKUIConversationMsg) null);
            entity.uiConveursationMsg = null;
            entity.isThreadToggle = true;
            entity.parentGroupNo = parentGroupNo;
            entity.threadCount = threadCount;
            entity.threadExpanded = expanded;
            return entity;
        }

        public WKUIConversationMsg uiConveursationMsg;
        public boolean isCheck;
        public boolean isForbidden;
        public boolean isBan;

        // Section header
        public boolean isSectionHeader = false;
        public String sectionId;
        public String sectionTitle;

        // 子区
        public boolean isThread = false;
        public String threadChannelId;
        public String threadName;

        // 子区折叠/展开 toggle
        public boolean isThreadToggle = false;
        public String parentGroupNo;
        public int threadCount;
        public boolean threadExpanded;
    }
}
