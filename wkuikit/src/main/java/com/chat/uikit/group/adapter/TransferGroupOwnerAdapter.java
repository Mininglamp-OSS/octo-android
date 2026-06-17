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

package com.chat.uikit.group.adapter;

import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.ui.components.CheckBox;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.StringUtils;
import com.chat.uikit.R;
import com.chat.uikit.group.GroupMemberEntity;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.List;

/**
 * 转让群主成员选择适配器 (单选)。
 *
 * <p>与 {@link DeleteGroupMemberAdapter} 视觉风格保持一致 (相同的 CheckBox 图标 / 头像 / 昵称布局),
 * 但语义是单选: 同一时刻只允许一个 item 处于 checked=1。Activity 层通过遍历 list 互斥实现,
 * 适配器自身不维护单选状态。
 */
public class TransferGroupOwnerAdapter extends BaseQuickAdapter<GroupMemberEntity, BaseViewHolder> {

    private String searchKey;

    public TransferGroupOwnerAdapter(@Nullable List<GroupMemberEntity> data) {
        super(R.layout.item_transfer_group_owner, data);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, GroupMemberEntity item, @NonNull List<?> payloads) {
        super.convert(holder, item, payloads);
        Object payload = payloads.isEmpty() ? null : payloads.get(0);
        if (payload instanceof GroupMemberEntity) {
            GroupMemberEntity entity = (GroupMemberEntity) payload;
            CheckBox checkBox = holder.getView(R.id.checkBox);
            checkBox.setChecked(entity.checked == 1, true);
            checkBox.setHasBorder(entity.checked == 1);
            checkBox.setDrawBackground(entity.checked == 1);
        }
    }

    @Override
    protected void convert(@NonNull BaseViewHolder helper, GroupMemberEntity item) {
        CheckBox checkBox = helper.getView(R.id.checkBox);
        checkBox.setResId(getContext(), R.mipmap.round_check2);
        checkBox.setDrawBackground(item.checked == 1);
        checkBox.setHasBorder(true);
        checkBox.setBorderColor(ContextCompat.getColor(getContext(), R.color.layoutColor));
        checkBox.setSize(24);
        checkBox.setStrokeWidth(AndroidUtilities.dp(2));
        checkBox.setColor(Theme.colorAccount, ContextCompat.getColor(getContext(), R.color.layoutColor));
        checkBox.setVisibility(View.VISIBLE);
        checkBox.setEnabled(true);
        checkBox.setChecked(item.checked == 1, true);

        if (item.member == null) return;
        String showName = TextUtils.isEmpty(item.member.memberRemark) ? item.member.memberName : item.member.memberRemark;
        AvatarView avatarView = helper.getView(R.id.avatarView);
        avatarView.showAvatar(item.member.memberUID, WKChannelType.PERSONAL, item.member.memberAvatarCacheKey);

        if (!TextUtils.isEmpty(searchKey)) {
            helper.setText(
                    R.id.nameTv, StringUtils.findSearch(
                            Theme.colorAccount,
                            showName == null ? "" : showName,
                            searchKey
                    )
            );
        } else {
            helper.setText(R.id.nameTv, showName == null ? "" : showName);
        }
    }

    public void setSearchKey(String searchKey) {
        this.searchKey = searchKey;
        notifyItemRangeChanged(0, getData().size());
    }
}
