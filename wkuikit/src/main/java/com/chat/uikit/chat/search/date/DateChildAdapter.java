package com.chat.uikit.chat.search.date;

import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.ui.Theme;
import com.chat.uikit.R;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 3/23/21 6:04 PM
 * 通过日期搜索聊天记录
 */
class DateChildAdapter extends BaseQuickAdapter<SearchWithDateEntity, BaseViewHolder> {
    private final int wH;

    public DateChildAdapter(List<SearchWithDateEntity> dateEntityList, int wH) {
        super(R.layout.item_search_msg_with_date_child_layout, dateEntityList);
        this.wH = wH;
    }

    @Override
    protected void convert(@NotNull BaseViewHolder baseViewHolder, SearchWithDateEntity chatWithDateEntity) {
        LinearLayout contentLayout = baseViewHolder.getView(R.id.contentLayout);
        contentLayout.getLayoutParams().width = wH;
        contentLayout.getLayoutParams().height = wH;
        baseViewHolder.setTextColor(R.id.toDayTv, Theme.colorAccount);
        if (!chatWithDateEntity.isNull) {
            baseViewHolder.setText(R.id.dayTv, chatWithDateEntity.day);
            TextView dayTv = baseViewHolder.getView(R.id.dayTv);
            dayTv.setBackground(Theme.getBackground(chatWithDateEntity.selected ? Theme.colorAccount : ContextCompat.getColor(getContext(), R.color.transparent), 40f, 40, 40));
//            baseViewHolder.setBackgroundResource(R.id.dayTv, chatWithDateEntity.selected ? R.drawable.search_chat_with_date_selected : R.drawable.search_chat_with_date_normal);
            if (chatWithDateEntity.selected) {
                baseViewHolder.setTextColor(R.id.dayTv, ContextCompat.getColor(getContext(), R.color.white));
            } else {
                baseViewHolder.setTextColor(R.id.dayTv, ContextCompat.getColor(getContext(), chatWithDateEntity.dayCount > 0 ? R.color.colorDark : R.color.color999));
            }

            if (chatWithDateEntity.isToDay) {
                baseViewHolder.setVisible(R.id.toDayTv, true);
            } else {
                baseViewHolder.setVisible(R.id.toDayTv, false);
            }
            baseViewHolder.setVisible(R.id.dayTv, true);
        } else {
            baseViewHolder.setVisible(R.id.toDayTv, false);
            baseViewHolder.setVisible(R.id.dayTv, false);
        }

    }
}
