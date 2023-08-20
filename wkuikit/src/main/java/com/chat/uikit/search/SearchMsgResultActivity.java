package com.chat.uikit.search;

import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.chat.uikit.databinding.ActCommonListLayoutBinding;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKMessageSearchResult;
import com.xinbida.wukongim.entity.WKMsg;

import java.util.ArrayList;
import java.util.List;

/**
 * 2020-08-30 18:41
 * 搜索消息结果
 */
public class SearchMsgResultActivity extends WKBaseActivity<ActCommonListLayoutBinding> {

    WKMessageSearchResult result;
    SearchMsgResultAdapter adapter;

    @Override
    protected ActCommonListLayoutBinding getViewBinding() {
        return ActCommonListLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(result.wkChannel.channelName);
    }

    @Override
    protected void initPresenter() {

    }

    @Override
    protected void initView() {
        result = getIntent().getParcelableExtra("result");
        String searchKey = getIntent().getStringExtra("searchKey");
        adapter = new SearchMsgResultAdapter(searchKey, new ArrayList<>());
        initAdapter(wkVBinding.recyclerView, adapter);
        List<WKMsg> msgList = WKIM.getInstance().getMsgManager().searchWithChannel(searchKey,result.wkChannel.channelID, result.wkChannel.channelType);
        adapter.setList(msgList);
    }

    @Override
    protected void initListener() {
        adapter.setOnItemClickListener((adapter1, view1, position) -> SingleClickUtil.determineTriggerSingleClick(view1, view2 -> {
            WKMsg msg = (WKMsg) adapter1.getItem(position);
            if (msg != null) {
                WKIMUtils.getInstance().startChatActivity(new ChatViewMenu(this, msg.channelID, msg.channelType, msg.orderSeq, false));
            }
        }));
    }
}
