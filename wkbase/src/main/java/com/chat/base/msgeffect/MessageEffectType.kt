package com.chat.base.msgeffect

sealed class MessageEffectType(val durationMs: Long) {

    object Rocket : MessageEffectType(5200L)
    object Bomb : MessageEffectType(8500L)
    object Hearts : MessageEffectType(3000L)
    object Confetti : MessageEffectType(10000L)
    object ThumbsUp : MessageEffectType(7500L)

    companion object {
        private val heartEmojis = listOf("❤️", "💗", "💕", "💖", "💘", "❤")
        private val partyEmojis = listOf("🎉", "🎊")

        fun detect(text: String?): MessageEffectType? {
            if (text.isNullOrEmpty()) return null
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
