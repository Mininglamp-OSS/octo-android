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
import com.chat.uikit.R;
import com.chat.uikit.group.GroupMemberEntity;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.List;

/**
 * 2020-01-31 14:20
 * 删除群成员
 */
public class DeleteGroupMemberAdapter extends BaseQuickAdapter<GroupMemberEntity, BaseViewHolder> {
    public DeleteGroupMemberAdapter(@Nullable List<GroupMemberEntity> data) {
        super(R.layout.item_delete_group_member_layout, data);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, GroupMemberEntity item, @NonNull List<?> payloads) {
        super.convert(holder, item, payloads);
        GroupMemberEntity groupMemberEntity = (GroupMemberEntity) payloads.get(0);
        if (groupMemberEntity != null) {
            CheckBox checkBox = holder.getView(R.id.checkBox);
            checkBox.setChecked(groupMemberEntity.checked == 1, true);
            checkBox.setHasBorder(groupMemberEntity.checked == 1);
            checkBox.setDrawBackground(item.checked == 1);
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
        checkBox.setColor(item.checked != 2 ? Theme.colorAccount : Theme.colorAccountDisable, ContextCompat.getColor(getContext(), R.color.layoutColor));
        checkBox.setVisibility(View.VISIBLE);
        checkBox.setEnabled(item.checked != 2);
        checkBox.setChecked(item.checked == 1, true);

        String showName = item.member.remark;
        if (TextUtils.isEmpty(showName)) {
            showName = TextUtils.isEmpty(item.member.memberRemark) ? item.member.memberName : item.member.memberRemark;
        }
        helper.setText(R.id.nameTv, showName);
        AvatarView avatarView = helper.getView(R.id.avatarView);
        avatarView.showAvatar(item.member.memberUID, WKChannelType.PERSONAL, item.member.memberAvatarCacheKey);
    }
}
