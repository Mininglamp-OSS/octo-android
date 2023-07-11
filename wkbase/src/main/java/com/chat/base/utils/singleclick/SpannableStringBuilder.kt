package com.chat.base.utils.singleclick

import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import androidx.core.text.inSpans
import java.util.concurrent.TimeUnit

/**
 * <pre>
 *     author : Taylor Zhang
 *     time   : 2021/03/20
 *     desc   : SpannableStringBuilder extensions.
 *     version: 1.0.0
 * </pre>
 */

/**
 * Wrap appended text in [builderAction] in a [ClickableSpan].
 * If selected and single clicked, the [listener] will be invoked.
 *
 * @param listener Single click listener.
 * @param interval Single click interval.Unit is [TimeUnit.MILLISECONDS].
 * @param isShareSingleClick True if this view is share single click interval whit other view
 *   in same Activity, false otherwise.
 * @param updateDrawStateAction Update draw state action.
 * @see SpannableStringBuilder.inSpans
 */
inline fun SpannableStringBuilder.onSingleClick(
    listener: View.OnClickListener,
    interval: Int = SingleClickUtil.singleClickInterval,
    isShareSingleClick: Boolean = true,
    noinline updateDrawStateAction: ((TextPaint) -> Unit)? = null,
    builderAction: SpannableStringBuilder .() -> Unit
): SpannableStringBuilder = inSpans(
    SingleClickableSpan(
        listener, interval, isShareSingleClick, updateDrawStateAction
    ),
    builderAction = builderAction
)

/**
 * Single clickable span.
 */
class SingleClickableSpan(
    private val listener: View.OnClickListener,
    private val interval: Int = SingleClickUtil.singleClickInterval,
    private val isShareSingleClick: Boolean = true,
    private val updateDrawStateAction: ((TextPaint) -> Unit)? = null,
) : ClickableSpan() {

    private var mFakeView: View? = null

    override fun onClick(widget: View) {
        if (isShareSingleClick) {
            widget
        } else {
            if (mFakeView == null) {
                mFakeView = View(widget.context)
            }
            mFakeView!!
        }.determineTriggerSingleClick(interval, isShareSingleClick, listener)
    }

    override fun updateDrawState(ds: TextPaint) {
        updateDrawStateAction?.invoke(ds)
    }
}