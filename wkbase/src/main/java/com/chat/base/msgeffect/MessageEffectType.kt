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

sealed class MessageEffectType(val durationMs: Long) {

    object Rocket : MessageEffectType(5200L)
    object Bomb : MessageEffectType(8500L)
    object Hearts : MessageEffectType(3000L)
    object Confetti : MessageEffectType(10000L)
    object ThumbsUp : MessageEffectType(7500L)
    object ActionVideo : MessageEffectType(4000L)
    object ClassyVideo : MessageEffectType(5500L)
    object ShangfangVideo : MessageEffectType(4000L)

    companion object {
        private val heartEmojis = listOf("❤️", "💗", "💕", "💖", "💘", "❤")
        private val partyEmojis = listOf("🎉", "🎊")

        fun detect(text: String?): MessageEffectType? {
            if (text.isNullOrEmpty()) return null
            val trimmed = text.trim()
            // 视频特效要求消息正文恰好是这个 tag（与 iOS WKMessageEffectManager 对齐），
            // 否则混在长消息里会让大段文字也触发全屏视频，体验割裂。
            if (trimmed == "[崇尚行动]") return ActionVideo
            if (trimmed == "[有品位]") return ClassyVideo
            if (trimmed == "[尚方宝剑]") return ShangfangVideo
            return when {
                text.contains("[使命必达]") -> Rocket
                text.contains("💣") -> Bomb
                heartEmojis.any { text.contains(it) } -> Hearts
                partyEmojis.any { text.contains(it) } -> Confetti
                text.contains("👍") -> ThumbsUp
                else -> null
            }
        }
    }
}
