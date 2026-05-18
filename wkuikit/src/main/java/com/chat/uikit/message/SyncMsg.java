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

package com.chat.uikit.message;

import java.util.Map;

/**
 * 2020-07-21 09:41
 * 同步消息对象
 */
public class SyncMsg {
    public String message_id;
    public int message_seq;
    public String client_msg_no;
    public String from_uid;
    public String channel_id;
    public byte channel_type;
    public int voice_status;
    public long timestamp;
    public int is_delete;
    public int unread_count;
    public int readed_count;
    public long extra_version;
    public Map payload;
    public SyncMsgHeader header;
    // 外部群来源字段（ / web PR #981 + #982 对齐）。服务端在外部群场景下随
    // 每条消息下发发送者的 home/source Space，以便客户端视角相对渲染
    // 「@SpaceName」后缀。wire 约定：无 from_ 前缀（与 web PR #981 统一）。
    // 任何字段缺失时走降级链（见 ExternalSourceResolver）。
    public Integer is_external;
    public String source_space_id;
    public String source_space_name;
    public String home_space_id;
    public String home_space_name;
}
