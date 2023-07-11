package com.chat.uikit.search

import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.View
import android.widget.Button
import com.chad.library.adapter.base.BaseMultiItemQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.config.WKConfig
import com.chat.base.ui.Theme
import com.chat.base.ui.components.AvatarView
import com.chat.uikit.R
import com.chat.uikit.enity.MailListEntity
import com.xinbida.wukongim.entity.WKChannelType
import kotlin.math.abs

class MailListAdapter : BaseMultiItemQuickAdapter<MailListEntity, BaseViewHolder>() {

    init {
        addItemType(0, R.layout.item_mail_list_layout)
        addItemType(1, R.layout.item_mail_list_text)
    }

    override fun convert(holder: BaseViewHolder, item: MailListEntity) {
        when (item.itemType) {
            0 -> {
                val addBtn = holder.getView<Button>(R.id.addBtn)
                addBtn.background.setTint(Theme.colorAccount)
                holder.setText(R.id.nameTv, item.name)
                holder.setText(R.id.phoneTv, item.phone)
                if (TextUtils.isEmpty(item.uid)) {
                    holder.setGone(R.id.nameIv, false)
                    holder.setGone(R.id.avatarView, true)
                    if (!TextUtils.isEmpty(item.name)) {
                        val name = item.name.substring(item.name.length - 1)
                        holder.setText(R.id.nameIv, name)
                        val colors = context.resources.getIntArray(R.array.name_colors)
                        val index = abs(item.name.hashCode()) % colors.size
                        val myShape =
                            holder.getView<View>(R.id.nameIv).background as GradientDrawable
                        myShape.setColor(colors[index])
                    }
                } else {
                    val avatarView: AvatarView = holder.getView(R.id.avatarView)
                    holder.setGone(R.id.nameIv, true)
                    holder.setGone(R.id.avatarView, false)
                    avatarView.showAvatar(item.uid, WKChannelType.PERSONAL)
                }
                if (!TextUtils.isEmpty(item.pying)) {
                    val index: Int = holder.bindingAdapterPosition
                    val index1: Int = getPositionForSection(item.pying.substring(0, 1))
                    if (index == index1) {
                        holder.setVisible(R.id.pyTv, true)
                        holder.setText(R.id.pyTv, item.pying.substring(0, 1))
                    } else {
                        holder.setVisible(R.id.pyTv, false)
                    }
                }
                if (item.is_friend == 1) {
                    holder.setGone(R.id.addedTv, false)
                    holder.setGone(R.id.addBtn, true)
                } else {
                    holder.setGone(R.id.addedTv, true)
                    holder.setGone(R.id.addBtn, false)
                    if (TextUtils.isEmpty(item.uid)) {
                        holder.setGone(R.id.addBtn, false)
                        holder.setText(R.id.addBtn, R.string.invite_users)
                    } else {
                        holder.setText(R.id.addBtn, R.string.add_friends)
                        if (item.uid == WKConfig.getInstance().uid) {
                            holder.setGone(R.id.addBtn, true)
                        } else {
                            holder.setGone(R.id.addBtn, false)
                        }
                    }
                }
            }
            1 -> holder.setText(R.id.titleTv, R.string.unregister_contacts)
        }
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