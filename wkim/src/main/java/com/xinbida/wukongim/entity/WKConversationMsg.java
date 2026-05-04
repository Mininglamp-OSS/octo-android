package com.xinbida.wukongim.entity;


import androidx.annotation.NonNull;

import com.xinbida.wukongim.utils.DateUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

/**
 * 2019-11-09 15:00
 * 会话列表消息
 */
public class WKConversationMsg {
    //频道id
    public String channelID;
    //频道类型
    public byte channelType;
    //最后一条消息本地ID
    public String lastClientMsgNO;
    //是否删除
    public int isDeleted;
    //服务器同步版本号
    public long version;
    //最后一条消息时间
    public long lastMsgTimestamp;
    //未读消息数量
    public int unreadCount;
    //最后一条消息序号
    public long lastMsgSeq;
    //扩展字段
    public HashMap localExtraMap;
    public WKConversationMsgExtra msgExtra;
    public String parentChannelID;
    public byte parentChannelType;
    /**
     * YUJ-326 · 本行归属 Space（32-hex space_id，个人/空 Space 时为 ""）。syncChat
     * 写入时从 server 响应 {@code WKSyncConvMsg.space_id} 取；历史行 migration 后为空
     * 字符串，首次 upgrade 会强制 clearAll 洗掉（见 {@link
     * com.xinbida.wukongim.manager.ConversationManager#onFirstUpgradeAfterV2WashIfNeeded}）
     * 避免永久滞留。per-Space clearForSpace / queryMaxVersionForSpace 按此列过滤。
     */
    public String spaceID = "";

    public WKConversationMsg() {
        this.lastMsgTimestamp = DateUtils.getInstance().getCurrentSeconds();
    }

    public String getLocalExtraString() {
        String extras = "";
        if (localExtraMap != null) {
            JSONObject jsonObject = new JSONObject();
            for (Object key : localExtraMap.keySet()) {
                try {
                    jsonObject.put(key.toString(), localExtraMap.get(key));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            extras = jsonObject.toString();
        }
        return extras;
    }

    @NonNull
    @Override
    public String toString() {
        return "WKConversationMsg{" +
                ", channelID='" + channelID + '\'' +
                ", channelType=" + channelType +
                ", lastClientMsgNO='" + lastClientMsgNO + '\'' +
                ", isDeleted=" + isDeleted +
                ", version=" + version +
                ", lastMsgTimestamp=" + lastMsgTimestamp +
                ", lastMsgSeq=" + lastMsgSeq +
                ", unreadCount=" + unreadCount +
                ", localExtraMap=" + localExtraMap +
                '}';
    }
}
