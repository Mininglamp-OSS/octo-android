package com.chat.uikit.chat.provider

import android.annotation.SuppressLint
import android.content.Context
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chat.uikit.R

class SelectTextPopAdapter(
    private val mContext: Context,
    private val mList: List<Pair<Int, String>>?
) : RecyclerView.Adapter<SelectTextPopAdapter.ViewHolder>() {
    private var itemWrapContent = false
    fun setItemWrapContent(itemWrapContent: Boolean) {
        this.itemWrapContent = itemWrapContent
    }

    private var listener: onClickItemListener? = null
    fun setOnclickItemListener(l: onClickItemListener?) {
        listener = l
    }

    interface onClickItemListener {
        fun onClick(position: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(mContext).inflate(R.layout.item_select_text_pop, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val drawableId = mList!![position].first
        val text = mList[position].second
        if (itemWrapContent) {
            val params = holder.tv_pop_func.layoutParams
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT
            holder.tv_pop_func.layoutParams = params
            holder.tv_pop_func.setPadding(
                SelectTextHelper.dp2px(8f),
                0,
                SelectTextHelper.dp2px(8f),
                0
            )
        }
        holder.iv_pop_icon.setImageResource(drawableId)
        holder.tv_pop_func.text = text
        holder.ll_pop_item.setOnClickListener { listener!!.onClick(position) }
    }

    override fun getItemCount(): Int {
        return mList?.size ?: 0
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ll_pop_item: LinearLayout
        val iv_pop_icon: ImageView
        val tv_pop_func: TextView

        init {
            ll_pop_item = itemView.findViewById(R.id.ll_pop_item)
            iv_pop_icon = itemView.findViewById(R.id.iv_pop_icon)
            tv_pop_func = itemView.findViewById(R.id.tv_pop_func)
        }
    }
}