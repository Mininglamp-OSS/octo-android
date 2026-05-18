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

package com.chat.uikit.db;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;

import com.chat.base.WKBaseApplication;
import com.chat.base.db.DBHelper;
import com.chat.base.db.WKCursor;
import com.chat.base.utils.WKReader;
import com.chat.uikit.enity.MailListEntity;

import java.util.ArrayList;
import java.util.List;

public class WKContactsDB {
    private WKContactsDB() {

    }

    private static class ContactsDBBinder {
        static WKContactsDB db = new WKContactsDB();
    }

    public static WKContactsDB getInstance() {
        return ContactsDBBinder.db;
    }

    public List<MailListEntity> query() {
        List<MailListEntity> list = new ArrayList<>();
        DBHelper helper = WKBaseApplication.getInstance().getDbHelper();
        if (helper == null || helper.isClosed()) return list;
        Cursor cursor = helper.rawQuery(
                "select * from user_contact", null);
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serialize(cursor));
        }
        cursor.close();
        return list;
    }

    @SuppressLint("Range")
    private MailListEntity serialize(Cursor cursor) {
        MailListEntity entity = new MailListEntity();
        entity.phone = WKCursor.readString(cursor, "phone");
        entity.zone = WKCursor.readString(cursor, "zone");
        entity.name = WKCursor.readString(cursor, "name");
        entity.uid = WKCursor.readString(cursor, "uid");
        entity.vercode = WKCursor.readString(cursor, "vercode");
        entity.is_friend = WKCursor.readInt(cursor, "is_friend");
        return entity;
    }

    public void save(List<MailListEntity> list) {
        if (WKReader.isEmpty(list)) return;
        DBHelper helper = WKBaseApplication.getInstance().getDbHelper();
        if (helper == null || helper.isClosed()) return;
        try {
            helper.beginTransaction();
            for (int i = 0, size = list.size(); i < size; i++) {
                boolean isAdd = true;
                if (isExist(list.get(i))) {
                    isAdd = delete(list.get(i));
                }
                if (isAdd)
                    insert(list.get(i));
            }
            helper.setTransactionSuccessful();
        } finally {
            helper.endTransaction();
        }
    }

    private boolean delete(MailListEntity entity) {
        DBHelper helper = WKBaseApplication.getInstance().getDbHelper();
        if (helper == null || helper.isClosed()) return false;
        String[] strings = new String[2];
        strings[0] = entity.phone;
        strings[1] = entity.name;
        return helper.delete("user_contact", "phone=? and name=?", strings);
    }

    public void updateFriendStatus(String uid, int isFriend) {
        DBHelper helper = WKBaseApplication.getInstance().getDbHelper();
        if (helper == null || helper.isClosed()) return;
        ContentValues contentValues = new ContentValues();
        contentValues.put("is_friend", isFriend);
        String[] strings = new String[1];
        strings[0] = uid;
        helper.update("user_contact", contentValues, "uid=?", strings);
    }

    private void insert(MailListEntity entity) {
        DBHelper helper = WKBaseApplication.getInstance().getDbHelper();
        if (helper == null || helper.isClosed()) return;
        ContentValues contentValues = new ContentValues();
        contentValues.put("phone", entity.phone);
        contentValues.put("uid", entity.uid);
        contentValues.put("zone", entity.zone);
        contentValues.put("name", entity.name);
        contentValues.put("vercode", entity.vercode);
        contentValues.put("is_friend", entity.is_friend);
        helper.insert("user_contact", contentValues);
    }

    private boolean isExist(MailListEntity entity) {
        DBHelper helper = WKBaseApplication.getInstance().getDbHelper();
        if (helper == null || helper.isClosed()) return false;
        boolean isExist = false;
        Cursor cursor = helper.rawQuery("select 1 from user_contact where phone=? and name=? limit 1", new String[]{entity.phone, entity.name});
        if (cursor != null && cursor.moveToNext()) {
            isExist = true;
        }
        if (cursor != null)
            cursor.close();
        return isExist;
    }
}
