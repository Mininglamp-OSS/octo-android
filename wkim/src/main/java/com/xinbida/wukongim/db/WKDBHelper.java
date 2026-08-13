package com.xinbida.wukongim.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.xinbida.wukongim.BuildConfig;
import com.xinbida.wukongim.utils.WKLoggerUtils;

import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteDebug;
import net.zetetic.database.sqlcipher.SQLiteGlobal;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;
import net.zetetic.database.sqlcipher.SQLiteConnection;
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 2019-11-12 13:57
 * 数据库辅助类
 */
public class WKDBHelper {
    private static final String TAG = "WKDBHelper";

    /** ANR 修复专用埋点 tag，只在 debug 输出：{@code adb logcat -s ANRFix}。 */
    public static final String PERF_TAG = "ANRFix";

    /**
     *  D · 启用 SQLCipher WAL（Write-Ahead Logging）模式，允许多 reader 与单 writer
     * 并发。默认 rollback journal 模式下写事务期间任何读都要排队，Space 切换 saveSyncChat
     * 多个写事务叠加 × 主线程 adapter.convert 大量 DB 读 → ANR 30s 闪退（参见  根因
     * 分析）。WAL 模式让 reader 不再阻塞 writer、writer 也不阻塞 reader，消除该路径的锁竞争。
     *
     * <p>回滚策略：若某机型出现 WAL 异常（磁盘空间 / 权限 / 特殊 FS），把此常量改为 {@code
     * false} 重编即可恢复 rollback journal 行为。SQLCipher 4.9.0 已官方支持 WAL。
     */
    private static final boolean ENABLE_WAL = true;

    /**
     * WAL 连接池大小。SQLCipher 只在 openFlags 带 {@link SQLiteDatabase#ENABLE_WRITE_AHEAD_LOGGING}
     * 时才把池扩到这个值，否则恒为 1（见 SQLiteConnectionPool#setMaxConnectionPoolSizeLocked）。
     *
     * <p>这是**惰性上限**不是预分配：SQLiteConnectionPool.open() 只建主连接一条，非主连接按需创建。
     * 真机实测（vivo V2464A / Android 16，上限临时开到 16 + monkey 压测 + 前后台切换）峰值占用
     * 为 4，池耗尽告警 0 次 —— 取 8 是在实测峰值上留一倍余量，当前负载下不会真的建到 8 条。
     *
     * <p>不取库默认 10 或更大：SQLCipher 每新建一条连接要做一次 PBKDF2-HMAC-SHA512
     * （4.x 默认 256000 轮，实测约 400ms），谁触发谁付、可能落在主线程；连接越多也越容易出现
     * 持旧快照的 reader 拖住 checkpoint、-wal 文件涨大。8 是"够用 + 有余量 + 不放大这两项代价"的折中。
     */
    private static final int WAL_CONNECTION_POOL_SIZE = 8;

    /** 仅 debug：启动后跑一次连接池争用自测，见 {@link #debugContentionSelfTest()}。测完置 false。 */
    private static final boolean DEBUG_CONTENTION_SELF_TEST = false;

    /** 仅 debug：持续采样连接池实际占用峰值，用来定 {@link #WAL_CONNECTION_POOL_SIZE}。测完置 false。 */
    private static final boolean DEBUG_POOL_USAGE_SAMPLER = false;
    private static final long POOL_SAMPLE_INTERVAL_MS = 300;
    private static final long SELF_TEST_START_DELAY_MS = 3000;
    private static final long SELF_TEST_PROBE_DELAY_MS = 900;
    private static final int SELF_TEST_PROBE_COUNT = 3;
    private static final long SELF_TEST_TXN_HOLD_MS = 4000;

//    private DatabaseHelper mDbHelper;
    private SQLiteDatabase mDb;

    // 数据库操作线程池（单线程，保证数据库操作的顺序性）
    private static final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    // 主线程 Handler，用于回调
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public SQLiteDatabase getDb() {
        return mDb;
    }

    public boolean isClosed() {
        SQLiteDatabase db = mDb;
        return db == null || !db.isOpen();
    }

    private volatile static WKDBHelper openHelper = null;
    // 数据库版本
    private final static int version = 1;
    private static String myDBName;
    private static String uid;
    
    /**
     * 数据库查询回调接口
     */
    public interface QueryCallback<T> {
        /**
         * 在后台线程执行查询操作
         * @param cursor 查询结果游标
         * @return 处理后的结果
         */
        T onQuery(Cursor cursor);
        
        /**
         * 在主线程接收查询结果
         * @param result 查询结果
         */
        void onResult(T result);
    }

    private WKDBHelper(Context ctx, String uid) {
        WKDBHelper.uid = uid;
        myDBName = "wk_" + uid + ".db";
        try {
            System.loadLibrary("sqlcipher");
            File databaseFile = ctx.getDatabasePath(myDBName);
            databaseFile.getParentFile().mkdirs();
            // WAL 必须在 open 时经 flags 传入。openOrCreateDatabase 的 11 个重载全部写死
            // CREATE_IF_NECESSARY，拿不到 ENABLE_WRITE_AHEAD_LOGGING，连接池就被钉死在 1 条；
            // 事后 PRAGMA journal_mode=WAL 只改日志模式，改不了池大小（池在 open 时按 flags 定死），
            // 主线程读依旧要排队等后台写事务放连接 —— 这是 ANRWatchdog 那批 ANR 的根因。
            int openFlags = SQLiteDatabase.CREATE_IF_NECESSARY;
            if (ENABLE_WAL) {
                SQLiteGlobal.setWALConnectionPoolSize(WAL_CONNECTION_POOL_SIZE);
                openFlags |= SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING;
            }
            mDb = SQLiteDatabase.openDatabase(databaseFile.getAbsolutePath(), uid, null,
                    openFlags, null, null);
            verifyWalMode();
            WKDBUpgrade.getInstance().onUpgrade(mDb);
            if (BuildConfig.DEBUG) debugContentionSelfTest();
            if (BuildConfig.DEBUG) debugPoolUsageSampler(databaseFile.getAbsolutePath());
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG + " init WKDBHelper error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 校验 WAL 是否真的生效。SQLCipher 在每条连接 open 时都会按 openFlags 自己设
     * journal_mode / synchronous，正常路径下这里只需读回确认；某些厂商 ROM 的特殊 FS 会拒绝
     * WAL，此时退回 rollback journal（池仍为 1，行为与打 flag 前一致），只记日志不阻断初始化。
     *
     * <p>不再手动设 wal_autocheckpoint / journal_size_limit：池 &gt; 1 后 execSQL 落到哪条
     * 连接不确定，只能配到其中一条，反而变成不可预期的配置。沿用库默认（1000 页 / 10000 字节）。
     */
    private void verifyWalMode() {
        if (!ENABLE_WAL || mDb == null) return;
        try {
            String mode = queryPragma("journal_mode");
            if ("wal".equalsIgnoreCase(mode)) {
                // 池 > 1 后 rawQuery 落到哪条连接不确定，但所有连接由 SQLiteConnection 在 open
                // 时按同一份 config 配置，读回任意一条都有代表性。
                if (BuildConfig.DEBUG) {
                    Log.d(PERF_TAG, "[WAL] ON pool=" + SQLiteGlobal.getWALConnectionPoolSize()
                            + " journal_mode=" + mode
                            + " synchronous=" + queryPragma("synchronous")
                            + " page_size=" + queryPragma("page_size")
                            + " wal_autocheckpoint=" + queryPragma("wal_autocheckpoint")
                            + " journal_size_limit=" + queryPragma("journal_size_limit"));
                }
            } else {
                WKLoggerUtils.getInstance().e(TAG, "SQLCipher WAL NOT active, journal_mode=" + mode
                        + " (connection pool stays at 1)");
            }
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG + " verifyWalMode error: " + e.getMessage());
        }
    }

    private String queryPragma(String name) {
        try (Cursor cursor = mDb.rawQuery("PRAGMA " + name, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 仅 debug：复现 ANR 堆栈里的争用场景，量化"连接池 = 1"到底让主线程等多久。
     *
     * <p>后台线程占住一个事务 {@link #SELF_TEST_TXN_HOLD_MS} 毫秒（不 setTransactionSuccessful，
     * 结束时回滚，不写入任何数据），期间主线程发一次读。池 = 1 时主线程拿不到连接，会一直
     * park 到事务结束 —— 这正是 ANRWatchdog 那批堆栈的形态；池 &gt; 1 时主线程能拿到另一条
     * 连接，立即返回。两次运行（{@link #ENABLE_WAL} 开 / 关）的 waited 值就是修复效果。
     *
     * <p>顺带打印 reminders 表行数：老写法每次是整表扫描，行数决定那条 SQL 的真实代价。
     */
    private void debugContentionSelfTest() {
        if (!DEBUG_CONTENTION_SELF_TEST) return;
        final SQLiteDatabase db = mDb;
        if (db == null) return;
        new Thread(() -> {
            try {
                Thread.sleep(SELF_TEST_START_DELAY_MS);
                Log.d(PERF_TAG, "[selftest] reminders rows="
                        + queryLong(db, "select count(*) from reminders")
                        + " undone=" + queryLong(db, "select count(*) from reminders where done=0")
                        + " | pool=" + (ENABLE_WAL ? SQLiteGlobal.getWALConnectionPoolSize() : 1));

                Handler main = new Handler(Looper.getMainLooper());
                for (int i = 1; i <= SELF_TEST_PROBE_COUNT; i++) {
                    final int seq = i;
                    final long dueAtMs = SystemClock.elapsedRealtime() + SELF_TEST_PROBE_DELAY_MS * i;
                    main.postDelayed(() -> {
                        // lateMs = 主线程消息队列延迟，正是 ANRWatchdog 量的东西。主线程若被
                        // 卡在等 DB 连接，这个 runnable 根本轮不上跑，lateMs 就等于卡住的时长。
                        long lateMs = SystemClock.elapsedRealtime() - dueAtMs;
                        long until = txnHeldUntilMs;
                        long t0 = System.nanoTime();
                        long rows = queryLong(db, "select count(*) from reminders where done=0");
                        Log.d(PERF_TAG, "[selftest] probe#" + seq
                                + " 主线程延迟=" + lateMs + "ms"
                                + " 查询耗时=" + (System.nanoTime() - t0) / 1_000_000 + "ms"
                                + " inWindow=" + (until != 0 && until > SystemClock.elapsedRealtime())
                                + " rows=" + rows);
                    }, SELF_TEST_PROBE_DELAY_MS * i);
                }

                db.beginTransaction();
                try {
                    queryLong(db, "select count(*) from reminders");
                    txnHeldUntilMs = SystemClock.elapsedRealtime() + SELF_TEST_TXN_HOLD_MS;
                    Log.d(PERF_TAG, "[selftest] bg txn ACQUIRED, holding " + SELF_TEST_TXN_HOLD_MS + "ms");
                    Thread.sleep(SELF_TEST_TXN_HOLD_MS);
                } finally {
                    db.endTransaction();
                    txnHeldUntilMs = 0;
                }
                Log.d(PERF_TAG, "[selftest] bg txn RELEASED");
            } catch (Exception e) {
                Log.e(PERF_TAG, "[selftest] error: " + e.getMessage());
            }
        }, "anrfix-selftest").start();
    }

    private static volatile long txnHeldUntilMs = 0;

    private static long queryLong(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) {
            return (cursor != null && cursor.moveToFirst()) ? cursor.getLong(0) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 仅 debug：持续采样连接池实际开了几条连接，只在刷新峰值时打日志。
     *
     * <p>SQLCipher 每条非主连接的 {@code DbStats.dbName} 会带 {@code (connectionId)} 后缀，
     * 按库路径前缀过滤即可数出本库的连接数。把 {@link #WAL_CONNECTION_POOL_SIZE} 临时开大
     * （如 16）跑一轮重负载，得到的峰值就是这个 App 真实需要的并发连接数 —— 池设成
     * 峰值 + 1 即可，再大只是白付 SQLCipher 每连接一次 PBKDF2 的开销和 checkpoint 饥饿风险。
     */
    private void debugPoolUsageSampler(String dbPath) {
        if (!DEBUG_POOL_USAGE_SAMPLER) return;
        new Thread(() -> {
            int peak = 0;
            while (true) {
                try {
                    Thread.sleep(POOL_SAMPLE_INTERVAL_MS);
                    int n = 0;
                    for (SQLiteDebug.DbStats s : SQLiteDebug.getDatabaseInfo().dbStats) {
                        if (s.dbName != null && s.dbName.startsWith(dbPath)) n++;
                    }
                    if (n > peak) {
                        peak = n;
                        Log.d(PERF_TAG, "[pool] 连接数峰值 -> " + peak
                                + " (上限 " + SQLiteGlobal.getWALConnectionPoolSize() + ")");
                    }
                } catch (Exception e) {
                    Log.e(PERF_TAG, "[pool] sampler stopped: " + e.getMessage());
                    return;
                }
            }
        }, "anrfix-poolsampler").start();
    }

    /**
     * 创建数据库实例
     *
     * @param context 上下文
     * @param _uid    用户ID
     * @return db
     */
    public synchronized static WKDBHelper getInstance(Context context, String _uid) {
        if (TextUtils.isEmpty(uid) || !uid.equals(_uid) || openHelper == null || openHelper.isClosed()) {
            synchronized (WKDBHelper.class) {
                if (openHelper != null) {
                    openHelper.close();
                    openHelper = null;
                }
                openHelper = new WKDBHelper(context, _uid);
            }
        }
        return openHelper;
    }

//    public static class DatabaseHelper extends SQLiteOpenHelper {
//        DatabaseHelper(Context context) {
//            super(context, myDBName, null, version);
//        }
//
//        @Override
//        public void onCreate(SQLiteDatabase db) {
//            // 在这里设置数据库密码
//            db.execSQL("PRAGMA key = '" + uid + "'");
//        }
//
//        @Override
//        public void onUpgrade(SQLiteDatabase db, int arg1, int arg2) {
//        }
//    }

    /**
     * 关闭数据库
     */
    public void close() {
        try {
            uid = "";
            if (mDb != null) {
                mDb.close();
                mDb = null;
            }
            myDBName = "";
//            if (mDbHelper != null) {
//                mDbHelper.close();
//                mDbHelper = null;
//            }
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG + " close WKDBHelper error");
        }
    }


    void insertSql(String tab, ContentValues cv) {
        SQLiteDatabase db = mDb;
        if (db == null || !db.isOpen()) {
            WKLoggerUtils.getInstance().e(TAG, "insertSql skipped: db unavailable, table=" + tab);
            return;
        }
        db.insertWithOnConflict(tab, "", cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public Cursor rawQuery(String sql) {
        SQLiteDatabase db = mDb;
        if (db == null || !db.isOpen()) {
            return null;
        }
        return db.rawQuery(sql, null);
    }

    public Cursor rawQuery(String sql, Object[] selectionArgs) {
        SQLiteDatabase db = mDb;
        if (db == null || !db.isOpen()) {
            return null;
        }
        return db.rawQuery(sql, selectionArgs);
    }

    public Cursor select(String table, String selection,
                         String[] selectionArgs,
                         String orderBy) {
        SQLiteDatabase db = mDb;
        if (db == null || !db.isOpen()) return null;
        Cursor cursor;
        try {
            cursor = db.query(table, null, selection, selectionArgs,
                    null, null, orderBy);
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG + " select WKDBHelper error");
            return null;
        }
        return cursor;
    }

    public long insert(String table, ContentValues cv) {
        SQLiteDatabase db = mDb;
        if (db == null || !db.isOpen()) {
            WKLoggerUtils.getInstance().e(TAG, "insert skipped: db unavailable, table=" + table);
            return 0;
        }
        long count = 0;
        try {
            count = db.insert(table, SQLiteDatabase.CONFLICT_REPLACE, cv);
        } catch (Exception e) {
            StringBuilder fields = new StringBuilder();
            for (Map.Entry<String, Object> item : cv.valueSet()) {
                if (!TextUtils.isEmpty(fields)) {
                    fields.append(",");
                }
                fields.append(item.getKey()).append(":").append(item.getValue());
            }
            WKLoggerUtils.getInstance().e(TAG, "Database insertion exception，Table：" + table + "，Fields：" + fields);
        }
        return count;
    }

    public boolean delete(String tableName, String where, String[] whereValue) {
        SQLiteDatabase db = mDb;
        if (db == null || !db.isOpen()) return false;
        int count = db.delete(tableName, where, whereValue);
        return count > 0;
    }

    public int update(String table, String[] updateFields,
                      String[] updateValues, String where, String[] whereValue) {
        SQLiteDatabase db = mDb;
        if (db == null || !db.isOpen()) return 0;
        ContentValues cv = new ContentValues();
        for (int i = 0; i < updateFields.length; i++) {
            cv.put(updateFields[i], updateValues[i]);
        }
        int count = 0;
        try {
            count = db.update(table, cv, where, whereValue);
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG, "update WKDBHelper error");
        }
        return count;
    }

    public boolean update(String tableName, ContentValues cv, String where,
                          String[] whereValue) {
        SQLiteDatabase db = mDb;
        if (db == null || !db.isOpen()) return false;
        boolean flag = false;
        try {
            flag = db.update(tableName, cv, where, whereValue) > 0;
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG, "update WKDBHelper error");
        }
        return flag;
    }

    public boolean update(String tableName, String whereClause,
                          ContentValues args) {
        SQLiteDatabase db = mDb;
        if (db == null || !db.isOpen()) return false;
        boolean flag = false;
        try {
            flag = db.update(tableName, args, whereClause, null) > 0;
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG + " update WKDBHelper error");
        }
        return flag;
    }

    // ==================== 事务安全方法 ====================

    public void beginTransaction() {
        SQLiteDatabase db = mDb;
        if (db != null && db.isOpen()) {
            db.beginTransaction();
        } else {
            WKLoggerUtils.getInstance().e(TAG, "beginTransaction skipped: db unavailable");
        }
    }

    public void setTransactionSuccessful() {
        SQLiteDatabase db = mDb;
        if (db != null && db.isOpen() && db.inTransaction()) db.setTransactionSuccessful();
    }

    public void endTransaction() {
        SQLiteDatabase db = mDb;
        if (db != null && db.isOpen() && db.inTransaction()) db.endTransaction();
    }

    public boolean inTransaction() {
        SQLiteDatabase db = mDb;
        return db != null && db.isOpen() && db.inTransaction();
    }

    public void execSQL(String sql) {
        SQLiteDatabase db = mDb;
        if (db != null && db.isOpen()) db.execSQL(sql);
    }

    public void execSQL(String sql, Object[] bindArgs) {
        SQLiteDatabase db = mDb;
        if (db != null && db.isOpen()) db.execSQL(sql, bindArgs);
    }

    // ==================== 异步查询方法 ====================
    
    /**
     * 异步执行原始 SQL 查询（推荐使用此方法避免 ANR）
     * 
     * @param sql SQL 语句
     * @param callback 查询回调
     * @param <T> 返回结果类型
     */
    public <T> void rawQueryAsync(String sql, QueryCallback<T> callback) {
        rawQueryAsync(sql, null, callback);
    }
    
    /**
     * 异步执行原始 SQL 查询（推荐使用此方法避免 ANR）
     * 
     * @param sql SQL 语句
     * @param selectionArgs 查询参数
     * @param callback 查询回调
     * @param <T> 返回结果类型
     */
    public <T> void rawQueryAsync(String sql, Object[] selectionArgs, QueryCallback<T> callback) {
        if (callback == null) {
            WKLoggerUtils.getInstance().e(TAG, "rawQueryAsync: callback is null");
            return;
        }
        
        dbExecutor.execute(() -> {
            Cursor cursor = null;
            T result = null;
            try {
                // 在后台线程执行查询
                cursor = rawQuery(sql, selectionArgs);
                // 让回调处理 Cursor 并返回结果
                result = callback.onQuery(cursor);
            } catch (Exception e) {
                WKLoggerUtils.getInstance().e(TAG, "rawQueryAsync error: " + e.getMessage());
            } finally {
                // 关闭 Cursor
                if (cursor != null) {
                    cursor.close();
                }
            }
            
            // 将结果回调到主线程
            final T finalResult = result;
            mainHandler.post(() -> {
                try {
                    callback.onResult(finalResult);
                } catch (Exception e) {
                    WKLoggerUtils.getInstance().e(TAG, "rawQueryAsync callback error: " + e.getMessage());
                }
            });
        });
    }
    
    /**
     * 异步执行 select 查询（推荐使用此方法避免 ANR）
     * 
     * @param table 表名
     * @param selection 查询条件
     * @param selectionArgs 查询参数
     * @param orderBy 排序
     * @param callback 查询回调
     * @param <T> 返回结果类型
     */
    public <T> void selectAsync(String table, String selection, String[] selectionArgs, 
                                String orderBy, QueryCallback<T> callback) {
        if (callback == null) {
            WKLoggerUtils.getInstance().e(TAG, "selectAsync: callback is null");
            return;
        }
        
        dbExecutor.execute(() -> {
            Cursor cursor = null;
            T result = null;
            try {
                // 在后台线程执行查询
                cursor = select(table, selection, selectionArgs, orderBy);
                // 让回调处理 Cursor 并返回结果
                result = callback.onQuery(cursor);
            } catch (Exception e) {
                WKLoggerUtils.getInstance().e(TAG, "selectAsync error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // 关闭 Cursor
                if (cursor != null) {
                    cursor.close();
                }
            }
            
            // 将结果回调到主线程
            final T finalResult = result;
            mainHandler.post(() -> {
                try {
                    callback.onResult(finalResult);
                } catch (Exception e) {
                    WKLoggerUtils.getInstance().e(TAG, "selectAsync callback error: " + e.getMessage());
                }
            });
        });
    }

}