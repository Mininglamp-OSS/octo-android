package com.xinbida.wukongim.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.lang.reflect.Method;

/**
 * YUJ-326 · host-side 契约测试：{@link ConversationDbManager} 新增的 per-Space API
 * 存在且签名正确。
 *
 * <p>host-side {@code returnDefaultValues=true} + {@code WKIMApplication.getContext()} 未初始化
 * 让真正触发 SQLite 的调用走不通。本测试只验证方法签名 + null 防御行为（clearForSpace 显式
 * 做了 null 短路，不依赖 WKIMApplication）。完整 DB 行为测试放 instrumented test。
 */
public class ConversationDbManagerSpaceApiTest {

    @Test
    public void clearForSpaceMethodExists() throws NoSuchMethodException {
        Method m = ConversationDbManager.class.getMethod("clearForSpace", String.class);
        assertEquals(boolean.class, m.getReturnType());
    }

    @Test
    public void queryMaxVersionForSpaceMethodExists() throws NoSuchMethodException {
        Method m = ConversationDbManager.class.getMethod("queryMaxVersionForSpace", String.class);
        assertEquals(long.class, m.getReturnType());
    }

    @Test
    public void queryLastMsgSeqsForSpaceMethodExists() throws NoSuchMethodException {
        Method m = ConversationDbManager.class.getMethod("queryLastMsgSeqsForSpace", String.class);
        assertEquals(String.class, m.getReturnType());
    }

    @Test
    public void clearForSpaceNullIsSafeNoop() {
        // null 参数必须不调用 delete（防御式）；host-side DB 为 null 也不抛。
        // 返回 false 表示没有删除任何行。
        boolean result = ConversationDbManager.getInstance().clearForSpace(null);
        assertFalse(result);
    }

    // 注：queryMaxVersionForSpace(null) / queryLastMsgSeqsForSpace(null) 会 fallthrough 到
    // 老方法 queryMaxVersion() / queryLastMsgSeqs()，它们在 host-side 无 {@code WKIMApplication}
    // context 的情况下 NPE（老代码路径）。本 Phase 不改老方法，真机 / Robolectric 覆盖。
}
