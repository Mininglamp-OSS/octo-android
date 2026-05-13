package com.chat.uikit.group.adapter;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.external.ExternalViewerResolver;
import com.chat.base.msgitem.WKChannelMemberRole;
import com.chat.base.realname.RealnameBadgeResolver;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.ui.components.RoundTextView;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.utils.StringUtils;
import com.chat.uikit.R;
import com.chat.uikit.enity.AllGroupMemberEntity;
import com.chat.uikit.message.MsgModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;

import org.jetbrains.annotations.NotNull;

/**
 * 2020-12-11 15:23
 * 所有成员
 *
 * 外部成员标识 v2（YUJ-87 / 对齐 web #1013）：
 *   成员名后内联「@SpaceName」后缀（灰色、同一行），取代 v1 紫色「外部」角标 +
 *   「来自 XX」副标题。当 viewer 当前 Space == 成员 home Space 时不渲染后缀。
 *   判定委托 {@link ExternalViewerResolver}，保证与 web / iOS 语义一致。
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

        // v2 「@SpaceName」后缀（viewer-relative）—— 灰色、同行；空串即不渲染
        String viewerSpaceId = MsgModel.getInstance().getCurrentSpaceId();
        ExternalViewerResolver.Resolution external =
                ExternalViewerResolver.resolveFromExtras(channelMember.extraMap, viewerSpaceId);
        String suffix = "";
        if (external.isExternal() && !TextUtils.isEmpty(external.getSourceSpaceName())) {
            suffix = getContext().getString(R.string.external_member_space_suffix, external.getSourceSpaceName());
        }

        CharSequence nameCs = buildNameWithSuffix(showName, suffix);
        baseViewHolder.setText(R.id.nameTv, nameCs);

        Boolean memberVerified = RealnameBadgeResolver.isVerifiedTriState(channelMember);
        boolean verified;
        if (memberVerified != null) {
            verified = memberVerified;
        } else {
            WKChannel ch = WKIM.getInstance().getChannelManager().getChannel(channelMember.memberUID, WKChannelType.PERSONAL);
            verified = RealnameBadgeResolver.isVerified(ch);
        }
        baseViewHolder.setGone(R.id.realnameBadgeIv, !verified);

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

        // v1 紫色「外部」角标 + 「来自 XX」副标题已被 v2 @SpaceName 后缀替代（YUJ-87）。
        // 清理历史可能残留的 v1 View，保证复用 ViewHolder 不出现幽灵角标/副标题。
        View oldExternalBadge = nameRow.findViewWithTag("external_badge");
        if (oldExternalBadge != null) nameRow.removeView(oldExternalBadge);
        TextView sourceSpaceTv = baseViewHolder.getView(R.id.sourceSpaceTv);
        if (sourceSpaceTv != null) sourceSpaceTv.setVisibility(View.GONE);
    }

    /**
     * 拼装「{昵称}{searchHighlight} @SpaceName」——  suffix 部分用 color999 灰色 span。
     * 搜索高亮只作用于昵称段，不染后缀。
     */
    private CharSequence buildNameWithSuffix(String showName, String suffix) {
        CharSequence namePart;
        if (!TextUtils.isEmpty(searchKey)) {
            namePart = StringUtils.findSearch(Theme.colorAccount, showName, searchKey);
        } else {
            namePart = new SpannableString(showName == null ? "" : showName);
        }
        if (TextUtils.isEmpty(suffix)) return namePart;

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        ssb.append(namePart);
        int start = ssb.length();
        // 前导空格
        ssb.append(' ').append(suffix);
        int grey = ContextCompat.getColor(getContext(), R.color.color999);
        ssb.setSpan(new ForegroundColorSpan(grey), start, ssb.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ssb;
    }

    public void setSearchKey(String searchKey) {
        this.searchKey = searchKey;
        notifyItemRangeChanged(0, getItemCount());
    }
}
