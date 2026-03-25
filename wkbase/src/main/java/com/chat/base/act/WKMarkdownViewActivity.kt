package com.chat.base.act

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.chat.base.R
import com.chat.base.base.WKBaseActivity
import com.chat.base.databinding.ActMarkdownViewLayoutBinding
import com.chat.base.entity.PopupMenuItem
import com.chat.base.markdown.WKMarkwonProvider
import com.chat.base.utils.WKDialogUtils
import com.chat.base.utils.WKToastUtils
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class WKMarkdownViewActivity : WKBaseActivity<ActMarkdownViewLayoutBinding>() {

    private var url: String = ""

    override fun getViewBinding(): ActMarkdownViewLayoutBinding {
        return ActMarkdownViewLayoutBinding.inflate(layoutInflater)
    }

    override fun setTitle(titleTv: TextView) {
        // 标题在 initView 中通过 findViewById 设置（initView 先于 initTitleBar 执行）
    }

    override fun initPresenter() {}

    override fun getBackResourceID(backIv: ImageView): Int {
        return R.mipmap.ic_ab_back
    }

    override fun getRightIvResourceId(imageView: ImageView): Int {
        return R.mipmap.ic_ab_other
    }

    override fun rightLayoutClick() {
        super.rightLayoutClick()
        val list = mutableListOf<PopupMenuItem>()
        list.add(PopupMenuItem(getString(R.string.copy_url), R.mipmap.search_links) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Label", url))
            WKToastUtils.getInstance().showToastNormal(getString(R.string.copyed))
        })
        list.add(PopupMenuItem(getString(R.string.refresh), R.mipmap.tool_rotate) {
            loadMarkdown()
        })
        list.add(PopupMenuItem(getString(R.string.open_system_browser), R.mipmap.msg_openin) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        })
        val rightIV = findViewById<ImageView>(R.id.titleRightIv)
        WKDialogUtils.getInstance().showScreenPopup(rightIV, list)
    }

    override fun initView() {
        url = intent.getStringExtra("url") ?: ""
        if (TextUtils.isEmpty(url)) {
            WKToastUtils.getInstance().showToast(getString(R.string.nodata))
            finish()
            return
        }
        // 从 URL 提取文件名作为标题
        val fileName = Uri.parse(url).lastPathSegment ?: "Markdown"
        findViewById<TextView>(R.id.titleCenterTv).text = fileName

        wkVBinding.contentTv.movementMethod = LinkMovementMethod.getInstance()
        loadMarkdown()
    }

    override fun initListener() {}

    private fun loadMarkdown() {
        wkVBinding.progress.visibility = View.VISIBLE
        wkVBinding.contentTv.text = ""

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    wkVBinding.progress.visibility = View.GONE
                    wkVBinding.contentTv.text = getString(R.string.nodata)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val content = response.body?.string() ?: ""
                runOnUiThread {
                    wkVBinding.progress.visibility = View.GONE
                    if (content.isEmpty()) {
                        wkVBinding.contentTv.text = getString(R.string.nodata)
                    } else {
                        val rendered = WKMarkwonProvider.toMarkdown(
                            this@WKMarkdownViewActivity, content
                        )
                        wkVBinding.contentTv.text = rendered
                    }
                }
            }
        })
    }
}
