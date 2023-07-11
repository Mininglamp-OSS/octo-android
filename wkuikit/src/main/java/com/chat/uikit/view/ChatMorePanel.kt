package com.chat.uikit.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.chat.base.config.WKConstants
import com.chat.base.utils.LayoutHelper
import com.chat.base.views.keyboard.IPanel

class ChatMorePanel : FrameLayout, IPanel {
    private val moreLayout: LinearLayout

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        moreLayout = LinearLayout(context)
        moreLayout.orientation = LinearLayout.HORIZONTAL
        addView(
            moreLayout, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT.toFloat()
            )
        )
        init()
    }

    override fun onVisibilityChanged(
        changedView: View,
        visibility: Int
    ) {
        super.onVisibilityChanged(changedView, visibility)
        val layoutParams = layoutParams
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.height = getPanelHeight()
        setLayoutParams(layoutParams)
    }

    private fun init() {
    }

    private val mMorePanelInvisibleRunnable =
        Runnable { visibility = View.GONE }

    override fun reset() {
        postDelayed(mMorePanelInvisibleRunnable, 0)
    }

    override fun getPanelHeight(): Int {
        return WKConstants.getKeyboardHeight()
    }

    fun addBottomView(view: View) {
        moreLayout.removeAllViews()
        moreLayout.addView(
            view,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat())
        )
    }
}