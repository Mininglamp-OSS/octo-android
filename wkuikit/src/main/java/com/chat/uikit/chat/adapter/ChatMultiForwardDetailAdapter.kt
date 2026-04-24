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
import com.chat.base.emoji.MoonUtil
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
                if (!TextUtils.isEmpty(item.msg.fromUID)) {
                    val channel = WKIM.getInstance().channelManager.getChannel(
                        item.msg.fromUID,
                        WKChannelType.PERSONAL
                    )
                    if (channel != null) {
                        holder.setText(R.id.nameTv, channel.channelName)
                        avatarView.showAvatar(channel)
                    } else {
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
                        holder.setGone(R.id.fileLayout, true)
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
                        holder.setGone(R.id.fileLayout, true)
                        val wkGifContent =
                            item.msg.baseContentMsgModel as WKGifContent
                        GlideUtils.getInstance().showImg(
                            context,
                            WKApiConfig.getShowUrl(wkGifContent.url),
                            holder.getView(R.id.gifIv)
                        )
                    }

                    WKContentType.WK_FILE -> {
                        holder.setGone(R.id.contentTv, true)
                        holder.setGone(R.id.contentLayout, true)
                        holder.setGone(R.id.gifIv, true)
                        holder.setGone(R.id.fileLayout, false)
                        val fileContent = item.msg.baseContentMsgModel as WKFileContent
                        WKFileProvider.setFileIcon(
                            holder.getView(R.id.fileIconIv),
                            fileContent.extension, fileContent.name
                        )
                        holder.setText(R.id.fileNameTv, fileContent.name ?: "")
                        holder.setText(R.id.fileSizeTv, WKFileProvider.formatFileSize(fileContent.size))
                        val progressBar = holder.getView<android.widget.ProgressBar>(R.id.fileProgressBar)
                        progressBar.visibility = View.GONE
                        holder.getView<View>(R.id.fileLayout).setOnClickListener {
                            handleForwardFileClick(fileContent, progressBar)
                        }
                    }

                    WKContentType.WK_MULTIPLE_FORWARD -> {
                        holder.setGone(R.id.contentLayout, true)
                        holder.setGone(R.id.gifIv, true)
                        holder.setGone(R.id.fileLayout, true)
                        holder.setGone(R.id.contentTv, false)
                        val forwardContent =
                            item.msg.baseContentMsgModel as com.chat.uikit.chat.msgmodel.WKMultiForwardContent
                        // 显示预览内容
                        val previewBuilder = StringBuilder()
                        if (forwardContent.msgList != null) {
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
                            val contentJson = forwardContent.encodeMsg().toString()
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

    private fun handleForwardFileClick(fileContent: WKFileContent, progressBar: android.widget.ProgressBar) {
        // Check download directory
        val downloadDir = WKConstants.chatDownloadFileDir + "forward/"
        WKFileUtils.getInstance().createFileDir(downloadDir)
        val rawName = fileContent.name ?: ("file." + (fileContent.extension ?: "dat"))
        val fileName = File(rawName).name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val filePath = downloadDir + fileName
        val file = File(filePath)
        if (file.exists()) {
            openForwardFile(file)
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
                        openForwardFile(downloadedFile)
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

    private fun openForwardFile(file: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                context.packageName + ".fileProvider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            intent.setDataAndType(uri, WKFileProvider.getMimeType(file.name))
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            WKToastUtils.getInstance()
                .showToastNormal(context.getString(R.string.str_file_not_exist))
        }
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
}