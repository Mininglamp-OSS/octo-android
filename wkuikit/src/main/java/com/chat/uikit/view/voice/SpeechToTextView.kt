package com.chat.uikit.view.voice

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.chat.base.ui.Theme
import com.chat.uikit.R
import java.util.Timer
import java.util.TimerTask

class SpeechToTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface SpeechToTextListener {
        fun onRecognizedText(text: String)
        fun onRecordingStarted()
    }

    var listener: SpeechToTextListener? = null

    private var tvStatus: TextView
    private var tvTimer: TextView
    private var levelContentView: LinearLayout
    private var waveBarContainer: LinearLayout
    private var micButtonContainer: FrameLayout
    private var btnMicBg: ImageView
    private var btnMicIcon: ImageView
    private var ivVoiceLine: ImageView
    private var btnCancel: ImageView

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecording = false
    private var isCancelled = false
    private var startFailed = false
    private var recognizedText: String? = null
    private var confirmedText: String? = null

    private var recordDuration = 0
    private var audioTimer: Timer? = null
    private var waveformTimer: Timer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val waveBars = mutableListOf<View>()

    // Touch tracking for cancel gesture
    private var touchDownY = 0f
    private var touchDownX = 0f
    private val cancelThreshold = 80f

    companion object {
        private const val MAX_RECORD_TIME = 60
        private const val WAVE_BAR_COUNT = 10
    }

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.view_speech_to_text, this, true)
        tvStatus = view.findViewById(R.id.tvStatus)
        tvTimer = view.findViewById(R.id.tvTimer)
        levelContentView = view.findViewById(R.id.levelContentView)
        waveBarContainer = view.findViewById(R.id.waveBarContainer)
        micButtonContainer = view.findViewById(R.id.micButtonContainer)
        btnMicBg = view.findViewById(R.id.btnMicBg)
        btnMicIcon = view.findViewById(R.id.btnMicIcon)
        ivVoiceLine = view.findViewById(R.id.ivVoiceLine)
        btnCancel = view.findViewById(R.id.btnCancel)

        setupWaveBars()
        setupTouchListeners()

        // 按钮颜色改为主题色（对齐语音输入 Tab）
        btnMicBg.setColorFilter(Theme.colorAccount, PorterDuff.Mode.SRC_IN)
    }

    private fun setupWaveBars() {
        val barWidth = (3 * resources.displayMetrics.density).toInt()
        val barMargin = (2 * resources.displayMetrics.density).toInt()
        val barHeight = (15 * resources.displayMetrics.density).toInt()
        val barColor = Color.parseColor("#FD6309")

        for (i in 0 until WAVE_BAR_COUNT) {
            val bar = View(context).apply {
                setBackgroundColor(barColor)
            }
            val lp = LinearLayout.LayoutParams(barWidth, barHeight).apply {
                if (i > 0) marginStart = barMargin
            }
            waveBarContainer.addView(bar, lp)
            waveBars.add(bar)
        }
    }

    private fun setupTouchListeners() {
        micButtonContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownY = event.rawY
                    touchDownX = event.rawX
                    isCancelled = false
                    startRecording()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - touchDownY
                    val deltaX = event.rawX - touchDownX
                    // Upward swipe to cancel
                    if (deltaY < -cancelThreshold) {
                        isCancelled = true
                        tvStatus.visibility = VISIBLE
                        tvStatus.text = context.getString(R.string.ar_feed_sound_cancel)
                        levelContentView.visibility = GONE
                    }
                    // Right swipe to cancel button
                    else if (deltaX > cancelThreshold) {
                        isCancelled = true
                        btnCancel.setImageResource(R.mipmap.aio_voice_operate_press)
                        tvStatus.visibility = VISIBLE
                        tvStatus.text = context.getString(R.string.ar_feed_sound_cancel)
                        levelContentView.visibility = GONE
                    } else {
                        isCancelled = false
                        btnCancel.setImageResource(R.mipmap.aio_voice_operate_nor)
                        tvStatus.visibility = GONE
                        levelContentView.visibility = VISIBLE
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    finishAndSend()
                    true
                }
                else -> false
            }
        }
    }

    private fun startRecording() {
        if (isRecording) return

        listener?.onRecordingStarted()

        confirmedText = null
        recognizedText = null
        isCancelled = false
        startFailed = false

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            Toast.makeText(context, R.string.speech_not_available, Toast.LENGTH_SHORT).show()
            return
        }

        speechRecognizer?.setRecognitionListener(createRecognitionListener())

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.startListening(intent)
        isRecording = true

        // UI updates
        btnMicBg.setImageResource(R.mipmap.aio_voice_button_press)
        btnMicBg.setColorFilter(Theme.colorAccount, PorterDuff.Mode.SRC_IN)
        tvStatus.visibility = GONE
        levelContentView.visibility = VISIBLE
        ivVoiceLine.visibility = VISIBLE
        btnCancel.visibility = VISIBLE
        btnCancel.setImageResource(R.mipmap.aio_voice_operate_nor)

        startTimers()
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                updateWaveform(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                mainHandler.post {
                    if (!isRecording) return@post

                    // Fatal errors: service not available on this device
                    if (error == SpeechRecognizer.ERROR_CLIENT
                        || error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        stopRecording()
                        Toast.makeText(context, R.string.speech_not_available, Toast.LENGTH_SHORT).show()
                        resetUI()
                        return@post
                    }

                    // ERROR_NO_MATCH / ERROR_SPEECH_TIMEOUT on first attempt = no service
                    if (startFailed) {
                        stopRecording()
                        Toast.makeText(context, R.string.speech_not_available, Toast.LENGTH_SHORT).show()
                        resetUI()
                        return@post
                    }

                    if (recognizedText.isNullOrEmpty() && confirmedText.isNullOrEmpty()) {
                        startFailed = true
                    }

                    // Save current text and restart if still recording
                    if (!recognizedText.isNullOrEmpty()) {
                        confirmedText = recognizedText
                        startFailed = false
                    }
                    restartRecognition()
                }
            }

            override fun onResults(results: Bundle?) {
                mainHandler.post {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val currentText = matches?.firstOrNull() ?: return@post

                    recognizedText = if (!confirmedText.isNullOrEmpty()) {
                        confirmedText + currentText
                    } else {
                        currentText
                    }

                    // onResults means task finished, save and restart
                    if (isRecording) {
                        confirmedText = recognizedText
                        restartRecognition()
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                mainHandler.post {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val currentText = matches?.firstOrNull() ?: return@post

                    recognizedText = if (!confirmedText.isNullOrEmpty()) {
                        confirmedText + currentText
                    } else {
                        currentText
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun restartRecognition() {
        if (!isRecording) return

        speechRecognizer?.cancel()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.startListening(intent)
    }

    private fun finishAndSend() {
        if (!isRecording) return
        stopRecording()

        btnMicBg.setImageResource(R.mipmap.aio_voice_button_nor)
        btnMicBg.setColorFilter(Theme.colorAccount, PorterDuff.Mode.SRC_IN)
        ivVoiceLine.visibility = GONE
        btnCancel.visibility = GONE

        val finalText = if (!recognizedText.isNullOrEmpty()) recognizedText else confirmedText

        if (isCancelled) {
            // Cancelled, do nothing
        } else if (!finalText.isNullOrEmpty()) {
            listener?.onRecognizedText(finalText)
        } else {
            Toast.makeText(context, R.string.speech_not_recognized, Toast.LENGTH_SHORT).show()
        }

        resetUI()
    }

    private fun stopRecording() {
        isRecording = false
        stopTimers()

        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun resetUI() {
        tvStatus.visibility = VISIBLE
        tvStatus.text = context.getString(R.string.speech_to_text)
        levelContentView.visibility = GONE
        ivVoiceLine.visibility = GONE
        btnCancel.visibility = GONE
        recordDuration = 0
        confirmedText = null
        recognizedText = null
    }

    private fun startTimers() {
        recordDuration = 0
        tvTimer.text = "0:00"

        audioTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    mainHandler.post {
                        recordDuration++
                        updateTimeLabel()
                        if (recordDuration >= MAX_RECORD_TIME) {
                            finishAndSend()
                        }
                    }
                }
            }, 1000, 1000)
        }
    }

    private fun stopTimers() {
        audioTimer?.cancel()
        audioTimer = null
    }

    private fun updateTimeLabel() {
        val text = if (recordDuration < 60) {
            String.format("0:%02d", recordDuration)
        } else {
            String.format("%d:%02d", recordDuration / 60, recordDuration % 60)
        }
        tvTimer.text = text
    }

    private fun updateWaveform(rmsdB: Float) {
        if (!isRecording) return
        val level = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
        val barHeight = (30 * resources.displayMetrics.density).toInt()

        mainHandler.post {
            for (bar in waveBars) {
                val variation = 0.4f + (Math.random().toFloat() * 0.6f)
                val h = (6 + level * (barHeight - 6) * variation).toInt()
                val lp = bar.layoutParams as LinearLayout.LayoutParams
                lp.height = h
                bar.layoutParams = lp
            }
        }
    }

    fun cancelRecording() {
        if (isRecording) {
            isCancelled = true
            finishAndSend()
        }
    }
}
