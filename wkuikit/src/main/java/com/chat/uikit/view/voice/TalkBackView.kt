package com.chat.uikit.view.voice

import android.content.Context
import android.graphics.PorterDuff
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
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

        recordAudioView.setRecordAudioListener(object : RecordAudioView.IRecordAudioListener {
            override fun onRecordPrepare(): Boolean = true

            override fun onRecordStart(): String {
                recordTotalTime = 0
                initTimer()
                timer?.schedule(timerTask, 0, 1000)
                audioFileName = WKUIKitApplication.getInstance().context.externalCacheDir.toString() +
                        File.separator + createAudioName()
                mHorVoiceView.startRecord()
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
                updateCancelUi()
                return false
            }

            override fun onSlideTop() {
                mHorVoiceView.visibility = INVISIBLE
                tvRecordTips.visibility = INVISIBLE
                layoutCancelView.visibility = VISIBLE
            }

            override fun onFingerPress() {
                mHorVoiceView.visibility = VISIBLE
                tvRecordTips.visibility = VISIBLE
                tvRecordTips.text = recordStatusDescription[1]
                layoutCancelView.visibility = INVISIBLE
            }
        })
    }

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
        updateCancelUi()
    }

    private fun createAudioName(): String {
        val time = System.currentTimeMillis()
        return UUID.randomUUID().toString().replace("-", "") + "_" + time + ".amr"
    }
}
