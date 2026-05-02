package com.chat.base.foldable

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.window.layout.WindowMetricsCalculator

/**
 * Pane-aware window metrics for Activity Embedding (YUJ-251 / GH #180).
 *
 * Background
 * ----------
 * Once Activity Embedding (YUJ-248 / PR#177) is active, the ChatActivity runs in the
 * secondary pane of a split container. Its visible bounds (the "pane") are narrower than
 * the full display, but everything that reads [android.util.DisplayMetrics.widthPixels]
 * or [com.chat.base.utils.AndroidUtilities.getScreenWidth] still returns the whole-device
 * width. Likewise, resource qualifiers like `values-sw600dp/` gate on smallestScreenWidth
 * of the device, not the current window — so on a sw600dp+ device the chat bubble stayed
 * capped at 420dp even after the user dragged the divider to widen the pane (YUJ-250).
 *
 * Fix
 * ---
 * [WindowMetricsCalculator.computeCurrentWindowMetrics] returns the current Activity's
 * visible bounds. When Embedding is inactive (API &lt; 32, manifest opt-out, or the device
 * is in a narrow window), the returned bounds equal the full display — so call sites
 * degrade to phone-mode behavior automatically, no branching required.
 *
 * API
 * ---
 * - [widthPx]            — visible pane width in px, for driving dialog/panel/grid sizing
 * - [bubbleMaxWidthPx]   — the 72 %-of-pane cap used by chat bubbles (Google Messages parity)
 *
 * Both overloads accept either an [Activity] or a generic [Context] (which is unwrapped
 * through [ContextWrapper] to find the hosting Activity). If no Activity can be found we
 * fall back to `resources.displayMetrics.widthPixels`, preserving pre-Embedding behavior
 * on phones and background Contexts.
 *
 * See the issue for the per-site audit of call sites that were migrated here.
 */
object PaneMetrics {

    /** Fraction of the current pane width allocated to chat bubbles (Google Messages parity). */
    const val BUBBLE_WIDTH_FRACTION: Float = 0.72f

    /** Current pane (visible Activity window) width in px. */
    @JvmStatic
    fun widthPx(activity: Activity): Int {
        return try {
            WindowMetricsCalculator.getOrCreate()
                .computeCurrentWindowMetrics(activity)
                .bounds
                .width()
        } catch (t: Throwable) {
            // Defensive: never let a metrics call crash the caller. Fall back to device px.
            activity.resources.displayMetrics.widthPixels
        }
    }

    /**
     * Generic [Context] overload. Unwraps [ContextWrapper] to find the hosting Activity;
     * falls back to `resources.displayMetrics.widthPixels` otherwise (preserves legacy
     * behavior for Application/Service contexts).
     */
    @JvmStatic
    fun widthPx(context: Context): Int {
        val activity = findActivity(context)
        return if (activity != null) widthPx(activity) else context.resources.displayMetrics.widthPixels
    }

    /** Chat bubble max-width cap (pane × [BUBBLE_WIDTH_FRACTION]). */
    @JvmStatic
    fun bubbleMaxWidthPx(activity: Activity): Int =
        (widthPx(activity) * BUBBLE_WIDTH_FRACTION).toInt()

    /** Chat bubble max-width cap, accepting any [Context] (see [widthPx]). */
    @JvmStatic
    fun bubbleMaxWidthPx(context: Context): Int =
        (widthPx(context) * BUBBLE_WIDTH_FRACTION).toInt()

    private fun findActivity(context: Context?): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
