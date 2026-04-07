package com.chat.uikit.view.voice

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.Build
import android.graphics.drawable.GradientDrawable
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.chat.base.net.voice.WKVoiceInputService
import com.chat.base.ui.Theme
import com.chat.uikit.R
import java.io.File
import java.util.Timer
import java.util.TimerTask

/**
 * 语音输入视图 —— 1:1 对齐 iOS WKVoiceInputView.m
 * 纯代码布局，不依赖 XML。
 */
class VoiceInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // ───────── 对外接口 ─────────
    interface VoiceInputListener {
        fun onTranscribed(text: String, shouldReplace: Boolean)
        fun onRecordingStarted()
        fun onRecordingStopped()
        fun getCurrentInputText(): String?
        fun getChatContext(): String?
        fun onInsertText(text: String)
        fun onDeleteBackward()
    }

    var listener: VoiceInputListener? = null

    // ───────── 常量（对齐 iOS） ─────────
    private val kPillWidth  get() = dp(120)
    private val kPillHeight get() = dp(46)
    private val kCircleBase get() = dp(80)
    private val kHelperBtnSize get() = dp(40)
    private val kHelperRightMargin get() = dp(16)
    private val kHelperTopMargin  get() = dp(10)
    private val kHelperGap get() = dp(8)
    private val kStatusIdleY  get() = kHelperTopMargin + kHelperBtnSize + dp(12)
    private val kStatusRecordY get() = kHelperTopMargin + dp(20)
    private val kMicY get() = kHelperTopMargin + kHelperBtnSize + dp(12) + dp(18) + dp(10)
    private val kBottomBtnW get() = dp(100)
    private val kBottomBtnH get() = dp(32)
    private val kBottomGap  get() = dp(24)

    private val bgColor = Color.parseColor("#EAEAED")
    private val statusIdleColor = Color.parseColor("#737378")
    private val statusRecordColor = Color.parseColor("#333333")
    private val helperBtnBg = Color.parseColor("#E0E0E6")
    private val glowColor = Color.parseColor("#66D1D1D6")
    private val accentColor: Int get() = Theme.colorAccount

    // ───────── UI 元素 ─────────
    private val btnAt: ImageView
    private val btnSpace: ImageView
    private val btnDelete: ImageView
    private val tvStatus: TextView
    private val glowView: View
    private val micButton: FrameLayout
    private val micIcon: ImageView
    private val dotsContainer: LinearLayout
    private val waveContainer: LinearLayout
    private val thinkingLabel: TextView
    private var thinkingFillView: View? = null
    private val bottomButton: TextView

    private val waveBars = mutableListOf<View>()

    // ───────── 录音状态 ─────────
    private var mediaRecorder: MediaRecorder? = null
    private var recordFilePath: String? = null
    private var recordTimer: Timer? = null
    private var waveformTimer: Timer? = null
    private var recordSeconds = 0
    private val maxDuration = 60
    private val mainHandler = Handler(Looper.getMainLooper())
    private var state = State.IDLE
    private var hasReceivedAudio = false
    private var currentPower = 0f

    // ───────── AudioRecord 振幅计量（兼容 getMaxAmplitude 失效的设备） ─────────
    private var audioRecord: AudioRecord? = null
    private var meteringThread: Thread? = null
    @Volatile private var meteringAmplitude = 0
    private var useAudioRecordMetering = false  // getMaxAmplitude 失效时自动切换
    private var maxAmpZeroCount = 0             // 连续返回 0 的次数

    // ───────── 动画过渡 ─────────
    private var transitionAnimator: ValueAnimator? = null
    // 动画插值的按钮宽高（用于 measure/layout 中间态）
    private var animatedMicW = 0
    private var animatedMicH = 0
    private var isAnimating = false

    // ───────── Thinking 渐进填充 ─────────
    private var thinkingTimer: Timer? = null
    private var thinkingProgress = 0f
    private var thinkingCompleting = false
    private var pendingTranscribeText: String? = null
    private var pendingTranscribeShouldReplace = false

    // ───────── 背景缓存 ─────────
    private var cachedBgState: State? = null
    private var cachedBgDrawable: GradientDrawable? = null

    private enum class State { IDLE, RECORDING, TRANSCRIBING }

    // ───────── 初始化 ─────────
    init {
        setBackgroundColor(bgColor)

        // 辅助按钮（右上角，对齐 iOS 顺序：@, space, delete 从左到右）
        btnAt = makeHelperBtn(R.drawable.ic_voice_at) { listener?.onInsertText("@") }
        btnSpace = makeHelperBtn(R.drawable.ic_voice_space) { listener?.onInsertText(" ") }
        btnDelete = makeHelperBtn(R.drawable.ic_voice_delete) { listener?.onDeleteBackward() }
        addView(btnDelete); addView(btnSpace); addView(btnAt)

        // 状态标签
        tvStatus = TextView(context).apply {
            textSize = 14f
            setTextColor(statusIdleColor)
            text = context.getString(R.string.click_to_speak)
            gravity = Gravity.CENTER
        }
        addView(tvStatus)

        // 光晕（静态灰色圆，不做脉冲动画，大小随 currentPower 自然变化）
        glowView = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(glowColor)
            }
            visibility = GONE
        }
        addView(glowView)

        // 主按钮（clipToOutline 让 Thinking 填充裁切到圆角内）
        micButton = FrameLayout(context).apply {
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }
        updateMicBackground(State.IDLE)

        micIcon = ImageView(context).apply {
            setImageResource(R.mipmap.aio_voice_button_icon)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        micButton.addView(micIcon, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // 圆点（7 个白色，录音初始静态显示，声音 >0.15 后切换为波形）
        dotsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = GONE
        }
        for (i in 0 until 7) {
            val dot = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                }
            }
            dotsContainer.addView(dot, LinearLayout.LayoutParams(dp(4), dp(4)).apply {
                if (i > 0) marginStart = dp(5)
            })
        }
        micButton.addView(dotsContainer, LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })

        // 波形（9 条白色柱体，在按钮内部）
        waveContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
            visibility = GONE
            // 用 padding 限制波形区域 ≈ 按钮 60%×50%
            val hPad = (kCircleBase * 0.2f).toInt()
            val vPad = (kCircleBase * 0.25f).toInt()
            setPadding(hPad, vPad, hPad, vPad)
        }
        for (i in 0 until 9) {
            val bar = View(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(1.5f)
                }
            }
            waveContainer.addView(bar, LinearLayout.LayoutParams(dp(3), dp(8)).apply {
                if (i > 0) marginStart = dp(3)
            })
            waveBars.add(bar)
        }
        micButton.addView(waveContainer, LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
        ))

        // Thinking 标签
        thinkingLabel = TextView(context).apply {
            text = context.getString(R.string.thinking_text)
            textSize = 14f
            setTextColor(Color.parseColor("#B3FFFFFF"))
            gravity = Gravity.CENTER
            visibility = GONE
        }
        micButton.addView(thinkingLabel, LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
        ))

        micButton.setOnClickListener { onMicTapped() }
        addView(micButton)

        // 底部按钮
        bottomButton = TextView(context).apply {
            text = context.getString(R.string.voice_newline)
            textSize = 14f
            setTextColor(statusIdleColor)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(helperBtnBg)
                cornerRadius = dp(16f)
            }
            setOnClickListener { onBottomButtonTapped() }
        }
        addView(bottomButton)

        // 初始尺寸
        animatedMicW = kPillWidth
        animatedMicH = kPillHeight
        applyIdleLayout()
    }

    // ───────── 背景管理（缓存，避免每次 onLayout 重建） ─────────
    private fun updateMicBackground(forState: State) {
        if (cachedBgState == forState && cachedBgDrawable != null) {
            micButton.background = cachedBgDrawable
            return
        }
        val d = GradientDrawable()
        when (forState) {
            State.RECORDING -> {
                d.shape = GradientDrawable.OVAL
                d.setColor(accentColor)
            }
            State.TRANSCRIBING -> {
                d.shape = GradientDrawable.RECTANGLE
                d.setColor(adjustAlpha(accentColor, 0.6f))
                d.cornerRadius = kPillHeight / 2f
            }
            State.IDLE -> {
                d.shape = GradientDrawable.RECTANGLE
                d.setColor(accentColor)
                d.cornerRadius = kPillHeight / 2f
            }
        }
        cachedBgState = forState
        cachedBgDrawable = d
        micButton.background = d
    }

    // ───────── 测量 & 布局 ─────────
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val w = MeasureSpec.getSize(widthMeasureSpec)

        // 辅助按钮
        val bs = kHelperBtnSize
        val btnSpec = MeasureSpec.makeMeasureSpec(bs, MeasureSpec.EXACTLY)
        btnAt.measure(btnSpec, btnSpec)
        btnSpace.measure(btnSpec, btnSpec)
        btnDelete.measure(btnSpec, btnSpec)

        // 状态标签
        tvStatus.measure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dp(20), MeasureSpec.AT_MOST)
        )

        // 主按钮（动画中用插值尺寸，否则用目标尺寸）
        val micW: Int
        val micH: Int
        if (isAnimating) {
            micW = animatedMicW; micH = animatedMicH
        } else {
            micW = targetMicWidth(); micH = targetMicHeight()
        }
        micButton.measure(
            MeasureSpec.makeMeasureSpec(micW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(micH, MeasureSpec.EXACTLY)
        )

        // 光晕
        if (glowView.visibility == VISIBLE) {
            val glowExtra = dp(20) + (currentPower * dp(15)).toInt()
            val glowSize = micW + glowExtra
            val glowSpec = MeasureSpec.makeMeasureSpec(glowSize, MeasureSpec.EXACTLY)
            glowView.measure(glowSpec, glowSpec)
        }

        // 底部按钮
        bottomButton.measure(
            MeasureSpec.makeMeasureSpec(kBottomBtnW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(kBottomBtnH, MeasureSpec.EXACTLY)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l

        // 辅助按钮（右上角）
        val bs = kHelperBtnSize
        val rm = kHelperRightMargin
        val tm = kHelperTopMargin
        val gap = kHelperGap
        placeChild(btnDelete, w - rm - bs, tm, bs, bs)
        placeChild(btnSpace, w - rm - bs - gap - bs, tm, bs, bs)
        placeChild(btnAt, w - rm - bs - gap - bs - gap - bs, tm, bs, bs)

        // 状态标签
        val statusY = if (state == State.RECORDING) kStatusRecordY else kStatusIdleY
        placeChild(tvStatus, 0, statusY, w, tvStatus.measuredHeight)

        // 主按钮
        val micW = micButton.measuredWidth
        val micH = micButton.measuredHeight
        placeChild(micButton, (w - micW) / 2, kMicY, micW, micH)

        // 动画中间态需要动态更新 cornerRadius
        if (isAnimating) {
            val d = micButton.background as? GradientDrawable
            d?.cornerRadius = micH / 2f
        }

        // 光晕
        if (glowView.visibility == VISIBLE) {
            val gW = glowView.measuredWidth
            val gH = glowView.measuredHeight
            placeChild(glowView, (w - gW) / 2, kMicY - (gH - micH) / 2, gW, gH)
        }

        // 底部按钮
        placeChild(bottomButton, (w - kBottomBtnW) / 2, kMicY + micH + kBottomGap, kBottomBtnW, kBottomBtnH)
    }

    private fun placeChild(v: View, x: Int, y: Int, w: Int, h: Int) {
        v.layout(x, y, x + w, y + h)
    }

    /** 目标按钮宽度（不含动画插值） */
    private fun targetMicWidth(): Int = when (state) {
        State.RECORDING -> ((kCircleBase * (1f + currentPower * 0.25f)).toInt())
        else -> kPillWidth
    }

    /** 目标按钮高度 */
    private fun targetMicHeight(): Int = when (state) {
        State.RECORDING -> ((kCircleBase * (1f + currentPower * 0.25f)).toInt())
        else -> kPillHeight
    }

    // 录音状态：扩大点击区域
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (state == State.RECORDING && ev.action == MotionEvent.ACTION_DOWN) {
            val cx = micButton.left + micButton.width / 2f
            val cy = micButton.top + micButton.height / 2f
            val r = dp(60f)
            val dx = ev.x - cx; val dy = ev.y - cy
            if (dx * dx + dy * dy <= r * r) return false
        }
        return super.onInterceptTouchEvent(ev)
    }

    // ───────── 状态机 + 过渡动画 ─────────
    private fun setState(newState: State) {
        val oldState = state
        state = newState

        // 先更新 visibility / text 等非尺寸属性
        when (newState) {
            State.IDLE -> applyIdleLayout()
            State.RECORDING -> applyRecordingLayout()
            State.TRANSCRIBING -> applyTranscribingLayout()
        }

        // 更新按钮背景
        updateMicBackground(newState)

        // 平滑尺寸过渡（对齐 iOS spring 0.3s, damping 0.8）
        if (oldState != newState) {
            animateTransition(oldState, newState)
        } else {
            requestLayout()
        }
    }

    /** 从 oldState 的按钮尺寸平滑过渡到 newState */
    private fun animateTransition(from: State, to: State) {
        transitionAnimator?.cancel()

        val fromW = animatedMicW
        val fromH = animatedMicH
        val toW = targetMicWidth()
        val toH = targetMicHeight()

        if (fromW == toW && fromH == toH) {
            requestLayout()
            return
        }

        transitionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                animatedMicW = (fromW + (toW - fromW) * f).toInt()
                animatedMicH = (fromH + (toH - fromH) * f).toInt()
                isAnimating = true

                // 动画中间态用 RECTANGLE + cornerRadius 实现 pill↔circle
                val d = micButton.background as? GradientDrawable
                if (d != null) {
                    d.shape = GradientDrawable.RECTANGLE
                    d.cornerRadius = animatedMicH / 2f
                }

                requestLayout()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isAnimating = false
                    animatedMicW = toW
                    animatedMicH = toH
                    updateMicBackground(to)
                    requestLayout()
                }
            })
            start()
        }
    }

    private fun applyIdleLayout() {
        micIcon.visibility = VISIBLE
        micIcon.setImageResource(R.mipmap.aio_voice_button_icon)
        micIcon.setPadding(dp(10), dp(10), dp(10), dp(10))
        micButton.isEnabled = true
        micButton.alpha = 1f

        tvStatus.text = context.getString(R.string.click_to_speak)
        tvStatus.setTextColor(statusIdleColor)
        tvStatus.paint.isFakeBoldText = false
        tvStatus.visibility = VISIBLE

        btnAt.visibility = VISIBLE; btnSpace.visibility = VISIBLE; btnDelete.visibility = VISIBLE
        bottomButton.visibility = VISIBLE
        bottomButton.text = context.getString(R.string.voice_newline)

        dotsContainer.visibility = GONE; waveContainer.visibility = GONE
        glowView.visibility = GONE; thinkingLabel.visibility = GONE
        stopThinkingAnimation()
        currentPower = 0f

        // 投递待发送的转写文本（对齐 iOS pendingTranscribeText 逻辑）
        val text = pendingTranscribeText
        val shouldReplace = pendingTranscribeShouldReplace
        if (!text.isNullOrEmpty()) {
            pendingTranscribeText = null
            pendingTranscribeShouldReplace = false
            listener?.onTranscribed(text, shouldReplace)
        }
    }

    private fun applyRecordingLayout() {
        micIcon.visibility = GONE
        micButton.isEnabled = true
        micButton.alpha = 1f

        tvStatus.text = context.getString(R.string.click_again_to_finish)
        tvStatus.setTextColor(statusRecordColor)
        tvStatus.paint.isFakeBoldText = true
        tvStatus.visibility = VISIBLE

        btnAt.visibility = GONE; btnSpace.visibility = GONE; btnDelete.visibility = GONE
        bottomButton.visibility = GONE
        thinkingLabel.visibility = GONE
        glowView.visibility = VISIBLE

        hasReceivedAudio = false
        dotsContainer.visibility = VISIBLE; waveContainer.visibility = GONE
        stopThinkingAnimation()
    }

    private fun applyTranscribingLayout() {
        micIcon.visibility = GONE
        micButton.isEnabled = false
        micButton.alpha = 1f

        tvStatus.visibility = GONE
        btnAt.visibility = GONE; btnSpace.visibility = GONE; btnDelete.visibility = GONE
        bottomButton.visibility = GONE
        waveContainer.visibility = GONE; dotsContainer.visibility = GONE; glowView.visibility = GONE

        thinkingLabel.visibility = VISIBLE
        startThinkingAnimation()
    }

    private fun adjustAlpha(color: Int, alpha: Float): Int {
        val a = (Color.alpha(color) * alpha).toInt()
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    // ───────── 按钮事件 ─────────
    private fun onMicTapped() {
        when (state) {
            State.IDLE -> startRecording()
            State.RECORDING -> stopRecordingAndTranscribe()
            State.TRANSCRIBING -> { /* ignore */ }
        }
    }

    private fun onBottomButtonTapped() {
        when (state) {
            State.RECORDING -> cancelRecording()
            State.IDLE -> listener?.onInsertText("\n")
            State.TRANSCRIBING -> { /* ignore */ }
        }
    }

    // ───────── 录音 ─────────
    private fun startRecording() {
        val tempDir = context.externalCacheDir ?: context.cacheDir
        recordFilePath = File(tempDir, "voice_input_${System.currentTimeMillis()}.m4a").absolutePath

        try {
            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            ).apply {
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
        currentPower = 0f
        maxAmpZeroCount = 0
        useAudioRecordMetering = false
        setState(State.RECORDING)
        listener?.onRecordingStarted()

        recordTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    mainHandler.post {
                        recordSeconds++
                        if (recordSeconds >= maxDuration) stopRecordingAndTranscribe()
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

    /** 启动 AudioRecord 备用计量（当 MediaRecorder.getMaxAmplitude 失效时） */
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
            if (ar.state != AudioRecord.STATE_INITIALIZED) {
                ar.release(); return
            }
            ar.startRecording()
            audioRecord = ar

            meteringThread = Thread {
                val buf = ShortArray(bufSize / 2)
                while (!Thread.currentThread().isInterrupted) {
                    val read = try { ar.read(buf, 0, buf.size) } catch (_: Exception) { break }
                    if (read > 0) {
                        var maxVal = 0
                        for (j in 0 until read) {
                            val v = Math.abs(buf[j].toInt())
                            if (v > maxVal) maxVal = v
                        }
                        meteringAmplitude = maxVal
                    }
                }
            }.apply { isDaemon = true; name = "VoiceMetering"; start() }
            // fallback metering started
        } catch (e: Exception) {
            // AudioRecord metering not available on this device
        }
    }

    private fun stopAudioRecordMetering() {
        meteringThread?.interrupt()
        meteringThread = null
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        meteringAmplitude = 0
    }

    private fun stopRecordingAndTranscribe() {
        invalidateTimers()
        stopAudioRecordMetering()
        val recorder = mediaRecorder ?: return
        val duration = recordSeconds

        try { recorder.stop(); recorder.release() } catch (_: Exception) {}
        mediaRecorder = null
        listener?.onRecordingStopped()

        if (duration < 1) {
            Toast.makeText(context, R.string.speech_too_short, Toast.LENGTH_SHORT).show()
            setState(State.IDLE); cleanupRecordFile(); return
        }

        val audioFile = File(recordFilePath ?: return)
        if (!audioFile.exists() || audioFile.length() == 0L) {
            setState(State.IDLE); cleanupRecordFile(); return
        }

        val contextText = listener?.getCurrentInputText()
        val chatContext = listener?.getChatContext()
        setState(State.TRANSCRIBING)

        WKVoiceInputService.instance.transcribeAudio(audioFile, contextText, chatContext) { result, error ->
            cleanupRecordFile()
            if (error != null || result == null || result.text.isEmpty()) {
                Toast.makeText(context, R.string.speech_recognize_failed, Toast.LENGTH_SHORT).show()
                setState(State.IDLE); return@transcribeAudio
            }
            pendingTranscribeText = result.text
            pendingTranscribeShouldReplace = !contextText.isNullOrEmpty()
            completeThinkingAnimation()
        }
    }

    fun cancelRecording() {
        invalidateTimers()
        stopAudioRecordMetering()
        try { mediaRecorder?.stop(); mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null; cleanupRecordFile()
        listener?.onRecordingStopped(); setState(State.IDLE)
    }

    fun cancelIfRecording() { if (state == State.RECORDING) cancelRecording() }

    private fun invalidateTimers() {
        recordTimer?.cancel(); recordTimer = null
        waveformTimer?.cancel(); waveformTimer = null
    }

    private fun cleanupRecordFile() {
        recordFilePath?.let { File(it).delete(); recordFilePath = null }
    }

    // ───────── 波形（对齐 iOS updateWaveform） ─────────
    private var prevPower = -1f
    private var smoothedPower = 0f // EMA 平滑后的 power
    // 每条 bar 的当前动画目标高度，用于平滑过渡
    private val barCurrentH = FloatArray(9) { 0f }

    private fun updateWaveform() {
        if (state != State.RECORDING || mediaRecorder == null) return
        try {
            // 优先用 MediaRecorder.getMaxAmplitude，连续 10 次返回 0 则切换到 AudioRecord
            val amplitude: Int
            if (!useAudioRecordMetering) {
                val mrAmp = mediaRecorder?.maxAmplitude ?: 0
                if (mrAmp == 0) {
                    maxAmpZeroCount++
                    if (maxAmpZeroCount >= 10) {
                        useAudioRecordMetering = true
                        startAudioRecordMetering()
                    }
                } else {
                    maxAmpZeroCount = 0
                }
                amplitude = mrAmp
            } else {
                // AudioRecord 备用计量
                amplitude = meteringAmplitude
            }

            // dB 归一化：amplitude 0~32767 → dB → 0~1
            var rawPower = if (amplitude > 0) {
                val db = 20 * Math.log10(amplitude / 32767.0)
                ((db + 50.0) / 50.0).toFloat().coerceIn(0f, 1f)
            } else 0f

            if (rawPower < 0.06f) rawPower = 0f

            // EMA 指数平滑（模拟 iOS animateWithDuration:0.1 的丝滑感）
            smoothedPower = if (rawPower > smoothedPower) {
                smoothedPower * 0.6f + rawPower * 0.4f  // 上升：慢平滑
            } else {
                smoothedPower * 0.4f + rawPower * 0.6f  // 下降：快衰减
            }
            if (smoothedPower < 0.02f) smoothedPower = 0f

            currentPower = smoothedPower

            // 有声音时从圆点→波形（用 smoothed 值判断，避免噪声误触发）
            if (!hasReceivedAudio && smoothedPower > 0.15f) {
                hasReceivedAudio = true
                dotsContainer.visibility = GONE
                waveContainer.visibility = VISIBLE
            }

            // 只在 power 真正变化时才 requestLayout
            val powerChanged = Math.abs(smoothedPower - prevPower) > 0.01f
            prevPower = smoothedPower
            if (powerChanged) requestLayout()

            // 更新波形柱体高度（带平滑过渡，对齐 iOS animateWithDuration:0.1）
            if (waveContainer.visibility == VISIBLE) {
                val availH = waveContainer.height - waveContainer.paddingTop - waveContainer.paddingBottom
                if (availH > 0) {
                    val baseH = dp(3).toFloat()
                    for ((i, bar) in waveBars.withIndex()) {
                        val targetH = if (smoothedPower == 0f) baseH
                        else {
                            val variation = 0.3f + (Math.random().toFloat() * 0.7f)
                            (baseH + smoothedPower * availH * 0.8f * variation)
                                .coerceIn(baseH, availH.toFloat())
                        }
                        // 平滑插值：每 tick 向目标值靠近 40%（模拟 iOS 0.1s 动画）
                        barCurrentH[i] = barCurrentH[i] + (targetH - barCurrentH[i]) * 0.4f
                        val h = barCurrentH[i].toInt().coerceAtLeast(baseH.toInt())
                        val lp = bar.layoutParams
                        if (lp != null && lp.height != h) {
                            lp.height = h
                            bar.layoutParams = lp
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // ───────── Thinking 渐进填充 ─────────
    private fun startThinkingAnimation() {
        stopThinkingAnimation()
        val fill = View(context).apply {
            background = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = kPillHeight / 2f
            }
        }
        micButton.addView(fill, 0, LayoutParams(0, kPillHeight))
        thinkingFillView = fill

        thinkingProgress = 0f
        thinkingCompleting = false

        thinkingTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() { mainHandler.post { tickThinkingProgress() } }
            }, 60, 60)
        }
    }

    private fun tickThinkingProgress() {
        if (thinkingCompleting) {
            val remaining = 1f - thinkingProgress
            thinkingProgress += remaining * 0.15f
            if (thinkingProgress >= 0.995f) {
                thinkingProgress = 1f
                thinkingTimer?.cancel(); thinkingTimer = null
                updateThinkingFillWidth()
                mainHandler.postDelayed({ setState(State.IDLE) }, 150)
                return
            }
        } else if (thinkingProgress < 0.8f) {
            val baseStep = 0.04f + (Math.random().toFloat() * 0.03f)
            val slowdown = 1f - (thinkingProgress / 0.8f) * 0.5f
            thinkingProgress = (thinkingProgress + baseStep * slowdown).coerceAtMost(0.8f)
        } else if (thinkingProgress < 0.95f) {
            if (Math.random() > 0.3) {
                val step = 0.002f + (Math.random().toFloat() * 0.003f)
                thinkingProgress = (thinkingProgress + step).coerceAtMost(0.95f)
            }
        }
        updateThinkingFillWidth()
    }

    private fun updateThinkingFillWidth() {
        val fill = thinkingFillView ?: return
        val targetWidth = (kPillWidth * thinkingProgress).toInt()
        val lp = fill.layoutParams as? LayoutParams ?: return
        if (lp.width != targetWidth) {
            lp.width = targetWidth
            fill.layoutParams = lp
        }
    }

    private fun completeThinkingAnimation() { thinkingCompleting = true }

    private fun stopThinkingAnimation() {
        thinkingTimer?.cancel(); thinkingTimer = null
        thinkingFillView?.let { micButton.removeView(it) }
        thinkingFillView = null; thinkingProgress = 0f; thinkingCompleting = false
    }

    // ───────── 辅助方法 ─────────
    private fun makeHelperBtn(iconRes: Int, onClick: () -> Unit): ImageView {
        return ImageView(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(helperBtnBg)
            }
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { onClick() }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
