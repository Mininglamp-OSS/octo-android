package com.chat.uikit.search;

import android.content.Intent;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.ui.Theme;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.chat.ChatActivity;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.chat.uikit.databinding.ActSearchAllLayoutBinding;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelSearchResult;
import com.xinbida.wukongim.entity.WKMessageSearchResult;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 2020-05-04 16:35
 * 搜索所有内容
 */
public class SearchAllActivity extends WKBaseActivity<ActSearchAllLayoutBinding> {
    private SearchChannelAdapter userAdapter;
    private SearchChannelAdapter groupAdapter;
    private SearchMsgAdapter msgAdapter;

    @Override
    protected ActSearchAllLayoutBinding getViewBinding() {
        return ActSearchAllLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {

    }

    @Override
    protected void initPresenter() {
        Theme.setColorFilter(this, wkVBinding.searchIv, R.color.popupTextColor);
        ViewCompat.setTransitionName(wkVBinding.searchIv, "searchView");
    }

    @Override
    protected void initView() {
        wkVBinding.searchKeyTv.setTextColor(Theme.colorAccount);
        Theme.setPressedBackground(wkVBinding.cancelTv);
        userAdapter = new SearchChannelAdapter();
        groupAdapter = new SearchChannelAdapter();
        msgAdapter = new SearchMsgAdapter();
        initAdapter(wkVBinding.userRecyclerView, userAdapter);
        initAdapter(wkVBinding.groupRecyclerView, groupAdapter);
        initAdapter(wkVBinding.msgRecyclerView, msgAdapter);
        wkVBinding.userRecyclerView.setNestedScrollingEnabled(false);
        wkVBinding.groupRecyclerView.setNestedScrollingEnabled(false);
        wkVBinding.msgRecyclerView.setNestedScrollingEnabled(false);
        SoftKeyboardUtils.getInstance().showSoftKeyBoard(SearchAllActivity.this, wkVBinding.searchEt);
    }

    @Override
    protected void initListener() {
        wkVBinding.searchEt.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        wkVBinding.searchEt.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                SoftKeyboardUtils.getInstance().hideSoftKeyboard(SearchAllActivity.this);
                return true;
            }

            return false;
        });
        msgAdapter.setOnItemClickListener((adapter, view1, position) -> {
            WKMessageSearchResult result = (WKMessageSearchResult) adapter.getItem(position);
            if (result != null) {
                if (result.messageCount > 1) {
                    Intent intent = new Intent(this, SearchMsgResultActivity.class);
                    intent.putExtra("result", result);
                    intent.putExtra("searchKey", Objects.requireNonNull(wkVBinding.searchEt.getText()).toString());
                    startActivity(intent);
                } else {
                    List<WKMsg> msgList = WKIM.getInstance().getMsgManager().searchWithChannel(Objects.requireNonNull(wkVBinding.searchEt.getText()).toString(),result.wkChannel.channelID, result.wkChannel.channelType);
                    if (WKReader.isNotEmpty(msgList)) {
                        WKIMUtils.getInstance().startChatActivity(new ChatViewMenu(this, msgList.get(0).channelID, msgList.get(0).channelType, msgList.get(0).orderSeq, false));
                    }

                }
                SoftKeyboardUtils.getInstance().hideInput(this, wkVBinding.searchEt);
            }
        });
        userAdapter.setOnItemClickListener((adapter, view1, position) -> SingleClickUtil.determineTriggerSingleClick(view1, view2 -> {
            WKChannelSearchResult result = (WKChannelSearchResult) adapter.getItem(position);
            if (result != null) {
                Intent intent = new Intent(SearchAllActivity.this, ChatActivity.class);
                intent.putExtra("channelId", result.wkChannel.channelID);
                intent.putExtra("channelType", result.wkChannel.channelType);
                startActivity(intent);
                SoftKeyboardUtils.getInstance().hideInput(this, wkVBinding.searchEt);
            }
        }));
        groupAdapter.setOnItemClickListener((adapter, view1, position) -> SingleClickUtil.determineTriggerSingleClick(view1, view2 -> {
            WKChannelSearchResult result = (WKChannelSearchResult) adapter.getItem(position);
            if (result != null) {
                SoftKeyboardUtils.getInstance().hideInput(this, wkVBinding.searchEt);
                WKIMUtils.getInstance().startChatActivity(new ChatViewMenu(this, result.wkChannel.channelID, result.wkChannel.channelType, 0, false));
            }
        }));
        wkVBinding.searchEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                String key = editable.toString();
                if (TextUtils.isEmpty(key)) {
                    wkVBinding.resultView.setVisibility(View.GONE);
                } else {
                    searchChannel(key);
                    wkVBinding.resultView.setVisibility(View.VISIBLE);
                }
            }
        });
        wkVBinding.cancelTv.setOnClickListener(v -> {
            SoftKeyboardUtils.getInstance().hideSoftKeyboard(this);
            onBackPressed();
        });
        SingleClickUtil.onSingleClick(wkVBinding.findUserLayout, v -> {
            SoftKeyboardUtils.getInstance().hideSoftKeyboard(this);
            String searchKey = Objects.requireNonNull(wkVBinding.searchEt.getText()).toString();
            Intent intent = new Intent(this, SearchUserActivity.class);
            intent.putExtra("searchKey", searchKey);
            startActivity(intent);
        });
    }

    private void searchChannel(String key) {
        List<WKChannelSearchResult> groupList = WKIM.getInstance().getChannelManager().search(key);
        List<WKChannel> tempList = WKIM.getInstance().getChannelManager().searchWithChannelTypeAndFollow(key, WKChannelType.PERSONAL, 1);
        List<WKChannelSearchResult> userList = new ArrayList<>();
        if (WKReader.isNotEmpty(tempList)) {
            for (int i = 0, size = tempList.size(); i < size; i++) {
                WKChannelSearchResult result = new WKChannelSearchResult();
                result.wkChannel = tempList.get(i);
                userList.add(result);
            }
        }
        List<WKMessageSearchResult> msgList = WKIM.getInstance().getMsgManager().search(key);
//        List<WKChannelSearchResult> groupList = new ArrayList<>();
//        if (list != null && list.size() > 0) {
//            for (int i = 0, size = list.size(); i < size; i++) {
//                if (list.get(i).wkChannel.channel_type == WKChannelType.PERSONAL) {
//                    userList.add(list.get(i));
//                } else if (list.get(i).wkChannel.channel_type == WKChannelType.GROUP) {
//                    groupList.add(list.get(i));
//                }
//            }
//        }
        userAdapter.setSearchKey(key);
        groupAdapter.setSearchKey(key);
        msgAdapter.setSearchKey(key);
        if (WKReader.isNotEmpty(userList)) {
            wkVBinding.userLayout.setVisibility(View.VISIBLE);
        } else {
            wkVBinding.userLayout.setVisibility(View.GONE);
        }
        userAdapter.setList(userList);
        if (WKReader.isNotEmpty(groupList)) {
            wkVBinding.groupLayout.setVisibility(View.VISIBLE);
        } else {
            wkVBinding.groupLayout.setVisibility(View.GONE);
        }
        groupAdapter.setList(groupList);
        if (WKReader.isNotEmpty(msgList)) {
            wkVBinding.msgLayout.setVisibility(View.VISIBLE);
        } else {
            wkVBinding.msgLayout.setVisibility(View.GONE);
        }
        msgAdapter.setList(msgList);
        wkVBinding.searchKeyTv.setText(key);
    }

    @Override
    public void finish() {
        super.finish();
        SoftKeyboardUtils.getInstance().hideSoftKeyboard(this);
    }
}
