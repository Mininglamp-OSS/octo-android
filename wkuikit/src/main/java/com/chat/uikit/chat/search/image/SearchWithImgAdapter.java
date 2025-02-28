package com.chat.uikit.chat.search.image;

import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseMultiItemQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.glide.GlideUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.views.pinnedsectionitemdecoration.utils.FullSpanUtil;
import com.chat.uikit.R;

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
