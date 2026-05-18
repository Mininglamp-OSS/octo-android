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

import android.view.View;

import com.chat.base.msg.IConversationContext;

/**
 * 1/1/21 2:43 PM
 * 聊天工具栏
 */
public class ChatToolBarMenu {
    public String sid;
    public int toolBarImageRecourseID;
    public int toolBarImageSelectedRecourseID;
    public boolean isSelected;
    public View bottomView;
    public boolean isDisable;

    public IChatToolBarListener iChatToolBarListener;

    public ChatToolBarMenu(String sid, int toolBarImageRecourseID, int toolBarImageSelectedRecourseID, View bottomView, IChatToolBarListener iChatToolBarListener) {
        this.sid = sid;
        this.toolBarImageRecourseID = toolBarImageRecourseID;
        this.toolBarImageSelectedRecourseID = toolBarImageSelectedRecourseID;
        this.bottomView = bottomView;
        this.iChatToolBarListener = iChatToolBarListener;
    }

    public interface IChatToolBarListener {
        void onChecked(boolean isSelected, IConversationContext iConversationContext);
    }

}
