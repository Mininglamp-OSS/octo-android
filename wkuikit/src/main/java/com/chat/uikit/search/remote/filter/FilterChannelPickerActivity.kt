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

package com.chat.uikit.search.remote.filter

import android.content.Context
import android.content.Intent
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import com.chat.base.base.WKBaseActivity
import com.chat.base.search.global.dto.GlobalChannelRef
import com.chat.base.space.SpaceFilter
import com.chat.base.utils.SoftKeyboardUtils
import com.chat.uikit.R
import com.chat.uikit.databinding.ActFilterPickerLayoutBinding
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelType
import java.util.Locale

/**
 * 筛选页"所在群聊或子区"二级多选页。数据源同口径
 * [com.chat.uikit.chat.choose.ForwardDirectoryActivity.loadGroups]：本地会话里的群与子区
 * （排除单聊，对齐 iOS "所在群聊或子区"字面语义不含 DM）。
 *
 * Intent extras: `preselected`(ArrayList<GlobalChannelRef>)
 * 返回: `setResult(RESULT_OK)` + `putParcelableArrayListExtra("selectedChannels", ArrayList<GlobalChannelRef>)`
 */
class FilterChannelPickerActivity : WKBaseActivity<ActFilterPickerLayoutBinding>() {

    companion object {
        private const val EXTRA_PRESELECTED = "preselected"
        const val EXTRA_RESULT_CHANNELS = "selectedChannels"

        fun newIntent(context: Context, preselected: List<GlobalChannelRef>): Intent =
            Intent(context, FilterChannelPickerActivity::class.java).apply {
                putParcelableArrayListExtra(EXTRA_PRESELECTED, ArrayList(preselected))
            }
    }

    private lateinit var adapter: FilterPickAdapter
    private val allItems = mutableListOf<FilterPickItem>()
    // key = "channelId|channelType"
    private val checkedKeys = LinkedHashMap<String, GlobalChannelRef>()

    override fun getViewBinding(): ActFilterPickerLayoutBinding =
        ActFilterPickerLayoutBinding.inflate(layoutInflater)

    override fun setTitle(titleTv: TextView?) {
        titleTv?.setText(R.string.filter_picker_channel_title)
    }

    override fun getRightTvText(textView: TextView?): String = ""

    override fun initPresenter() {
        intent.getParcelableArrayListExtra<GlobalChannelRef>(EXTRA_PRESELECTED)?.forEach {
            checkedKeys[uniqueKey(it.channelId, it.channelType)] = it
        }
    }

    override fun initView() {
        adapter = FilterPickAdapter()
        initAdapter(wkVBinding.recyclerView, adapter)
        updateDoneButton()
        loadChannels()
    }

    override fun initListener() {
        wkVBinding.searchEt.imeOptions = EditorInfo.IME_ACTION_SEARCH
        wkVBinding.searchEt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                SoftKeyboardUtils.getInstance().hideSoftKeyboard(this)
                true
            } else false
        }
        wkVBinding.searchEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = applyFilter()
        })
        adapter.setOnItemClickListener { _, _, position ->
            SoftKeyboardUtils.getInstance().hideSoftKeyboard(this)
            toggle(position)
        }
        wkVBinding.doneTv.setOnClickListener {
            val data = Intent().putParcelableArrayListExtra(
                EXTRA_RESULT_CHANNELS,
                ArrayList(checkedKeys.values),
            )
            setResult(RESULT_OK, data)
            finish()
        }
    }

    /** 同口径 [com.chat.uikit.chat.choose.ForwardDirectoryActivity.loadGroups]，额外纳入子区。 */
    private fun loadChannels() {
        WKIM.getInstance().conversationManager.getAll { conversations ->
            val items = mutableListOf<FilterPickItem>()
            if (conversations != null) {
                for (conv in conversations) {
                    if (conv.channelType != WKChannelType.GROUP &&
                        conv.channelType != WKChannelType.COMMUNITY_TOPIC
                    ) continue
                    if (TextUtils.isEmpty(conv.channelID)) continue
                    if (SpaceFilter.shouldSkipChannelForSpace(conv.channelID, conv.channelType)) continue
                    var ch: WKChannel? = conv.wkChannel
                        ?: WKIM.getInstance().channelManager.getChannel(conv.channelID, conv.channelType)
                    if (ch == null) ch = WKChannel(conv.channelID, conv.channelType)
                    val showName = if (!TextUtils.isEmpty(ch.channelRemark)) ch.channelRemark
                        else if (!TextUtils.isEmpty(ch.channelName)) ch.channelName
                        else conv.channelID
                    val key = uniqueKey(conv.channelID, conv.channelType)
                    items.add(
                        FilterPickItem(
                            key = key,
                            channel = ch,
                            displayName = showName,
                            isCheck = checkedKeys.containsKey(key),
                        ),
                    )
                }
            }
            runOnUiThread {
                allItems.clear()
                allItems.addAll(items)
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        val keyword = wkVBinding.searchEt.text?.toString().orEmpty().trim().lowercase(Locale.getDefault())
        val filtered = if (keyword.isEmpty()) allItems
        else allItems.filter { it.displayName.lowercase(Locale.getDefault()).contains(keyword) }
        for (item in allItems) item.isCheck = checkedKeys.containsKey(item.key)
        adapter.setList(filtered)
    }

    /** [position] 为 adapter 当前列表（可能是搜索过滤后的子集）中的下标，直接用它刷新行，
     *  不走 `indexOf` —— 后者依赖 data class equals 与可变 isCheck 的微妙配合。 */
    private fun toggle(position: Int) {
        val item = adapter.getItem(position)
        if (checkedKeys.containsKey(item.key)) {
            checkedKeys.remove(item.key)
            item.isCheck = false
        } else {
            checkedKeys[item.key] = GlobalChannelRef(item.channel.channelID, item.channel.channelType)
            item.isCheck = true
        }
        adapter.notifyItemChanged(position)
        updateDoneButton()
    }

    private fun updateDoneButton() {
        wkVBinding.doneTv.text = getString(R.string.filter_picker_done, checkedKeys.size)
    }

    private fun uniqueKey(channelId: String, channelType: Byte): String = "$channelId|${channelType.toInt()}"
}
