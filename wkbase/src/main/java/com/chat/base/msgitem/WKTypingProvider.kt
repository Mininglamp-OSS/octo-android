package com.chat.base.msgitem

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.chat.base.R
import com.chat.base.WKBaseApplication
import com.chat.base.views.BubbleLayout
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannelType
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.observers.ResourceObserver
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class WKTypingProvider : WKChatBaseProvider() {
    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
        return LayoutInflater.from(context).inflate(R.layout.chat_typing_layout, parentView, false)
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        Observable.interval(0, 1, TimeUnit.SECONDS)
            .take((8 + 1).toLong())
            .map { takeValue: Long -> 8 - takeValue }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : ResourceObserver<Long>() {
                override fun onNext(t: Long) {
                }

                override fun onError(e: Throwable) {
                }

                override fun onComplete() {
                    for (i in getAdapter()!!.data.indices.reversed()) {
                        if (getAdapter()!!.data[i].wkMsg.type == WKContentType.typing) {
                            getAdapter()!!.removeAt(i)
                            if (getAdapter()!!.data.size > 0) {
                                val index = getAdapter()!!.data.size - 1
                                getAdapter()!!.data[index].nextMsg = null
                                getAdapter()!!.notifyItemChanged(index)
                            }
                            break
                        }
                    }

                }

            })
        val contentTvLayout = parentView.findViewById<BubbleLayout>(R.id.contentLayout)
        val bgType = getMsgBgType(
            uiChatMsgItemEntity.previousMsg,
            uiChatMsgItemEntity.wkMsg,
            uiChatMsgItemEntity.nextMsg
        )
        contentTvLayout.setAll(bgType, from, WKContentType.typing)
        val receivedTextNameTv = parentView.findViewById<TextView>(R.id.receivedTextNameTv)
        val isShowNickName: Boolean =
            (bgType == WKMsgBgType.single || bgType == WKMsgBgType.top) && uiChatMsgItemEntity.showNickName && uiChatMsgItemEntity.wkMsg.channelType == WKChannelType.GROUP
        if (isShowNickName) {
            if (from == WKChatIteMsgFromType.RECEIVED) {

                var showName: String? = null
                var channelName = ""
                if (uiChatMsgItemEntity.wkMsg.from != null) {
                    showName = uiChatMsgItemEntity.wkMsg.from.channelRemark
                    channelName = uiChatMsgItemEntity.wkMsg.from.channelName
                }
                if (TextUtils.isEmpty(showName)) {
                    if (uiChatMsgItemEntity.wkMsg.memberOfFrom != null) {
                        showName =
                            if (TextUtils.isEmpty(uiChatMsgItemEntity.wkMsg.memberOfFrom.memberRemark)) uiChatMsgItemEntity.wkMsg.memberOfFrom.memberName else uiChatMsgItemEntity.wkMsg.memberOfFrom.memberRemark
                    } else {
                        if (TextUtils.isEmpty(showName)) {
                            showName = channelName
                        }
                    }
                }

                if (TextUtils.isEmpty(showName)) {
                    WKIM.getInstance().channelManager.fetchChannelInfo(
                        uiChatMsgItemEntity.wkMsg.fromUID, WKChannelType.PERSONAL
                    )
                    receivedTextNameTv.visibility = View.GONE
                } else {
                    receivedTextNameTv.text = showName
                    receivedTextNameTv.visibility = View.VISIBLE
                    if (!TextUtils.isEmpty(uiChatMsgItemEntity.wkMsg.fromUID)) {
                        val colors =
                            WKBaseApplication.getInstance().context.resources.getIntArray(R.array.name_colors)
                        val index = abs(uiChatMsgItemEntity.wkMsg.fromUID.hashCode()) % colors.size
                        receivedTextNameTv.setTextColor(colors[index])
                    }
                }
            } else {
                receivedTextNameTv.visibility = View.GONE
            }
        } else {
            receivedTextNameTv.visibility = View.GONE
        }
    }

    override val itemViewType: Int
        get() = WKContentType.typing
}