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

/**
 * 4/2/21 12:29 PM
 * 撤回消息
 */
public class WithdrawMsgMenu {
    public String message_id;
    public String channel_id;
    public String client_msg_no;
    public byte channel_type;

    public WithdrawMsgMenu(String message_id, String channel_id, String client_msg_no, byte channel_type) {
        this.message_id = message_id;
        this.channel_id = channel_id;
        this.client_msg_no = client_msg_no;
        this.channel_type = channel_type;
    }
}
