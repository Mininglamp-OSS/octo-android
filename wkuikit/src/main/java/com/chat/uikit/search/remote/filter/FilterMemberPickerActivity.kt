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
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import com.chat.base.base.WKBaseActivity
import com.chat.base.config.WKConfig
import com.chat.base.utils.SoftKeyboardUtils
import com.chat.uikit.R
import com.chat.uikit.databinding.ActFilterPickerLayoutBinding
import com.chat.uikit.message.MsgModel
import com.chat.uikit.space.SpaceEntity
import com.chat.uikit.space.SpaceModel
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelType
import java.util.Locale

/**
 * 筛选页"发送者"/"包含成员"二级多选页。数据源同口径 [com.chat.uikit.chat.choose.ForwardDirectoryActivity]
 * 的联系人 tab：当前 space 全部成员。
 *
 * **候选集按维度不同**，见 [MemberPickerScope]：发送者含自己（置顶显示「我」）与 bot，
 * 包含成员排除两者。两个维度共用本页但不能共用过滤谓词。
 *
 * 不做 50 人上限前端校验——对齐 iOS 全局筛选模式现状（服务端 `senderIds`/`memberUids` 截断前
 * 50 个兜底，参见 [com.chat.base.search.global.dto.GlobalSearchFilters] KDoc）。
 *
 * Intent extras: `title`(String) `preselected`(ArrayList<String> uid) `scope`(Int, 见 [MemberPickerScope])
 * 返回: `setResult(RESULT_OK)` + `putStringArrayListExtra("selectedUids", ArrayList<String>)`
 *   + `putExtra("scope", Int)`（回传维度，调用方据此决定写回哪个字段，不依赖自身存活的字段状态）
 */
class FilterMemberPickerActivity : WKBaseActivity<ActFilterPickerLayoutBinding>() {

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_PRESELECTED = "preselected"
        private const val EXTRA_SCOPE = "scope"
        const val EXTRA_RESULT_UIDS = "selectedUids"

        /** 结果 Intent 里回传的维度 key。与 [EXTRA_SCOPE] 同名但属于不同 Intent，互不影响。 */
        const val EXTRA_RESULT_SCOPE = "scope"

        /** 恢复在途勾选：旋转/深色模式/进程死亡后重建时覆盖 [EXTRA_PRESELECTED]。 */
        private const val KEY_CHECKED_UIDS = "checkedUids"

        fun newIntent(
            context: Context,
            title: String,
            preselected: List<String>,
            scope: Int,
        ): Intent =
            Intent(context, FilterMemberPickerActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putStringArrayListExtra(EXTRA_PRESELECTED, ArrayList(preselected))
                putExtra(EXTRA_SCOPE, scope)
            }
    }

    private lateinit var adapter: FilterPickAdapter
    private val allItems = mutableListOf<FilterPickItem>()
    private val checkedUids = LinkedHashSet<String>()
    private var titleText: String = ""
    private var scope: Int = MemberPickerScope.MEMBER

    override fun getViewBinding(): ActFilterPickerLayoutBinding =
        ActFilterPickerLayoutBinding.inflate(layoutInflater)

    override fun setTitle(titleTv: TextView?) {
        titleTv?.text = titleText
    }

    override fun getRightTvText(textView: TextView?): String = ""

    override fun initPresenter() {
        titleText = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        scope = intent.getIntExtra(EXTRA_SCOPE, MemberPickerScope.MEMBER)
        checkedUids.addAll(intent.getStringArrayListExtra(EXTRA_PRESELECTED).orEmpty())
    }

    /**
     * 重建后恢复在途勾选，覆盖 [initPresenter] 从 intent 取的初值
     * （同 [com.chat.uikit.chat.PreviewNewImgActivity] 的"恢复值优先于 intent extra"精度规则）。
     * [checkedUids] 是 val，必须 clear + addAll 而不能重新赋值。
     */
    override fun initData(savedInstanceState: Bundle?) {
        super.initData(savedInstanceState)
        val saved = savedInstanceState?.getStringArrayList(KEY_CHECKED_UIDS) ?: return
        checkedUids.clear()
        checkedUids.addAll(saved)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(KEY_CHECKED_UIDS, ArrayList(checkedUids))
    }

    override fun initView() {
        adapter = FilterPickAdapter()
        initAdapter(wkVBinding.recyclerView, adapter)
        updateDoneButton()
        loadMembers()
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
            val data = Intent()
                .putStringArrayListExtra(EXTRA_RESULT_UIDS, ArrayList(checkedUids))
                .putExtra(EXTRA_RESULT_SCOPE, scope)
            setResult(RESULT_OK, data)
            finish()
        }
    }

    /**
     * 同口径 [com.chat.uikit.chat.choose.ForwardDirectoryActivity.loadMembers]：当前 space 全部成员，
     * 再按 [MemberPickerScope] 收窄到本维度的候选集。
     *
     * 所有失败/空分支都要清空列表并重跑 [applyFilter]——原址同样如此。否则切换 space
     * 或接口失败时，屏幕上会留着上一轮的陈旧成员行。
     */
    private fun loadMembers() {
        val spaceId = MsgModel.getInstance().currentSpaceId
        if (TextUtils.isEmpty(spaceId)) {
            clearMembers()
            return
        }
        val myUid = WKConfig.getInstance().uid
        SpaceModel.getInstance().getMembers(spaceId, object : SpaceModel.IMembersListener {
            override fun onResult(members: List<SpaceEntity.SpaceMember>?) {
                if (members == null) {
                    clearMembers()
                    return
                }
                val items = mutableListOf<FilterPickItem>()
                var selfItem: FilterPickItem? = null
                for (m in members) {
                    val isSelf = m.uid == myUid
                    if (!MemberPickerScope.shouldInclude(scope, isSelf, m.robot == 1)) continue
                    val ch = WKChannel(m.uid, WKChannelType.PERSONAL).apply {
                        channelName = m.name
                        // 原址 ForwardDirectoryActivity 会带上 robot 标记，合成 channel 丢了它
                        // 会影响头像/角标渲染，这里补齐。
                        robot = m.robot
                        WKIM.getInstance().channelManager.getChannel(m.uid, WKChannelType.PERSONAL)?.let { cached ->
                            if (!TextUtils.isEmpty(cached.channelRemark)) channelRemark = cached.channelRemark
                            if (cached.remoteExtraMap != null) remoteExtraMap = cached.remoteExtraMap
                        }
                    }
                    val showName = if (isSelf) getString(R.string.filter_picker_self)
                        else if (!TextUtils.isEmpty(ch.channelRemark)) ch.channelRemark
                        else if (!TextUtils.isEmpty(m.name)) m.name else m.uid
                    val item = FilterPickItem(
                        key = m.uid,
                        channel = ch,
                        displayName = showName,
                        isCheck = checkedUids.contains(m.uid),
                    )
                    if (isSelf) selfItem = item else items.add(item)
                }
                items.sortWith(compareBy { it.displayName.lowercase(Locale.getDefault()) })
                // 「我」置顶，不参与排序（同 iOS WKChannelHistorySenderPickerVC 把「我」放首行）。
                selfItem?.let { items.add(0, it) }
                runOnUiThread {
                    allItems.clear()
                    allItems.addAll(items)
                    applyFilter()
                }
            }

            override fun onError(code: Int, msg: String?) {
                clearMembers()
            }
        })
    }

    /** 清空候选并刷新列表，避免失败/空态下留着上一轮的陈旧行。 */
    private fun clearMembers() {
        runOnUiThread {
            allItems.clear()
            applyFilter()
        }
    }

    private fun applyFilter() {
        val keyword = wkVBinding.searchEt.text?.toString().orEmpty().trim().lowercase(Locale.getDefault())
        val filtered = if (keyword.isEmpty()) allItems
        else allItems.filter { it.displayName.lowercase(Locale.getDefault()).contains(keyword) }
        for (item in allItems) item.isCheck = checkedUids.contains(item.key)
        adapter.setList(filtered)
    }

    /** [position] 为 adapter 当前列表（可能是搜索过滤后的子集）中的下标，直接用它刷新行，
     *  不走 `indexOf` —— 后者依赖 data class equals 与可变 isCheck 的微妙配合。 */
    private fun toggle(position: Int) {
        val item = adapter.getItem(position)
        if (checkedUids.contains(item.key)) {
            checkedUids.remove(item.key)
            item.isCheck = false
        } else {
            checkedUids.add(item.key)
            item.isCheck = true
        }
        adapter.notifyItemChanged(position)
        updateDoneButton()
    }

    private fun updateDoneButton() {
        wkVBinding.doneTv.text = getString(R.string.filter_picker_done, checkedUids.size)
    }
}
