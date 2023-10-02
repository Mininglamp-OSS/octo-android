package com.chat.base.ui.components

import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import androidx.annotation.NonNull


class NormalClickableSpan(
    private val isShowUnderLine: Boolean,
    private val color: Int,
    val clickableContent: NormalClickableContent,
    @NonNull val iClick: IClick
) :
    ClickableSpan() {
    override fun onClick(p0: View) {
        iClick.onClick(p0)
    }

    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        ds.isUnderlineText = isShowUnderLine
        ds.color = color
        ds.clearShadowLayer()
    }

    interface IClick {
        fun onClick(view: View)
    }

}