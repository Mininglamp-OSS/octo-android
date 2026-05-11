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

        // YUJ-420 R6 fix (Jerry R3 Critical / lml2468 R2 Blocker 9): Space 为空 / 切换 Space / logout
        // 时必须清旧缓存, 避免 getVoiceContext() 旧 Space 的 personal_context 泄漏给新 Space 的请求。
        if (spaceId.isNullOrEmpty()) {
            invalidateVoiceContextCache()
            return
        }
        if (voiceContextSpaceId != null && voiceContextSpaceId != spaceId) {
            invalidateVoiceContextCache()
        }

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
                    // 仅在 inflight spaceId 仍是本请求的 spaceId 时才重置,
                    // 避免并发 space switch 后的 late failure 清掉20新 Space 的 inflight.
                    if (voiceContextSpaceId == spaceId) {
                        voiceContextInflight = false
                        cachedVoiceContext = null
                        flushVoiceContextCallbacks(null)
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = try { response.body?.string() ?: "{}" } catch (_: Exception) { "{}" }
                mainHandler.post {
                    // YUJ-420 R7 fix (Jerry R4 加强): 双重校验异步回包对应的仍是:
                    // (1) 当前 inflight 的 spaceId 仍是 requesting-time 的 spaceId
                    // (2) currentSpaceId (SpaceFilter 现值) 也仍是 requesting-time 的 spaceId
                    // 两个条件同时成立才写缓存, 避免三态切换 (Space-1→Space-2→Space-3) 竞态中
                    // invalidateCache 暂时为 null 的 gap 被 late response 塑充的问题。
                    val currentSpaceId = com.chat.base.space.SpaceFilter.getCurrentSpaceId()
                    if (voiceContextSpaceId != spaceId || currentSpaceId != spaceId) return@post
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

    /**
     * YUJ-420 R6: Space 切换 / logout 时清 voice context 缓存,
     * 避免旧 Space personal_context 被新 Space 的转写请求误作为 personal_context 上传。
     *
     * 可被 SpaceChangedBroadcaster 订阅方或 LoginModel.logout() 调用。
     */
    fun invalidateVoiceContextCache() {
        cachedVoiceContext = null
        voiceContextCachedAt = 0
        voiceContextSpaceId = null
        voiceContextInflight = false
        // pending callbacks 不 flush null 给他们, 让新 prefetch 开启新轮时再分发
        voiceContextPendingCallbacks.clear()
    }

    fun getVoiceContext(completion: (String?) -> Unit) {
        // YUJ-420 R6 fix (Jerry R3 Critical / lml2468 R2 Blocker 9):
        // 校验 (1) 缓存的 spaceId 必须等于当前 Space (2) 未过 TTL.
        // 任一不满足 → return null (避免跨 Space 数据泄漏), 并顺手 invalidate 隔离胏脈。
        val currentSpaceId = com.chat.base.space.SpaceFilter.getCurrentSpaceId()
        val cachedForSameSpace = voiceContextSpaceId != null &&
                voiceContextSpaceId == currentSpaceId &&
                !currentSpaceId.isNullOrEmpty()
        val withinTtl = (System.currentTimeMillis() - voiceContextCachedAt) < VOICE_CONTEXT_CACHE_TTL

        if (!voiceContextInflight) {
            if (cachedForSameSpace && withinTtl) {
                completion(cachedVoiceContext)
            } else {
                if (!cachedForSameSpace) {
                    // Space 已切换 (或 spaceId 空) → 主动清旧 Space 缓存
                    invalidateVoiceContextCache()
                }
                completion(null)
            }
            return
        }
        // inflight 中: 确认 inflight 的也是当前 Space, 否则丟回 null 并 invalidate
        if (!cachedForSameSpace) {
            invalidateVoiceContextCache()
            completion(null)
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
