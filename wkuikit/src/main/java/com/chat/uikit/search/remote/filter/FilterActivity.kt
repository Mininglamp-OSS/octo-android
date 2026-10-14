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

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.chat.base.base.WKBaseActivity
import com.chat.base.msgitem.WKContentType
import com.chat.base.search.global.dto.GlobalChannelRef
import com.chat.base.search.global.dto.GlobalSearchFilters
import com.chat.uikit.R
import com.chat.uikit.databinding.ActSearchFilterLayoutBinding
import com.chat.uikit.search.remote.GlobalActivity
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannelType
import java.util.Calendar
import java.util.Locale

/**
 * 筛选主页，对齐 iOS `WKChannelHistoryFilterVC`。Section 顺序：发送者 → 所在群聊或子区 →
 * 包含成员 → 聊天类型 → 消息类型(仅聊天记录tab)/文件类型(仅文件tab) → 日期范围 → 排序。
 *
 * 导航栏左侧关闭按钮走 [WKBaseActivity] 默认 `backListener`（finish，不保存）。
 * 底部"重置"只清空 draft 并刷新列表，不关闭页面、不自动应用；"应用"回传 filters 并关闭。
 */
class FilterActivity : WKBaseActivity<ActSearchFilterLayoutBinding>() {

    companion object {
        private const val EXTRA_TAB = "tab"
        private const val EXTRA_FILTERS = "filters"
        const val EXTRA_RESULT_FILTERS = "resultFilters"

        /** 回传发起时的 tab，调用方据此写回 `filtersByTab`，不依赖自己的 live view state。 */
        const val EXTRA_RESULT_TAB = "resultTab"

        /** 恢复在途编辑的 draft：旋转/深色模式/进程死亡后重建时覆盖 [EXTRA_FILTERS]。 */
        private const val KEY_DRAFT = "draft"

        private val CONTENT_TYPE_OPTIONS = listOf(
            WKContentType.WK_TEXT to R.string.filter_content_type_text,
            WKContentType.WK_IMAGE to R.string.filter_content_type_image,
            WKContentType.WK_VIDEO to R.string.filter_content_type_video,
            WKContentType.WK_FILE to R.string.filter_content_type_file,
            WKContentType.WK_MULTIPLE_FORWARD to R.string.filter_content_type_forward,
            14 to R.string.filter_content_type_richtext, // 图文：WKContentType 无常量，服务端枚举值 14
        )

        private val FILE_TYPE_OPTIONS = listOf(
            R.string.filter_file_type_document to listOf("doc", "docx"),
            R.string.filter_file_type_spreadsheet to listOf("xls", "xlsx"),
            R.string.filter_file_type_pdf to listOf("pdf"),
            R.string.filter_file_type_archive to listOf("zip", "rar", "7z", "tar", "gz"),
            R.string.filter_file_type_webpage to listOf("html", "htm"),
            R.string.filter_file_type_text to listOf("txt"),
            R.string.filter_file_type_markdown to listOf("md"),
            R.string.filter_file_type_gif to listOf("gif"),
            R.string.filter_file_type_video to listOf("mp4", "avi", "mov", "mkv", "webm"),
        )

        fun newIntent(context: Context, tab: Int, filters: GlobalSearchFilters): Intent =
            Intent(context, FilterActivity::class.java).apply {
                putExtra(EXTRA_TAB, tab)
                putExtra(EXTRA_FILTERS, filters)
            }
    }

    private lateinit var adapter: FilterAdapter
    private lateinit var draft: FilterDraft
    private var tab: Int = GlobalActivity.TAB_CHATS

    /**
     * 发送者与包含成员各自一个 launcher。
     *
     * 曾经是**单 launcher + 一个可变 mode 字段**区分写回目标，这有一个静默错数据的缺陷：
     * 该字段无持久化，配置变更（旋转/折叠/深色模式/字体/多窗口）或进程死亡让本页重建后
     * 它回退默认值，于是"发送者"的选择结果被写进 `memberUids`——请求发出 `member_uids`
     * 而非 `sender_ids`，结果错、无报错、用户无从察觉。且当时第二个分支是无条件
     * `else if`，所以标志丢失永远不会响亮失败，只会错路由。
     *
     * 两个 launcher 把维度编码在**注册身份**里而不是可变状态里，该类错误从结构上不可能再发生。
     * 维度仍通过 extra 传给 picker（它需要据此决定候选集，见 [MemberPickerScope]）。
     */
    private val senderPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val uids = result.data?.getStringArrayListExtra(FilterMemberPickerActivity.EXTRA_RESULT_UIDS)
            ?: return@registerForActivityResult
        draft.senderIds = uids.toMutableList()
        rebuildRows()
    }

    private val memberPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val uids = result.data?.getStringArrayListExtra(FilterMemberPickerActivity.EXTRA_RESULT_UIDS)
            ?: return@registerForActivityResult
        draft.memberUids = uids.toMutableList()
        rebuildRows()
    }

    private val channelPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val channels = result.data?.getParcelableArrayListExtra<GlobalChannelRef>(
            FilterChannelPickerActivity.EXTRA_RESULT_CHANNELS,
        ) ?: return@registerForActivityResult
        draft.channelIds = channels.toMutableList()
        rebuildRows()
    }

    override fun getViewBinding(): ActSearchFilterLayoutBinding =
        ActSearchFilterLayoutBinding.inflate(layoutInflater)

    override fun setTitle(titleTv: TextView?) {
        titleTv?.setText(R.string.filter_title)
    }

    override fun getRightTvText(textView: TextView?): String = ""

    override fun hideStatusBar(): Boolean = true

    // 弹窗样式对齐 iOS Form Sheet：关闭 SwipeBackActivityHelper 的全屏包裹层，
    // 否则 FilterDialogTheme 的透明/浮动窗口会被 SwipeBackLayout(match_parent) 覆盖铺满。
    override fun supportSlideBack(): Boolean = false

    override fun initPresenter() {
        tab = intent.getIntExtra(EXTRA_TAB, GlobalActivity.TAB_CHATS)
        val filters = intent.getParcelableExtra(EXTRA_FILTERS) ?: GlobalSearchFilters()
        draft = FilterDraft(filters)
    }

    /**
     * 重建后恢复在途编辑的 draft，覆盖 [initPresenter] 从不可变 intent extra 派生的初值
     * （同 [com.chat.uikit.chat.PreviewNewImgActivity] 的"恢复值优先于 intent extra"精度规则）。
     *
     * 不需要给 [FilterDraft] 加 Parcelable：[FilterDraft.toFilters] 对全部字段（含 legacy
     * `memberUid` 与 `sortTimeAsc`）无损，而 [GlobalSearchFilters] 本身是 `@Parcelize`，
     * 故存 `toFilters()` 再喂回构造函数就是零新类型的无损往返。
     */
    override fun initData(savedInstanceState: Bundle?) {
        super.initData(savedInstanceState)
        val saved = savedInstanceState?.getParcelable<GlobalSearchFilters>(KEY_DRAFT) ?: return
        draft = FilterDraft(saved)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(KEY_DRAFT, draft.toFilters())
    }

    override fun initView() {
        adapter = FilterAdapter()
        initAdapter(wkVBinding.recyclerView, adapter)
        rebuildRows()
    }

    override fun initListener() {
        wkVBinding.resetTv.setOnClickListener {
            draft.reset()
            rebuildRows()
        }
        wkVBinding.applyTv.setOnClickListener {
            val data = Intent()
                .putExtra(EXTRA_RESULT_FILTERS, draft.toFilters())
                .putExtra(EXTRA_RESULT_TAB, tab)
            setResult(RESULT_OK, data)
            finish()
        }
    }

    private fun rebuildRows() {
        val rows = mutableListOf<FilterRow>()

        rows += FilterRow.Header(getString(R.string.filter_section_sender))
        rows += FilterRow.Nav(
            title = getString(R.string.filter_section_sender),
            detail = if (draft.senderIds.isEmpty()) getString(R.string.filter_detail_all_members)
                else getString(R.string.filter_detail_selected_people, draft.senderIds.size),
            onClick = {
                senderPickerLauncher.launch(
                    FilterMemberPickerActivity.newIntent(
                        this,
                        getString(R.string.filter_picker_sender_title),
                        draft.senderIds,
                        MemberPickerScope.SENDER,
                    ),
                )
            },
        )

        rows += FilterRow.Header(getString(R.string.filter_section_channels))
        rows += FilterRow.Nav(
            title = getString(R.string.filter_section_channels),
            detail = if (draft.channelIds.isEmpty()) getString(R.string.filter_detail_unlimited)
                else getString(R.string.filter_detail_selected_count, draft.channelIds.size),
            onClick = {
                channelPickerLauncher.launch(FilterChannelPickerActivity.newIntent(this, draft.channelIds))
            },
        )

        rows += FilterRow.Header(getString(R.string.filter_section_members))
        rows += FilterRow.Nav(
            title = getString(R.string.filter_section_members),
            detail = if (draft.memberUids.isEmpty()) getString(R.string.filter_detail_unlimited)
                else getString(R.string.filter_detail_selected_people, draft.memberUids.size),
            onClick = {
                memberPickerLauncher.launch(
                    FilterMemberPickerActivity.newIntent(
                        this,
                        getString(R.string.filter_picker_member_title),
                        draft.memberUids,
                        MemberPickerScope.MEMBER,
                    ),
                )
            },
        )

        rows += FilterRow.Header(getString(R.string.filter_section_chat_type))
        rows += FilterRow.Check(
            title = getString(R.string.filter_chat_type_single),
            checked = draft.isChatTypeSingleSelected(),
            onClick = { draft.toggleChatTypeSingle(); rebuildRows() },
        )
        rows += FilterRow.Check(
            title = getString(R.string.filter_chat_type_group),
            checked = draft.isChatTypeGroupSelected(),
            onClick = { draft.toggleChatTypeGroup(); rebuildRows() },
        )

        if (tab == GlobalActivity.TAB_CHATS) {
            rows += FilterRow.Header(getString(R.string.filter_section_content_type))
            for ((value, labelRes) in CONTENT_TYPE_OPTIONS) {
                rows += FilterRow.Check(
                    title = getString(labelRes),
                    checked = draft.contentTypes.contains(value),
                    onClick = {
                        if (draft.contentTypes.contains(value)) draft.contentTypes.remove(value)
                        else draft.contentTypes.add(value)
                        rebuildRows()
                    },
                )
            }
        } else if (tab == GlobalActivity.TAB_FILES) {
            rows += FilterRow.Header(getString(R.string.filter_section_file_type))
            for ((labelRes, exts) in FILE_TYPE_OPTIONS) {
                val checked = exts.all { draft.fileExts.contains(it) }
                rows += FilterRow.Check(
                    title = getString(labelRes),
                    checked = checked,
                    onClick = {
                        if (checked) draft.fileExts.removeAll(exts.toSet())
                        else draft.fileExts.addAll(exts)
                        rebuildRows()
                    },
                )
            }
        }

        rows += FilterRow.Header(getString(R.string.filter_section_date))
        rows += FilterRow.Nav(
            title = getString(R.string.filter_date_from),
            detail = draft.sentAtFrom ?: getString(R.string.filter_date_not_set),
            onClick = { showDatePicker(isFrom = true) },
        )
        rows += FilterRow.Nav(
            title = getString(R.string.filter_date_to),
            detail = draft.sentAtTo ?: getString(R.string.filter_date_not_set),
            onClick = { showDatePicker(isFrom = false) },
        )
        rows += FilterRow.Nav(
            title = getString(R.string.filter_date_quick_select),
            detail = "",
            onClick = { showQuickDatePicker() },
        )

        // 排序仅聊天记录 tab 展示：sort 是请求体顶层字段且只有 L2 `_search_global_messages`
        // 消费，文件端点 `_search_global_files` 不发送（iOS 同样不发）。iOS 的筛选页在文件 tab
        // 仍渲染这个 section，但它对结果毫无影响——这里刻意不照搬该不一致，避免给用户一个
        // 点了没反应的开关。聊天记录 tab 则在下钻页真实生效（见 GroupDrillViewModel）。
        if (tab == GlobalActivity.TAB_CHATS) {
            rows += FilterRow.Header(getString(R.string.filter_section_sort))
            rows += FilterRow.Check(
                title = getString(R.string.filter_sort_desc),
                checked = draft.sortDescending,
                onClick = { draft.sortDescending = true; rebuildRows() },
            )
            rows += FilterRow.Check(
                title = getString(R.string.filter_sort_asc),
                checked = !draft.sortDescending,
                onClick = { draft.sortDescending = false; rebuildRows() },
            )
        }

        adapter.setList(rows)
    }

    /**
     * 起始/结束日期选择，对齐 iOS `showDatePickerForStart:`：
     *  - 初值取已选日期，无则今天；
     *  - 提供"清除"单独清掉该项（iOS 的 destructive 行为）；
     *  - 确定后若 from > to **自动交换**而不是报错——iOS 的守卫注释明确是为了避免
     *    两个日期倒挂导致服务端返回空结果，同时不打断用户操作。
     */
    private fun showDatePicker(isFrom: Boolean) {
        val calendar = Calendar.getInstance()
        val existing = if (isFrom) draft.sentAtFrom else draft.sentAtTo
        existing?.let { runCatching { parseDate(it, calendar) } }
        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val formatted = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                if (isFrom) draft.sentAtFrom = formatted else draft.sentAtTo = formatted
                normalizeDateRange()
                rebuildRows()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        )
        dialog.setButton(
            DatePickerDialog.BUTTON_NEUTRAL,
            getString(R.string.filter_date_clear),
        ) { _, _ ->
            if (isFrom) draft.sentAtFrom = null else draft.sentAtTo = null
            rebuildRows()
        }
        dialog.show()
    }

    /**
     * from > to 时交换两者，保证 `sent_at_from <= sent_at_to`（服务端契约要求，见
     * [GlobalSearchFilters] KDoc）。字符串是 `yyyy-MM-dd` 定宽格式，字典序即时间序，可直接比较。
     */
    private fun normalizeDateRange() {
        val from = draft.sentAtFrom
        val to = draft.sentAtTo
        if (!from.isNullOrEmpty() && !to.isNullOrEmpty() && from > to) {
            draft.sentAtFrom = to
            draft.sentAtTo = from
        }
    }

    /** 解析 `yyyy-MM-dd`；任一段非法则整体放弃（不半途修改 [calendar]，避免落到脏日期）。 */
    private fun parseDate(value: String, calendar: Calendar) {
        val parts = value.split("-")
        if (parts.size != 3) return
        val year = parts[0].toIntOrNull() ?: return
        val month = parts[1].toIntOrNull() ?: return
        val day = parts[2].toIntOrNull() ?: return
        calendar.set(year, month - 1, day)
    }

    private fun showQuickDatePicker() {
        val labels = arrayOf(
            getString(R.string.filter_date_preset_today),
            getString(R.string.filter_date_preset_7days),
            getString(R.string.filter_date_preset_30days),
            getString(R.string.filter_date_preset_all_time),
        )
        val presets = arrayOf(
            QuickDateRange.Preset.TODAY,
            QuickDateRange.Preset.LAST_7_DAYS,
            QuickDateRange.Preset.LAST_30_DAYS,
            QuickDateRange.Preset.ALL_TIME,
        )
        AlertDialog.Builder(this)
            .setItems(labels) { _, index ->
                val (from, to) = QuickDateRange.resolve(presets[index])
                draft.sentAtFrom = from
                draft.sentAtTo = to
                rebuildRows()
            }
            .show()
    }
}
