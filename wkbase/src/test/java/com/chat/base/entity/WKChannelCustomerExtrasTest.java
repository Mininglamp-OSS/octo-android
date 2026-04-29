package com.chat.base.entity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 外部群 Phase 1 — YUJ-86 EP1 频道扩展 key 回归保护。
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
