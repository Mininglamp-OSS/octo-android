package com.chat.uikit.chat

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.*
import androidx.recyclerview.widget.RecyclerView
import com.chat.base.msg.ChatAdapter
import com.chat.base.utils.AndroidUtilities
import com.chat.uikit.R
import kotlin.math.abs
import kotlin.math.min

class MessageSwipeController(
    private val context: Context,
    private val swipeControllerActions: SwipeControllerActions
) :
    ItemTouchHelper.Callback() {

    private lateinit var imageDrawable: Drawable
    private lateinit var shareRound: Drawable

    private var currentItemViewHolder: RecyclerView.ViewHolder? = null
    private lateinit var mView: View
    private var dX = 0f

    private var replyButtonProgress: Float = 0.toFloat()
    private var lastReplyButtonAnimationTime: Long = 0
    private var swipeBack = false
    private var isVibrate = false
    private var startTracking = false

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        mView = viewHolder.itemView
        imageDrawable = context.getDrawable(R.drawable.ic_reply_black_24dp)!!
        shareRound = context.getDrawable(R.mipmap.ic_round_shape)!!
        val index = viewHolder.bindingAdapterPosition
        val chatAdapter = viewHolder.bindingAdapter as ChatAdapter
        val isSwipe = chatAdapter.isCanSwipe(index)
        return if (isSwipe)
            makeMovementFlags(ACTION_STATE_IDLE, LEFT)
        else makeMovementFlags(0, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun convertToAbsoluteDirection(flags: Int, layoutDirection: Int): Int {
        if (swipeBack) {
            swipeBack = false
            return 0
        }
        return super.convertToAbsoluteDirection(flags, layoutDirection)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ACTION_STATE_SWIPE) {
            setTouchListener(recyclerView, viewHolder)
        }
        if (abs(mView.translationX) < AndroidUtilities.dp(130f) || abs(dX) < abs(this.dX)) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            this.dX = abs(dX)
            startTracking = true
        }
        currentItemViewHolder = viewHolder
        drawReplyButton(c)

        if (dX == 0f) {
//            Handler(Looper.myLooper()!!).postDelayed({
//                swipeControllerActions.hideSoft()
//            }, 2000)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setTouchListener(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
    ) {
        recyclerView.setOnTouchListener { _, event ->
            swipeBack =
                event.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP
            if (swipeBack) {
                if (abs(mView.translationX) >= AndroidUtilities.dp(100f)) {
                    swipeControllerActions.showReplyUI(viewHolder.bindingAdapterPosition)
                }
            }

            false
        }
    }

    private fun drawReplyButton(canvas: Canvas) {
        if (currentItemViewHolder == null) {
            return
        }

        val translationX = abs(mView.translationX)
        val newTime = System.currentTimeMillis()
        val dt = min(17, newTime - lastReplyButtonAnimationTime)
        lastReplyButtonAnimationTime = newTime
        val showing = translationX >= AndroidUtilities.dp(30f)
        if (showing) {
            if (replyButtonProgress < 1.0f) {
                replyButtonProgress += dt / 180.0f
                if (replyButtonProgress > 1.0f) {
                    replyButtonProgress = 1.0f
                } else {
                    mView.invalidate()
                }
            }
        } else if (translationX <= 0.0f) {
            replyButtonProgress = 0f
            startTracking = false
            isVibrate = false
        } else {
            if (replyButtonProgress > 0.0f) {
                replyButtonProgress -= dt / 180.0f
                if (replyButtonProgress < 0.1f) {
                    replyButtonProgress = 0f
                } else {
                    mView.invalidate()
                }
            }
        }
        val alpha: Int
        val scale: Float
        if (showing) {
            scale = if (replyButtonProgress <= 0.8f) {
                1.2f * (replyButtonProgress / 0.8f)
            } else {
                1.2f - 0.2f * ((replyButtonProgress - 0.8f) / 0.2f)
            }
            alpha = min(255f, 255 * (replyButtonProgress / 0.8f)).toInt()
        } else {
            scale = replyButtonProgress
            alpha = min(255f, 255 * replyButtonProgress).toInt()
        }
        shareRound.alpha = alpha

        imageDrawable.alpha = alpha
        if (startTracking) {
            if (!isVibrate && abs(mView.translationX) >= AndroidUtilities.dp(100f)) {
                mView.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
                isVibrate = true
            }
        }
        val maxWidth = AndroidUtilities.getScreenWidth()
        val x: Int = if (abs(mView.translationX) > AndroidUtilities.dp(130f)) {
            maxWidth - AndroidUtilities.dp(130f) / 2
        } else {
            maxWidth - (abs(mView.translationX) / 2).toInt()
        }

        val y = (mView.top + mView.measuredHeight / 2).toFloat()
        shareRound.colorFilter =
            PorterDuffColorFilter(
                ContextCompat.getColor(context, R.color.colorSystemBg),
                PorterDuff.Mode.MULTIPLY
            )

        shareRound.setBounds(
            (x - AndroidUtilities.dp(18f) * scale).toInt(),
            (y - AndroidUtilities.dp(18f) * scale).toInt(),
            (x + AndroidUtilities.dp(18f) * scale).toInt(),
            (y + AndroidUtilities.dp(18f) * scale).toInt()
        )
        shareRound.draw(canvas)
        imageDrawable.setBounds(
            (x - AndroidUtilities.dp(12f) * scale).toInt(),
            (y - AndroidUtilities.dp(11f) * scale).toInt(),
            (x + AndroidUtilities.dp(12f) * scale).toInt(),
            (y + AndroidUtilities.dp(10f) * scale).toInt()
        )
        imageDrawable.draw(canvas)
        shareRound.alpha = 255
        imageDrawable.alpha = 255
    }
}
