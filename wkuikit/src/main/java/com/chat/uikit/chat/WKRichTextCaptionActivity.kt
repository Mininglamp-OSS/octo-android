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

package com.chat.uikit.chat

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.chat.base.glide.ChooseMimeType
import com.chat.base.glide.GlideUtils
import com.chat.base.utils.AndroidUtilities
import com.chat.base.utils.WKFileUtils
import com.chat.base.utils.WKReader
import com.chat.uikit.R
import com.chat.uikit.chat.manager.WKRichTextComposeModel
import com.chat.uikit.databinding.ActRichtextCaptionLayoutBinding
import com.chat.uikit.group.RemindMemberAdapter
import com.chat.uikit.group.GroupMemberEntity
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannelMember
import com.xinbida.wukongim.entity.WKChannelType

class WKRichTextCaptionActivity : AppCompatActivity() {

    private lateinit var binding: ActRichtextCaptionLayoutBinding
    private val imagePaths = mutableListOf<String>()
    private lateinit var gridAdapter: GridAdapter
    private var channelId = ""
    private var channelType: Byte = WKChannelType.GROUP
    private var existingMentionUids: List<String> = emptyList()
    private var mentionAdapter: RemindMemberAdapter? = null
    private var suppressMentionDetection = false
    private var mentionQueryLength = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActRichtextCaptionLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val paths = intent.getStringArrayListExtra("paths") ?: arrayListOf()
        imagePaths.addAll(paths)
        channelId = intent.getStringExtra("channelId") ?: ""
        channelType = intent.getByteExtra("channelType", WKChannelType.GROUP)
        existingMentionUids = intent.getStringArrayListExtra("existingMentionUids") ?: emptyList()

        val existingCaption = intent.getStringExtra("caption")
        if (!existingCaption.isNullOrBlank()) {
            binding.captionEt.setText(existingCaption)
            binding.captionEt.setSelection(existingCaption.length)
        }

        setupGrid()
        setupListeners()
        setupMentionDetection()
    }

    private fun setupGrid() {
        gridAdapter = GridAdapter()
        binding.gridRecyclerView.layoutManager = GridLayoutManager(this, 3)
        binding.gridRecyclerView.adapter = gridAdapter

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END, 0
        ) {
            override fun onMove(
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from < 0 || to < 0 || from >= imagePaths.size || to >= imagePaths.size) return false
                val item = imagePaths.removeAt(from)
                imagePaths.add(to, item)
                gridAdapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled(): Boolean = true
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                if (viewHolder.bindingAdapterPosition >= imagePaths.size) return 0
                return super.getMovementFlags(recyclerView, viewHolder)
            }
        })
        touchHelper.attachToRecyclerView(binding.gridRecyclerView)
    }

    private fun setupListeners() {
        binding.cancelBtn.setOnClickListener {
            returnCaptionOnCancel()
            finish()
        }

        binding.sendBtn.setOnClickListener {
            val caption = binding.captionEt.text?.toString() ?: ""
            val modalUids = binding.captionEt.allUIDs
            val allUids = LinkedHashSet<String>()
            allUids.addAll(existingMentionUids)
            allUids.addAll(modalUids)
            val hasAll = allUids.contains("-1")
            val hasAis = allUids.contains("-2")
            val result = Intent()
            result.putStringArrayListExtra("paths", ArrayList(imagePaths))
            result.putExtra("caption", caption)
            result.putStringArrayListExtra("mentionUids", ArrayList(allUids))
            result.putExtra("mentionAll", hasAll)
            result.putExtra("mentionAis", hasAis)
            setResult(RESULT_OK, result)
            finish()
        }
    }

    private fun setupMentionDetection() {
        if (channelType != WKChannelType.GROUP && channelType != WKChannelType.COMMUNITY_TOPIC) return

        binding.captionEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (suppressMentionDetection) return
                if (count == 1 && s != null && start < s.length && s[start] == '@') {
                    mentionQueryLength = 0
                    showMemberPicker()
                } else if (binding.mentionRecyclerView.visibility == View.VISIBLE) {
                    if (count >= 1 && before == 0) {
                        mentionQueryLength += count
                    } else if (count == 0 && before >= 1) {
                        mentionQueryLength = (mentionQueryLength - before).coerceAtLeast(0)
                    }
                    if (mentionQueryLength > 0) {
                        val text = binding.captionEt.text?.toString() ?: ""
                        val cursorPos = binding.captionEt.selectionStart
                        val atPos = cursorPos - mentionQueryLength - 1
                        if (atPos >= 0 && atPos < text.length && text[atPos] == '@') {
                            val query = text.substring(atPos + 1, cursorPos)
                            mentionAdapter?.onSearch(query)
                        }
                    } else {
                        mentionAdapter?.onNormal()
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun showMemberPicker() {
        val mentionRv = binding.mentionRecyclerView
        if (mentionAdapter != null) {
            mentionAdapter!!.onNormal()
            mentionRv.visibility = View.VISIBLE
            return
        }

        val mentionChannelID: String
        val mentionChannelType: Byte
        if (channelType == WKChannelType.COMMUNITY_TOPIC) {
            val parsed = com.chat.uikit.thread.service.ThreadModel.getInstance()
                .parseChannelId(channelId)
            mentionChannelID = parsed?.get(0) ?: channelId
            mentionChannelType = WKChannelType.GROUP
        } else {
            mentionChannelID = channelId
            mentionChannelType = channelType
        }

        mentionRv.layoutManager = LinearLayoutManager(this)
        val adapter = RemindMemberAdapter(mentionChannelID, mentionChannelType)
        mentionRv.adapter = adapter
        adapter.onNormal()
        mentionAdapter = adapter

        adapter.setOnItemClickListener { adapterObj, _, position ->
            val entity = adapterObj.data[position] as? GroupMemberEntity ?: return@setOnItemClickListener
            var memberEntity = entity.member
            if (memberEntity == null) {
                memberEntity = WKChannelMember()
                if (entity.type == GroupMemberEntity.TYPE_AT_AIS) {
                    memberEntity.memberName = getString(R.string.all_ais)
                    memberEntity.memberUID = "-2"
                } else {
                    memberEntity.memberName = getString(R.string.all)
                    memberEntity.memberUID = "-1"
                }
            }
            var showName = memberEntity.memberName
            val channel = WKIM.getInstance().channelManager.getChannel(
                memberEntity.memberUID, WKChannelType.PERSONAL
            )
            if (channel != null && !TextUtils.isEmpty(channel.channelName)) {
                showName = channel.channelName
            }

            val et = binding.captionEt
            val deleteCount = 1 + mentionQueryLength
            for (i in 0 until deleteCount) {
                et.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            }
            suppressMentionDetection = true
            et.addSpan("@$showName ", memberEntity.memberUID)
            suppressMentionDetection = false
            mentionQueryLength = 0
            hideMentionList()
        }

        mentionRv.visibility = View.VISIBLE
    }

    private fun hideMentionList() {
        binding.mentionRecyclerView.visibility = View.GONE
    }

    private fun returnCaptionOnCancel() {
        val caption = binding.captionEt.text?.toString() ?: ""
        val result = Intent()
        result.putExtra("caption", caption)
        setResult(RESULT_CANCELED, result)
    }

    override fun onBackPressed() {
        returnCaptionOnCancel()
        super.onBackPressed()
    }

    private fun openImagePicker() {
        val remaining = WKRichTextComposeModel.MAX_IMAGES - imagePaths.size
        if (remaining <= 0) {
            val msg = String.format(
                getString(com.chat.base.R.string.richtext_image_limit),
                WKRichTextComposeModel.MAX_IMAGES
            )
            com.chat.base.utils.WKToastUtils.getInstance().showToastNormal(msg)
            return
        }
        GlideUtils.getInstance().chooseIMG(
            this, remaining, true, ChooseMimeType.img, false, false,
            object : GlideUtils.ISelectBack {
                override fun onBack(paths: MutableList<com.chat.base.glide.ChooseResult>?) {
                    if (!WKReader.isNotEmpty(paths)) return
                    for (result in paths!!) {
                        if (result == null || TextUtils.isEmpty(result.path)) continue
                        if (WKFileUtils.getInstance().isGif(result.path)) continue
                        if (imagePaths.size >= WKRichTextComposeModel.MAX_IMAGES) break
                        imagePaths.add(result.path)
                    }
                    gridAdapter.notifyDataSetChanged()
                }

                override fun onCancel() {}
            }
        )
    }

    private inner class GridAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_IMAGE = 0
        private val TYPE_ADD = 1

        override fun getItemViewType(position: Int): Int {
            return if (position < imagePaths.size) TYPE_IMAGE else TYPE_ADD
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val cellSize = (parent.width - AndroidUtilities.dp(48f)) / 3
            val actualCellSize = if (cellSize > 0) cellSize else AndroidUtilities.dp(100f)

            if (viewType == TYPE_ADD) {
                val cell = FrameLayout(this@WKRichTextCaptionActivity)
                cell.layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, actualCellSize
                )
                val margin = AndroidUtilities.dp(4f)
                val addView = TextView(this@WKRichTextCaptionActivity).apply {
                    text = "+"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
                    setTextColor(Color.parseColor("#999999"))
                    gravity = Gravity.CENTER
                    setBackgroundColor(Color.parseColor("#F0F0F0"))
                }
                val addLp = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply { setMargins(margin, margin, margin, margin) }
                cell.addView(addView, addLp)
                return AddVH(cell)
            }

            val cell = FrameLayout(this@WKRichTextCaptionActivity)
            cell.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, actualCellSize
            )

            val thumb = AppCompatImageView(this@WKRichTextCaptionActivity)
            thumb.scaleType = ImageView.ScaleType.CENTER_CROP
            val margin = AndroidUtilities.dp(4f)
            val thumbLp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { setMargins(margin, margin, margin, margin) }
            cell.addView(thumb, thumbLp)

            val remove = AppCompatImageView(this@WKRichTextCaptionActivity)
            remove.setImageResource(com.chat.base.R.drawable.ic_tray_remove)
            remove.setBackgroundResource(com.chat.base.R.drawable.bg_tray_remove_btn)
            remove.scaleType = ImageView.ScaleType.CENTER
            val removeLp = FrameLayout.LayoutParams(
                AndroidUtilities.dp(22f),
                AndroidUtilities.dp(22f),
                Gravity.TOP or Gravity.END
            )
            cell.addView(remove, removeLp)

            return ImageVH(cell, thumb, remove)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is AddVH) {
                holder.itemView.setOnClickListener { openImagePicker() }
                return
            }
            if (holder is ImageVH) {
                val path = imagePaths[position]
                Glide.with(this@WKRichTextCaptionActivity)
                    .load(path)
                    .transform(CenterCrop(), RoundedCorners(AndroidUtilities.dp(8f)))
                    .into(holder.thumb)

                holder.removeBtn.setOnClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos >= 0 && pos < imagePaths.size) {
                        imagePaths.removeAt(pos)
                        notifyDataSetChanged()
                        if (imagePaths.isEmpty()) {
                            returnCaptionOnCancel()
                            finish()
                        }
                    }
                }
            }
        }

        override fun getItemCount(): Int {
            val showAdd = imagePaths.size < WKRichTextComposeModel.MAX_IMAGES
            return imagePaths.size + if (showAdd) 1 else 0
        }

        inner class ImageVH(
            itemView: View,
            val thumb: AppCompatImageView,
            val removeBtn: AppCompatImageView
        ) : RecyclerView.ViewHolder(itemView)

        inner class AddVH(itemView: View) : RecyclerView.ViewHolder(itemView)
    }
}
