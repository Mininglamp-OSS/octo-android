package com.xinbida.wukongim.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

/**
 * YUJ-326 · {@link WKDBSpaceIdBackfill} host-side 契约 + gate 测试。
 *
 * <p>完整 DB 行为测试（事务 / 大表分批 / 磁盘检查）由 instrumented test 覆盖 ——
 * host-side {@code returnDefaultValues=true} 让 {@code StatFs / Context.getDatabasePath /
 * SharedPreferences} 全部返回 0/null，真实 SQLCipher 也没法在 JVM 起。这里只守住
 * 公开 API 契约 + {@link SpaceCacheBackfillGate} 内存状态行为。
 */
public class WKDBSpaceIdBackfillApiTest {

    @Before
    public void setUp() {
        SpaceCacheBackfillGate.setBackfillDone(false);
    }

    @Test
    public void runIfNeededMethodExists() throws NoSuchMethodException {
        Method m = WKDBSpaceIdBackfill.class.getMethod(
                "runIfNeeded",
                android.content.Context.class,
                net.zetetic.database.sqlcipher.SQLiteDatabase.class,
                String.class);
        assertEquals(void.class, m.getReturnType());
    }

    @Test
    public void runIfNeededInvalidArgsIsNoop() {
        // null / 空 uid 必须安全返回，不抛异常
        WKDBSpaceIdBackfill.runIfNeeded(null, null, null);
        WKDBSpaceIdBackfill.runIfNeeded(null, null, "");
    }

    @Test
    public void stateConstantsStable() {
        // SP 存的状态字符串是跨版本 contract（卸载重装 / migration 后仍要读得到老值），
        // 这里锁定字面值，防止 refactor 静默改名。
        assertEquals("none", WKDBSpaceIdBackfill.STATE_NONE);
        assertEquals("in_progress", WKDBSpaceIdBackfill.STATE_IN_PROGRESS);
        assertEquals("done", WKDBSpaceIdBackfill.STATE_DONE);
        assertEquals("retry_pending", WKDBSpaceIdBackfill.STATE_RETRY_PENDING);
        assertEquals("retry_pending_disk", WKDBSpaceIdBackfill.STATE_RETRY_DISK);
        assertEquals("failed", WKDBSpaceIdBackfill.STATE_FAILED);
    }

    @Test
    public void batchingConstantsReasonable() {
        // Yu review 指定 5000 行 / batch；阈值 10 万行作为 "要不要分批" 分水岭。
        assertEquals(5000, WKDBSpaceIdBackfill.MSG_BATCH_SIZE);
        assertEquals(100_000, WKDBSpaceIdBackfill.MSG_SINGLE_UPDATE_THRESHOLD);
        assertEquals(3, WKDBSpaceIdBackfill.MAX_RETRIES);
        assertTrue(WKDBSpaceIdBackfill.DISK_HEADROOM_MULTIPLIER >= 2.0);
    }

    @Test
    public void backfillGateDefaultsFalse() {
        SpaceCacheBackfillGate.setBackfillDone(false);
        assertFalse(SpaceCacheBackfillGate.isBackfillDone());
    }

    @Test
    public void backfillGateSetTrueReadsTrue() {
        SpaceCacheBackfillGate.setBackfillDone(true);
        assertTrue(SpaceCacheBackfillGate.isBackfillDone());
        SpaceCacheBackfillGate.setBackfillDone(false);
        assertFalse(SpaceCacheBackfillGate.isBackfillDone());
    }
}
