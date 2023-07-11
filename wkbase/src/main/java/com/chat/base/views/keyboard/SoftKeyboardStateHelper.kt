package com.chat.base.views.keyboard

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import com.chat.base.utils.AndroidUtilities
import java.util.*

class SoftKeyboardStateHelper : ViewTreeObserver.OnGlobalLayoutListener {

    interface SoftKeyboardStateListener {
        fun onSoftKeyboardOpened(keyboardHeight: Int)
        fun onSoftKeyboardClosed()
    }

    private val listeners: MutableList<SoftKeyboardStateListener> =
        LinkedList()
    private var activityRootView: View? = null
    private var lastSoftKeyboardHeightInPx = 0
    private var isSoftKeyboardOpened = false

    constructor(activityRootView: View?) : this(activityRootView, false)

    constructor(activityRootView: View?, isSoftKeyboardOpened: Boolean) {
        this.activityRootView = activityRootView
        this.isSoftKeyboardOpened = isSoftKeyboardOpened
        activityRootView?.viewTreeObserver?.addOnGlobalLayoutListener(this)
    }

    private var maxHeight = 0
    override fun onGlobalLayout() {
        val rect = Rect()
        activityRootView!!.getWindowVisibleDisplayFrame(rect)
        if (rect.bottom > maxHeight) {
            maxHeight = rect.bottom
        }
        val screenHeight: Int = AndroidUtilities.getScreenHeight()
        val heightDifference = maxHeight - rect.bottom
        val visible = heightDifference > screenHeight / 4
        if (!isSoftKeyboardOpened && visible) {
            isSoftKeyboardOpened = true
            notifyOnSoftKeyboardOpened(heightDifference)
        } else if (isSoftKeyboardOpened && !visible) {
            isSoftKeyboardOpened = false
            notifyOnSoftKeyboardClosed()
        }
    }


    fun setIsSoftKeyboardOpened(isSoftKeyboardOpened: Boolean) {
        this.isSoftKeyboardOpened = isSoftKeyboardOpened
    }

    fun isSoftKeyboardOpened(): Boolean {
        return isSoftKeyboardOpened
    }

    fun getLastSoftKeyboardHeightInPx(): Int {
        return lastSoftKeyboardHeightInPx
    }

    fun addSoftKeyboardStateListener(listener: SoftKeyboardStateListener) {
        listeners.add(listener)
    }

    fun removeSoftKeyboardStateListener(listener: SoftKeyboardStateListener) {
        listeners.remove(listener)
    }

    private fun notifyOnSoftKeyboardOpened(keyboardHeightInPx: Int) {
        lastSoftKeyboardHeightInPx = keyboardHeightInPx
        for (listener in listeners) {
            listener.onSoftKeyboardOpened(keyboardHeightInPx)
        }
    }

    private fun notifyOnSoftKeyboardClosed() {
        for (listener in listeners) {
            listener.onSoftKeyboardClosed()
        }
    }

    fun release() {
        activityRootView?.viewTreeObserver?.removeOnGlobalLayoutListener(this)
    }
}