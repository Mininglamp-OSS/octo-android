package com.xinbida.wukongim.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.xinbida.wukongim.interfaces.ISyncConversationChatBack;

import org.junit.Test;

import java.lang.reflect.Method;

/**
 * YUJ-326 · host-side 契约测试：{@link ConversationManager} 的 per-Space 公共 API。
 *
 * <p>host-side 无法启动真实 SDK（需要 Application / WKIM.init），所以只验证：
 * <ul>
 *   <li>{@code clearAllForSpace(String)} 存在、签名正确、null 参数安全短路。</li>
 *   <li>{@code setSyncConversationListener(String, ISyncConversationChatBack)} 单 spaceId 重载存在。</li>
 *   <li>{@code setSyncConversationListener(String, long, String, ISyncConversationChatBack)} 四参数显式 cursor 重载存在。</li>
 * </ul>
 */
public class ConversationManagerSpaceApiTest {

    @Test
    public void clearAllForSpaceMethodExists() throws NoSuchMethodException {
        Method m = ConversationManager.class.getMethod("clearAllForSpace", String.class);
        assertEquals(boolean.class, m.getReturnType());
    }

    @Test
    public void clearAllForSpaceNullIsSafeNoop() {
        boolean result = ConversationManager.getInstance().clearAllForSpace(null);
        assertFalse(result);
    }

    @Test
    public void setSyncConversationListenerSpaceOverloadExists() throws NoSuchMethodException {
        Method m = ConversationManager.class.getMethod(
                "setSyncConversationListener", String.class, ISyncConversationChatBack.class);
        assertNotNull(m);
    }

    @Test
    public void setSyncConversationListenerExplicitCursorOverloadExists() throws NoSuchMethodException {
        Method m = ConversationManager.class.getMethod(
                "setSyncConversationListener",
                String.class, long.class, String.class, ISyncConversationChatBack.class);
        assertNotNull(m);
    }
}
