package com.chat.uikit.thread.adapter;

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.uikit.R;
import com.chat.uikit.thread.service.ThreadModel;
import com.chat.uikit.thread.service.entity.ThreadEntity;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKUIConversationMsg;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class ThreadListAdapter extends BaseQuickAdapter<ThreadEntity, BaseViewHolder> {

    private String groupNo;
    private final Set<String> visitedShortIds = new HashSet<>();

    public ThreadListAdapter() {
        super(R.layout.item_thread_layout);
    }

    public void setGroupNo(String groupNo) {
        this.groupNo = groupNo;
    }

    public void markVisited(String shortId) {
        visitedShortIds.add(shortId);
    }

    @Override
    protected void convert(@NotNull BaseViewHolder helper, ThreadEntity item) {
        helper.setText(R.id.threadNameTv, item.name);

        // 统计行：N条消息 · N位成员 · 时间
        StringBuilder stats = new StringBuilder();
        stats.append(item.message_count).append("条消息");
        stats.append(" · ").append(item.member_count).append("位成员");
        if (!TextUtils.isEmpty(item.updated_at)) {
            stats.append(" · ").append(item.updated_at);
        }
        helper.setText(R.id.threadStatsTv, stats.toString());

        // 最后消息预览
        if (!TextUtils.isEmpty(item.last_message_content)) {
            helper.getView(R.id.lastMessageTv).setVisibility(View.VISIBLE);
            String preview = item.last_message_content;
            if (!TextUtils.isEmpty(item.last_message_sender_name)) {
                preview = item.last_message_sender_name + ": " + preview;
            }
            helper.setText(R.id.lastMessageTv, preview);
        } else {
            helper.getView(R.id.lastMessageTv).setVisibility(View.GONE);
        }

        // 未读数气泡：进入过的子区视为已读，否则查本地会话，fallback 到服务端值
        TextView unreadBadge = helper.getView(R.id.threadUnreadBadge);
        int unread = 0;
        if (visitedShortIds.contains(item.short_id)) {
            unread = 0;
        } else if (!TextUtils.isEmpty(groupNo)) {
            String threadChannelId = ThreadModel.getInstance().buildChannelId(groupNo, item.short_id);
            WKUIConversationMsg threadConv = WKIM.getInstance().getConversationManager()
                    .getUIConversationMsg(threadChannelId, WKChannelType.COMMUNITY_TOPIC);
            if (threadConv != null) {
                unread = threadConv.unreadCount;
            } else {
                unread = item.unread_count;
            }
        }
        if (unread > 0) {
            unreadBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
            unreadBadge.setVisibility(View.VISIBLE);
        } else {
            unreadBadge.setVisibility(View.GONE);
        }
    }
}
