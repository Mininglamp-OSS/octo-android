package com.chat.uikit.contacts;

import android.content.Intent;
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

import com.chat.base.base.WKBaseActivity;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.HanziToPinyin;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.base.views.sidebar.listener.OnQuickSideBarTouchListener;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActContactsListLayoutBinding;
import com.chat.uikit.message.MsgModel;
import com.chat.uikit.robot.entity.BotStoreEntity;
import com.chat.uikit.robot.service.WKRobotModel;
import com.chat.uikit.user.UserDetailActivity;
import com.chat.uikit.utils.CharacterParser;
import com.chat.uikit.utils.PyingUtils;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 广场 — 展示 Space 内所有可用 Bot（含未添加/审批中/已添加三种状态）
 */
public class BotStoreActivity extends WKBaseActivity<ActContactsListLayoutBinding> implements OnQuickSideBarTouchListener {

    private BotStoreAdapter adapter;
    private TextView countTv;

    @Override
    protected ActContactsListLayoutBinding getViewBinding() {
        return ActContactsListLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.contacts_section_bot_store);
    }

    @Override
    protected void initView() {
        adapter = new BotStoreAdapter();
        adapter.addFooterView(createFooterView());
        initAdapter(wkVBinding.recyclerView, adapter);
        wkVBinding.recyclerView.setItemAnimator(null);
        int stickyHeight = AndroidUtilities.dp(30);
        wkVBinding.recyclerView.addItemDecoration(StickyHeaderDecoration.forFriendAdapter(
                stickyHeight, 0,
                i -> {
                    List<BotStoreUIEntity> data = adapter.getData();
                    return (i >= 0 && i < data.size() && data.get(i).pying != null) ? data.get(i).pying : "#";
                },
                () -> adapter.getData().size()
        ));
        int themeColor = Color.parseColor("#7761F4");
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
                if (first >= 0 && first < adapter.getData().size()) {
                    String pying = adapter.getData().get(first).pying;
                    if (pying != null && !pying.isEmpty()) {
                        wkVBinding.quickSideBarView.setChooseLetter(pying.substring(0, 1).toUpperCase());
                    }
                }
            }
        });
        adapter.addChildClickViewIds(R.id.contentLayout);
        adapter.setOnItemChildClickListener((a, view, position) ->
                SingleClickUtil.determineTriggerSingleClick(view, v -> {
                    BotStoreUIEntity entity = adapter.getItem(position);
                    if (entity != null) {
                        Intent intent = new Intent(this, UserDetailActivity.class);
                        intent.putExtra("uid", entity.channel.channelID);
                        startActivity(intent);
                    }
                }));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        String spaceId = MsgModel.getInstance().getCurrentSpaceId();
        if (TextUtils.isEmpty(spaceId)) {
            wkVBinding.nodataTv.setVisibility(View.VISIBLE);
            return;
        }
        WKRobotModel.getInstance().getSpaceBots(spaceId, new WKRobotModel.ISpaceBotsListener() {
            @Override
            public void onResult(List<BotStoreEntity> result) {
                List<BotStoreUIEntity> list = new ArrayList<>();
                if (WKReader.isNotEmpty(result)) {
                    for (BotStoreEntity bot : result) {
                        WKChannel channel = new WKChannel(bot.uid, WKChannelType.PERSONAL);
                        channel.channelName = bot.name;
                        channel.robot = 1;
                        BotStoreUIEntity entity = new BotStoreUIEntity(channel, bot.status, bot.description);
                        String showName = bot.name;
                        if (!TextUtils.isEmpty(showName)) {
                            entity.pying = PyingUtils.getInstance().isStartNum(showName)
                                    ? "#" : HanziToPinyin.getInstance().getPY(showName);
                        } else {
                            entity.pying = "#";
                        }
                        list.add(entity);
                    }
                }
                PyingUtils.getInstance().sortBotStoreList(list);
                list = sortLettersFirst(list);
                wkVBinding.nodataTv.setVisibility(WKReader.isEmpty(list) ? View.VISIBLE : View.GONE);
                adapter.setList(list);
                countTv.setText(String.format(getString(R.string.contacts_bots_store_count), list.size()));
            }

            @Override
            public void onError(int code, String msg) {
                wkVBinding.nodataTv.setVisibility(View.VISIBLE);
            }
        });
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
        List<BotStoreUIEntity> list = adapter.getData();
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

    private List<BotStoreUIEntity> sortLettersFirst(List<BotStoreUIEntity> list) {
        List<BotStoreUIEntity> letterList = new ArrayList<>();
        List<BotStoreUIEntity> otherList = new ArrayList<>();
        for (BotStoreUIEntity item : list) {
            if (item.pying != null && PyingUtils.getInstance().isStartLetter(item.pying)) {
                letterList.add(item);
            } else {
                otherList.add(item);
            }
        }
        letterList.addAll(otherList);
        return letterList;
    }
}
