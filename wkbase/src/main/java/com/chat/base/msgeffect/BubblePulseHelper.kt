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

package com.chat.base.msgeffect

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * 给消息气泡（或 RecyclerView item view）跑一次快速 scale 脉冲，作为粒子穿过气泡时的反馈。
 *
 * 视觉对齐 iOS WKBubbleInteractionHelper.pulseCell：keyframe scale
 * [1.0, 1.12, 0.95, 1.03, 1.0]，keyTimes [0, 0.2, 0.5, 0.8, 1.0]，duration 0.35s。
 *
 * 用 [View.animate] 做 scale，不改 layoutParams，避免与 RecyclerView ItemAnimator 冲突；
 * 同一 view 重复触发会取消旧的 ValueAnimator，避免叠加。
 */
object BubblePulseHelper {

    private val TAG_KEY = com.chat.base.R.id.bubble_pulse_animator_tag
    private const val DURATION_MS = 350L

    private val keyTimes = floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1.0f)
    private val keyValues = floatArrayOf(1.0f, 1.12f, 0.95f, 1.03f, 1.0f)

    fun pulse(view: View) {
        // 取消上一轮的 animator，避免同一 view 短时间内被多枚粒子重复命中时动画叠加跳变。
        val prev = view.getTag(TAG_KEY) as? ValueAnimator
        prev?.cancel()

        view.pivotX = view.width / 2f
        view.pivotY = view.height / 2f

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                val s = scaleAt(t)
                view.scaleX = s
                view.scaleY = s
            }
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: android.animation.Animator) {
                view.scaleX = 1f
                view.scaleY = 1f
                view.setTag(TAG_KEY, null)
            }

            override fun onAnimationCancel(a: android.animation.Animator) {
                view.setTag(TAG_KEY, null)
            }
        })
        view.setTag(TAG_KEY, animator)
        animator.start()
    }

    private fun scaleAt(progress: Float): Float {
        val seg = keyTimes.size - 1
        for (i in 0 until seg) {
            if (progress <= keyTimes[i + 1]) {
                val span = keyTimes[i + 1] - keyTimes[i]
                val local = if (span > 0f) (progress - keyTimes[i]) / span else 0f
                return keyValues[i] + (keyValues[i + 1] - keyValues[i]) * local
            }
        }
        return keyValues.last()
    }
}
