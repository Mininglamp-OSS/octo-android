package com.chat.uikit.thread.adapter;

import android.text.TextUtils;
import android.view.View;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.uikit.R;
import com.chat.uikit.thread.service.entity.ThreadEntity;

import org.jetbrains.annotations.NotNull;

public class ThreadListAdapter extends BaseQuickAdapter<ThreadEntity, BaseViewHolder> {

    public ThreadListAdapter() {
        super(R.layout.item_thread_layout);
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
    }
}
