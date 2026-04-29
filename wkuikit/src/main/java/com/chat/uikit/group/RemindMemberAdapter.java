package com.chat.uikit.group;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.external.ExternalViewerResolver;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.utils.StringUtils;
import com.chat.base.views.NoEventRecycleView;
import com.chat.uikit.R;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RemindMemberAdapter extends BaseQuickAdapter<GroupMemberEntity, BaseViewHolder> {
    /**
     * 外部 Space 后缀灰紫色 (YUJ-134 / 对齐 Web createMentionSuggestion 视觉规范 &
     * WKChatBaseProvider.appendExternalSpaceSuffix 的气泡染色值)。
     */
    private static final int EXTERNAL_SPACE_SUFFIX_COLOR = 0xFF8B5CF6;

    private final String channelID;
    private final byte channelType;
    private String searchKey;
    private int page = 1;
    private final String loginUID = WKConfig.getInstance().getUid();

    public RemindMemberAdapter(String channelID, byte channelType) {
        super(R.layout.item_choose_remind_layout);
        this.channelID = channelID;
        this.channelType = channelType;
    }

    @Override
    protected void convert(@NonNull BaseViewHolder baseViewHolder, GroupMemberEntity groupMemberEntity) {
        AvatarView avatarView = baseViewHolder.getView(R.id.avatarView);
        avatarView.setSize(30);
        if (groupMemberEntity.member == null) {
            baseViewHolder.setText(R.id.nameTv, R.string.at_all);
            avatarView.imageView.setImageResource(R.mipmap.icon_mention_all);
        } else {
            TextView nameTv = baseViewHolder.getView(R.id.nameTv);
            String showName = groupMemberEntity.member.remark;
            if (TextUtils.isEmpty(showName)) {
                showName = TextUtils.isEmpty(groupMemberEntity.member.memberRemark) ? groupMemberEntity.member.memberName : groupMemberEntity.member.memberRemark;
            }
            // YUJ-134: 计算外部成员 @SpaceName 后缀（跨 Space 才显示），
            // 与 Web `createMentionSuggestion` 的 resolveExternalForViewer 语义一致，
            // 避免用户在 @候选菜单里看不出对方是外部成员造成跨 Space 数据泄漏。
            String externalSpaceSuffix = resolveExternalSpaceName(groupMemberEntity.member,
                    WKSharedPreferencesUtil.getInstance().getSPWithUID("current_space_id"));
            CharSequence baseName;
            if (TextUtils.isEmpty(searchKey)) {
                baseName = showName == null ? "" : showName;
            } else {
                baseName = StringUtils.findSearch(Theme.colorAccount, showName == null ? "" : showName, searchKey);
            }
            if (TextUtils.isEmpty(externalSpaceSuffix)) {
                nameTv.setText(baseName);
            } else {
                SpannableStringBuilder builder = new SpannableStringBuilder(baseName);
                appendExternalSpaceSuffix(builder, externalSpaceSuffix);
                nameTv.setText(builder);
            }

            avatarView.showAvatar(groupMemberEntity.member.memberUID, WKChannelType.PERSONAL, groupMemberEntity.member.memberAvatarCacheKey);
        }
    }

    /**
     * 读取成员 extraMap 中的 home_space_* / is_external / source_space_name，
     * 通过 {@link ExternalViewerResolver} 按 viewer 当前 Space 判定是否为外部成员。
     *
     * <p>返回非空字符串表示应该在昵称后拼 " @<SpaceName>"；返回 null 代表：
     * <ul>
     *   <li>成员处于当前 viewer 的同一 Space（不渲染后缀）</li>
     *   <li>缺少 home_space_id 且降级字段 is_external == 0 / source_space_name 为空</li>
     *   <li>member 或 extraMap 为空</li>
     * </ul>
     *
     * <p>可见性改为 package-private 是为了让 JVM 层单测直接喂 {@link WKChannelMember}
     * 实例覆盖 4 个场景（跨 Space 显示 / 同 Space 不显示 / 空 sourceSpaceName 不显示 /
     * legacy 降级）。viewerSpaceId 独立参数传入，方便测试时避开
     * {@link WKSharedPreferencesUtil} 单例引导。
     */
    static String resolveExternalSpaceName(WKChannelMember member, String viewerSpaceId) {
        if (member == null || member.extraMap == null || member.extraMap.isEmpty()) {
            return null;
        }
        Map<String, Object> extras = toStringKeyMap(member.extraMap);
        ExternalViewerResolver.Resolution resolution =
                ExternalViewerResolver.resolveFromExtras(extras, viewerSpaceId);
        if (!resolution.isExternal()) {
            return null;
        }
        String name = resolution.getSourceSpaceName();
        // 不使用 TextUtils.isEmpty 判空：wkuikit 的单元测试配置了
        // unitTests.returnDefaultValues = true，TextUtils 会被 stub 成默认值，
        // 直接走 String 自身的 isEmpty 才能让 "空 sourceSpaceName 不渲染" 场景在
        // JVM 层单测中稳定可验证。
        return (name == null || name.isEmpty()) ? null : name;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> toStringKeyMap(HashMap raw) {
        // WKChannelMember.extraMap 是 raw HashMap（历史遗留类型），这里显式转一层
        // 避免把 raw 类型漏给 resolver；空键被过滤以防 NPE。
        Map<String, Object> out = new HashMap<>();
        for (Object entry : raw.entrySet()) {
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) entry;
            Object k = e.getKey();
            if (k == null) continue;
            out.put(String.valueOf(k), e.getValue());
        }
        return out;
    }

    private static void appendExternalSpaceSuffix(SpannableStringBuilder builder, String spaceName) {
        int start = builder.length();
        builder.append(" @").append(spaceName);
        builder.setSpan(
                new ForegroundColorSpan(EXTERNAL_SPACE_SUFFIX_COLOR),
                start,
                builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
    }


    public void onNormal() {
        page = 1;
        this.searchKey = "";
        getChannelMember(true);
    }

    public void onSearch(String keyword) {
        this.searchKey = keyword;
        page = 1;
        getChannelMember(TextUtils.isEmpty(keyword));
    }

    private void getChannelMember(boolean isNormal) {
        int size = 100;
        WKIM.getInstance().getChannelMembersManager().getWithPageOrSearch(channelID, channelType, searchKey, page, size, (list, b) -> resort(list, isNormal));
    }

    private void resort(List<WKChannelMember> list, boolean isNormal) {
        List<GroupMemberEntity> memberList = new ArrayList<>();
        // @所有人 对所有群成员可见，对齐 Web 端行为（移除管理员角色限制）
        if (page == 1 && isNormal) {
            memberList.add(new GroupMemberEntity());
        }
        for (WKChannelMember member : list) {
            if (member != null && member.isDeleted == 0 && !member.memberUID.equals(loginUID)) {
                memberList.add(new GroupMemberEntity(member));
            }
        }
        if (page == 1) {
            setList(memberList);
        } else {
            addData(memberList);
        }
        ((NoEventRecycleView) getRecyclerView()).setItemCount(getItemCount());
    }

    public String getSearchKey() {
        return searchKey;
    }
}
