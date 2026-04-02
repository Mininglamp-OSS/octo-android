package com.chat.uikit.contacts;

import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.utils.HanziToPinyin;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.base.views.sidebar.listener.OnQuickSideBarTouchListener;
import com.chat.uikit.R;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.chat.uikit.databinding.ActContactsListLayoutBinding;
import com.chat.uikit.utils.CharacterParser;
import com.chat.uikit.utils.PyingUtils;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKConversationMsg;

import java.util.ArrayList;
import java.util.List;

/**
 * 我的群组列表（从本地会话获取所有群聊）
 */
public class MyGroupsListActivity extends WKBaseActivity<ActContactsListLayoutBinding> implements OnQuickSideBarTouchListener {

    private GroupListAdapter groupAdapter;
    private TextView countTv;

    @Override
    protected ActContactsListLayoutBinding getViewBinding() {
        return ActContactsListLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.contacts_section_groups);
    }

    @Override
    protected void initView() {
        groupAdapter = new GroupListAdapter();
        groupAdapter.addFooterView(createFooterView());
        initAdapter(wkVBinding.recyclerView, groupAdapter);
        int stickyHeight = com.chat.base.utils.AndroidUtilities.dp(30);
        wkVBinding.recyclerView.addItemDecoration(StickyHeaderDecoration.forGenericAdapter(
                stickyHeight, 0,
                i -> {
                    List<GroupItem> data = groupAdapter.getData();
                    return (i >= 0 && i < data.size() && data.get(i).pying != null) ? data.get(i).pying : "#";
                },
                () -> groupAdapter.getData().size()
        ));
        int themeColor = Color.parseColor("#6366f1");
        wkVBinding.quickSideBarView.setLetters(CharacterParser.getInstance().getList());
        wkVBinding.quickSideBarView.setTextChooseColor(themeColor);
        wkVBinding.quickSideBarTipsView.setBackgroundColor(themeColor);
    }

    @Override
    protected void initListener() {
        wkVBinding.quickSideBarView.setOnQuickSideBarTouchListener(this);
        wkVBinding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) return;
                int first = lm.findFirstVisibleItemPosition();
                if (first >= 0 && first < groupAdapter.getData().size()) {
                    String pying = groupAdapter.getData().get(first).pying;
                    if (pying != null && !pying.isEmpty()) {
                        wkVBinding.quickSideBarView.setChooseLetter(pying.substring(0, 1).toUpperCase());
                    }
                }
            }
        });
        groupAdapter.addChildClickViewIds(R.id.contentLayout);
        groupAdapter.setOnItemChildClickListener((adapter, view, position) ->
                SingleClickUtil.determineTriggerSingleClick(view, v -> {
                    GroupItem item = groupAdapter.getItem(position);
                    if (item != null) {
                        WKIMUtils.getInstance().startChatActivity(
                                new ChatViewMenu(this, item.channel.channelID, WKChannelType.GROUP, 0, true));
                    }
                }));
    }

    private boolean isDataLoaded = false;

    @Override
    protected void onResume() {
        super.onResume();
        if (!isDataLoaded) {
            loadData();
        }
    }

    private void loadData() {
        List<WKConversationMsg> conversations = WKIM.getInstance().getConversationManager()
                .getWithChannelType(WKChannelType.GROUP);
        List<GroupItem> items = new ArrayList<>();
        if (conversations != null) {
            for (WKConversationMsg conv : conversations) {
                if (TextUtils.isEmpty(conv.channelID)) continue;
                WKChannel channel = WKIM.getInstance().getChannelManager()
                        .getChannel(conv.channelID, WKChannelType.GROUP);
                if (channel == null) {
                    channel = new WKChannel(conv.channelID, WKChannelType.GROUP);
                    channel.channelName = conv.channelID;
                }
                String showName = TextUtils.isEmpty(channel.channelRemark) ? channel.channelName : channel.channelRemark;
                String pying;
                if (!TextUtils.isEmpty(showName)) {
                    pying = PyingUtils.getInstance().isStartNum(showName)
                            ? "#" : HanziToPinyin.getInstance().getPY(showName);
                } else {
                    pying = "#";
                }
                items.add(new GroupItem(channel, pying));
            }
        }
        // Sort by pinyin, letters first, # at end
        items.sort((a, b) -> {
            if (a.pying == null) return 1;
            if (b.pying == null) return -1;
            boolean aIsLetter = !a.pying.isEmpty() && Character.isLetter(a.pying.charAt(0));
            boolean bIsLetter = !b.pying.isEmpty() && Character.isLetter(b.pying.charAt(0));
            if (aIsLetter && !bIsLetter) return -1;
            if (!aIsLetter && bIsLetter) return 1;
            return a.pying.compareToIgnoreCase(b.pying);
        });

        if (WKReader.isEmpty(items)) {
            wkVBinding.nodataTv.setVisibility(View.VISIBLE);
        }
        groupAdapter.setList(items);
        isDataLoaded = true;
        countTv.setText(String.format(getString(R.string.contacts_groups_count), items.size()));
    }

    private View createFooterView() {
        countTv = new TextView(this);
        countTv.setGravity(Gravity.CENTER);
        countTv.setTextSize(16);
        countTv.setTextColor(ContextCompat.getColor(this, R.color.colorDark));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        layout.setBackgroundColor(ContextCompat.getColor(this, R.color.homeColor));
        layout.addView(countTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) countTv.getLayoutParams();
        lp.topMargin = AndroidUtilities.dp(15);
        lp.bottomMargin = AndroidUtilities.dp(15);
        return layout;
    }

    @Override
    public void onLetterChanged(String letter, int position, float y) {
        wkVBinding.quickSideBarTipsView.setText(letter, position, y);
        List<GroupItem> list = groupAdapter.getData();
        if (WKReader.isNotEmpty(list)) {
            for (int i = 0, size = list.size(); i < size; i++) {
                if (list.get(i).pying != null && list.get(i).pying.toUpperCase().startsWith(letter.toUpperCase())) {
                    wkVBinding.recyclerView.stopScroll();
                    LinearLayoutManager lm = (LinearLayoutManager) wkVBinding.recyclerView.getLayoutManager();
                    if (lm != null) {
                        lm.scrollToPositionWithOffset(i, 0);
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void onLetterTouching(boolean touching) {
        wkVBinding.quickSideBarTipsView.setVisibility(touching ? View.VISIBLE : View.INVISIBLE);
    }

    static class GroupItem {
        WKChannel channel;
        String pying;

        GroupItem(WKChannel channel, String pying) {
            this.channel = channel;
            this.pying = pying;
        }
    }

    static class GroupListAdapter extends BaseQuickAdapter<GroupItem, BaseViewHolder> {

        GroupListAdapter() {
            super(R.layout.item_contacts_group);
        }

        @Override
        protected void convert(BaseViewHolder holder, GroupItem item) {
            String name = TextUtils.isEmpty(item.channel.channelRemark) ? item.channel.channelName : item.channel.channelRemark;
            holder.setText(R.id.groupNameTv, name);
            AvatarView avatarView = holder.getView(R.id.avatarView);
            avatarView.setSize(50f);
            avatarView.showAvatar(item.channel, true);
        }
    }
}
