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
        private const val TRANSCRIBE_TIMEOUT = 30_000L // 30 seconds
        private const val MAX_FILE_SIZE = 5 * 1024 * 1024L // 5MB
    }

    private var cachedConfig: WKVoiceInputConfig? = null
    private var cachedAt: Long = 0

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

    fun transcribeAudio(
        audioFile: File,
        contextText: String?,
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
