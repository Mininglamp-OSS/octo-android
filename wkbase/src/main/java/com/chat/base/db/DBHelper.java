/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    public boolean isClosed() {
        return mDb == null || !mDb.isOpen();
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
        if (TextUtils.isEmpty(uid) || !uid.equals(_uid) || openHelper == null || openHelper.isClosed()) {
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
        if (mDb == null || !mDb.isOpen()) return null;
        return mDb.rawQuery(sql, null);
    }

    public Cursor rawQuery(String sql, String[] selectionArgs) {
        if (mDb == null || !mDb.isOpen()) return null;
        return mDb.rawQuery(sql, selectionArgs);
    }

    public Cursor select(String table, String selection,
                         String[] selectionArgs,
                         String orderBy) {
        if (mDb == null || !mDb.isOpen()) return null;
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
        if (mDb == null || !mDb.isOpen()) return -1;
        return mDb.insert(table, null, cv);
    }

    public long insertOrReplace(String table, ContentValues cv) {
        if (mDb == null || !mDb.isOpen()) return -1;
        return mDb.insertWithOnConflict(table, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public boolean update(String tableName, ContentValues cv, String where,
                          String[] whereValue) {
        if (mDb == null || !mDb.isOpen()) return false;
        boolean flag = false;
        try {
            flag = mDb.update(tableName, cv, where, whereValue) > 0;
        } catch (Exception e) {
            WKLogUtils.e("执行修改操作错误");
        }
        return flag;
    }

    public boolean delete(String tableName, String where, String[] whereValue) {
        if (mDb == null || !mDb.isOpen()) return false;
        int count = mDb.delete(tableName, where, whereValue);
        return count > 0;
    }

    // ==================== 事务安全方法 ====================

    public void beginTransaction() {
        if (mDb != null && mDb.isOpen()) mDb.beginTransaction();
    }

    public void setTransactionSuccessful() {
        if (mDb != null && mDb.isOpen() && mDb.inTransaction()) mDb.setTransactionSuccessful();
    }

    public void endTransaction() {
        if (mDb != null && mDb.isOpen() && mDb.inTransaction()) mDb.endTransaction();
    }

    public boolean inTransaction() {
        return mDb != null && mDb.isOpen() && mDb.inTransaction();
    }
}
