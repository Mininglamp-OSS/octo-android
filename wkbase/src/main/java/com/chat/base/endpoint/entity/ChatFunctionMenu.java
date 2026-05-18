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

import com.chat.base.msg.IConversationContext;

/**
 * 2020-09-02 13:21
 * 聊天面板模块
 */
public class ChatFunctionMenu extends BaseEndpoint {
    public IChatFunctionCLick iChatFunctionCLick;
    public String sid;

    public ChatFunctionMenu(String sid, int imgResourceID, String text, IChatFunctionCLick iChatFunctionCLick) {
        this.sid = sid;
        this.imgResourceID = imgResourceID;
        this.text = text;
        this.iChatFunctionCLick = iChatFunctionCLick;
    }

    public interface IChatFunctionCLick {
        void onClick(IConversationContext conversationContext);
    }
}
