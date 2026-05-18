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
