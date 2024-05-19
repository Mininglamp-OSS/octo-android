package com.chat.base.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import com.chat.base.utils.WKLogUtils;


/**
 * 2019-12-05 14:42
 * 数据库辅助类
 */
public class DBHelper {
    private volatile static DBHelper openHelper = null;
    private static String myDBName;
    private final static int version = 1;
    private static String uid;
    private DBHelper.DatabaseHelper mDbHelper;
    private SQLiteDatabase mDb;

    public SQLiteDatabase getDB() {
        return mDb;
    }

    private DBHelper(Context ctx, String uid) {
        DBHelper.uid = uid;
        myDBName = uid + ".db";

        try {
            mDbHelper = new DBHelper.DatabaseHelper(ctx);
            mDb = mDbHelper.getWritableDatabase();
            onUpgrade();
        } catch (Exception e) {
            WKLogUtils.e("初始化db错误");
        }
    }

    public synchronized void onUpgrade() {
        if (mDb != null)
            WKBaseDBManager.getInstance().onUpgrade(mDb);
    }

    public static DBHelper getInstance(Context context, String _uid) {
        if (TextUtils.isEmpty(uid) || !uid.equals(_uid) || openHelper == null) {
            synchronized (DBHelper.class) {
                if (openHelper != null) {
                    openHelper.close();
                    openHelper = null;
                }
                openHelper = new DBHelper(context, _uid);
            }
        }
        return openHelper;
    }

    public static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context) {
            super(context, myDBName, null, version);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int arg1, int arg2) {
        }
    }

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

            if (mDbHelper != null) {
                mDbHelper.close();
                mDbHelper = null;
            }

        } catch (Exception e) {
            WKLogUtils.e("关闭db错误");
        }
    }

    public Cursor rawQuery(String sql) {
        return mDb.rawQuery(sql, null);
    }

    public Cursor rawQuery(String sql, String[] selectionArgs) {
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
            WKLogUtils.e("执行查询操作错误");
            return null;
        }
        return cursor;
    }

    public long insert(String table, ContentValues cv) {
        return mDb.insert(table, null, cv);
    }

    public long insertOrReplace(String table, ContentValues cv) {
        return mDb.insertWithOnConflict(table, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public boolean update(String tableName, ContentValues cv, String where,
                          String[] whereValue) {
        boolean flag = false;
        try {
            flag = mDb.update(tableName, cv, where, whereValue) > 0;
        } catch (Exception e) {
            WKLogUtils.e("执行修改操作错误");
        }
        return flag;
    }

    public boolean delete(String tableName, String where, String[] whereValue) {
        int count = mDb.delete(tableName, where, whereValue);
        return count > 0;
    }
}
