package com.chat.uikit.chat.adapter

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.chad.library.adapter.base.BaseMultiItemQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.config.WKApiConfig
import com.chat.base.emoji.MoonUtil
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.EndpointSID
import com.chat.base.endpoint.entity.ChatChooseContacts
import com.chat.base.endpoint.entity.ChooseChatMenu
import com.chat.base.endpoint.entity.PlayVideoMenu
import com.chat.base.entity.ImagePopupBottomSheetItem
import com.chat.base.entity.PopupMenuItem
import com.chat.base.glide.GlideUtils
import com.chat.base.msg.model.WKGifContent
import com.chat.base.msgitem.WKContentType
import com.chat.base.ui.components.AvatarView
import com.chat.base.utils.ImageUtils
import com.chat.base.utils.WKDialogUtils
import com.chat.base.utils.WKTimeUtils
import com.chat.base.utils.WKToastUtils
import com.chat.uikit.R
import com.chat.uikit.enity.ChatMultiForwardEntity
import com.google.android.material.snackbar.Snackbar
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelType
import com.xinbida.wukongim.msgmodel.WKImageContent
import com.xinbida.wukongim.msgmodel.WKMessageContent
import com.xinbida.wukongim.msgmodel.WKVideoContent
import java.io.File

class ChatMultiForwardDetailAdapter(
    private val showDetailTime: Boolean,
    val list: List<ChatMultiForwardEntity>
) :
    BaseMultiItemQuickAdapter<ChatMultiForwardEntity, BaseViewHolder>() {
    init {
        addItemType(0, R.layout.item_chat_multi_froward_content)
        addItemType(1, R.layout.item_chat_multi_froward_time)
        addItemType(2, R.layout.item_chat_multi_froward_view)
        setList(list)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun convert(holder: BaseViewHolder, item: ChatMultiForwardEntity) {

        when (item.itemType) {
            1 -> holder.setText(R.id.timeTv, item.title)
            0 -> {
                if (showDetailTime) holder.setText(
                    R.id.timeTv,
                    WKTimeUtils.getInstance()
                        .time2DateStr1(item.msg.timestamp * 1000)
                ) else holder.setText(
                    R.id.timeTv,
                    WKTimeUtils.getInstance()
                        .time2DateStr(item.msg.timestamp * 1000)
                )
                holder.setGone(
                    R.id.viewLine,
                    holder.bindingAdapterPosition == itemCount - 2
                )
                val avatarView: AvatarView = holder.getView(R.id.avatarView)
                avatarView.setSize(40f)
                if (!TextUtils.isEmpty(item.msg.baseContentMsgModel.fromUID)) {
                    val channel = WKIM.getInstance().channelManager.getChannel(
                        item.msg.baseContentMsgModel.fromUID,
                        WKChannelType.PERSONAL
                    )
                    if (channel != null) {
                        holder.setText(R.id.nameTv, channel.channelName)
                        avatarView.showAvatar(channel)
                    } else {
                        avatarView.showAvatar(
                            item.msg.baseContentMsgModel.fromUID,
                            WKChannelType.PERSONAL
                        )
                        WKIM.getInstance().channelManager.fetchChannelInfo(
                            item.msg.baseContentMsgModel.fromUID,
                            WKChannelType.PERSONAL
                        )
                    }
                }
                val isGone =
                    (holder.bindingAdapterPosition != 0 && data[holder.bindingAdapterPosition - 1].itemType == 0 && item.msg.baseContentMsgModel != null && data[holder.bindingAdapterPosition - 1].msg.baseContentMsgModel != null && !TextUtils.isEmpty(
                        item.msg.baseContentMsgModel.fromUID
                    )
                            && !TextUtils.isEmpty(data[holder.bindingAdapterPosition - 1].msg.baseContentMsgModel.fromUID)
                            && item.msg.baseContentMsgModel.fromUID == data[holder.bindingAdapterPosition - 1].msg.baseContentMsgModel.fromUID)
                avatarView.visibility = if (isGone) View.INVISIBLE else View.VISIBLE
                when (item.msg.baseContentMsgModel.type) {
                    WKContentType.WK_IMAGE -> {
                        holder.setGone(R.id.progressView, true)
                        holder.setGone(R.id.playIv, true)
                        holder.setGone(R.id.contentTv, true)
                        holder.setGone(R.id.contentLayout, false)
                        holder.setGone(R.id.gifIv, true)
                        holder.setGone(R.id.imageView, false)
                        val imgMsgModel = item.msg.baseContentMsgModel as WKImageContent
                        var showUrl: String
                        if (!TextUtils.isEmpty(imgMsgModel.localPath)) {
                            showUrl = imgMsgModel.localPath
                            val file = File(showUrl)
                            if (!file.exists()) {
                                //如果本地文件被删除就显示网络图片
                                showUrl = WKApiConfig.getShowUrl(imgMsgModel.url)
                            }
                        } else {
                            showUrl = WKApiConfig.getShowUrl(imgMsgModel.url)
                        }
                        GlideUtils.getInstance().showImg(
                            context,
                            showUrl,
                            holder.getView(R.id.imageView)
                        )
                        val tempUrl = showUrl
                        holder.getView<View>(R.id.imageView)
                            .setOnClickListener {
                                showImages(
                                    tempUrl,
                                    holder.getView(R.id.imageView),
                                    imgMsgModel
                                )
                            }
                        val layoutParams: ViewGroup.LayoutParams =
                            holder.getView<View>(R.id.imageView).layoutParams
                        val ints = ImageUtils.getInstance()
                            .getImageWidthAndHeightToTalk(imgMsgModel.width, imgMsgModel.height)
                        layoutParams.height = ints[1]
                        layoutParams.width = ints[0]
                        holder.getView<View>(R.id.imageView).layoutParams = layoutParams

                        holder.getView<FrameLayout>(R.id.contentLayout).layoutParams.height =
                            ints[1]
                        holder.getView<FrameLayout>(R.id.contentLayout).layoutParams.width = ints[0]
                    }

                    WKContentType.WK_VIDEO -> {
                        holder.setGone(R.id.contentTv, true)
                        holder.setGone(R.id.contentLayout, false)
                        holder.setGone(R.id.imageView, false)
                        holder.setGone(R.id.gifIv, true)
                        holder.setGone(R.id.progressView, false)
                        holder.setGone(R.id.playIv, false)
                        val videoModel = item.msg.baseContentMsgModel as WKVideoContent
                        var coverURL = ""
                        if (!TextUtils.isEmpty(videoModel.coverLocalPath)) {
                            val file = File(videoModel.coverLocalPath)
                            if (file.exists()) coverURL = videoModel.coverLocalPath
                        } else {
                            coverURL = WKApiConfig.getShowUrl(videoModel.cover)
                        }
                        GlideUtils.getInstance().showImg(
                            context,
                            coverURL,
                            holder.getView(R.id.imageView)
                        )
                        val layoutParams: ViewGroup.LayoutParams =
                            holder.getView<View>(R.id.imageView).layoutParams
                        val ints = ImageUtils.getInstance()
                            .getImageWidthAndHeightToTalk(videoModel.width, videoModel.height)
                        layoutParams.height = ints[1]
                        layoutParams.width = ints[0]
                        holder.getView<View>(R.id.imageView).layoutParams = layoutParams
                        holder.getView<FrameLayout>(R.id.contentLayout).layoutParams.height =
                            ints[1]
                        holder.getView<FrameLayout>(R.id.contentLayout).layoutParams.width = ints[0]
                        holder.getView<View>(R.id.imageView)
                            .setOnClickListener {
                                val videoUrl: String =
                                    if (!TextUtils.isEmpty(videoModel.localPath)) {
                                        val file = File(videoModel.localPath)
                                        if (!file.exists()) {
                                            WKApiConfig.getShowUrl(videoModel.url)
                                        } else videoModel.localPath
                                    } else WKApiConfig.getShowUrl(videoModel.url)

                                EndpointManager.getInstance().invoke(
                                    "play_video",
                                    PlayVideoMenu(
                                        context as AppCompatActivity,
                                        holder.getView(R.id.imageView),
                                        "",
                                        videoUrl,
                                        coverURL
                                    )
                                )
                            }
                    }

                    WKContentType.WK_GIF -> {
                        holder.setGone(R.id.progressView, true)
                        holder.setGone(R.id.playIv, true)
                        holder.setGone(R.id.contentTv, true)
                        holder.setGone(R.id.imageView, true)
                        holder.setGone(R.id.gifIv, false)
                        holder.setGone(R.id.contentLayout, true)
                        val wkGifContent =
                            item.msg.baseContentMsgModel as WKGifContent
                        GlideUtils.getInstance().showImg(
                            context,
                            WKApiConfig.getShowUrl(wkGifContent.url),
                            holder.getView(R.id.gifIv)
                        )
                    }

                    else -> {
                        MoonUtil.identifyFaceExpression(
                            context,
                            holder.getView(R.id.contentTv),
                            item.msg.baseContentMsgModel.getDisplayContent(),
                            MoonUtil.DEF_SCALE
                        )
                        (holder.getView<View>(R.id.contentTv) as TextView).movementMethod =
                            LinkMovementMethod.getInstance()
                        holder.setGone(R.id.contentTv, false)
                        holder.setGone(R.id.contentLayout, true)
                        holder.setGone(R.id.gifIv, true)
                        holder.setGone(R.id.imageView, true)
                        holder.setGone(R.id.progressView, true)
                        holder.setGone(R.id.playIv, true)

                        val list: MutableList<PopupMenuItem> = java.util.ArrayList()
                        list.add(
                            PopupMenuItem(
                                context.getString(R.string.copy),
                                R.mipmap.msg_copy, object : PopupMenuItem.IClick {
                                    override fun onClick() {
                                        val cm =
                                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                                        val mClipData =
                                            ClipData.newPlainText(
                                                "Label",
                                                holder.getView<TextView>(R.id.contentTv).text.toString()
                                            )
                                        assert(cm != null)
                                        cm!!.setPrimaryClip(mClipData)
                                        WKToastUtils.getInstance()
                                            .showToastNormal(context.getString(R.string.copyed))
                                    }
                                })
                        )
                        WKDialogUtils.getInstance()
                            .setViewLongClickPopup(holder.getView<TextView>(R.id.contentTv), list)

                    }
                }
            }
        }
    }


    private fun showImages(
        uri: String,
        imageView: ImageView,
        messageContent: WKMessageContent
    ) {

        //查看大图
        val imgList: MutableList<ImageView> = ArrayList()
        imgList.add(imageView)
        val tempImgList: MutableList<Any> = ArrayList()
        tempImgList.add(uri)
        val bottomEntityList: MutableList<ImagePopupBottomSheetItem> = ArrayList()
        bottomEntityList.add(
            ImagePopupBottomSheetItem(
                context.getString(
                    R.string.forward
                ), R.mipmap.msg_forward, object : ImagePopupBottomSheetItem.IBottomSheetClick {
                    override fun onClick(index: Int) {
                        EndpointManager.getInstance().invoke(
                            EndpointSID.showChooseChatView,
                            ChooseChatMenu(
                                ChatChooseContacts { list1: List<WKChannel>? ->
                                    if (!list1.isNullOrEmpty()) {
                                        for (channel in list1) {
                                            WKIM.getInstance().msgManager.sendMessage(
                                                messageContent,
                                                channel.channelID,
                                                channel.channelType
                                            )
                                        }
                                        val viewGroup =
                                            (context as Activity).findViewById<View>(android.R.id.content)
                                                .rootView as ViewGroup
                                        Snackbar.make(
                                            viewGroup,
                                            context.getString(R.string.is_forward),
                                            1000
                                        )
                                            .setAction(
                                                ""
                                            ) { }
                                            .show()
                                    }
                                },
                                messageContent
                            )
                        )

                    }

                }
            )
        )
        WKDialogUtils.getInstance().showImagePopup(
            context,
            tempImgList,
            imgList,
            imageView,
            0,
            bottomEntityList, null,
            null
        )
    }
}