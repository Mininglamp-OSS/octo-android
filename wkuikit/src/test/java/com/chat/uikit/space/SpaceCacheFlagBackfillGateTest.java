package com.chat.uikit.space;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.xinbida.wukongim.db.SpaceCacheBackfillGate;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * YUJ-326 Yu review 2026-05-04 04:41Z · {@link SpaceCacheFlag#isEnabled()} 必须尊重
 * backfill gate：只有 {@link SpaceCacheBackfillGate#isBackfillDone()} = true 且上游
 * (test / SP / remote / BuildConfig) 至少一层 enabled 时才开启 per-Space 路径。
 *
 * <p>host-side SP 不可用（WKSharedPreferencesUtil.getInstance 会 NPE 因 Context 未初始化），
 * 所以只测 testOverride 路径 —— testOverride 故意设计成绕过 backfill gate，方便单测
 * 不依赖真实 DB 状态（见 {@link SpaceCacheFlag#isEnabled} javadoc）。
 *
 * <p>真实路径（非 testOverride）下的 backfill gate 行为由 instrumented test 覆盖，
 * 需要真 SQLCipher + Context。
 */
public class SpaceCacheFlagBackfillGateTest {

    @Before
    public void setUp() {
        SpaceCacheFlag.setTestOverrideForUnitTest(null);
        SpaceCacheBackfillGate.setBackfillDone(false);
    }

    @After
    public void tearDown() {
        SpaceCacheFlag.setTestOverrideForUnitTest(null);
        SpaceCacheBackfillGate.setBackfillDone(false);
    }

    @Test
    public void testOverrideByPassesBackfillGate() {
        // testOverride=true 忽略 backfill gate，返回 true
        SpaceCacheBackfillGate.setBackfillDone(false);
        SpaceCacheFlag.setTestOverrideForUnitTest(Boolean.TRUE);
        assertTrue(SpaceCacheFlag.isEnabled());
    }

    @Test
    public void testOverrideFalseBypassesBackfillGate() {
        // testOverride=false 同样忽略 gate，返回 false
        SpaceCacheBackfillGate.setBackfillDone(true);
        SpaceCacheFlag.setTestOverrideForUnitTest(Boolean.FALSE);
        assertFalse(SpaceCacheFlag.isEnabled());
    }

    @Test
    public void backfillGateSetDoesNotThrow() {
        SpaceCacheBackfillGate.setBackfillDone(true);
        assertTrue(SpaceCacheBackfillGate.isBackfillDone());
        SpaceCacheBackfillGate.setBackfillDone(false);
        assertFalse(SpaceCacheBackfillGate.isBackfillDone());
    }
}
