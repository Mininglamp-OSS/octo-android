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

package com.chat.base.msgeffect.effects

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import com.chat.base.R
import com.chat.base.WKBaseApplication

/**
 * 跟随系统媒体音量的一次性音效播放器。
 *
 * 设计要点（对齐 brief 的 "Audio playback strategy"）：
 *
 *   1. 使用 `AudioAttributes.USAGE_MEDIA + CONTENT_TYPE_SONIFICATION`，受系统
 *      媒体音量统一控制；不申请 audio focus → 背景音乐 / 播客 / 通话不被压低或
 *      打断（对齐 iOS `.ambient` 不抢占语义，但比 iOS 严格：系统媒体音量为 0
 *      时直接跳过播放，不会有"啵"的爆裂声泄漏）。
 *
 *   2. 静音模式 vs 媒体音量：Android 的「静音模式」只静铃声 (`STREAM_RING`)，
 *      `STREAM_MUSIC` 是独立的滑杆。如果用户拉低了媒体音量（不是静音模式），
 *      我们就**不响**——这是用户在主动表达「我不要听媒体音」。如果用户只是
 *      把铃声调到静音但媒体音量正常，特效音照常播——和微信 / Telegram 发
 *      🎉 在静音模式下仍能响一致。
 *
 *   3. 一次性 player：每次 [play] 都新建 MediaPlayer 并在播完 / 出错 / [release]
 *      时立即释放。原因：MessageEffectOverlayView.onDetachedFromWindow 会一次性
 *      把所有 active effect 的 onEnd 调用掉，我们需要释放可靠且不残留。
 *
 *   4. 资源加载策略：只在 `wkbase/src/main/res/raw/` 里实际存在 raw resource 时
 *      才尝试播放——若资源被裁掉（例如 confetti_burst.mp3 没有打包进来），
 *      `Resources.getIdentifier(...)` 返回 0，安静跳过即可，不抛异常。
 */
internal class ConfettiAudio {

    private var cheerPlayer: MediaPlayer? = null
    private var burstPlayer: MediaPlayer? = null

    /**
     * 立即播放爆裂声（confetti_burst，如果资源存在）+ 全场欢呼（confetti_cheer）。
     * 如果系统媒体音量为 0，整体跳过，不创建任何 MediaPlayer。
     */
    fun play() {
        val context = runCatching {
            WKBaseApplication.getInstance().context
        }.getOrNull() ?: return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return

        // 媒体音量 = 0 → 用户在主动表达「不要听媒体音」。完全跳过，连 MediaPlayer
        // 都不创建（避免 prepare 的 IO 开销和潜在的弱设备杂音）。
        if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) <= 0) return

        burstPlayer = startOneShot(context, R.raw.confetti_burst, volume = 1.0f)
        // 略低于 burst → 不压过爆裂声，与 iOS WKConfettiView playCheerSound 的
        // cheerAudioPlayer.volume = 0.85 完全一致。
        cheerPlayer = startOneShot(context, R.raw.confetti_cheer, volume = 0.85f)
    }

    /**
     * 显式释放——effect 的 onEnd 会调一次；MediaPlayer 完成播放后内部 listener
     * 也会调一次。多次 release 是幂等的。
     */
    fun release() {
        cheerPlayer?.safeRelease()
        cheerPlayer = null
        burstPlayer?.safeRelease()
        burstPlayer = null
    }

    private fun startOneShot(
        context: Context,
        @androidx.annotation.RawRes resId: Int,
        volume: Float,
    ): MediaPlayer? {
        return runCatching {
            // create() 内部完成 setDataSource + prepare，比手动一遍少踩坑；
            // 返回 null 表示资源损坏或解码失败——按设计安静吞掉。
            val player = MediaPlayer.create(context, resId) ?: return@runCatching null

            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setVolume(volume, volume)
            player.isLooping = false
            player.setOnCompletionListener { mp ->
                mp.safeRelease()
                if (cheerPlayer === mp) cheerPlayer = null
                if (burstPlayer === mp) burstPlayer = null
            }
            player.setOnErrorListener { mp, _, _ ->
                mp.safeRelease()
                true
            }
            player.start()
            player
        }.getOrNull()
    }

    private fun MediaPlayer.safeRelease() {
        runCatching {
            if (isPlaying) stop()
        }
        runCatching { reset() }
        runCatching { release() }
    }
}
