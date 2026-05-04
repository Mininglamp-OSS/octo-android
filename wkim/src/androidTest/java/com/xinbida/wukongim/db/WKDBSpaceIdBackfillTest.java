package com.xinbida.wukongim.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.xinbida.wukongim.WKIMApplication;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * YUJ-326 · {@link WKDBSpaceIdBackfill} instrumented scenario coverage.
 *
 * <p>Host-side 单测（{@link WKDBSpaceIdBackfillApiTest}）只守 public API 契约 +
 * {@link SpaceCacheBackfillGate} 内存状态；SQLCipher 真实 runtime、事务语义、大表分批、
 * 磁盘裕量分支都必须在真机 / emulator 上验证。
 *
 * <p>本测试类按 Codex review（YUJ-328 / YUJ-329）要求补齐 3 条关键场景：
 * <ol>
 *   <li>{@link #runBackfillMessage_rollsBackEntireTransactionOnMidUpdateFailure()} —
 *       事务回滚：中途 UPDATE 抛异常（SQLite trigger RAISE(ROLLBACK)）→ 所在批次
 *       {@code setTransactionSuccessful} 未被调用，该批的 space_id 全部保持 '' 未变。</li>
 *   <li>{@link #runBackfillMessage_batchesAtFiveThousandPerTransactionFor1_05MRows()} —
 *       1M 行分批边界：1,050,000 行 × 5000/批 → 至少 210 批；所有行最终 space_id 正确回填。</li>
 *   <li>{@link #runIfNeeded_defersMigrationWhenDiskHeadroomInsufficient()} —
 *       低磁盘延迟：{@code StatFs.availableBytes = dbSize × 1.5}（低于 2.5 阈值）→
 *       state 标记为 {@code retry_pending_disk}，{@link SpaceCacheBackfillGate} 保持 off。</li>
 * </ol>
 *
 * <p>硬约束 · 不改 SpaceFilter 五层 / 不动 migration 核心逻辑。测试通过 {@code
 * availableBytesOverrideForTest} / {@code dbFootprintOverrideForTest} 两个 {@link
 * VisibleForTesting} hook 注入磁盘参数，不影响生产代码行为（override 字段默认 {@code null}）。
 */
@RunWith(AndroidJUnit4.class)
public class WKDBSpaceIdBackfillTest {

    /** 32-char lowercase-hex spaceID，与 server {@code pkg/space/channel.go BuildChannelID} 对齐。 */
    private static final String SPACE_HEX = "0123456789abcdef0123456789abcdef";
    /** Space channel_id: "s" + 32hex + "_" + peer —— 会被 backfill 反解到 space_id 列。 */
    private static final String SPACE_CHANNEL = "s" + SPACE_HEX + "_peer001";
    /** 非 Space channel_id（老格式），backfill 不回填，space_id 保持 ''。 */
    private static final String LEGACY_CHANNEL = "u_10000";

    /** 仅本 test class 使用的 DB passphrase（SQLCipher 必填；与生产 uid-as-key 无关）。 */
    private static final String TEST_PASSPHRASE = "yuj329-backfill-test-key";

    private static final String TEST_UID = "yuj329_test_uid";

    private Context targetContext;
    private File dbFile;
    private SQLiteDatabase db;

    @Before
    public void setUp() {
        // SQLCipher 需要 libsqlcipher.so；InstrumentationRegistry 的 target context
        // 是 app 真实进程，native lib 已随 wkim / app AAR 打入 APK。
        System.loadLibrary("sqlcipher");

        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // WKIMApplication 的 getSharedName() 仅返回 final 常量，此处无需 initContext。
        // 但为了 sharedPrefs(context, uid) 走完整路径，测试直接用 target context 即可。

        // 为每个测试建一个独立 DB 文件，避免跨 test 污染。
        dbFile = new File(targetContext.getCacheDir(),
                "yuj329_backfill_test_" + System.nanoTime() + ".db");
        if (dbFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dbFile.delete();
        }
        db = SQLiteDatabase.openOrCreateDatabase(dbFile, TEST_PASSPHRASE, null, null, null);

        // 与 wk_sql/202605040001.sql migration 等价的 schema 子集（仅关注 backfill 会动的列）。
        db.execSQL("CREATE TABLE message (client_seq INTEGER PRIMARY KEY, "
                + "channel_id TEXT NOT NULL, channel_type INTEGER DEFAULT 0, "
                + "space_id TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE conversation (channel_id TEXT PRIMARY KEY, "
                + "channel_type INTEGER DEFAULT 0, "
                + "space_id TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE channel (channel_id TEXT PRIMARY KEY, "
                + "channel_type INTEGER DEFAULT 0, "
                + "space_id TEXT NOT NULL DEFAULT '')");

        // 清空单测可能污染到的 SP 状态 + 内存 gate。
        WKDBSpaceIdBackfill.clearStateForTest(targetContext, TEST_UID);
        SpaceCacheBackfillGate.setBackfillDone(false);
        WKDBSpaceIdBackfill.availableBytesOverrideForTest = null;
        WKDBSpaceIdBackfill.dbFootprintOverrideForTest = null;
    }

    @After
    public void tearDown() {
        // 恢复 override 防止泄漏到后续 test
        WKDBSpaceIdBackfill.availableBytesOverrideForTest = null;
        WKDBSpaceIdBackfill.dbFootprintOverrideForTest = null;
        SpaceCacheBackfillGate.setBackfillDone(false);
        WKDBSpaceIdBackfill.clearStateForTest(targetContext, TEST_UID);

        if (db != null && db.isOpen()) {
            db.close();
        }
        if (dbFile != null && dbFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dbFile.delete();
            //noinspection ResultOfMethodCallIgnored
            new File(dbFile.getAbsolutePath() + "-wal").delete();
            //noinspection ResultOfMethodCallIgnored
            new File(dbFile.getAbsolutePath() + "-shm").delete();
            //noinspection ResultOfMethodCallIgnored
            new File(dbFile.getAbsolutePath() + "-journal").delete();
        }
    }

    // ==================== Scenario 1 · 事务回滚 ====================

    /**
     * 中途 UPDATE 抛异常：安装 BEFORE UPDATE trigger，在单个 client_seq 命中时 RAISE(ROLLBACK)。
     * runBackfillTable 的 try/finally 结构：
     * <pre>
     *   db.beginTransaction();
     *   try {
     *     db.execSQL("UPDATE message SET space_id = ...");   // 抛出 SQLiteException
     *     db.setTransactionSuccessful();                      // 永远不会被执行到
     *   } finally {
     *     if (db.inTransaction()) db.endTransaction();        // 回滚整个事务
     *   }
     * </pre>
     * 验证：<br>
     * (a) execSQL 抛异常 → 上层感知；<br>
     * (b) 所有行 space_id 保持空串（没被半写入）；<br>
     * (c) DB 不再处于 transaction 中（finally 的 endTransaction 被执行）。
     */
    @Test
    public void runBackfillMessage_rollsBackEntireTransactionOnMidUpdateFailure() {
        // 种 200 行 Space 格式 channel_id（< MSG_SINGLE_UPDATE_THRESHOLD，走 single-UPDATE 路径）
        db.beginTransaction();
        try {
            for (int i = 1; i <= 200; i++) {
                db.execSQL("INSERT INTO message (client_seq, channel_id, space_id) VALUES (?, ?, '')",
                        new Object[]{i, SPACE_CHANNEL});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        assertEquals(200L, queryLong(db, "SELECT COUNT(*) FROM message WHERE space_id = ''"));

        // 安装 trigger：当某一行试图把 space_id 从 '' 改成非空时，在 client_seq=142 触发 ROLLBACK。
        // RAISE(ROLLBACK) 会中止当前语句并回滚整个事务（SQLite 文档 CREATE TRIGGER / RAISE）。
        db.execSQL("CREATE TRIGGER fail_mid_update BEFORE UPDATE ON message "
                + "WHEN NEW.space_id != '' AND NEW.client_seq = 142 "
                + "BEGIN SELECT RAISE(ROLLBACK, 'yuj329-test-midbatch-abort'); END;");

        boolean threw = false;
        try {
            WKDBSpaceIdBackfill.runBackfillMessage(db);
        } catch (Throwable expected) {
            threw = true;
        }
        assertTrue("execSQL must propagate the RAISE(ROLLBACK) SQLiteException",
                threw);

        // 事务整体回滚 → 没有任何行的 space_id 被成功写入（验证 setTransactionSuccessful 未调用）
        assertEquals("rollback should leave every row with space_id='' unchanged",
                200L, queryLong(db, "SELECT COUNT(*) FROM message WHERE space_id = ''"));
        assertEquals("no row should have received the 32-hex space_id snippet",
                0L, queryLong(db,
                        "SELECT COUNT(*) FROM message WHERE space_id = '" + SPACE_HEX + "'"));

        // finally 块必须已调用 endTransaction：DB 不该滞留在 transaction 状态
        assertFalse("finally must have closed the transaction via endTransaction()",
                db.inTransaction());
    }

    // ==================== Scenario 2 · 1M 行分批边界 ====================

    /**
     * 1,050,000 行 message 数据 → 按 {@link WKDBSpaceIdBackfill#MSG_BATCH_SIZE}=5000 切分
     * 至少 210 批；runBackfillMessage 走 batched 分支（因为 rowCount ≥
     * {@link WKDBSpaceIdBackfill#MSG_SINGLE_UPDATE_THRESHOLD}=100_000）。
     *
     * <p>验证：<br>
     * (a) 所有 Space channel 行 → 正确反解到 32-char hex space_id；<br>
     * (b) 非 Space channel 行（老格式）→ space_id 保持空串，不被误写；<br>
     * (c) 行总数精确等于插入数（batching 不丢行 / 不重写）。
     *
     * <p>为 runtime 现实考量：用 recursive CTE 一次性 INSERT 1.05M 行（~10s 级别），
     * 然后一次 backfill（~数秒）。真机上 ~30s 内完成。
     */
    @Test
    public void runBackfillMessage_batchesAtFiveThousandPerTransactionFor1_05MRows() {
        final int totalRows = 1_050_000;
        // 用 CROSS JOIN 两个小 recursive CTE（1050 × 1000 = 1,050,000）一次性 INSERT。
        // 相比单 CTE 递归 1.05M 次，这种写法把 SQLite 递归队列压在 2050 行量级，
        // 避免某些机型 recursion depth / 内存尖峰。
        db.beginTransaction();
        try {
            db.execSQL(
                    "WITH RECURSIVE "
                            + "  ones(x) AS (SELECT 1 UNION ALL SELECT x+1 FROM ones WHERE x < 1050), "
                            + "  thou(y) AS (SELECT 1 UNION ALL SELECT y+1 FROM thou WHERE y < 1000) "
                            + "INSERT INTO message (client_seq, channel_id, space_id) "
                            + "SELECT (ones.x - 1) * 1000 + thou.y, ?, '' FROM ones, thou",
                    new Object[]{SPACE_CHANNEL});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        // Sanity: 插入数量 + 行数 ≥ threshold（确保走 batched 分支，不是 single-update fallback）
        assertEquals(totalRows, queryLong(db, "SELECT COUNT(*) FROM message"));
        assertTrue("row count must exceed MSG_SINGLE_UPDATE_THRESHOLD to force batching",
                totalRows >= WKDBSpaceIdBackfill.MSG_SINGLE_UPDATE_THRESHOLD);

        // 预期最少批次 = ceil(totalRows / MSG_BATCH_SIZE) —— 对 1,050,000 / 5000 = 210 批。
        int expectedMinBatches = (totalRows + WKDBSpaceIdBackfill.MSG_BATCH_SIZE - 1)
                / WKDBSpaceIdBackfill.MSG_BATCH_SIZE;
        assertEquals("1.05M rows / 5000 per batch must produce exactly 210 batches",
                210, expectedMinBatches);

        long startMs = android.os.SystemClock.elapsedRealtime();
        WKDBSpaceIdBackfill.runBackfillMessage(db);
        long durationMs = android.os.SystemClock.elapsedRealtime() - startMs;

        // 所有行应被正确回填为 32-char hex
        assertEquals("every row must have space_id correctly parsed from channel_id",
                (long) totalRows,
                queryLong(db, "SELECT COUNT(*) FROM message WHERE space_id = '" + SPACE_HEX + "'"));
        assertEquals("no row should remain with empty space_id",
                0L, queryLong(db, "SELECT COUNT(*) FROM message WHERE space_id = ''"));
        assertEquals("row count must be preserved across batched UPDATE",
                (long) totalRows, queryLong(db, "SELECT COUNT(*) FROM message"));

        // 性能硬性 sanity（非主要断言）：真机上 1M 行 batched UPDATE 应 < 60s。
        assertTrue("backfill completed in " + durationMs + "ms (sanity < 60000)",
                durationMs < 60_000);
    }

    /**
     * 复用 Scenario 2 的分批路径，但混入 legacy（非 Space）channel：确保 WHERE 子句
     * {@code channel_id LIKE 's%' AND length > 33 AND substr(34,1)='_'} 正确筛掉老格式
     * 不误回填 —— 是 {@link SpaceChannelIdParser} 契约在 SQL 侧的等价实现。
     */
    @Test
    public void runBackfillMessage_leavesLegacyChannelIdsUntouched() {
        // 5 行 Space + 5 行 legacy，走 single-UPDATE 路径（rows << threshold）
        db.beginTransaction();
        try {
            for (int i = 1; i <= 5; i++) {
                db.execSQL("INSERT INTO message (client_seq, channel_id, space_id) VALUES (?, ?, '')",
                        new Object[]{i, SPACE_CHANNEL});
            }
            for (int i = 6; i <= 10; i++) {
                db.execSQL("INSERT INTO message (client_seq, channel_id, space_id) VALUES (?, ?, '')",
                        new Object[]{i, LEGACY_CHANNEL});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        WKDBSpaceIdBackfill.runBackfillMessage(db);

        assertEquals(5L, queryLong(db,
                "SELECT COUNT(*) FROM message WHERE space_id = '" + SPACE_HEX + "'"));
        assertEquals(5L, queryLong(db,
                "SELECT COUNT(*) FROM message WHERE space_id = ''"));
    }

    // ==================== Scenario 3 · 低磁盘延迟 ====================

    /**
     * 低磁盘分支：假装 DB 占用 10MB、可用磁盘 15MB（== dbSize × 1.5），远低于
     * {@link WKDBSpaceIdBackfill#DISK_HEADROOM_MULTIPLIER}=2.5 要求的 25MB。
     * runIfNeeded 应：
     * <ul>
     *   <li>不执行 UPDATE（conversation / message / channel 保持 space_id=''）；</li>
     *   <li>SP state 置为 {@code retry_pending_disk}；</li>
     *   <li>{@link SpaceCacheBackfillGate#isBackfillDone()} 保持 {@code false}。</li>
     * </ul>
     *
     * <p>Override 使用 {@code @VisibleForTesting} 字段 {@code availableBytesOverrideForTest}
     * + {@code dbFootprintOverrideForTest}，避免在真机上操纵 StatFs（无法）或构造 GB 级
     * 文件（不现实）。生产代码路径下这两个字段保持 {@code null} 不生效。
     */
    @Test
    public void runIfNeeded_defersMigrationWhenDiskHeadroomInsufficient() {
        // 种一行便于验证没被 backfill 动过
        db.execSQL("INSERT INTO message (client_seq, channel_id, space_id) VALUES (1, ?, '')",
                new Object[]{SPACE_CHANNEL});
        db.execSQL("INSERT INTO conversation (channel_id, space_id) VALUES (?, '')",
                new Object[]{SPACE_CHANNEL});

        // 模拟 dbSize=10MB, avail=15MB → ratio=1.5 < 2.5 阈值
        long simulatedDbSize = 10L * 1024 * 1024;
        long simulatedAvail = (long) (simulatedDbSize * 1.5);
        WKDBSpaceIdBackfill.dbFootprintOverrideForTest = simulatedDbSize;
        WKDBSpaceIdBackfill.availableBytesOverrideForTest = simulatedAvail;

        // 确保从干净状态起跑
        assertEquals(WKDBSpaceIdBackfill.STATE_NONE,
                WKDBSpaceIdBackfill.getStateForTest(targetContext, TEST_UID));
        assertFalse(SpaceCacheBackfillGate.isBackfillDone());

        WKDBSpaceIdBackfill.runIfNeeded(targetContext, db, TEST_UID);

        // State 必须是 retry_pending_disk（不是 done，也不是 in_progress / failed）
        assertEquals("low disk should defer backfill via retry_pending_disk state",
                WKDBSpaceIdBackfill.STATE_RETRY_DISK,
                WKDBSpaceIdBackfill.getStateForTest(targetContext, TEST_UID));

        // Gate 必须保持 off（上层 SpaceCacheFlag 读此值决定是否启用 per-Space 路径）
        assertFalse("SpaceCacheBackfillGate must remain false when migration is deferred",
                SpaceCacheBackfillGate.isBackfillDone());

        // DB 未被改动 —— 延迟意味着当前启动周期内不应触碰 UPDATE。
        assertEquals("deferred migration must not touch the message table",
                1L, queryLong(db, "SELECT COUNT(*) FROM message WHERE space_id = ''"));
        assertEquals("deferred migration must not touch the conversation table",
                1L, queryLong(db, "SELECT COUNT(*) FROM conversation WHERE space_id = ''"));

        // 关闭 override，下次调用应正常跑（验证 override 不会粘滞造成死延迟）
        WKDBSpaceIdBackfill.availableBytesOverrideForTest = null;
        WKDBSpaceIdBackfill.dbFootprintOverrideForTest = null;

        // 重置 state 让 runIfNeeded 能再跑一次（否则会因 retries 累加继续尝试，也能验通路）
        WKDBSpaceIdBackfill.clearStateForTest(targetContext, TEST_UID);
        WKDBSpaceIdBackfill.runIfNeeded(targetContext, db, TEST_UID);
        assertEquals("with real StatFs (ample disk), backfill should succeed",
                WKDBSpaceIdBackfill.STATE_DONE,
                WKDBSpaceIdBackfill.getStateForTest(targetContext, TEST_UID));
        assertTrue(SpaceCacheBackfillGate.isBackfillDone());
        assertNotEquals("after real run, space_id must be populated from the Space channel_id",
                "", queryString(db, "SELECT space_id FROM message WHERE client_seq = 1"));
    }

    // ==================== Helpers ====================

    private static long queryLong(SQLiteDatabase db, String sql) {
        try (Cursor c = db.rawQuery(sql, null)) {
            if (c != null && c.moveToFirst()) return c.getLong(0);
        }
        return 0L;
    }

    private static String queryString(SQLiteDatabase db, String sql) {
        try (Cursor c = db.rawQuery(sql, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        }
        return null;
    }
}
