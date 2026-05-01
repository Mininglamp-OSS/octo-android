package com.chat.uikit.group.adapter;

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.external.ExternalViewerResolver;
import com.chat.base.msgitem.WKChannelMemberRole;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.AvatarView;
import com.chat.uikit.R;
import com.chat.uikit.message.MsgModel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.List;

/**
 * 2019-12-06 15:21
 * 群成员适配器（群详情顶部缩略图网格）
 *
 * 外部成员标识 v2.1（YUJ-184）：缩略图格子很窄，单行后缀会被 ellipsize 截断（只看到 "张三 @"），
 * 改为双行 —— nameTv 放昵称，spaceNameTv 放 @SpaceName（10sp 灰色，visibility=gone for 非外部），
 * 对齐企微样式。viewer-relative 判定仍通过 {@link ExternalViewerResolver}。
 */
public class GroupMemberAdapter extends BaseQuickAdapter<WKChannelMember, BaseViewHolder> {
    public GroupMemberAdapter(@Nullable List<WKChannelMember> data) {
        super(R.layout.item_group_member_layout, data);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder helper, WKChannelMember item) {

        if (item.memberUID.equalsIgnoreCase("-1")) {
            helper.setImageResource(R.id.handlerIv, R.mipmap.icon_chat_add);
            helper.setGone(R.id.handlerIv, false);
            helper.setGone(R.id.userLayout, true);
        } else if (item.memberUID.equalsIgnoreCase("-2")) {
            helper.setImageResource(R.id.handlerIv, R.mipmap.icon_chat_delete);
            helper.setGone(R.id.handlerIv, false);
            helper.setGone(R.id.userLayout, true);
        } else {
            String showName = item.remark;
            if (TextUtils.isEmpty(showName)) {
                showName = TextUtils.isEmpty(item.memberRemark) ? item.memberName : item.memberRemark;
            }
            AvatarView avatarView = helper.getView(R.id.avatarView);
            avatarView.setSize(50f);
            helper.setText(R.id.nameTv, showName == null ? "" : showName);
            bindExternalSpaceName(helper, item);
            avatarView.showAvatar(item.memberUID, WKChannelType.PERSONAL, item.memberAvatarCacheKey);
            helper.setGone(R.id.handlerIv, true);
            helper.setGone(R.id.userLayout, false);
            if (item.role == WKChannelMemberRole.admin) {
                avatarView.onlineTv.setVisibility(View.VISIBLE);
                avatarView.spotView.setVisibility(View.GONE);
                avatarView.onlineTv.setText(R.string.group_owner);
                avatarView.onlineTv.setBackgroundResource(R.drawable.radian_normal_layout);
                avatarView.onlineTv.setTextColor(ContextCompat.getColor(getContext(), R.color.colorAccent));
            } else if (item.role == WKChannelMemberRole.manager) {
                avatarView.onlineTv.setText(R.string.group_manager);
                avatarView.onlineTv.setTextColor(Theme.colorAccount);
                avatarView.onlineTv.setVisibility(View.VISIBLE);
                avatarView.onlineTv.setBackgroundResource(R.drawable.radian_normal_layout);
            } else {
                avatarView.spotView.setVisibility(View.GONE);
                avatarView.onlineTv.setVisibility(View.GONE);
            }
        }
    }

    private void bindExternalSpaceName(@NonNull BaseViewHolder helper, WKChannelMember item) {
        TextView spaceNameTv = helper.getView(R.id.spaceNameTv);
        String viewerSpaceId = MsgModel.getInstance().getCurrentSpaceId();
        ExternalViewerResolver.Resolution res = ExternalViewerResolver.resolveFromExtras(
                item == null ? null : item.extraMap, viewerSpaceId);
        if (!res.isExternal() || TextUtils.isEmpty(res.getSourceSpaceName())) {
            spaceNameTv.setVisibility(View.GONE);
            spaceNameTv.setText("");
            return;
        }
        String suffix = getContext().getString(
                R.string.external_member_space_suffix, res.getSourceSpaceName());
        spaceNameTv.setText(suffix);
        spaceNameTv.setVisibility(View.VISIBLE);
    }
}

