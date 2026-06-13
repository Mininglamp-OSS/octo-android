/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.chat.choose

import android.content.Intent
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import com.chat.base.base.WKBaseActivity
import com.chat.base.config.WKConfig
import com.chat.base.space.SpaceFilter
import com.chat.base.utils.HanziToPinyin
import com.chat.base.utils.SoftKeyboardUtils
import com.chat.base.views.sidebar.listener.OnQuickSideBarTouchListener
import com.chat.uikit.R
import com.chat.uikit.databinding.ActForwardDirectoryLayoutBinding
import com.chat.uikit.message.MsgModel
import com.chat.uikit.space.SpaceEntity
import com.chat.uikit.space.SpaceModel
import com.chat.uikit.utils.CharacterParser
import com.chat.uikit.utils.PyingUtils
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelType
import java.util.Locale

/**
 * 新建会话页. 1:1 对齐 iOS WKForwardDirectoryVC: 群聊 / 联系人 / Bot 三 tab.
 *
 * 入口: ChooseChatActivity 主页"新建会话" 紫字 → startActivityForResult.
 * 回传: setResult(RESULT_OK, "list" extra) ParcelableArrayList<WKChannel>.
 *
 * 数据源:
 *   - 群聊: GroupModel.getMyGroups (/group/my) + SpaceFilter 过滤当前 space
 *   - 联系人: ChannelDBManager.queryWithFollowAndStatus(PRIVATE, follow=1, status=normal),
 *           过滤 robot=1, 按 pinyin 排序
 *   - Bot: BotStoreService.getSpaceBots (robot/space_bots)
 */
class ForwardDirectoryActivity : WKBaseActivity<ActForwardDirectoryLayoutBinding>(), OnQuickSideBarTouchListener {

    private val TAB_GROUPS = 0
    private val TAB_CONTACTS = 1
    private val TAB_BOTS = 2

    private var currentTab = TAB_GROUPS
    private var rightBtn: Button? = null

    private val groupItems = mutableListOf<ForwardDirItem>()
    private val contactItems = mutableListOf<ForwardDirItem>()
    private val botItems = mutableListOf<ForwardDirItem>()

    /** 选中态: uniqueKey ("channelId|channelType") → ForwardDirItem,跨 tab 共享。 */
    private val checkedItems = LinkedHashMap<String, ForwardDirItem>()

    private lateinit var adapter: ForwardDirAdapter

    override fun getViewBinding(): ActForwardDirectoryLayoutBinding =
        ActForwardDirectoryLayoutBinding.inflate(layoutInflater)

    override fun setTitle(titleTv: TextView) {
        titleTv.setText(R.string.create_new_chat)
    }

    override fun getRightBtnText(titleRightBtn: Button): String {
        rightBtn = titleRightBtn
        return getString(R.string.sure)
    }

    override fun rightButtonClick() {
        super.rightButtonClick()
        if (checkedItems.isEmpty()) return
        val data = Intent()
        data.putParcelableArrayListExtra("list", ArrayList(checkedItems.values.map { it.channel }))
        setResult(RESULT_OK, data)
        finish()
    }

    override fun initView() {
        adapter = ForwardDirAdapter()
        initAdapter(wkVBinding.recyclerView, adapter)

        wkVBinding.tabsView.setTabs(
            arrayOf(
                getString(R.string.group_chat),
                getString(R.string.contacts),
                "Bot",
            ),
        )
        wkVBinding.tabsView.setOnTabSelectedListener { index ->
            currentTab = index
            applyFilter()
            updateQuickSideBarVisibility()
        }

        // 联系人 tab A-Z 快捷定位 (与 ContactsFragment 同款)
        wkVBinding.quickSideBarView.setLetters(CharacterParser.getInstance().list)
        wkVBinding.quickSideBarView.setOnQuickSideBarTouchListener(this)

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
            override fun afterTextChanged(s: Editable?) {
                applyFilter()
                updateQuickSideBarVisibility()
            }
        })

        adapter.setOnItemClickListener { _, _, position ->
            val item = adapter.getItem(position)
            toggle(item)
        }

        rightBtn?.visibility = View.INVISIBLE
    }

    override fun initData() {
        super.initData()
        loadGroups()
        loadMembers()  // 同时填充 contacts (robot=0) + bots (robot=1)
    }

    /** 群聊 tab: 与 MyGroupsListActivity 同口径,从本地会话过滤 GROUP + 当前 space。 */
    private fun loadGroups() {
        WKIM.getInstance().conversationManager.getAll { conversations ->
            val items = mutableListOf<ForwardDirItem>()
            if (conversations != null) {
                for (conv in conversations) {
                    if (conv.channelType != WKChannelType.GROUP) continue
                    if (TextUtils.isEmpty(conv.channelID)) continue
                    if (SpaceFilter.shouldSkipChannelForSpace(conv.channelID, WKChannelType.GROUP)) continue
                    var ch: WKChannel? = conv.wkChannel
                    if (ch == null) {
                        ch = WKIM.getInstance().channelManager
                            .getChannel(conv.channelID, WKChannelType.GROUP)
                    }
                    if (ch == null) ch = WKChannel(conv.channelID, WKChannelType.GROUP)
                    val showName = if (!TextUtils.isEmpty(ch.channelRemark)) ch.channelRemark
                        else if (!TextUtils.isEmpty(ch.channelName)) ch.channelName
                        else conv.channelID
                    items.add(
                        ForwardDirItem(
                            key = uniqueKey(ch.channelID, WKChannelType.GROUP),
                            channel = ch,
                            displayName = showName,
                            isRobot = false,
                            pinyin = "",
                            showHashPrefix = false,
                        ),
                    )
                }
            }
            // getAll 走 IO 线程, adapter.setList 必须回主线程刷
            runOnUiThread {
                groupItems.clear()
                groupItems.addAll(items)
                if (currentTab == TAB_GROUPS) applyFilter()
            }
        }
    }

    /**
     * 联系人 + Bot tab: 与 ContactsFragment.getContactsFromSpace / SpaceBotsListActivity 同口径,
     * 从 SpaceModel.getMembers 拿当前 space 全部成员, 按 robot 字段分流。
     */
    private fun loadMembers() {
        val spaceId = MsgModel.getInstance().currentSpaceId
        if (TextUtils.isEmpty(spaceId)) {
            contactItems.clear(); botItems.clear()
            if (currentTab != TAB_GROUPS) applyFilter()
            return
        }
        val myUid = WKConfig.getInstance().uid
        SpaceModel.getInstance().getMembers(spaceId, object : SpaceModel.IMembersListener {
            override fun onResult(members: List<SpaceEntity.SpaceMember>?) {
                contactItems.clear(); botItems.clear()
                if (members == null) {
                    if (currentTab != TAB_GROUPS) applyFilter()
                    return
                }
                for (m in members) {
                    if (m.uid == myUid) continue
                    val ch = WKChannel(m.uid, WKChannelType.PERSONAL).apply {
                        channelName = m.name
                        robot = m.robot
                        // 优先用本地缓存 remark / 头像 cacheKey
                        WKIM.getInstance().channelManager.getChannel(m.uid, WKChannelType.PERSONAL)?.let { cached ->
                            if (!TextUtils.isEmpty(cached.channelRemark)) channelRemark = cached.channelRemark
                            if (cached.remoteExtraMap != null) remoteExtraMap = cached.remoteExtraMap
                        }
                    }
                    val showName = if (!TextUtils.isEmpty(ch.channelRemark)) ch.channelRemark
                        else if (!TextUtils.isEmpty(m.name)) m.name else m.uid
                    val pinyin = if (!TextUtils.isEmpty(showName)) {
                        if (PyingUtils.getInstance().isStartNum(showName)) "#"
                        else HanziToPinyin.getInstance().getPY(showName) ?: "#"
                    } else "#"
                    val item = ForwardDirItem(
                        key = uniqueKey(m.uid, WKChannelType.PERSONAL),
                        channel = ch,
                        displayName = showName,
                        isRobot = m.robot == 1,
                        pinyin = pinyin,
                        showHashPrefix = false,
                    )
                    if (m.robot == 1) botItems.add(item) else contactItems.add(item)
                }
                contactItems.sortWith(compareBy { it.pinyin.lowercase(Locale.getDefault()) })
                botItems.sortWith(compareBy { it.pinyin.lowercase(Locale.getDefault()) })
                if (currentTab != TAB_GROUPS) applyFilter()
                updateQuickSideBarVisibility()
            }

            override fun onError(code: Int, msg: String?) {
                // 静默, contacts/bot tab 显示空(或保留 cached 数据)
            }
        })
    }

    private fun applyFilter() {
        val keyword = wkVBinding.searchEt.text?.toString().orEmpty().trim().lowercase(Locale.getDefault())
        val source = when (currentTab) {
            TAB_GROUPS -> groupItems
            TAB_CONTACTS -> contactItems
            TAB_BOTS -> botItems
            else -> emptyList()
        }
        val filtered = if (keyword.isEmpty()) source
        else source.filter { it.displayName.lowercase(Locale.getDefault()).contains(keyword) }
        // 同步选中态(checkedItems 是 source of truth, item.isCheck 仅渲染用)
        for (item in source) item.isCheck = checkedItems.containsKey(item.key)
        adapter.setList(filtered)
    }

    private fun toggle(item: ForwardDirItem) {
        if (checkedItems.containsKey(item.key)) {
            checkedItems.remove(item.key)
            item.isCheck = false
        } else {
            checkedItems[item.key] = item
            item.isCheck = true
        }
        adapter.notifyItemChanged(adapter.data.indexOf(item))
        updateRightBtn()
    }

    private fun updateRightBtn() {
        val n = checkedItems.size
        rightBtn?.let {
            if (n > 0) {
                it.visibility = View.VISIBLE
                it.text = String.format("%s(%s)", getString(R.string.sure), n)
            } else {
                it.visibility = View.INVISIBLE
                it.text = getString(R.string.sure)
            }
        }
    }

    private fun uniqueKey(channelId: String, channelType: Byte): String =
        "${channelId}|${channelType.toInt()}"

    /** 仅联系人 tab 且无搜索时显示 A-Z 侧栏。 */
    private fun updateQuickSideBarVisibility() {
        val keyword = wkVBinding.searchEt.text?.toString().orEmpty().trim()
        val show = currentTab == TAB_CONTACTS && keyword.isEmpty() && contactItems.isNotEmpty()
        wkVBinding.quickSideBarView.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) wkVBinding.quickSideBarTipsView.visibility = View.INVISIBLE
    }

    override fun onLetterChanged(letter: String?, position: Int, y: Float) {
        if (letter == null) return
        wkVBinding.quickSideBarTipsView.setText(letter, position, y)
        // 在当前展示数据(adapter.data)中找首个 pinyin 起始字母匹配项滚到位
        val data = adapter.data
        for (i in data.indices) {
            val py = data[i].pinyin
            if (!py.isEmpty() && py.uppercase(Locale.getDefault())
                    .startsWith(letter.uppercase(Locale.getDefault()))
            ) {
                wkVBinding.recyclerView.stopScroll()
                val lm = wkVBinding.recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                lm?.scrollToPositionWithOffset(i + adapter.headerLayoutCount, 0)
                return
            }
        }
    }

    override fun onLetterTouching(touching: Boolean) {
        // 触摸时显示 tips 浮层, 离手时隐藏
        wkVBinding.quickSideBarTipsView.visibility =
            if (touching && currentTab == TAB_CONTACTS) View.VISIBLE else View.INVISIBLE
    }
}
