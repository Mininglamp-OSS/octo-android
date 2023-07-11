package com.chat.base.views.keyboard

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.*
import android.widget.PopupWindow
import com.chat.base.config.WKConstants

class KeyboardStatePopupWindow(var context: Context, anchorView: View) : PopupWindow(),
    ViewTreeObserver.OnGlobalLayoutListener {

    init {
        val contentView = View(context)
        setContentView(contentView)
        width = 0
        height = ViewGroup.LayoutParams.MATCH_PARENT
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        inputMethodMode = INPUT_METHOD_NEEDED
        contentView.viewTreeObserver.addOnGlobalLayoutListener(this)

        anchorView.post {
            if (!(context as Activity).isFinishing)
                showAtLocation(
                    anchorView,
                    Gravity.NO_GRAVITY,
                    0,
                    0
                )
        }
    }

    private var maxHeight = 0
    private var isSoftKeyboardOpened = false

    override fun onGlobalLayout() {
        val rect = Rect()
        contentView.getWindowVisibleDisplayFrame(rect)
        if (rect.bottom > maxHeight) {
            maxHeight = rect.bottom
        }
        val screenHeight: Int = DensityUtil.getScreenHeight(context)
        //键盘的高度
        var keyboardHeight = maxHeight - rect.bottom
        if (WKConstants.getKeyboardHeight() != 0 && keyboardHeight >= WKConstants.getKeyboardHeight() * 2) {
            keyboardHeight = WKConstants.getKeyboardHeight()
        }

        val visible = keyboardHeight > screenHeight / 4
        if (!isSoftKeyboardOpened && visible) {
            isSoftKeyboardOpened = true
            onKeyboardStateListener?.onOpened(keyboardHeight)
            KeyboardHelper.keyboardHeight = keyboardHeight
        } else if (isSoftKeyboardOpened && !visible) {
            isSoftKeyboardOpened = false
            onKeyboardStateListener?.onClosed()
        }
    }

    fun release() {
        contentView.viewTreeObserver.removeOnGlobalLayoutListener(this)
    }

    private var onKeyboardStateListener: OnKeyboardStateListener? = null

    fun setOnKeyboardStateListener(listener: OnKeyboardStateListener?) {
        this.onKeyboardStateListener = listener
    }

    interface OnKeyboardStateListener {
        fun onOpened(keyboardHeight: Int)
        fun onClosed()
    }
}