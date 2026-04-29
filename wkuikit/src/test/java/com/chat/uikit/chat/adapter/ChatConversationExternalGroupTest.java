package com.chat.uikit.chat.adapter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.chat.base.entity.WKChannelCustomerExtras;
import com.xinbida.wukongim.entity.WKChannel;

import org.junit.Test;

import java.util.HashMap;

/**
 * YUJ-90 · EP5 会话列表外部群 Tag —— 字段透传兜底测试。
 *
 * 目的：防止 web YUJ-53 同类 P0（model 层未透传 is_external_group
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
