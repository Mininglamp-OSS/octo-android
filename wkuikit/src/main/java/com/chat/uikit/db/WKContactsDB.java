package com.chat.uikit.db;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;

import com.chat.base.WKBaseApplication;
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
        Cursor cursor = WKBaseApplication
                .getInstance()
                .getDbHelper()
                .rawQuery(
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
        try {
            WKBaseApplication.getInstance().getDbHelper().getDB().beginTransaction();
            for (int i = 0, size = list.size(); i < size; i++) {
                boolean isAdd = true;
                if (isExist(list.get(i))) {
                    isAdd = delete(list.get(i));
                }
                if (isAdd)
                    insert(list.get(i));
            }
            WKBaseApplication.getInstance().getDbHelper().getDB().setTransactionSuccessful();
        } finally {
            WKBaseApplication.getInstance().getDbHelper().getDB().endTransaction();
        }
    }

    private boolean delete(MailListEntity entity) {
        String[] strings = new String[2];
        strings[0] = entity.phone;
        strings[1] = entity.name;
        return WKBaseApplication.getInstance().getDbHelper().delete("user_contact", "phone=? and name=?", strings);
    }

    public void updateFriendStatus(String uid, int isFriend) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("is_friend", isFriend);
        String[] strings = new String[1];
        strings[0] = uid;
        WKBaseApplication.getInstance().getDbHelper().update("user_contact", contentValues, "uid=?", strings);
    }

    private void insert(MailListEntity entity) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("phone", entity.phone);
        contentValues.put("uid", entity.uid);
        contentValues.put("zone", entity.zone);
        contentValues.put("name", entity.name);
        contentValues.put("vercode", entity.vercode);
        contentValues.put("is_friend", entity.is_friend);
        WKBaseApplication.getInstance().getDbHelper().insert("user_contact", contentValues);
    }

    private boolean isExist(MailListEntity entity) {
        boolean isExist = false;
        String sql = "select * from user_contact where phone=" + "\"" + entity.phone + "\"" + " and name=" + "\"" + entity.name + "\"";
        Cursor cursor = WKBaseApplication.getInstance().getDbHelper().rawQuery(sql, null);
        if (cursor != null && cursor.moveToNext()) {
            isExist = true;
        }
        if (cursor != null)
            cursor.close();
        return isExist;
    }
}
