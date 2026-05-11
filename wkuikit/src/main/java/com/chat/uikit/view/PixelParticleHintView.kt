package com.chat.uikit.view

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.chat.base.glide.GlideUtils
import com.chat.base.ui.Theme
import java.lang.ref.WeakReference

class PixelParticleHintView(context: Context) : FrameLayout(context) {

    private val handler = Handler(Looper.getMainLooper())
    private var dismissed = false
    private var tapAction: Runnable? = null

    private val cyan = Color.parseColor("#00FFEB")
    private val magenta = Color.parseColor("#FF0099")
    private val themeColor = Theme.colorAccount
    private val bgColor = Color.argb(240, 5, 10, 26)

    private val hintW = dp(240f)
    private val hintH = dp(58f)
    private val cornerR = dp(4f).toFloat()
    private val avatarSize = dp(34f)

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f).toFloat()
    }
    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f).toFloat()
        strokeCap = Paint.Cap.SQUARE
        color = withAlpha(cyan, 0.7f)
    }
    private val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
    }
    private val leftBarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val triPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = withAlpha(magenta, 0.25f)
    }
    private val gridPaint = Paint().apply {
        color = withAlpha(cyan, 0.06f)
        strokeWidth = dp(0.5f).toFloat()
    }
    private val avatarBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f).toFloat()
        color = withAlpha(cyan, 0.25f)
    }

    private var scanY = -dp(2f).toFloat()
    private var scanAnimator: ValueAnimator? = null
    private var bracketAlpha = 0.7f
    private var bracketAnimator: ValueAnimator? = null

    private val bgRect = RectF()
    private val bracketPath = Path()
    private val triPath = Path()

    private var avatarView: ImageView? = null
    private var initialLabel: TextView? = null
    private var nameLabel: TextView? = null
    private var contentLabel: TextView? = null
    private var tagLabel: TextView? = null

    private var fullContentText = ""
    private var typewriterIndex = 0
    private var typewriterRunnable: Runnable? = null
    private var cursorRunnable: Runnable? = null
    private var dismissRunnable: Runnable? = null

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        setOnClickListener {
            val action = tapAction
            cleanup()
            action?.run()
        }
    }

    fun buildHUD(avatarUrl: String?, name: String, content: String?) {
        removeAllViews()

        val avatarX = dp(14f)
        val avatarY = (hintH - avatarSize) / 2
        val textLeft = avatarX + avatarSize + dp(12f)
        val textWidth = hintW - textLeft - dp(16f)

        // Avatar
        avatarView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LayoutParams(avatarSize, avatarSize).apply {
                leftMargin = avatarX
                topMargin = avatarY
            }
        }
        addView(avatarView)

        // Initial label (fallback)
        initialLabel = TextView(context).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create("monospace", Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
            setTextColor(cyan)
            setBackgroundColor(Color.parseColor("#05191F"))
            layoutParams = LayoutParams(avatarSize, avatarSize).apply {
                leftMargin = avatarX
                topMargin = avatarY
            }
            visibility = GONE
        }
        addView(initialLabel)

        if (!avatarUrl.isNullOrEmpty()) {
            GlideUtils.getInstance().showImg(context, avatarUrl, avatarView)
        } else {
            avatarView?.visibility = GONE
            initialLabel?.visibility = VISIBLE
            initialLabel?.text = if (name.isNotEmpty()) name.substring(0, 1) else "#"
        }

        // Tag label
        tagLabel = TextView(context).apply {
            typeface = Typeface.create("monospace", Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 7f)
            setTextColor(withAlpha(magenta, 0.7f))
            text = "⚡ INCOMING"
            gravity = Gravity.END
            layoutParams = LayoutParams(dp(70f), dp(10f)).apply {
                leftMargin = hintW - dp(80f)
                topMargin = dp(5f)
            }
        }
        addView(tagLabel)

        // Name label
        val displayName = if (name.length > 14) "${name.substring(0, 14)}…" else name
        nameLabel = TextView(context).apply {
            typeface = Typeface.create("monospace", Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f)
            setTextColor(Color.WHITE)
            text = "▸ $displayName"
            maxLines = 1
            alpha = 0f
            layoutParams = LayoutParams(textWidth, dp(16f)).apply {
                leftMargin = textLeft
                topMargin = dp(12f)
            }
        }
        addView(nameLabel)

        // Content label
        contentLabel = TextView(context).apply {
            typeface = Typeface.create("monospace", Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10f)
            setTextColor(withAlpha(cyan, 0.75f))
            text = ""
            maxLines = 1
            layoutParams = LayoutParams(textWidth, dp(16f)).apply {
                leftMargin = textLeft
                topMargin = dp(30f)
            }
        }
        addView(contentLabel)

        fullContentText = when {
            content == null -> ""
            content.length > 22 -> "${content.substring(0, 22)}…"
            else -> content
        }
        typewriterIndex = 0

        buildPaths()
        startScanAnimation()
        startBracketAnimation()
    }

    private fun buildPaths() {
        bgRect.set(0f, 0f, hintW.toFloat(), hintH.toFloat())

        val bLen = dp(10f).toFloat()
        val bInset = dp(2f).toFloat()
        val w = hintW.toFloat()
        val h = hintH.toFloat()
        bracketPath.reset()
        bracketPath.moveTo(bInset, bInset + bLen)
        bracketPath.lineTo(bInset, bInset)
        bracketPath.lineTo(bInset + bLen, bInset)
        bracketPath.moveTo(w - bInset - bLen, bInset)
        bracketPath.lineTo(w - bInset, bInset)
        bracketPath.lineTo(w - bInset, bInset + bLen)
        bracketPath.moveTo(w - bInset, h - bInset - bLen)
        bracketPath.lineTo(w - bInset, h - bInset)
        bracketPath.lineTo(w - bInset - bLen, h - bInset)
        bracketPath.moveTo(bInset + bLen, h - bInset)
        bracketPath.lineTo(bInset, h - bInset)
        bracketPath.lineTo(bInset, h - bInset - bLen)

        val triSize = dp(14f).toFloat()
        val triX = w - dp(18f)
        val triY = h - dp(18f)
        triPath.reset()
        triPath.moveTo(triX + triSize, triY)
        triPath.lineTo(triX + triSize, triY + triSize)
        triPath.lineTo(triX, triY + triSize)
        triPath.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = hintW.toFloat()
        val h = hintH.toFloat()

        // Shadow glow
        bgPaint.setShadowLayer(dp(15f).toFloat(), 0f, 0f, withAlpha(cyan, 0.5f))
        canvas.drawRoundRect(bgRect, cornerR, cornerR, bgPaint)
        bgPaint.clearShadowLayer()

        // Background
        bgPaint.color = bgColor
        canvas.drawRoundRect(bgRect, cornerR, cornerR, bgPaint)

        // Grid lines
        for (i in 1..3) {
            val y = h * i / 4f
            canvas.drawLine(dp(8f).toFloat(), y, w - dp(8f), y, gridPaint)
        }

        // Left bar gradient
        leftBarPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(magenta, themeColor, cyan),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, dp(3f).toFloat(), h, leftBarPaint)

        // Border gradient
        borderPaint.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(
                withAlpha(cyan, 0.9f),
                withAlpha(themeColor, 0.6f),
                withAlpha(magenta, 0.4f),
                withAlpha(cyan, 0.7f)
            ),
            floatArrayOf(0f, 0.4f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(bgRect, cornerR, cornerR, borderPaint)

        // Corner brackets
        bracketPaint.alpha = (bracketAlpha * 255).toInt()
        canvas.drawPath(bracketPath, bracketPaint)

        // Scan line
        if (scanY in 0f..h) {
            scanPaint.shader = LinearGradient(
                0f, scanY, w, scanY,
                intArrayOf(
                    Color.TRANSPARENT,
                    withAlpha(cyan, 0.4f),
                    withAlpha(cyan, 0.6f),
                    Color.TRANSPARENT
                ),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, scanY, w, scanY + dp(2f), scanPaint)
        }

        // Avatar border
        val avatarX = dp(14f).toFloat()
        val avatarY = (hintH - avatarSize) / 2f
        canvas.drawRoundRect(
            avatarX - dp(2f), avatarY - dp(2f),
            avatarX + avatarSize + dp(2f), avatarY + avatarSize + dp(2f),
            dp(4f).toFloat(), dp(4f).toFloat(), avatarBorderPaint
        )

        // Triangle accent
        canvas.drawPath(triPath, triPaint)
    }

    private fun startScanAnimation() {
        scanAnimator = ValueAnimator.ofFloat(-dp(2f).toFloat(), hintH + dp(2f).toFloat()).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                scanY = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun startBracketAnimation() {
        bracketAnimator = ValueAnimator.ofFloat(0.7f, 1.0f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                bracketAlpha = it.animatedValue as Float
            }
            start()
        }
    }

    private fun revealContent() {
        if (dismissed) return
        nameLabel?.alpha = 1f

        if (fullContentText.isEmpty()) {
            scheduleDismiss()
            return
        }
        typewriterIndex = 0
        typewriterRunnable = object : Runnable {
            override fun run() {
                if (dismissed) return
                typewriterIndex++
                if (typewriterIndex >= fullContentText.length) {
                    contentLabel?.text = "${fullContentText}▌"
                    cursorRunnable = Runnable {
                        if (!dismissed) contentLabel?.text = fullContentText
                    }
                    handler.postDelayed(cursorRunnable!!, 300)
                    scheduleDismiss()
                    return
                }
                contentLabel?.text = "${fullContentText.substring(0, typewriterIndex)}▌"
                handler.postDelayed(this, 40)
            }
        }
        handler.postDelayed(typewriterRunnable!!, 40)
    }

    private fun scheduleDismiss() {
        dismissRunnable = Runnable {
            if (!dismissed) animateSlideOut()
        }
        handler.postDelayed(dismissRunnable!!, 3000)
    }

    private fun animateSlideOut() {
        val lp = layoutParams as? FrameLayout.LayoutParams ?: run { cleanup(); return }
        val startMargin = lp.topMargin
        val endMargin = startMargin + dp(60f)
        val animator = ValueAnimator.ofInt(startMargin, endMargin)
        animator.duration = 350
        animator.addUpdateListener {
            val value = it.animatedValue as Int
            lp.topMargin = value
            layoutParams = lp
            alpha = 1f - (value - startMargin).toFloat() / (endMargin - startMargin)
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                cleanup()
            }
        })
        animator.start()
    }

    fun animateSlideIn(targetTopMargin: Float) {
        alpha = 0f
        val lp = layoutParams as? FrameLayout.LayoutParams ?: return
        val startMargin = lp.topMargin
        val animator = ValueAnimator.ofInt(startMargin, targetTopMargin.toInt())
        animator.duration = 500
        animator.interpolator = FastOutSlowInInterpolator()
        animator.addUpdateListener {
            val value = it.animatedValue as Int
            lp.topMargin = value
            layoutParams = lp
            alpha = 1f - (value - targetTopMargin) / (startMargin - targetTopMargin)
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (!dismissed) revealContent()
            }
        })
        animator.start()
    }

    fun cleanup() {
        dismissed = true
        typewriterRunnable?.let { handler.removeCallbacks(it) }
        cursorRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable?.let { handler.removeCallbacks(it) }
        scanAnimator?.cancel()
        bracketAnimator?.cancel()
        animate().cancel()
        (parent as? ViewGroup)?.removeView(this)
        if (currentHint?.get() == this) currentHint = null
    }

    private fun dp(value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics
        ).toInt()
    }

    companion object {
        private var currentHint: WeakReference<PixelParticleHintView>? = null
        private var lastShowTime = 0L

        @JvmStatic
        fun show(
            parent: ViewGroup,
            avatarUrl: String?,
            name: String,
            content: String?,
            onTap: Runnable?
        ) {
            val now = System.currentTimeMillis()
            if (now - lastShowTime < 500) return
            lastShowTime = now

            dismissCurrent()

            val activity = parent.context as? Activity ?: return
            val contentView = activity.findViewById<FrameLayout>(android.R.id.content) ?: return

            val hint = PixelParticleHintView(activity)
            hint.tapAction = onTap

            val hintW = hint.hintW
            val hintH = hint.hintH

            val containerW = contentView.width
            val containerH = contentView.height
            val tabBarH = hint.dp(49f)
            val startY = containerH
            val targetY = containerH - tabBarH - hintH

            val lp = LayoutParams(hintW, hintH).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = startY
            }
            contentView.addView(hint, lp)
            currentHint = WeakReference(hint)

            hint.buildHUD(avatarUrl, name, content)
            hint.post { hint.animateSlideIn(targetY.toFloat()) }
        }

        @JvmStatic
        fun dismissCurrent() {
            currentHint?.get()?.cleanup()
        }
    }
}

private fun withAlpha(color: Int, alpha: Float): Int {
    return Color.argb(
        (alpha * 255).toInt(),
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )
}
