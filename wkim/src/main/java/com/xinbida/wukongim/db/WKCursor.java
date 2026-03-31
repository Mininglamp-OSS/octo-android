package com.xinbida.wukongim.db;

import android.database.Cursor;

public class WKCursor {
    public static String readString(Cursor cursor, String key) {
        try {
            int index = cursor.getColumnIndexOrThrow(key);
            return cursor.getString(index);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    public static int readInt(Cursor cursor, String key) {
        try {
            return cursor.getInt(cursor.getColumnIndexOrThrow(key));
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    public static long readLong(Cursor cursor, String key) {
        try {
            return cursor.getLong(cursor.getColumnIndexOrThrow(key));
        } catch (IllegalArgumentException e) {
            return 0L;
        }

    }

    public static byte readByte(Cursor cursor, String key) {
        try {
            int v = cursor.getInt(cursor.getColumnIndexOrThrow(key));
            return (byte) v;
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    public static byte[] readBlob(Cursor cursor, String key) {
        try {
            return cursor.getBlob(cursor.getColumnIndexOrThrow(key));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String getPlaceholders(int count) {
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i != 0) {
                placeholders.append(", ");
            }
            placeholders.append("?");
        }
        return placeholders.toString();
    }
}
