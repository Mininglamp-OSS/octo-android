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

package com.chat.base.endpoint.entity;

import androidx.activity.ComponentActivity;

import com.xinbida.wukongim.msgmodel.WKMessageContent;

import java.util.List;

/**
 * 3/22/21 5:52 PM
 * 显示聊天信息
 */
public class ChatViewMenu {
    public String channelID;
    public byte channelType;
    // tipMsgOrderSeq >0 需要强提醒某条消息 场景：搜索进入聊天等
    // tipMsgOrderSeq =0 正常会话列表进入聊天
    public long tipMsgOrderSeq;
    public ComponentActivity activity;
    public boolean isNewTask;
    public List<WKMessageContent> forwardMsgList;

    public ChatViewMenu(ComponentActivity activity, String channelID, byte channelType, long tipMsgOrderSeq, boolean isNewTask) {
        this.channelID = channelID;
        this.channelType = channelType;
        this.tipMsgOrderSeq = tipMsgOrderSeq;
        this.activity = activity;
        this.isNewTask = isNewTask;
        this.forwardMsgList = null;
    }

    public ChatViewMenu(ComponentActivity activity, String channelID, byte channelType, long tipMsgOrderSeq, boolean isNewTask, List<WKMessageContent> forwardMsgList) {
        this.channelID = channelID;
        this.channelType = channelType;
        this.tipMsgOrderSeq = tipMsgOrderSeq;
        this.activity = activity;
        this.isNewTask = isNewTask;
        this.forwardMsgList = forwardMsgList;
    }
}
