package com.chat.uikit.group.adapter;

import android.text.SpannableString;
import android.text.TextUtils;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.msgitem.WKChannelMemberRole;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.ui.components.RoundTextView;
import com.chat.base.utils.StringUtils;
import com.chat.uikit.R;
import com.chat.uikit.enity.AllGroupMemberEntity;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;

import org.jetbrains.annotations.NotNull;

/**
 * 2020-12-11 15:23
 * 所有成员
 */
public class AllMembersAdapter extends BaseQuickAdapter<AllGroupMemberEntity, BaseViewHolder> {
    private String searchKey;

    public AllMembersAdapter() {
        super(R.layout.item_all_members_layout);
    }

    @Override
    protected void convert(@NotNull BaseViewHolder baseViewHolder, AllGroupMemberEntity entity) {
        WKChannelMember channelMember = entity.getChannelMember();

        String showName = channelMember.remark;
        if (TextUtils.isEmpty(showName)) {
            showName = TextUtils.isEmpty(channelMember.memberRemark) ? channelMember.memberName : channelMember.memberRemark;
        }
        if (!TextUtils.isEmpty(searchKey)) {
            SpannableString key = StringUtils.findSearch(Theme.colorAccount, showName, searchKey);
            baseViewHolder.setText(R.id.nameTv, key);
        } else {
            baseViewHolder.setText(R.id.nameTv, showName);
        }
        AvatarView avatarView = baseViewHolder.getView(R.id.avatarView);
        avatarView.setSize(45);
        if (entity.getOnLine() == 1) {
            avatarView.spotView.setVisibility(View.VISIBLE);
            avatarView.onlineTv.setVisibility(View.GONE);
            baseViewHolder.setGone(R.id.timeTv, true);
        } else {
            avatarView.spotView.setVisibility(View.GONE);
            if (!TextUtils.isEmpty(entity.getLastOnlineTime())) {
                avatarView.onlineTv.setVisibility(View.VISIBLE);
                avatarView.onlineTv.setText(entity.getLastOnlineTime());
                baseViewHolder.setGone(R.id.timeTv, true);
            } else {
                avatarView.onlineTv.setVisibility(View.GONE);
                String time = String.format("%s %s", getContext().getString(R.string.last_seen_time), entity.getLastOfflineTime());
                baseViewHolder.setText(R.id.timeTv, time);
                baseViewHolder.setGone(R.id.timeTv, TextUtils.isEmpty(entity.getLastOfflineTime()));
            }
        }
        //   baseViewHolder.setText(R.id.nameTv, showName);
        RoundTextView roleTv = baseViewHolder.getView(R.id.roleTv);
        avatarView.showAvatar(channelMember.memberUID, WKChannelType.PERSONAL, channelMember.memberAvatar);
        if (channelMember.role == WKChannelMemberRole.admin) {
            roleTv.setVisibility(View.VISIBLE);
            roleTv.setText(R.string.group_owner);
            roleTv.setBackGroundColor(ContextCompat.getColor(getContext(), R.color.colorFFC107));
        } else if (channelMember.role == WKChannelMemberRole.manager) {
            roleTv.setVisibility(View.VISIBLE);
            roleTv.setText(R.string.group_manager);
            roleTv.setBackGroundColor(ContextCompat.getColor(getContext(), R.color.colorAccent));
        } else {
            roleTv.setVisibility(View.GONE);
        }
    }

    public void setSearchKey(String searchKey) {
        this.searchKey = searchKey;
        notifyItemRangeChanged(0, getItemCount());
    }
}
