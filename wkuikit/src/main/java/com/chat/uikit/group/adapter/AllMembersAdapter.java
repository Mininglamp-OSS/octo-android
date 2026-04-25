package com.chat.uikit.group.adapter;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.msgitem.WKChannelMemberRole;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.ui.components.RoundTextView;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.utils.StringUtils;
import com.chat.uikit.R;
import com.chat.uikit.enity.AllGroupMemberEntity;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelMemberExtras;
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
        avatarView.showAvatar(channelMember.memberUID, WKChannelType.PERSONAL, channelMember.memberAvatarCacheKey);
        if (channelMember.role == WKChannelMemberRole.admin) {
            roleTv.setVisibility(View.VISIBLE);
            roleTv.setText(R.string.group_owner);
            roleTv.setBackGroundColor(ContextCompat.getColor(getContext(), R.color.colorAccent));
        } else if (channelMember.role == WKChannelMemberRole.manager) {
            roleTv.setVisibility(View.VISIBLE);
            roleTv.setText(R.string.group_manager);
            roleTv.setBackGroundColor(ContextCompat.getColor(getContext(), R.color.colorAccent));
        } else {
            roleTv.setVisibility(View.GONE);
        }

        // AI 标识
        LinearLayout nameRow = (LinearLayout) baseViewHolder.getView(R.id.nameTv).getParent();
        View oldBadge = nameRow.findViewWithTag("ai_badge");
        if (oldBadge != null) nameRow.removeView(oldBadge);
        if (channelMember.robot == 1) {
            TextView aiBadge = new TextView(getContext());
            aiBadge.setTag("ai_badge");
            aiBadge.setText("AI");
            aiBadge.setTextColor(Color.WHITE);
            aiBadge.setTextSize(10f);
            aiBadge.setTypeface(Typeface.DEFAULT_BOLD);
            aiBadge.setBackgroundResource(R.drawable.bg_ai_badge);
            int hPad = AndroidUtilities.dp(5f);
            int vPad = AndroidUtilities.dp(1f);
            aiBadge.setPadding(hPad, vPad, hPad, vPad);
            nameRow.addView(aiBadge, LayoutHelper.createLinear(
                    LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL, 5, 0, 0, 0));
        }

        // 外部成员标识：紫色「外部」角标 + 「来自 {source_space_name}」副标题
        View oldExternalBadge = nameRow.findViewWithTag("external_badge");
        if (oldExternalBadge != null) nameRow.removeView(oldExternalBadge);
        TextView sourceSpaceTv = baseViewHolder.getView(R.id.sourceSpaceTv);
        if (isExternalMember(channelMember)) {
            TextView externalBadge = new TextView(getContext());
            externalBadge.setTag("external_badge");
            externalBadge.setText(R.string.external_member_badge);
            externalBadge.setTextColor(ContextCompat.getColor(getContext(), R.color.external_member_badge_text));
            externalBadge.setTextSize(10f);
            externalBadge.setTypeface(Typeface.DEFAULT_BOLD);
            externalBadge.setBackgroundResource(R.drawable.bg_external_member_badge);
            int hPad = AndroidUtilities.dp(5f);
            int vPad = AndroidUtilities.dp(1f);
            externalBadge.setPadding(hPad, vPad, hPad, vPad);
            nameRow.addView(externalBadge, LayoutHelper.createLinear(
                    LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL, 5, 0, 0, 0));

            String sourceSpaceName = sourceSpaceName(channelMember);
            if (!TextUtils.isEmpty(sourceSpaceName)) {
                sourceSpaceTv.setText(getContext().getString(R.string.external_member_source_format, sourceSpaceName));
                sourceSpaceTv.setVisibility(View.VISIBLE);
            } else {
                sourceSpaceTv.setVisibility(View.GONE);
            }
        } else {
            sourceSpaceTv.setVisibility(View.GONE);
        }
    }

    private static boolean isExternalMember(WKChannelMember member) {
        if (member == null || member.extraMap == null) return false;
        Object v = member.extraMap.get(WKChannelMemberExtras.isExternal);
        if (v instanceof Number) return ((Number) v).intValue() == 1;
        if (v instanceof Boolean) return (Boolean) v;
        return false;
    }

    private static String sourceSpaceName(WKChannelMember member) {
        if (member == null || member.extraMap == null) return null;
        Object v = member.extraMap.get(WKChannelMemberExtras.sourceSpaceName);
        return v == null ? null : v.toString();
    }

    public void setSearchKey(String searchKey) {
        this.searchKey = searchKey;
        notifyItemRangeChanged(0, getItemCount());
    }
}
