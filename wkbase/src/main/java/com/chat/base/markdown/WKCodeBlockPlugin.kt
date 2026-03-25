package com.chat.base.markdown

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.core.MarkwonTheme

class WKCodeBlockPlugin private constructor(
    private val context: Context
) : AbstractMarkwonPlugin() {

    val codeBlockBackgroundColor: Int = COLOR_CODE_BLOCK_BG

    companion object {
        private val COLOR_CODE_BLOCK_BG = Color.parseColor("#0D000000")
        private val COLOR_CODE_INLINE_BG = Color.parseColor("#0D000000")
        private val COLOR_CODE_TEXT = Color.parseColor("#333333")

        @JvmStatic
        fun create(context: Context): WKCodeBlockPlugin {
            return WKCodeBlockPlugin(context)
        }
    }

    private fun spToPx(sp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics
        ).toInt()
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics
        ).toInt()
    }

    override fun configureTheme(builder: MarkwonTheme.Builder) {
        builder
            .codeBlockBackgroundColor(COLOR_CODE_BLOCK_BG)
            .codeBackgroundColor(COLOR_CODE_INLINE_BG)
            .codeTextColor(COLOR_CODE_TEXT)
            .codeTypeface(Typeface.MONOSPACE)
            .codeTextSize(spToPx(15f))
            .headingBreakHeight(0)
            .blockQuoteColor(Color.parseColor("#CCCCCC"))
            .blockQuoteWidth(dpToPx(3f))
            .blockMargin(dpToPx(8f))
    }
}
