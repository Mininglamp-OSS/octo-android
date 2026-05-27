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

import android.text.TextUtils;
import android.util.Log;
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
import com.tencent.bugly.crashreport.CrashReport;
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
        // 支持两种打开方式：
        // 1. client_msg_no — 从数据库查消息（一级合并转发）
        // 2. forward_content_json — 直接传入内容JSON（嵌套合并转发）
        String contentJson = getIntent().getStringExtra("forward_content_json");
        if (!TextUtils.isEmpty(contentJson)) {
            try {
                org.json.JSONObject json = new org.json.JSONObject(contentJson);
                WKMultiForwardContent = new WKMultiForwardContent();
                WKMultiForwardContent.decodeMsg(json);
            } catch (Exception e) {
                Log.e("MultiForwardDetail", "解析嵌套合并转发内容失败", e);
            }
        } else {
            clientMsgNo = getIntent().getStringExtra("client_msg_no");
            WKMsg msg = WKIM.getInstance().getMsgManager().getWithClientMsgNO(clientMsgNo);
            if (msg != null) {
                WKMultiForwardContent = (WKMultiForwardContent) msg.baseContentMsgModel;
            }
        }
        if (WKMultiForwardContent == null || WKMultiForwardContent.msgList == null) {
            String source = !TextUtils.isEmpty(contentJson) ? "json" : "db";
            String msgNo = clientMsgNo != null ? clientMsgNo : "null";
            boolean contentNull = WKMultiForwardContent == null;
            boolean listNull = !contentNull && WKMultiForwardContent.msgList == null;
            String detail = "source=" + source
                    + " clientMsgNo=" + msgNo
                    + " contentNull=" + contentNull
                    + " msgListNull=" + listNull
                    + " rawJson=" + (contentJson != null ? contentJson.substring(0, Math.min(contentJson.length(), 500)) : "null");
            CrashReport.postCatchedException(new IllegalStateException("MultiForwardDetail 数据异常: " + detail));
            showToast(getString(R.string.toast_invalid_data));
            finish();
            return;
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
//            if (temp.msg.type != 0)
            list.add(temp);
        }
        ChatMultiForwardEntity view = new ChatMultiForwardEntity();
        view.itemType = 2;
        list.add(view);
        ChatMultiForwardDetailAdapter adapter = new ChatMultiForwardDetailAdapter(showDetailTime, list, WKMultiForwardContent.userList);
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
