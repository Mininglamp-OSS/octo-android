package com.chat.uikit.chat.search;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.endpoint.entity.SearchChatContentMenu;
import com.chat.base.ui.Theme;
import com.chat.uikit.R;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 3/22/21 5:11 PM
 * 搜索类型
 */
class SearchTypeAdapter extends BaseQuickAdapter<SearchChatContentMenu, BaseViewHolder> {
    public SearchTypeAdapter(List<SearchChatContentMenu> data) {
        super(R.layout.item_search_message_type_layout, data);
    }

    @Override
    protected void convert(@NotNull BaseViewHolder baseViewHolder, SearchChatContentMenu menu) {
        baseViewHolder.setText(R.id.nameTv, menu.text);
        baseViewHolder.setTextColor(R.id.nameTv,Theme.colorAccount);
        Theme.setPressedBackground(baseViewHolder.getView(R.id.nameTv));
    }
}
