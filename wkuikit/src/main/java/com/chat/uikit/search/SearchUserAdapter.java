/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.uikit.search;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseMultiItemQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.external.ExternalViewerResolver;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.AvatarView;
import com.chat.uikit.R;
import com.chat.uikit.enity.UserInfo;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.List;

/**
 * 2019-11-20 11:34
 * 搜索好友信息
 */
public class SearchUserAdapter extends BaseMultiItemQuickAdapter<SearchUserEntity, BaseViewHolder> {
    /**
     * 外部 Space 后缀灰紫色（ / 对齐  RemindMemberAdapter &
     * WKChatBaseProvider.appendExternalSpaceSuffix 的气泡染色规范）。
     */
    private static final int EXTERNAL_SPACE_SUFFIX_COLOR = 0xFF8B5CF6;

    public SearchUserAdapter(@Nullable List<SearchUserEntity> data) {
        super(data);
        addItemType(0, R.layout.item_search_user_layout);
        addItemType(1, R.layout.item_nodata_layout);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder helper, SearchUserEntity item) {
        switch (item.getItemType()) {
            case 1:
                break;
            case 0:
                AvatarView avatarView = helper.getView(R.id.avatarView);
                avatarView.showAvatar(item.data.uid, WKChannelType.PERSONAL);
                TextView nameTv = helper.getView(R.id.nameTv);
                // : 在搜索结果昵称后拼外部成员 " @SpaceName"，与 Web
                //  / Android  @Mention 候选菜单语义一致 —
                // 避免用户在加好友 / 通讯录搜索时看不出对方是跨 Space 外部成员，
                // 减少跨 Space 数据泄漏的误触发。
                String externalSpaceSuffix = resolveExternalSpaceName(item.data,
                        WKSharedPreferencesUtil.getInstance().getSPWithUID("current_space_id"));
                String baseName = item.data.name == null ? "" : item.data.name;
                if (TextUtils.isEmpty(externalSpaceSuffix)) {
                    nameTv.setText(baseName);
                } else {
                    SpannableStringBuilder builder = new SpannableStringBuilder(baseName);
                    appendExternalSpaceSuffix(builder, externalSpaceSuffix);
                    nameTv.setText(builder);
                }
                helper.setGone(R.id.applyBtn, !item.showApply || !TextUtils.isEmpty(item.data.uid) && item.data.uid.equals(WKConfig.getInstance().getUid()));
                helper.getView(R.id.applyBtn).setAlpha(item.status == 0 ? 1 : 0.2f);
                Button applyBtn = helper.getView(R.id.applyBtn);
                applyBtn.getBackground().setTint(Theme.colorAccount);
                break;
        }
    }

    /**
     * 读取 UserInfo 上的 home_space_* / is_external / source_space_name，
     * 通过 {@link ExternalViewerResolver} 按 viewer 当前 Space 判定是否为外部成员。
     *
     * <p>返回非空字符串表示应拼 " @SpaceName" 后缀；返回 null 代表同 Space /
     * 无 home_space_id 且 legacy 字段也不满足 / UserInfo 为空 —— 这三种场景下
     * 都不渲染后缀，与 RemindMemberAdapter.resolveExternalSpaceName 行为一致。
     *
     * <p>包可见 + static 签名是为了支持 JVM 单测覆盖 4 类场景（跨 Space / 同 Space /
     * 空 sourceSpaceName / legacy 降级），避免 Robolectric。viewerSpaceId 独立参数
     * 传入同样是为了测试友好（绕开 WKSharedPreferencesUtil 单例）。
     */
    static String resolveExternalSpaceName(UserInfo user, String viewerSpaceId) {
        if (user == null) {
            return null;
        }
        ExternalViewerResolver.MemberOrgData org = new ExternalViewerResolver.MemberOrgData(
                user.home_space_id,
                user.home_space_name,
                user.is_external,
                user.source_space_name
        );
        ExternalViewerResolver.Resolution resolution =
                ExternalViewerResolver.resolve(org, viewerSpaceId);
        if (!resolution.isExternal()) {
            return null;
        }
        String name = resolution.getSourceSpaceName();
        // 不使用 TextUtils.isEmpty 判空：wkuikit 的单元测试配置了
        // unitTests.returnDefaultValues = true，TextUtils 会 stub 成默认值，
        // 直接走 String.isEmpty 让 "空 sourceSpaceName 不渲染" 在 JVM 单测中可验证。
        return (name == null || name.isEmpty()) ? null : name;
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
}
