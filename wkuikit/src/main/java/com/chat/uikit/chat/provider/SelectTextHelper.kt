package com.chat.uikit.chat.provider

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.*
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.util.Log
import android.util.Pair
import android.view.*
import android.view.View.OnTouchListener
import android.view.ViewTreeObserver.OnScrollChangedListener
import android.widget.Magnifier
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chat.base.emoji.EmojiManager
import com.chat.base.emoji.MoonUtil
import com.chat.base.endpoint.EndpointManager
import com.chat.base.ui.components.AlignImageSpan
import com.chat.base.ui.components.NormalClickableSpan
import com.chat.base.utils.AndroidUtilities
import com.chat.uikit.R
import java.util.*

/**
 * Created by hxg on 21/9/13 929842234@qq.com
 *
 *
 * 仿照的例子：https://github.com/laobie
 * 放大镜 Magnifier：https://developer.android.google.cn/guide/topics/text/magnifier
 */
class SelectTextHelper(builder: Builder) {
    private var mTextView: TextView

    private var mOriginalContent: String // 原本的文本
    private var mStartHandle: CursorHandle? = null // 开始操作标
    private var mEndHandle: CursorHandle? = null // 结束操作标
    private var mOperateWindow: OperateWindow? = null // 操作弹窗
    private var mMagnifier: Magnifier? = null // 放大镜组件
    private val mSelectionInfo = SelectionInfo()
    private var mSelectListener: OnSelectListener? = null
    private val mContext: Context
    private var mSpannable: Spannable? = null
    private var mTouchX = 0
    private var mTouchY = 0
    private var mTextViewMarginStart = 0 // textView的marginStart值
    private val mSelectedColor: Int // 选中文本的颜色
    private val mCursorHandleColor: Int // 游标的颜色
    private val mCursorHandleSize: Int // 游标大小
    private val mSelectAll: Boolean // 全选
    private val mFlame: Int // 是否阅后即焚
    private val mIsShowPinnedMessage: Int // 是否显示置顶消息
    private val mSelectedAllNoPop: Boolean // 已经全选无弹窗
    private val mScrollShow: Boolean // 滑动依然显示弹窗
    private val mMagnifierShow: Boolean // 显示放大镜
    private val mPopSpanCount: Int // 弹窗每行个数
    private val mPopBgResource: Int // 弹窗箭头
    private val mSelectTextLength: Int // 首次选择文字长度
    private val mPopDelay: Int // 弹窗延迟时间
    private val mPopArrowImg: Int // 弹窗箭头
    private val itemTextList: List<Pair<Int, String>> // 操作弹窗item文本
    private var itemListenerList: List<Builder.onSeparateItemClickListener> =
        LinkedList() // 操作弹窗item监听
    private var mSpan: BackgroundColorSpan? = null
    private var isHideWhenScroll = false
    private var isHide = true
    private var usedClickListener = false // 消费了点击事件
    private var mOnPreDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private var mOnScrollChangedListener: OnScrollChangedListener? = null
    private var mRootTouchListener: OnTouchListener? = null
    private var coordinate: FloatArray? = null
    private var fullLayout: View

    init {
        fullLayout = builder.fullLayout
        mTextView = builder.mTextView
        mOriginalContent = mTextView.text.toString()
        mContext = mTextView.context
        mSelectedColor = builder.mSelectedColor
        mCursorHandleColor = builder.mCursorHandleColor
        mSelectAll = builder.mSelectAll
        mFlame = builder.mFlame
        mIsShowPinnedMessage = builder.mIsShowPinnedMessage
        mScrollShow = builder.mScrollShow
        mMagnifierShow = builder.mMagnifierShow
        mPopSpanCount = builder.mPopSpanCount
        mPopBgResource = builder.mPopBgResource
        mSelectTextLength = builder.mSelectTextLength
        mPopDelay = builder.mPopDelay
        mPopArrowImg = builder.mPopArrowImg
        mSelectedAllNoPop = builder.mSelectedAllNoPop
        itemTextList = builder.itemTextList
        itemListenerList = builder.itemListenerList
        mCursorHandleSize = dp2px(builder.mCursorHandleSizeInDp)
        init()
    }

    /**
     * public start
     */
    companion object {
        private const val DEFAULT_SELECTION_LENGTH = 2 // 选2个字节长度 例:表情属于2个字节
        private const val DEFAULT_SHOW_DURATION = 100 // 弹窗100毫秒延迟

        /**
         * public start
         */
        @Volatile
        var emojiMap: MutableMap<String, Int> = HashMap()

        @JvmStatic
        @Synchronized
        fun putAllEmojiMap(map: Map<String, Int>?) {
            emojiMap.putAll(map!!)
        }

        @JvmStatic
        @Synchronized
        fun putEmojiMap(emojiKey: String, @DrawableRes drawableRes: Int) {
            emojiMap[emojiKey] = drawableRes
        }

        /**
         * 文字转化成图片背景
         *
         * @param context       Context
         * @param stringBuilder SpannableStringBuilder text
         * @param content       Target content
         */
        fun replaceText2Emoji(
            context: Context?, stringBuilder: SpannableStringBuilder, content: String
        ) {
//            if (emojiMap.isEmpty()) {
//                return
//            }

            val matcher = EmojiManager.getInstance().getPattern().matcher(content)
            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()
                val emoji = content.substring(start, end)
                val d = MoonUtil.getEmotDrawable(context, emoji, MoonUtil.DEF_SCALE)
                if (d != null) {
                    val span: AlignImageSpan = object : AlignImageSpan(d, ALIGN_CENTER) {
                        override fun onClick(view: View) {}
                    }
//                    val span = SelectImageSpan(d ,Color.TRANSPARENT, DynamicDrawableSpan.ALIGN_CENTER)
                    stringBuilder.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

//            for ((key, drawableRes) in emojiMap) {
//                val matcher = Pattern.compile(key).matcher(content)
//                while (matcher.find()) {
//                    val start = matcher.start()
//                    val end = matcher.end()
//                    val drawable = ContextCompat.getDrawable(context!!, drawableRes)
//                    drawable!!.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
//                    val span = SelectImageSpan(
//                        drawable, Color.TRANSPARENT, DynamicDrawableSpan.ALIGN_CENTER
//                    )
//                    stringBuilder.setSpan(span, start, end, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
//                }
//            }
        }

        // util
        fun getPreciseOffset(textView: TextView, x: Int, y: Int): Int {
            val layout = textView.layout
            return if (layout != null) {
                val topVisibleLine = layout.getLineForVertical(y)
                val offset = layout.getOffsetForHorizontal(topVisibleLine, x.toFloat())
                val offsetX = layout.getPrimaryHorizontal(offset).toInt()
                if (offsetX > x) {
                    layout.getOffsetToLeftOf(offset)
                } else {
                    offset
                }
            } else {
                -1
            }
        }

        fun getHysteresisOffset(textView: TextView, x: Int, y: Int, previousOffset: Int): Int {
            var previousOffsetCopy = previousOffset
            val layout = textView.layout ?: return -1
            var line = layout.getLineForVertical(y)

            // The "HACK BLOCK"S in this function is required because of how Android Layout for
            // TextView works - if 'offset' equals to the last character of a line, then
            //
            // * getLineForOffset(offset) will result the NEXT line
            // * getPrimaryHorizontal(offset) will return 0 because the next insertion point is on the next line
            // * getOffsetForHorizontal(line, x) will not return the last offset of a line no matter where x is
            // These are highly undesired and is worked around with the HACK BLOCK
            //
            // @see Moon+ Reader/Color Note - see how it can't select the last character of a line unless you move
            // the cursor to the beginning of the next line.
            //
            ////////////////////HACK BLOCK////////////////////////////////////////////////////
            if (isEndOfLineOffset(
                    layout, previousOffsetCopy
                )
            ) { // we have to minus one from the offset so that the code below to find
                // the previous line can work correctly.
                val left = layout.getPrimaryHorizontal(previousOffsetCopy - 1).toInt()
                val right = layout.getLineRight(line).toInt()
                val threshold = (right - left) / 2 // half the width of the last character
                if (x > right - threshold) {
                    previousOffsetCopy -= 1
                }
            } ///////////////////////////////////////////////////////////////////////////////////
            val previousLine = layout.getLineForOffset(previousOffsetCopy)
            val previousLineTop = layout.getLineTop(previousLine)
            val previousLineBottom = layout.getLineBottom(previousLine)
            val hysteresisThreshold = (previousLineBottom - previousLineTop) / 2

            // If new line is just before or after previous line and y position is less than
            // hysteresisThreshold away from previous line, keep cursor on previous line.
            if (line == previousLine + 1 && y - previousLineBottom < hysteresisThreshold || line == previousLine - 1 && (previousLineTop - y) < hysteresisThreshold) {
                line = previousLine
            }
            var offset = layout.getOffsetForHorizontal(line, x.toFloat())

            // This allow the user to select the last character of a line without moving the
            // cursor to the next line. (As Layout.getOffsetForHorizontal does not return the
            // offset of the last character of the specified line)
            //
            // But this function will probably get called again immediately, must decrement the offset
            // by 1 to compensate for the change made below. (see previous HACK BLOCK)
            /////////////////////HACK BLOCK///////////////////////////////////////////////////
            if (offset < textView.text.length - 1) {
                if (isEndOfLineOffset(layout, offset + 1)) {
                    val left = layout.getPrimaryHorizontal(offset).toInt()
                    val right = layout.getLineRight(line).toInt()
                    val threshold = (right - left) / 2 // half the width of the last character
                    if (x > right - threshold) {
                        offset += 1
                    }
                }
            } //////////////////////////////////////////////////////////////////////////////////
            return offset
        }

        private fun isEndOfLineOffset(layout: Layout, offset: Int): Boolean {
            return offset > 0 && layout.getLineForOffset(offset) == layout.getLineForOffset(offset - 1) + 1
        }

        @JvmStatic
        val displayWidth: Int
            get() = Resources.getSystem().displayMetrics.widthPixels

        @JvmStatic
        val displayHeight: Int
            get() = Resources.getSystem().displayMetrics.heightPixels

        fun dp2px(dpValue: Float): Int {
            return AndroidUtilities.dp(dpValue)
        }

        /**
         * 设置宽高
         *
         * @param v
         * @param w
         * @param h
         */
        @JvmStatic
        fun setWidthHeight(v: View, w: Int, h: Int) {
            val params = v.layoutParams
            params.width = w
            params.height = h
            v.layoutParams = params
        }

        /**
         * 通知栏的高度
         */
        private var STATUS_HEIGHT = 0

        /**
         * 获取通知栏的高度
         */
        @JvmStatic
        val statusHeight: Int
            get() {
                if (0 != STATUS_HEIGHT) {
                    return STATUS_HEIGHT
                }
                val resid =
                    Resources.getSystem().getIdentifier("status_bar_height", "dimen", "android")
                if (resid > 0) {
                    STATUS_HEIGHT = Resources.getSystem().getDimensionPixelSize(resid)
                    return STATUS_HEIGHT
                }
                return -1
            }

        /**
         * 判断是否为emoji表情符
         *
         * @param c 字符
         * @return 是否为emoji字符
         */
        fun isEmojiText(c: Char): Boolean {
            return !(c.code == 0x0 || c.code == 0x9 || c.code == 0xA || c.code == 0xD || c.code in 0x20..0xD7FF || c.code in 0xE000..0xFFFD || c.code in 0x100000..0x10FFFF)
        }

        /**
         * 利用反射检测文本是否是ImageSpan文本
         */
        private fun isImageSpanText(mSpannable: Spannable): Boolean {
            if (TextUtils.isEmpty(mSpannable)) {
                return false
            }
            var isImageSpan = false
            try {
                val clazz = mSpannable.getSpans(0, mSpannable.length, AlignImageSpan::class.java)
                for (mSpan in clazz) {
                    if (mSpan is AlignImageSpan) {
                        isImageSpan = true
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return isImageSpan
        }

        /**
         * 匹配Image
         *
         * @param content Target content
         */
        fun matchImageSpan(content: String): Boolean {
//            if (emojiMap.isEmpty()) {
//                return false
//            }
//            for ((key) in emojiMap) {
//                val matcher = Pattern.compile(key).matcher(content)
//                if (matcher.find()) {
//                    return true
//                }
//            }
//            return false


            val matcher = EmojiManager.getInstance().getPattern().matcher(content)
            while (matcher.find()) {
                return true
            }
            return false
        }
    }

    interface OnSelectListener {
        fun onClick(v: View?, originalContent: String?) // 点击textView
        fun onLongClick(v: View, local: FloatArray) // 长按textView
        fun onTextSelected(content: String?) // 选中文本回调
        fun onDismiss() // 解除弹窗回调
        fun onClickLink(
            clickableContent: NormalClickableSpan
        ) // 点击文本里的url回调

        fun onSelectAllShowCustomPop(local: FloatArray) // 全选显示自定义弹窗回调
        fun onReset() // 重置回调
        fun onDismissCustomPop() // 解除自定义弹窗回调
        fun onScrolling() // 正在滚动回调
    }

    class Builder(val mTextView: TextView, val fullLayout: View) {
        var mCursorHandleColor = -0xec862a
        var mSelectedColor = -0x501e0c
        var mCursorHandleSizeInDp = 24f
        var mSelectAll = true
        var mFlame = 0
        var mSelectedAllNoPop = false
        var mScrollShow = true
        var mMagnifierShow = true
        var mPopSpanCount = 3
        var mPopBgResource = 0
        var mSelectTextLength = DEFAULT_SELECTION_LENGTH
        var mPopDelay = DEFAULT_SHOW_DURATION
        var mPopArrowImg = 0
        val itemTextList: MutableList<Pair<Int, String>> = LinkedList()
        val itemListenerList: MutableList<onSeparateItemClickListener> = LinkedList()
        var mIsShowPinnedMessage = 0

        /**
         * 选择游标颜色
         */
        fun setCursorHandleColor(@ColorInt cursorHandleColor: Int): Builder {
            mCursorHandleColor = cursorHandleColor
            return this
        }

        /**
         * 选择游标大小
         */
        fun setCursorHandleSizeInDp(cursorHandleSizeInDp: Float): Builder {
            mCursorHandleSizeInDp = cursorHandleSizeInDp
            return this
        }

        /**
         * 选中文本的颜色
         */
        fun setSelectedColor(@ColorInt selectedBgColor: Int): Builder {
            mSelectedColor = selectedBgColor
            return this
        }

        /**
         * 全选
         */
        fun setSelectAll(selectAll: Boolean): Builder {
            mSelectAll = selectAll
            return this
        }

        fun setFlame(flame: Int): Builder {
            mFlame = flame
            return this
        }

        fun setIsShowPinnedMessage(isShowPinnedMessage: Int): Builder {
            mIsShowPinnedMessage = isShowPinnedMessage
            return this
        }

        /**
         * 已经全选无弹窗
         */
        fun setSelectedAllNoPop(selectedAllNoPop: Boolean): Builder {
            mSelectedAllNoPop = selectedAllNoPop
            return this
        }

        /**
         * 滑动依然显示弹窗
         */
        fun setScrollShow(scrollShow: Boolean): Builder {
            mScrollShow = scrollShow
            return this
        }

        /**
         * 显示放大镜
         */
        fun setMagnifierShow(magnifierShow: Boolean): Builder {
            mMagnifierShow = magnifierShow
            return this
        }

        /**
         * 弹窗每行个数
         */
        fun setPopSpanCount(popSpanCount: Int): Builder {
            mPopSpanCount = popSpanCount
            return this
        }

        /**
         * 弹窗背景颜色、弹窗箭头
         */
        fun setPopStyle(popBgResource: Int, popArrowImg: Int): Builder {
            mPopBgResource = popBgResource
            mPopArrowImg = popArrowImg
            return this
        }

        /**
         * 选择选择个数
         */
        fun setSelectTextLength(selectTextLength: Int): Builder {
            mSelectTextLength = selectTextLength
            return this
        }

        /**
         * 弹窗延迟
         */
        fun setPopDelay(popDelay: Int): Builder {
            mPopDelay = popDelay
            return this
        }

        fun addItem(
            @DrawableRes drawableId: Int,
            @StringRes textResId: Int,
            listener: onSeparateItemClickListener
        ): Builder {
            itemTextList.add(Pair(drawableId, mTextView.context.resources.getString(textResId)))
            itemListenerList.add(listener)
            return this
        }

        fun addItem(
            @DrawableRes drawableId: Int, itemText: String, listener: onSeparateItemClickListener
        ): Builder {
            itemTextList.add(Pair(drawableId, itemText))
            itemListenerList.add(listener)
            return this
        }

        fun addItem(@StringRes textResId: Int, listener: onSeparateItemClickListener): Builder {
            itemTextList.add(Pair(0, mTextView.context.resources.getString(textResId)))
            itemListenerList.add(listener)
            return this
        }

        fun addItem(itemText: String, listener: onSeparateItemClickListener): Builder {
            itemTextList.add(Pair(0, itemText))
            itemListenerList.add(listener)
            return this
        }

        fun build(): SelectTextHelper {
            return SelectTextHelper(this)
        }

        interface onSeparateItemClickListener {
            fun onClick()
        }
    }

    /**
     * 重置弹窗
     */
    fun reset() {
        hideSelectView()
        resetSelectionInfo() // 重置弹窗回调
        if (mSelectListener != null) {
            mSelectListener!!.onReset()
        }
    }

    /**
     * 操作弹窗是否显示中
     */
    val isPopShowing: Boolean
        get() = if (null != mOperateWindow) {
            mOperateWindow!!.isShowing
        } else false

    /**
     * 销毁操作弹窗
     */
    fun dismissOperateWindow() {
        if (null != mOperateWindow) {
            mOperateWindow!!.dismiss()
        }
    }

    /**
     * 选择文本监听
     */
    fun setSelectListener(selectListener: OnSelectListener?) {
        mSelectListener = selectListener
    }

    /**
     * 销毁
     */
    fun destroy() {
        mTextView.viewTreeObserver.removeOnScrollChangedListener(mOnScrollChangedListener)
        mTextView.viewTreeObserver.removeOnPreDrawListener(mOnPreDrawListener)
        mTextView.rootView.setOnTouchListener(null)
        reset()
        mStartHandle = null
        mEndHandle = null
        mOperateWindow = null
    }

    /**
     * 全选
     */
    fun selectAll() {
        hideSelectView()
        selectText(0, mTextView.text.length)
        isHide = false

        if (mStartHandle == null) mStartHandle = CursorHandle(true)
        if (mEndHandle == null) mEndHandle = CursorHandle(false)
        showCursorHandle(mStartHandle)
        showCursorHandle(mEndHandle)
        showOperateWindow()
    }

    /**
     * public end
     */

    @SuppressLint("ClickableViewAccessibility")
    private fun init() {
        // 处理空格 把空格转成不间断空格
        // 为什么处理2个，而不是1个呢？
        // 避免英文单词出现断节
//        val newContent = mOriginalContent
//            // 半角空格(英文符号) 转 不间断空格
//            .replace("\u0020\u0020", "\u00A0\u00A0")
//            // 全角空格(中文符号) 转 不间断空格
//            .replace("\u3000\u3000", "\u00A0\u00A0")
        // val spanStr = SpannableStringBuilder(newContent)
        // replaceText2Emoji(mContext, spanStr, newContent)

        // 去除超链接点击背景色
        //  mTextView.highlightColor = Color.TRANSPARENT
        //  mTextView.setText(spanStr, TextView.BufferType.SPANNABLE)
        val fullLayoutLocation = arrayOf(FloatArray(2))
        fullLayout.setOnTouchListener { _, event ->
            if (event!!.action == MotionEvent.ACTION_DOWN) {
                fullLayoutLocation[0] = floatArrayOf(event.rawX, event.rawY)
            }
            false
        }
        fullLayout.setOnClickListener {
            if (usedClickListener) {
                usedClickListener = false
                return@setOnClickListener
            }
            if (null != mSelectListener && (null == mOperateWindow || !mOperateWindow!!.isShowing)) {
                mSelectListener!!.onDismiss()
            }
            reset()
            if (null != mSelectListener) {
                mSelectListener!!.onClick(mTextView, mOriginalContent)
            }
        }
        fullLayout.setOnLongClickListener(object : View.OnLongClickListener {
            override fun onLongClick(p0: View?): Boolean {
                if (mIsShowPinnedMessage == 1) {
                    return true
                }
                coordinate = fullLayoutLocation[0]
                mTouchX = fullLayoutLocation[0][0].toInt()
                mTouchY = fullLayoutLocation[0][1].toInt()
                mTextView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {}
                    override fun onViewDetachedFromWindow(v: View) {
                        destroy()
                    }
                })
                mOnPreDrawListener = ViewTreeObserver.OnPreDrawListener {
                    if (isHideWhenScroll) {
                        isHideWhenScroll = false
                        postShowSelectView(mPopDelay)
                    } // 拿textView的x坐标
                    if (0 == mTextViewMarginStart) {
                        val location = IntArray(2)
                        mTextView.getLocationInWindow(location)
                        mTextViewMarginStart = location[0]
                    }
                    return@OnPreDrawListener true
                }
                mTextView.viewTreeObserver.addOnPreDrawListener(mOnPreDrawListener)
                EndpointManager.getInstance().setMethod("chat_activity_touch") {
                    reset()
                    mTextView.rootView.setOnTouchListener(null)
                    null
                }
                // 根布局监听
                mRootTouchListener = OnTouchListener { _, _ ->
                    reset()
                    mTextView.rootView.setOnTouchListener(null)
                    return@OnTouchListener false
                }
                mTextView.rootView.setOnTouchListener(mRootTouchListener)
                mOnScrollChangedListener = OnScrollChangedListener {
                    if (mScrollShow) {
                        if (!isHideWhenScroll && !isHide) {
                            isHideWhenScroll = true
                            if (mOperateWindow != null) {
                                mOperateWindow!!.dismiss()
                            }
                            if (mStartHandle != null) {
                                mStartHandle!!.dismiss()
                            }
                            if (mEndHandle != null) {
                                mEndHandle!!.dismiss()
                            }
                        }
                        if (null != mSelectListener) {
                            mSelectListener!!.onScrolling()
                        }
                    } else {
                        reset()
                    }
                }
                mTextView.viewTreeObserver.addOnScrollChangedListener(mOnScrollChangedListener)
                if (null == mOperateWindow) {
                    mOperateWindow = OperateWindow(mContext)
                }
                if (mSelectAll) {
                    showAllView()
                } else {
                    showSelectView(mTouchX, mTouchY)
                }
                if (null != mSelectListener) {
                    mSelectListener!!.onLongClick(mTextView, coordinate!!)
                }

                return true
            }
        })

        val textViewLocation = arrayOf(FloatArray(2))
        mTextView.setOnTouchListener { _, event ->
            if (event!!.action == MotionEvent.ACTION_DOWN) {
                textViewLocation[0] = floatArrayOf(event.rawX, event.rawY)
            }
            false
        }
        mTextView.setOnClickListener {
            if (usedClickListener) {
                usedClickListener = false
                return@setOnClickListener
            }
            if (null != mSelectListener && (null == mOperateWindow || !mOperateWindow!!.isShowing)) {
                mSelectListener!!.onDismiss()
            }
            reset()
            if (null != mSelectListener) {
                mSelectListener!!.onClick(mTextView, mOriginalContent)
            }
        }
        mTextView.setOnLongClickListener(object : View.OnLongClickListener {
            override fun onLongClick(p0: View?): Boolean {
                if (mIsShowPinnedMessage == 1) {
                    return true
                }
                coordinate = textViewLocation[0]
                mTouchX = textViewLocation[0][0].toInt()
                mTouchY = textViewLocation[0][1].toInt()
                mTextView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {}
                    override fun onViewDetachedFromWindow(v: View) {
                        destroy()
                    }
                })
                mOnPreDrawListener = ViewTreeObserver.OnPreDrawListener {
                    if (isHideWhenScroll) {
                        isHideWhenScroll = false
                        postShowSelectView(mPopDelay)
                    } // 拿textView的x坐标
                    if (0 == mTextViewMarginStart) {
                        val location = IntArray(2)
                        mTextView.getLocationInWindow(location)
                        mTextViewMarginStart = location[0]
                    }
                    return@OnPreDrawListener true
                }
                mTextView.viewTreeObserver.addOnPreDrawListener(mOnPreDrawListener)
                EndpointManager.getInstance().setMethod("chat_activity_touch") {
                    reset()
                    mTextView.rootView.setOnTouchListener(null)
                    null
                }
                // 根布局监听
                mRootTouchListener = OnTouchListener { _, _ ->
                    reset()
                    mTextView.rootView.setOnTouchListener(null)
                    return@OnTouchListener false
                }
                mTextView.rootView.setOnTouchListener(mRootTouchListener)
                mOnScrollChangedListener = OnScrollChangedListener {
                    if (mScrollShow) {
                        if (!isHideWhenScroll && !isHide) {
                            isHideWhenScroll = true
                            if (mOperateWindow != null) {
                                mOperateWindow!!.dismiss()
                            }
                            if (mStartHandle != null) {
                                mStartHandle!!.dismiss()
                            }
                            if (mEndHandle != null) {
                                mEndHandle!!.dismiss()
                            }
                        }
                        if (null != mSelectListener) {
                            mSelectListener!!.onScrolling()
                        }
                    } else {
                        reset()
                    }
                }
                mTextView.viewTreeObserver.addOnScrollChangedListener(mOnScrollChangedListener)
                if (null == mOperateWindow) {
                    mOperateWindow = OperateWindow(mContext)
                }
                if (mSelectAll) {
                    showAllView()
                } else {
                    showSelectView(mTouchX, mTouchY)
                }
                if (null != mSelectListener) {
                    mSelectListener!!.onLongClick(mTextView, coordinate!!)
                }

                return true
            }
        })

//        mTextView.setOnTouchListener { _: View?, event: MotionEvent ->
//            mTouchX = event.x.toInt()
//            mTouchY = event.y.toInt()
//            false
//        }
//        mTextView.setOnClickListener {
//            if (usedClickListener) {
//                usedClickListener = false
//                return@setOnClickListener
//            }
//            if (null != mSelectListener && (null == mOperateWindow || !mOperateWindow!!.isShowing)) {
//                mSelectListener!!.onDismiss()
//            }
//            reset()
//            if (null != mSelectListener) {
//                mSelectListener!!.onClick(mTextView, mOriginalContent)
//            }
//        }
//        mTextView.setOnLongClickListener {
//            mTextView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
//                override fun onViewAttachedToWindow(v: View) {}
//                override fun onViewDetachedFromWindow(v: View) {
//                    destroy()
//                }
//            })
//            mOnPreDrawListener = ViewTreeObserver.OnPreDrawListener {
//                if (isHideWhenScroll) {
//                    isHideWhenScroll = false
//                    postShowSelectView(mPopDelay)
//                } // 拿textView的x坐标
//                if (0 == mTextViewMarginStart) {
//                    val location = IntArray(2)
//                    mTextView.getLocationInWindow(location)
//                    mTextViewMarginStart = location[0]
//                }
//                return@OnPreDrawListener true
//            }
//            mTextView.viewTreeObserver.addOnPreDrawListener(mOnPreDrawListener)
//
//            // 根布局监听
//            mRootTouchListener = OnTouchListener { _, _ ->
//                reset()
//                mTextView.rootView.setOnTouchListener(null)
//                return@OnTouchListener false
//            }
//            mTextView.rootView.setOnTouchListener(mRootTouchListener)
//            mOnScrollChangedListener = OnScrollChangedListener {
//                if (mScrollShow) {
//                    if (!isHideWhenScroll && !isHide) {
//                        isHideWhenScroll = true
//                        if (mOperateWindow != null) {
//                            mOperateWindow!!.dismiss()
//                        }
//                        if (mStartHandle != null) {
//                            mStartHandle!!.dismiss()
//                        }
//                        if (mEndHandle != null) {
//                            mEndHandle!!.dismiss()
//                        }
//                    }
//                    if (null != mSelectListener) {
//                        mSelectListener!!.onScrolling()
//                    }
//                } else {
//                    reset()
//                }
//            }
//            mTextView.viewTreeObserver.addOnScrollChangedListener(mOnScrollChangedListener)
//            if (null == mOperateWindow) {
//                mOperateWindow = OperateWindow(mContext)
//            }
//            if (mSelectAll) {
//                showAllView()
//            } else {
//                showSelectView(mTouchX, mTouchY)
//            }
//            if (null != mSelectListener) {
//                val fl = floatArrayOf(mTouchX.toFloat(),mTouchY.toFloat())
//                mSelectListener!!.onLongClick(mTextView,fl)
//            }
//            true
//        } // 此setMovementMethod可被修改
        mTextView.movementMethod = LinkMovementMethodInterceptor()
    }

    private fun postShowSelectView(duration: Int) {
        mTextView.removeCallbacks(mShowSelectViewRunnable)
        if (duration <= 0) {
            mShowSelectViewRunnable.run()
        } else {
            mTextView.postDelayed(mShowSelectViewRunnable, duration.toLong())
        }
    }

    private val mShowSelectViewRunnable = Runnable {
        if (isHide) return@Runnable
        if (null != mOperateWindow) {
            showOperateWindow()
        }
        if (mStartHandle != null) {
            showCursorHandle(mStartHandle)
        }
        if (mEndHandle != null) {
            showCursorHandle(mEndHandle)
        }
    }

    private fun hideSelectView() {
        isHide = true
        usedClickListener = false
        if (mStartHandle != null) {
            mStartHandle!!.dismiss()
        }
        if (mEndHandle != null) {
            mEndHandle!!.dismiss()
        }
        if (mOperateWindow != null) {
            mOperateWindow!!.dismiss()
        }
    }

    private fun resetSelectionInfo() {
        resetEmojiBackground()
        mSelectionInfo.mSelectionContent = null
        if (mSpannable != null && mSpan != null) {
            mSpannable!!.removeSpan(mSpan)
            mSpan = null
        }
    }

    /**
     * @param x 长按时的手指的x坐标
     * @param y 长按时的手指的y坐标
     */
    private fun showSelectView(x: Int, y: Int) {
        reset()
        if (mFlame == 1) return
        isHide = false
        if (mStartHandle == null) mStartHandle = CursorHandle(true)
        if (mEndHandle == null) mEndHandle = CursorHandle(false)
        val startOffset = getPreciseOffset(mTextView, x, y)
        var endOffset = startOffset + mSelectTextLength
        if (mTextView.text is Spannable) {
            mSpannable = mTextView.text as Spannable
        }
        if (mSpannable == null || endOffset - 1 >= mTextView.text.length) {
            endOffset = mTextView.text.length
        }
        endOffset = changeEndOffset(startOffset, endOffset)
        selectText(startOffset, endOffset)
        showCursorHandle(mStartHandle)
        showCursorHandle(mEndHandle)
        showOperateWindow()
    }

    /**
     * 处理endOffset位置
     * ImageSpan文本，则会加够ImageSpan匹配的字符
     * Emoji文本，则去除最后的文字emoji字符
     *
     * @param startOffset 开始文字坐标
     * @param endOffset   结束文字坐标
     * @return endOffset
     */
    private fun changeEndOffset(startOffset: Int, endOffset: Int): Int {
        var endOffsetCopy = endOffset
        var selectText =
            mSpannable!!.subSequence(startOffset, endOffsetCopy) as Spannable // 是否ImageSpan文本
        if (isImageSpanText(selectText)) { // 是否匹配Image
            while (!matchImageSpan(selectText.toString())) {
                endOffsetCopy++
                selectText = mSpannable!!.subSequence(startOffset, endOffsetCopy) as Spannable
            }
        } // 选中的文字倒数第二个是文字 且 倒数第一个字符是文字emoji
        // 则去除最后的文字emoji字符
        val selectTextString = selectText.toString()
        if (selectTextString.length > 1) {
            if (!isEmojiText(selectTextString[selectTextString.length - 2]) && isEmojiText(
                    selectTextString[selectTextString.length - 1]
                )
            ) {
                endOffsetCopy--
            }
        }
        return endOffsetCopy
    }

    /**
     * 显示操作弹窗
     * 可能多次调用
     */
    private fun showOperateWindow() {

        if (null == mOperateWindow) {
            mOperateWindow = OperateWindow(mContext)
        } // 开启已经全选无弹窗
        if (mSelectedAllNoPop && mSelectionInfo.mSelectionContent == mTextView.text.toString()) {
            mOperateWindow!!.dismiss()
            if (mSelectListener != null) {
                mSelectListener!!.onSelectAllShowCustomPop(coordinate!!)
            }
        } else {
            if (mFlame == 1) return
            mOperateWindow!!.show()
        }
    }

    /**
     * 全选
     * Select all
     */
    private fun showAllView() {
        reset()
        isHide = false
        if (mStartHandle == null) mStartHandle = CursorHandle(true)
        if (mEndHandle == null) mEndHandle = CursorHandle(false)
        if (mTextView.text is Spannable) {
            mSpannable = mTextView.text as Spannable
        }
        if (mSpannable == null) {
            return
        }
        selectText(0, mTextView.text.length)
        showCursorHandle(mStartHandle)
        showCursorHandle(mEndHandle)
        showOperateWindow()
    }

    private fun showCursorHandle(cursorHandle: CursorHandle?) {
        val layout = mTextView.layout
        val offset = if (cursorHandle!!.isLeft) mSelectionInfo.mStart else mSelectionInfo.mEnd
        var x = layout.getPrimaryHorizontal(offset).toInt()
        var y = layout.getLineBottom(layout.getLineForOffset(offset))

        // 右游标
        // mSelectionInfo.mEnd != 0 不是第一位
        // x == 0 右游标在最后面
        // 把右游标水平坐标定位在减去一个字的线条右侧
        // 把右游标底部线坐标定位在上一行
        if (!cursorHandle.isLeft && mSelectionInfo.mEnd != 0 && x == 0) {
            x = layout.getLineRight(layout.getLineForOffset(mSelectionInfo.mEnd - 1)).toInt()
            y = layout.getLineBottom(layout.getLineForOffset(mSelectionInfo.mEnd - 1))
        }
        cursorHandle.show(x, y)
    }

    private fun selectText(startPos: Int, endPos: Int) {
        if (startPos != -1) {
            mSelectionInfo.mStart = startPos
        }
        if (endPos != -1) {
            mSelectionInfo.mEnd = endPos
        }
        if (mSelectionInfo.mStart > mSelectionInfo.mEnd) {
            val temp = mSelectionInfo.mStart
            mSelectionInfo.mStart = mSelectionInfo.mEnd
            mSelectionInfo.mEnd = temp
        }
        if (mSpannable != null) {
            if (mSpan == null) {
                mSpan = BackgroundColorSpan(mSelectedColor)
            }
            mSelectionInfo.mSelectionContent =
                mSpannable!!.subSequence(mSelectionInfo.mStart, mSelectionInfo.mEnd).toString()
            mSpannable!!.setSpan(
                mSpan, mSelectionInfo.mStart, mSelectionInfo.mEnd, Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            )
            if (mSelectListener != null) {
                mSelectListener!!.onTextSelected(mSelectionInfo.mSelectionContent)
            }

            // 设置图片表情选中背景
            setEmojiBackground()
        }
    }

    /**
     * 设置图片表情选中背景
     */
    private fun setEmojiBackground() {
//        if (emojiMap.isEmpty()) {
//            return
//        }
        // 前部 透明背景
        setEmojiBackground(
            mSpannable!!.subSequence(0, mSelectionInfo.mStart) as Spannable, Color.TRANSPARENT
        ) // 中间 选择背景
        setEmojiBackground(
            mSpannable!!.subSequence(mSelectionInfo.mStart, mSelectionInfo.mEnd) as Spannable,
            mSelectedColor
        ) // 尾部 透明背景
        setEmojiBackground(
            mSpannable!!.subSequence(mSelectionInfo.mEnd, mSpannable!!.length) as Spannable,
            Color.TRANSPARENT
        )
    }

    /**
     * 利用反射改变图片背景颜色
     *
     * @param mSpannable Spannable
     * @param bgColor    background
     */
    private fun setEmojiBackground(mSpannable: Spannable, @ColorInt bgColor: Int) {
        if (TextUtils.isEmpty(mSpannable)) {
            return
        }
        val clazz = mSpannable.getSpans(0, mSpannable.length, AlignImageSpan::class.java)
        for (mSpan in clazz) {
            if (mSpan is AlignImageSpan) {
                val imageSpan = mSpan
                if (imageSpan.bgColor != bgColor) {
                    imageSpan.bgColor = bgColor
                }
            }
        }
    }

    /**
     * 重置emoji选择背景
     */
    private fun resetEmojiBackground() {
        mSpannable?.let { setEmojiBackground(it, Color.TRANSPARENT) }
    }

    /**
     * 操作弹窗
     * 提供全选时可另外配置自定义弹窗
     * 自定义功能：复制、全选、等等
     * Custom function:Copy, Select all, And so on.
     */
    @SuppressLint("InflateParams")
    private inner class OperateWindow(context: Context?) {
        private val mWindow: PopupWindow?
        private val mTempCoors = IntArray(2)
        private val mWidth: Int
        private val mHeight: Int
        private val listAdapter: SelectTextPopAdapter
        private val rvContent: RecyclerView?

        init {
            val contentView = LayoutInflater.from(context).inflate(R.layout.pop_operate, null)
            rvContent = contentView.findViewById(R.id.rv_content)
            if (0 != mPopBgResource) {
                rvContent.setBackgroundResource(mPopBgResource)
            }
            val backgroundDrawable: Drawable? =
                ResourcesCompat.getDrawable(context!!.resources, R.mipmap.popup_fixed_alert, null)
            backgroundDrawable!!.colorFilter =
                PorterDuffColorFilter(
                    ContextCompat.getColor(context, R.color.screen_bg),
                    PorterDuff.Mode.MULTIPLY
                )

            rvContent.background = backgroundDrawable

            //            if (0 != mPopArrowImg) {
//                ivArrow.setBackgroundResource(mPopArrowImg)
//            }
            val size = itemTextList.size
            // 宽 个数超过mPopSpanCount 取 mPopSpanCount
            mWidth = dp2px((52 * size).toFloat())
            // 行数
            val row =
                (size / mPopSpanCount + if (size % mPopSpanCount == 0) 0 else 1) // 有余数 加一行 // 高
            mHeight = dp2px((12 * (1 + row) + 52 * row + 5).toFloat())
            mWindow = PopupWindow(
                contentView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                false
            )
            mWindow.isClippingEnabled = false
            listAdapter = SelectTextPopAdapter(context!!, itemTextList)
            listAdapter.setItemWrapContent(true)
            listAdapter.setOnclickItemListener(object : SelectTextPopAdapter.onClickItemListener {
                override fun onClick(position: Int) {
                    dismiss()
                    itemListenerList[position].onClick()
                }
            })
            if (rvContent != null) {
                rvContent.adapter = listAdapter
            }
        }

        fun show() {
            val deviceWidth = displayWidth
//            val size = itemTextList.size
//            if (size > mPopSpanCount) {
//                rvContent!!.layoutManager =
//                    GridLayoutManager(mContext, mPopSpanCount, GridLayoutManager.VERTICAL, false)
//            } else {
//                rvContent!!.layoutManager =
//                    GridLayoutManager(mContext, 5, GridLayoutManager.VERTICAL, false)
//            }

            rvContent!!.layoutManager =
                LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false)
            mTextView.getLocationInWindow(mTempCoors)
            val layout = mTextView.layout
            var posX: Int
            var posXTemp = 0
            val startX = layout.getPrimaryHorizontal(mSelectionInfo.mStart).toInt() + mTempCoors[0]
            val startY = layout.getLineTop(layout.getLineForOffset(mSelectionInfo.mStart))
            val endY = layout.getLineTop(layout.getLineForOffset(mSelectionInfo.mEnd))
            var posY = startY + mTempCoors[1] - mHeight
            if (posY < 0) posY = 0

            // 在同一行
            posX = if (startY == endY) {
                val endX = layout.getPrimaryHorizontal(mSelectionInfo.mEnd)
                    .toInt() + mTempCoors[0] // posX = (起始点 + 终点) / 2 - (向左移动 mWidth / 2)
                (startX + endX) / 2 - mWidth / 2
            } else { // posX = (起始点 + (文本左边距  + 文本宽度                - 文本右padding)) / 2         - (向左移动 mWidth / 2)
                (startX + (mTempCoors[0] + mTextView.width - mTextView.paddingRight)) / 2 - mWidth / 2
            }
            if (posX <= 0) {
                posXTemp = posX
                posX = 0
            } else if (posX + mWidth > deviceWidth) {
                posXTemp = posX
                posX = deviceWidth - mWidth
            }

            val size = itemTextList.size
            // 宽 个数超过mPopSpanCount 取 mPopSpanCount
            val mWidth = dp2px((52 * size).toFloat())
            Log.e("开始位置", "$posX")
            if (posX > mWidth) {
                posX = mWidth - AndroidUtilities.dp(80f)
            }
            Log.e("开始坐标", "$posX")
            mWindow!!.showAtLocation(mTextView, Gravity.NO_GRAVITY, posX, posY) // view中心位置
            var arrowTranslationX: Int // 在中间
            arrowTranslationX = when {
                posXTemp == 0 -> { // - dp2px(mContext, 16) 是 的margin
                    mWidth / 2 - dp2px(16f)
                }

                posXTemp < 0 -> {
                    posXTemp + mWidth / 2
                }

                else -> { // arrowTranslationX = 两坐标中心点   - 弹窗左侧点 - iv_arrow的margin
                    posXTemp + mWidth / 2 - posX - dp2px(16f)
                }
            }
            if (arrowTranslationX < dp2px(4f)) {
                arrowTranslationX = dp2px(4f)
            } else if (arrowTranslationX > mWidth - dp2px(4f)) {
                arrowTranslationX = mWidth - dp2px(4f)
            }
//            ivArrow.translationX = arrowTranslationX.toFloat()
        }

        fun dismiss() {
            mWindow!!.dismiss()
            if (null != mSelectListener) {
                mSelectListener!!.onDismissCustomPop()
            }
        }

        val isShowing: Boolean
            get() = mWindow?.isShowing ?: false
    }

    /**
     * 游标
     */
    private inner class CursorHandle(var isLeft: Boolean) : View(mContext) {
        private var mPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val mPopupWindow: PopupWindow
        private val mCircleRadius = mCursorHandleSize / 2
        private val mWidth = mCursorHandleSize
        private val mHeight = mCursorHandleSize
        private val mPadding = 32 // 游标padding

        init {
            mPaint.color = mCursorHandleColor
            mPopupWindow = PopupWindow(this)
            mPopupWindow.isClippingEnabled = false
            mPopupWindow.width = mWidth + mPadding * 2
            mPopupWindow.height = mHeight + mPadding / 2
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawCircle(
                (mCircleRadius + mPadding).toFloat(),
                mCircleRadius.toFloat(),
                mCircleRadius.toFloat(),
                mPaint
            )
            if (isLeft) {
                canvas.drawRect(
                    (mCircleRadius + mPadding).toFloat(),
                    0f,
                    (mCircleRadius * 2 + mPadding).toFloat(),
                    mCircleRadius.toFloat(),
                    mPaint
                )
            } else {
                canvas.drawRect(
                    mPadding.toFloat(),
                    0f,
                    (mCircleRadius + mPadding).toFloat(),
                    mCircleRadius.toFloat(),
                    mPaint
                )
            }
        }

        private var mAdjustX = 0
        private var mAdjustY = 0
        private var mBeforeDragStart = 0
        private var mBeforeDragEnd = 0

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mBeforeDragStart = mSelectionInfo.mStart
                    mBeforeDragEnd = mSelectionInfo.mEnd
                    mAdjustX = event.x.toInt()
                    mAdjustY = event.y.toInt()
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    showOperateWindow()
                    if (mMagnifierShow) { // android 9 放大镜
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && null != mMagnifier) {
                            mMagnifier!!.dismiss()
                        }
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    mOperateWindow!!.dismiss()
                    if (null != mSelectListener) {
                        mSelectListener!!.onDismissCustomPop()
                    }
                    val rawX = event.rawX.toInt()
                    val rawY = event.rawY.toInt() // x y不准 x 减去textView距离x轴距离值  y减去字体大小的像素值
                    update(
                        rawX + mAdjustX - mWidth - mTextViewMarginStart,
                        rawY + mAdjustY - mHeight - mTextView.textSize.toInt()
                    )
                    if (mMagnifierShow) { // android 9 放大镜功能
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            if (null == mMagnifier) {
                                mMagnifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    Magnifier.Builder(mTextView).build()
                                } else {
                                    Magnifier(mTextView)
                                }
                            }
                            val viewPosition = IntArray(2)
                            mTextView.getLocationOnScreen(viewPosition)
                            val magnifierX = rawX - viewPosition[0]
                            val magnifierY = rawY - viewPosition[1] - dp2px(32f)
                            mMagnifier!!.show(
                                magnifierX.toFloat(), magnifierY.coerceAtLeast(0).toFloat()
                            )
                        }
                    }
                }
            }
            return true
        }

        private fun changeDirection() {
            isLeft = !isLeft
            invalidate()
        }

        fun dismiss() {
            mPopupWindow.dismiss()
        }

        private val mTempCoors = IntArray(2)
        fun update(x: Int, y: Int) {
            var yCopy = y
            mTextView.getLocationInWindow(mTempCoors)
            val oldOffset = if (isLeft) {
                mSelectionInfo.mStart
            } else {
                mSelectionInfo.mEnd
            }
            yCopy -= mTempCoors[1]
            val offset = getHysteresisOffset(mTextView, x, yCopy, oldOffset)
            if (offset != oldOffset) {
                resetSelectionInfo()
                if (isLeft) {
                    if (offset > mBeforeDragEnd) {
                        val handle = getCursorHandle(false)
                        changeDirection()
                        handle!!.changeDirection()
                        mBeforeDragStart = mBeforeDragEnd
                        selectText(mBeforeDragEnd, offset)
                        handle.updateCursorHandle()
                    } else {
                        selectText(offset, -1)
                    }
                    updateCursorHandle()
                } else {
                    if (offset < mBeforeDragStart) {
                        val handle = getCursorHandle(true)
                        handle!!.changeDirection()
                        changeDirection()
                        mBeforeDragEnd = mBeforeDragStart
                        selectText(offset, mBeforeDragStart)
                        handle.updateCursorHandle()
                    } else {
                        selectText(mBeforeDragStart, offset)
                    }
                    updateCursorHandle()
                }
            }
        }

        private fun updateCursorHandle() {
            mTextView.getLocationInWindow(mTempCoors)
            val layout = mTextView.layout
            if (isLeft) {
                mPopupWindow.update(
                    layout.getPrimaryHorizontal(mSelectionInfo.mStart).toInt() - mWidth + extraX,
                    layout.getLineBottom(layout.getLineForOffset(mSelectionInfo.mStart)) + extraY,
                    -1,
                    -1
                )
            } else {
                var horizontalEnd = layout.getPrimaryHorizontal(mSelectionInfo.mEnd).toInt()
                var lineBottomEnd =
                    layout.getLineBottom(layout.getLineForOffset(mSelectionInfo.mEnd)) // 右游标 // mSelectionInfo.mEnd != 0 不是第一位
                // horizontalEnd == 0 右游标在最后面
                // 把右游标水平坐标定位在减去一个字的线条右侧
                // 把右游标底部线坐标定位在上一行
                if (mSelectionInfo.mEnd != 0 && horizontalEnd == 0) {
                    horizontalEnd =
                        layout.getLineRight(layout.getLineForOffset(mSelectionInfo.mEnd - 1))
                            .toInt()
                    lineBottomEnd =
                        layout.getLineBottom(layout.getLineForOffset(mSelectionInfo.mEnd - 1))
                }
                mPopupWindow.update(horizontalEnd + extraX, lineBottomEnd + extraY, -1, -1)
            }
        }

        fun show(x: Int, y: Int) {
            mTextView.getLocationInWindow(mTempCoors)
            val offset = if (isLeft) mWidth else 0
            mPopupWindow.showAtLocation(
                mTextView, Gravity.NO_GRAVITY, x - offset + extraX, y + extraY
            )
        }

        val extraX: Int
            get() = mTempCoors[0] - mPadding + mTextView.paddingLeft
        val extraY: Int
            get() = mTempCoors[1] + mTextView.paddingTop
    }

    private fun getCursorHandle(isLeft: Boolean): CursorHandle? {
        return if (mStartHandle!!.isLeft == isLeft) {
            mStartHandle
        } else {
            mEndHandle
        }
    }

    private inner class SelectionInfo {
        var mStart = 0
        var mEnd = 0
        var mSelectionContent: String? = null
    }

    /**
     * 处理内容链接跳转
     */
    private inner class LinkMovementMethodInterceptor : LinkMovementMethod() {
        private var downLinkTime: Long = 0
        override fun onTouchEvent(
            widget: TextView, buffer: Spannable, event: MotionEvent
        ): Boolean {
            val action = event.action
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
                var x = event.x.toInt()
                var y = event.y.toInt()
                x -= widget.totalPaddingLeft
                y -= widget.totalPaddingTop
                x += widget.scrollX
                y += widget.scrollY
                val layout = widget.layout
                val line = layout.getLineForVertical(y)
                val off = layout.getOffsetForHorizontal(line, x.toFloat())
                val links = buffer.getSpans(off, off, ClickableSpan::class.java)
                if (links.isNotEmpty()) {
                    if (action == MotionEvent.ACTION_UP) { // 长按
                        if (downLinkTime + ViewConfiguration.getLongPressTimeout() < System.currentTimeMillis()) {
                            return false
                        } // 点击
                        if (links[0] is NormalClickableSpan) {
                            val url = links[0] as NormalClickableSpan
                            if (!TextUtils.isEmpty(url.toString())) {

                                if (null != mSelectListener) {
                                    usedClickListener = true
                                    mSelectListener!!.onClickLink(url)
                                }
                                return true
                            } else {
                                links[0].onClick(widget)
                            }
                        }
                    } else {
                        downLinkTime = System.currentTimeMillis()
                        Selection.setSelection(
                            buffer, buffer.getSpanStart(links[0]), buffer.getSpanEnd(links[0])
                        )
                    }
                    return true
                } else {
                    Selection.removeSelection(buffer)
                }
            }
            return super.onTouchEvent(widget, buffer, event)
        }
    }

}