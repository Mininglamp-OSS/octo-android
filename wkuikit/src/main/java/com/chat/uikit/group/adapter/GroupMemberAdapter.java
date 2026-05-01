package com.chat.uikit.group.adapter;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.View;

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
 * 外部成员标识 v2（YUJ-87 → YUJ-189 回归）：昵称后拼「@SpaceName」灰紫色单行后缀，
 * viewer-relative 通过 {@link ExternalViewerResolver} 判定。缩略图格子很窄，
 * 后缀随昵称一起按 {@code ellipsize="end"} 截断（显示 "..."）——对齐企微：
 * 想看完整 @SpaceName 去「查看全部成员 (N)」列表页（{@code WKAllMembersActivity}）
 * 一人一行展示。本回归撤掉 YUJ-184 PR#141 的二次换行方案。
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
            helper.setText(R.id.nameTv, buildNameWithExternalSuffix(showName, item));
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

    /**
     * 拼 "昵称 @SpaceName"（灰紫色 0x8B5CF6）后缀。非外部成员或缺来源 Space 名时
     * 返回原始昵称（等效 YUJ-87 的单行方案）。TextView 的 {@code lines=1 +
     * ellipsize=end} 保证窄格子内整体以 "..." 尾截断，YUJ-189 撤掉 YUJ-184 的
     * 第二行布局，用「查看全部成员 (N)」入口作为完整显示的兜底。
     */
    private CharSequence buildNameWithExternalSuffix(String showName, WKChannelMember item) {
        String viewerSpaceId = MsgModel.getInstance().getCurrentSpaceId();
        ExternalViewerResolver.Resolution res = ExternalViewerResolver.resolveFromExtras(
                item == null ? null : item.extraMap, viewerSpaceId);
        if (!res.isExternal() || TextUtils.isEmpty(res.getSourceSpaceName())) {
            return showName == null ? "" : showName;
        }
        String suffix = getContext().getString(
                R.string.external_member_space_suffix, res.getSourceSpaceName());
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        ssb.append(showName == null ? "" : showName);
        int start = ssb.length();
        ssb.append(' ').append(suffix);
        ssb.setSpan(new ForegroundColorSpan(0xFF8B5CF6),
                start, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ssb;
    }
}
