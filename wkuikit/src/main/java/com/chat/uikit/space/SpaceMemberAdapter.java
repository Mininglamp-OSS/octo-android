package com.chat.uikit.space;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.ui.components.AvatarView;
import com.chat.uikit.R;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

public class SpaceMemberAdapter extends BaseQuickAdapter<SpaceEntity.SpaceMember, BaseViewHolder> {

    public SpaceMemberAdapter() {
        super(R.layout.item_space_member);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, SpaceEntity.SpaceMember member) {
        holder.setText(R.id.nameTv, member.name);

        // Load avatar using AvatarView pattern
        AvatarView avatarView = holder.getView(R.id.avatarView);
        WKChannel channel = new WKChannel(member.uid, WKChannelType.PERSONAL);
        channel.channelName = member.name;
        avatarView.showAvatar(channel);

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
