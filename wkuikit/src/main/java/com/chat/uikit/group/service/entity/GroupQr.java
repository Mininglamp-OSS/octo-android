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

package com.chat.uikit.group.service.entity;

/**
 * 2020-07-20 21:54
 * 群二维码信息
 */
public class GroupQr {
    public int day;
    public String qrcode;
    public String expire;
    /**
     * 跨 Space 用户扫码入群的邀请链接。
     * 后端 ChannelQrcodeResp.invite_url（PR #1201）
     */
    public String invite_url;
}
