package com.chat.base.markdown

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.chat.base.R
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.core.MarkwonTheme

class WKCodeBlockPlugin private constructor(
    private val context: Context
) : AbstractMarkwonPlugin() {

    val codeBlockBackgroundColor: Int =
        ContextCompat.getColor(context, R.color.code_block_bg)

    companion object {
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
        val codeBlockBg = ContextCompat.getColor(context, R.color.code_block_bg)
        val codeTextColor = ContextCompat.getColor(context, R.color.code_text_color)
        val blockquoteColor = ContextCompat.getColor(context, R.color.blockquote_color)

        builder
            .codeBlockBackgroundColor(codeBlockBg)
            .codeBackgroundColor(codeBlockBg)
            .codeTextColor(codeTextColor)
            .codeTypeface(Typeface.MONOSPACE)
            .codeTextSize(spToPx(15f))
            .headingBreakHeight(0)
            .blockQuoteColor(blockquoteColor)
            .blockQuoteWidth(dpToPx(3f))
            .blockMargin(dpToPx(8f))
    }
}
