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

import com.chat.base.msg.ChatAdapter;
import com.xinbida.wukongim.entity.WKMsg;

/**
 * 4/16/21 5:08 PM
 * 消息回应
 */
public class MsgReactionMenu {
    public String emoji;
    public ChatAdapter chatAdapter;
    public int[] location;
    public WKMsg wkMsg;

    public MsgReactionMenu(WKMsg wkMsg, String emoji, ChatAdapter chatAdapter, int[] location) {
        this.emoji = emoji;
        this.wkMsg = wkMsg;
        this.chatAdapter = chatAdapter;
        this.location = location;
    }
}
