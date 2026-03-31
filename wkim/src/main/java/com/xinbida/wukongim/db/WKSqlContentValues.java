package com.xinbida.wukongim.db;

import android.content.ContentValues;
import android.text.TextUtils;

import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKConversationMsg;
import com.xinbida.wukongim.entity.WKConversationMsgExtra;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKMsgExtra;
import com.xinbida.wukongim.entity.WKMsgReaction;
import com.xinbida.wukongim.entity.WKMsgSetting;
import com.xinbida.wukongim.entity.WKReminder;
import com.xinbida.wukongim.utils.WKLoggerUtils;
import com.xinbida.wukongim.utils.WKTypeUtils;

import org.json.JSONException;
import org.json.JSONObject;


class WKSqlContentValues {
    private final static String TAG = "WKSqlContentValues";

    static ContentValues getContentValuesWithMsg(WKMsg msg) {
        ContentValues contentValues = new ContentValues();
        if (msg == null) {
            return contentValues;
        }
        if (msg.setting == null) {
            msg.setting = new WKMsgSetting();
        }
        contentValues.put(WKDBColumns.WKMessageColumns.message_id, msg.messageID);
        contentValues.put(WKDBColumns.WKMessageColumns.message_seq, msg.messageSeq);

        contentValues.put(WKDBColumns.WKMessageColumns.order_seq, msg.orderSeq);
        contentValues.put(WKDBColumns.WKMessageColumns.timestamp, msg.timestamp);
        contentValues.put(WKDBColumns.WKMessageColumns.from_uid, msg.fromUID);
        contentValues.put(WKDBColumns.WKMessageColumns.channel_id, msg.channelID);
        contentValues.put(WKDBColumns.WKMessageColumns.channel_type, msg.channelType);
        contentValues.put(WKDBColumns.WKMessageColumns.is_deleted, msg.isDeleted);
        contentValues.put(WKDBColumns.WKMessageColumns.type, msg.type);
        contentValues.put(WKDBColumns.WKMessageColumns.content, msg.content);
        contentValues.put(WKDBColumns.WKMessageColumns.status, msg.status);
        contentValues.put(WKDBColumns.WKMessageColumns.created_at, msg.createdAt);
        contentValues.put(WKDBColumns.WKMessageColumns.updated_at, msg.updatedAt);
        contentValues.put(WKDBColumns.WKMessageColumns.voice_status, msg.voiceStatus);
        contentValues.put(WKDBColumns.WKMessageColumns.client_msg_no, msg.clientMsgNO);
        contentValues.put(WKDBColumns.WKMessageColumns.flame, msg.flame);
        contentValues.put(WKDBColumns.WKMessageColumns.flame_second, msg.flameSecond);
        contentValues.put(WKDBColumns.WKMessageColumns.viewed, msg.viewed);
        contentValues.put(WKDBColumns.WKMessageColumns.viewed_at, msg.viewedAt);
        contentValues.put(WKDBColumns.WKMessageColumns.topic_id, msg.topicID);
        contentValues.put(WKDBColumns.WKMessageColumns.expire_time, msg.expireTime);
        contentValues.put(WKDBColumns.WKMessageColumns.expire_timestamp, msg.expireTimestamp);
        byte setting = WKTypeUtils.getInstance().getMsgSetting(msg.setting);
        contentValues.put(WKDBColumns.WKMessageColumns.setting, setting);
        if (msg.baseContentMsgModel != null) {
            contentValues.put(WKDBColumns.WKMessageColumns.searchable_word, msg.baseContentMsgModel.getSearchableWord());
        }
        contentValues.put(WKDBColumns.WKMessageColumns.extra, msg.getLocalMapExtraString());
        return contentValues;
    }

    static ContentValues getContentValuesWithCoverMsg(WKConversationMsg wkConversationMsg, boolean isSync) {
        ContentValues contentValues = new ContentValues();
        if (wkConversationMsg == null) {
            return contentValues;
        }
        contentValues.put(WKDBColumns.WKCoverMessageColumns.channel_id, wkConversationMsg.channelID);
        contentValues.put(WKDBColumns.WKCoverMessageColumns.channel_type, wkConversationMsg.channelType);
        contentValues.put(WKDBColumns.WKCoverMessageColumns.last_client_msg_no, wkConversationMsg.lastClientMsgNO);
        contentValues.put(WKDBColumns.WKCoverMessageColumns.last_msg_timestamp, wkConversationMsg.lastMsgTimestamp);
        contentValues.put(WKDBColumns.WKCoverMessageColumns.last_msg_seq, wkConversationMsg.lastMsgSeq);
        contentValues.put(WKDBColumns.WKCoverMessageColumns.unread_count, wkConversationMsg.unreadCount);
        contentValues.put(WKDBColumns.WKCoverMessageColumns.parent_channel_id, wkConversationMsg.parentChannelID);
        contentValues.put(WKDBColumns.WKCoverMessageColumns.parent_channel_type, wkConversationMsg.parentChannelType);
        if (isSync) {
            contentValues.put(WKDBColumns.WKCoverMessageColumns.version, wkConversationMsg.version);
        }
        contentValues.put(WKDBColumns.WKCoverMessageColumns.is_deleted, wkConversationMsg.isDeleted);
        contentValues.put(WKDBColumns.WKCoverMessageColumns.extra, wkConversationMsg.getLocalExtraString());
        return contentValues;
    }

    static ContentValues getContentValuesWithChannel(WKChannel channel) {
        ContentValues contentValues = new ContentValues();
        if (channel == null) {
            return contentValues;
        }
        contentValues.put(WKDBColumns.WKChannelColumns.channel_id, channel.channelID);
        contentValues.put(WKDBColumns.WKChannelColumns.channel_type, channel.channelType);
        contentValues.put(WKDBColumns.WKChannelColumns.channel_name, channel.channelName);
        contentValues.put(WKDBColumns.WKChannelColumns.channel_remark, channel.channelRemark);
        contentValues.put(WKDBColumns.WKChannelColumns.avatar, channel.avatar);
        contentValues.put(WKDBColumns.WKChannelColumns.top, channel.top);
        contentValues.put(WKDBColumns.WKChannelColumns.save, channel.save);
        contentValues.put(WKDBColumns.WKChannelColumns.mute, channel.mute);
        contentValues.put(WKDBColumns.WKChannelColumns.forbidden, channel.forbidden);
        contentValues.put(WKDBColumns.WKChannelColumns.invite, channel.invite);
        contentValues.put(WKDBColumns.WKChannelColumns.status, channel.status);
        contentValues.put(WKDBColumns.WKChannelColumns.is_deleted, channel.isDeleted);
        contentValues.put(WKDBColumns.WKChannelColumns.follow, channel.follow);
        contentValues.put(WKDBColumns.WKChannelColumns.version, channel.version);
        contentValues.put(WKDBColumns.WKChannelColumns.show_nick, channel.showNick);
        contentValues.put(WKDBColumns.WKChannelColumns.created_at, channel.createdAt);
        contentValues.put(WKDBColumns.WKChannelColumns.updated_at, channel.updatedAt);
        contentValues.put(WKDBColumns.WKChannelColumns.online, channel.online);
        contentValues.put(WKDBColumns.WKChannelColumns.last_offline, channel.lastOffline);
        contentValues.put(WKDBColumns.WKChannelColumns.receipt, channel.receipt);
        contentValues.put(WKDBColumns.WKChannelColumns.robot, channel.robot);
        contentValues.put(WKDBColumns.WKChannelColumns.category, channel.category);
        contentValues.put(WKDBColumns.WKChannelColumns.username, channel.username);
        contentValues.put(WKDBColumns.WKChannelColumns.avatar_cache_key, TextUtils.isEmpty(channel.avatarCacheKey) ? "" : channel.avatarCacheKey);
        contentValues.put(WKDBColumns.WKChannelColumns.flame, channel.flame);
        contentValues.put(WKDBColumns.WKChannelColumns.flame_second, channel.flameSecond);
        contentValues.put(WKDBColumns.WKChannelColumns.device_flag, channel.deviceFlag);
        contentValues.put(WKDBColumns.WKChannelColumns.parent_channel_id, channel.parentChannelID);
        contentValues.put(WKDBColumns.WKChannelColumns.parent_channel_type, channel.parentChannelType);

        if (channel.localExtra != null) {
            JSONObject jsonObject = new JSONObject(channel.localExtra);
            contentValues.put(WKDBColumns.WKChannelColumns.localExtra, jsonObject.toString());
        }
        if (channel.remoteExtraMap != null) {
            JSONObject jsonObject = new JSONObject(channel.remoteExtraMap);
            contentValues.put(WKDBColumns.WKChannelColumns.remote_extra, jsonObject.toString());
        }
        return contentValues;
    }

    static ContentValues getContentValuesWithChannelMember(WKChannelMember channelMember) {
        ContentValues contentValues = new ContentValues();
        if (channelMember == null) {
            return contentValues;
        }
        contentValues.put(WKDBColumns.WKChannelMembersColumns.channel_id, channelMember.channelID);
        contentValues.put(WKDBColumns.WKChannelMembersColumns.channel_type, channelMember.channelType);
        contentValues.put(WKDBColumns.WKChannelMembersColumns.member_invite_uid, channelMember.memberInviteUID);
        contentValues.put(WKDBColumns.WKChannelMembersColumns.member_uid, channelMember.memberUID);
        contentValues.put(WKDBColumns.WKChannelMembersColumns.member_name, channelMember.memberName);
        contentValues.put(WKDBColumns.WKChannelMembersColumns.member_remark, channelMember.memberRemark);
        contentValues.put(WKDBColumns.WKChannelMembersColumns.member_avatar, channelMember.memberAvatar);
        contentValues.put(WKDBColumns.WKChannelMembersColumns.memberAvatarCacheKey, TextUtils.isEmpty(channelMember.memberAvatarCacheKey) ? "" : channelMember.memberAvatarCacheKey);
        contentValues.put(WKDBColumns.WKChannelMembersColumns.role, channelMember.role);
        contentValues.put(WKDBColumns.WKChannelMembersColumns.is_deleted, channelMember.isDeleted);
        contentValues.put(WKDBColumns.WKChannelMembersColumns.version, channelMember.version);
        contentValues.put(WKDBColumns.WKChannelMembersColumns.status, channelMember.status);
        contentValues.put(WKDBColumns.WKChannelMembersColumns.robot, channelMember.robot);
        contentValues.put(WKDBColumns.WKChannelMembersColumns.forbidden_expiration_time, channelMember.forbiddenExpirationTime);
        contentValues.put(WKDBColumns.WKChannelMembersColumns.created_at, channelMember.createdAt);
        contentValues.put(WKDBColumns.WKChannelMembersColumns.updated_at, channelMember.updatedAt);

        if (channelMember.extraMap != null) {
            JSONObject jsonObject = new JSONObject(channelMember.extraMap);
            contentValues.put(WKDBColumns.WKChannelMembersColumns.extra, jsonObject.toString());
        }

        return contentValues;
    }

    static ContentValues getContentValuesWithMsgReaction(WKMsgReaction reaction) {
        ContentValues contentValues = new ContentValues();
        if (reaction == null) {
            return contentValues;
        }
        contentValues.put("channel_id", reaction.channelID);
        contentValues.put("channel_type", reaction.channelType);
        contentValues.put("message_id", reaction.messageID);
        contentValues.put("uid", reaction.uid);
        contentValues.put("name", reaction.name);
        contentValues.put("is_deleted", reaction.isDeleted);
        contentValues.put("seq", reaction.seq);
        contentValues.put("emoji", reaction.emoji);
        contentValues.put("created_at", reaction.createdAt);
        return contentValues;
    }

    static ContentValues getCVWithMsgExtra(WKMsgExtra extra) {
        ContentValues cv = new ContentValues();
        cv.put("channel_id", extra.channelID);
        cv.put("channel_type", extra.channelType);
        cv.put("message_id", extra.messageID);
        cv.put("readed", extra.readed);
        cv.put("readed_count", extra.readedCount);
        cv.put("unread_count", extra.unreadCount);
        cv.put("revoke", extra.revoke);
        cv.put("revoker", extra.revoker);
        cv.put("extra_version", extra.extraVersion);
        cv.put("is_mutual_deleted", extra.isMutualDeleted);
        cv.put("content_edit", extra.contentEdit);
        cv.put("edited_at", extra.editedAt);
        cv.put("need_upload", extra.needUpload);
        cv.put("is_pinned", extra.isPinned);
        return cv;
    }

    static ContentValues getCVWithReminder(WKReminder reminder) {
        ContentValues cv = new ContentValues();
        cv.put("channel_id", reminder.channelID);
        cv.put("channel_type", reminder.channelType);
        cv.put("reminder_id", reminder.reminderID);
        cv.put("message_id", reminder.messageID);
        cv.put("message_seq", reminder.messageSeq);
        cv.put("uid", reminder.uid);
        cv.put("type", reminder.type);
        cv.put("is_locate", reminder.isLocate);
        cv.put("text", reminder.text);
        cv.put("version", reminder.version);
        cv.put("done", reminder.done);
        cv.put("need_upload", reminder.needUpload);
        cv.put("publisher", reminder.publisher);

        if (reminder.data != null) {
            JSONObject jsonObject = new JSONObject();
            for (Object key : reminder.data.keySet()) {
                try {
                    jsonObject.put(String.valueOf(key), reminder.data.get(key));
                } catch (JSONException e) {
                    WKLoggerUtils.getInstance().e(TAG, "getCVWithReminder error");
                }
            }
            cv.put("data", jsonObject.toString());
        }
        return cv;
    }

    static ContentValues getCVWithExtra(WKConversationMsgExtra extra) {
        ContentValues cv = new ContentValues();
        cv.put("channel_id", extra.channelID);
        cv.put("channel_type", extra.channelType);
        cv.put("browse_to", extra.browseTo);
        cv.put("keep_message_seq", extra.keepMessageSeq);
        cv.put("keep_offset_y", extra.keepOffsetY);
        cv.put("draft", extra.draft);
        cv.put("draft_updated_at", extra.draftUpdatedAt);
        cv.put("version", extra.version);
        return cv;
    }
}
