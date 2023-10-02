package com.chat.base.views

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chat.base.utils.AndroidUtilities


class RecyclerViewMarginDecoration(margin: Int, private var spanCount: Int) :
    RecyclerView.ItemDecoration() {
    private var mOrderPosition = 0
    private var isPullUp = false
    private var topPosition = -1
    private var childLayoutOuPosition = 0
    private var childLayoutJiPosition = 0
    private var mMargin: Int = 0

    init {
        mMargin = AndroidUtilities.dp(margin.toFloat())
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)
        val manager = parent.layoutManager
        if (manager is GridLayoutManager) {
            val spanSizeLookup = manager.spanSizeLookup
            val spanSize = spanSizeLookup.getSpanSize(parent.getChildAdapterPosition(view))
            if (spanSize == 1) {
                //找到第一个开始位置  判断第一个item是基数还是偶数
                if (parent.getChildLayoutPosition(view) % spanCount == 0 && topPosition == -1) {
                    topPosition = parent.getChildLayoutPosition(view)
                } else if (parent.getChildLayoutPosition(view) % spanCount != 0 && topPosition == -1) {
                    topPosition = parent.getChildLayoutPosition(view)
                }

                //此处parent.getChildLayoutPosition(view))会有一个正序和反序，对应上划和下划，需要区分，因为我们下边会根据当前item的Position做条件设置合适的margin
                isPullUp = parent.getChildLayoutPosition(view) > mOrderPosition
                mOrderPosition = parent.getChildLayoutPosition(view)

                //根据奇偶数来改变内部判断条件 区分是单纯的一个一行n个item，还是多个一行n个item
                if (topPosition % spanCount == 0) {
                    if (parent.getChildLayoutPosition(view) % spanCount == 0) {
                        outRect[mMargin, 0, mMargin / 2] = 0
                        childLayoutOuPosition = parent.getChildLayoutPosition(view)
                    } else {
                        //根据正反序找到这一行的最后一个item位置  加载示例：上拉 0 1  2 3  4 5  下拉： 3 2 1 0
                        val lastItem =
                            if (isPullUp) childLayoutOuPosition + spanCount - 1 else parent.getChildLayoutPosition(
                                view
                            )
                        if (parent.getChildLayoutPosition(view) == lastItem) {
                            outRect[mMargin / 2, 0, mMargin] = 0
                        } else {
                            outRect[mMargin / 2, 0, mMargin / 2] = 0
                        }
                    }
                } else {
                    if (parent.getChildLayoutPosition(view) % spanCount != 0) {
                        outRect[mMargin, 0, mMargin / 2] = 0
                        childLayoutJiPosition = parent.getChildLayoutPosition(view)
                    } else {
                        val lastItem =
                            if (isPullUp) childLayoutJiPosition + spanCount - 1 else parent.getChildLayoutPosition(
                                view
                            )
                        if (parent.getChildLayoutPosition(view) == lastItem) {
                            outRect[mMargin / 2, 0, mMargin] = 0
                        } else {
                            outRect[mMargin / 2, 0, mMargin / 2] = 0
                        }
                    }
                }
            }
        }
    }


}