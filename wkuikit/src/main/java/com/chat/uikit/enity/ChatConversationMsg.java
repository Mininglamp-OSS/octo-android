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

package com.chat.uikit.enity;

import android.text.TextUtils;

import com.chat.base.config.WKConfig;
import com.chat.base.utils.WKReader;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
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
    public int isCalling = 0;

    // Section header 支持
    public boolean isSectionHeader = false;
    public String sectionId;
    public String sectionTitle;
    public int sectionGroupCount = -1;
    public int sectionUnreadCount = 0;
    public boolean sectionHasMention = false;

    public ChatConversationMsg(WKUIConversationMsg msg) {
        this.uiConversationMsg = msg;
        if (uiConversationMsg.getWkChannel() != null) {
            isTop = uiConversationMsg.getWkChannel().top;
        }
        loginUID = WKConfig.getInstance().getUid();
        if (!TextUtils.isEmpty(msg.clientMsgNo)) {
            WKIMUtils.getInstance().resetMsgProhibitWord(msg.getWkMsg());
        }
    }

    /** Section header 专用构造 */
    public ChatConversationMsg(String sectionId, String sectionTitle) {
        this.isSectionHeader = true;
        this.sectionId = sectionId;
        this.sectionTitle = sectionTitle;
        this.uiConversationMsg = null;
        this.loginUID = "";
    }

    /**
     * Content signature for DiffUtil.areContentsTheSame.
     *
     * NOTE (): For section headers, contentHash drives real diff
     * behaviour — headers are re-constructed per filterAndDisplay with new
     * identities but comparable content. For normal conversation rows,
     * oldList and newList share the same ChatConversationMsg instances
     * (via channelMap lookups), so contentHash effectively returns true for
     * identity-equal rows — all content-change refreshes are driven by the
     * channel-listener side-channel (notifyRecycler + isRefreshChannelInfo
     * etc.). Channel-field coverage below exists as defense-in-depth for
     * code paths that mutate channel state without firing the listener.
     *
     *  original contract:
     *   Section header: sectionId + sectionTitle + sectionGroupCount
     *                   + sectionUnreadCount + sectionHasMention
     *   Normal:         channelID + channelType + lastMsgTimestamp + unreadCount
     *                   + clientMsgNo + channel.top + channel.mute
     *                   + typingUserName + typingStartTime + isTop + isCalling
     *                   + draft + draftUpdatedAt + reminderList.size()
     *
     *  P1 hardening: added channelName / channelRemark / forbidden /
     * category / robot / avatarCacheKey so DiffUtil can detect these even
     * when the channel listener is not fired.
     *
     *  P2-1: removed the 7 isRefreshXxx / isResetXxx signal bits —
     * they are signals, not data, and are harmless-but-useless while all
     * three lists share instance references, but would cause false-positive
     * dirty diffs if callers ever clone rows.
     */
    public long contentHash() {
        long h = 1125899906842597L;
        if (isSectionHeader) {
            h = 31 * h + (sectionId != null ? sectionId.hashCode() : 0);
            h = 31 * h + (sectionTitle != null ? sectionTitle.hashCode() : 0);
            h = 31 * h + sectionGroupCount;
            h = 31 * h + sectionUnreadCount;
            h = 31 * h + (sectionHasMention ? 1 : 0);
            return h;
        }
        if (uiConversationMsg == null) return h;
        h = 31 * h + (uiConversationMsg.channelID != null ? uiConversationMsg.channelID.hashCode() : 0);
        h = 31 * h + uiConversationMsg.channelType;
        h = 31 * h + Long.hashCode(uiConversationMsg.lastMsgTimestamp);
        h = 31 * h + uiConversationMsg.unreadCount;
        h = 31 * h + (uiConversationMsg.clientMsgNo != null ? uiConversationMsg.clientMsgNo.hashCode() : 0);
        // : P1 defensive — cover channel fields that UI reads directly.
        // Without these in contentHash, DiffUtil can only detect changes via the
        // channel-listener side-channel (isRefreshChannelInfo). If any new code
        // path mutates these fields without triggering the listener, the row
        // silently fails to refresh. Defensive cost is negligible (short strings).
        WKChannel ch = uiConversationMsg.getWkChannel();
        if (ch != null) {
            h = 31 * h + ch.top;
            h = 31 * h + ch.mute;
            h = 31 * h + (ch.channelName != null ? ch.channelName.hashCode() : 0);
            h = 31 * h + (ch.channelRemark != null ? ch.channelRemark.hashCode() : 0);
            h = 31 * h + ch.forbidden;
            h = 31 * h + (ch.category != null ? ch.category.hashCode() : 0);
            h = 31 * h + ch.robot;
            h = 31 * h + (ch.avatarCacheKey != null ? ch.avatarCacheKey.hashCode() : 0);
        }
        h = 31 * h + (typingUserName != null ? typingUserName.hashCode() : 0);
        h = 31 * h + Long.hashCode(typingStartTime);
        h = 31 * h + isTop;
        h = 31 * h + isCalling;
        // draft
        if (uiConversationMsg.getRemoteMsgExtra() != null) {
            String draft = uiConversationMsg.getRemoteMsgExtra().draft;
            h = 31 * h + (draft != null ? draft.hashCode() : 0);
            h = 31 * h + Long.hashCode(uiConversationMsg.getRemoteMsgExtra().draftUpdatedAt);
        }
        // reminder 数量（@mention 红点触发点）
        if (uiConversationMsg.getReminderList() != null) {
            h = 31 * h + uiConversationMsg.getReminderList().size();
        }
        return h;
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
            if (TextUtils.isEmpty(reminder.publisher) || (!TextUtils.isEmpty(reminder.publisher) && !reminder.publisher.equals(loginUID))) {
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
