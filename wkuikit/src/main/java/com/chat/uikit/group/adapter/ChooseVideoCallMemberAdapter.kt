package com.chat.uikit.group.adapter

import android.text.TextUtils
import android.view.View
import androidx.core.content.ContextCompat
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.ui.Theme
import com.chat.base.ui.components.AvatarView
import com.chat.base.ui.components.CheckBox
import com.chat.base.utils.AndroidUtilities
import com.chat.uikit.R
import com.chat.uikit.group.GroupMemberEntity
import com.xinbida.wukongim.entity.WKChannelType

class ChooseVideoCallMemberAdapter :
    BaseQuickAdapter<GroupMemberEntity, BaseViewHolder>(R.layout.item_choose_video_call_memberes_layout) {

    override fun convert(holder: BaseViewHolder, item: GroupMemberEntity, payloads: List<Any>) {
        super.convert(holder, item, payloads)
        val entity = payloads[0] as GroupMemberEntity
        val checkBox = holder.getView<CheckBox>(R.id.checkBox)
        val isChecked = entity.checked == 1 || entity.isCanCheck == 0
        checkBox.setChecked(isChecked, true)
        checkBox.setDrawBackground(isChecked)
    }

    override fun convert(
        holder: BaseViewHolder,
        item: GroupMemberEntity
    ) {
        val avatarView: AvatarView = holder.getView(R.id.avatarView)
        avatarView.showAvatar(
            item.member.memberUID,
            WKChannelType.PERSONAL,
            item.member.memberAvatarCacheKey
        )
        var showName = ""
        if (!TextUtils.isEmpty(item.member.remark)) showName = item.member.remark
        if (TextUtils.isEmpty(showName)) showName = item.member.memberRemark
        if (TextUtils.isEmpty(showName)) showName = item.member.memberName

        holder.setText(
            R.id.nameTv,
            showName
        )


        val isChecked = item.checked == 1 || item.isCanCheck == 0
        val checkBox: CheckBox = holder.getView(R.id.checkBox)
        checkBox.setResId(context, R.mipmap.round_check2)
        checkBox.setDrawBackground(isChecked)
        checkBox.setHasBorder(true)
        checkBox.setBorderColor(ContextCompat.getColor(context, R.color.layoutColor))
        checkBox.setSize(24)
        checkBox.setStrokeWidth(AndroidUtilities.dp(2f))
        checkBox.setColor(
            if (item.isCanCheck == 1) Theme.colorAccount else Theme.colorAccountDisable,
            ContextCompat.getColor(context, R.color.white)
        )
        checkBox.visibility = View.VISIBLE
        checkBox.isEnabled = item.isCanCheck == 1

        checkBox.setChecked(isChecked, true)
    }
}