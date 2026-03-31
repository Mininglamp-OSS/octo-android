package com.xinbida.wukongim.entity;


import android.text.TextUtils;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.db.MsgDbManager;
import com.xinbida.wukongim.db.ReminderDBManager;
import com.xinbida.wukongim.interfaces.IReminderResult;
import com.xinbida.wukongim.manager.ChannelManager;

import java.util.HashMap;
import java.util.List;

/**
 * 2019-12-01 17:50
 * UI层显示最近会话消息
 */
public class WKUIConversationMsg {
    public long lastMsgSeq;
    public String clientMsgNo;
    //频道ID
    public String channelID;
    //频道类型
    public byte channelType;
    //最后一条消息时间
    public long lastMsgTimestamp;
    //消息频道
    private WKChannel wkChannel;
    //消息正文
    private WKMsg wkMsg;
    //未读消息数量
    public int unreadCount;
    public int isDeleted;
    private WKConversationMsgExtra remoteMsgExtra;
    //高亮内容[{type:1,text:'[有人@你]'}]
    private List<WKReminder> reminderList;
    //扩展字段
    public HashMap<String, Object> localExtraMap;
    public String parentChannelID;
    public byte parentChannelType;


    public WKMsg getWkMsg() {
        if (wkMsg == null) {
            wkMsg = MsgDbManager.getInstance().queryWithClientMsgNo(clientMsgNo);
            if (wkMsg != null && wkMsg.isDeleted == 1) wkMsg = null;
        }
        return wkMsg;
    }

    public void setWkMsg(WKMsg wkMsg) {
        this.wkMsg = wkMsg;
    }

    public WKChannel getWkChannel() {
        if (wkChannel == null) {
            wkChannel = ChannelManager.getInstance().getChannel(channelID, channelType);
        }
        return wkChannel;
    }

    public void setWkChannel(WKChannel wkChannel) {
        this.wkChannel = wkChannel;
    }

    public List<WKReminder> getReminderList() {
        if (reminderList == null) {
            reminderList = WKIM.getInstance().getReminderManager().getReminders(channelID, channelType);
//            reminderList = ReminderDBManager.getInstance().queryWithChannelAndDone(channelID, channelType, 0);
        }

        return reminderList;
    }

//    public void getReminderListAsync(IReminderResult iReminderResult) {
//        ReminderDBManager.getInstance().queryWithChannelAndDoneAsync(channelID, channelType, 0, iReminderResult);
//    }

    public void setReminderList(List<WKReminder> list) {
        this.reminderList = list;
    }

    public WKConversationMsgExtra getRemoteMsgExtra() {
        return remoteMsgExtra;
    }

    public void setRemoteMsgExtra(WKConversationMsgExtra extra) {
        this.remoteMsgExtra = extra;
    }

    public long getSortTime() {
        if (getRemoteMsgExtra() != null && !TextUtils.isEmpty(getRemoteMsgExtra().draft)) {
            return getRemoteMsgExtra().draftUpdatedAt;
        }
        return lastMsgTimestamp;
    }

}
