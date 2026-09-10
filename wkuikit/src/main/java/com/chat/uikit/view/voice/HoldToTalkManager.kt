/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.uikit.view.voice

import android.app.Activity
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import com.chat.base.net.voice.WKVoiceInputService
import com.chat.base.utils.AndroidUtilities
import com.chat.base.utils.WKToastUtils
import com.chat.uikit.R
import java.io.File
import java.util.Timer
import java.util.TimerTask
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

class HoldToTalkManager(private val activity: Activity) {

    enum class State { IDLE, RECORDING, THINKING, RESULT }

    interface Listener {
        fun onSendText(text: String)
        fun onSendVoice(audioPath: String, seconds: Int, waveform: String)
        fun onRecordingStarted()
        fun onRecordingStopped()
        fun getChatContext(): String?
        fun onShowResultUI(text: String)
        fun onDismissResultUI()
        fun onAppendText(text: String)
        fun onAppendThinkingStart()
        fun onAppendThinkingEnd()
    }

    var listener: Listener? = null
    var state = State.IDLE
        private set

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var recordTimer: Timer? = null
    private var waveformTimer: Timer? = null
    private var recordDuration = 0
    private val maxDuration = 60
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: HoldToTalkOverlayView? = null

    var smoothedPower = 0f
        private set
    private var maxAmpZeroCount = 0
    private var useAudioRecordMetering = false
    private var meteringRecord: AudioRecord? = null
    private var meteringThread: Thread? = null
    @Volatile private var meteringRunning = false
    @Volatile private var meteringAmplitude = 0

    private val waveformLevels = mutableListOf<Float>()

    // 对齐 iOS WKHoldToTalkManager.transcribedText：context_text 只用本次语音会话
    // 已转写出的文本（连续追加说话场景），与外部输入框草稿无关。
    private var transcribedText: String? = null

    // 追加录音的会话世代号：每次开始新的一轮语音会话（含 startRecording/cancelResult/
    // sendResultText）都递增，追加识别回调据此判断响应是否仍属于当前会话——纯 state 判断
    // 不够，因为迟到响应落地时新会话可能同样合法地处于 State.RESULT。
    private var appendSession = 0

    fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (state == State.IDLE) {
                    startRecording()
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (state == State.RECORDING) {
                    overlayView?.updateDragPosition(event.rawX, event.rawY)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (state == State.RECORDING) {
                    handleRelease()
                    return true
                }
            }
        }
        return false
    }

    fun cancelRecording() {
        if (state == State.RECORDING) {
            stopRecordingInternal()
            dismissOverlay()
            cleanupAudioFile()
            state = State.IDLE
            listener?.onRecordingStopped()
        }
    }

    fun dismissAll() {
        when (state) {
            State.RECORDING -> cancelRecording()
            State.THINKING -> {
                dismissOverlay()
                stopRecordingInternal()
                cleanupAudioFile()
                state = State.IDLE
            }
            State.RESULT -> cancelResult()
            State.IDLE -> {}
        }
    }

    private fun startRecording() {
        val file = File(activity.cacheDir, "hold_talk_${System.currentTimeMillis()}.amr")
        audioFile = file

        try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            WKToastUtils.getInstance().showToastNormal(activity.getString(R.string.voice_record_failed))
            cleanupAudioFile()
            return
        }

        transcribedText = null
        appendSession++
        state = State.RECORDING
        recordDuration = 0
        smoothedPower = 0f
        maxAmpZeroCount = 0
        useAudioRecordMetering = false
        waveformLevels.clear()

        showOverlay()
        startTimers()
        listener?.onRecordingStarted()

        WKVoiceInputService.instance.prefetchVoiceContext()
    }

    private fun handleRelease() {
        val dragState = overlayView?.getDragState() ?: HoldToTalkOverlayView.DragState.RECORDING
        stopRecordingInternal()

        when (dragState) {
            HoldToTalkOverlayView.DragState.CANCEL -> {
                dismissOverlay()
                cleanupAudioFile()
                state = State.IDLE
            }
            HoldToTalkOverlayView.DragState.SEND_VOICE -> {
                dismissOverlay()
                if (recordDuration < 1) {
                    WKToastUtils.getInstance().showToastNormal(activity.getString(R.string.voice_too_short))
                    cleanupAudioFile()
                    state = State.IDLE
                } else {
                    val path = audioFile?.absolutePath ?: ""
                    val waveformBytes = ByteArray(waveformLevels.size) { i ->
                        val db = (waveformLevels[i] * 50).toInt().coerceIn(0, 127).toByte()
                        if (db.toInt() == 0) 2.toByte() else db
                    }
                    val waveform = android.util.Base64.encodeToString(waveformBytes, android.util.Base64.NO_WRAP)
                    state = State.IDLE
                    audioFile = null
                    listener?.onSendVoice(path, recordDuration, waveform)
                }
            }
            HoldToTalkOverlayView.DragState.RECORDING -> {
                if (recordDuration < 1) {
                    dismissOverlay()
                    WKToastUtils.getInstance().showToastNormal(activity.getString(R.string.voice_too_short))
                    cleanupAudioFile()
                    state = State.IDLE
                } else {
                    startTranscription()
                }
            }
        }
        listener?.onRecordingStopped()
    }

    private fun startTranscription() {
        state = State.THINKING
        overlayView?.showThinking()

        val file = audioFile ?: run {
            WKToastUtils.getInstance().showToastNormal(activity.getString(R.string.voice_file_error))
            dismissOverlay()
            state = State.IDLE
            return
        }

        // context_text: 只用本次语音会话已转写的文本，不读外部输入框草稿（对齐 iOS）
        val contextText = transcribedText
        val fullContext = listener?.getChatContext()

        // 拆分 fullContext：聊天成员：xxx\n[发送者]: yyy → memberContext + chatContext
        var memberContext: String? = null
        var chatContext: String? = null
        if (fullContext != null && fullContext.startsWith("聊天成员：")) {
            val newlineIdx = fullContext.indexOf('\n')
            if (newlineIdx > 0) {
                memberContext = fullContext.substring(0, newlineIdx)
                chatContext = fullContext.substring(newlineIdx + 1)
            } else {
                memberContext = fullContext
            }
        } else {
            chatContext = fullContext
        }

        WKVoiceInputService.instance.getVoiceContext { personalContext ->
            WKVoiceInputService.instance.transcribeAudio(file, contextText, chatContext, personalContext, memberContext) { result, error ->
                if (state != State.THINKING) return@transcribeAudio
                if (error != null || result == null || result.text.isEmpty()) {
                    WKToastUtils.getInstance().showToastNormal(activity.getString(R.string.voice_recognize_failed_retry))
                    dismissOverlay()
                    cleanupAudioFile()
                    state = State.IDLE
                    return@transcribeAudio
                }

                transcribedText = result.text
                dismissOverlay()
                showResult(result.text)
            }
        }
    }

    private fun showResult(text: String) {
        state = State.RESULT
        listener?.onShowResultUI(text)
    }

    /**
     * 用户手动编辑结果气泡文本时调用，同步为下一次追加录音的 context_text（对齐 iOS
     * textViewDidChange 回写 self.transcribedText）。
     */
    fun updateTranscribedText(text: String) {
        transcribedText = text
    }

    fun sendResultText(text: String) {
        state = State.IDLE
        listener?.onDismissResultUI()
        listener?.onSendText(text)
        transcribedText = null
        appendSession++
        cleanupAudioFile()
    }

    fun cancelResult() {
        state = State.IDLE
        listener?.onDismissResultUI()
        transcribedText = null
        appendSession++
        cleanupAudioFile()
    }

    fun startAppendRecording() {
        val file = File(activity.cacheDir, "hold_talk_append_${System.currentTimeMillis()}.amr")
        audioFile = file

        try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recordDuration = 0
            smoothedPower = 0f
            startTimers()
            listener?.onRecordingStarted()
        } catch (e: Exception) {
            WKToastUtils.getInstance().showToastNormal(activity.getString(R.string.voice_record_failed))
        }
    }

    fun stopAppendRecording() {
        stopRecordingInternal()
        listener?.onRecordingStopped()

        if (recordDuration < 1) {
            WKToastUtils.getInstance().showToastNormal(activity.getString(R.string.voice_too_short))
            cleanupAudioFile()
            return
        }

        listener?.onAppendThinkingStart()

        val file = audioFile ?: return
        val session = appendSession
        // context_text: 只用本次语音会话已转写的文本，不读外部输入框草稿（对齐 iOS）
        val contextText = transcribedText
        val fullContext = listener?.getChatContext()

        var memberContext: String? = null
        var chatContext: String? = null
        if (fullContext != null && fullContext.startsWith("聊天成员：")) {
            val newlineIdx = fullContext.indexOf('\n')
            if (newlineIdx > 0) {
                memberContext = fullContext.substring(0, newlineIdx)
                chatContext = fullContext.substring(newlineIdx + 1)
            } else {
                memberContext = fullContext
            }
        } else {
            chatContext = fullContext
        }

        WKVoiceInputService.instance.getVoiceContext { personalContext ->
            WKVoiceInputService.instance.transcribeAudio(file, contextText, chatContext, personalContext, memberContext) { result, error ->
                // session 校验：state==RESULT 不够，因为响应落地时新会话可能同样合法地
                // 处于 RESULT——必须比对会话世代号，确保响应仍属于发起它的那一轮会话。
                if (session != appendSession) return@transcribeAudio
                listener?.onAppendThinkingEnd()
                if (error != null || result == null || result.text.isEmpty()) {
                    WKToastUtils.getInstance().showToastNormal(activity.getString(R.string.voice_recognize_failed))
                    cleanupAudioFile()
                    return@transcribeAudio
                }
                // 追加录音必带上下文，服务端已基于 transcribedText 续接返回，直接替换（对齐 iOS）
                transcribedText = result.text
                listener?.onAppendText(result.text)
                cleanupAudioFile()
            }
        }
    }

    fun cancelAppendRecording() {
        stopRecordingInternal()
        listener?.onRecordingStopped()
        cleanupAudioFile()
    }

    private fun showOverlay() {
        overlayView = HoldToTalkOverlayView(activity).apply {
            listener = object : HoldToTalkOverlayView.Listener {
                override fun onDragStateChanged(state: HoldToTalkOverlayView.DragState) {
                    // Visual feedback handled by overlay
                }
            }
        }
        val contentView = activity.findViewById<FrameLayout>(android.R.id.content)
        contentView.addView(overlayView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    private fun dismissOverlay() {
        overlayView?.dismiss()
        overlayView = null
    }


    private fun startTimers() {
        recordTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    recordDuration++
                    if (recordDuration >= maxDuration && state == State.RECORDING) {
                        mainHandler.post { handleRelease() }
                    }
                }
            }, 1000, 1000)
        }

        waveformTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    mainHandler.post { updateWaveform() }
                }
            }, 100, 100)
        }
    }

    private fun stopTimers() {
        recordTimer?.cancel()
        recordTimer = null
        waveformTimer?.cancel()
        waveformTimer = null
    }

    private fun updateWaveform() {
        val amplitude = try {
            if (useAudioRecordMetering) {
                meteringAmplitude
            } else {
                mediaRecorder?.maxAmplitude ?: 0
            }
        } catch (e: Exception) { 0 }

        if (!useAudioRecordMetering && amplitude == 0) {
            maxAmpZeroCount++
            if (maxAmpZeroCount >= 10) {
                useAudioRecordMetering = true
                startAudioRecordMetering()
            }
        }

        val rawPower = if (amplitude > 0) {
            val db = 20 * log10(amplitude.toDouble() / 32767.0)
            val norm = ((db + 40) / 40.0).toFloat().coerceIn(0f, 1f)
            if (norm < 0.08f) 0f else norm
        } else 0f

        smoothedPower = if (rawPower > smoothedPower) {
            smoothedPower * 0.6f + rawPower * 0.4f
        } else {
            smoothedPower * 0.2f + rawPower * 0.8f
        }

        waveformLevels.add(smoothedPower)
        overlayView?.updateAmplitude(smoothedPower)
    }

    private fun startAudioRecordMetering() {
        try {
            val sampleRate = 16000
            val bufSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            meteringRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize
            )
            meteringRecord?.startRecording()

            meteringRunning = true
            meteringThread = Thread {
                val buffer = ShortArray(bufSize / 2)
                while (meteringRunning && meteringRecord != null) {
                    try {
                        val read = meteringRecord?.read(buffer, 0, buffer.size) ?: break
                        if (read > 0) {
                            var maxVal = 0
                            for (i in 0 until read) {
                                val v = abs(buffer[i].toInt())
                                if (v > maxVal) maxVal = v
                            }
                            meteringAmplitude = maxVal
                        }
                        Thread.sleep(80)
                    } catch (_: Exception) {
                        break
                    }
                }
            }
            meteringThread?.start()
        } catch (_: Exception) {}
    }

    private fun stopAudioRecordMetering() {
        meteringRunning = false
        meteringThread = null
        try {
            meteringRecord?.stop()
            meteringRecord?.release()
        } catch (_: Exception) {}
        meteringRecord = null
    }

    private fun stopRecordingInternal() {
        stopTimers()
        stopAudioRecordMetering()
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
    }

    private fun cleanupAudioFile() {
        audioFile?.let { if (it.exists()) it.delete() }
        audioFile = null
    }

    fun release() {
        dismissAll()
        stopRecordingInternal()
        cleanupAudioFile()
        listener = null
    }
}
