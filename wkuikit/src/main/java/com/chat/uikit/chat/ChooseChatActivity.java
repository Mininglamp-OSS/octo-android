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
import androidx.annotation.Nullable;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.msgitem.WKChannelMemberRole;
import com.chat.base.space.SpaceFilter;
import com.chat.base.ui.components.SegmentTabView;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.WKReader;
import com.chat.uikit.R;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.category.CategoryEntity;
import com.chat.uikit.category.CategoryModel;
import com.chat.uikit.chat.adapter.ChooseChatAdapter;
import com.chat.uikit.chat.choose.ForwardDirectoryActivity;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 选择会话页. 1:1 对齐 iOS WKForwardSelectVC:
 *   - 关注 tab: 自定义分组(category) + 群下子区按需展开 + (将来) 已关注 DM
 *   - 最近 tab: 完整会话列表(DM + 群 + 子区,不过滤),按置顶 + 时间排序
 *   - "新建会话" 紫字按钮 → 跳 ForwardDirectoryActivity (3 tab 群聊/联系人/Bot)
 *
 * 对外契约保持不变: isChoose / singleSelect extra 行为 + setResult("list") 协议 byte-identical。
 */
public class ChooseChatActivity extends WKBaseActivity<ActChooseChatLayoutBinding> {
    private static final int REQ_FORWARD_DIRECTORY = 0x9001;

    /**
     * 进入页面时已选中的频道列表 (Parcelable<WKChannel>),用于"二次编辑": 例如发起总结
     * 选过一批源后再次进入选择页, 这些频道应该自动打勾,而不是清空。
     * 与 setResult("list") 协议同 key,不增加额外 extra 名,调用方传 putParcelableArrayListExtra
     * (PRESELECTED_CHANNELS, list) 即可。
     */
    public static final String EXTRA_PRESELECTED_CHANNELS = "preselected_channels";

    private ChooseChatAdapter chooseChatAdapter;
    private Button rightBtn;
    private boolean isChoose;
    private boolean singleSelect;

    private List<ChooseChatEntity> allList;
    private List<ChooseChatEntity> groupList;
    /** 最近 tab 数据源, 装所有会话(不过滤),含 DM/群/子区,对齐 iOS recent tab 语义。 */
    private List<ChooseChatEntity> recentList;

    /**
     * 来自 ForwardDirectoryActivity 的额外选中频道(主页 allList 中没有的群/Bot/老联系人),
     * uniqueKey ("channelId|channelType") → WKChannel,确认时与本页选中合并。
     */
    private final LinkedHashMap<String, WKChannel> extraSelectedChannels = new LinkedHashMap<>();

    private int currentTab = 0;
    private SegmentTabView segmentTabView;
    private List<CategoryEntity> categoryList = new ArrayList<>();

    // 子区数据
    private final Map<String, List<ThreadEntity>> threadCache = new HashMap<>();
    private final Map<String, List<ChooseChatEntity>> threadEntityCache = new HashMap<>();
    private final Set<String> expandedThreadGroups = new HashSet<>();
    private boolean threadsLoaded = false;

    /**
     * 调用方通过 {@link #EXTRA_PRESELECTED_CHANNELS} 传入的预选频道列表,
     * 在 initData / threads 载入完成后, 把对应 entity 标记 isCheck=true, 同时
     * 对子区把父群 groupNo 加进 expandedThreadGroups 让父群默认展开,
     * 主页 entity 找不到的(典型来自 ForwardDirectoryActivity 的群/Bot/老联系人)
     * 入 extraSelectedChannels, 跨返回上下文不丢勾。
     */
    private ArrayList<WKChannel> preselectedChannels;

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

        // 收集已选: 必须遍历底层数据 (allList + threadEntityCache),不要只看
        // chooseChatAdapter.getData() 那是当前可见行 — 搜索过滤 / tab 切换 / 父群折叠
        // 都会让已勾选项暂时不在可见列表里; 与 getSelectedCount() 同口径,否则计数与实际
        // 回传不一致,用户最后看到的"已选 N"会比真实回传多。
        List<WKChannel> list = new ArrayList<>();
        Set<String> addedIds = new HashSet<>();
        if (allList != null) {
            for (int i = 0, size = allList.size(); i < size; i++) {
                ChooseChatEntity entity = allList.get(i);
                if (!entity.isCheck) continue;
                // allList 的子区是顶层 conversation (最近 tab 那种), wkChannel 直接拿
                if (entity.uiConveursationMsg != null && entity.uiConveursationMsg.getWkChannel() != null) {
                    String cid = entity.uiConveursationMsg.getWkChannel().channelID;
                    if (!TextUtils.isEmpty(cid) && addedIds.add(cid)) {
                        list.add(entity.uiConveursationMsg.getWkChannel());
                    }
                }
            }
        }
        // threadEntityCache 装的是关注 tab 父群下挂载的子区 item, 与 allList 同 channelID
        // 时被 addedIds 去重, 不重复添加。
        for (List<ChooseChatEntity> threadItems : threadEntityCache.values()) {
            for (ChooseChatEntity entity : threadItems) {
                if (!entity.isCheck) continue;
                if (TextUtils.isEmpty(entity.threadChannelId)) continue;
                if (!addedIds.add(entity.threadChannelId)) continue;
                WKChannel channel = new WKChannel(entity.threadChannelId, WKChannelType.COMMUNITY_TOPIC);
                channel.channelName = entity.threadName;
                list.add(channel);
            }
        }
        // 合并 ForwardDirectoryActivity 带回的额外频道(去重 by channelID)
        for (WKChannel ch : extraSelectedChannels.values()) {
            if (ch != null && !TextUtils.isEmpty(ch.channelID) && addedIds.add(ch.channelID)) {
                list.add(ch);
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
        ArrayList<WKChannel> pre = getIntent().getParcelableArrayListExtra(EXTRA_PRESELECTED_CHANNELS);
        preselectedChannels = pre != null ? pre : new ArrayList<>();
    }

    @Override
    protected void initView() {
        chooseChatAdapter = new ChooseChatAdapter(new ArrayList<>());
        initAdapter(wkVBinding.recyclerView, chooseChatAdapter);

        wkVBinding.createTv.setOnClickListener(v -> openNewChatDirectory());

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

    /**
     * "新建会话" 紫字按钮: 跳 ForwardDirectoryActivity (3 tab 群聊/联系人/Bot),
     * 选中频道回传后合并到 extraSelectedChannels,与本页 isCheck=true 项一起在确定时返回。
     */
    private void openNewChatDirectory() {
        Intent intent = new Intent(this, ForwardDirectoryActivity.class);
        // 透传 singleSelect: 主页约束是单选时, 新建会话路径也只能选一个,否则老调用点
        // (如 RTC 拉人 / 单聊视频转发) 拿到列表协议会越界 — review 标记的契约漏。
        intent.putExtra("singleSelect", singleSelect);
        startActivityForResult(intent, REQ_FORWARD_DIRECTORY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FORWARD_DIRECTORY && resultCode == RESULT_OK && data != null) {
            ArrayList<WKChannel> picked = data.getParcelableArrayListExtra("list");
            if (picked == null || picked.isEmpty()) return;
            for (WKChannel ch : picked) {
                if (ch == null || TextUtils.isEmpty(ch.channelID)) continue;
                String key = ch.channelID + "|" + ch.channelType;
                extraSelectedChannels.put(key, ch);
            }
            updateRightBtn();
        }
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
        count += extraSelectedChannels.size();
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
        List<ChooseChatEntity> source = currentTab == 0 ? groupList : recentList;
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
        recentList = new ArrayList<>();

        for (int i = 0, size = list.size(); i < size; i++) {
            WKUIConversationMsg conv = list.get(i);
            // 当前 space 过滤,与消息列表/MyGroupsList 同口径,避免最近 tab 出现别 space 的群/子区
            if (SpaceFilter.shouldSkipChannelForSpace(conv.channelID, conv.channelType)) continue;
            ChooseChatEntity chooseChatEntity = new ChooseChatEntity(conv);
            if (conv.getWkChannel() != null) {
                WKChannelMember mChannelMember = WKIM.getInstance().getChannelMembersManager().getMember(conv.getWkChannel().channelID, conv.getWkChannel().channelType, WKConfig.getInstance().getUid());
                if (conv.getWkChannel().forbidden == 1) {
                    if (mChannelMember != null) {
                        chooseChatEntity.isForbidden = mChannelMember.role == WKChannelMemberRole.normal;
                    }
                } else {
                    if (mChannelMember != null)
                        chooseChatEntity.isForbidden = mChannelMember.forbiddenExpirationTime > 0;
                    else chooseChatEntity.isForbidden = false;
                }
                chooseChatEntity.isBan = conv.getWkChannel().status == WKChannelStatus.statusDisabled;
            }
            allList.add(chooseChatEntity);

            // 关注 tab 仍然只装 GROUP, 走 categoryList 分组 + 子区按需展开
            if (conv.channelType == WKChannelType.GROUP) {
                groupList.add(chooseChatEntity);
            }
            // 最近 tab 装所有会话(DM + 群 + 子区), 不再过滤,对齐 iOS recent tab 语义
            recentList.add(chooseChatEntity);
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
            applyPreselectionIfAny();
            filterAndDisplay();
            return;
        }

        final int[] pending = {groupList.size()};
        for (ChooseChatEntity groupEntity : groupList) {
            if (groupEntity.uiConveursationMsg == null) {
                pending[0]--;
                if (pending[0] <= 0) {
                    threadsLoaded = true;
                    applyPreselectionIfAny();
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
                    applyPreselectionIfAny();
                    filterAndDisplay();
                }
            });
        }
    }

    /**
     * 把 {@link #preselectedChannels} 落到 entity.isCheck / extraSelectedChannels / expandedThreadGroups 三处。
     * 触发时机: loadAllThreads 收尾 (threadsLoaded=true) 之后, filterAndDisplay 之前。
     * 仅生效一次, 避免 search / tab 切换 / 子区 toggle 重复 reload 时把用户取消的勾再加回去。
     *
     * 1:1 对齐 iOS WKForwardSelectVC.viewDidLoad preselect 逻辑 (commit 333f247): 子区 key 用纯 channelId,
     * 父群 groupNo 进 expandedThreadGroups 让用户能看到已勾选的子区。
     */
    private boolean preselectionApplied = false;

    private void applyPreselectionIfAny() {
        if (preselectionApplied) return;
        preselectionApplied = true;
        if (preselectedChannels == null || preselectedChannels.isEmpty()) return;

        // 索引 allList 主入口数据 (DM + 群 + 子区会话项)
        HashMap<String, ChooseChatEntity> mainIndex = new HashMap<>();
        for (ChooseChatEntity e : allList) {
            if (e.uiConveursationMsg != null && e.uiConveursationMsg.getWkChannel() != null) {
                mainIndex.put(e.uiConveursationMsg.getWkChannel().channelID, e);
            }
        }
        // 索引 threadEntityCache (子区条目)
        HashMap<String, ChooseChatEntity> threadIndex = new HashMap<>();
        for (List<ChooseChatEntity> items : threadEntityCache.values()) {
            for (ChooseChatEntity te : items) {
                if (!TextUtils.isEmpty(te.threadChannelId)) {
                    threadIndex.put(te.threadChannelId, te);
                }
            }
        }

        for (WKChannel ch : preselectedChannels) {
            if (ch == null || TextUtils.isEmpty(ch.channelID)) continue;
            boolean matched = false;

            if (ch.channelType == WKChannelType.COMMUNITY_TOPIC) {
                // 子区可能出现在两处: 关注 tab 的 threadEntityCache (作为父群下的 thread item),
                // 或最近 tab 的 allList/recentList (作为顶层会话 entity)。两处都打勾, 不漏。
                ChooseChatEntity te = threadIndex.get(ch.channelID);
                if (te != null) {
                    te.isCheck = true;
                    matched = true;
                }
                ChooseChatEntity me = mainIndex.get(ch.channelID);
                if (me != null) {
                    me.isCheck = true;
                    matched = true;
                }
                // 父群 auto-expand: channelId 形如 "groupNo____shortId"
                String[] parts = ch.channelID.split("____", 2);
                if (parts.length == 2 && !TextUtils.isEmpty(parts[0])) {
                    expandedThreadGroups.add(parts[0]);
                }
            } else {
                ChooseChatEntity e = mainIndex.get(ch.channelID);
                if (e != null) {
                    e.isCheck = true;
                    matched = true;
                }
            }

            // 主页 / 子区缓存都没找到 → 走 extraSelectedChannels 兜底, 确认时仍能回传。
            if (!matched) {
                String key = ch.channelID + "|" + ch.channelType;
                extraSelectedChannels.put(key, ch);
            }
        }
        updateRightBtn();
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
            // 最近 tab: 完整列表(DM + 群 + 子区)按置顶 + 时间排序
            List<ChooseChatEntity> recent = new ArrayList<>(recentList);
            sortByTopAndTime(recent);
            chooseChatAdapter.setList(recent);
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
