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

package com.chat.uikit.group.webhook;

/**
 * 与群入站 Webhook 模块相关的常量。
 */
public final class WebhookConstants {

    private WebhookConstants() {
    }

    /**
     * channelInfo.extra 中存放 webhook 数量的 key，用于群信息页副标题显示。
     * 与 iOS 一致，便于本地缓存命中后立即展示「已配置 N 个」。
     */
    public static final String EXTRA_COUNT_KEY = "incoming_webhook_count";
}
