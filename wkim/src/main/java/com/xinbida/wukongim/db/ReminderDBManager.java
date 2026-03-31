package com.xinbida.wukongim.db;

import static com.xinbida.wukongim.db.WKDBColumns.TABLE.reminders;

import android.content.ContentValues;
import android.database.Cursor;
import android.text.TextUtils;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.entity.WKReminder;
import com.xinbida.wukongim.entity.WKUIConversationMsg;
import com.xinbida.wukongim.interfaces.IReminderResult;
import com.xinbida.wukongim.utils.WKLoggerUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class ReminderDBManager {
    private static final String TAG = "ReminderDBManager";

    private ReminderDBManager() {
    }

    private static class ReminderDBManagerBinder {
        final static ReminderDBManager binder = new ReminderDBManager();
    }

    public static ReminderDBManager getInstance() {
        return ReminderDBManagerBinder.binder;
    }

    public void doneWithReminderIds(List<Long> ids) {
        ContentValues cv = new ContentValues();
        cv.put("done", 1);
        String[] strings = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            strings[i] = ids.get(i) + "";
        }
        WKIMApplication.getInstance().getDbHelper().update(reminders, cv, "reminder_id in (" + WKCursor.getPlaceholders(ids.size()) + ")", strings);
    }

    public long queryMaxVersion() {
        String sql = "select * from " + reminders + " order by version desc limit 1";
        long version = 0;
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql)) {
            if (cursor == null) {
                return 0;
            }
            if (cursor.moveToLast()) {
                version = WKCursor.readLong(cursor, "version");
            }
        }
        return version;
    }

    /**
     * 同步查询（仅供后台线程使用）
     *
     * @deprecated 建议使用 queryWithChannelAndDoneAsync 避免 ANR
     */
    @Deprecated
    public List<WKReminder> queryWithChannelAndDone(String channelID, byte channelType, int done) {
        String sql = "select * from " + reminders + " where channel_id=? and channel_type=? and done=? order by message_seq desc";
        List<WKReminder> list = new ArrayList<>();
        try (Cursor cursor = WKIMApplication.getInstance().getDbHelper().rawQuery(sql, new Object[]{channelID, channelType, done})) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                WKReminder reminder = serializeReminder(cursor);
                list.add(reminder);
            }
        }
        return list;
    }

    /**
     * 异步查询（推荐使用，避免 ANR）
     */
    public void queryWithChannelAndDoneAsync(String channelID, byte channelType, int done, IReminderResult callback) {
        String sql = "select * from " + reminders + " where channel_id=? and channel_type=? and done=? order by message_seq desc";
        WKIMApplication.getInstance().getDbHelper().rawQueryAsync(sql, new Object[]{channelID, channelType, done},
                new WKDBHelper.QueryCallback<List<WKReminder>>() {
                    @Override
                    public List<WKReminder> onQuery(Cursor cursor) {
                        // 在后台线程处理 Cursor
                        List<WKReminder> list = new ArrayList<>();
                        if (cursor == null) {
                            return list;
                        }
                        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                            WKReminder reminder = serializeReminder(cursor);
                            list.add(reminder);
                        }
                        return list;
                    }

                    @Override
                    public void onResult(List<WKReminder> result) {
                        // 在主线程回调结果
                        if (callback != null) {
                            callback.onResult(result);
                        }
                    }
                });
    }


    public List<WKReminder> queryWithChannelAndTypeAndDone(String channelID, byte channelType, int type, int done) {
        String sql = "select * from " + reminders + " where channel_id=? and channel_type=? and done=? and type =? order by message_seq desc";
        List<WKReminder> list = new ArrayList<>();
        try (Cursor cursor = WKIMApplication.getInstance().getDbHelper().rawQuery(sql, new Object[]{channelID, channelType, done, type})) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                WKReminder reminder = serializeReminder(cursor);
                list.add(reminder);
            }
        }
        return list;
    }

    private List<WKReminder> queryWithIds(List<Long> ids) {
        StringBuilder stringBuffer = new StringBuilder();
        for (int i = 0, size = ids.size(); i < size; i++) {
            if (!TextUtils.isEmpty(stringBuffer)) {
                stringBuffer.append(",");
            }
            stringBuffer.append(ids.get(i));
        }
        String sql = "select * from " + reminders + " where reminder_id in (" + stringBuffer + ")";
        List<WKReminder> list = new ArrayList<>();
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper()
                .rawQuery(sql)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                WKReminder reminder = serializeReminder(cursor);
                list.add(reminder);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private List<WKReminder> queryWithChannelIds(List<String> channelIds) {
        List<WKReminder> list = new ArrayList<>();
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper()
                .select(reminders, "channel_id in (" + WKCursor.getPlaceholders(channelIds.size()) + ")", channelIds.toArray(new String[0]), null)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                WKReminder reminder = serializeReminder(cursor);
                list.add(reminder);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public List<WKReminder> insertOrUpdateReminders(List<WKReminder> list) {
        List<Long> ids = new ArrayList<>();
        List<String> channelIds = new ArrayList<>();
        for (int i = 0, size = list.size(); i < size; i++) {
            boolean isAdd = true;
            for (String channelId : channelIds) {
                if (!TextUtils.isEmpty(list.get(i).channelID) && channelId.equals(list.get(i).channelID)) {
                    isAdd = false;
                    break;
                }
            }
            if (isAdd) channelIds.add(list.get(i).channelID);
            ids.add(list.get(i).reminderID);

        }
        List<ContentValues> insertCVs = new ArrayList<>();
        List<ContentValues> updateCVs = new ArrayList<>();
        List<WKReminder> allList = queryWithIds(ids);
        for (int i = 0, size = list.size(); i < size; i++) {
            boolean isAdd = true;
            for (WKReminder reminder : allList) {
                if (reminder.reminderID == list.get(i).reminderID) {
                    updateCVs.add(WKSqlContentValues.getCVWithReminder(list.get(i)));
                    isAdd = false;
                    break;
                }
            }
            if (isAdd) {
                insertCVs.add(WKSqlContentValues.getCVWithReminder(list.get(i)));
            }
        }
        try {
            WKIMApplication.getInstance().getDbHelper().getDb()
                    .beginTransaction();
            if (!insertCVs.isEmpty()) {
                for (ContentValues cv : insertCVs) {
                    WKIMApplication.getInstance().getDbHelper().insert(reminders, cv);
                }
            }
            if (!updateCVs.isEmpty()) {
                for (ContentValues cv : updateCVs) {
                    String[] update = new String[1];
                    update[0] = cv.getAsString("reminder_id");
                    WKIMApplication.getInstance().getDbHelper()
                            .update(reminders, cv, "reminder_id=?", update);
                }
            }
            WKIMApplication.getInstance().getDbHelper().getDb()
                    .setTransactionSuccessful();
        } catch (Exception ignored) {
        } finally {
            if (WKIMApplication.getInstance().getDbHelper().getDb().inTransaction()) {
                WKIMApplication.getInstance().getDbHelper().getDb()
                        .endTransaction();
            }
        }

        List<WKReminder> reminderList = queryWithChannelIds(channelIds);
        HashMap<String, List<WKReminder>> maps = listToMap(reminderList);
        List<WKUIConversationMsg> uiMsgList = ConversationDbManager.getInstance().queryWithChannelIds(channelIds);
        for (int i = 0, size = uiMsgList.size(); i < size; i++) {
            String key = uiMsgList.get(i).channelID + "_" + uiMsgList.get(i).channelType;
            if (maps.containsKey(key)) {
                uiMsgList.get(i).setReminderList(maps.get(key));
            }
            // WKIM.getInstance().getConversationManager().setOnRefreshMsg(uiMsgList.get(i), i == list.size() - 1, "saveReminders");
        }
        WKIM.getInstance().getConversationManager().setOnRefreshMsg(uiMsgList, "saveReminders");
        return reminderList;
    }

    private HashMap<String, List<WKReminder>> listToMap(List<WKReminder> list) {
        HashMap<String, List<WKReminder>> map = new HashMap<>();
        if (list == null || list.isEmpty()) {
            return map;
        }
        for (WKReminder reminder : list) {
            String key = reminder.channelID + "_" + reminder.channelType;
            List<WKReminder> tempList = null;
            if (map.containsKey(key)) {
                tempList = map.get(key);
            }
            if (tempList == null) tempList = new ArrayList<>();
            tempList.add(reminder);
            map.put(key, tempList);
        }
        return map;
    }

    private WKReminder serializeReminder(Cursor cursor) {
        WKReminder reminder = new WKReminder();
        reminder.type = WKCursor.readInt(cursor, "type");
        reminder.reminderID = WKCursor.readLong(cursor, "reminder_id");
        reminder.messageID = WKCursor.readString(cursor, "message_id");
        reminder.messageSeq = WKCursor.readLong(cursor, "message_seq");
        reminder.isLocate = WKCursor.readInt(cursor, "is_locate");
        reminder.channelID = WKCursor.readString(cursor, "channel_id");
        reminder.channelType = (byte) WKCursor.readInt(cursor, "channel_type");
        reminder.text = WKCursor.readString(cursor, "text");
        reminder.version = WKCursor.readLong(cursor, "version");
        reminder.done = WKCursor.readInt(cursor, "done");
        String data = WKCursor.readString(cursor, "data");
        reminder.needUpload = WKCursor.readInt(cursor, "need_upload");
        reminder.publisher = WKCursor.readString(cursor, "publisher");
        if (!TextUtils.isEmpty(data)) {
            HashMap<String, Object> hashMap = new HashMap<>();
            try {
                JSONObject jsonObject = new JSONObject(data);
                Iterator<String> keys = jsonObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    hashMap.put(key, jsonObject.opt(key));
                }
            } catch (JSONException e) {
                WKLoggerUtils.getInstance().e(TAG, "serializeReminder error");
            }
            reminder.data = hashMap;
        }
        return reminder;
    }
}
