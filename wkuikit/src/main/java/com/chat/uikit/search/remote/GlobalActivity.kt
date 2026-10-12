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

package com.chat.uikit.search.remote

import android.content.Intent
import android.text.Editable
import android.text.Html
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.base.WKBaseActivity
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.EndpointSID
import com.chat.base.endpoint.entity.ChatViewMenu
import com.chat.base.msgitem.WKContentType
import com.chat.base.net.HttpResponseCode
import com.chat.base.search.channel.ChannelSearchUiAction
import com.chat.base.search.channel.Rfc3339
import com.chat.base.search.channel.dto.FileHit
import com.chat.base.search.global.dto.GlobalSearchFilters
import com.chat.base.search.global.dto.GroupBucket
import com.chat.base.ui.Theme
import com.chat.base.ui.components.SearchTabBarView
import com.chat.base.utils.AndroidUtilities
import com.chat.base.utils.SoftKeyboardUtils
import com.chat.base.utils.WKReader
import com.chat.base.utils.WKTimeUtils
import com.chat.uikit.R
import com.chat.uikit.chat.provider.WKFileProvider
import com.chat.uikit.databinding.ActGlobalLayoutBinding
import com.chat.base.entity.GlobalChannel
import com.chat.base.entity.GlobalSearchReq
import com.chat.base.search.GlobalSearchModel
import com.chat.uikit.search.SearchUserActivity
import com.chat.uikit.search.remote.drill.GroupDrillActivity
import com.chat.uikit.search.remote.filter.FilterActivity
import com.chat.uikit.search.remote.filter.FilterChipBarView
import com.chat.uikit.search.remote.filter.FilterDraft
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannelType
import java.util.Objects
import kotlinx.coroutines.launch

class GlobalActivity : WKBaseActivity<ActGlobalLayoutBinding>() {

    companion object {
        const val TAB_ALL = 0
        const val TAB_CHATS = 1
        const val TAB_CONTACTS = 2
        const val TAB_GROUPS = 3
        const val TAB_FILES = 4
        const val TAB_COUNT = 5
        private const val CHATS_TAB_VM_KEY = "chatsTabViewModel"
        private val EMPTY_TEXTS = arrayOf(
            R.string.search_empty_all,
            R.string.search_empty_chats,
            R.string.search_empty_contacts,
            R.string.search_empty_groups,
            R.string.search_empty_files,
        )
    }

    lateinit var adapter: GlobalAdapter
    private lateinit var tabBar: SearchTabBarView
    private lateinit var filterChipBar: FilterChipBarView
    private lateinit var pagerAdapter: SearchResultPagerAdapter
    private lateinit var emptyViews: Array<EmptyStateView>
    private var keyword: String = ""

    // 聊天记录/联系人/群组/文件 4 个分类 tab 各自的 Adapter，与 TAB_ALL 的 [adapter] 分开，
    // 挂在各自独立的 RecyclerView 上（见 [initView] 的 pager.post{} 绑定）。
    private lateinit var chatsTabAdapter: GlobalAdapter
    private lateinit var contactsTabAdapter: GlobalAdapter
    private lateinit var groupsTabAdapter: GlobalAdapter
    private lateinit var filesTabAdapter: FilesTabAdapter

    // 筛选条件仅聊天记录/文件 tab 可编辑，各自独立，切 tab 不互相污染（见 [onPageSelected]）。
    private val filtersByTab = mutableMapOf<Int, GlobalSearchFilters>()

    private val filterLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val filters = result.data?.getParcelableExtra<GlobalSearchFilters>(FilterActivity.EXTRA_RESULT_FILTERS)
            ?: return@registerForActivityResult
        val tab = wkVBinding.searchResultPager.currentItem
        filtersByTab[tab] = filters
        updateChipBar(tab)
        // 筛选"应用"后立即对该 tab 的数据层生效并重新搜索；联系人/群组 tab 不支持筛选，不会走到这里
        // （筛选入口本身已对这两个 tab 隐藏）。
        reapplyFilters(tab, filters)
    }

    private val viewModel: GlobalSearchViewModel by viewModels()

    // 聊天记录 tab 独立于 TAB_ALL 的聊天记录段：各自的 keyword 生命周期和筛选条件不互相干扰，
    // 因此不能共享 [viewModel] 实例（后者服务 TAB_ALL，不支持 filters）。
    private val chatsTabViewModel: GlobalSearchViewModel by lazy {
        ViewModelProvider(this)[CHATS_TAB_VM_KEY, GlobalSearchViewModel::class.java]
    }
    private val filesTabViewModel: FilesSearchViewModel by lazy {
        ViewModelProvider(this)[FilesSearchViewModel::class.java]
    }

    // 三段结果缓存。任一段变化时 [rebuildList] 组合成最终列表提交给 adapter，
    // 避免联系人/群段（老 /search/global 异步）和聊天记录段（新 L1 异步）之间的顺序竞态。
    private var friendsList: List<GlobalChannel> = emptyList()
    private var groupsList: List<GlobalChannel> = emptyList()
    private var searchShortcut: DataVO? = null
    private var chatRecords: List<DataVO> = emptyList()

    // 「所有」tab 无结果态判定：仅当联系人/群段、聊天记录段两条异步请求都已各自返回一次
    // 才能判定"真的无结果"，避免请求未完成时提前显示"无搜索结果"。searchShortcut（"搜索更多联系人"
    // 入口）不计入结果判断——它是入口不是结果类别（对应 R4）。关键词变化时重置为 false。
    private var contactsGroupsLoaded = false
    private var chatRecordsLoaded = false

    // Toast 去重：state.collect 每次 emission 都触发（含 repeatOnLifecycle 从 STOPPED 恢复时重放
    // 上一次 state），无去重会让同一 errorCode 重复弹 toast 打扰用户。
    // 新一轮请求发起时 errorCode 被清 null，允许下次错误再次弹。
    private var lastToastedErrorCode: String? = null

    override fun getViewBinding(): ActGlobalLayoutBinding {
        return ActGlobalLayoutBinding.inflate(layoutInflater)
    }

    override fun setTitle(titleTv: TextView?) {
        titleTv?.text = getString(R.string.search_title)
    }

    override fun getRightTvText(textView: TextView?): String =
        getString(R.string.search_filter)

    override fun rightLayoutClick() {
        val tab = wkVBinding.searchResultPager.currentItem
        val filters = filtersByTab[tab] ?: GlobalSearchFilters()
        filterLauncher.launch(FilterActivity.newIntent(this, tab, filters))
    }

    override fun backListener(type: Int) {
        SoftKeyboardUtils.getInstance().hideSoftKeyboard(this)
        finish()
    }

    /** 点击搜索框以外的空白区域时收起键盘，对齐常见搜索页交互。 */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val touchedView = currentFocus
            if (touchedView is EditText && !isTouchInsideView(touchedView, ev)) {
                SoftKeyboardUtils.getInstance().hideSoftKeyboard(this)
                touchedView.clearFocus()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun isTouchInsideView(view: View, ev: MotionEvent): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val x = ev.rawX.toInt()
        val y = ev.rawY.toInt()
        return x >= location[0] && x <= location[0] + view.width &&
            y >= location[1] && y <= location[1] + view.height
    }

    override fun initView() {
        Theme.setColorFilter(this, wkVBinding.searchIv, R.color.popupTextColor)
        ViewCompat.setTransitionName(wkVBinding.searchIv, "searchView")
        SoftKeyboardUtils.getInstance()
            .showSoftKeyBoard(this@GlobalActivity, wkVBinding.searchEt)

        pagerAdapter = SearchResultPagerAdapter()
        wkVBinding.searchResultPager.adapter = pagerAdapter
        wkVBinding.searchResultPager.offscreenPageLimit = TAB_COUNT - 1
        // 显式钉住初始页为"所有"：ViewPager2 子 RecyclerView 在键盘弹出等窗口 resize 场景下
        // 可能因为抢焦点意外滚动，不能只依赖 setAdapter 后的默认位置 0。
        wkVBinding.searchResultPager.setCurrentItem(TAB_ALL, false)

        tabBar = SearchTabBarView(
            this,
            arrayOf(
                getString(R.string.str_all),
                getString(R.string.chat_records),
                getString(R.string.contacts),
                getString(R.string.search_tab_groups),
                getString(R.string.str_file),
            ),
        )
        wkVBinding.searchTabBarContainer.addView(
            tabBar,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT),
        )
        tabBar.setOnTabSelectedListener { index ->
            wkVBinding.searchResultPager.setCurrentItem(index, true)
        }
        wkVBinding.searchResultPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tabBar.selectTabWithoutCallback(position)
                updateFilterButtonVisibility(position)
                updateChipBar(position)
                // 用户可能先在其它 tab 输入关键词再切过来；setKeyword 内部按 keyword 去重
                // （相同值直接 return），不会造成重复请求，与 iOS "VM 内部去重" 思路一致。
                when (position) {
                    TAB_CHATS -> chatsTabViewModel.setKeyword(keyword)
                    TAB_FILES -> filesTabViewModel.setKeyword(keyword)
                }
            }
        })

        filterChipBar = FilterChipBarView(this)
        wkVBinding.filterChipBarContainer.addView(
            filterChipBar,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT),
        )

        adapter = GlobalAdapter()
        // ViewPager2 的子 RecyclerView 要等 onBindViewHolder 跑完才存在，延迟到 post{} 里绑定。
        // 期间若已有 adapter.setList() 调用（见 rebuildList），BaseQuickAdapter 允许未挂 RecyclerView
        // 时持有数据，稍后 attach 时会用现有数据首次绑定，不会丢。
        wkVBinding.searchResultPager.post {
            pagerAdapter.getPageRecyclerView(TAB_ALL)?.let { initAdapter(it, adapter) }
        }

        chatsTabAdapter = GlobalAdapter()
        contactsTabAdapter = GlobalAdapter()
        groupsTabAdapter = GlobalAdapter()
        filesTabAdapter = FilesTabAdapter()
        wkVBinding.searchResultPager.post {
            pagerAdapter.getPageRecyclerView(TAB_CHATS)?.let { initAdapter(it, chatsTabAdapter) }
            pagerAdapter.getPageRecyclerView(TAB_CONTACTS)?.let { initAdapter(it, contactsTabAdapter) }
            pagerAdapter.getPageRecyclerView(TAB_GROUPS)?.let { initAdapter(it, groupsTabAdapter) }
            pagerAdapter.getPageRecyclerView(TAB_FILES)?.let { initAdapter(it, filesTabAdapter) }
        }
        setupChannelTabItemClick(chatsTabAdapter, TAB_CHATS)
        setupChannelTabItemClick(contactsTabAdapter, TAB_CONTACTS)
        setupChannelTabItemClick(groupsTabAdapter, TAB_GROUPS)
        filesTabAdapter.setOnItemClickListener { a, _, position ->
            val hit = a.getItem(position) as? FileHit ?: return@setOnItemClickListener
            if (hit.channel_id.isEmpty()) return@setOnItemClickListener
            EndpointManager.getInstance().invoke(
                EndpointSID.chatView,
                ChatViewMenu(this@GlobalActivity, hit.channel_id, hit.channel_type, 0, false),
            )
        }
        observeChatsTabState()
        observeFilesTabState()

        emptyViews = Array(TAB_COUNT) { index ->
            EmptyStateView(this).apply {
                setText(getString(EMPTY_TEXTS[index]))
                // Error 态重试：仅聊天记录/文件 tab 有独立 VM 可重跑（对齐 iOS emptyView.onRetry
                // → syncActiveVM，只有这两个 tab 走 _search_global_* 端点）。
                onRetry = when (index) {
                    TAB_CHATS -> ({ retryTab(TAB_CHATS) })
                    TAB_FILES -> ({ retryTab(TAB_FILES) })
                    else -> null
                }
            }
        }
        wkVBinding.searchResultPager.post {
            for (i in 0 until TAB_COUNT) {
                pagerAdapter.getPageContainer(i)?.addView(
                    emptyViews[i],
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT),
                )
            }
            updateAllEmptyView()
        }
        observeChatRecordsState()
    }

    override fun initData() {
        super.initData()
        // initTitleBar() 在 initView()/initListener() 之后、initData() 之前执行（见 WKBaseActivity
        // onCreate 顺序），titleRightLayout 要到这里才非 null，更早调用 hideTitleRightView() 会静默失效。
        updateFilterButtonVisibility(TAB_ALL)
        updateChipBar(TAB_ALL)
    }

    override fun initListener() {
        wkVBinding.searchEt.imeOptions = EditorInfo.IME_ACTION_SEARCH
        wkVBinding.searchEt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                SoftKeyboardUtils.getInstance()
                    .hideSoftKeyboard(this@GlobalActivity)
                viewModel.triggerNow()
                return@setOnEditorActionListener true
            }
            false
        }
        wkVBinding.searchEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                val content = s.toString()
                keyword = content
                // 聊天记录段：服务端 L1 + 竞态防护（debounce/sequence/cancel）由 ViewModel 统管。
                viewModel.setKeyword(content)
                chatsTabViewModel.setKeyword(content)
                filesTabViewModel.setKeyword(content)
                contactsGroupsLoaded = false
                chatRecordsLoaded = false
                if (TextUtils.isEmpty(content)) {
                    friendsList = emptyList()
                    groupsList = emptyList()
                    searchShortcut = null
                    chatRecords = emptyList()
                    adapter.setList(ArrayList())
                    contactsTabAdapter.setList(ArrayList())
                    groupsTabAdapter.setList(ArrayList())
                } else {
                    // 联系人/群段仍走本地 + 老 /search/global；见 [loadContactsAndGroups]。
                    loadContactsAndGroups()
                }
                updateAllEmptyView()
            }

        })
        adapter.setOnItemClickListener { adapter, _, position ->
            val item = adapter.data[position]
            if (item is DataVO) {
                when (item.type) {
                    DataVO.CHANNEL -> {
                        EndpointManager.getInstance().invoke(
                            EndpointSID.chatView,
                            ChatViewMenu(
                                this@GlobalActivity,
                                item.channel!!.channel_id,
                                item.channel.channel_type,
                                0,
                                false
                            ).apply { fromSearch = true }
                        )
                    }

                    DataVO.SEARCH -> {
                        SoftKeyboardUtils.getInstance().hideSoftKeyboard(this)
                        val searchKey = Objects.requireNonNull(wkVBinding.searchEt.text).toString()
                        val intent = Intent(
                            this,
                            SearchUserActivity::class.java
                        )
                        intent.putExtra("searchKey", searchKey)
                        startActivity(intent)
                    }

                    DataVO.MESSAGE -> {
                        val orderSeq = WKIM.getInstance().msgManager.getMessageOrderSeq(
                            item.message!!.message_seq,
                            item.channel!!.channel_id,
                            item.channel.channel_type
                        )
                        EndpointManager.getInstance().invoke(
                            EndpointSID.chatView,
                            ChatViewMenu(
                                this@GlobalActivity,
                                item.channel.channel_id,
                                item.channel.channel_type,
                                orderSeq,
                                false
                            ).apply { fromSearch = true }
                        )
                    }

                    DataVO.LOCAL_MSG -> {
                        if (item.messageCount == 1 && item.orderSeq > 0) {
                            EndpointManager.getInstance().invoke(
                                EndpointSID.chatView,
                                ChatViewMenu(
                                    this@GlobalActivity,
                                    item.channel!!.channel_id,
                                    item.channel.channel_type,
                                    item.orderSeq,
                                    false
                                ).apply { fromSearch = true }
                            )
                        } else {
                            // 服务端 L1 桶 (item.orderSeq=0 硬编码): 跳 GroupDrillActivity
                            // 走 L2 (_search_global_messages)，传群 id 自动展开群+子区，
                            // 让用户一次看到该会话下所有关键词命中 (对齐服务端契约 §3)。
                            // 老 IMSDK 本地聚合桶 (orderSeq>0 只走定位分支; orderSeq=0 且
                            // messageCount>1 也走这里) 也跳 GroupDrill，服务端 L2 补齐历史。
                            val intent = Intent(this@GlobalActivity, GroupDrillActivity::class.java)
                            intent.putExtra("channel_id", item.channel!!.channel_id)
                            intent.putExtra("channel_type", item.channel.channel_type)
                            intent.putExtra("keyword", item.keyword)
                            startActivity(intent)
                        }
                    }
                }
            }
        }
    }

    /**
     * 拉联系人 + 群段：本地会话搜索合并老 /search/global（保留原来的 friends/groups 逻辑）。
     * 老接口的 messages 段不再消费（服务端 L1 已覆盖），避免同一条消息被展示两次。
     */
    private fun loadContactsAndGroups() {
        val contentType = ArrayList<Int>()
        contentType.add(WKContentType.WK_TEXT)
        contentType.add(WKContentType.WK_FILE)
        val req = GlobalSearchReq(0, keyword, "", 0, "", "", contentType, 1, 20, 0, 0)
        val requestKeyword = keyword
        GlobalSearchModel.search(req) { code, msg, resp ->
            // 关键词已变或已清空时丢弃这次响应，避免过期结果覆盖当前段
            if (requestKeyword != keyword || TextUtils.isEmpty(keyword)) return@search
            if (code == HttpResponseCode.success && resp != null) {
                // 合并本地会话搜索，弥补服务端 space_id 对群组过滤不完整
                val localResult = searchLocalConversations(keyword)
                friendsList = mergeChannels(resp.friends, localResult.first)
                groupsList = mergeChannels(resp.groups, localResult.second)
                searchShortcut = DataVO(DataVO.SEARCH, null, null, keyword)
                contactsGroupsLoaded = true
                rebuildList()
                renderContactsAndGroupsTabs()
                updateAllEmptyView()
            } else if (code != HttpResponseCode.success) {
                // 老接口失败也要退化：仅用本地会话结果，保证联系人/群段不因服务端问题空掉
                val localResult = searchLocalConversations(keyword)
                friendsList = localResult.first
                groupsList = localResult.second
                searchShortcut = DataVO(DataVO.SEARCH, null, null, keyword)
                contactsGroupsLoaded = true
                rebuildList()
                renderContactsAndGroupsTabs()
                updateAllEmptyView()
                if (!msg.isNullOrEmpty()) showToast(msg)
            }
        }
    }

    /**
     * 联系人/群组 tab：直接复用 [loadContactsAndGroups] 已拉到的 [friendsList]/[groupsList]，
     * 不发起独立请求（这两个 tab 没有专门的服务端模糊搜索接口，对齐 iOS 设计——tab 不支持筛选，
     * 数据域与"全部" tab 共享同一次 /search/global 结果）。
     */
    private fun renderContactsAndGroupsTabs() {
        contactsTabAdapter.setList(friendsList.map { DataVO(DataVO.CHANNEL, it, null, "") })
        groupsTabAdapter.setList(groupsList.map { DataVO(DataVO.CHANNEL, it, null, "") })
    }

    /**
     * 聊天记录/联系人/群组 tab 复用同一套 [DataVO.CHANNEL] 点击跳转逻辑：直接打开对应会话。
     *
     * [tab] 用于取该 tab 当前生效的筛选条件带入 GroupDrill（仅聊天记录 tab 有筛选入口，
     * 联系人/群组 tab 在 [filtersByTab] 里取不到值，等价于空筛选）。
     */
    private fun setupChannelTabItemClick(tabAdapter: BaseQuickAdapter<DataVO, BaseViewHolder>, tab: Int) {
        tabAdapter.setOnItemClickListener { a, _, position ->
            val item = a.getItem(position) as? DataVO ?: return@setOnItemClickListener
            val channel = item.channel ?: return@setOnItemClickListener
            when (item.type) {
                DataVO.CHANNEL -> EndpointManager.getInstance().invoke(
                    EndpointSID.chatView,
                    ChatViewMenu(this@GlobalActivity, channel.channel_id, channel.channel_type, 0, false)
                        .apply { fromSearch = true },
                )
                DataVO.LOCAL_MSG -> {
                    if (item.messageCount == 1 && item.orderSeq > 0) {
                        EndpointManager.getInstance().invoke(
                            EndpointSID.chatView,
                            ChatViewMenu(this@GlobalActivity, channel.channel_id, channel.channel_type, item.orderSeq, false)
                                .apply { fromSearch = true },
                        )
                    } else {
                        val intent = Intent(this@GlobalActivity, GroupDrillActivity::class.java)
                        intent.putExtra("channel_id", channel.channel_id)
                        intent.putExtra("channel_type", channel.channel_type)
                        intent.putExtra("keyword", item.keyword)
                        intent.putExtra("filters", filtersByTab[tab] ?: GlobalSearchFilters())
                        startActivity(intent)
                    }
                }
            }
        }
    }

    /** 聊天记录 tab：独立 keyword/filters 生命周期的 L1 聚合结果，对齐 [observeChatRecordsState] 的映射规则。 */
    private fun observeChatsTabState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatsTabViewModel.state.collect { state ->
                    if (state.keyword.isEmpty()) {
                        chatsTabAdapter.setList(ArrayList())
                        chatsTabLoaded = false
                        chatsTabError = false
                        updateAllEmptyView()
                        return@collect
                    }
                    val action = state.uiAction()
                    val items = when (action) {
                        null -> state.groups.map { bucketToDataVO(it, state.keyword) }
                        ChannelSearchUiAction.FALLBACK_TO_LOCAL -> localMessagesAsDataVO(state.keyword)
                        else -> emptyList()
                    }
                    // FALLBACK_TO_LOCAL 已有本地结果兜底，不算错误态；其余非 null action
                    // （限流/未启用/权限/校验/通用）视为加载失败，展示 Error + 重试。
                    chatsTabError = action != null && action != ChannelSearchUiAction.FALLBACK_TO_LOCAL
                    chatsTabAdapter.setList(items)
                    if (!state.isLoading) chatsTabLoaded = true
                    updateAllEmptyView()
                }
            }
        }
    }

    /**
     * 文件 tab：无本地兜底数据源（文件不像消息有 IMSDK 本地聚合），任何失败都落 Error 态
     * 并展示重试按钮——不能像消息那样回退到本地聚合，若混进 NoResults 则「接口未部署」
     * 与「真的没文件」在 UI 上无法区分（对齐 iOS 文件 tab 不做本地回落、直接走 Error 的行为）。
     */
    private fun observeFilesTabState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                filesTabViewModel.state.collect { state ->
                    if (state.keyword.isEmpty()) {
                        filesTabAdapter.setList(ArrayList())
                        filesTabLoaded = false
                        filesTabError = false
                        updateAllEmptyView()
                        return@collect
                    }
                    val action = state.uiAction()
                    filesTabError = action != null
                    filesTabAdapter.setList(if (action == null) state.files else emptyList())
                    if (!state.isLoading) filesTabLoaded = true
                    updateAllEmptyView()
                }
            }
        }
    }

    /** 订阅 [GlobalSearchViewModel.state]：把 L1 [GroupBucket] 映射为 [DataVO.LOCAL_MSG]，
     * 失败/离线时自动回退到 IMSDK 本地聚合（与频道内搜索 FALLBACK_TO_LOCAL 语义一致）。 */
    private fun observeChatRecordsState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    // 关键词已被用户清空 → 强制清聊天记录段
                    if (state.keyword.isEmpty()) {
                        if (chatRecords.isNotEmpty()) {
                            chatRecords = emptyList()
                            rebuildList()
                        }
                        return@collect
                    }
                    chatRecords = when (state.uiAction()) {
                        null -> {
                            // 新请求成功或刚发起时 errorCode 被清空 → 允许下次 toast
                            lastToastedErrorCode = null
                            state.groups.map { bucketToDataVO(it, state.keyword) }
                        }
                        ChannelSearchUiAction.FALLBACK_TO_LOCAL -> localMessagesAsDataVO(state.keyword)
                        // 其他错误（限流 / 未启用 / 权限 / 校验 / 通用错误）：清空聊天记录段
                        // 让用户明确知道服务端没有返回结果；对应 toast 由 [showErrorToast] 处理
                        else -> {
                            if (state.errorCode != lastToastedErrorCode) {
                                lastToastedErrorCode = state.errorCode
                                showErrorToast(state)
                            }
                            emptyList()
                        }
                    }
                    // isLoading=false 才代表这轮请求（含 debounce 发出后的响应落地）真正完成，
                    // 不能用 uiAction()==null 判断——setKeyword 刚写入 keyword 时也满足该条件但请求未发出。
                    if (!state.isLoading) {
                        chatRecordsLoaded = true
                    }
                    rebuildList()
                    updateAllEmptyView()
                }
            }
        }
    }

    private fun bucketToDataVO(bucket: GroupBucket, keyword: String): DataVO {
        val isThread = bucket.channel_type == WKChannelType.COMMUNITY_TOPIC
        // 子区展示 "父群名 · 子区名"，方便用户识别归属；纯群/DM 直接展 group_name
        val displayName = when {
            isThread && !bucket.thread_name.isNullOrEmpty() -> {
                if (bucket.group_name.isNotEmpty()) "${bucket.group_name} · ${bucket.thread_name}"
                else bucket.thread_name!!
            }
            bucket.group_name.isNotEmpty() -> bucket.group_name
            else -> bucket.channel_id
        }
        val gc = GlobalChannel().apply {
            channel_id = bucket.channel_id
            channel_type = bucket.channel_type
            channel_name = displayName
        }
        // 单条命中直接展示 snippet；多条则展示"约 N 条"折叠。
        // 服务端 snippet 带 <mark>...</mark>，客户端 Adapter 走 keyword 高亮，先剥掉 mark 标签。
        val text = if (bucket.match_count <= 1L && bucket.preview.isNotEmpty()) {
            stripMarkTags(bucket.preview[0].snippet)
        } else {
            val prefix = if (bucket.match_count_approx) "约 " else ""
            "$prefix${bucket.match_count} 条相关聊天记录"
        }
        // messageCount>1 且 orderSeq=0 → 点击落到 GroupDrillActivity 走 L2
        val count = bucket.match_count.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        return DataVO(DataVO.LOCAL_MSG, gc, null, text, keyword, count, 0L)
    }

    /** 服务端失败/离线兜底：本地 IMSDK 消息聚合，输出 shape 与 L1 结果一致。 */
    private fun localMessagesAsDataVO(keyword: String): List<DataVO> {
        val local = WKIM.getInstance().msgManager.search(keyword) ?: return emptyList()
        return local.mapNotNull { result ->
            val ch = result.wkChannel ?: return@mapNotNull null
            val fullChannel = WKIM.getInstance().channelManager.getChannel(ch.channelID, ch.channelType) ?: ch
            val displayName = fullChannel.channelRemark?.takeIf { it.isNotEmpty() }
                ?: fullChannel.channelName?.takeIf { it.isNotEmpty() }
                ?: fullChannel.channelID
                ?: ""
            val gc = GlobalChannel().apply {
                channel_id = fullChannel.channelID ?: ""
                channel_type = fullChannel.channelType
                channel_name = displayName
            }
            val snippet = if (result.messageCount == 1 && !result.searchableWord.isNullOrEmpty()) {
                snippetFromText(result.searchableWord, keyword, 40)
            } else {
                "${result.messageCount} 条相关聊天记录"
            }
            DataVO(DataVO.LOCAL_MSG, gc, null, snippet, keyword, result.messageCount, result.orderSeq)
        }
    }

    private fun stripMarkTags(text: String): String =
        text.replace("<mark>", "").replace("</mark>", "")

    /**
     * 筛选入口仅聊天记录/文件 tab 可见（R8）；联系人/群组/全部均隐藏。
     *
     * 不走基类 showTitleRightView()/hideTitleRightView()：它们隐藏时把 titleRightLayout
     * 设为 GONE，宽度被回收会导致 titleCenterTv 的居中可用区间变化，tab 切换时标题整体
     * 跟着挪动。改为固定保留该区域宽度（INVISIBLE，不参与动画），对齐 iOS 系统导航栏标题
     * 真正居中、不随左右按钮增减而偏移的效果。
     */
    private fun updateFilterButtonVisibility(tab: Int) {
        val visible = tab == TAB_CHATS || tab == TAB_FILES
        findViewById<View>(R.id.titleRightLayout)?.visibility =
            if (visible) View.VISIBLE else View.INVISIBLE
    }

    /**
     * 筛选条件变化（应用筛选 / 点 chip ✕ 移除某一维度）后，把新筛选条件同步到对应 tab 的
     * ViewModel 并重新触发搜索。重置 loaded 标记并清空旧列表后再刷新空态视图——否则上一轮
     * 筛选结果仍留在 adapter 里，[updateTabEmptyView] 的 isEmpty 判断会读到"非空"，导致
     * "搜索中" loading UI 被跳过，直到新结果落地才刷新（旧结果期间无任何加载态提示）。
     */
    private fun reapplyFilters(tab: Int, filters: GlobalSearchFilters) {
        when (tab) {
            TAB_CHATS -> {
                chatsTabLoaded = false
                chatsTabError = false
                chatsTabAdapter.setList(ArrayList())
                chatsTabViewModel.setFilters(filters)
                chatsTabViewModel.triggerNow()
            }
            TAB_FILES -> {
                filesTabLoaded = false
                filesTabError = false
                filesTabAdapter.setList(ArrayList())
                filesTabViewModel.setFilters(filters)
                filesTabViewModel.triggerNow()
            }
        }
        updateAllEmptyView()
    }

    /**
     * Error 态重试：用该 tab 当前生效的筛选条件重跑一次搜索（对齐 iOS
     * `emptyView.onRetry = ^{ [ws syncActiveVM]; }`——重试不改变任何条件，只是再发一次请求）。
     */
    private fun retryTab(tab: Int) {
        reapplyFilters(tab, filtersByTab[tab] ?: GlobalSearchFilters())
    }

    /**
     * 已应用筛选条件 chip 条，对齐 iOS `WKGlobalSearchV2VC updateChipBar` 的文案规则（见设计计划）。
     * 仅聊天记录/文件 tab 展示；点 chip 文字重新打开筛选弹窗，点"✕"单独清除该维度并重新渲染，
     * 同时同步到对应 tab 的 ViewModel 并重新触发搜索（与 [filterLauncher] 应用筛选的流程一致）。
     */
    private fun updateChipBar(tab: Int) {
        if (tab != TAB_CHATS && tab != TAB_FILES) {
            filterChipBar.setChips(emptyList())
            return
        }
        val filters = filtersByTab[tab] ?: GlobalSearchFilters()
        val chips = mutableListOf<FilterChipBarView.ChipItem>()

        fun openFilter() {
            filterLauncher.launch(FilterActivity.newIntent(this, tab, filtersByTab[tab] ?: GlobalSearchFilters()))
        }

        fun clearAndReapply(mutate: FilterDraft.() -> Unit) {
            val draft = FilterDraft(filtersByTab[tab] ?: GlobalSearchFilters())
            draft.mutate()
            val newFilters = draft.toFilters()
            filtersByTab[tab] = newFilters
            updateChipBar(tab)
            reapplyFilters(tab, newFilters)
        }

        filters.senderIds?.takeIf { it.isNotEmpty() }?.let { senders ->
            chips += FilterChipBarView.ChipItem(
                text = getString(R.string.filter_chip_sender_count, senders.size),
                onTap = ::openFilter,
                onClear = { clearAndReapply { senderIds.clear() } },
            )
        }
        filters.channelIds?.takeIf { it.isNotEmpty() }?.let { channels ->
            chips += FilterChipBarView.ChipItem(
                text = getString(R.string.filter_chip_channels_count, channels.size),
                onTap = ::openFilter,
                onClear = { clearAndReapply { channelIds.clear() } },
            )
        }
        filters.memberUids?.takeIf { it.isNotEmpty() }?.let { members ->
            chips += FilterChipBarView.ChipItem(
                text = getString(R.string.filter_chip_members_count, members.size),
                onTap = ::openFilter,
                onClear = { clearAndReapply { memberUids.clear() } },
            )
        }
        filters.channelTypes?.takeIf { it.isNotEmpty() }?.let { types ->
            // 判定口径与筛选页勾选态一致（对齐 iOS updateChipBar 的 dm/grp 两个谓词）：
            // 群聊只要含 2 或 5 任一即算选中，不做精确集合匹配——否则服务端/历史数据
            // 只给 [2] 时会被误判成"单聊+群聊"。
            val dm = types.contains(1.toByte())
            val grp = types.any { it == 2.toByte() || it == 5.toByte() }
            val textRes = when {
                dm && grp -> R.string.filter_chip_chat_type_both
                dm -> R.string.filter_chip_chat_type_single
                else -> R.string.filter_chip_chat_type_group
            }
            chips += FilterChipBarView.ChipItem(
                text = getString(textRes),
                onTap = ::openFilter,
                onClear = { clearAndReapply { channelTypes.clear() } },
            )
        }
        if (tab == TAB_CHATS) {
            filters.contentTypes?.takeIf { it.isNotEmpty() }?.let { types ->
                chips += FilterChipBarView.ChipItem(
                    text = getString(R.string.filter_chip_content_type_count, types.size),
                    onTap = ::openFilter,
                    onClear = { clearAndReapply { contentTypes.clear() } },
                )
            }
        }
        if (tab == TAB_FILES) {
            filters.fileExts?.takeIf { it.isNotEmpty() }?.let {
                chips += FilterChipBarView.ChipItem(
                    text = getString(R.string.filter_chip_file_type),
                    onTap = ::openFilter,
                    onClear = { clearAndReapply { fileExts.clear() } },
                )
            }
        }
        if (!filters.sentAtFrom.isNullOrEmpty() || !filters.sentAtTo.isNullOrEmpty()) {
            chips += FilterChipBarView.ChipItem(
                text = formatDateRangeChip(filters.sentAtFrom, filters.sentAtTo),
                onTap = ::openFilter,
                onClear = {
                    clearAndReapply {
                        sentAtFrom = null
                        sentAtTo = null
                    }
                },
            )
        }
        // 排序 chip 仅在聊天记录 tab 且非默认（时间正序）时出现：倒序是默认值不占 chip 位，
        // 文件 tab 不支持排序故不展示（与 FilterActivity 隐藏排序 section 保持一致）。
        // ✕ 清除即回到倒序默认值。
        if (tab == TAB_CHATS && filters.sortTimeAsc) {
            chips += FilterChipBarView.ChipItem(
                text = getString(R.string.filter_chip_sort_asc),
                onTap = ::openFilter,
                onClear = { clearAndReapply { sortDescending = true } },
            )
        }

        filterChipBar.setChips(chips)
    }

    /** 日期范围 chip 文案，对齐 iOS：缺起始显示"最早"，缺结束显示"至今"，格式 yyyy/MM/dd。 */
    private fun formatDateRangeChip(from: String?, to: String?): String {
        fun format(raw: String?): String? =
            raw?.takeIf { it.isNotEmpty() }?.let { it.replace("-", "/").take(10) }
        val fromText = format(from) ?: getString(R.string.filter_chip_date_earliest)
        val toText = format(to) ?: getString(R.string.filter_chip_date_until_now)
        return "$fromText ~ $toText"
    }

    private fun showErrorToast(state: GlobalSearchViewModel.State) {
        val msg = when (state.uiAction()) {
            ChannelSearchUiAction.RATE_LIMITED ->
                if (state.retryAfterSec > 0) "请求过于频繁，${state.retryAfterSec}s 后重试"
                else "请求过于频繁，请稍后重试"
            ChannelSearchUiAction.FEATURE_DISABLED -> "搜索功能未启用"
            ChannelSearchUiAction.BLOCK_NOT_FOUND -> null   // 不打扰
            ChannelSearchUiAction.VALIDATION_ERROR -> null  // 触发条件不满足是常态
            else -> state.errorMessage?.takeIf { it.isNotEmpty() } ?: "搜索失败，请重试"
        }
        msg?.let { showToast(it) }
    }

    /** 组合三段 + 分隔符，一次提交给 adapter。空字符串场景由 [afterTextChanged] 直接 setList(空) 处理。 */
    private fun rebuildList() {
        if (TextUtils.isEmpty(keyword)) {
            adapter.setList(ArrayList())
            return
        }
        val list = ArrayList<DataVO>()
        if (WKReader.isNotEmpty(friendsList)) {
            list.add(DataVO(DataVO.TEXT, null, null, getString(R.string.contacts)))
            for (channel in friendsList) {
                list.add(DataVO(DataVO.CHANNEL, channel, null, ""))
            }
            list.add(DataVO(DataVO.SPAN, null, null, ""))
        }
        if (WKReader.isNotEmpty(groupsList)) {
            list.add(DataVO(DataVO.TEXT, null, null, getString(R.string.group_chat)))
            for (channel in groupsList) {
                list.add(DataVO(DataVO.CHANNEL, channel, null, ""))
            }
            list.add(DataVO(DataVO.SPAN, null, null, ""))
        }
        searchShortcut?.let {
            list.add(it)
            list.add(DataVO(DataVO.SPAN, null, null, ""))
        }
        if (chatRecords.isNotEmpty()) {
            list.add(DataVO(DataVO.TEXT, null, null, getString(R.string.chat_records)))
            list.addAll(chatRecords)
            list.add(DataVO(DataVO.SPAN, null, null, ""))
        }
        adapter.setList(list)
    }

    // 聊天记录/文件 tab 各自是否已完成至少一轮加载（!isLoading），用于空态判定——
    // 不能复用 TAB_ALL 的 chatRecordsLoaded（那是 [viewModel] 的状态，与这两个独立 VM 实例无关）。
    private var chatsTabLoaded = false
    private var filesTabLoaded = false

    // 首屏请求是否以「真错误」结束（非本地兜底、非空结果），用于区分 Error 态与 NoResults 态。
    // 对齐 iOS WKGlobalSearchV2VC.activeFirstPageError：接口未部署/网络异常/服务不可用时
    // 必须展示「加载失败 + 重试」，不能和「未找到相关内容」混为一谈。文件 tab 无本地兜底，
    // 这个区分尤其关键（否则接口 404 与真的没文件在 UI 上完全一样）。
    private var chatsTabError = false
    private var filesTabError = false

    /**
     * 更新 5 个 tab 的空态提示。「所有」tab 区分三种态：空输入提示 / 无搜索结果 / 隐藏（有结果或仍在
     * 加载中）。联系人/群段和聊天记录段是两路独立异步请求，任一路已完成（不论结果是否为空）就应
     * 退出 loading——不必等另一路也完成，否则一路已经有结果也会被 loading UI 挡住。
     * 其余 4 个分类 tab 现已接入真实数据层，同样按"已完成一轮加载且结果为空"判定；
     * 联系人/群组 tab 复用 [contactsGroupsLoaded]（与 TAB_ALL 共享同一次 /search/global 请求）。
     */
    private fun updateAllEmptyView() {
        if (!::emptyViews.isInitialized) return
        val isEmptyKeyword = TextUtils.isEmpty(keyword)
        emptyViews[TAB_ALL].apply {
            when {
                isEmptyKeyword -> {
                    render(getString(R.string.search_empty_all), loading = false)
                    visibility = View.VISIBLE
                }
                !contactsGroupsLoaded && !chatRecordsLoaded -> {
                    render(getString(R.string.search_searching), loading = true)
                    visibility = View.VISIBLE
                }
                contactsGroupsLoaded && chatRecordsLoaded &&
                    friendsList.isEmpty() && groupsList.isEmpty() && chatRecords.isEmpty() -> {
                    render(getString(R.string.search_no_result), loading = false)
                    visibility = View.VISIBLE
                }
                else -> visibility = View.GONE
            }
        }
        updateTabEmptyView(TAB_CHATS, isEmptyKeyword, loaded = chatsTabLoaded, isEmpty = chatsTabAdapter.data.isEmpty(), isError = chatsTabError)
        updateTabEmptyView(TAB_CONTACTS, isEmptyKeyword, loaded = contactsGroupsLoaded, isEmpty = friendsList.isEmpty())
        updateTabEmptyView(TAB_GROUPS, isEmptyKeyword, loaded = contactsGroupsLoaded, isEmpty = groupsList.isEmpty())
        updateTabEmptyView(TAB_FILES, isEmptyKeyword, loaded = filesTabLoaded, isEmpty = filesTabAdapter.data.isEmpty(), isError = filesTabError)
    }

    /**
     * 分类 tab 空态/加载态优先级，对齐 iOS `WKGlobalSearchV2VC.updateEmptyState` 的判定顺序：
     * 空输入提示(WaitingInput) > 加载失败(Error，带重试) > 搜索中(Loading) > 无搜索结果(NoResults) > 隐藏(有结果)。
     *
     * iOS 的 Offline 态依赖 `WKNetworkListener`，Android 侧未接入网络监听，并入 Error 态展示。
     */
    private fun updateTabEmptyView(
        tab: Int,
        isEmptyKeyword: Boolean,
        loaded: Boolean,
        isEmpty: Boolean,
        isError: Boolean = false,
    ) {
        emptyViews[tab].apply {
            visibility = when {
                isEmptyKeyword -> {
                    render(getString(EMPTY_TEXTS[tab]), loading = false)
                    View.VISIBLE
                }
                isError && isEmpty -> {
                    render(
                        getString(R.string.search_load_failed),
                        loading = false,
                        secondary = getString(R.string.search_load_failed_hint),
                        retryText = getString(R.string.search_retry),
                    )
                    View.VISIBLE
                }
                !loaded && isEmpty -> {
                    render(getString(R.string.search_searching), loading = true)
                    View.VISIBLE
                }
                loaded && isEmpty -> {
                    render(
                        getString(R.string.search_no_result),
                        loading = false,
                        secondary = getString(R.string.search_no_result_hint),
                    )
                    View.VISIBLE
                }
                else -> View.GONE
            }
        }
    }

    private fun snippetFromText(text: String, keyword: String, maxLength: Int): String {
        val idx = text.lowercase().indexOf(keyword.lowercase())
        if (idx < 0) return if (text.length > maxLength) "${text.substring(0, maxLength)}..." else text
        val radius = (maxLength - keyword.length) / 2
        val start = maxOf(0, idx - radius)
        val end = minOf(text.length, idx + keyword.length + radius)
        var snippet = text.substring(start, end)
        if (start > 0) snippet = "...$snippet"
        if (end < text.length) snippet = "$snippet..."
        return snippet
    }

    /**
     * 从本地会话列表按关键词搜索联系人和群组（与 iOS searchLocalConversations 一致）。
     * 服务端 space_id 过滤对群组支持不完整，本地搜索作为补充。
     * @return Pair<联系人列表, 群组列表>
     */
    private fun searchLocalConversations(keyword: String): Pair<List<GlobalChannel>, List<GlobalChannel>> {
        val friends = mutableListOf<GlobalChannel>()
        val groups = mutableListOf<GlobalChannel>()
        val conversations = WKIM.getInstance().conversationManager.all ?: return Pair(friends, groups)
        val lowerKeyword = keyword.lowercase()
        for (conv in conversations) {
            if (conv.channelType == WKChannelType.COMMUNITY_TOPIC) continue
            val channel = conv.getWkChannel() ?: continue
            val displayName = when {
                !channel.channelRemark.isNullOrEmpty() -> channel.channelRemark
                !channel.channelName.isNullOrEmpty() -> channel.channelName
                else -> continue
            }
            if (!displayName.lowercase().contains(lowerKeyword)) continue
            val gc = GlobalChannel().apply {
                channel_id = conv.channelID ?: ""
                channel_type = conv.channelType
                channel_name = displayName
                channel_remark = channel.channelRemark ?: ""
            }
            if (conv.channelType == WKChannelType.GROUP) {
                groups.add(gc)
            } else {
                friends.add(gc)
            }
        }
        return Pair(friends, groups)
    }

    /**
     * 合并服务端和本地搜索结果，按 channel_id 去重
     */
    private fun mergeChannels(remote: List<GlobalChannel>?, local: List<GlobalChannel>): List<GlobalChannel> {
        val result = mutableListOf<GlobalChannel>()
        val ids = mutableSetOf<String>()
        remote?.forEach {
            if (ids.add(it.channel_id)) result.add(it)
        }
        local.forEach {
            if (ids.add(it.channel_id)) result.add(it)
        }
        return result
    }
}

/**
 * 每个 tab 的空态/加载态占位视图，对齐 iOS `WKChannelHistorySearchEmptyView`：
 * spinner / 主文案 / 副文案 / 重试按钮，由 [render] 按模式控制显隐。
 *
 * 模式优先级与 iOS `WKGlobalSearchV2VC.updateEmptyState` 一致：
 * Error > Loading > WaitingInput > NoResults（Offline 态 Android 侧未接入网络监听，
 * 与 Error 合并展示）。重试按钮仅 Error 态可见，对应 iOS `retryBtn.hidden` 规则。
 */
private class EmptyStateView(context: android.content.Context) : LinearLayout(context) {
    private val spinner = ProgressBar(context).apply {
        isIndeterminate = true
    }
    private val textView = TextView(context).apply {
        setTextColor(context.getColor(com.chat.base.R.color.color999))
        textSize = 14f
        gravity = Gravity.CENTER
    }
    private val secondaryView = TextView(context).apply {
        setTextColor(context.getColor(com.chat.base.R.color.color999))
        textSize = 13f
        gravity = Gravity.CENTER
        visibility = View.GONE
    }
    private val retryBtn = TextView(context).apply {
        setTextColor(context.getColor(com.chat.base.R.color.colorAccent))
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(
            AndroidUtilities.dp(16f),
            AndroidUtilities.dp(6f),
            AndroidUtilities.dp(16f),
            AndroidUtilities.dp(6f),
        )
        visibility = View.GONE
    }

    /** Error 态重试回调，由宿主注入（对齐 iOS `emptyView.onRetry`）。 */
    var onRetry: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, AndroidUtilities.dp(80f), 0, 0)
        addView(spinner, LayoutParams(AndroidUtilities.dp(24f), AndroidUtilities.dp(24f)).apply {
            bottomMargin = AndroidUtilities.dp(8f)
        })
        addView(textView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(secondaryView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = AndroidUtilities.dp(4f)
        })
        addView(retryBtn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = AndroidUtilities.dp(8f)
        })
        retryBtn.setOnClickListener { onRetry?.invoke() }
    }

    fun setText(text: String) {
        textView.text = text
    }

    /**
     * [loading] 为 true 时显示 spinner + 文案；false 时只显示文案（对齐 iOS Loading 模式隐藏 icon 的规则）。
     * [secondary] 非空时展示副文案；[retryText] 非空时展示重试按钮（仅 Error 态传入）。
     */
    fun render(
        text: String,
        loading: Boolean,
        secondary: String? = null,
        retryText: String? = null,
    ) {
        textView.text = text
        spinner.visibility = if (loading) View.VISIBLE else View.GONE
        secondaryView.text = secondary.orEmpty()
        secondaryView.visibility = if (secondary.isNullOrEmpty()) View.GONE else View.VISIBLE
        retryBtn.text = retryText.orEmpty()
        retryBtn.visibility = if (retryText.isNullOrEmpty()) View.GONE else View.VISIBLE
    }
}
