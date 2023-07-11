package com.chat.base.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.R
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.entity.ParseQrCodeMenu
import com.lxj.xpopup.core.BottomPopupView

@SuppressLint("ViewConstructor")
class CommonBottomView(
    context: Context,
    val path: String,
    val list: List<BottomEntity>,
    private val iBottomClick: IBottomClick
) : BottomPopupView(context) {

    private var qrBitmap: Bitmap? = null

    override fun onCreate() {
        super.onCreate()
        findViewById<View>(R.id.cancelTv).setOnClickListener {
            dismiss()
            iBottomClick.onClick(-1, null)
        }
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val adapter = CommonAdapter()
        adapter.setList(list)
        recyclerView.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        adapter.isAnimationFirstOnly = true
        recyclerView.adapter = adapter
        adapter.setOnItemClickListener { adapter1: BaseQuickAdapter<*, *>, _: View?, position: Int ->
            val entity = adapter1.getItem(position) as BottomEntity?
            if (entity != null && entity.isCanClick) {
                dismiss()
                iBottomClick.onClick(position, entity)
            }
        }
        val scanQrCodeView = findViewById<View>(R.id.scanQrCodeView)

        if (!TextUtils.isEmpty(path)) {
            Glide.with(context)
                .asBitmap()
                .load(path)
                .into(object : CustomTarget<Bitmap?>(SIZE_ORIGINAL, SIZE_ORIGINAL) {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap?>?
                    ) {
                        EndpointManager.getInstance().invoke("parse_qrcode",
                            ParseQrCodeMenu(
                                context as AppCompatActivity, resource, false
                            ) { codeContentStr: String? ->
                                if (!TextUtils.isEmpty(codeContentStr)) {
                                    qrBitmap = resource
                                    scanQrCodeView.visibility = VISIBLE
                                }
                            })
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        }
        scanQrCodeView.setOnClickListener {
            EndpointManager.getInstance().invoke(
                "parse_qrcode",
                ParseQrCodeMenu(
                    context as AppCompatActivity,
                    qrBitmap,
                    true,
                    null
                )
            )
        }
    }

    override fun getImplLayoutId(): Int {
        return R.layout.wk_base_common_bottom_layout
    }

    class CommonAdapter :
        BaseQuickAdapter<BottomEntity, BaseViewHolder>(
            R.layout.wk_base_item_common_bottom_layout
        ) {

        override fun convert(holder: BaseViewHolder, item: BottomEntity) {
            holder.setText(R.id.titleCenterTv, item.content)
            holder.setText(R.id.subtitleTv, item.subContent)
            holder.setGone(R.id.subtitleTv, TextUtils.isEmpty(item.subContent))
            holder.setTextColor(
                R.id.titleCenterTv,
                ContextCompat.getColor(context, item.textColor)
            )
            holder.setTextColor(
                R.id.subtitleTv,
                ContextCompat.getColor(context, item.subColor)
            )
            holder.setEnabled(R.id.contentLayout, item.isCanClick)
            holder.setBackgroundResource(
                R.id.contentLayout,
                if (holder.bindingAdapterPosition == 0) R.drawable.common_bottom_view_top_bg else R.drawable.common_bottom_view_center_bg
            )
        }
    }

    interface IBottomClick {
        fun onClick(position: Int, bottomEntity: BottomEntity?)
    }

}