package com.chat.uikit.chat.search

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chat.base.base.WKBaseActivity
import com.chat.base.endpoint.EndpointCategory
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.EndpointSID
import com.chat.base.endpoint.entity.ChatViewMenu
import com.chat.base.endpoint.entity.SearchChatContentMenu
import com.chat.base.entity.GlobalMessage
import com.chat.base.entity.GlobalSearchReq
import com.chat.base.msgitem.WKContentType
import com.chat.base.net.HttpResponseCode
import com.chat.base.search.GlobalSearchModel
import com.chat.base.ui.Theme
import com.chat.base.utils.SoftKeyboardUtils
import com.chat.base.utils.WKReader
import com.chat.base.views.FullyGridLayoutManager
import com.chat.uikit.R
import com.chat.uikit.databinding.ActMessageRecordLayoutBinding
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelType

class MessageRecordActivity : WKBaseActivity<ActMessageRecordLayoutBinding>() {
    private lateinit var channelID: String
    private var channelType: Byte = WKChannelType.PERSONAL
    private lateinit var searchTypeAdapter: SearchTypeAdapter
    private lateinit var messageAdapter: SearchMessageAdapter
    private var keyword = ""
    private var page = 1
    override fun getViewBinding(): ActMessageRecordLayoutBinding {
        return ActMessageRecordLayoutBinding.inflate(layoutInflater)
    }

    override fun initPresenter() {
        channelID = intent.getStringExtra("channel_id")!!
        channelType = intent.getByteExtra("channel_type", WKChannelType.PERSONAL)
    }

    override fun initView() {
        Theme.setPressedBackground(wkVBinding.cancelTv)
        wkVBinding.refreshLayout.setEnableRefresh(false)
        wkVBinding.refreshLayout.setEnableLoadMore(true)
        wkVBinding.searchIv.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(
                this, R.color.popupTextColor
            ), PorterDuff.Mode.MULTIPLY
        )
        messageAdapter = SearchMessageAdapter()
        initAdapter(wkVBinding.recyclerView, messageAdapter)
        val channel = WKChannel()
        channel.channelID = channelID
        channel.channelType = channelType
        val list = EndpointManager.getInstance()
            .invokes<SearchChatContentMenu>(EndpointCategory.wkSearchChatContent, channel)
        var i = 0
        val size: Int = list.size
        while (i < size) {
            if (list[i] == null || TextUtils.isEmpty(list[i].text)) {
                list.removeAt(i)
                i--
            }
            i++
        }
        searchTypeAdapter = SearchTypeAdapter(list)
        val layoutManager = FullyGridLayoutManager(this, 3)
        wkVBinding.typeRecyclerView.layoutManager = layoutManager
        wkVBinding.typeRecyclerView.adapter = searchTypeAdapter
    }

    override fun initListener() {
        wkVBinding.refreshLayout.setOnRefreshLoadMoreListener(object : OnRefreshLoadMoreListener {
            override fun onLoadMore(refreshLayout: RefreshLayout) {
                page++
                searchMessage()
            }

            override fun onRefresh(refreshLayout: RefreshLayout) {}
        })
        wkVBinding.searchEt.imeOptions = EditorInfo.IME_ACTION_SEARCH
        wkVBinding.searchEt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                SoftKeyboardUtils.getInstance().hideSoftKeyboard(this@MessageRecordActivity)
                return@setOnEditorActionListener true
            }
            false
        }

        wkVBinding.cancelTv.setOnClickListener { _ -> finish() }
        wkVBinding.searchEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                keyword = s.toString()
                if (TextUtils.isEmpty(keyword)) {
                    wkVBinding.resultView.visibility = View.GONE
                    wkVBinding.searchTypeLayout.visibility = View.VISIBLE
                } else {
                    page = 1
                    searchMessage()
                    wkVBinding.resultView.visibility = View.VISIBLE
                    wkVBinding.searchTypeLayout.visibility = View.GONE
                }
            }
        })

        searchTypeAdapter.setOnItemClickListener { adapter1: BaseQuickAdapter<*, *>, _: View?, position: Int ->
            val menu = adapter1.data[position] as SearchChatContentMenu?
            if (menu?.iClick != null) {
                SoftKeyboardUtils.getInstance().hideSoftKeyboard(this)
                menu.iClick.onClick(channelID, channelType)
            }
        }

        messageAdapter.setOnItemClickListener { adapter1: BaseQuickAdapter<*, *>, _: View?, position: Int ->
            val item = adapter1.getItem(position) as GlobalMessage?
            if (item != null) {
                val orderSeq = WKIM.getInstance().msgManager.getMessageOrderSeq(
                    item.message_seq,
                    item.channel.channel_id,
                    item.channel.channel_type
                )
                SoftKeyboardUtils.getInstance().hideSoftKeyboard(this)
                EndpointManager.getInstance().invoke(
                    EndpointSID.chatView,
                    ChatViewMenu(
                        this,
                        item.channel.channel_id,
                        item.channel.channel_type,
                        orderSeq,
                        false
                    )
                )
            }
        }
    }

    fun searchMessage() {
        val contentType = ArrayList<Int>()
        contentType.add(WKContentType.WK_TEXT)
        contentType.add(WKContentType.WK_FILE)
        val req =
            GlobalSearchReq(1, keyword, channelID, channelType, "", "", contentType, page, 20, 0, 0)

        GlobalSearchModel.search(req) { code, msg, resp ->
            wkVBinding.refreshLayout.finishRefresh()
            wkVBinding.refreshLayout.finishLoadMore()
            if (code != HttpResponseCode.success) {
                showToast(msg)
                return@search
            }
            if (resp == null || WKReader.isEmpty(resp.messages)) {
                wkVBinding.refreshLayout.setEnableLoadMore(false)
                if (page == 1) {
                    wkVBinding.noDataTv.visibility = View.VISIBLE
                }
                return@search
            }
            wkVBinding.noDataTv.visibility = View.GONE
            if (page == 1) {
                messageAdapter.setList(resp.messages)
            } else {
                messageAdapter.addData(resp.messages)
            }
        }
    }
}