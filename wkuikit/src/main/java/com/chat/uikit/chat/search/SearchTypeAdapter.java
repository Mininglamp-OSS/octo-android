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
