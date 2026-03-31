package com.xinbida.wukongim.db;

import static com.xinbida.wukongim.db.WKDBColumns.TABLE.channel;
import static com.xinbida.wukongim.db.WKDBColumns.TABLE.channelMembers;

import android.content.ContentValues;
import android.database.Cursor;
import android.text.TextUtils;

import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelSearchResult;
import com.xinbida.wukongim.utils.WKCommonUtils;
import com.xinbida.wukongim.utils.WKLoggerUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 5/20/21 5:53 PM
 * channel DB manager
 */
public class ChannelDBManager {
    private static final String TAG = "ChannelDBManager";

    private ChannelDBManager() {
    }

    private static class ChannelDBManagerBinder {
        static final ChannelDBManager channelDBManager = new ChannelDBManager();
    }

    public static ChannelDBManager getInstance() {
        return ChannelDBManagerBinder.channelDBManager;
    }

    public List<WKChannel> queryWithChannelIds(List<String> channelIDs) {
        List<WKChannel> list = new ArrayList<>();
        if (WKIMApplication
                .getInstance()
                .getDbHelper() == null) {
            return list;
        }
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper()
                .select(channel, "channel_id in (" + WKCursor.getPlaceholders(channelIDs.size()) + ")", channelIDs.toArray(new String[0]), null)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                WKChannel channel = serializableChannel(cursor);
                list.add(channel);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public List<WKChannel> queryWithChannelIdsAndChannelType(List<String> channelIDs, byte channelType) {
        List<WKChannel> list = new ArrayList<>();
        if (WKIMApplication
                .getInstance()
                .getDbHelper() == null) {
            return list;
        }
        List<String> args = new ArrayList<>(channelIDs);
        args.add(String.valueOf(channelType));
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper()
                .select(channel, "channel_id in (" + WKCursor.getPlaceholders(channelIDs.size()) + ") and channel_type=?", args.toArray(new String[0]), null)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                WKChannel channel = serializableChannel(cursor);
                list.add(channel);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public synchronized WKChannel query(String channelId, int channelType) {
        String selection = WKDBColumns.WKChannelColumns.channel_id + "=? and " + WKDBColumns.WKChannelColumns.channel_type + "=?";
        String[] selectionArgs = new String[2];
        selectionArgs[0] = channelId;
        selectionArgs[1] = String.valueOf(channelType);
        Cursor cursor = null;
        WKChannel wkChannel = null;
        if (WKIMApplication
                .getInstance()
                .getDbHelper() == null) {
            return null;
        }
        try {
            cursor = WKIMApplication
                    .getInstance()
                    .getDbHelper()
                    .select(channel, selection, selectionArgs,
                            null);
            if (cursor != null) {
                if (cursor.getCount() > 0) {
                    cursor.moveToNext();
                    wkChannel = serializableChannel(cursor);
                }
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return wkChannel;
    }

    private boolean isExist(String channelId, int channelType) {
        String selection = WKDBColumns.WKChannelColumns.channel_id + "=? and " + WKDBColumns.WKChannelColumns.channel_type + "=?";
        String[] selectionArgs = new String[2];
        selectionArgs[0] = channelId;
        selectionArgs[1] = String.valueOf(channelType);
        Cursor cursor = null;
        boolean isExist = false;
        try {
            if (WKIMApplication
                    .getInstance()
                    .getDbHelper() == null) {
                return false;
            }
            cursor = WKIMApplication
                    .getInstance()
                    .getDbHelper()
                    .select(channel, selection, selectionArgs,
                            null);
            if (cursor != null && cursor.moveToNext()) {
                isExist = true;
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return isExist;
    }

    public synchronized void insertChannels(List<WKChannel> list) {
        List<ContentValues> newCVList = new ArrayList<>();
        for (WKChannel channel : list) {
            ContentValues cv = WKSqlContentValues.getContentValuesWithChannel(channel);
            newCVList.add(cv);
        }
        try {
            if (WKIMApplication.getInstance().getDbHelper() == null) {
                return;
            }
            WKIMApplication.getInstance().getDbHelper().getDb()
                    .beginTransaction();
            for (ContentValues cv : newCVList) {
                WKIMApplication.getInstance().getDbHelper()
                        .insert(channel, cv);
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

    public synchronized void insertOrUpdate(WKChannel channel) {
        if (isExist(channel.channelID, channel.channelType)) {
            update(channel);
        } else {
            insert(channel);
        }
    }

    private synchronized void insert(WKChannel wkChannel) {
        ContentValues cv = new ContentValues();
        try {
            cv = WKSqlContentValues.getContentValuesWithChannel(wkChannel);
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG, "insert channel error");
        }
        if (WKIMApplication.getInstance().getDbHelper() == null) {
            return;
        }
        WKIMApplication.getInstance().getDbHelper()
                .insert(channel, cv);
    }

    public synchronized void update(WKChannel wkChannel) {
        String[] update = new String[2];
        update[0] = wkChannel.channelID;
        update[1] = String.valueOf(wkChannel.channelType);
        ContentValues cv = new ContentValues();
        try {
            cv = WKSqlContentValues.getContentValuesWithChannel(wkChannel);
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG, "update channel error");
        }
        if (WKIMApplication.getInstance().getDbHelper() == null) {
            return;
        }
        WKIMApplication.getInstance().getDbHelper()
                .update(channel, cv, WKDBColumns.WKChannelColumns.channel_id + "=? and " + WKDBColumns.WKChannelColumns.channel_type + "=?", update);

    }

    /**
     * 查询频道
     *
     * @param channelType 频道类型
     * @param follow      是否关注 好友或陌生人
     * @param status      状态 正常或黑名单
     * @return List<WKChannel>
     */
    public synchronized List<WKChannel> queryWithFollowAndStatus(byte channelType, int follow, int status) {
        String[] args = new String[3];
        args[0] = String.valueOf(channelType);
        args[1] = String.valueOf(follow);
        args[2] = String.valueOf(status);
        String selection = WKDBColumns.WKChannelColumns.channel_type + "=? and " + WKDBColumns.WKChannelColumns.follow + "=? and " + WKDBColumns.WKChannelColumns.status + "=? and is_deleted=0";
        List<WKChannel> channels = new ArrayList<>();
        if (WKIMApplication
                .getInstance()
                .getDbHelper() != null) {
            try (Cursor cursor = WKIMApplication
                    .getInstance()
                    .getDbHelper().select(channel, selection, args, null)) {
                if (cursor == null) {
                    return channels;
                }
                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    channels.add(serializableChannel(cursor));
                }
            }
        }

        return channels;
    }

    /**
     * 查下指定频道类型和频道状态的频道
     *
     * @param channelType 频道类型
     * @param status      状态[sdk不维护状态]
     * @return List<WKChannel>
     */
    public synchronized List<WKChannel> queryWithStatus(byte channelType, int status) {
        String[] args = new String[2];
        args[0] = String.valueOf(channelType);
        args[1] = String.valueOf(status);
        String selection = WKDBColumns.WKChannelColumns.channel_type + "=? and " + WKDBColumns.WKChannelColumns.status + "=?";
        List<WKChannel> channels = new ArrayList<>();
        if (WKIMApplication.getInstance().getDbHelper() == null) {
            return channels;
        }
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().select(channel, selection, args, null)) {
            if (cursor == null) {
                return channels;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                channels.add(serializableChannel(cursor));
            }
        }
        return channels;
    }

    public synchronized List<WKChannelSearchResult> search(String searchKey) {
        List<WKChannelSearchResult> list = new ArrayList<>();
        Object[] args = new Object[4];
        args[0] = "%" + searchKey + "%";
        args[1] = "%" + searchKey + "%";
        args[2] = "%" + searchKey + "%";
        args[3] = "%" + searchKey + "%";
        String sql = " select t.*,cm.member_name,cm.member_remark from (\n" +
                " select " + channel + ".*,max(" + channelMembers + ".id) mid from " + channel + "," + channelMembers + " " +
                "where " + channel + ".channel_id=" + channelMembers + ".channel_id and " + channel + ".channel_type=" + channelMembers + ".channel_type" +
                " and (" + channel + ".channel_name like ? or " + channel + ".channel_remark" +
                " like ? or " + channelMembers + ".member_name like ? or " + channelMembers + ".member_remark like ?)\n" +
                " group by " + channel + ".channel_id," + channel + ".channel_type\n" +
                " ) t," + channelMembers + " cm where t.channel_id=cm.channel_id and t.channel_type=cm.channel_type and t.mid=cm.id";
        Cursor cursor = WKIMApplication.getInstance().getDbHelper().rawQuery(sql, args);
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            String member_name = WKCursor.readString(cursor, "member_name");
            String member_remark = WKCursor.readString(cursor, "member_remark");
            WKChannel channel = serializableChannel(cursor);
            WKChannelSearchResult result = new WKChannelSearchResult();
            result.wkChannel = channel;
            if (!TextUtils.isEmpty(member_remark)) {
                //优先显示备注名称
                if (member_remark.toUpperCase().contains(searchKey.toUpperCase())) {
                    result.containMemberName = member_remark;
                }
            }
            if (TextUtils.isEmpty(result.containMemberName)) {
                if (!TextUtils.isEmpty(member_name)) {
                    if (member_name.toUpperCase().contains(searchKey.toUpperCase())) {
                        result.containMemberName = member_name;
                    }
                }
            }
            list.add(result);
        }
        cursor.close();
        return list;
    }

    public synchronized List<WKChannel> searchWithChannelType(String searchKey, byte channelType) {
        List<WKChannel> list = new ArrayList<>();
        Object[] args = new Object[3];
        args[0] = "%" + searchKey + "%";
        args[1] = "%" + searchKey + "%";
        args[2] = channelType;
        String sql = "select * from " + channel + " where (" + WKDBColumns.WKChannelColumns.channel_name + " LIKE ? or " + WKDBColumns.WKChannelColumns.channel_remark + " LIKE ?) and " + WKDBColumns.WKChannelColumns.channel_type + "=?";
        try (Cursor cursor = WKIMApplication.getInstance().getDbHelper().rawQuery(sql, args)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                list.add(serializableChannel(cursor));
            }
        }
        return list;
    }

    public synchronized List<WKChannel> searchWithChannelTypeAndFollow(String searchKey, byte channelType, int follow) {
        List<WKChannel> list = new ArrayList<>();
        Object[] args = new Object[4];
        args[0] = "%" + searchKey + "%";
        args[1] = "%" + searchKey + "%";
        args[2] = channelType;
        args[3] = follow;
        String sql = "select * from " + channel + " where (" + WKDBColumns.WKChannelColumns.channel_name + " LIKE ? or " + WKDBColumns.WKChannelColumns.channel_remark + " LIKE ?) and " + WKDBColumns.WKChannelColumns.channel_type + "=? and " + WKDBColumns.WKChannelColumns.follow + "=?";
        try (Cursor cursor = WKIMApplication.getInstance().getDbHelper().rawQuery(sql, args)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                list.add(serializableChannel(cursor));
            }
        }
        return list;
    }

    public synchronized List<WKChannel> queryWithChannelTypeAndFollow(byte channelType, int follow) {
        String[] args = new String[2];
        args[0] = String.valueOf(channelType);
        args[1] = String.valueOf(follow);
        String selection = WKDBColumns.WKChannelColumns.channel_type + "=? and " + WKDBColumns.WKChannelColumns.follow + "=?";
        List<WKChannel> channels = new ArrayList<>();
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().select(channel, selection, args, null)) {
            if (cursor == null) {
                return channels;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                channels.add(serializableChannel(cursor));
            }
        }
        return channels;
    }

    public synchronized void updateWithField(String channelID, byte channelType, String field, String value) {
        String[] updateKey = new String[]{field};
        String[] updateValue = new String[]{value};
        String where = WKDBColumns.WKChannelColumns.channel_id + "=? and " + WKDBColumns.WKChannelColumns.channel_type + "=?";
        String[] whereValue = new String[2];
        whereValue[0] = channelID;
        whereValue[1] = String.valueOf(channelType);
        WKIMApplication.getInstance().getDbHelper()
                .update(channel, updateKey, updateValue, where, whereValue);
    }

    public WKChannel serializableChannel(Cursor cursor) {
        WKChannel channel = new WKChannel();
        channel.id = WKCursor.readLong(cursor, WKDBColumns.WKChannelColumns.id);
        channel.channelID = WKCursor.readString(cursor, WKDBColumns.WKChannelColumns.channel_id);
        channel.channelType = WKCursor.readByte(cursor, WKDBColumns.WKChannelColumns.channel_type);
        channel.channelName = WKCursor.readString(cursor, WKDBColumns.WKChannelColumns.channel_name);
        channel.channelRemark = WKCursor.readString(cursor, WKDBColumns.WKChannelColumns.channel_remark);
        channel.showNick = WKCursor.readInt(cursor, WKDBColumns.WKChannelColumns.show_nick);
        channel.top = WKCursor.readInt(cursor, WKDBColumns.WKChannelColumns.top);
        channel.mute = WKCursor.readInt(cursor, WKDBColumns.WKChannelColumns.mute);
        channel.isDeleted = WKCursor.readInt(cursor, WKDBColumns.WKChannelColumns.is_deleted);
        channel.forbidden = WKCursor.readInt(cursor, WKDBColumns.WKChannelColumns.forbidden);
        channel.status = WKCursor.readInt(cursor, WKDBColumns.WKChannelColumns.status);
        channel.follow = WKCursor.readInt(cursor, WKDBColumns.WKChannelColumns.follow);
        channel.invite = WKCursor.readInt(cursor, WKDBColumns.WKChannelColumns.invite);
        channel.version = WKCursor.readLong(cursor, WKDBColumns.WKChannelColumns.version);
        channel.createdAt = WKCursor.readString(cursor, WKDBColumns.WKChannelColumns.created_at);
        channel.updatedAt = WKCursor.readString(cursor, WKDBColumns.WKChannelColumns.updated_at);
        channel.avatar = WKCursor.readString(cursor, WKDBColumns.WKChannelColumns.avatar);
        channel.online = WKCursor.readInt(cursor, WKDBColumns.WKChannelColumns.online);
        channel.lastOffline = WKCursor.readLong(cursor, WKDBColumns.WKChannelColumns.last_offline);
        channel.category = WKCursor.readString(cursor, WKDBColumns.WKChannelColumns.category);
        channel.receipt = WKCursor.readInt(cursor, WKDBColumns.WKChannelColumns.receipt);
        channel.robot = WKCursor.readInt(cursor, WKDBColumns.WKChannelColumns.robot);
        channel.username = WKCursor.readString(cursor, WKDBColumns.WKChannelColumns.username);
        channel.avatarCacheKey = WKCursor.readString(cursor, WKDBColumns.WKChannelColumns.avatar_cache_key);
        channel.flame = WKCursor.readInt(cursor, WKDBColumns.WKChannelColumns.flame);
        channel.flameSecond = WKCursor.readInt(cursor, WKDBColumns.WKChannelColumns.flame_second);
        channel.deviceFlag = WKCursor.readInt(cursor, WKDBColumns.WKChannelColumns.device_flag);
        channel.parentChannelID = WKCursor.readString(cursor, WKDBColumns.WKChannelColumns.parent_channel_id);
        channel.parentChannelType = WKCursor.readByte(cursor, WKDBColumns.WKChannelColumns.parent_channel_type);
        String extra = WKCursor.readString(cursor, WKDBColumns.WKChannelColumns.localExtra);
        String remoteExtra = WKCursor.readString(cursor, WKDBColumns.WKChannelColumns.remote_extra);
        channel.localExtra = WKCommonUtils.str2HashMap(extra);
        channel.remoteExtraMap = WKCommonUtils.str2HashMap(remoteExtra);
        return channel;
    }
}
