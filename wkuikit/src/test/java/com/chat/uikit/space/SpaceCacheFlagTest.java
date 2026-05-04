package com.chat.uikit.space;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

/**
 * YUJ-326 · Host-side 单测：{@link SpaceCacheFlag#setTestOverrideForUnitTest} 的测试覆盖
 * 能短路 SP + BuildConfig 整条判定链。SP 路径本身依赖真实 {@code Context}，host-side
 * {@code returnDefaultValues=true} 下 {@code WKSharedPreferencesUtil.getInstance()} 会 NPE，
 * 所以 bucket/SP 语义只能在 instrumented test 或 Robolectric 下覆盖（见 TODO 注释）。
 */
public class SpaceCacheFlagTest {

    @After
    public void tearDown() {
        SpaceCacheFlag.setTestOverrideForUnitTest(null);
    }

    @Test
    public void testOverrideTrueWins() {
        SpaceCacheFlag.setTestOverrideForUnitTest(Boolean.TRUE);
        assertTrue(SpaceCacheFlag.isEnabled());
    }

    @Test
    public void testOverrideFalseWins() {
        SpaceCacheFlag.setTestOverrideForUnitTest(Boolean.FALSE);
        assertFalse(SpaceCacheFlag.isEnabled());
    }
}
