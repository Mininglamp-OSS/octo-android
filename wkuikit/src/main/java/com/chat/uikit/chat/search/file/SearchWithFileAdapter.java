package com.chat.uikit.chat.search.file;

import android.widget.ImageView;

import com.chad.library.adapter.base.BaseMultiItemQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKTimeUtils;
import com.chat.uikit.R;
import com.chat.uikit.chat.provider.WKFileProvider;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class SearchWithFileAdapter extends BaseMultiItemQuickAdapter<SearchFileEntity, BaseViewHolder> {
    private final IClick iClick;

    SearchWithFileAdapter(IClick iClick) {
        super();
        this.iClick = iClick;
        addItemType(SearchFileEntity.TYPE_FILE, R.layout.item_search_msg_file_layout);
        addItemType(SearchFileEntity.TYPE_DATE_HEADER, R.layout.item_search_msg_img_date_layout);
    }

    @Override
    protected void convert(@NotNull BaseViewHolder holder, SearchFileEntity entity) {
        if (entity.getItemType() == SearchFileEntity.TYPE_DATE_HEADER) {
            holder.setText(R.id.dateTv, entity.date);
        } else {
            holder.setText(R.id.fileNameTv, entity.fileName);
            holder.setText(R.id.fileSizeTv, WKFileProvider.formatFileSize(entity.fileSize));
            holder.setText(R.id.fileTimeTv,
                    WKTimeUtils.getInstance().getTimeString(entity.message.getTimestamp() * 1000));

            ImageView fileIconIv = holder.getView(R.id.fileIconIv);
            WKFileProvider.setFileIcon(fileIconIv, entity.extension, entity.fileName);

            List<PopupMenuItem> list = new ArrayList<>();
            list.add(new PopupMenuItem(getContext().getString(R.string.forward), R.mipmap.msg_forward,
                    () -> iClick.onForward(entity)));
            list.add(new PopupMenuItem(getContext().getString(R.string.uikit_go_to_chat_item), R.mipmap.msg_message,
                    () -> iClick.onClick(entity)));
            WKDialogUtils.getInstance().setViewLongClickPopup(holder.itemView, list);
        }
    }

    public interface IClick {
        void onClick(SearchFileEntity entity);
        void onForward(SearchFileEntity entity);
    }
}
