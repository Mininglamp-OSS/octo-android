package com.chat.uikit.view.voice

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.chat.base.net.voice.WKVoiceInputService
import com.chat.uikit.R
import java.io.File
import java.util.Timer
import java.util.TimerTask

class VoiceInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface VoiceInputListener {
        fun onTranscribed(text: String, shouldReplace: Boolean)
        fun onRecordingStarted()
        fun onRecordingStopped()
        fun getCurrentInputText(): String?
    }

    var listener: VoiceInputListener? = null

    private val tvStatus: TextView
    private val btnMic: ImageView
    private val btnCancel: TextView
    private val waveContainer: LinearLayout
    private val spinner: ProgressBar

    private var mediaRecorder: MediaRecorder? = null
    private var recordFilePath: String? = null
    private var recordTimer: Timer? = null
    private var waveformTimer: Timer? = null
    private var recordSeconds = 0
    private val maxDuration = 60
    private val mainHandler = Handler(Looper.getMainLooper())

    private val waveBars = mutableListOf<View>()
    private var state = State.IDLE

    private val blueColor = Color.parseColor("#29B5F6")
    private val redColor = Color.parseColor("#FF6B6B")

    private enum class State {
        IDLE, RECORDING, TRANSCRIBING
    }

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.view_voice_input, this, true)
        tvStatus = view.findViewById(R.id.tvStatus)
        btnMic = view.findViewById(R.id.btnMic)
        btnCancel = view.findViewById(R.id.btnCancel)
        waveContainer = view.findViewById(R.id.waveContainer)
        spinner = view.findViewById(R.id.spinner)

        setupWaveBars()
        setupMicButton()
        setupCancelButton()
        updateUIForState(State.IDLE)
    }

    private fun setupWaveBars() {
        val barCount = 12
        val barWidth = (4 * resources.displayMetrics.density).toInt()
        val barHeight = (14 * resources.displayMetrics.density).toInt()
        val barGap = ((180 * resources.displayMetrics.density).toInt() - barWidth * barCount) / (barCount - 1)

        for (i in 0 until barCount) {
            val bar = View(context).apply {
                background = GradientDrawable().apply {
                    setColor(redColor)
                    cornerRadius = 2 * resources.displayMetrics.density
                }
            }
            val lp = LinearLayout.LayoutParams(barWidth, barHeight).apply {
                if (i > 0) marginStart = barGap.coerceAtLeast(1)
            }
            waveContainer.addView(bar, lp)
            waveBars.add(bar)
        }
    }

    private fun setupMicButton() {
        btnMic.setOnClickListener {
            when (state) {
                State.IDLE -> startRecording()
                State.RECORDING -> stopRecordingAndTranscribe()
                State.TRANSCRIBING -> { /* ignore */ }
            }
        }
    }

    private fun setupCancelButton() {
        // Set rounded background
        btnCancel.background = GradientDrawable().apply {
            setColor(Color.parseColor("#FCEAEA"))
            cornerRadius = 16 * resources.displayMetrics.density
        }
        btnCancel.setOnClickListener {
            if (state == State.RECORDING) {
                cancelRecording()
            }
        }
    }

    private fun updateUIForState(newState: State) {
        state = newState
        when (newState) {
            State.IDLE -> {
                setMicBackground(blueColor)
                btnMic.isEnabled = true
                btnMic.alpha = 1f
                btnMic.setPadding(dp(15), dp(15), dp(15), dp(15))
                btnMic.setImageResource(R.mipmap.ic_mic_idle)
                tvStatus.text = context.getString(R.string.click_to_speak)
                tvStatus.setTextColor(Color.GRAY)
                waveContainer.visibility = GONE
                btnCancel.visibility = GONE
                spinner.visibility = GONE
            }
            State.RECORDING -> {
                setMicBackground(redColor)
                btnMic.isEnabled = true
                btnMic.alpha = 1f
                btnMic.setPadding(0, 0, 0, 0)
                btnMic.setImageResource(R.mipmap.ic_mic_stop)
                tvStatus.text = context.getString(R.string.click_to_end)
                tvStatus.setTextColor(redColor)
                waveContainer.visibility = VISIBLE
                btnCancel.visibility = VISIBLE
                spinner.visibility = GONE
            }
            State.TRANSCRIBING -> {
                setMicBackground(blueColor)
                btnMic.isEnabled = false
                btnMic.alpha = 0.5f
                btnMic.setPadding(dp(15), dp(15), dp(15), dp(15))
                btnMic.setImageResource(R.mipmap.ic_mic_idle)
                tvStatus.text = context.getString(R.string.recognizing)
                tvStatus.setTextColor(Color.GRAY)
                waveContainer.visibility = GONE
                btnCancel.visibility = GONE
                spinner.visibility = VISIBLE
            }
        }
    }

    private fun setMicBackground(color: Int) {
        btnMic.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun startRecording() {
        val tempDir = context.externalCacheDir ?: context.cacheDir
        recordFilePath = File(tempDir, "voice_input_${System.currentTimeMillis()}.m4a").absolutePath

        try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioChannels(1)
                setOutputFile(recordFilePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Toast.makeText(context, R.string.speech_recognize_failed, Toast.LENGTH_SHORT).show()
            cleanupRecordFile()
            return
        }

        recordSeconds = 0
        updateUIForState(State.RECORDING)
        listener?.onRecordingStarted()

        recordTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    mainHandler.post {
                        recordSeconds++
                        if (recordSeconds >= maxDuration) {
                            stopRecordingAndTranscribe()
                        }
                    }
                }
            }, 1000, 1000)
        }

        waveformTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    mainHandler.post { updateWaveform() }
                }
            }, 100, 100)
        }
    }

    private fun stopRecordingAndTranscribe() {
        invalidateTimers()

        val recorder = mediaRecorder ?: return
        val duration = recordSeconds

        try {
            recorder.stop()
            recorder.release()
        } catch (e: Exception) {
            // ignored
        }
        mediaRecorder = null

        listener?.onRecordingStopped()

        if (duration < 1) {
            Toast.makeText(context, R.string.speech_too_short, Toast.LENGTH_SHORT).show()
            updateUIForState(State.IDLE)
            cleanupRecordFile()
            return
        }

        val audioFile = File(recordFilePath ?: return)
        if (!audioFile.exists() || audioFile.length() == 0L) {
            updateUIForState(State.IDLE)
            cleanupRecordFile()
            return
        }

        val contextText = listener?.getCurrentInputText()

        updateUIForState(State.TRANSCRIBING)

        WKVoiceInputService.instance.transcribeAudio(audioFile, contextText) { result, error ->
            cleanupRecordFile()
            if (error != null || result == null || result.text.isEmpty()) {
                Toast.makeText(context, R.string.speech_recognize_failed, Toast.LENGTH_SHORT).show()
                updateUIForState(State.IDLE)
                return@transcribeAudio
            }

            val shouldReplace = !contextText.isNullOrEmpty()
            listener?.onTranscribed(result.text, shouldReplace)
            updateUIForState(State.IDLE)
        }
    }

    fun cancelRecording() {
        invalidateTimers()
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            // ignored
        }
        mediaRecorder = null
        cleanupRecordFile()
        listener?.onRecordingStopped()
        updateUIForState(State.IDLE)
    }

    fun cancelIfRecording() {
        if (state == State.RECORDING) {
            cancelRecording()
        }
    }

    private fun invalidateTimers() {
        recordTimer?.cancel()
        recordTimer = null
        waveformTimer?.cancel()
        waveformTimer = null
    }

    private fun cleanupRecordFile() {
        recordFilePath?.let {
            File(it).delete()
            recordFilePath = null
        }
    }

    private fun updateWaveform() {
        if (state != State.RECORDING || mediaRecorder == null) return
        try {
            val amplitude = mediaRecorder?.maxAmplitude ?: 0
            val normalizedPower = if (amplitude > 0) {
                ((20 * Math.log10(amplitude.toDouble()) + 50) / 50).toFloat().coerceIn(0f, 1f)
            } else 0f

            val waveHeight = (36 * resources.displayMetrics.density).toInt()
            val barWidth = (4 * resources.displayMetrics.density).toInt()

            for (bar in waveBars) {
                val variation = 0.4f + (Math.random().toFloat() * 0.6f)
                val h = (6 + normalizedPower * 28 * variation).toInt()
                    .coerceAtMost(waveHeight)
                    .coerceAtLeast((6 * resources.displayMetrics.density).toInt())
                val lp = bar.layoutParams as LinearLayout.LayoutParams
                lp.height = h
                bar.layoutParams = lp
            }
        } catch (e: Exception) {
            // MediaRecorder may have been released
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
