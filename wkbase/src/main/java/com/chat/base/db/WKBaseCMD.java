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

package com.chat.base.db;

/**
 * 2020-11-23 11:50
 * cmd
 */
public class WKBaseCMD {
    public String message_id;
    public long message_seq;
    public String client_msg_no;
    public long timestamp;
    public String cmd;
    public String sign;
    public String param;
    public int is_deleted;
    public String created_at;
}
