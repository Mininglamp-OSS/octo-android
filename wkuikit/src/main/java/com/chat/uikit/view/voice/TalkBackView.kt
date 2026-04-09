package com.chat.uikit.view.voice

import android.content.Context
import android.graphics.PorterDuff
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.chat.base.ui.Theme
import com.chat.base.utils.WKCommonUtils
import com.chat.base.utils.StringUtils
import com.chat.uikit.R
import com.chat.uikit.WKUIKitApplication
import java.io.File
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

class TalkBackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface TalkBackViewListener {
        fun onSendRecord(seconds: Int, audioPath: String, waveform: String)
    }

    var listener: TalkBackViewListener? = null

    private var recordAudioView: RecordAudioView
    private var tvRecordTips: TextView
    private var layoutCancelView: LinearLayout
    private var mHorVoiceView: LineWaveVoiceView

    private var recordTotalTime = 0L
    private val maxRecordTime = 60000L
    private val minRecordTime = 1000L
    private var timer: Timer? = null
    private var timerTask: TimerTask? = null
    private var audioFileName: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val recordStatusDescription: Array<String>

    // -- overlay --
    private var overlayView: RecordingOverlayView? = null
    private var amplitudeTimer: Timer? = null

    // -- AudioRecord 振幅降级（兼容 getMaxAmplitude 失效的设备/系统） --
    private var audioRecord: AudioRecord? = null
    private var meteringThread: Thread? = null
    @Volatile private var meteringAmplitude = 0
    private var useAudioRecordMetering = false
    private var maxAmpZeroCount = 0

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.view_talk_back, this, true)
        recordAudioView = view.findViewById(R.id.ivRecording)
        tvRecordTips = view.findViewById(R.id.record_tips)
        layoutCancelView = view.findViewById(R.id.pp_layout_cancel)
        mHorVoiceView = view.findViewById(R.id.waveVoiceView)
        mHorVoiceView.setTextColor(Theme.colorAccount)
        mHorVoiceView.setLineColor(Theme.colorAccount)

        recordStatusDescription = arrayOf(
            context.getString(R.string.press_talk),
            context.getString(R.string.hold_to_record)
        )

        // 按钮颜色改为主题色 + 图标居中（对齐语音转文字 Tab）
        recordAudioView.background.mutate().setColorFilter(Theme.colorAccount, PorterDuff.Mode.SRC_IN)
        // foreground 图标加 padding 使其居中（40dp icon in 100dp button → 30dp padding）
        val iconPad = (30 * resources.displayMetrics.density).toInt()
        recordAudioView.foreground?.let {
            it.setBounds(0, 0, it.intrinsicWidth, it.intrinsicHeight)
        }
        recordAudioView.setPadding(iconPad, iconPad, iconPad, iconPad)

        // 转发触摸坐标到覆盖层（驱动光圈跟随手指）
        recordAudioView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                    overlayView?.updateTouchPosition(event.rawX, event.rawY)
            }
            false // 不消费，让 RecordAudioView 继续处理
        }

        recordAudioView.setRecordAudioListener(object : RecordAudioView.IRecordAudioListener {
            override fun onRecordPrepare(): Boolean = true

            override fun onRecordStart(): String {
                recordTotalTime = 0
                initTimer()
                timer?.schedule(timerTask, 0, 1000)
                audioFileName = WKUIKitApplication.getInstance().context.externalCacheDir.toString() +
                        File.separator + createAudioName()
                mHorVoiceView.startRecord()
                showOverlay()
                return audioFileName!!
            }

            override fun onRecordStop(): Boolean {
                Log.e("录制的总时长：", "$recordTotalTime")
                if (recordTotalTime >= minRecordTime) {
                    timer?.cancel()
                    val time = (recordTotalTime / 1000).toInt()
                    if (time <= 0) return false
                    val dbs = AudioRecordManager.getInstance().dbs
                    val waveform = WKCommonUtils.getInstance().base64Encode(dbs)
                    listener?.onSendRecord(time, audioFileName!!, waveform)
                }
                onRecordCancel()
                return false
            }

            override fun onRecordCancel(): Boolean {
                timer?.cancel()
                dismissOverlay()
                updateCancelUi()
                return false
            }

            override fun onSlideTop() {
                if (overlayView != null) {
                    // 覆盖层显示时，由覆盖层展示取消状态，隐藏原有 UI
                    overlayView?.updateState(true)
                } else {
                    mHorVoiceView.visibility = INVISIBLE
                    tvRecordTips.visibility = INVISIBLE
                    layoutCancelView.visibility = VISIBLE
                }
            }

            override fun onFingerPress() {
                if (overlayView != null) {
                    // 覆盖层显示时，由覆盖层展示录音状态，隐藏原有 UI
                    overlayView?.updateState(false)
                } else {
                    mHorVoiceView.visibility = VISIBLE
                    tvRecordTips.visibility = VISIBLE
                    tvRecordTips.text = recordStatusDescription[1]
                    layoutCancelView.visibility = INVISIBLE
                }
            }
        })
    }

    // ========== overlay management ==========

    private fun showOverlay() {
        // 录音中禁止 ViewPager2 拦截触摸（防止水平滑动导致 ACTION_CANCEL 中断录音）
        recordAudioView.parent?.requestDisallowInterceptTouchEvent(true)

        // 隐藏原有录音 UI（按钮 INVISIBLE 保留触摸能力）
        recordAudioView.visibility = INVISIBLE
        mHorVoiceView.visibility = INVISIBLE
        tvRecordTips.visibility = INVISIBLE
        layoutCancelView.visibility = INVISIBLE

        val overlay = RecordingOverlayView(context)
        overlayView = overlay
        overlay.show()
        startAmplitudePolling()
    }

    private fun dismissOverlay() {
        stopAmplitudePolling()
        overlayView?.dismiss()
        overlayView = null

        // 恢复触摸拦截和按钮可见
        recordAudioView.parent?.requestDisallowInterceptTouchEvent(false)
        recordAudioView.visibility = VISIBLE
    }

    private fun startAmplitudePolling() {
        maxAmpZeroCount = 0
        useAudioRecordMetering = false
        amplitudeTimer = Timer().also { t ->
            t.schedule(object : TimerTask() {
                override fun run() {
                    val amp: Float
                    if (!useAudioRecordMetering) {
                        val raw = AudioRecordManager.getInstance().maxAmplitude
                        if (raw == 0f) {
                            maxAmpZeroCount++
                            if (maxAmpZeroCount >= 10) {
                                useAudioRecordMetering = true
                                startAudioRecordMetering()
                            }
                        } else {
                            maxAmpZeroCount = 0
                        }
                        amp = raw
                    } else {
                        amp = meteringAmplitude / 32768f
                    }
                    mainHandler.post {
                        overlayView?.updateAmplitude(amp)
                    }
                }
            }, 0, 80)
        }
    }

    private fun stopAmplitudePolling() {
        amplitudeTimer?.cancel()
        amplitudeTimer = null
        stopAudioRecordMetering()
    }

    @Suppress("MissingPermission")
    private fun startAudioRecordMetering() {
        try {
            val sampleRate = 16000
            val bufSize = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (bufSize <= 0) return
            val ar = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufSize
            )
            if (ar.state != AudioRecord.STATE_INITIALIZED) { ar.release(); return }
            ar.startRecording()
            audioRecord = ar
            meteringThread = Thread {
                val buf = ShortArray(bufSize / 2)
                while (!Thread.currentThread().isInterrupted) {
                    val read = try { ar.read(buf, 0, buf.size) } catch (_: Exception) { break }
                    if (read > 0) {
                        var maxVal = 0
                        for (j in 0 until read) {
                            val v = kotlin.math.abs(buf[j].toInt())
                            if (v > maxVal) maxVal = v
                        }
                        meteringAmplitude = maxVal
                    }
                }
            }.apply { isDaemon = true; name = "TalkBackMetering"; start() }
        } catch (_: Exception) {}
    }

    private fun stopAudioRecordMetering() {
        meteringThread?.interrupt(); meteringThread = null
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null; meteringAmplitude = 0
    }

    // ========== existing helpers ==========

    private fun initTimer() {
        timer = Timer()
        timerTask = object : TimerTask() {
            override fun run() {
                mainHandler.post {
                    recordTotalTime += 1000
                    updateTimerUI()
                }
            }
        }
    }

    private fun updateTimerUI() {
        if (recordTotalTime >= maxRecordTime) {
            recordAudioView.invokeStop()
        } else {
            val content = context.getString(R.string.time_remaining)
            val string = " $content ${StringUtils.formatRecordTime(recordTotalTime, maxRecordTime)} "
            mHorVoiceView.setText(string)
        }
    }

    private fun updateCancelUi() {
        mHorVoiceView.visibility = INVISIBLE
        tvRecordTips.visibility = VISIBLE
        layoutCancelView.visibility = INVISIBLE
        tvRecordTips.text = recordStatusDescription[0]
        mHorVoiceView.stopRecord()
    }

    fun cancelRecording() {
        timer?.cancel()
        AudioRecordManager.getInstance().cancelRecord()
        dismissOverlay()
        updateCancelUi()
    }

    private fun createAudioName(): String {
        val time = System.currentTimeMillis()
        return UUID.randomUUID().toString().replace("-", "") + "_" + time + ".amr"
    }
}
