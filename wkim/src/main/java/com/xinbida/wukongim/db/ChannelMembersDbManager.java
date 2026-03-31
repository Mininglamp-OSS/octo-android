package com.xinbida.wukongim.db;

import static com.xinbida.wukongim.db.WKDBColumns.TABLE.channel;
import static com.xinbida.wukongim.db.WKDBColumns.TABLE.channelMembers;

import android.content.ContentValues;
import android.database.Cursor;
import android.text.TextUtils;

import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.manager.ChannelMembersManager;
import com.xinbida.wukongim.utils.WKCommonUtils;
import com.xinbida.wukongim.utils.WKLoggerUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * 2019-11-10 14:06
 * 频道成员数据管理
 */
public class ChannelMembersDbManager {
    private static final String TAG = "ChannelMembersDbManager";
    final String channelCols = channel + ".channel_remark," + channel + ".channel_name," + channel + ".avatar," + channel + ".avatar_cache_key";

    private ChannelMembersDbManager() {
    }

    private static class ChannelMembersManagerBinder {
        private final static ChannelMembersDbManager channelMembersManager = new ChannelMembersDbManager();
    }

    public static ChannelMembersDbManager getInstance() {
        return ChannelMembersManagerBinder.channelMembersManager;
    }

    public synchronized List<WKChannelMember> search(String channelId, byte channelType, String keyword, int page, int size) {
        int queryPage = (page - 1) * size;
        Object[] args = new Object[6];
        args[0] = channelId;
        args[1] = channelType;
        args[2] = "%" + keyword + "%";
        args[3] = "%" + keyword + "%";
        args[4] = "%" + keyword + "%";
        args[5] = "%" + keyword + "%";
        String sql = "select " + channelMembers + ".*," + channelCols + " from " + channelMembers + " LEFT JOIN " + channel + " on " + channelMembers + ".member_uid=" + channel + ".channel_id and " + channel + ".channel_type=1 where " + channelMembers + ".channel_id=? and " + channelMembers + ".channel_type=? and " + channelMembers + ".is_deleted=0 and " + channelMembers + ".status=1 and (member_name like ? or member_remark like ? or channel_name like ? or channel_remark like ?) order by " + channelMembers + ".role=1 desc," + channelMembers + ".role=2 desc," + channelMembers + "." + WKDBColumns.WKChannelMembersColumns.created_at + " asc limit " + queryPage + "," + size;
        Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args);
        List<WKChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    public synchronized List<WKChannelMember> queryWithPage(String channelId, byte channelType, int page, int size) {
        int queryPage = (page - 1) * size;
        Object[] args = new Object[2];
        args[0] = channelId;
        args[1] = channelType;
        String sql = "select " + channelMembers + ".*," + channelCols + " from " + channelMembers + " LEFT JOIN " + channel + " on " + channelMembers + ".member_uid=" + channel + ".channel_id and " + channel + ".channel_type=1 where " + channelMembers + ".channel_id=? and " + channelMembers + ".channel_type=? and " + channelMembers + ".is_deleted=0 and " + channelMembers + ".status=1 order by " + channelMembers + ".role=1 desc," + channelMembers + ".role=2 desc," + channelMembers + "." + WKDBColumns.WKChannelMembersColumns.created_at + " asc limit " + queryPage + "," + size;
        Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args);
        List<WKChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    /**
     * 查询某个频道的所有成员
     *
     * @param channelId 频道ID
     * @return List<WKChannelMember>
     */
    public synchronized List<WKChannelMember> query(String channelId, byte channelType) {
        Object[] args = new Object[2];
        args[0] = channelId;
        args[1] = channelType;
        String sql = "select " + channelMembers + ".*," + channelCols + " from " + channelMembers + " LEFT JOIN " + channel + " on " + channelMembers + ".member_uid=" + channel + ".channel_id and " + channel + ".channel_type=1 where " + channelMembers + ".channel_id=? and " + channelMembers + ".channel_type=? and " + channelMembers + ".is_deleted=0 and " + channelMembers + ".status=1 order by " + channelMembers + ".role=1 desc," + channelMembers + ".role=2 desc," + channelMembers + "." + WKDBColumns.WKChannelMembersColumns.created_at + " asc";
        Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args);
        List<WKChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    public synchronized List<WKChannelMember> queryDeleted(String channelId, byte channelType) {
        Object[] args = new Object[2];
        args[0] = channelId;
        args[1] = channelType;
        String sql = "select " + channelMembers + ".*," + channelCols + " from " + channelMembers + " LEFT JOIN " + channel + " on " + channelMembers + ".member_uid=" + channel + ".channel_id and " + channel + ".channel_type=1 where " + channelMembers + ".channel_id=? and " + channelMembers + ".channel_type=? and " + channelMembers + ".is_deleted=1 and " + channelMembers + ".status=1 order by " + channelMembers + ".role=1 desc," + channelMembers + ".role=2 desc," + channelMembers + "." + WKDBColumns.WKChannelMembersColumns.created_at + " asc";
        Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args);
        List<WKChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    public synchronized boolean isExist(String channelId, byte channelType, String uid) {
        boolean isExist = false;
        Object[] args = new Object[3];
        args[0] = channelId;
        args[1] = channelType;
        args[2] = uid;
        String sql = "select " + channelMembers + ".*," + channelCols + " from " + channelMembers + " left join " + channel + " on " + channelMembers + ".member_uid = " + channel + ".channel_id AND " + channel + ".channel_type=1 where (" + channelMembers + "." + WKDBColumns.WKChannelMembersColumns.channel_id + "=? and " + channelMembers + "." + WKDBColumns.WKChannelMembersColumns.channel_type + "=? and " + channelMembers + "." + WKDBColumns.WKChannelMembersColumns.member_uid + "=?)";
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args)) {

            if (cursor != null && cursor.moveToLast()) {
                isExist = true;
            }
        }
        return isExist;
    }

    public List<WKChannelMember> queryWithUIDs(String channelID, byte channelType, List<String> uidList) {
        List<String> args = new ArrayList<>();
        args.add(channelID);
        args.add(String.valueOf(channelType));
        args.addAll(uidList);
        uidList.add(String.valueOf(channelType));
        Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().select(channelMembers, "channel_id =? and channel_type=? and member_uid in (" + WKCursor.getPlaceholders(uidList.size()) + ")", args.toArray(new String[0]), null);
        List<WKChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    /**
     * 查询单个频道成员
     *
     * @param channelId 频道ID
     * @param uid       用户ID
     */
    public synchronized WKChannelMember query(String channelId, byte channelType, String uid) {
        WKChannelMember wkChannelMember = null;
        Object[] args = new Object[3];
        args[0] = channelId;
        args[1] = channelType;
        args[2] = uid;
        String sql = "select " + channelMembers + ".*," + channelCols + " from " + channelMembers + " left join " + channel + " on " + channelMembers + ".member_uid = " + channel + ".channel_id AND " + channel + ".channel_type=1 where (" + channelMembers + "." + WKDBColumns.WKChannelMembersColumns.channel_id + "=? and " + channelMembers + "." + WKDBColumns.WKChannelMembersColumns.channel_type + "=? and " + channelMembers + "." + WKDBColumns.WKChannelMembersColumns.member_uid + "=?)";
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args)) {
            if (cursor == null) {
                return null;
            }
            if (cursor.moveToLast()) {
                wkChannelMember = serializableChannelMember(cursor);
            }
        }
        return wkChannelMember;
    }

    public synchronized void insert(WKChannelMember channelMember) {
        if (TextUtils.isEmpty(channelMember.channelID) || TextUtils.isEmpty(channelMember.memberUID))
            return;
        ContentValues cv = new ContentValues();
        try {
            cv = WKSqlContentValues.getContentValuesWithChannelMember(channelMember);
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG, "insert error");
        }
        WKIMApplication.getInstance().getDbHelper()
                .insert(channelMembers, cv);
    }

    /**
     * 批量插入频道成员
     *
     * @param list List<WKChannelMember>
     */
    public void insertMembers(List<WKChannelMember> list) {
        List<ContentValues> newCVList = new ArrayList<>();
        for (WKChannelMember member : list) {
            ContentValues cv = WKSqlContentValues.getContentValuesWithChannelMember(member);
            newCVList.add(cv);
        }
        try {
            WKIMApplication.getInstance().getDbHelper().getDb()
                    .beginTransaction();
            if (WKCommonUtils.isNotEmpty(newCVList)) {
                for (ContentValues cv : newCVList) {
                    WKIMApplication.getInstance().getDbHelper().insert(channelMembers, cv);
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
    }

    public void insertMembers(List<WKChannelMember> allMemberList, List<WKChannelMember> existList) {
        List<ContentValues> insertCVList = new ArrayList<>();
//        List<ContentValues> updateCVList = new ArrayList<>();
        for (WKChannelMember channelMember : allMemberList) {
//            boolean isAdd = true;
//            for (WKChannelMember cm : existList) {
//                if (channelMember.memberUID.equals(cm.memberUID)) {
//                    isAdd = false;
//                    updateCVList.add(WKSqlContentValues.getContentValuesWithChannelMember(channelMember));
//                    break;
//                }
//            }
//            if (isAdd) {
            insertCVList.add(WKSqlContentValues.getContentValuesWithChannelMember(channelMember));
//            }
        }
        WKIMApplication.getInstance().getDbHelper().getDb()
                .beginTransaction();
        try {
            if (WKCommonUtils.isNotEmpty(insertCVList)) {
                for (ContentValues cv : insertCVList) {
                    WKIMApplication.getInstance().getDbHelper().insert(channelMembers, cv);
                }
            }
//            if (WKCommonUtils.isNotEmpty(updateCVList)) {
//                for (ContentValues cv : updateCVList) {
//                    String[] update = new String[3];
//                    update[0] = cv.getAsString(WKDBColumns.WKChannelMembersColumns.channel_id);
//                    update[1] = String.valueOf(cv.getAsByte(WKDBColumns.WKChannelMembersColumns.channel_type));
//                    update[2] = cv.getAsString(WKDBColumns.WKChannelMembersColumns.member_uid);
//                    WKIMApplication.getInstance().getDbHelper()
//                            .update(channelMembers, cv, WKDBColumns.WKChannelMembersColumns.channel_id + "=? and " + WKDBColumns.WKChannelMembersColumns.channel_type + "=? and " + WKDBColumns.WKChannelMembersColumns.member_uid + "=?", update);
//                }
//            }
            WKIMApplication.getInstance().getDbHelper().getDb().setTransactionSuccessful();
        } finally {
            if (WKIMApplication.getInstance().getDbHelper().getDb().inTransaction()) {
                WKIMApplication.getInstance().getDbHelper().getDb()
                        .endTransaction();
            }
        }
    }

    public void insertOrUpdate(WKChannelMember channelMember) {
        if (channelMember == null) return;
        if (isExist(channelMember.channelID, channelMember.channelType, channelMember.memberUID)) {
            update(channelMember);
        } else {
            insert(channelMember);
        }
    }

    /**
     * 修改某个频道的某个成员信息
     *
     * @param channelMember 成员
     */
    public synchronized void update(WKChannelMember channelMember) {
        String[] update = new String[3];
        update[0] = channelMember.channelID;
        update[1] = String.valueOf(channelMember.channelType);
        update[2] = channelMember.memberUID;
        ContentValues cv = new ContentValues();
        try {
            cv = WKSqlContentValues.getContentValuesWithChannelMember(channelMember);
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG, "update error");
        }
        WKIMApplication.getInstance().getDbHelper()
                .update(channelMembers, cv, WKDBColumns.WKChannelMembersColumns.channel_id + "=? and " + WKDBColumns.WKChannelMembersColumns.channel_type + "=? and " + WKDBColumns.WKChannelMembersColumns.member_uid + "=?", update);
    }

    /**
     * 根据字段修改频道成员
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param uid         用户ID
     * @param field       字段
     * @param value       值
     */
    public synchronized boolean updateWithField(String channelID, byte channelType, String uid, String field, String value) {
        String[] updateKey = new String[]{field};
        String[] updateValue = new String[]{value};
        String where = WKDBColumns.WKChannelMembersColumns.channel_id + "=? and " + WKDBColumns.WKChannelMembersColumns.channel_type + "=? and " + WKDBColumns.WKChannelMembersColumns.member_uid + "=?";
        String[] whereValue = new String[3];
        whereValue[0] = channelID;
        whereValue[1] = String.valueOf(channelType);
        whereValue[2] = uid;
        int row = WKIMApplication.getInstance().getDbHelper()
                .update(channelMembers, updateKey, updateValue, where, whereValue);
        if (row > 0) {
            WKChannelMember channelMember = query(channelID, channelType, uid);
            if (channelMember != null)
                //刷新频道成员信息
                ChannelMembersManager.getInstance().setRefreshChannelMember(channelMember, true);
        }
        return row > 0;
    }

    public void deleteWithChannel(String channelID, byte channelType) {
        String selection = "channel_id=? and channel_type=?";
        String[] selectionArgs = new String[2];
        selectionArgs[0] = channelID;
        selectionArgs[1] = String.valueOf(channelType);
        WKIMApplication.getInstance().getDbHelper().delete(channelMembers, selection, selectionArgs);
    }

    /**
     * 批量删除频道成员
     *
     * @param list 频道成员
     */
    public synchronized void deleteMembers(List<WKChannelMember> list) {
        try {
            WKIMApplication.getInstance().getDbHelper().getDb()
                    .beginTransaction();
            if (WKCommonUtils.isNotEmpty(list)) {
                for (int i = 0, size = list.size(); i < size; i++) {
                    insertOrUpdate(list.get(i));
                }
                WKIMApplication.getInstance().getDbHelper().getDb()
                        .setTransactionSuccessful();
            }
        } catch (Exception ignored) {
        } finally {
            if (WKIMApplication.getInstance().getDbHelper().getDb().inTransaction()) {
                WKIMApplication.getInstance().getDbHelper().getDb()
                        .endTransaction();
            }
        }
        ChannelMembersManager.getInstance().setOnRemoveChannelMember(list);
    }

    public long queryMaxVersion(String channelID, byte channelType) {
        Object[] args = new Object[2];
        args[0] = channelID;
        args[1] = channelType;
        String sql = "select max(version) version from " + channelMembers + " where channel_id =? and channel_type=? limit 0, 1";
        long version = 0;
        try {
            if (WKIMApplication.getInstance().getDbHelper() != null) {
                Cursor cursor = WKIMApplication
                        .getInstance()
                        .getDbHelper()
                        .rawQuery(sql, args);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        version = WKCursor.readLong(cursor, "version");
                    }
                    cursor.close();
                }
            }
        } catch (Exception ignored) {
        }
        return version;
    }

    @Deprecated
    public synchronized WKChannelMember queryMaxVersionMember(String channelID, byte channelType) {
        WKChannelMember channelMember = null;
        Object[] args = new Object[2];
        args[0] = channelID;
        args[1] = channelType;
        String sql = "select * from " + channelMembers + " where " + WKDBColumns.WKChannelMembersColumns.channel_id + "=? and " + WKDBColumns.WKChannelMembersColumns.channel_type + "=? order by " + WKDBColumns.WKChannelMembersColumns.version + " desc limit 0,1";
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args)) {
            if (cursor == null) {
                return null;
            }
            if (cursor.moveToLast()) {
                channelMember = serializableChannelMember(cursor);
            }
        }
        return channelMember;
    }

    public synchronized List<WKChannelMember> queryRobotMembers(String channelId, byte channelType) {
        String selection = "channel_id=? and channel_type=? and robot=1 and is_deleted=0";
        Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().select(channelMembers, selection, new String[]{channelId, String.valueOf(channelType)}, null);
        List<WKChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    public List<WKChannelMember> queryWithRole(String channelId, byte channelType, int role) {
        String selection = "channel_id=? AND channel_type=? AND role=? AND is_deleted=0";
        Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().select(channelMembers, selection, new String[]{channelId, String.valueOf(channelType), String.valueOf(role)}, null);
        List<WKChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    public synchronized List<WKChannelMember> queryWithStatus(String channelId, byte channelType, int status) {
        Object[] args = new Object[3];
        args[0] = channelId;
        args[1] = channelType;
        args[2] = status;
        String sql = "select " + channelMembers + ".*," + channel + ".channel_name," + channel + ".channel_remark," + channel + ".avatar from " + channelMembers + " left Join " + channel + " where " + channelMembers + ".member_uid = " + channel + ".channel_id AND " + channel + ".channel_type=1 AND " + channelMembers + ".channel_id=? and " + channelMembers + ".channel_type=? and " + channelMembers + ".status=? order by " + channelMembers + ".role=1 desc," + channelMembers + ".role=2 desc," + channelMembers + "." + WKDBColumns.WKChannelMembersColumns.created_at + " asc";
        Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args);
        List<WKChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    public synchronized int queryCount(String channelID, byte channelType) {
        Object[] args = new Object[2];
        args[0] = channelID;
        args[1] = channelType;
        String sql = "select count(*) from " + channelMembers
                + " where (" + WKDBColumns.WKChannelMembersColumns.channel_id + "=? and "
                + WKDBColumns.WKChannelMembersColumns.channel_type + "=? and " + WKDBColumns.WKChannelMembersColumns.is_deleted + "=0 and " + WKDBColumns.WKChannelMembersColumns.status + "=1)";
        Cursor cursor = WKIMApplication.getInstance().getDbHelper().rawQuery(sql, args);
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    /**
     * 序列化频道成员
     *
     * @param cursor Cursor
     * @return WKChannelMember
     */
    private WKChannelMember serializableChannelMember(Cursor cursor) {
        WKChannelMember channelMember = new WKChannelMember();
        channelMember.id = WKCursor.readLong(cursor, WKDBColumns.WKChannelMembersColumns.id);
        channelMember.status = WKCursor.readInt(cursor, WKDBColumns.WKChannelMembersColumns.status);
        channelMember.channelID = WKCursor.readString(cursor, WKDBColumns.WKChannelMembersColumns.channel_id);
        channelMember.channelType = (byte) WKCursor.readInt(cursor, WKDBColumns.WKChannelMembersColumns.channel_type);
        channelMember.memberUID = WKCursor.readString(cursor, WKDBColumns.WKChannelMembersColumns.member_uid);
        channelMember.memberName = WKCursor.readString(cursor, WKDBColumns.WKChannelMembersColumns.member_name);
        channelMember.memberAvatar = WKCursor.readString(cursor, WKDBColumns.WKChannelMembersColumns.member_avatar);
        channelMember.memberRemark = WKCursor.readString(cursor, WKDBColumns.WKChannelMembersColumns.member_remark);
        channelMember.role = WKCursor.readInt(cursor, WKDBColumns.WKChannelMembersColumns.role);
        channelMember.isDeleted = WKCursor.readInt(cursor, WKDBColumns.WKChannelMembersColumns.is_deleted);
        channelMember.version = WKCursor.readLong(cursor, WKDBColumns.WKChannelMembersColumns.version);
        channelMember.createdAt = WKCursor.readString(cursor, WKDBColumns.WKChannelMembersColumns.created_at);
        channelMember.updatedAt = WKCursor.readString(cursor, WKDBColumns.WKChannelMembersColumns.updated_at);
        channelMember.memberInviteUID = WKCursor.readString(cursor, WKDBColumns.WKChannelMembersColumns.member_invite_uid);
        channelMember.robot = WKCursor.readInt(cursor, WKDBColumns.WKChannelMembersColumns.robot);
        channelMember.forbiddenExpirationTime = WKCursor.readLong(cursor, WKDBColumns.WKChannelMembersColumns.forbidden_expiration_time);
        String channelName = WKCursor.readString(cursor, WKDBColumns.WKChannelColumns.channel_name);
        if (!TextUtils.isEmpty(channelName)) channelMember.memberName = channelName;
        channelMember.remark = WKCursor.readString(cursor, WKDBColumns.WKChannelColumns.channel_remark);
        channelMember.memberAvatar = WKCursor.readString(cursor, WKDBColumns.WKChannelColumns.avatar);
        String avatarCache = WKCursor.readString(cursor, WKDBColumns.WKChannelColumns.avatar_cache_key);
        if (!TextUtils.isEmpty(avatarCache)) {
            channelMember.memberAvatarCacheKey = avatarCache;
        } else {
            channelMember.memberAvatarCacheKey = WKCursor.readString(cursor, WKDBColumns.WKChannelMembersColumns.memberAvatarCacheKey);
        }
        String extra = WKCursor.readString(cursor, WKDBColumns.WKChannelMembersColumns.extra);
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
                WKLoggerUtils.getInstance().e(TAG, "serializableChannelMember extra error");
            }
            channelMember.extraMap = hashMap;
        }
        return channelMember;
    }
}
