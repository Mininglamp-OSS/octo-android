package com.chat.uikit.space;

import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.uikit.R;

public class SpaceListAdapter extends BaseQuickAdapter<SpaceEntity, BaseViewHolder> {

    public SpaceListAdapter() {
        super(R.layout.item_space_list);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, SpaceEntity entity) {
        TextView avatarTv = holder.getView(R.id.avatarTv);
        String initial = entity.name != null && !entity.name.isEmpty()
                ? entity.name.substring(0, 1).toUpperCase() : "S";
        avatarTv.setText(initial);
        holder.setText(R.id.nameTv, entity.name);
    }
}
