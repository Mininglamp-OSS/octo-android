package com.chat.base.net.voice

import android.os.Handler
import android.os.Looper
import com.chat.base.config.WKApiConfig
import com.chat.base.net.OkHttpUtils
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class WKVoiceInputConfig(
    val enabled: Boolean,
    val maxDuration: Int
)

data class WKVoiceInputResult(
    val text: String,
    val model: String
)

class WKVoiceInputService private constructor() {

    fun interface ConfigCallback {
        fun onResult(config: WKVoiceInputConfig?, error: Exception?)
    }

    fun interface TranscribeCallback {
        fun onResult(result: WKVoiceInputResult?, error: Exception?)
    }

    companion object {
        @JvmStatic
        val instance: WKVoiceInputService by lazy { WKVoiceInputService() }

        private const val CONFIG_CACHE_TTL = 300_000L // 5 minutes
        private const val VOICE_CONTEXT_CACHE_TTL = 300_000L // 5 minutes
        private const val VOICE_CONTEXT_TIMEOUT = 5_000L // 5 seconds
        private const val TRANSCRIBE_TIMEOUT = 30_000L // 30 seconds
        private const val MAX_FILE_SIZE = 5 * 1024 * 1024L // 5MB
    }

    private var cachedConfig: WKVoiceInputConfig? = null
    private var cachedAt: Long = 0

    private var cachedVoiceContext: String? = null
    private var voiceContextCachedAt: Long = 0
    private var voiceContextSpaceId: String? = null
    private var voiceContextInflight: Boolean = false
    private val voiceContextPendingCallbacks = mutableListOf<(String?) -> Unit>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val client: OkHttpClient
        get() = OkHttpUtils.getInstance().okHttpClient

    fun fetchConfig(completion: ConfigCallback?) {
        val cached = cachedConfig
        if (cached != null && (System.currentTimeMillis() - cachedAt) < CONFIG_CACHE_TTL) {
            mainHandler.post { completion?.onResult(cached, null) }
            return
        }

        val url = WKApiConfig.baseUrl + "voice/config"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { completion?.onResult(null, e) }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: "{}"
                    val json = JSONObject(body)
                    val config = WKVoiceInputConfig(
                        enabled = json.optBoolean("enabled", false),
                        maxDuration = json.optInt("max_duration", 60).let { if (it <= 0) 60 else it }
                    )
                    cachedConfig = config
                    cachedAt = System.currentTimeMillis()
                    mainHandler.post { completion?.onResult(config, null) }
                } catch (e: Exception) {
                    mainHandler.post { completion?.onResult(null, e) }
                }
            }
        })
    }

    fun prefetchConfig() {
        fetchConfig(null)
    }

    fun clearConfigCache() {
        cachedConfig = null
        cachedAt = 0
    }

    fun prefetchVoiceContext() {
        // YUJ-420 R3 fix (lml2468 R1 Blocker): 原代码读 android.preference.PreferenceManager 的
        // "currentSpaceId" 键, 但全库其它 Space 隔离代码走 WKSharedPreferencesUtil + SPWithUID
        // 的 "current_space_id" 键 (见 SpaceFilter.getCurrentSpaceId())。
        // 键名不匹配导致此处永远读不到 spaceId 早返回,
        // personal_context 预取路径活不起来。统一改为 SpaceFilter.getCurrentSpaceId()。
        val spaceId = com.chat.base.space.SpaceFilter.getCurrentSpaceId()
        if (spaceId.isNullOrEmpty()) return

        if (cachedVoiceContext != null &&
            voiceContextSpaceId == spaceId &&
            (System.currentTimeMillis() - voiceContextCachedAt) < VOICE_CONTEXT_CACHE_TTL) {
            return
        }

        if (voiceContextInflight && voiceContextSpaceId == spaceId) return

        voiceContextInflight = true
        voiceContextSpaceId = spaceId

        mainHandler.postDelayed({
            if (voiceContextInflight && voiceContextSpaceId == spaceId) {
                voiceContextInflight = false
                flushVoiceContextCallbacks(null)
            }
        }, VOICE_CONTEXT_TIMEOUT)

        val url = WKApiConfig.baseUrl + "voice/context?space_id=" +
                android.net.Uri.encode(spaceId)
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    voiceContextInflight = false
                    cachedVoiceContext = null
                    flushVoiceContextCallbacks(null)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = try { response.body?.string() ?: "{}" } catch (_: Exception) { "{}" }
                mainHandler.post {
                    try {
                        val json = JSONObject(bodyStr)
                        val hasContext = json.optBoolean("has_context", false)
                        val context = json.optString("context", "")
                        cachedVoiceContext = if (hasContext && context.isNotEmpty()) context else null
                        voiceContextCachedAt = System.currentTimeMillis()
                    } catch (_: Exception) {
                        cachedVoiceContext = null
                    }
                    voiceContextInflight = false
                    flushVoiceContextCallbacks(cachedVoiceContext)
                }
            }
        })
    }

    fun getVoiceContext(completion: (String?) -> Unit) {
        if (!voiceContextInflight) {
            completion(cachedVoiceContext)
            return
        }
        voiceContextPendingCallbacks.add(completion)
    }

    private fun flushVoiceContextCallbacks(context: String?) {
        val callbacks = voiceContextPendingCallbacks.toList()
        voiceContextPendingCallbacks.clear()
        callbacks.forEach { it(context) }
    }

    fun transcribeAudio(
        audioFile: File,
        contextText: String?,
        chatContext: String? = null,
        personalContext: String? = null,
        memberContext: String? = null,
        completion: TranscribeCallback
    ) {
        if (audioFile.length() > MAX_FILE_SIZE) {
            mainHandler.post {
                completion.onResult(null, Exception("Audio file too large"))
            }
            return
        }

        val url = WKApiConfig.baseUrl + "voice/transcribe"

        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "audio",
                "recording.m4a",
                audioFile.asRequestBody("audio/mp4".toMediaType())
            )

        if (!contextText.isNullOrEmpty()) {
            bodyBuilder.addFormDataPart("context_text", contextText)
        }
        if (!chatContext.isNullOrEmpty()) {
            bodyBuilder.addFormDataPart("chat_context", chatContext)
        }
        if (!personalContext.isNullOrEmpty()) {
            bodyBuilder.addFormDataPart("personal_context", personalContext)
        }
        if (!memberContext.isNullOrEmpty()) {
            bodyBuilder.addFormDataPart("member_context", memberContext)
        }

        val request = Request.Builder()
            .url(url)
            .post(bodyBuilder.build())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { completion.onResult(null, e) }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: "{}"
                    val json = JSONObject(body)
                    val status = json.optInt("status", 0)
                    if (status != 200) {
                        val msg = json.optString("msg", "transcription failed")
                        mainHandler.post { completion.onResult(null, Exception(msg)) }
                        return
                    }
                    val result = WKVoiceInputResult(
                        text = json.optString("text", ""),
                        model = json.optString("model", "")
                    )
                    mainHandler.post { completion.onResult(result, null) }
                } catch (e: Exception) {
                    mainHandler.post { completion.onResult(null, e) }
                }
            }
        })
    }
}
