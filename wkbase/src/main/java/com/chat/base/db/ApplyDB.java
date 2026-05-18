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

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import com.chat.base.WKBaseApplication;
import com.chat.base.entity.NewFriendEntity;
import com.chat.base.utils.WKLogUtils;
import com.chat.base.utils.WKReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 2019-12-05 15:21
 * 好友申请管理
 */
public class ApplyDB {

    private ApplyDB() {

    }

    private static class ApplyDbBinder {
        private final static ApplyDB applyDb = new ApplyDB();
    }

    final static String tableName = "apply_tab";

    public static ApplyDB getInstance() {
        return ApplyDbBinder.applyDb;
    }

    public List<NewFriendEntity> queryAll() {
        List<NewFriendEntity> list = new ArrayList<>();
        DBHelper helper = WKBaseApplication.getInstance().getDbHelper();
        if (helper == null || helper.isClosed()) return list;
        Cursor cursor = helper.rawQuery(
                "select * from " + tableName + " order by created_at desc", null);
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializeFriend(cursor));
        }
        cursor.close();
        return list;
    }

    public NewFriendEntity query(String applyUid) {
        NewFriendEntity newFriendEntity = null;
        DBHelper helper = WKBaseApplication.getInstance().getDbHelper();
        if (helper == null || helper.isClosed()) return null;
        Cursor cursor = helper.rawQuery(
                "select * from " + tableName + " where apply_uid=?", new String[]{applyUid});
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                newFriendEntity = serializeFriend(cursor);
            }
            cursor.close();
        }
        return newFriendEntity;
    }


    public synchronized long insert(NewFriendEntity friendEntity) {
        ContentValues cv = new ContentValues();
        try {
            cv = getContentValues(friendEntity);
        } catch (Exception e) {
            WKLogUtils.e("新增申请数据错误");
        }
        long result = -1;
        DBHelper helper = WKBaseApplication.getInstance().getDbHelper();
        if (helper == null || helper.isClosed()) return result;
        try {
            result = helper.insert(tableName, cv);
        } catch (Exception e) {
            Log.e("插入数据库异常：", Objects.requireNonNull(e.getMessage()));
        }
        return result;
    }

    /**
     * 批量保存
     *
     * @param list List<NewFriendEntity>
     */
    public synchronized void insert(List<NewFriendEntity> list) {
        if (WKReader.isEmpty(list)) return;
        DBHelper helper = WKBaseApplication.getInstance().getDbHelper();
        if (helper == null || helper.isClosed()) return;
        try {
            helper.beginTransaction();
            for (int i = 0; i < list.size(); i++) {
                insert(list.get(i));
            }
            helper.setTransactionSuccessful();
        } catch (Exception ignored) {
        } finally {
            helper.endTransaction();
        }
    }


    public boolean update(NewFriendEntity friendEntity) {
        String[] update = new String[1];
        update[0] = friendEntity.apply_uid;
        ContentValues cv = new ContentValues();
        try {
            cv = getContentValues(friendEntity);
        } catch (Exception e) {
            WKLogUtils.e("修改申请加好友数据错误");
        }
        DBHelper helper = WKBaseApplication.getInstance().getDbHelper();
        if (helper == null || helper.isClosed()) return false;
        return helper.update(tableName, cv, "apply_uid=?", update);
    }

    public void delete(String uid) {
        DBHelper helper = WKBaseApplication.getInstance().getDbHelper();
        if (helper == null || helper.isClosed()) return;
        String[] where = new String[1];
        where[0] = uid;
        helper.delete(tableName, "apply_uid=?", where);
    }

    @SuppressLint("Range")
    private NewFriendEntity serializeFriend(Cursor cursor) {
        NewFriendEntity friendEntity = new NewFriendEntity();
        friendEntity.apply_uid = cursor.getString(cursor.getColumnIndex("apply_uid"));
        friendEntity.apply_name = cursor.getString(cursor.getColumnIndex("apply_name"));
        friendEntity.token = cursor.getString(cursor.getColumnIndex("token"));
        friendEntity.status = cursor.getInt(cursor.getColumnIndex("status"));
        friendEntity.remark = cursor.getString(cursor.getColumnIndex("remark"));
        friendEntity.created_at = cursor.getString(cursor.getColumnIndex("created_at"));
        return friendEntity;
    }

    private ContentValues getContentValues(NewFriendEntity friendEntity) {
        ContentValues contentValues = new ContentValues();
        if (friendEntity == null) {
            return contentValues;
        }
        contentValues.put("apply_uid", friendEntity.apply_uid);
        contentValues.put("apply_name", friendEntity.apply_name);
        contentValues.put("token", friendEntity.token);
        contentValues.put("status", friendEntity.status);
        contentValues.put("remark", friendEntity.remark);
        contentValues.put("created_at", friendEntity.created_at);

        return contentValues;
    }

}
