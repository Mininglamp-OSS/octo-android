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
 * 4/30/21 4:51 PM
 */
public class RTCMenu {

    public int callType;//0语音1视频
    public IConversationContext iConversationContext;
    public RTCMenu(IConversationContext iConversationContext, int callType) {
       this.iConversationContext = iConversationContext;
        this.callType = callType;
    }
}
