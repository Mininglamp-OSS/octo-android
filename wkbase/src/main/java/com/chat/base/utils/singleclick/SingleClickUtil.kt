package com.chat.base.utils.singleclick

import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import androidx.core.text.inSpans
import java.util.concurrent.TimeUnit

object SingleClickUtil {
    /**
     * Global single click interval.
     */
    var singleClickInterval: Int = 1000
        set(value) {
            if (value <= 0) {
                throw IllegalArgumentException("Single click interval must be greater than 0.")
            }
            field = value
        }

    /**
     * Register a callback to be invoked when this view is single clicked.
     *
     * @param listener Single click listener.
     */
    @JvmStatic
    fun onSingleClick(view: View, listener: View.OnClickListener) {
        view.onSingleClick(listener = listener)
    }

    /**
     * Register a callback to be invoked when this view is single clicked.
     *
     * @param interval Single click interval. Unit is [TimeUnit.MILLISECONDS].
     * @param listener Single click listener.
     */
    @JvmStatic
    fun onSingleClick(view: View, interval: Int, listener: View.OnClickListener) {
        view.onSingleClick(interval, listener = listener)
    }

    /**
     * Register a callback to be invoked when this view is single clicked.
     *
     * @param isShareSingleClick True if this view is share single click interval whit other view
     *   in same Activity, false otherwise.
     * @param listener Single click listener.
     */
    @JvmStatic
    fun onSingleClick(view: View, isShareSingleClick: Boolean, listener: View.OnClickListener) {
        view.onSingleClick(isShareSingleClick = isShareSingleClick, listener = listener)
    }

    /**
     * Register a callback to be invoked when this view is single clicked.
     *
     * @param interval Single click interval. Unit is [TimeUnit.MILLISECONDS].
     * @param isShareSingleClick True if this view is share single click interval whit other view
     *   in same Activity, false otherwise.
     * @param listener Single click listener.
     */
    @JvmStatic
    fun onSingleClick(
        view: View,
        interval: Int,
        isShareSingleClick: Boolean,
        listener: View.OnClickListener
    ) {
        view.onSingleClick(interval, isShareSingleClick, listener)
    }

    /**
     * Wrap appended text in [builderAction] in a [ClickableSpan].
     * If selected and single clicked, the [listener] will be invoked.
     *
     * @param listener Single click listener.
     * @see SpannableStringBuilder.inSpans
     */
    @JvmStatic
    fun onSingleClick(
        builder: SpannableStringBuilder,
        listener: View.OnClickListener,
        builderAction: SpannableStringBuilder .() -> Unit
    ) {
        builder.onSingleClick(listener, builderAction = builderAction)
    }

    /**
     * Wrap appended text in [builderAction] in a [ClickableSpan].
     * If selected and single clicked, the [listener] will be invoked.
     *
     * @param listener Single click listener.
     * @param interval Single click interval.Unit is [TimeUnit.MILLISECONDS].
     * @see SpannableStringBuilder.inSpans
     */
    @JvmStatic
    fun onSingleClick(
        builder: SpannableStringBuilder,
        interval: Int,
        listener: View.OnClickListener,
        builderAction: SpannableStringBuilder .() -> Unit
    ) {
        builder.onSingleClick(listener, interval, builderAction = builderAction)
    }

    /**
     * Wrap appended text in [builderAction] in a [ClickableSpan].
     * If selected and single clicked, the [listener] will be invoked.
     *
     * @param listener Single click listener.
     * @param isShareSingleClick True if this view is share single click interval whit other view
     *   in same Activity, false otherwise.
     * @see SpannableStringBuilder.inSpans
     */
    @JvmStatic
    fun onSingleClick(
        builder: SpannableStringBuilder,
        isShareSingleClick: Boolean,
        listener: View.OnClickListener,
        builderAction: SpannableStringBuilder .() -> Unit
    ) {
        builder.onSingleClick(
            listener, isShareSingleClick = isShareSingleClick, builderAction = builderAction
        )
    }

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
    @JvmStatic
    fun onSingleClick(
        builder: SpannableStringBuilder,
        interval: Int,
        isShareSingleClick: Boolean,
        listener: View.OnClickListener,
        updateDrawStateAction: ((TextPaint) -> Unit),
        builderAction: SpannableStringBuilder .() -> Unit
    ) {
        builder.onSingleClick(
            listener, interval, isShareSingleClick, updateDrawStateAction, builderAction
        )
    }

    /**
     * Determine whether to trigger a single click.
     *
     * @param listener Single click listener.
     */
    @JvmStatic
    fun determineTriggerSingleClick(view: View, listener: View.OnClickListener) {
        view.determineTriggerSingleClick(listener = listener)
    }

    /**
     * Determine whether to trigger a single click.
     *
     * @param interval Single click interval.Unit is [TimeUnit.MILLISECONDS].
     * @param listener Single click listener.
     */
    @JvmStatic
    fun determineTriggerSingleClick(view: View, interval: Int, listener: View.OnClickListener) {
        view.determineTriggerSingleClick(interval = interval, listener = listener)
    }

    /**
     * Determine whether to trigger a single click.
     *
     * @param isShareSingleClick True if this view is share single click interval whit other view
     *   in same Activity, false otherwise.
     * @param listener Single click listener.
     */
    @JvmStatic
    fun determineTriggerSingleClick(
        view: View,
        isShareSingleClick: Boolean,
        listener: View.OnClickListener
    ) {
        view.determineTriggerSingleClick(
            isShareSingleClick = isShareSingleClick, listener = listener
        )
    }

    /**
     * Determine whether to trigger a single click.
     *
     * @param interval Single click interval.Unit is [TimeUnit.MILLISECONDS].
     * @param isShareSingleClick True if this view is share single click interval whit other view
     *   in same Activity, false otherwise.
     * @param listener Single click listener.
     */
    @JvmStatic
    fun determineTriggerSingleClick(
        view: View,
        interval: Int,
        isShareSingleClick: Boolean,
        listener: View.OnClickListener
    ) {
        view.determineTriggerSingleClick(interval, isShareSingleClick, listener)
    }
}