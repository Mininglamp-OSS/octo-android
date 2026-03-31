package com.xinbida.wukongim.db;

import static com.xinbida.wukongim.db.WKDBColumns.TABLE.channel;
import static com.xinbida.wukongim.db.WKDBColumns.TABLE.conversation;
import static com.xinbida.wukongim.db.WKDBColumns.TABLE.conversationExtra;
import static com.xinbida.wukongim.db.WKDBColumns.TABLE.message;

import android.content.ContentValues;
import android.database.Cursor;
import android.text.TextUtils;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKConversationMsg;
import com.xinbida.wukongim.entity.WKConversationMsgExtra;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKMsgExtra;
import com.xinbida.wukongim.entity.WKUIConversationMsg;
import com.xinbida.wukongim.manager.ConversationManager;
import com.xinbida.wukongim.utils.WKCommonUtils;
import com.xinbida.wukongim.utils.WKLoggerUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * 5/21/21 12:14 PM
 * 最近会话
 */
public class ConversationDbManager {
    private final String TAG = "ConversationDbManager";
    private final String extraCols = "IFNULL(" + conversationExtra + ".browse_to,0) AS browse_to,IFNULL(" + conversationExtra + ".keep_message_seq,0) AS keep_message_seq,IFNULL(" + conversationExtra + ".keep_offset_y,0) AS keep_offset_y,IFNULL(" + conversationExtra + ".draft,'') AS draft,IFNULL(" + conversationExtra + ".version,0) AS extra_version";
    private final String channelCols = channel + ".channel_remark," +
            channel + ".channel_name," +
            channel + ".top," +
            channel + ".mute," +
            channel + ".save," +
            channel + ".status as channel_status," +
            channel + ".forbidden," +
            channel + ".invite," +
            channel + ".follow," +
            channel + ".is_deleted as channel_is_deleted," +
            channel + ".show_nick," +
            channel + ".avatar," +
            channel + ".avatar_cache_key," +
            channel + ".online," +
            channel + ".last_offline," +
            channel + ".category," +
            channel + ".receipt," +
            channel + ".robot," +
            channel + ".parent_channel_id AS c_parent_channel_id," +
            channel + ".parent_channel_type AS c_parent_channel_type," +
            channel + ".version AS channel_version," +
            channel + ".remote_extra AS channel_remote_extra," +
            channel + ".extra AS channel_extra";

    private ConversationDbManager() {
    }

    private static class ConversationDbManagerBinder {
        static final ConversationDbManager db = new ConversationDbManager();
    }

    public static ConversationDbManager getInstance() {
        return ConversationDbManagerBinder.db;
    }

    public synchronized List<WKUIConversationMsg> queryAll() {
        List<WKUIConversationMsg> list = new ArrayList<>();
        if (WKIMApplication.getInstance().getDbHelper() == null || WKIMApplication.getInstance().getDbHelper().getDb() == null) {
            return list;
        }

        String sql = "SELECT " + conversation + ".*," + channelCols + "," + extraCols + " FROM "
                + conversation + " LEFT JOIN " + channel + " ON "
                + conversation + ".channel_id = " + channel + ".channel_id AND "
                + conversation + ".channel_type = " + channel + ".channel_type LEFT JOIN " + conversationExtra + " ON " + conversation + ".channel_id=" + conversationExtra + ".channel_id AND " + conversation + ".channel_type=" + conversationExtra + ".channel_type where " + conversation + ".is_deleted=0 order by "
                + WKDBColumns.WKCoverMessageColumns.last_msg_timestamp + " desc";
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper()
                .rawQuery(sql)) {
            if (cursor == null) {
                return list;
            }
            List<String> clientMsgNos = new ArrayList<>();
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                WKConversationMsg msg = serializeMsg(cursor);
                if (msg.isDeleted == 0) {
                    WKUIConversationMsg uiMsg = getUIMsg(msg, cursor);
                    list.add(uiMsg);
                    clientMsgNos.add(uiMsg.clientMsgNo);
                }
            }
            if (!clientMsgNos.isEmpty()) {
                List<WKMsg> msgList = queryWithClientMsgNos(clientMsgNos);
                List<String> msgIds = new ArrayList<>();
                if (WKCommonUtils.isNotEmpty(msgList)) {
                    for (WKUIConversationMsg uiMsg : list) {
                        for (WKMsg msg : msgList) {
                            if (uiMsg.clientMsgNo.equals(msg.clientMsgNO)) {
                                uiMsg.setWkMsg(msg);
                                if (!TextUtils.isEmpty(msg.messageID)) {
                                    msgIds.add(msg.messageID);
                                }
                                break;
                            }
                        }
                    }
                }
                List<WKMsgExtra> extraList = queryWithMsgIds(msgIds);
                if (WKCommonUtils.isNotEmpty(extraList)) {
                    for (WKUIConversationMsg uiMsg : list) {
                        for (WKMsgExtra extra : extraList) {
                            if (uiMsg.getWkMsg() != null && !TextUtils.isEmpty(uiMsg.getWkMsg().messageID) && uiMsg.getWkMsg().messageID.equals(extra.messageID)) {
                                uiMsg.getWkMsg().remoteExtra = extra;
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG, "queryAll error");
        }
        return list;
    }

    private List<WKMsgExtra> queryWithMsgIds(List<String> msgIds) {
        List<WKMsgExtra> msgExtraList = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0, size = msgIds.size(); i < size; i++) {
            if (ids.size() == 200) {
                List<WKMsgExtra> list = MsgDbManager.getInstance().queryMsgExtrasWithMsgIds(ids);
                if (WKCommonUtils.isNotEmpty(list)) {
                    msgExtraList.addAll(list);
                }
                ids.clear();
            }
            ids.add(msgIds.get(i));
        }
        return msgExtraList;
    }

    private List<WKMsg> queryWithClientMsgNos(List<String> clientMsgNos) {
        List<WKMsg> msgList = new ArrayList<>();
        List<String> nos = new ArrayList<>();
        for (int i = 0, size = clientMsgNos.size(); i < size; i++) {
            if (nos.size() == 200) {
                List<WKMsg> list = MsgDbManager.getInstance().queryWithClientMsgNos(nos);
                if (WKCommonUtils.isNotEmpty(list)) {
                    msgList.addAll(list);
                }
                nos.clear();
            }
            nos.add(clientMsgNos.get(i));
        }
        if (!nos.isEmpty()) {
            List<WKMsg> list = MsgDbManager.getInstance().queryWithClientMsgNos(nos);
            if (WKCommonUtils.isNotEmpty(list)) {
                msgList.addAll(list);
            }
            nos.clear();
        }
        return msgList;
    }

    public List<WKUIConversationMsg> queryWithChannelIds(List<String> channelIds) {
        String sql = "select " + conversation + ".*," + channelCols + "," + extraCols + " from " + conversation + " left join " + channel + " on " + conversation + ".channel_id=" + channel + ".channel_id and " + conversation + ".channel_type=" + channel + ".channel_type left join " + conversationExtra + " on " + conversation + ".channel_id=" + conversationExtra + ".channel_id and " + conversation + ".channel_type=" + conversationExtra + ".channel_type where " + conversation + ".is_deleted=0 and " + conversation + ".channel_id in (" + WKCursor.getPlaceholders(channelIds.size()) + ")";
        List<WKUIConversationMsg> list = new ArrayList<>();
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper()
                .rawQuery(sql, channelIds.toArray(new String[0]))) {
            if (cursor == null) {
                return list;
            }

            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                WKConversationMsg msg = serializeMsg(cursor);
                WKUIConversationMsg uiMsg = getUIMsg(msg, cursor);
                list.add(uiMsg);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public List<WKConversationMsg> queryWithChannelType(byte channelType) {
        List<WKConversationMsg> list = new ArrayList<>();
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper()
                .select(conversation, "channel_type=?", new String[]{String.valueOf(channelType)}, null)) {
            if (cursor == null) {
                return list;
            }

            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                WKConversationMsg msg = serializeMsg(cursor);
                list.add(msg);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private WKUIConversationMsg getUIMsg(WKConversationMsg msg, Cursor cursor) {
        WKUIConversationMsg uiMsg = getUIMsg(msg);
        WKChannel channel = ChannelDBManager.getInstance().serializableChannel(cursor);
        if (channel != null) {
            String extra = WKCursor.readString(cursor, "channel_extra");
            channel.localExtra = WKCommonUtils.str2HashMap(extra);
            String remoteExtra = WKCursor.readString(cursor, "channel_remote_extra");
            channel.remoteExtraMap = WKCommonUtils.str2HashMap(remoteExtra);
            channel.status = WKCursor.readInt(cursor, "channel_status");
            channel.version = WKCursor.readLong(cursor, "channel_version");
            channel.parentChannelID = WKCursor.readString(cursor, "c_parent_channel_id");
            channel.parentChannelType = WKCursor.readByte(cursor, "c_parent_channel_type");
            channel.channelID = msg.channelID;
            channel.channelType = msg.channelType;
            uiMsg.setWkChannel(channel);
        }
        return uiMsg;
    }

    public long queryMaxVersion() {
        long maxVersion = 0;
        String sql = "select max(version) version from " + conversation + " limit 0, 1";
        Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper()
                .rawQuery(sql);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                maxVersion = WKCursor.readLong(cursor, WKDBColumns.WKCoverMessageColumns.version);
            }
            cursor.close();
        }
        return maxVersion;
    }

    public synchronized ContentValues getInsertSyncCV(WKConversationMsg conversationMsg) {
        return WKSqlContentValues.getContentValuesWithCoverMsg(conversationMsg, true);
    }

    public synchronized void insertSyncMsg(ContentValues cv) {
        WKIMApplication.getInstance().getDbHelper().insertSql(conversation, cv);
    }

    public synchronized String queryLastMsgSeqs() {
        String lastMsgSeqs = "";
        String sql = "select GROUP_CONCAT(channel_id||':'||channel_type||':'|| last_seq,'|') synckey from (select *,(select max(message_seq) from " + message + " where " + message + ".channel_id=" + conversation + ".channel_id and " + message + ".channel_type=" + conversation + ".channel_type limit 1) last_seq from " + conversation + ") cn where channel_id<>'' AND is_deleted=0";
        Cursor cursor = WKIMApplication.getInstance().getDbHelper().rawQuery(sql);
        if (cursor == null) {
            return lastMsgSeqs;
        }
        if (cursor.moveToFirst()) {
            lastMsgSeqs = WKCursor.readString(cursor, "synckey");
        }
        cursor.close();

        return lastMsgSeqs;
    }

    public synchronized boolean updateRedDot(String channelID, byte channelType, int redDot) {
        if (WKIMApplication.getInstance().getDbHelper() == null || WKIMApplication.getInstance().getDbHelper().getDb() == null) {
            return false;
        }
        ContentValues cv = new ContentValues();
        cv.put(WKDBColumns.WKCoverMessageColumns.unread_count, redDot);
        return WKIMApplication.getInstance().getDbHelper().update(conversation, WKDBColumns.WKCoverMessageColumns.channel_id + "='" + channelID + "' and " + WKDBColumns.WKCoverMessageColumns.channel_type + "=" + channelType, cv);
    }

    public synchronized void updateMsg(String channelID, byte channelType, String clientMsgNo, long lastMsgSeq, int count) {
        String[] update = new String[2];
        update[0] = channelID;
        update[1] = String.valueOf(channelType);
        ContentValues cv = new ContentValues();
        try {
            cv.put(WKDBColumns.WKCoverMessageColumns.last_client_msg_no, clientMsgNo);
            cv.put(WKDBColumns.WKCoverMessageColumns.last_msg_seq, lastMsgSeq);
            cv.put(WKDBColumns.WKCoverMessageColumns.unread_count, count);
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG, "updateMsg error");
        }
        WKIMApplication.getInstance().getDbHelper()
                .update(conversation, cv, WKDBColumns.WKCoverMessageColumns.channel_id + "=? and " + WKDBColumns.WKCoverMessageColumns.channel_type + "=?", update);
    }

    public WKConversationMsg queryWithChannel(String channelID, byte channelType) {
        String sql = "select " + conversation + ".*," + channelCols + "," + extraCols + " from " + conversation + " left join " + channel + " on " + conversation + ".channel_id=" + channel + ".channel_id and " + conversation + ".channel_type=" + channel + ".channel_type left join " + conversationExtra + " on " + conversation + ".channel_id=" + conversationExtra + ".channel_id and " + conversation + ".channel_type=" + conversationExtra + ".channel_type where " + conversation + ".channel_id=? and " + conversation + ".channel_type=? and " + conversation + ".is_deleted=0";
        Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper()
                .rawQuery(sql, new Object[]{channelID, channelType});
        WKConversationMsg conversationMsg = null;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                conversationMsg = serializeMsg(cursor);
            }
            cursor.close();
        }
        return conversationMsg;
    }

    public synchronized boolean deleteWithChannel(String channelID, byte channelType, int isDeleted) {
        String[] update = new String[2];
        update[0] = channelID;
        update[1] = String.valueOf(channelType);
        ContentValues cv = new ContentValues();
        try {
            cv.put(WKDBColumns.WKCoverMessageColumns.is_deleted, isDeleted);
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG, "deleteWithChannel error");
        }

        boolean result = WKIMApplication.getInstance().getDbHelper()
                .update(conversation, cv, WKDBColumns.WKCoverMessageColumns.channel_id + "=? and " + WKDBColumns.WKCoverMessageColumns.channel_type + "=?", update);
        if (result) {
            ConversationManager.getInstance().setDeleteMsg(channelID, channelType);
        }
        return result;

    }

    public synchronized WKUIConversationMsg insertOrUpdateWithMsg(WKMsg msg, int unreadCount) {
        if (msg.channelID.equals(WKIMApplication.getInstance().getUid())) return null;
        WKConversationMsg wkConversationMsg = new WKConversationMsg();
        if (msg.channelType == WKChannelType.COMMUNITY_TOPIC && !TextUtils.isEmpty(msg.channelID)) {
            if (msg.channelID.contains("@")) {
                String[] str = msg.channelID.split("@");
                wkConversationMsg.parentChannelID = str[0];
                wkConversationMsg.parentChannelType = WKChannelType.COMMUNITY;
            }
        }
        wkConversationMsg.channelID = msg.channelID;
        wkConversationMsg.channelType = msg.channelType;
//        wkConversationMsg.localExtraMap = msg.localExtraMap;
        wkConversationMsg.lastMsgTimestamp = msg.timestamp;
        wkConversationMsg.lastClientMsgNO = msg.clientMsgNO;
        wkConversationMsg.lastMsgSeq = msg.messageSeq;
        wkConversationMsg.unreadCount = unreadCount;
        return insertOrUpdateWithConvMsg(wkConversationMsg);// 插入消息列表数据表
    }

    public synchronized WKUIConversationMsg insertOrUpdateWithConvMsg(WKConversationMsg conversationMsg) {
        boolean result;
        WKConversationMsg lastMsg = queryWithChannelId(conversationMsg.channelID, conversationMsg.channelType);
        if (lastMsg == null || TextUtils.isEmpty(lastMsg.channelID)) {
            //如果服务器自增id为0则表示是本地数据|直接保存
            result = insert(conversationMsg);
        } else {
            conversationMsg.unreadCount = lastMsg.unreadCount + conversationMsg.unreadCount;
            result = update(conversationMsg);
        }
        if (result) {
            return getUIMsg(conversationMsg);
        }
        return null;
    }

    private synchronized boolean insert(WKConversationMsg msg) {
        ContentValues cv = new ContentValues();
        try {
            cv = WKSqlContentValues.getContentValuesWithCoverMsg(msg, false);
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG, "insert error");
        }
        long result = -1;
        try {
            result = WKIMApplication.getInstance().getDbHelper()
                    .insert(conversation, cv);
        } catch (Exception ignored) {
        }
        return result > 0;
    }

    /**
     * 更新会话记录消息
     *
     * @param msg 会话消息
     * @return 修改结果
     */
    private synchronized boolean update(WKConversationMsg msg) {
        String[] update = new String[2];
        update[0] = msg.channelID;
        update[1] = String.valueOf(msg.channelType);
        ContentValues cv = new ContentValues();
        try {
            cv = WKSqlContentValues.getContentValuesWithCoverMsg(msg, false);
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG, "update error");
        }
        return WKIMApplication.getInstance().getDbHelper()
                .update(conversation, cv, WKDBColumns.WKCoverMessageColumns.channel_id + "=? and " + WKDBColumns.WKCoverMessageColumns.channel_type + "=?", update);
    }

    private synchronized WKConversationMsg queryWithChannelId(String channelId, byte channelType) {
        WKConversationMsg msg = null;
        String selection = WKDBColumns.WKCoverMessageColumns.channel_id + " = ? and " + WKDBColumns.WKCoverMessageColumns.channel_type + "=?";
        String[] selectionArgs = new String[]{channelId, String.valueOf(channelType)};
        Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper()
                .select(conversation, selection, selectionArgs,
                        null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                msg = serializeMsg(cursor);
            }
            cursor.close();
        }
        return msg;
    }


    public synchronized boolean clearEmpty() {
        return WKIMApplication.getInstance().getDbHelper()
                .delete(conversation, null, null);
    }

    public WKConversationMsgExtra queryMsgExtraWithChannel(String channelID, byte channelType) {
        WKConversationMsgExtra msgExtra = null;
        String selection = "channel_id=? and channel_type=?";
        Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().select(conversationExtra, selection, new String[]{channelID, String.valueOf(channelType)}, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                msgExtra = serializeMsgExtra(cursor);
            }
            cursor.close();
        }
        return msgExtra;
    }

    private List<WKConversationMsgExtra> queryWithExtraChannelIds(List<String> channelIds) {
        List<WKConversationMsgExtra> list = new ArrayList<>();
        try (Cursor cursor = WKIMApplication.getInstance().getDbHelper().select(conversationExtra, "channel_id in (" + WKCursor.getPlaceholders(channelIds.size()) + ")", channelIds.toArray(new String[0]), null)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                WKConversationMsgExtra extra = serializeMsgExtra(cursor);
                list.add(extra);
            }
        }
        return list;
    }

    public synchronized boolean insertOrUpdateMsgExtra(WKConversationMsgExtra extra) {
        WKConversationMsgExtra msgExtra = queryMsgExtraWithChannel(extra.channelID, extra.channelType);
        boolean isAdd = true;
        if (msgExtra != null) {
            extra.version = msgExtra.version;
            isAdd = false;
        }
        ContentValues cv = WKSqlContentValues.getCVWithExtra(extra);
        if (isAdd) {
            return WKIMApplication.getInstance().getDbHelper().insert(conversationExtra, cv) > 0;
        }
        return WKIMApplication.getInstance().getDbHelper().update(conversationExtra, "channel_id='" + extra.channelID + "' and channel_type=" + extra.channelType, cv);
    }

    public synchronized void insertMsgExtras(List<WKConversationMsgExtra> list) {
        List<String> channelIds = new ArrayList<>();
        for (WKConversationMsgExtra extra : list) {
            boolean isAdd = true;
            for (String channelID : channelIds) {
                if (channelID.equals(extra.channelID)) {
                    isAdd = false;
                    break;
                }
            }
            if (isAdd) channelIds.add(extra.channelID);
        }
        List<ContentValues> insertCVList = new ArrayList<>();
        List<ContentValues> updateCVList = new ArrayList<>();
        List<WKConversationMsgExtra> existList = queryWithExtraChannelIds(channelIds);
        for (WKConversationMsgExtra extra : list) {
            boolean isAdd = true;
            for (WKConversationMsgExtra existExtra : existList) {
                if (existExtra.channelID.equals(extra.channelID) && existExtra.channelType == extra.channelType) {
                    isAdd = false;
                    break;
                }
            }
            if (isAdd) {
                insertCVList.add(WKSqlContentValues.getCVWithExtra(extra));
            } else {
                updateCVList.add(WKSqlContentValues.getCVWithExtra(extra));
            }
        }

        try {
            WKIMApplication.getInstance().getDbHelper().getDb().beginTransaction();
            if (WKCommonUtils.isNotEmpty(insertCVList)) {
                for (ContentValues cv : insertCVList) {
                    WKIMApplication.getInstance().getDbHelper()
                            .insert(conversationExtra, cv);
                }
            }
            if (WKCommonUtils.isNotEmpty(updateCVList)) {
                for (ContentValues cv : updateCVList) {
                    String[] sv = new String[2];
                    sv[0] = cv.getAsString("channel_id");
                    sv[1] = cv.getAsString("channel_type");
                    WKIMApplication.getInstance().getDbHelper()
                            .update(conversationExtra, cv, "channel_id=? and channel_type=?", sv);
                }
            }
            WKIMApplication.getInstance().getDbHelper().getDb().setTransactionSuccessful();
        } finally {
            WKIMApplication.getInstance().getDbHelper().getDb().endTransaction();
        }
        List<WKUIConversationMsg> uiMsgList = ConversationDbManager.getInstance().queryWithChannelIds(channelIds);
//        for (int i = 0, size = uiMsgList.size(); i < size; i++) {
//            WKIM.getInstance().getConversationManager().setOnRefreshMsg(uiMsgList.get(i), i == uiMsgList.size() - 1, "saveMsgExtras");
//        }
        WKIM.getInstance().getConversationManager().setOnRefreshMsg(uiMsgList,"saveMsgExtras");
    }

    public long queryMsgExtraMaxVersion() {
        long maxVersion = 0;
        String sql = "select max(version) version from " + conversationExtra;
        Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper()
                .rawQuery(sql);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                maxVersion = WKCursor.readLong(cursor, "version");
            }
            cursor.close();
        }
        return maxVersion;
    }

    private synchronized WKConversationMsgExtra serializeMsgExtra(Cursor cursor) {
        WKConversationMsgExtra extra = new WKConversationMsgExtra();
        extra.channelID = WKCursor.readString(cursor, "channel_id");
        extra.channelType = (byte) WKCursor.readInt(cursor, "channel_type");
        extra.keepMessageSeq = WKCursor.readLong(cursor, "keep_message_seq");
        extra.keepOffsetY = WKCursor.readInt(cursor, "keep_offset_y");
        extra.draft = WKCursor.readString(cursor, "draft");
        extra.browseTo = WKCursor.readLong(cursor, "browse_to");
        extra.draftUpdatedAt = WKCursor.readLong(cursor, "draft_updated_at");
        extra.version = WKCursor.readLong(cursor, "version");
        if (cursor.getColumnIndex("extra_version") > 0) {
            extra.version = WKCursor.readLong(cursor, "extra_version");
        }
        return extra;
    }

    private synchronized WKConversationMsg serializeMsg(Cursor cursor) {
        WKConversationMsg msg = new WKConversationMsg();
        msg.channelID = WKCursor.readString(cursor, WKDBColumns.WKCoverMessageColumns.channel_id);
        msg.channelType = WKCursor.readByte(cursor, WKDBColumns.WKCoverMessageColumns.channel_type);
        msg.lastMsgTimestamp = WKCursor.readLong(cursor, WKDBColumns.WKCoverMessageColumns.last_msg_timestamp);
        msg.unreadCount = WKCursor.readInt(cursor, WKDBColumns.WKCoverMessageColumns.unread_count);
        msg.isDeleted = WKCursor.readInt(cursor, WKDBColumns.WKCoverMessageColumns.is_deleted);
        msg.version = WKCursor.readLong(cursor, WKDBColumns.WKCoverMessageColumns.version);
        msg.lastClientMsgNO = WKCursor.readString(cursor, WKDBColumns.WKCoverMessageColumns.last_client_msg_no);
        msg.lastMsgSeq = WKCursor.readLong(cursor, WKDBColumns.WKCoverMessageColumns.last_msg_seq);
        msg.parentChannelID = WKCursor.readString(cursor, WKDBColumns.WKCoverMessageColumns.parent_channel_id);
        msg.parentChannelType = WKCursor.readByte(cursor, WKDBColumns.WKCoverMessageColumns.parent_channel_type);
        String extra = WKCursor.readString(cursor, WKDBColumns.WKCoverMessageColumns.extra);
        if (!TextUtils.isEmpty(extra)) {
            HashMap<String, Object> hashMap = new HashMap<>();
            try {
                JSONObject jsonObject = new JSONObject(extra);
                Iterator<String> keys = jsonObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    hashMap.put(key, jsonObject.opt(key));
                }
            } catch (JSONException e) {
                WKLoggerUtils.getInstance().e(TAG, "serializeMsg error");
            }
            msg.localExtraMap = hashMap;
        }
        msg.msgExtra = serializeMsgExtra(cursor);
        return msg;
    }

    public WKUIConversationMsg getUIMsg(WKConversationMsg conversationMsg) {
        WKUIConversationMsg msg = new WKUIConversationMsg();
        msg.lastMsgSeq = conversationMsg.lastMsgSeq;
        msg.clientMsgNo = conversationMsg.lastClientMsgNO;
        msg.unreadCount = conversationMsg.unreadCount;
        msg.lastMsgTimestamp = conversationMsg.lastMsgTimestamp;
        msg.channelID = conversationMsg.channelID;
        msg.channelType = conversationMsg.channelType;
        msg.isDeleted = conversationMsg.isDeleted;
        msg.parentChannelID = conversationMsg.parentChannelID;
        msg.parentChannelType = conversationMsg.parentChannelType;
        msg.setRemoteMsgExtra(conversationMsg.msgExtra);
        return msg;
    }
}
