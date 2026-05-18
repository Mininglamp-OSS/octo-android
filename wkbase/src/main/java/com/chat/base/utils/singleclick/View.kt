/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.base.utils.singleclick

import android.app.Activity
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.View
import com.chat.base.R
import java.util.concurrent.TimeUnit

/**
 * <pre>
 *     author : Taylor Zhang
 *     time   : 2021/03/19
 *     desc   : View extensions.
 *     version: 1.0.0
 * </pre>
 */

/**
 * Register a callback to be invoked when this view is single clicked.
 *
 * @param interval Single click interval. Unit is [TimeUnit.MILLISECONDS].
 * @param isShareSingleClick True if this view is share single click interval whit other view
 *   in same Activity, false otherwise.
 *   : default flipped from true to false. Sharing the throttle window
 *   on the Activity decorView caused first-click-eaten bugs in TabActivity
 *   (tap group A, tap group B within 1s → B is dropped). Per-view throttle
 *   is the safe default; opt-in to shared throttle explicitly if needed.
 * @param listener Single click listener.
 */
fun View.onSingleClick(
    interval: Int? = SingleClickUtil.singleClickInterval,
    isShareSingleClick: Boolean? = false,
    listener: View.OnClickListener? = null
) {
    if (listener == null) {
        return
    }

    setOnClickListener {
        determineTriggerSingleClick(
            interval ?: SingleClickUtil.singleClickInterval, isShareSingleClick ?: false, listener
        )
    }
}

/**
 * Determine whether to trigger a single click.
 *
 * @param interval Single click interval.Unit is [TimeUnit.MILLISECONDS].
 * @param isShareSingleClick True if this view is share single click interval whit other view
 *   in same Activity, false otherwise. : default flipped from true to false.
 * @param listener Single click listener.
 */
fun View.determineTriggerSingleClick(
    interval: Int = SingleClickUtil.singleClickInterval,
    isShareSingleClick: Boolean = false,
    listener: View.OnClickListener
) {
    val target = if (isShareSingleClick) getActivity(this)?.window?.decorView ?: this else this
    val millis = target.getTag(R.id.single_click_tag_last_single_click_millis) as? Long ?: 0
    if (SystemClock.uptimeMillis() - millis >= interval) {
        target.setTag(R.id.single_click_tag_last_single_click_millis, SystemClock.uptimeMillis())
        listener.onClick(this)
    }
}

private fun getActivity(view: View): Activity? {
    var context = view.context
    while (context is ContextWrapper) {
        if (context is Activity) {
            return context
        }
        context = context.baseContext
    }
    return null
}