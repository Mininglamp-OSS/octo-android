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

package com.chat.base.entity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 外部群 Phase 1 —  EP1 频道扩展 key 回归保护。
 *
 * 锁死后端契约字段名：web #965 (allow_external) + 原有 is_external_group +
 * 群归属 space_id。这些常量值一旦被改动，所有依赖这些 key 的 UI 代码会静默失效。
 */
public class WKChannelCustomerExtrasTest {

    @Test
    public void channelExtrasKeys_matchBackendContract() {
        assertEquals("is_external_group", WKChannelCustomerExtras.isExternalGroup);
        assertEquals("allow_external", WKChannelCustomerExtras.allowExternal);
        assertEquals("space_id", WKChannelCustomerExtras.spaceId);
    }
}
