package com.chat.uikit.space;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.glide.GlideUtils;
import com.chat.base.config.WKApiConfig;
import com.chat.uikit.R;
import com.xinbida.wukongim.entity.WKChannelType;

public class SpaceMemberAdapter extends BaseQuickAdapter<SpaceEntity.SpaceMember, BaseViewHolder> {

    public SpaceMemberAdapter() {
        super(R.layout.item_space_member);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, SpaceEntity.SpaceMember member) {
        holder.setText(R.id.nameTv, member.name);

        // Load avatar
        com.chat.base.views.CircleImageView avatarIv = holder.getView(R.id.avatarIv);
        String avatarUrl = WKApiConfig.baseUrl + "users/" + member.uid + "/avatar";
        GlideUtils.getInstance().showImg(getContext(), avatarUrl, avatarIv);

        // Role badge
        TextView roleTv = holder.getView(R.id.roleTv);
        switch (member.role) {
            case 2:
                roleTv.setVisibility(View.VISIBLE);
                roleTv.setText(R.string.space_role_owner);
                break;
            case 1:
                roleTv.setVisibility(View.VISIBLE);
                roleTv.setText(R.string.space_role_admin);
                break;
            default:
                roleTv.setVisibility(View.GONE);
                break;
        }
    }
}
