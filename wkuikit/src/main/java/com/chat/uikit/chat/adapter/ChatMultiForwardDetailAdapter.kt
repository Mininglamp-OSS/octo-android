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
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.graphics.drawable.GradientDrawable
import com.chat.base.config.WKApiConfig
import com.chat.base.config.WKSharedPreferencesUtil
import com.chat.base.emoji.MoonUtil
import com.chat.base.external.ExternalSourceResolver
import com.chat.base.markdown.WKMarkwonProvider
import com.chat.base.markdown.WKTableData
import com.chat.base.markdown.WKTablePlugin
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
import com.chat.base.utils.WKFileUtils
import com.chat.base.utils.WKToastUtils
import com.chat.base.config.WKConstants
import com.chat.base.msgcontent.WKFileContent
import com.chat.base.net.ud.WKDownloader
import com.chat.base.net.ud.WKProgressManager
import com.chat.uikit.R
import com.chat.uikit.chat.provider.WKFileProvider
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
    val list: List<ChatMultiForwardEntity>,
    /**
     * The `users[]` entries from the enclosing merge-forward payload. Each entry
     * carries per-user external-source fields (is_external / source_space_name /
     * home_space_id / home_space_name) parsed by
     * [com.chat.uikit.chat.msgmodel.WKMultiForwardContent]. Typed nullable
     * because the upstream [com.chat.uikit.chat.msgmodel.WKMultiForwardContent.userList]
     * field itself may be null when the payload omits users[].
     * See  / web PR #981.
     */
    private val users: List<WKChannel>?,
    /**
     * 父消息（合并转发包装消息）的 clientMsgNO，来自 message 表的真实持久化行。
     * 用于文件缓存键的 bundle 作用域前缀——forward/ 目录是全局共享的，子消息键
     * 必须先按 bundle 隔离，再按条目隔离，否则跨会话互相命中。嵌套转发（走
     * forward_content_json 进入）没有 DB 行，传 null，此时退化为内容哈希作用域。
     */
    private val parentClientMsgNO: String?
) :
    BaseMultiItemQuickAdapter<ChatMultiForwardEntity, BaseViewHolder>() {
    /** UI 下标 → bundle 作用域缓存键，构造时一次性算好。 */
    private val bundleScopedKeys: MutableMap<Int, String> = HashMap()
    init {
        addItemType(0, R.layout.item_chat_multi_froward_content)
        addItemType(1, R.layout.item_chat_multi_froward_time)
        addItemType(2, R.layout.item_chat_multi_froward_view)
        setList(list)
        // bundle 作用域键：list 与 msgList 一一对应（header 在 index 0、尾部 view 在
        // 末尾），构造时一次算好整张映射，点击时按下标查，不用在点击回调里重复 identity
        // 查找。见 [bundleScopedKeys] 与 [forwardFileCacheKey]。
        buildBundleScopedKeys()
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
                if (!TextUtils.isEmpty(item.msg.fromUID)) {
                    val channel = WKIM.getInstance().channelManager.getChannel(
                        item.msg.fromUID,
                        WKChannelType.PERSONAL
                    )
                    val displayName = buildDisplayName(item.msg, channel?.channelName)
                    if (channel != null) {
                        holder.setText(R.id.nameTv, displayName)
                        avatarView.showAvatar(channel)
                    } else {
                        holder.setText(R.id.nameTv, displayName)
                        avatarView.showAvatar(
                            item.msg.fromUID,
                            WKChannelType.PERSONAL
                        )
                        WKIM.getInstance().channelManager.fetchChannelInfo(
                            item.msg.fromUID,
                            WKChannelType.PERSONAL
                        )
                    }
                }
                val isGone =
                    (holder.bindingAdapterPosition != 0 && data[holder.bindingAdapterPosition - 1].itemType == 0 && item.msg.baseContentMsgModel != null && data[holder.bindingAdapterPosition - 1].msg.baseContentMsgModel != null && !TextUtils.isEmpty(
                        item.msg.fromUID
                    )
                            && !TextUtils.isEmpty(data[holder.bindingAdapterPosition - 1].msg.fromUID)
                            && item.msg.fromUID == data[holder.bindingAdapterPosition - 1].msg.fromUID)
                avatarView.visibility = if (isGone) View.INVISIBLE else View.VISIBLE
                when (item.msg.baseContentMsgModel.type) {
                    WKContentType.WK_IMAGE -> {
                        holder.setGone(R.id.progressView, true)
                        holder.setGone(R.id.playIv, true)
                        holder.setGone(R.id.contentTv, true)
                        holder.setGone(R.id.contentLayout, false)
                        holder.setGone(R.id.gifIv, true)
                        holder.setGone(R.id.fileLayout, true)
                        holder.setGone(R.id.imageView, false)
                        val imgMsgModel = item.msg.baseContentMsgModel as? WKImageContent
                        var showUrl: String
                        if (imgMsgModel != null && !TextUtils.isEmpty(imgMsgModel.localPath)) {
                            showUrl = imgMsgModel.localPath
                            val file = File(showUrl)
                            if (!file.exists()) {
                                //如果本地文件被删除就显示网络图片
                                showUrl = WKApiConfig.getShowUrl(imgMsgModel?.url ?: "")
                            }
                        } else {
                            showUrl = WKApiConfig.getShowUrl(imgMsgModel?.url ?: "")
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
                                    imgMsgModel ?: item.msg.baseContentMsgModel
                                )
                            }
                        val layoutParams: ViewGroup.LayoutParams =
                            holder.getView<View>(R.id.imageView).layoutParams
                        val ints = ImageUtils.getInstance()
                            .getImageWidthAndHeightToTalk(imgMsgModel?.width ?: 0, imgMsgModel?.height ?: 0)
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
                        holder.setGone(R.id.fileLayout, true)
                        holder.setGone(R.id.progressView, false)
                        holder.setGone(R.id.playIv, false)
                        val videoModel = item.msg.baseContentMsgModel as? WKVideoContent
                        var coverURL = ""
                        if (videoModel != null && !TextUtils.isEmpty(videoModel.coverLocalPath)) {
                            val file = File(videoModel.coverLocalPath)
                            if (file.exists()) coverURL = videoModel.coverLocalPath
                        } else {
                            coverURL = WKApiConfig.getShowUrl(videoModel?.cover ?: "")
                        }
                        GlideUtils.getInstance().showImg(
                            context,
                            coverURL,
                            holder.getView(R.id.imageView)
                        )
                        val layoutParams: ViewGroup.LayoutParams =
                            holder.getView<View>(R.id.imageView).layoutParams
                        val ints = ImageUtils.getInstance()
                            .getImageWidthAndHeightToTalk(videoModel?.width ?: 0, videoModel?.height ?: 0)
                        layoutParams.height = ints[1]
                        layoutParams.width = ints[0]
                        holder.getView<View>(R.id.imageView).layoutParams = layoutParams
                        holder.getView<FrameLayout>(R.id.contentLayout).layoutParams.height =
                            ints[1]
                        holder.getView<FrameLayout>(R.id.contentLayout).layoutParams.width = ints[0]
                        holder.getView<View>(R.id.imageView)
                            .setOnClickListener {
                                val videoUrl: String =
                                    if (!TextUtils.isEmpty(videoModel?.localPath)) {
                                        val file = File(videoModel!!.localPath)
                                        if (!file.exists()) {
                                            WKApiConfig.getShowUrl(videoModel.url)
                                        } else videoModel.localPath
                                    } else WKApiConfig.getShowUrl(videoModel?.url ?: "")

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
                        holder.setGone(R.id.fileLayout, true)
                        val wkGifContent =
                            item.msg.baseContentMsgModel as? WKGifContent
                        GlideUtils.getInstance().showImg(
                            context,
                            WKApiConfig.getShowUrl(wkGifContent?.url ?: ""),
                            holder.getView(R.id.gifIv)
                        )
                    }

                    WKContentType.WK_FILE -> {
                        holder.setGone(R.id.contentTv, true)
                        holder.setGone(R.id.contentLayout, true)
                        holder.setGone(R.id.gifIv, true)
                        holder.setGone(R.id.fileLayout, false)
                        val fileContent = item.msg.baseContentMsgModel as? WKFileContent
                        WKFileProvider.setFileIcon(
                            holder.getView(R.id.fileIconIv),
                            fileContent?.extension, fileContent?.name
                        )
                        holder.setText(R.id.fileNameTv, fileContent?.name ?: "")
                        holder.setText(R.id.fileSizeTv, WKFileProvider.formatFileSize(fileContent?.size ?: 0))
                        val progressBar = holder.getView<android.widget.ProgressBar>(R.id.fileProgressBar)
                        progressBar.visibility = View.GONE
                        holder.getView<View>(R.id.fileLayout).setOnClickListener {
                            if (fileContent != null) {
                                handleForwardFileClick(
                                    forwardFileCacheKey(item),
                                    fileContent,
                                    progressBar
                                )
                            }
                        }
                    }

                    WKContentType.WK_MULTIPLE_FORWARD -> {
                        holder.setGone(R.id.contentLayout, true)
                        holder.setGone(R.id.gifIv, true)
                        holder.setGone(R.id.fileLayout, true)
                        holder.setGone(R.id.contentTv, false)
                        val forwardContent =
                            item.msg.baseContentMsgModel as? com.chat.uikit.chat.msgmodel.WKMultiForwardContent
                        // 显示预览内容
                        val previewBuilder = StringBuilder()
                        if (forwardContent?.msgList != null) {
                            val size = kotlin.math.min(forwardContent.msgList.size, 3)
                            for (i in 0 until size) {
                                val innerMsg = forwardContent.msgList[i]
                                var name = ""
                                if (!TextUtils.isEmpty(innerMsg.fromUID)) {
                                    val ch = WKIM.getInstance().channelManager.getChannel(
                                        innerMsg.fromUID, WKChannelType.PERSONAL
                                    )
                                    if (ch != null) name = ch.channelName
                                }
                                val msgContent = innerMsg.baseContentMsgModel?.displayContent
                                    ?: context.getString(R.string.base_unknow_msg)
                                if (previewBuilder.isNotEmpty()) previewBuilder.append("\n")
                                previewBuilder.append(name).append(":").append(msgContent)
                            }
                        }
                        val displayText = context.getString(R.string.last_msg_chat_record) +
                            "\n" + previewBuilder.toString()
                        holder.setText(R.id.contentTv, displayText)
                        // 点击打开嵌套合并转发详情
                        holder.getView<TextView>(R.id.contentTv).setOnClickListener {
                            val contentJson = forwardContent?.encodeMsg()?.toString() ?: return@setOnClickListener
                            val intent = android.content.Intent(
                                context,
                                com.chat.uikit.chat.ChatMultiForwardDetailActivity::class.java
                            )
                            intent.putExtra("forward_content_json", contentJson)
                            context.startActivity(intent)
                        }
                    }

                    else -> {
                        var content = item.msg.baseContentMsgModel.displayContent
                        if (TextUtils.isEmpty(content)) {
                            content = context.getString(R.string.base_unknow_msg)
                        }
                        holder.setGone(R.id.contentTv, false)
                        holder.setGone(R.id.fileLayout, true)
                        holder.setGone(R.id.contentLayout, true)
                        holder.setGone(R.id.gifIv, true)
                        holder.setGone(R.id.imageView, true)
                        holder.setGone(R.id.progressView, true)
                        holder.setGone(R.id.playIv, true)

                        // Markwon 渲染 + 表格卡片
                        val contentTv = holder.getView<TextView>(R.id.contentTv)
                        val tableContainer = holder.getView<LinearLayout>(R.id.tableCardContainer)
                        tableContainer.removeAllViews()

                        val (rawSpanned, tables) = WKMarkwonProvider.toMarkdownWithTables(context, content)
                        val spanned = applyUrlHighlighting(rawSpanned)
                        if (tables.isEmpty()) {
                            contentTv.text = spanned
                        } else {
                            val fullText = spanned.toString()
                            val positions = mutableListOf<Int>()
                            var idx = 0
                            while (idx < fullText.length) {
                                val pos = fullText.indexOf(WKTablePlugin.TABLE_PLACEHOLDER, idx)
                                if (pos < 0) break
                                positions.add(pos)
                                idx = pos + 1
                            }
                            if (positions.size == tables.size) {
                                val segments = mutableListOf<CharSequence>()
                                var start = 0
                                for (pos in positions) {
                                    segments.add(spanned.subSequence(start, pos))
                                    start = pos + WKTablePlugin.TABLE_PLACEHOLDER.length
                                }
                                segments.add(spanned.subSequence(start, spanned.length))
                                contentTv.text = trimNewlines(segments[0])
                                if (segments[0].toString().isBlank()) {
                                    contentTv.visibility = View.GONE
                                } else {
                                    contentTv.visibility = View.VISIBLE
                                }
                                for (i in tables.indices) {
                                    tableContainer.addView(buildForwardTableCard(tables[i]))
                                    val next = segments.getOrNull(i + 1) ?: continue
                                    val trimmed = trimNewlines(next)
                                    if (trimmed.isBlank()) continue
                                    val extraTv = TextView(context).apply {
                                        text = trimmed
                                        setTextColor(contentTv.currentTextColor)
                                        setTextSize(TypedValue.COMPLEX_UNIT_PX, contentTv.textSize)
                                        movementMethod = LinkMovementMethod.getInstance()
                                    }
                                    tableContainer.addView(extraTv)
                                }
                            } else {
                                contentTv.text = spanned
                                for (table in tables) {
                                    tableContainer.addView(buildForwardTableCard(table))
                                }
                            }
                        }
                        contentTv.movementMethod = LinkMovementMethod.getInstance()

                        val popList: MutableList<PopupMenuItem> = java.util.ArrayList()
                        popList.add(
                            PopupMenuItem(
                                context.getString(R.string.copy),
                                R.mipmap.msg_copy, object : PopupMenuItem.IClick {
                                    override fun onClick() {
                                        val cm =
                                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                                        val mClipData =
                                            ClipData.newPlainText(
                                                "Label",
                                                contentTv.text.toString()
                                            )
                                        assert(cm != null)
                                        cm!!.setPrimaryClip(mClipData)
                                        WKToastUtils.getInstance()
                                            .showToastNormal(context.getString(R.string.copyed))
                                    }
                                })
                        )
                        WKDialogUtils.getInstance()
                            .setViewLongClickPopup(contentTv, popList)

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
                                            WKIM.getInstance().msgManager.send(
                                                messageContent,
                                                channel
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

    /**
     * 给 list 里每个条目算出**原始身份串**并缓存（UI 下标 → identity）。
     *
     * 注意存的是身份串、不是磁盘名：磁盘名在点击时由
     * WKFileProvider.buildCacheFileNameForIdentity 对身份串做 SHA-256 派生。旧实现
     * 在这里就把 messageID 过了 sanitizeScope（`:` `?` 等→`_`、截断 64 字符）并把
     * 结果当缓存键与校验键，该变换不是单射：`m:a` 与 `m?a` 塌成同一个键，两条不同
     * 子消息映射到同一磁盘路径且校验也无法区分（B-1）。现在清洗只发生在派生出的
     * basename 之后——而 SHA-256 的输出只含 [0-9a-f]，本就无需清洗。
     *
     * 作用域仍是两层：bundle 层（父消息 clientMsgNO，或嵌套转发时的 payload 摘要）
     * × 条目层（messageID，缺失时 bundle 内下标，两者走不同命名空间前缀）。
     */
    private fun buildBundleScopedKeys() {
        // 嵌套转发（forward_content_json 进入）没有父 DB 行，用 payload 摘要做作用域：
        // 同一转发内容在两个会话里命中同一缓存是正确行为（字节相同）。
        val scope = if (!parentClientMsgNO.isNullOrEmpty()) {
            "b:$parentClientMsgNO"
        } else {
            "h:${payloadDigest()}"
        }
        bundleScopedKeys.clear()
        var fileIndex = 0
        for (i in list.indices) {
            val item = list[i]
            val msg = item.msg ?: continue
            bundleScopedKeys[i] = com.chat.base.utils.WKCacheKeys.entryIdentity(
                scope, msg.messageID, fileIndex
            )
            fileIndex++
        }
    }

    /**
     * 嵌套转发的 bundle 作用域摘要。算法实现在
     * [com.chat.base.utils.WKCacheKeys.payloadDigest]（纯 Java、无 Android 依赖），
     * 这里只做字段提取——生产与 JVM 单测调用同一份实现，不存在"测试里复制一份
     * 算法、改一边忘另一边静默失去覆盖"的漂移。
     */
    private fun payloadDigest(): String {
        val ids = ArrayList<String>()
        val times = ArrayList<Long>()
        val uids = ArrayList<String>()
        val contents = ArrayList<String>()
        for (item in list) {
            val msg = item.msg ?: continue
            ids.add(msg.messageID ?: "")
            times.add(msg.timestamp)
            uids.add(msg.fromUID ?: "")
            contents.add(msg.content ?: "")
        }
        return com.chat.base.utils.WKCacheKeys.payloadDigest(ids, times, uids, contents)
    }

    /**
     * 合并转发子消息的**原始身份串**（bundle 作用域 × 条目）。
     *
     * 不能用 `item.msg.clientMsgNO`：子消息由 `WKMultiForwardContent.decodeMsg` 反序列化
     * 得来，decodeMsg 从不赋值 clientMsgNO，它来自 `WKMsg` 构造函数的
     * `createClientMsgNO()` 兜底，是个每次 decode 都重新生成的随机 UUID。
     *
     * messageID 跨 decode 稳定且全局唯一，但可能缺失（发送中/发送失败的消息 encode 时
     * messageID 为 null）；缺失时按 bundle 内顺序取下标，并由 WKCacheKeys.entryIdentity
     * 用 `ix:` 前缀与 `id:` 命名空间隔开。下标永远不单独使用，必须挂在 bundle 作用域之下。
     */
    private fun forwardFileCacheKey(item: ChatMultiForwardEntity): String {
        val index = list.indexOf(item)
        bundleScopedKeys[index]?.let { return it }
        // 理论兜底：映射里查不到（adapter 数据被整体替换后未重建映射）。用该条目自身
        // 内容字段的全量摘要做独立作用域——跨进程稳定，且不与任何 bundle 作用域撞车。
        val msg = item.msg
        val raw = com.chat.base.utils.WKCacheKeys.sha256Hex(
            (msg?.messageID ?: "") + "\u001F" + (msg?.timestamp ?: 0L) +
                    "\u001F" + (msg?.fromUID ?: "") + "\u001F" + (msg?.content ?: "")
        )
        return com.chat.base.utils.WKCacheKeys.entryIdentity("f:$raw", msg?.messageID, index)
    }

    private fun handleForwardFileClick(identity: String, fileContent: WKFileContent, progressBar: android.widget.ProgressBar) {
        // 缓存键不能用 item.msg.clientMsgNO：合并转发的子消息是 WKMultiForwardContent
        // .decodeMsg 的运行时反序列化产物，decodeMsg 从不赋值 clientMsgNO，它是 WKMsg
        // 构造函数兜底生成的随机 UUID——每次打开详情页都变，既不唯一也查不到 DB 行。
        // 结果是缓存永不命中（每次重下）+ forward/ 目录无界增长（该目录不在 Clear Cache
        // 覆盖范围内）。这里改用调用处传入的稳定键（messageID，缺失时回落下标复合键）。
        val downloadDir = WKConstants.chatDownloadFileDir + "forward/"
        WKFileUtils.getInstance().createFileDir(downloadDir)
        // 磁盘名 = SHA-256(identity) + 展示名后缀。identity 原样进摘要，不经任何
        // 字符替换与截断——这是 B-1 的修复点，详见 WKCacheKeys 的类注释。
        val fileName = WKFileProvider.buildCacheFileNameForIdentity(
            identity,
            fileContent.extension,
            fileContent.name
        )
        val filePath = downloadDir + fileName
        val file = File(filePath)
        if (file.exists() && WKFileProvider.isValidFileCache(file, fileContent.size, identity)) {
            openForwardFile(file, fileContent.name)
            return
        }

        if (TextUtils.isEmpty(fileContent.url)) {
            WKToastUtils.getInstance()
                .showToastNormal(context.getString(R.string.str_file_not_exist))
            return
        }

        val downloadUrl = WKApiConfig.getShowUrl(fileContent.url)
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        WKDownloader.instance.download(downloadUrl, filePath,
            object : WKProgressManager.IProgress {
                override fun onProgress(tag: Any?, progress: Int) {
                    progressBar.progress = progress
                }

                override fun onSuccess(tag: Any?, path: String?) {
                    progressBar.visibility = View.GONE
                    val downloadedFile = File(filePath)
                    if (downloadedFile.exists()) {
                        // 标记已完整落地，供 isValidFileCache 在发送方上报 size 不准时放行
                        WKFileProvider.markDownloadVerified(downloadedFile, identity)
                        // 这里刻意不回写 localPath / 不调 updateContentAndRefresh：合并转发
                        // 的子消息在 message 表里没有自己的行（它只是父消息 content 里的一个
                        // payload 条目），按任何键去 UPDATE 都匹配不到行，是静默 no-op。
                        // 重开命中靠的是上面稳定的 cacheKey 路径，不需要持久化。
                        openForwardFile(downloadedFile, fileContent.name)
                    }
                }

                override fun onFail(tag: Any?, msg: String?) {
                    progressBar.visibility = View.GONE
                    WKToastUtils.getInstance()
                        .showToastNormal(context.getString(R.string.str_file_download_fail))
                }
            })
    }

    private fun trimNewlines(cs: CharSequence): CharSequence {
        var s = 0
        var e = cs.length
        while (s < e && cs[s] == '\n') s++
        while (e > s && cs[e - 1] == '\n') e--
        return if (s == 0 && e == cs.length) cs else cs.subSequence(s, e)
    }

    private fun buildForwardTableCard(tableData: WKTableData): View {
        val cardView = LayoutInflater.from(context)
            .inflate(com.chat.base.R.layout.layout_markdown_table_card, null)

        val tableContent = cardView.findViewById<TableLayout>(com.chat.base.R.id.tableContent)
        val tableScrollView = cardView.findViewById<HorizontalScrollView>(com.chat.base.R.id.tableScrollView)
        val copyBtn = cardView.findViewById<ImageView>(com.chat.base.R.id.tableCopyBtn)

        // 表格无数据时移除 stretchColumns 避免 Android 框架除零崩溃
        if (tableData.headers.isEmpty() && tableData.rows.isEmpty()) {
            tableContent.setStretchAllColumns(false)
            return cardView
        }

        tableScrollView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                    v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        val dp = context.resources.displayMetrics.density
        val cellPaddingH = (10 * dp).toInt()
        val cellPaddingV = (8 * dp).toInt()
        val textSize = 13f
        val headerBgColor = Color.parseColor("#F0F0F0")
        val evenRowBgColor = Color.parseColor("#FAFAFA")
        val borderColor = Color.parseColor("#E8E8E8")

        // 确认有列数据后再启用 stretchColumns，避免 0 列时框架除零崩溃
        tableContent.setStretchAllColumns(true)

        if (tableData.headers.isNotEmpty()) {
            val headerRow = TableRow(context)
            headerRow.setBackgroundColor(headerBgColor)
            for ((colIdx, header) in tableData.headers.withIndex()) {
                headerRow.addView(createCell(header.text, textSize, cellPaddingH, cellPaddingV,
                    Color.parseColor("#333333"), true, tableData, colIdx, borderColor))
            }
            tableContent.addView(headerRow)
        }
        for ((rowIdx, row) in tableData.rows.withIndex()) {
            val tableRow = TableRow(context)
            if (rowIdx % 2 == 1) tableRow.setBackgroundColor(evenRowBgColor)
            for ((colIdx, cell) in row.withIndex()) {
                tableRow.addView(createCell(cell.text, textSize, cellPaddingH, cellPaddingV,
                    Color.parseColor("#555555"), false, tableData, colIdx, borderColor))
            }
            tableContent.addView(tableRow)
        }
        copyBtn.setOnClickListener {
            val sb = StringBuilder()
            if (tableData.headers.isNotEmpty()) sb.appendLine(tableData.headers.joinToString("\t") { it.text })
            for (row in tableData.rows) sb.appendLine(row.joinToString("\t") { it.text })
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("table", sb.toString().trimEnd()))
            WKToastUtils.getInstance().showToastNormal(context.getString(com.chat.base.R.string.str_table_copied))
        }
        return cardView
    }

    private fun createCell(
        text: String, textSize: Float, paddingH: Int, paddingV: Int,
        textColor: Int, isBold: Boolean, tableData: WKTableData, colIdx: Int, borderColor: Int
    ): TextView {
        return TextView(context).apply {
            this.text = text
            this.textSize = textSize
            setTextColor(textColor)
            setPadding(paddingH, paddingV, paddingH, paddingV)
            isSingleLine = true
            if (isBold) typeface = Typeface.DEFAULT_BOLD
            if (colIdx < tableData.alignments.size) {
                gravity = when (tableData.alignments[colIdx]) {
                    org.commonmark.ext.gfm.tables.TableCell.Alignment.CENTER -> Gravity.CENTER
                    org.commonmark.ext.gfm.tables.TableCell.Alignment.RIGHT -> Gravity.CENTER_VERTICAL or Gravity.END
                    else -> Gravity.CENTER_VERTICAL or Gravity.START
                }
            }
            layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
            val gd = GradientDrawable()
            gd.setStroke(1, borderColor)
            gd.setColor(Color.TRANSPARENT)
            background = gd
        }
    }

    private fun openForwardFile(file: File, displayName: String? = null) {
        WKFileProvider.openFileWithOptions(context, file, displayName)
    }

    private fun applyUrlHighlighting(spanned: android.text.Spanned): android.text.SpannableStringBuilder {
        val ssb = android.text.SpannableStringBuilder(spanned)
        val displayText = ssb.toString()
        val urls = com.chat.base.utils.StringUtils.getStrUrls(displayText)
        for (url in urls) {
            var fromIndex = 0
            while (fromIndex >= 0) {
                fromIndex = displayText.indexOf(url, fromIndex)
                if (fromIndex >= 0) {
                    ssb.setSpan(object : android.text.style.ClickableSpan() {
                        override fun onClick(widget: android.view.View) {
                            val intent = android.content.Intent(context, com.chat.base.act.WKWebViewActivity::class.java)
                            intent.putExtra("url", url)
                            context.startActivity(intent)
                        }
                        override fun updateDrawState(ds: android.text.TextPaint) {
                            ds.color = androidx.core.content.ContextCompat.getColor(context, com.chat.base.R.color.blue)
                            ds.isUnderlineText = false
                        }
                    }, fromIndex, fromIndex + url.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.setSpan(android.text.style.StyleSpan(Typeface.BOLD),
                        fromIndex, fromIndex + url.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    fromIndex += url.length
                }
            }
        }
        return ssb
    }

    /**
     *  / web PR #981: build the per-row merge-forward display name with an
     * inline "@SpaceName" suffix when the original sender is external relative
     * to the current viewer. The resolution prefers per-msg fields (populated
     * from the merge-forward `msgs[].home_space_*` fields) and falls back
     * to the per-user `users[]` entry. Users whose home Space matches the
     * current viewer get no suffix so same-Space conversations stay visually
     * unchanged.
     */
    private fun buildDisplayName(
        msg: com.xinbida.wukongim.entity.WKMsg,
        fallbackName: String?
    ): CharSequence {
        val baseName = fallbackName ?: msg.fromUID ?: ""
        val viewerHomeSpaceId = WKSharedPreferencesUtil.getInstance()
            .getSPWithUID("current_space_id") ?: ""
        // 1) prefer msg-level fields so per-forwarded-msg external metadata wins
        var sourceSpaceName =
            ExternalSourceResolver.resolveSourceSpaceName(msg, viewerHomeSpaceId)
        if (sourceSpaceName.isNullOrEmpty() && users != null) {
            val matched = users.firstOrNull {
                !TextUtils.isEmpty(it.channelID) && it.channelID == msg.fromUID
            }
            if (matched != null) {
                sourceSpaceName = ExternalSourceResolver
                    .resolveMergeForwardUserSpaceName(matched, viewerHomeSpaceId)
            }
        }
        if (sourceSpaceName.isNullOrEmpty()) return baseName
        val builder = android.text.SpannableStringBuilder(baseName)
        val start = builder.length
        builder.append(" @").append(sourceSpaceName)
        builder.setSpan(
            android.text.style.ForegroundColorSpan(0xFF8B5CF6.toInt()),
            start,
            builder.length,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return builder
    }

}