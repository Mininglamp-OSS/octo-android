package com.xinbida.wukongim.db;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.StatFs;
import android.os.SystemClock;

import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.utils.WKLoggerUtils;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import java.io.File;

/**
 * YUJ-326 · v1 → v2 inline {@code space_id} 回填。
 *
 * <p>替换方案 A 初版 "首次升级 DELETE FROM conversation 吞全量代价" 的实现
 * （Yu review 2026-05-04 04:41Z 否决原因：存量重度用户流量 + 状态丢失 + 弱网空列表 +
 * 大 ALTER 慢锁 + 离线首开无法补回）。
 *
 * <p>执行路径：
 * <ol>
 *   <li>Schema migration（{@code wk_sql/202605040001.sql}）已由 {@link WKDBUpgrade} 跑完，
 *       三表 {@code space_id} 列默认 {@code ''}。</li>
 *   <li>本类由 {@link WKDBHelper} 构造末尾调用 {@link #runIfNeeded(Context, SQLiteDatabase, String)}。</li>
 *   <li>状态机（SP key {@link #SP_STATE}）：{@code none → in_progress → done | retry | failed}。
 *       {@code done} / {@code failed} 立即短路；{@code retry} 在 MAX_RETRIES 前继续尝试。</li>
 *   <li>磁盘检查：{@code StatFs.availableBytes &lt; dbSize × 2.5} 推迟到下次启动
 *       （标记 {@code retry_pending_disk}），避免在低磁盘设备上触发 out-of-space 崩溃。</li>
 *   <li>conversation + channel：典型行数 &lt; 5000，单事务批量 UPDATE 一次过。</li>
 *   <li>message：可能百万级，按 {@code client_seq} 分段每批 {@link #MSG_BATCH_SIZE} 行一个事务。</li>
 *   <li>全部成功 → 标记 {@code done}，{@link SpaceCacheBackfillGate#setBackfillDone(boolean)}
 *       打开 SpaceCacheFlag（wkuikit 侧 flag 读此状态作为最终 gate）。</li>
 *   <li>任一表失败 → 回滚该表事务（SQLite 原生），增量重试计数；超过 MAX_RETRIES 标记
 *       {@code failed} + {@link SpaceCacheBackfillGate#setBackfillDone(boolean) 强制关
 *       SpaceCacheFlag}，确保上层走 {@code clearAll()} 老路径而非半回填态。</li>
 * </ol>
 *
 * <p>硬约束 ·
 * <ul>
 *   <li>ALTER 列已在 migration SQL 里完成（idempotent，重复执行不会重建），本类只管
 *       UPDATE 回填 + INDEX 由 migration SQL 负责。</li>
 *   <li>失败降级路径不改 DB schema（不做 "version=1 回退"）—— 保留列的 DEFAULT ''
 *       语义对所有老代码路径是无副作用的（conversation.space_id='' 不参与任何查询
 *       过滤除非 flag on，而 flag 在失败后被强制关）。</li>
 *   <li>所有 UPDATE 走 server 规则 {@code s&lt;32hex&gt;_peer} 前缀反解；不匹配该格式的
 *       channel_id（普通 peer 频道 / 早期格式）保持 {@code space_id=''}，语义正确。</li>
 *   <li>打点：耗时 / 成功 / 失败 / 磁盘不足 / 重试走 {@link WKLoggerUtils} TAG=YUJ326。</li>
 * </ul>
 */
public final class WKDBSpaceIdBackfill {

    private static final String TAG = "YUJ326-backfill";

    // SP keys（uid-scoped · 落 "wk_account_config" 文件，与 WKIMApplication.setDBUpgradeIndex
    // / getDBUpgradeIndex 是同一个 SharedPreferences，不要把回填状态拆到别的文件避免状态
    // 发散 —— 见 {@link #sharedPrefs} 说明。YUJ-330 Jerry review 2026-05-04 06:49Z Warning #4
    // 删除未使用的 SP_FILE_SUFFIX 常量，并把"同一文件"的约束在注释里点名到字面量。
    private static final String SP_STATE = "yuj326_backfill_state";
    private static final String SP_RETRIES = "yuj326_backfill_retries";
    private static final String SP_LAST_DURATION_MS = "yuj326_backfill_last_duration_ms";
    private static final String SP_LAST_ERROR = "yuj326_backfill_last_error";

    // 状态常量
    static final String STATE_NONE = "none";
    static final String STATE_IN_PROGRESS = "in_progress";
    static final String STATE_DONE = "done";
    static final String STATE_RETRY_PENDING = "retry_pending";
    static final String STATE_RETRY_DISK = "retry_pending_disk";
    static final String STATE_FAILED = "failed";

    // 调参
    static final int MAX_RETRIES = 3;
    /** message 表每批 row 数，参考 Yu review 建议 5000。 */
    static final int MSG_BATCH_SIZE = 5000;
    /** message 表低于此阈值一次性 UPDATE（避免多次 txn overhead）。 */
    static final int MSG_SINGLE_UPDATE_THRESHOLD = 100_000;
    /** 磁盘裕量倍率：要求 avail ≥ dbSize × N，防 ALTER/UPDATE 期间 out-of-space。 */
    static final double DISK_HEADROOM_MULTIPLIER = 2.5;

    /**
     * 纯工具类，禁止实例化。对 SP / context 的所有依赖从参数传入，单测可 mock。
     */
    private WKDBSpaceIdBackfill() {}

    /**
     * 根据 SP 状态决定是否执行 backfill。
     *
     * @param context 应用上下文（用于 SharedPreferences + StatFs）
     * @param db      已打开的 SQLCipher DB 句柄（由 WKDBHelper 管理生命周期）
     * @param uid     当前登录用户 uid（SP key prefix + DB 文件名一部分）
     */
    public static void runIfNeeded(Context context, SQLiteDatabase db, String uid) {
        if (context == null || db == null || uid == null || uid.isEmpty()) {
            WKLoggerUtils.getInstance().e(TAG, "runIfNeeded skipped: invalid inputs");
            return;
        }
        SharedPreferences sp = sharedPrefs(context, uid);
        String state = sp.getString(SP_STATE, STATE_NONE);
        if (STATE_DONE.equals(state)) {
            // 已完成，幂等返回。
            SpaceCacheBackfillGate.setBackfillDone(true);
            return;
        }
        if (STATE_FAILED.equals(state)) {
            // 硬失败过 → 不重试，强制关 flag（上层 SpaceCacheFlag.isEnabled 会读此 gate）。
            SpaceCacheBackfillGate.setBackfillDone(false);
            WKLoggerUtils.getInstance().e(TAG, "runIfNeeded: previously FAILED, skip");
            return;
        }
        int retries = sp.getInt(SP_RETRIES, 0);
        if (retries >= MAX_RETRIES) {
            markFailed(sp, "retries exhausted (" + retries + ")");
            return;
        }

        // 磁盘检查
        long dbSize = estimateDbFootprint(context, uid);
        long avail = availableBytes(context);
        if (avail > 0 && dbSize > 0 && avail < (long) (dbSize * DISK_HEADROOM_MULTIPLIER)) {
            // 磁盘不足：标记 retry_pending_disk，下次启动再试。不计入 retries（非本次 impl 失败）。
            sp.edit().putString(SP_STATE, STATE_RETRY_DISK).apply();
            WKLoggerUtils.getInstance().e(TAG,
                    "runIfNeeded deferred: low disk avail=" + avail + " dbSize=" + dbSize
                            + " need=" + (long) (dbSize * DISK_HEADROOM_MULTIPLIER));
            SpaceCacheBackfillGate.setBackfillDone(false);
            return;
        }

        // 开始回填
        sp.edit()
                .putString(SP_STATE, STATE_IN_PROGRESS)
                .putInt(SP_RETRIES, retries + 1)
                .apply();
        long startMs = SystemClock.elapsedRealtime();
        WKLoggerUtils.getInstance().e(TAG, "backfill START attempt=" + (retries + 1));

        try {
            runBackfillTable(db, "conversation");
            runBackfillTable(db, "channel");
            runBackfillMessage(db);
            long duration = SystemClock.elapsedRealtime() - startMs;
            sp.edit()
                    .putString(SP_STATE, STATE_DONE)
                    .putLong(SP_LAST_DURATION_MS, duration)
                    .remove(SP_LAST_ERROR)
                    .apply();
            SpaceCacheBackfillGate.setBackfillDone(true);
            WKLoggerUtils.getInstance().e(TAG, "backfill DONE +" + duration + "ms");
        } catch (Throwable e) {
            long duration = SystemClock.elapsedRealtime() - startMs;
            String errMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
            WKLoggerUtils.getInstance().e(TAG, "backfill FAILED attempt=" + (retries + 1)
                    + " +" + duration + "ms err=" + errMsg);
            if (retries + 1 >= MAX_RETRIES) {
                markFailed(sp, errMsg);
            } else {
                sp.edit()
                        .putString(SP_STATE, STATE_RETRY_PENDING)
                        .putLong(SP_LAST_DURATION_MS, duration)
                        .putString(SP_LAST_ERROR, errMsg)
                        .apply();
                SpaceCacheBackfillGate.setBackfillDone(false);
            }
        }
    }

    /**
     * 单表单事务 UPDATE。conversation / channel 行数小（&lt; 5000 典型），一次过不分批。
     * 事务失败会被 {@code try/finally + endTransaction()} 自动回滚。
     */
    static void runBackfillTable(SQLiteDatabase db, String table) {
        db.beginTransaction();
        try {
            db.execSQL(
                    "UPDATE " + table + " SET space_id = substr(channel_id, 2, 32) "
                            + "WHERE channel_id LIKE 's%' "
                            + "AND length(channel_id) > 33 "
                            + "AND substr(channel_id, 34, 1) = '_'");
            db.setTransactionSuccessful();
        } finally {
            if (db.inTransaction()) db.endTransaction();
        }
    }

    /**
     * message 表大表分批。查出 row count 判断是否分批；分批时按 {@code client_seq}
     * 主键范围切，保证每批大小稳定，且不会因为中间删除行导致遗漏。
     */
    static void runBackfillMessage(SQLiteDatabase db) {
        long rowCount = queryCount(db, "SELECT COUNT(*) FROM message");
        if (rowCount < MSG_SINGLE_UPDATE_THRESHOLD) {
            runBackfillTable(db, "message");
            return;
        }
        long minId = queryCount(db, "SELECT COALESCE(MIN(client_seq), 0) FROM message");
        long maxId = queryCount(db, "SELECT COALESCE(MAX(client_seq), 0) FROM message");
        if (maxId < minId) return; // 空表保护

        long from = minId;
        int batches = 0;
        while (from <= maxId) {
            long to = from + MSG_BATCH_SIZE - 1;
            db.beginTransaction();
            try {
                db.execSQL(
                        "UPDATE message SET space_id = substr(channel_id, 2, 32) "
                                + "WHERE client_seq BETWEEN ? AND ? "
                                + "AND channel_id LIKE 's%' "
                                + "AND length(channel_id) > 33 "
                                + "AND substr(channel_id, 34, 1) = '_'",
                        new Object[]{from, to});
                db.setTransactionSuccessful();
            } finally {
                if (db.inTransaction()) db.endTransaction();
            }
            batches++;
            from = to + 1;
        }
        WKLoggerUtils.getInstance().e(TAG,
                "message backfill batched: rows=" + rowCount + " batches=" + batches);
    }

    /** 单行标量查询，查不到返回 0。 */
    private static long queryCount(SQLiteDatabase db, String sql) {
        try (Cursor c = db.rawQuery(sql, null)) {
            if (c != null && c.moveToFirst()) return c.getLong(0);
        } catch (Throwable e) {
            WKLoggerUtils.getInstance().e(TAG, "queryCount error sql=" + sql
                    + " err=" + e.getMessage());
        }
        return 0L;
    }

    /** 估算 DB 占用（主文件 + WAL + SHM）作为 "需要多少磁盘裕量" 的基准。 */
    static long estimateDbFootprint(Context context, String uid) {
        Long override = dbFootprintOverrideForTest;
        if (override != null) return override;
        try {
            File dbFile = context.getDatabasePath("wk_" + uid + ".db");
            long size = safeLength(dbFile);
            size += safeLength(new File(dbFile.getAbsolutePath() + "-wal"));
            size += safeLength(new File(dbFile.getAbsolutePath() + "-shm"));
            return size;
        } catch (Throwable e) {
            return 0L;
        }
    }

    private static long safeLength(File f) {
        try {
            return (f != null && f.exists()) ? f.length() : 0L;
        } catch (Throwable e) {
            return 0L;
        }
    }

    /** 当前 DB 目录剩余可用字节，失败返回 0（视作"无法判定"，不阻塞 migration）。 */
    static long availableBytes(Context context) {
        Long override = availableBytesOverrideForTest;
        if (override != null) return override;
        try {
            File parent = context.getDatabasePath("probe").getParentFile();
            if (parent == null) return 0L;
            StatFs stat = new StatFs(parent.getAbsolutePath());
            return stat.getAvailableBytes();
        } catch (Throwable e) {
            return 0L;
        }
    }

    private static SharedPreferences sharedPrefs(Context context, String uid) {
        // 与 WKIMApplication.getDBUpgradeIndex / setDBUpgradeIndex 同一个文件（"wk_account_config"，
        // 由 WKIMApplication.sharedName 常量定义，通过 getSharedName() 暴露）。同一文件避免
        // 升级索引与 backfill 状态落到不同 SP 造成状态发散。
        return context.getSharedPreferences(
                WKIMApplication.getInstance().getSharedName(), Context.MODE_PRIVATE);
    }

    private static void markFailed(SharedPreferences sp, String reason) {
        sp.edit()
                .putString(SP_STATE, STATE_FAILED)
                .putString(SP_LAST_ERROR, reason)
                .apply();
        SpaceCacheBackfillGate.setBackfillDone(false);
        WKLoggerUtils.getInstance().e(TAG, "backfill HARD FAILED: " + reason);
    }

    // ==================== 测试 hook ====================

    /**
     * @VisibleForTesting 强制覆盖 {@link #availableBytes(Context)} 返回值。
     * 用于 instrumented 测试低磁盘分支（真实设备无法控制 StatFs 可用字节）。
     * {@code null} 表示走真实 StatFs 查询。生产代码路径永远不碰此字段。
     */
    static volatile Long availableBytesOverrideForTest = null;

    /**
     * @VisibleForTesting 强制覆盖 {@link #estimateDbFootprint(Context, String)} 返回值。
     * 用于 instrumented 测试低磁盘分支。{@code null} 表示走真实文件 size 查询。
     */
    static volatile Long dbFootprintOverrideForTest = null;

    /** @VisibleForTesting 重置状态（仅单测）。 */
    static void clearStateForTest(Context context, String uid) {
        if (context == null || uid == null) return;
        sharedPrefs(context, uid).edit()
                .remove(SP_STATE)
                .remove(SP_RETRIES)
                .remove(SP_LAST_DURATION_MS)
                .remove(SP_LAST_ERROR)
                .apply();
    }

    /** @VisibleForTesting 读当前状态。 */
    public static String getStateForTest(Context context, String uid) {
        if (context == null || uid == null) return STATE_NONE;
        return sharedPrefs(context, uid).getString(SP_STATE, STATE_NONE);
    }
}
