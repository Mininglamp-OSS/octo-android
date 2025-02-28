package com.chat.uikit.search.remote

import android.content.Intent
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import androidx.core.view.ViewCompat
import com.chat.base.base.WKBaseActivity
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.EndpointSID
import com.chat.base.endpoint.entity.ChatViewMenu
import com.chat.base.msgitem.WKContentType
import com.chat.base.net.HttpResponseCode
import com.chat.base.ui.Theme
import com.chat.base.utils.SoftKeyboardUtils
import com.chat.base.utils.WKReader
import com.chat.uikit.R
import com.chat.uikit.databinding.ActGlobalLayoutBinding
import com.chat.base.entity.GlobalSearchReq
import com.chat.base.search.GlobalSearchModel
import com.chat.uikit.search.SearchUserActivity
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener
import com.xinbida.wukongim.WKIM
import java.util.Objects

class GlobalActivity : WKBaseActivity<ActGlobalLayoutBinding>() {
    lateinit var adapter: GlobalAdapter
    private var keyword: String = ""
    private var page = 1
    override fun getViewBinding(): ActGlobalLayoutBinding {
        return ActGlobalLayoutBinding.inflate(layoutInflater)
    }

    override fun initView() {
        Theme.setColorFilter(this, wkVBinding.searchIv, R.color.popupTextColor)
        ViewCompat.setTransitionName(wkVBinding.searchIv, "searchView")
        Theme.setPressedBackground(wkVBinding.cancelTv)
        SoftKeyboardUtils.getInstance()
            .showSoftKeyBoard(this@GlobalActivity, wkVBinding.searchEt)

        wkVBinding.refreshLayout.setEnableRefresh(false)
        wkVBinding.refreshLayout.setEnableLoadMore(true)
        adapter = GlobalAdapter()
        initAdapter(wkVBinding.recyclerView, adapter)
    }

    override fun initListener() {
        wkVBinding.searchEt.imeOptions = EditorInfo.IME_ACTION_SEARCH
        wkVBinding.searchEt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                SoftKeyboardUtils.getInstance()
                    .hideSoftKeyboard(this@GlobalActivity)
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
                if (TextUtils.isEmpty(content)) {
                    adapter.setList(ArrayList())
                } else {
                    keyword = content
                    page = 1
                    getData(0)
                }
            }

        })
        wkVBinding.refreshLayout.setOnRefreshLoadMoreListener(object : OnRefreshLoadMoreListener {
            override fun onLoadMore(refreshLayout: RefreshLayout) {
                page++
                getData(1)
            }

            override fun onRefresh(refreshLayout: RefreshLayout) {}
        })
        wkVBinding.cancelTv.setOnClickListener { _ ->
            SoftKeyboardUtils.getInstance().hideSoftKeyboard(this)
            finish()
        }
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
                            )
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
                            )
                        )
                    }
                }
            }
        }
    }

    private fun getData(onlyMessage: Int) {
        val contentType = ArrayList<Int>()
        contentType.add(WKContentType.WK_TEXT)
        contentType.add(WKContentType.WK_FILE)
        val req = GlobalSearchReq(onlyMessage, keyword, "", 0, "", "", contentType, page, 20, 0, 0)
        GlobalSearchModel.search(req) { code, msg, resp ->
            wkVBinding.refreshLayout.finishRefresh()
            wkVBinding.refreshLayout.finishLoadMore()
            if (code == HttpResponseCode.success) {
                if (resp == null) {
                    return@search
                }
                if (page == 1) {
                    val list = ArrayList<DataVO>()
                    if (WKReader.isNotEmpty(resp.friends)) {
                        val textVO =
                            DataVO(DataVO.TEXT, null, null, getString(R.string.contacts))
                        list.add(textVO)
                        for (channel in resp.friends) {
                            val friendVO =
                                DataVO(DataVO.CHANNEL, channel, null, "")
                            list.add(friendVO)
                        }
                        val spanVO = DataVO(DataVO.SPAN, null, null, "")
                        list.add(spanVO)
                    }
                    if (WKReader.isNotEmpty(resp.groups)) {
                        val textVO =
                            DataVO(DataVO.TEXT, null, null, getString(R.string.group_chat))
                        list.add(textVO)
                        for (channel in resp.groups) {
                            val groupVO =
                                DataVO(DataVO.CHANNEL, channel, null, "")
                            list.add(groupVO)
                        }
                        val spanVO = DataVO(DataVO.SPAN, null, null, "")
                        list.add(spanVO)
                    }

                    val searchVO = DataVO(DataVO.SEARCH, null, null, keyword)
                    list.add(searchVO)
                    val spanVO = DataVO(DataVO.SPAN, null, null, "")
                    list.add(spanVO)
                    if (WKReader.isNotEmpty(resp.messages)) {
                        val textVO =
                            DataVO(DataVO.TEXT, null, null, getString(R.string.chat_records))
                        list.add(textVO)
                        for (message in resp.messages) {
                            val messageVO =
                                DataVO(DataVO.MESSAGE, message.channel, message, "")
                            list.add(messageVO)
                        }
                    }
                    adapter.setList(list)
                } else {
                    if (WKReader.isNotEmpty(resp.messages)) {
                        val list = ArrayList<DataVO>()
                        for (message in resp.messages) {
                            val messageVO =
                                DataVO(DataVO.MESSAGE, message.channel, message, "")
                            list.add(messageVO)
                        }
                        adapter.addData(list)
                    } else {
                        wkVBinding.refreshLayout.setEnableLoadMore(false)
                    }
                }
            } else {
                showToast(msg)
            }
        }
    }
}