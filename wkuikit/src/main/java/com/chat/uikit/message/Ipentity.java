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

/**
 * 2020-07-20 23:24
 *
 * Response payload of /users/{uid}/im. WuKongIM 同时返回 tcp / ws / wss 三种入口地址，
 * 客户端按优先级 wss_addr → ws_addr → tcp_addr 选用（YUJ-2226）。
 */
public class Ipentity {
    public String tcp_addr;
    public String ws_addr;
    public String wss_addr;
}
