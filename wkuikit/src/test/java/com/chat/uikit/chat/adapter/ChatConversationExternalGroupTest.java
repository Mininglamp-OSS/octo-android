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

package com.chat.uikit.chat.adapter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.chat.base.entity.WKChannelCustomerExtras;
import com.xinbida.wukongim.entity.WKChannel;

import org.junit.Test;

import java.util.HashMap;

/**
 *  · EP5 会话列表外部群 Tag —— 字段透传兜底测试。
 *
 * 目的：防止 web  同类 P0（model 层未透传 is_external_group
 * 导致 UI 静默失败 / count=0）在 Android 复现。
 *
 * 仅覆盖 {@link ChatConversationAdapter#isExternalGroup(WKChannel)} 纯判定逻辑。
 * UI 显隐由 {@code applyExternalGroupTag(...)} 在 channelType==GROUP 门控。
 */
public class ChatConversationExternalGroupTest {

    private WKChannel channelWith(Object value) {
        WKChannel c = new WKChannel();
        c.remoteExtraMap = new HashMap<>();
        if (value != null) {
            c.remoteExtraMap.put(WKChannelCustomerExtras.isExternalGroup, value);
        }
        return c;
    }

    @Test
    public void externalGroup_intOne_showsTag() {
        assertTrue(ChatConversationAdapter.isExternalGroup(channelWith(1)));
    }

    @Test
    public void externalGroup_longOne_showsTag() {
        // 后端 JSON 数值默认可能被反序列化为 Long
        assertTrue(ChatConversationAdapter.isExternalGroup(channelWith(1L)));
    }

    @Test
    public void externalGroup_booleanTrue_showsTag() {
        assertTrue(ChatConversationAdapter.isExternalGroup(channelWith(Boolean.TRUE)));
    }

    @Test
    public void externalGroup_intZero_hidesTag() {
        assertFalse(ChatConversationAdapter.isExternalGroup(channelWith(0)));
    }

    @Test
    public void externalGroup_booleanFalse_hidesTag() {
        assertFalse(ChatConversationAdapter.isExternalGroup(channelWith(Boolean.FALSE)));
    }

    @Test
    public void externalGroup_missingKey_hidesTag() {
        WKChannel c = new WKChannel();
        c.remoteExtraMap = new HashMap<>();
        assertFalse(ChatConversationAdapter.isExternalGroup(c));
    }

    @Test
    public void externalGroup_nullExtraMap_hidesTag() {
        WKChannel c = new WKChannel();
        c.remoteExtraMap = null;
        assertFalse(ChatConversationAdapter.isExternalGroup(c));
    }

    @Test
    public void externalGroup_nullChannel_hidesTag() {
        assertFalse(ChatConversationAdapter.isExternalGroup(null));
    }

    @Test
    public void externalGroup_stringValue_hidesTag() {
        // 非 Number / Boolean 的 extra 值应安全降级为 false
        assertFalse(ChatConversationAdapter.isExternalGroup(channelWith("1")));
    }
}
