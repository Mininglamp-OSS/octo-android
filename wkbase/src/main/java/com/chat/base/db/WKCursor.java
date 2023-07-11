package com.chat.base.db;

import android.annotation.SuppressLint;
import android.database.Cursor;

public class WKCursor {
    @SuppressLint("Range")
    public static String readString(Cursor cursor, String key) {
        if (cursor.getColumnIndex(key) >= 0)
            return cursor.getString(cursor.getColumnIndex(key));
        return "";
    }

    @SuppressLint("Range")
    public static int readInt(Cursor cursor, String key) {
        if (cursor.getColumnIndex(key) >= 0)
            return cursor.getInt(cursor.getColumnIndex(key));
        return 0;
    }

    @SuppressLint("Range")
    public static long readLong(Cursor cursor, String key) {
        if (cursor.getColumnIndex(key) >= 0)
            return cursor.getLong(cursor.getColumnIndex(key));
        return 0L;
    }
}
