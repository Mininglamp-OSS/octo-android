package com.chat.uikit.enity;

import android.text.TextUtils;

import com.chat.base.config.WKConfig;
import com.chat.base.utils.WKReader;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKReminder;
import com.xinbida.wukongim.entity.WKUIConversationMsg;

import java.util.ArrayList;
import java.util.List;

public class ChatConversationMsg {
    public WKUIConversationMsg uiConversationMsg;
    public boolean isRefreshChannelInfo;
    public boolean isResetCounter;
    public boolean isResetReminders;
    public boolean isResetContent;
    public boolean isResetTime;
    public boolean isResetTyping;
    public boolean isRefreshStatus;
    public long typingStartTime = 0;
    public String typingUserName;
    public int isTop;
    public List<ChatConversationMsg> childList;
    private final String loginUID;

    public ChatConversationMsg(WKUIConversationMsg msg) {
        this.uiConversationMsg = msg;
        if (uiConversationMsg.getWkChannel() != null) {
            isTop = uiConversationMsg.getWkChannel().top;
        }
        loginUID = WKConfig.getInstance().getUid();
        WKIMUtils.getInstance().resetMsgProhibitWord(msg.getWkMsg());
    }

    public int getUnReadCount() {
        if (WKReader.isEmpty(childList))
            return uiConversationMsg.unreadCount;
        int count = 0;
        for (ChatConversationMsg msg : childList) {
            count += msg.uiConversationMsg.unreadCount;
        }
        return count;
    }

    public List<WKReminder> getReminders() {
        List<WKReminder> list = new ArrayList<>();
        if (WKReader.isEmpty(childList)) {
            list.addAll(uiConversationMsg.getReminderList());
        } else {
            for (ChatConversationMsg msg : childList) {
                list.addAll(msg.uiConversationMsg.getReminderList());
            }
        }
        List<WKReminder> resultList = new ArrayList<>();
        for (WKReminder reminder : list) {
            if (!TextUtils.isEmpty(reminder.publisher) && !reminder.publisher.equals(loginUID)) {
                resultList.add(reminder);
            }
        }
        return resultList;
    }

    private WKMsg lastMsg;
    private String lastClientMsgNo = "";

    public WKMsg getMsg() {
        if (WKReader.isEmpty(childList))
            return uiConversationMsg.getWkMsg();
        String clientMsgNo = "";
        long lastMsgTimestamp = 0;
        for (ChatConversationMsg msg : childList) {
            if (msg.uiConversationMsg.lastMsgTimestamp > lastMsgTimestamp) {
                lastMsgTimestamp = msg.uiConversationMsg.lastMsgTimestamp;
                clientMsgNo = msg.uiConversationMsg.clientMsgNo;
            }
        }
        if (lastClientMsgNo.equals(clientMsgNo) && lastMsg != null) {
            return lastMsg;
        }

        lastClientMsgNo = clientMsgNo;
        lastMsg = WKIM.getInstance().getMsgManager().getWithClientMsgNO(lastClientMsgNo);
        return lastMsg;
    }
}
