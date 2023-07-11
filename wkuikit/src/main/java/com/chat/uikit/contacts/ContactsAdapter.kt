package com.chat.uikit.contacts

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

class ContactsAdapter :
    BaseQuickAdapter<FriendUIEntity, BaseViewHolder>(R.layout.item_contacts_layout) {

    override fun convert(holder: BaseViewHolder, item: FriendUIEntity, payloads: List<Any>) {
        super.convert(holder, item, payloads)
        val friendUIEntity = payloads[0] as FriendUIEntity?
        if (friendUIEntity != null) {
            val checkBox = holder.getView<CheckBox>(R.id.checkBox)
            val isChecked = item.check || !item.isCanCheck
            checkBox.setChecked(isChecked, true)
            checkBox.setDrawBackground(isChecked)
        }
    }

    override fun convert(holder: BaseViewHolder, item: FriendUIEntity) {
        val avatarView: AvatarView = holder.getView(R.id.avatarView)
        avatarView.showAvatar(item.channel)
        holder.setText(
            R.id.nameTv,
            if (TextUtils.isEmpty(item.channel.channelRemark)) item.channel.channelName else item.channel.channelRemark
        )
        val index: Int = holder.bindingAdapterPosition
        val index1: Int = getPositionForSection(item.pying.substring(0, 1))
        if (index == index1) {
            holder.setVisible(R.id.pyTv, true)
            holder.setText(R.id.pyTv, item.pying.substring(0, 1))
        } else {
            holder.setVisible(R.id.pyTv, false)
        }
        val isChecked = item.check || !item.isCanCheck
        val checkBox: CheckBox = holder.getView(R.id.checkBox)
        checkBox.setResId(context, R.mipmap.round_check2)
        checkBox.setDrawBackground(isChecked)
        checkBox.setHasBorder(true)
        checkBox.setStrokeWidth(AndroidUtilities.dp(2f))
        checkBox.setBorderColor(ContextCompat.getColor(context, R.color.layoutColor))
        checkBox.setSize(24)
//        checkBox.setCheckOffset(AndroidUtilities.dp(2));
        //        checkBox.setCheckOffset(AndroidUtilities.dp(2));
        checkBox.setColor(
            if (item.isCanCheck) Theme.colorAccount else Theme.colorAccountDisable,
            ContextCompat.getColor(context, R.color.white)
        )
        checkBox.visibility = View.VISIBLE
        checkBox.isEnabled = item.isCanCheck
        checkBox.setChecked(isChecked, true)
    }


    private fun getPositionForSection(catalog: String): Int {
        var i = 0
        val size = data.size
        while (i < size) {
            val sortStr = data[i].pying.substring(0, 1)
            if (catalog.equals(sortStr, ignoreCase = true)) {
                return i
            }
            i++
        }
        return -1
    }
}