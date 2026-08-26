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

package com.chat.base.msgeffect.video

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Plays a full-screen luma-keyed mp4 (e.g. action_celebrate / classy_celebrate /
 * shangfang_celebrate) inside the provided container: fade-in → play once →
 * fade-out → remove. Watchdog removes the view if the MediaPlayer never
 * completes. Per-effect keying params come from [LumaKeyParams].
 */
class LumaKeyVideoEffectPlayer(private val context: Context) {

    private var videoView: LumaKeyVideoView? = null
    private var container: ViewGroup? = null
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable { cleanup() }

    fun play(
        container: ViewGroup,
        assetPath: String,
        timeoutMs: Long,
        params: LumaKeyParams = LumaKeyParams.DEFAULT
    ) {
        if (isPlaying) return
        isPlaying = true
        this.container = container

        val view = LumaKeyVideoView(context)
        // 必须在 startVideo 之前设置：保护圈几何遮罩按首帧时的 params 预计算。
        view.params = params
        videoView = view
        view.alpha = 0f

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        container.addView(view, params)

        view.onFirstFrame = {
            view.animate()
                .alpha(1f)
                .setDuration(FADE_IN_MS)
                .start()
        }

        view.onVideoEnd = { fadeOutAndRemove() }

        handler.postDelayed(timeoutRunnable, timeoutMs)

        try {
            val afd = context.assets.openFd(assetPath)
            view.startVideo(afd)
        } catch (e: Exception) {
            cleanup()
        }
    }

    private fun fadeOutAndRemove() {
        handler.removeCallbacks(timeoutRunnable)
        val view = videoView ?: return
        view.animate()
            .alpha(0f)
            .setDuration(FADE_OUT_MS)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    removeView()
                }
            })
            .start()
    }

    private fun removeView() {
        val view = videoView ?: return
        view.release()
        container?.removeView(view)
        videoView = null
        container = null
        isPlaying = false
    }

    fun cancel() {
        handler.removeCallbacks(timeoutRunnable)
        cleanup()
    }

    private fun cleanup() {
        val view = videoView ?: return
        view.release()
        (view.parent as? ViewGroup)?.removeView(view)
        videoView = null
        container = null
        isPlaying = false
    }

    companion object {
        private const val FADE_IN_MS = 450L
        private const val FADE_OUT_MS = 600L
    }
}
