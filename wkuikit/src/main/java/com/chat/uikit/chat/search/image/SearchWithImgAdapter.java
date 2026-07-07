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

package com.chat.uikit.chat.search.image;

import android.view.View;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseMultiItemQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.glide.GlideUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.views.pinnedsectionitemdecoration.utils.FullSpanUtil;
import com.chat.uikit.R;
import com.xinbida.wukongim.msgmodel.WKVideoContent;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 3/23/21 10:33 AM
 * 搜索聊天图片
 */
class SearchWithImgAdapter extends BaseMultiItemQuickAdapter<SearchImgEntity, BaseViewHolder> {
    private final int wH;
    private final ICLick iLick;

    SearchWithImgAdapter(int showWidth, ICLick iLick) {
        super();
        wH = showWidth;
        this.iLick = iLick;
        addItemType(0, R.layout.item_search_msg_img_layout);
        addItemType(1, R.layout.item_search_msg_img_date_layout);
    }

    @Override
    public void onAttachedToRecyclerView(@NotNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        FullSpanUtil.onAttachedToRecyclerView(recyclerView, this, 1);
    }

    @Override
    public void onViewAttachedToWindow(@NotNull BaseViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        FullSpanUtil.onViewAttachedToWindow(holder, this, 1);
    }

    @Override
    protected void convert(@NotNull BaseViewHolder baseViewHolder, SearchImgEntity chatImgEntity) {
        switch (chatImgEntity.getItemType()) {
            case 1 -> baseViewHolder.setText(R.id.dateTv, chatImgEntity.date);
            case 0 -> {
                ImageView imageView = baseViewHolder.getView(R.id.imageView);
                imageView.getLayoutParams().width = wH;
                imageView.getLayoutParams().height = wH;
                GlideUtils.getInstance().showImg(getContext(), chatImgEntity.url, imageView);
                ImageView playIv = baseViewHolder.getView(R.id.playIv);
                playIv.setVisibility(chatImgEntity.originalContent instanceof WKVideoContent ? View.VISIBLE : View.GONE);
                List<PopupMenuItem> list = new ArrayList<>();
                list.add(new PopupMenuItem(getContext().getString(R.string.forward), R.mipmap.msg_forward, () -> iLick.onForward(chatImgEntity)));
                list.add(new PopupMenuItem(getContext().getString(R.string.uikit_go_to_chat_item), R.mipmap.msg_message, () -> iLick.onClick(chatImgEntity)));
                WKDialogUtils.getInstance().setViewLongClickPopup(baseViewHolder.getView(R.id.imageView), list);
            }
        }
    }

    public interface ICLick {
        void onClick(SearchImgEntity searchChatImgEntity);
        void onForward(SearchImgEntity searchChatImgEntity);
    }
}
