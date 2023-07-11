package com.chat.base.views.keyboard

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * @name
 * @author      FreddyChen
 * @date        2020/06/07 01:16
 * @email       chenshichao@outlook.com
 * @github      https://github.com/FreddyChen
 * @describe
 */
object UIUtil {

    /**
     * 使控件获取焦点
     *
     * @param view
     */
    fun requestFocus(view: View?) {
        if (view != null) {
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.requestFocus()
        }
    }

    /**
     * 使控件失去焦点
     *
     * @param view
     */
    fun loseFocus(view: View?) {
        if (view != null) {
            val parent = view.parent as ViewGroup
            parent.isFocusable = true
            parent.isFocusableInTouchMode = true
            parent.requestFocus()
        }
    }

    /**
     * 是否应该隐藏键盘
     *
     * @param v
     * @param event
     * @return
     */
    fun isShouldHideInput(v: View?, event: MotionEvent): Boolean {
        if (v != null && v is EditText) {
            val leftTop = intArrayOf(0, 0)
            //获取输入框当前的location位置
            v.getLocationInWindow(leftTop)
            val left = leftTop[0]
            val top = leftTop[1]
            val bottom = top + v.getHeight()
            val right = left + v.getWidth()
            return !(event.x > left && event.x < right && event.y > top && event.y < bottom)
        }
        return false
    }

    /**
     * 隐藏键盘
     *
     * @param context
     * @param v       输入框
     */
    fun hideSoftInput(context: Context, v: View) {
        val imm = context
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(v.applicationWindowToken, 0)
    }

    fun showSoftInput(context: Context, v: View?) {
        val imm =
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(v, 0)
    }
}