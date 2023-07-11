package com.chat.uikit.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.chat.base.utils.AndroidUtilities
import com.chat.uikit.R
import com.chat.uikit.view.CircleProgress.Companion.STATUS_ERROR
import com.chat.uikit.view.CircleProgress.Companion.STATUS_PAUSE
import com.chat.uikit.view.CircleProgress.Companion.STATUS_PLAY
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class WaveformView : View {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun setWaveform(waveform: ByteArray) {
        waveformBytes = waveform
    }

    private var waveformBytes: ByteArray? = null

    private var innerColor = ContextCompat.getColor(context,R.color.color999)
    private var outerColor = ContextCompat.getColor(context,R.color.colorAccent)
    private var freshColor = ContextCompat.getColor(context, R.color.blue)
    private var paintInner: Paint = Paint()
    private var paintOuter: Paint = Paint()
    private var thumbX = 0
    var isFresh = false

    fun setProgress(progress: Float) {
        if (progress < 0) {
            return
        }
        thumbX = ceil((width * progress).toDouble()).toInt()
        if (thumbX < 0) {
            thumbX = 0
        } else if (thumbX > width) {
            thumbX = width
        }
        invalidate()
    }

    private var disposable: Disposable? = null
    private var mBindId: String? = null

    fun setBind(id: String?) {
        if (id != mBindId) {
            mBindId = id
        }
    }

    override fun onAttachedToWindow() {
        if (disposable == null) {
            disposable = RxBus.listen(ProgressEvent::class.java)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        if (it.id == mBindId) {
                            if (it.status == STATUS_PAUSE || it.status == STATUS_PLAY) {
                                setProgress(it.progress)
                            }
                        } else {
                            if (it.status == STATUS_PAUSE ||
                                    it.status == STATUS_PLAY ||
                                    it.status == STATUS_ERROR
                            ) {
                                setProgress(0f)
                            }
                        }
                    }
        }
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        disposable?.let {
            if (!it.isDisposed) {
                it.dispose()
                disposable = null
            }
        }
        disposable = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (waveformBytes == null || width == 0) {
            return
        }
        val totalBarsCount = (width / AndroidUtilities.dp( 3f)).toFloat()
        if (totalBarsCount <= 0.1f) {
            return
        }
        var value: Byte
        val samplesCount = waveformBytes!!.size * 8 / 5
        val samplesPerBar = samplesCount / totalBarsCount
        var barCounter = 0f
        var nextBarNum = 0

        paintInner.color = if (isFresh && thumbX == 0) freshColor else innerColor
        paintOuter.color = outerColor

        val y = height
        var barNum = 0
        var lastBarNum: Int
        var drawBarCount: Int

        for (a in 0 until samplesCount) {
            if (a != nextBarNum) {
                continue
            }
            drawBarCount = 0
            lastBarNum = nextBarNum
            while (lastBarNum == nextBarNum) {
                barCounter += samplesPerBar
                nextBarNum = barCounter.toInt()
                drawBarCount++
            }

            val bitPointer = a * 5
            val byteNum = bitPointer / 8
            val byteBitOffset = bitPointer - byteNum * 8
            val currentByteCount = 8 - byteBitOffset
            val nextByteRest = 5 - currentByteCount
            value = (waveformBytes!![byteNum].toInt() shr byteBitOffset and (2 shl min(5, currentByteCount) - 1) - 1).toByte()
            if (nextByteRest > 0) {
                value = (value.toInt() shl nextByteRest).toByte()
                value = value or (waveformBytes!![byteNum + 1] and ((2 shl nextByteRest - 1) - 1).toByte())
            }

            for (b in 0 until drawBarCount) {
                val x = barNum * AndroidUtilities.dp( 3f)
                if (x < thumbX && x + AndroidUtilities.dp( 2f) < thumbX) {
                    canvas.drawRect(
                            x.toFloat(),
                            (y - AndroidUtilities.dp( max(1f, 14.0f * value / 31.0f))).toFloat(),
                            (x + AndroidUtilities.dp( 2f)).toFloat(),
                            (y).toFloat(),
                            paintOuter
                    )
                } else {
                    canvas.drawRect(
                            x.toFloat(),
                            (y - AndroidUtilities.dp( max(1f, 14.0f * value / 31.0f))).toFloat(),
                            (x + AndroidUtilities.dp( 2f)).toFloat(),
                            (y).toFloat(),
                            paintInner
                    )
                    if (x < thumbX) {
                        canvas.drawRect(x.toFloat(), (y - AndroidUtilities.dp( max(1f, 14.0f * value / 31.0f))).toFloat(), thumbX.toFloat(), (y).toFloat(), paintOuter)
                    }
                }
                barNum++
            }
        }
    }
}
