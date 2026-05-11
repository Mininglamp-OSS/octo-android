package com.chat.base.msgeffect

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import com.tencent.bugly.crashreport.CrashReport
import com.xinbida.wukongim.entity.WKMsg
import java.lang.ref.WeakReference

class MessageEffectManager(
    activity: Activity,
    private val overlayView: MessageEffectOverlayView
) {
    private val activityRef = WeakReference(activity)
    private val handler = Handler(Looper.getMainLooper())
    private val triggeredSet = HashSet<String>()
    private val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 收集阶段：打开聊天时短暂收集所有可见的未读效果消息，只播放最后一条
    private var collectPhase = true
    private var collectRunnable: Runnable? = null
    private var bestCandidate: CandidateEffect? = null

    private data class CandidateEffect(
        val msg: WKMsg,
        val effectType: MessageEffectType,
        val bubbleView: WeakReference<View>?,
        val avatarBitmap: Bitmap?
    )

    init {
        loadTriggeredSet()
        collectRunnable = Runnable {
            collectPhase = false
            fireBestCandidate()
        }
        handler.postDelayed(collectRunnable!!, COLLECT_WINDOW_MS)
    }

    fun onMessageVisible(msg: WKMsg?, bubbleView: View?) {
        try {
            if (msg == null) return
            if (activityRef.get()?.isFinishing == true) return

            val effectType = shouldTrigger(msg) ?: return
            val clientMsgNO = msg.clientMsgNO ?: return

            persistTriggered(clientMsgNO)

            val avatarBitmap = extractAvatar(bubbleView)

            if (collectPhase) {
                val current = bestCandidate
                if (current == null || msg.timestamp >= current.msg.timestamp) {
                    bestCandidate = CandidateEffect(
                        msg, effectType,
                        if (bubbleView != null) WeakReference(bubbleView) else null,
                        avatarBitmap
                    )
                }
            } else {
                handler.postDelayed({
                    try {
                        if (activityRef.get()?.isFinishing == true) return@postDelayed
                        playEffect(effectType, bubbleView, avatarBitmap)
                    } catch (e: Exception) {
                        CrashReport.postCatchedException(e)
                    }
                }, DEBOUNCE_MS)
            }
        } catch (e: Exception) {
            CrashReport.postCatchedException(e)
        }
    }

    private fun fireBestCandidate() {
        try {
            val candidate = bestCandidate ?: return
            bestCandidate = null
            if (activityRef.get()?.isFinishing == true) return

            val cx = overlayView.width / 2f
            val cy = overlayView.height * 0.8f
            val sourceRect = RectF(cx - 50f, cy - 50f, cx + 50f, cy + 50f)
            overlayView.playEffect(candidate.effectType, sourceRect, candidate.avatarBitmap)
        } catch (e: Exception) {
            CrashReport.postCatchedException(e)
        }
    }

    private fun playEffect(effectType: MessageEffectType, bubbleView: View?, avatarBitmap: Bitmap?) {
        val sourceRect = calculateSourceRect(bubbleView)
        overlayView.playEffect(effectType, sourceRect, avatarBitmap)
    }

    private fun shouldTrigger(msg: WKMsg): MessageEffectType? {
        val nowSec = System.currentTimeMillis() / 1000
        if (nowSec - msg.timestamp > 30) return null

        if (msg.type.toInt() != 1) return null

        val clientMsgNO = msg.clientMsgNO ?: return null
        if (triggeredSet.contains(clientMsgNO)) return null

        val content = msg.baseContentMsgModel?.getDisplayContent() ?: return null
        return MessageEffectType.detect(content)
    }

    private fun extractAvatar(bubbleView: View?): Bitmap? {
        if (bubbleView == null) return null
        try {
            val avatarView = bubbleView.findViewById<View>(com.chat.base.R.id.avatarView)
                ?: return null
            val imgView = findImageView(avatarView) ?: return null
            val drawable = imgView.drawable ?: return null
            if (drawable is BitmapDrawable) return drawable.bitmap
        } catch (_: Exception) {}
        return null
    }

    private fun findImageView(view: View): ImageView? {
        if (view is ImageView) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findImageView(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun calculateSourceRect(bubbleView: View?): RectF {
        if (bubbleView == null || !bubbleView.isAttachedToWindow) {
            val cx = overlayView.width / 2f
            val cy = overlayView.height * 0.8f
            return RectF(cx - 50f, cy - 50f, cx + 50f, cy + 50f)
        }

        val location = IntArray(2)
        bubbleView.getLocationOnScreen(location)
        val overlayLocation = IntArray(2)
        overlayView.getLocationOnScreen(overlayLocation)

        val left = (location[0] - overlayLocation[0]).toFloat()
        val top = (location[1] - overlayLocation[1]).toFloat()
        return RectF(left, top, left + bubbleView.width, top + bubbleView.height)
    }

    private fun persistTriggered(clientMsgNO: String) {
        triggeredSet.add(clientMsgNO)
        prefs.edit().putBoolean(clientMsgNO, true).apply()

        if (triggeredSet.size > MAX_CACHE_SIZE) {
            prefs.edit().clear().apply()
            triggeredSet.clear()
        }
    }

    private fun loadTriggeredSet() {
        prefs.all.keys.forEach { triggeredSet.add(it) }
    }

    fun destroy() {
        collectRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
        overlayView.cancelAll()
        bestCandidate = null
    }

    companion object {
        private const val PREFS_NAME = "msg_effect_triggered"
        private const val DEBOUNCE_MS = 150L
        private const val COLLECT_WINDOW_MS = 500L
        private const val MAX_CACHE_SIZE = 10000
    }
}
