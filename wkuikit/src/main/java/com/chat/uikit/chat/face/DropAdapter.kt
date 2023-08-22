package com.chat.uikit.chat.face

import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.content.ContextCompat
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.uikit.R

class DropAdapter : BaseQuickAdapter<Drop, BaseViewHolder>(R.layout.item_chat_function_drop) {
    override fun convert(holder: BaseViewHolder, item: Drop) {
        val lineView = holder.getView<View>(R.id.lineView)
        val myShapeDrawable = lineView.background as GradientDrawable
        if (item.isSelect) {
            myShapeDrawable.setColor(ContextCompat.getColor(context, R.color.colorAccent));
        } else {
            myShapeDrawable.setColor(ContextCompat.getColor(context, R.color.transparent));
        }
    }
}