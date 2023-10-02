package com.chat.uikit.contacts

import android.content.Intent
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.config.WKSystemAccount
import com.chat.base.endpoint.entity.ChatViewMenu
import com.chat.base.entity.PopupMenuItem
import com.chat.base.ui.Theme
import com.chat.base.ui.components.AvatarView
import com.chat.base.utils.LayoutHelper
import com.chat.base.utils.WKDialogUtils
import com.chat.base.utils.WKTimeUtils
import com.chat.uikit.R
import com.chat.uikit.chat.manager.WKIMUtils
import com.chat.uikit.user.SetUserRemarkActivity

class FriendAdapter :
    BaseQuickAdapter<FriendUIEntity, BaseViewHolder>(R.layout.item_friend_layout) {
    override fun convert(holder: BaseViewHolder, item: FriendUIEntity) {
        holder.setText(
            R.id.nameTv,
            if (TextUtils.isEmpty(item.channel.channelRemark)) item.channel.channelName else item.channel.channelRemark
        )
        val index: Int = holder.bindingAdapterPosition - 1
        val index1: Int = getPositionForSection(item.pying.substring(0, 1))
        holder.setText(R.id.pyTv, item.pying.substring(0, 1))
        holder.setGone(R.id.pyTv, index != index1)
        val avatarView: AvatarView = holder.getView(R.id.avatarView)
        avatarView.setSize(50f)
        avatarView.showAvatar(item.channel, true)
        val linearLayout: LinearLayout = holder.getView(R.id.categoryLayout)
        linearLayout.removeAllViews()
        if (!TextUtils.isEmpty(item.channel.category)) {
            if (item.channel.category == WKSystemAccount.accountCategorySystem) {
                linearLayout.addView(
                    Theme.getChannelCategoryTV(
                        context,
                        context.getString(R.string.official),
                        ContextCompat.getColor(
                            context, R.color.transparent
                        ),
                        ContextCompat.getColor(
                            context, R.color.reminderColor
                        ),
                        ContextCompat.getColor(
                            context, R.color.reminderColor
                        )
                    ),
                    LayoutHelper.createLinear(
                        LayoutHelper.WRAP_CONTENT,
                        LayoutHelper.WRAP_CONTENT,
                        Gravity.CENTER,
                        5,
                        1,
                        0,
                        0
                    )
                )
            }
            if (item.channel.category == WKSystemAccount.accountCategoryCustomerService) {
                linearLayout.addView(
                    Theme.getChannelCategoryTV(
                        context,
                        context.getString(R.string.customer_service),
                        Theme.colorAccount,
                        ContextCompat.getColor(
                            context, R.color.white
                        ),
                        Theme.colorAccount
                    ),
                    LayoutHelper.createLinear(
                        LayoutHelper.WRAP_CONTENT,
                        LayoutHelper.WRAP_CONTENT,
                        Gravity.CENTER,
                        5,
                        1,
                        0,
                        0
                    )
                )
            }
            if (item.channel.category == WKSystemAccount.accountCategoryVisitor) {
                linearLayout.addView(
                    Theme.getChannelCategoryTV(
                        context,
                        context.getString(R.string.visitor),
                        ContextCompat.getColor(
                            context, R.color.transparent
                        ),
                        ContextCompat.getColor(
                            context, R.color.colorFFC107
                        ),
                        ContextCompat.getColor(
                            context, R.color.colorFFC107
                        )
                    ),
                    LayoutHelper.createLinear(
                        LayoutHelper.WRAP_CONTENT,
                        LayoutHelper.WRAP_CONTENT,
                        Gravity.CENTER,
                        5,
                        1,
                        0,
                        0
                    )
                )
            }
        }
        if (item.channel.robot == 1) {
            linearLayout.addView(
                Theme.getChannelCategoryTV(
                    context,
                    context.getString(R.string.bot),
                    ContextCompat.getColor(
                        context, R.color.colorFFC107
                    ),
                    ContextCompat.getColor(
                        context, R.color.white
                    ),
                    ContextCompat.getColor(
                        context, R.color.colorFFC107
                    )
                ),
                LayoutHelper.createLinear(
                    LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER,
                    5,
                    1,
                    1,
                    0
                )
            )
        }
        if (item.channel.online == 1) {
            holder.setGone(R.id.offlineTv, true)
        } else {
            if (item.channel.lastOffline == 0L) {
                holder.setGone(R.id.offlineTv, true)
            } else {
                val lastSeenTime =
                    WKTimeUtils.getInstance().getOnlineTime(item.channel.lastOffline)
                if (TextUtils.isEmpty(lastSeenTime)) {
                    holder.setGone(R.id.offlineTv, false)
                    val time = WKTimeUtils.getInstance()
                        .getShowDateAndMinute(item.channel.lastOffline * 1000L)
                    val content =
                        String.format("%s %s", context.getString(R.string.last_seen_time), time)
                    holder.setText(R.id.offlineTv, content)
                } else {
                    holder.setGone(R.id.offlineTv, true)
                }
            }
        }
        val list: MutableList<PopupMenuItem> = ArrayList()
        list.add(
            PopupMenuItem(
                context.getString(R.string.set_remark),
                R.mipmap.msg_edit,
                object : PopupMenuItem.IClick {
                    override fun onClick() {
                        val intent = Intent(context, SetUserRemarkActivity::class.java)
                        intent.putExtra("uid", item.channel.channelID)
                        intent.putExtra(
                            "oldStr",
                            if (item.channel == null) "" else item.channel.channelRemark
                        )
                        context.startActivity(intent)
                    }
                })
        )
        list.add(
            PopupMenuItem(context.getString(R.string.send_msg), R.mipmap.menu_chats,
                object : PopupMenuItem.IClick {
                    override fun onClick() {
                        WKIMUtils.getInstance().startChatActivity(
                            ChatViewMenu(
                                context as ComponentActivity,
                                item.channel.channelID,
                                item.channel.channelType,
                                0,
                                false
                            )
                        )
                    }
                })
        )
        WKDialogUtils.getInstance().setViewLongClickPopup(holder.getView(R.id.contentLayout),list)
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