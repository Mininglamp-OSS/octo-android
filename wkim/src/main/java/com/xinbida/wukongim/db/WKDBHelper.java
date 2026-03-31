package com.xinbida.wukongim.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.xinbida.wukongim.utils.WKLoggerUtils;

import net.zetetic.database.sqlcipher.SQLiteDatabase;
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
//    private DatabaseHelper mDbHelper;
    private SQLiteDatabase mDb;
    
    // 数据库操作线程池（单线程，保证数据库操作的顺序性）
    private static final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    // 主线程 Handler，用于回调
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public SQLiteDatabase getDb() {
        return mDb;
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
            mDb = SQLiteDatabase.openOrCreateDatabase(databaseFile, uid, null, null, null);
            WKDBUpgrade.getInstance().onUpgrade(mDb);
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG + " init WKDBHelper error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 创建数据库实例
     *
     * @param context 上下文
     * @param _uid    用户ID
     * @return db
     */
    public synchronized static WKDBHelper getInstance(Context context, String _uid) {
        if (TextUtils.isEmpty(uid) || !uid.equals(_uid) || openHelper == null) {
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
        if (mDb == null) {
            return;
        }
        mDb.insertWithOnConflict(tab, "", cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public Cursor rawQuery(String sql) {
        if (mDb == null) {
            return null;
        }
        return mDb.rawQuery(sql, null);
    }

    public Cursor rawQuery(String sql, Object[] selectionArgs) {
        if (mDb == null) {
            return null;
        }
        return mDb.rawQuery(sql, selectionArgs);
    }

    public Cursor select(String table, String selection,
                         String[] selectionArgs,
                         String orderBy) {
        if (mDb == null) return null;
        Cursor cursor;
        try {
            cursor = mDb.query(table, null, selection, selectionArgs,
                    null, null, orderBy);
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG + " select WKDBHelper error");
            return null;
        }
        return cursor;
    }

    public long insert(String table, ContentValues cv) {
        if (mDb == null) return 0;
        long count = 0;
        try {
            count = mDb.insert(table, SQLiteDatabase.CONFLICT_REPLACE, cv);
//            count = mDb.insert(table, null, cv);
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
        if (mDb == null) return false;
        int count = mDb.delete(tableName, where, whereValue);
        return count > 0;
    }

    public int update(String table, String[] updateFields,
                      String[] updateValues, String where, String[] whereValue) {
        if (mDb == null) return 0;
        ContentValues cv = new ContentValues();
        for (int i = 0; i < updateFields.length; i++) {
            cv.put(updateFields[i], updateValues[i]);
        }
        int count = 0;
        try {
            count = mDb.update(table, cv, where, whereValue);
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG, "update WKDBHelper error");
        }
        return count;
    }

    public boolean update(String tableName, ContentValues cv, String where,
                          String[] whereValue) {
        if (mDb == null) return false;
        boolean flag = false;
        try {
            flag = mDb.update(tableName, cv, where, whereValue) > 0;
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG, "update WKDBHelper error");
        }
        return flag;
    }

    public boolean update(String tableName, String whereClause,
                          ContentValues args) {
        if (mDb == null) return false;
        boolean flag = false;
        try {
            flag = mDb.update(tableName, args, whereClause, null) > 0;
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG + " update WKDBHelper error");
        }
        return flag;
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