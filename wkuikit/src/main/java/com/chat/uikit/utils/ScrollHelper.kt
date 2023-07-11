package com.chat.uikit.utils

import android.view.View
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.chat.base.utils.WKTimeUtils

class ScrollHelper {
    companion object {
        /**滚动类别: 默认不特殊处理. 滚动到item显示了就完事*/
        const val SCROLL_TYPE_NORMAL = 0

        /**滚动类别: 将item滚动到第一个位置*/
        const val SCROLL_TYPE_TOP = 1

        /**滚动类别: 将item滚动到最后一个位置*/
        const val SCROLL_TYPE_BOTTOM = 2

        /**滚动类别: 将item滚动到居中位置*/
        const val SCROLL_TYPE_CENTER = 3
    }

    internal var recyclerView: RecyclerView? = null

    /**触发滚动是否伴随了adapter的addItem*/
    var isFromAddItem = false

    /**滚动是否需要动画*/
    var isScrollAnim = false

    /**滚动类别*/
    var scrollType = SCROLL_TYPE_NORMAL

    /**额外的偏移距离*/
    var scrollOffset: Int = 0

    init {
        resetValue()
    }

    fun attach(recyclerView: RecyclerView) {
        if (this.recyclerView == recyclerView) {
            return
        }
        detach()
        this.recyclerView = recyclerView
    }

    fun detach() {
        recyclerView = null
    }

    fun resetValue() {
        isFromAddItem = false
        isScrollAnim = false
        scrollOffset = 0
        scrollType = SCROLL_TYPE_NORMAL
    }

    fun lastItemPosition(): Int {
        return (recyclerView?.layoutManager?.itemCount ?: 0) - 1
    }

    fun scrollToLast(scrollParams: ScrollParams = _defaultScrollParams()) {
        startScroll(lastItemPosition(), scrollParams)
    }

    fun _defaultScrollParams(): ScrollParams {
        return ScrollParams(-1, scrollType, isScrollAnim, scrollOffset, isFromAddItem)
    }

    fun startScroll(scrollParams: ScrollParams = _defaultScrollParams()) {
        startScroll(scrollParams.scrollPosition, scrollParams)
    }

    fun scroll(position: Int) {
        startScroll(position, _defaultScrollParams())
    }

    fun startScroll(position: Int, scrollParams: ScrollParams = _defaultScrollParams()) {
        if (check(position)) {
            scrollParams.scrollPosition = position

            recyclerView?.stopScroll()

            if (isPositionVisible(position)) {
                scrollWithVisible(scrollParams)
            } else {
                if (scrollParams.scrollAnim) {
                    if (scrollParams.isFromAddItem) {
                        if (recyclerView?.itemAnimator is SimpleItemAnimator) {
                            //itemAnimator 自带动画
                            recyclerView?.scrollToPosition(position)
                        } else {
                            recyclerView?.smoothScrollToPosition(position)
                        }
                    } else {
                        recyclerView?.smoothScrollToPosition(position)
                    }


                } else {
                    if (scrollParams.isFromAddItem) {
                        val itemAnimator = recyclerView?.itemAnimator
                        if (itemAnimator != null) {
                            //有默认的动画
                            recyclerView?.itemAnimator = null
                            OnNoAnimScrollIdleListener(itemAnimator).attach(recyclerView!!)
                        }
                    }
                    recyclerView?.scrollToPosition(position)
                }
                if (scrollParams.scrollType != SCROLL_TYPE_NORMAL) {
                    //不可见时, 需要现滚动到可见位置, 再进行微调
                    OnScrollIdleListener(scrollParams).attach(recyclerView!!)
                }
            }
            resetValue()
        }
    }

    private var lockLayoutListener: LockLayoutListener? = null

    /**短时间之内, 锁定滚动到0的位置*/
    fun scrollToFirst(config: LockDrawListener.() -> Unit = {}) {
        lockPositionByDraw {
            lockPosition = 0
            firstScrollAnim = true
            scrollAnim = true
            force = true
            firstForce = true
            lockDuration = 60
            autoDetach = true
            config()
        }
    }

    /**
     * 当界面有变化时, 自动滚动到最后一个位置
     * [unlockPosition]
     * */
    fun lockPosition(config: LockLayoutListener.() -> Unit = {}) {
        if (lockLayoutListener == null && recyclerView != null) {
            lockLayoutListener = LockLayoutListener().apply {
                scrollAnim = isScrollAnim
                config()
                attach(recyclerView!!)
            }
        }
    }

    fun lockPositionByDraw(config: LockDrawListener.() -> Unit = {}) {
        recyclerView?.let {
            LockDrawListener().apply {
                config()
                attach(it)
            }
        }
    }

    fun lockPositionByLayout(config: LockLayoutListener.() -> Unit = {}) {
        recyclerView?.let {
            LockLayoutListener().apply {
                config()
                attach(it)
            }
        }
    }

    fun unlockPosition() {
        lockLayoutListener?.detach()
        lockLayoutListener = null
    }

    /**当需要滚动的目标位置已经在屏幕上可见*/
    internal fun scrollWithVisible(scrollParams: ScrollParams) {
        when (scrollType) {
            SCROLL_TYPE_NORMAL -> {
                //nothing
            }
            SCROLL_TYPE_TOP -> {
                viewByPosition(scrollParams.scrollPosition)?.also { child ->
                    recyclerView?.apply {
                        val dx = layoutManager!!.getDecoratedLeft(child) -
                                paddingLeft - scrollParams.scrollOffset

                        val dy = layoutManager!!.getDecoratedTop(child) -
                                paddingTop - scrollParams.scrollOffset

                        if (scrollParams.scrollAnim) {
                            smoothScrollBy(dx, dy)
                        } else {
                            scrollBy(dx, dy)
                        }
                    }
                }
            }
            SCROLL_TYPE_BOTTOM -> {
                viewByPosition(scrollParams.scrollPosition)?.also { child ->
                    recyclerView?.apply {
                        val dx =
                            layoutManager!!.getDecoratedRight(child) -
                                    measuredWidth + paddingRight + scrollParams.scrollOffset
                        val dy =
                            layoutManager!!.getDecoratedBottom(child) -
                                    measuredHeight + paddingBottom + scrollParams.scrollOffset

                        if (scrollParams.scrollAnim) {
                            smoothScrollBy(dx, dy)
                        } else {
                            scrollBy(dx, dy)
                        }
                    }
                }
            }
            SCROLL_TYPE_CENTER -> {
                viewByPosition(scrollParams.scrollPosition)?.also { child ->

                    recyclerView?.apply {
                        val recyclerCenterX =
                            (measuredWidth - paddingLeft - paddingRight) / 2 + paddingLeft

                        val recyclerCenterY =
                            (measuredHeight - paddingTop - paddingBottom) / 2 + paddingTop

                        val dx = layoutManager!!.getDecoratedLeft(child) - recyclerCenterX +
                                layoutManager!!.getDecoratedMeasuredWidth(child) / 2 + scrollParams.scrollOffset

                        val dy = layoutManager!!.getDecoratedTop(child) - recyclerCenterY +
                                layoutManager!!.getDecoratedMeasuredHeight(child) / 2 + scrollParams.scrollOffset

                        if (scrollParams.scrollAnim) {
                            smoothScrollBy(dx, dy)
                        } else {
                            scrollBy(dx, dy)
                        }
                    }
                }
            }
        }
    }

    /**位置是否可见*/
    private fun isPositionVisible(position: Int): Boolean {
        return recyclerView?.layoutManager.isPositionVisible(position)
    }

    private fun viewByPosition(position: Int): View? {
        return recyclerView?.layoutManager?.findViewByPosition(position)
    }

    private fun check(position: Int): Boolean {
        if (recyclerView == null) {
            return false
        }

        if (recyclerView?.adapter == null) {
            return false
        }

        if (recyclerView?.layoutManager == null) {
            return false
        }

        val itemCount = recyclerView?.layoutManager?.itemCount ?: 0
        if (position < 0 || position >= itemCount) {
            return false
        }

        return true
    }

    fun log(recyclerView: RecyclerView? = this.recyclerView) {
        recyclerView?.viewTreeObserver?.apply {
            this.addOnDrawListener {
            }
            this.addOnGlobalFocusChangeListener { _, _ ->
            }
            this.addOnGlobalLayoutListener {
            }
            //此方法回调很频繁
            this.addOnPreDrawListener {
                //L.v("this....")
                true
            }
            this.addOnScrollChangedListener {
            }
            this.addOnTouchModeChangeListener {
            }
            this.addOnWindowFocusChangeListener {
            }
        }
    }

    private inner abstract class OnScrollListener : ViewTreeObserver.OnScrollChangedListener,
        IAttachListener {
        var attachView: View? = null

        override fun attach(view: View) {
            detach()
            attachView = view
            view.viewTreeObserver.addOnScrollChangedListener(this)
        }

        override fun detach() {
            attachView?.viewTreeObserver?.removeOnScrollChangedListener(this)
        }

        override fun onScrollChanged() {
            onScrollChanged(recyclerView?.scrollState ?: RecyclerView.SCROLL_STATE_IDLE)
            detach()
        }

        abstract fun onScrollChanged(state: Int)
    }

    /**滚动结束之后, 根据类别, 继续滚动.*/
    private inner class OnScrollIdleListener(val scrollParams: ScrollParams) :
        OnScrollListener() {

        override fun onScrollChanged(state: Int) {
            if (state == RecyclerView.SCROLL_STATE_IDLE) {
                scrollWithVisible(scrollParams)
            }
        }
    }

    /**临时去掉动画滚动, 之后恢复动画*/
    private inner class OnNoAnimScrollIdleListener(val itemAnimator: RecyclerView.ItemAnimator?) :
        OnScrollListener() {

        override fun onScrollChanged(state: Int) {
            if (state == RecyclerView.SCROLL_STATE_IDLE) {
                recyclerView?.itemAnimator = itemAnimator
            }
        }
    }

    inner abstract class LockScrollListener : ViewTreeObserver.OnGlobalLayoutListener,
        ViewTreeObserver.OnDrawListener,
        IAttachListener, Runnable {

        /**激活滚动动画*/
        var scrollAnim: Boolean = true

        /**激活第一个滚动的动画*/
        var firstScrollAnim: Boolean = false

        /**不检查界面 情况, 强制滚动到最后的位置. 关闭后. 会智能判断*/
        var force: Boolean = false

        /**第一次时, 是否强制滚动*/
        var firstForce: Boolean = true

        /**滚动阈值, 倒数第几个可见时, 就允许滚动*/
        var scrollThreshold = 2

        /**锁定需要滚动的position, -1就是最后一个*/
        var lockPosition = RecyclerView.NO_POSITION

        /**是否激活功能*/
        var enableLock = true

        /**滚动到目标后, 自动调用[detach]*/
        var autoDetach = false

        /**锁定时长, 毫秒*/
        var lockDuration: Long = -1

        //记录开始的统计时间
        var _lockStartTime = 0L

        override fun run() {
            if (!enableLock || recyclerView?.layoutManager?.itemCount ?: 0 <= 0) {
                return
            }

            isScrollAnim = if (firstForce) firstScrollAnim else scrollAnim
            scrollType = SCROLL_TYPE_BOTTOM

            val position =
                if (lockPosition == RecyclerView.NO_POSITION) lastItemPosition() else lockPosition

            if (force || firstForce) {
                scroll(position)
                onScrollTrigger()
            } else {
                val lastItemPosition = lastItemPosition()
                if (lastItemPosition != RecyclerView.NO_POSITION) {
                    //智能判断是否可以锁定
                    if (position == 0) {
                        //滚动到顶部
                        val findFirstVisibleItemPosition =
                            recyclerView?.layoutManager.findFirstVisibleItemPosition()

                        if (findFirstVisibleItemPosition <= scrollThreshold) {
                            scroll(position)
                            onScrollTrigger()
                        }
                    } else {
                        val findLastVisibleItemPosition =
                            recyclerView?.layoutManager.findLastVisibleItemPosition()

                        if (lastItemPosition - findLastVisibleItemPosition <= scrollThreshold) {
                            //最后第一个或者最后第2个可见, 智能判断为可以滚动到尾部
                            scroll(position)
                            onScrollTrigger()
                        }
                    }
                }
            }

            firstForce = false
        }

        var attachView: View? = null

        override fun attach(view: View) {
            detach()
            attachView = view
        }

        override fun detach() {
            attachView?.removeCallbacks(this)
        }

        /**[ViewTreeObserver.OnDrawListener]*/
        override fun onDraw() {
            initLockStartTime()
            onLockScroll()
        }

        /**[ViewTreeObserver.OnGlobalLayoutListener]*/
        override fun onGlobalLayout() {
            initLockStartTime()
            onLockScroll()
        }

        open fun initLockStartTime() {
            if (_lockStartTime <= 0) {
                _lockStartTime = WKTimeUtils.getInstance().currentMills
            }
        }

        open fun isLockTimeout(): Boolean {
            return if (lockDuration > 0) {
                val nowTime = WKTimeUtils.getInstance().currentMills
                nowTime - _lockStartTime > lockDuration
            } else {
                false
            }
        }

        open fun onLockScroll() {
            attachView?.removeCallbacks(this)
            if (enableLock) {
                if (isLockTimeout()) {
                    //锁定超时, 放弃操作
                } else {
                    attachView?.post(this)
                }
            }
        }

        open fun onScrollTrigger() {
            if (autoDetach) {
                if (isLockTimeout() || lockDuration == -1L) {
                    detach()
                }
            }
        }
    }

    /**锁定滚动到最后一个位置*/
    inner class LockLayoutListener : LockScrollListener() {

        override fun attach(view: View) {
            super.attach(view)
            view.viewTreeObserver.addOnGlobalLayoutListener(this)
        }

        override fun detach() {
            super.detach()
            attachView?.viewTreeObserver?.removeOnGlobalLayoutListener(this)
        }
    }

    /**滚动到0*/
    inner class LockDrawListener : LockScrollListener() {

        override fun attach(view: View) {
            super.attach(view)
            view.viewTreeObserver.addOnDrawListener(this)
        }

        override fun detach() {
            super.detach()
            attachView?.viewTreeObserver?.removeOnDrawListener(this)
        }
    }

    private interface IAttachListener {
        fun attach(view: View)

        fun detach()
    }
}

//滚动参数
data class ScrollParams(
    var scrollPosition: Int = RecyclerView.NO_POSITION,
    var scrollType: Int = ScrollHelper.SCROLL_TYPE_NORMAL,
    var scrollAnim: Boolean = true,
    var scrollOffset: Int = 0,
    var isFromAddItem: Boolean = false
)

fun RecyclerView?.findFirstVisibleItemPosition(): Int {
    return this?.layoutManager.findFirstVisibleItemPosition()
}

fun RecyclerView.LayoutManager?.findFirstVisibleItemPosition(): Int {
    var result = RecyclerView.NO_POSITION
    this?.also { layoutManager ->
        var firstItemPosition: Int = -1
        if (layoutManager is LinearLayoutManager) {
            firstItemPosition = layoutManager.findFirstVisibleItemPosition()
        } else if (layoutManager is StaggeredGridLayoutManager) {
            firstItemPosition =
                layoutManager.findFirstVisibleItemPositions(null).firstOrNull() ?: -1
        }
        result = firstItemPosition
    }
    return result
}

fun RecyclerView?.findLastVisibleItemPosition(): Int {
    return this?.layoutManager.findLastVisibleItemPosition()
}

fun RecyclerView.LayoutManager?.findLastVisibleItemPosition(): Int {
    var result = RecyclerView.NO_POSITION
    this?.also { layoutManager ->
        var lastItemPosition: Int = -1
        if (layoutManager is LinearLayoutManager) {
            lastItemPosition = layoutManager.findLastVisibleItemPosition()
        } else if (layoutManager is StaggeredGridLayoutManager) {
            lastItemPosition =
                layoutManager.findLastVisibleItemPositions(null).lastOrNull() ?: -1
        }
        result = lastItemPosition
    }
    return result
}

fun RecyclerView?.isPositionVisible(position: Int): Boolean {
    return this?.layoutManager.isPositionVisible(position)
}

fun RecyclerView.LayoutManager?.isPositionVisible(position: Int): Boolean {
    return position >= 0 && position in findFirstVisibleItemPosition()..findLastVisibleItemPosition()

}