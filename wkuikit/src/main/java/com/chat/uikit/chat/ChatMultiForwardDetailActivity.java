package com.chat.uikit.chat;

import android.text.TextUtils;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.utils.WKTimeUtils;
import com.chat.uikit.R;
import com.chat.uikit.chat.adapter.ChatMultiForwardDetailAdapter;
import com.chat.uikit.chat.msgmodel.WKMultiForwardContent;
import com.chat.uikit.databinding.ActCommonListLayoutWhiteBinding;
import com.chat.uikit.enity.ChatMultiForwardEntity;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKCMDKeys;
import com.xinbida.wukongim.entity.WKMsg;

import java.util.ArrayList;
import java.util.List;

/**
 * 2020-09-22 11:57
 * 合并转发消息详情
 */
public class ChatMultiForwardDetailActivity extends WKBaseActivity<ActCommonListLayoutWhiteBinding> {

    WKMultiForwardContent WKMultiForwardContent;
    String clientMsgNo = "";

    @Override
    protected ActCommonListLayoutWhiteBinding getViewBinding() {
        return ActCommonListLayoutWhiteBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        String title;
        if (WKMultiForwardContent.channelType == 1) {
            if (WKMultiForwardContent.userList.size() > 1) {
                StringBuilder sBuilder = new StringBuilder();
                for (int i = 0; i < WKMultiForwardContent.userList.size(); i++) {
                    if (!TextUtils.isEmpty(sBuilder))
                        sBuilder.append("、");
                    sBuilder.append(WKMultiForwardContent.userList.get(i).channelName);
                }
                title = sBuilder.toString();
            } else title = WKMultiForwardContent.userList.get(0).channelName;
        } else {
            title = getString(R.string.group_chat);
        }
        titleTv.setText(String.format(getString(R.string.chat_title_records), title));
    }

    @Override
    protected void initPresenter() {
        clientMsgNo = getIntent().getStringExtra("client_msg_no");
        WKMsg msg = WKIM.getInstance().getMsgManager().getWithClientMsgNO(clientMsgNo);
        WKMultiForwardContent = (WKMultiForwardContent) msg.baseContentMsgModel;
        if (WKMultiForwardContent == null) {
            showToast("传入数据有误！");
            finish();
        }
        long minTime = 0;
        long maxTime = 0;
        for (int i = 0, size = WKMultiForwardContent.msgList.size(); i < size; i++) {
            if (WKMultiForwardContent.msgList.get(i).timestamp > maxTime || maxTime == 0)
                maxTime = WKMultiForwardContent.msgList.get(i).timestamp;
            if (WKMultiForwardContent.msgList.get(i).timestamp < minTime || minTime == 0)
                minTime = WKMultiForwardContent.msgList.get(i).timestamp;
        }
        String time;
        boolean showDetailTime;
        if (!WKTimeUtils.getInstance().isSameDayOfMillis(minTime * 1000, maxTime * 1000)) {
            showDetailTime = true;
            String tempTime1 = WKTimeUtils.getInstance().time2DataDay1(minTime * 1000);
            String tempTime2 = WKTimeUtils.getInstance().time2DataDay1(maxTime * 1000);
            time = String.format(getString(R.string.time_section), tempTime1, tempTime2);
        } else {
            showDetailTime = false;
            time = WKTimeUtils.getInstance().time2DataDay1(minTime * 1000);
        }
        List<ChatMultiForwardEntity> list = new ArrayList<>();
        ChatMultiForwardEntity entity = new ChatMultiForwardEntity();
        entity.itemType = 1;
        entity.title = time;
        list.add(entity);
        for (int i = 0, size = WKMultiForwardContent.msgList.size(); i < size; i++) {
            ChatMultiForwardEntity temp = new ChatMultiForwardEntity();
            temp.msg = WKMultiForwardContent.msgList.get(i);
            if (temp.msg.type != 0)
                list.add(temp);
        }
        ChatMultiForwardEntity view = new ChatMultiForwardEntity();
        view.itemType = 2;
        list.add(view);
        ChatMultiForwardDetailAdapter adapter = new ChatMultiForwardDetailAdapter(showDetailTime, list);
        initAdapter(wkVBinding.recyclerView, adapter);
    }

    @Override
    protected void initListener() {
        WKIM.getInstance().getCMDManager().addCmdListener("chat_multi_forward_detail", cmd -> {
            if (!TextUtils.isEmpty(cmd.cmdKey)) {
                if (cmd.cmdKey.equals(WKCMDKeys.wk_messageRevoke)) {
                    if (cmd.paramJsonObject != null && cmd.paramJsonObject.has("message_id")) {
                        String msgID = cmd.paramJsonObject.optString("message_id");
                        WKMsg msg = WKIM.getInstance().getMsgManager().getWithMessageID(msgID);
                        if (msg != null) {
                            if (msg.clientMsgNO.equals(clientMsgNo)) {
                                showToast(getString(R.string.msg_revoked));
                                finish();
                            }
                        }
                    }
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WKIM.getInstance().getMsgManager().removeRefreshMsgListener("chat_multi_forward_detail");
    }
}
