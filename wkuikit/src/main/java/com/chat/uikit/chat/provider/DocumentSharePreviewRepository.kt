package com.chat.uikit.chat.provider

import com.chat.base.config.WKApiConfig
import com.chat.base.config.WKConfig
import com.chat.uikit.chat.msgmodel.WKDocumentShareContent
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URI
import java.util.IdentityHashMap
import java.util.concurrent.TimeUnit

class DocumentSharePreviewRepository(
    private val transport: Transport = OkHttpTransport(),
    private val now: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val session: () -> Session = { WKConfig.getInstance().getSessionSnapshot().let { Session(it.uid.orEmpty(), it.token.orEmpty(), it.version) } },
    private val config: ConfigSource = ConfigSource { WKApiConfig.baseUrl to WKApiConfig.baseWebUrl },
    private val sessionEvents: SessionEvents = DefaultSessionEvents(),
    private val launch: ((() -> Unit) -> Unit) = { task -> Thread(task).start() },
    private val beforeCachedDelivery: () -> Unit = {}
) : AutoCloseable {
    enum class State { loading, denied, unavailable, error, empty, ready }
    data class Session(val uid: String, val token: String, val version: Long = 0)
    data class Context(val version: Long, val uid: String, val token: String, val apiBaseUrl: String, val webOrigin: String?)
    data class Result(val state: State, val preview: DocumentSharePreviewParser.Preview? = null, val context: Context? = null)
    interface Transport { fun get(url: String, token: String, spaceId: String, callback: (Int, String?) -> Unit) }
    interface Subscription { fun cancel() }
    fun interface ConfigSource { fun origins(): Pair<String, String> }
    interface SessionEvents { fun add(listener: () -> Unit); fun remove(listener: () -> Unit) }
    private class DefaultSessionEvents : SessionEvents {
        private val map = IdentityHashMap<() -> Unit, WKConfig.SessionListener>()
        override fun add(listener: () -> Unit) { val value = WKConfig.SessionListener { listener() }; map[listener] = value; WKConfig.getInstance().addSessionListener(value) }
        override fun remove(listener: () -> Unit) { map.remove(listener)?.let { WKConfig.getInstance().removeSessionListener(it) } }
    }
    private data class Key(val context: Context, val space: String, val id: String, val kind: String)
    private data class Callback(val callback: (Result) -> Unit, @Volatile var active: Boolean = true)
    private data class Pending(val id: Long, val callbacks: MutableMap<Long, Callback>, val key: Key, val generation: Long)
    private data class Cached(val result: Result, val expires: Long)
    private val lock = Any(); private val cache = LinkedHashMap<Key, Cached>(16, 0.75f, true); private val inflight = HashMap<Key, Pending>()
    private var nextRequestId = 0L; private var nextSubscriptionId = 0L; private var generation = 0L
    @Volatile private var closed = false
    private val sessionListener: () -> Unit = { invalidateSession() }
    init { sessionEvents.add(sessionListener) }

    fun fetch(content: WKDocumentShareContent, force: Boolean = false, callback: (Result) -> Unit): Subscription {
        if (closed) { callback(Result(State.error)); return EmptySubscription }
        val id = content.docId.orEmpty(); val space = content.spaceId.orEmpty(); val origins = config.origins()
        val api = apiBaseUrl(origins.first); val web = webOrigin(origins.second); val s = session()
        if (!validDocId(id) || (space.isNotEmpty() && !validDocId(space)) || api == null || s.uid.isEmpty() || s.token.isEmpty()) { callback(Result(State.error)); return EmptySubscription }
        val context = Context(s.version, s.uid, s.token, api, web); val key = Key(context, space, id, normalizeKind(content.kind))
        var cached: Result? = null; var pending: Pending? = null; var start: Pending? = null; var subscriptionId = 0L; var lookupGeneration = 0L
        synchronized(lock) {
            if (closed) { callback(Result(State.error)); return EmptySubscription }
            evictExpiredLocked()
            lookupGeneration = generation
            if (!force) cache[key]?.takeIf { it.expires > now() && isCurrentLocked(context, lookupGeneration) }?.let { cached = it.result }
            if (cached == null) {
                pending = inflight[key]
                if (pending == null) { pending = Pending(++nextRequestId, LinkedHashMap(), key, generation); inflight[key] = pending!!; start = pending }
                subscriptionId = ++nextSubscriptionId; pending!!.callbacks[subscriptionId] = Callback(callback)
            }
        }
        cached?.let { beforeCachedDelivery(); deliverIfCurrent(key.context, lookupGeneration) { callback(it) }; return EmptySubscription }
        start?.let { launch { execute(it) } }; val request = pending!!
        return object : Subscription { override fun cancel() { synchronized(lock) { request.callbacks.remove(subscriptionId)?.active = false } } }
    }

    fun requestPreview(content: WKDocumentShareContent, callback: (Result) -> Unit) = fetch(content, false, callback)
    fun invalidateSession() { synchronized(lock) { if (closed) return; generation++; cache.clear(); inflight.clear() } }
    override fun close() { synchronized(lock) { if (closed) return; closed = true; generation++; cache.clear(); inflight.values.forEach { pending -> pending.callbacks.values.forEach { it.active = false } }; inflight.clear() }; sessionEvents.remove(sessionListener) }

    private fun execute(request: Pending) {
        val key = request.key
        // This lock check is the request's start point. A close after this point may let the
        // already-started transport finish; complete() still rejects its stale result.
        val allowed = synchronized(lock) {
            !closed && generation == request.generation && inflight[key]?.id == request.id && inflight[key]?.generation == request.generation && currentContext() == key.context
        }
        if (!allowed) return
        try { transport.get(buildRequestUrl(key.context.apiBaseUrl, key.id, key.kind, key.space), key.context.token, key.space) { code, body ->
            try { complete(request, mapResult(code, body, key.context, key.kind)) } catch (_: Throwable) { complete(request, Result(State.error, context = key.context)) }
        } } catch (_: Throwable) { complete(request, Result(State.error, context = key.context)) }
    }

    private fun complete(request: Pending, result: Result) {
        val callbacks: List<Callback>; val valid: Boolean; val deliveryGeneration: Long; val deliveryContext: Context
        synchronized(lock) {
            val current = inflight[request.key]
            if (closed || current?.id != request.id || current.generation != request.generation || request.generation != generation) return
            valid = currentContext() == request.key.context; inflight.remove(request.key)
            deliveryGeneration = generation; deliveryContext = request.key.context
            evictExpiredLocked()
            if (valid && result.state != State.error) {
                cache[request.key] = Cached(result, now() + 30_000)
                while (cache.size > MAX_CACHE_ENTRIES) cache.entries.iterator().run { next(); remove() }
            }
            callbacks = if (valid) request.callbacks.values.toList() else emptyList()
        }
        if (valid) callbacks.forEach { item ->
            val stillValid = item.active && synchronized(lock) { !closed && generation == deliveryGeneration && currentContext() == deliveryContext }
            if (stillValid) runCatching { item.callback(result) }
        }
    }

    private fun currentContext(): Context? { val origins = config.origins(); val s = session(); val api = apiBaseUrl(origins.first) ?: return null; return Context(s.version, s.uid, s.token, api, webOrigin(origins.second)) }
    private fun evictExpiredLocked() { val timestamp = now(); val iterator = cache.entries.iterator(); while (iterator.hasNext()) if (iterator.next().value.expires <= timestamp) iterator.remove() }
    private fun isCurrentLocked(context: Context, expectedGeneration: Long) = !closed && expectedGeneration == generation && currentContext() == context
    private fun deliverIfCurrent(context: Context, expectedGeneration: Long, callback: () -> Unit) { val valid = synchronized(lock) { isCurrentLocked(context, expectedGeneration) }; if (valid) callback() }

    private fun mapResult(code: Int, body: String?, context: Context, kind: String): Result {
        if (code == 403) return Result(State.denied, context = context)
        if (code == 404 || code == 410) return Result(State.unavailable, context = context)
        val json = body?.let { org.json.JSONObject(it) }; val error = json?.opt("error") as? String
        return when {
            code == 409 && error == "unsupported_doc_type" -> Result(State.empty, context = context)
            code in 200..299 -> {
                if (json == null) Result(State.error, context = context)
                else {
                    val preview = when (kind) {
                        "sheet" -> DocumentSharePreviewParser.parseSheet(json)
                        "board" -> DocumentSharePreviewParser.parseBoard(json)
                        "html" -> DocumentSharePreviewParser.parseHtml(json)
                        else -> DocumentSharePreviewParser.parseDoc(json)
                    }
                    if (preview == null) Result(State.empty, context = context) else Result(State.ready, preview, context)
                }
            }
            else -> Result(State.error, context = context)
        }
    }

    internal fun buildRequestUrl(apiBaseUrl: String, id: String, kind: String, space: String): String {
        val endpoint = when (kind) { "sheet" -> "sheet"; "board" -> "scene"; "html" -> "html-preview"; else -> "content" }
        val query = if (space.isNotEmpty()) "sp=${java.net.URLEncoder.encode(space, Charsets.UTF_8.name()).replace("+", "%20")}" else null
        return URI.create("$apiBaseUrl/docs/$id/$endpoint" + (query?.let { "?$it" } ?: "")).toASCIIString()
    }
    fun buildNavigationUrl(docId: String): String? = if (validDocId(docId)) webOrigin(config.origins().second)?.let { "$it/d/$docId" } else null

    companion object {
        internal const val MAX_CACHE_ENTRIES = 128
        private val applicationRepository by lazy { DocumentSharePreviewRepository() }
        fun shared() = applicationRepository
        fun navigationUrl(docId: String): String? = navigationUrl(docId, WKApiConfig.baseWebUrl)
        fun navigationUrl(docId: String, baseWebUrl: String): String? = if (validDocId(docId)) webOrigin(baseWebUrl)?.let { "$it/d/$docId" } else null
        private object EmptySubscription : Subscription { override fun cancel() = Unit }
        fun validDocId(value: String) = value.matches(Regex("[A-Za-z0-9_-]{1,128}"))
        fun normalizeKind(value: String?) = when (value) { "sheet", "board", "html" -> value!! else -> "doc" }
        fun apiBaseUrl(value: String): String? = runCatching {
            val uri = URI(value.trim())
            if (!uri.scheme.equals("https", true) || uri.host.isNullOrEmpty() || uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
            val path = uri.rawPath.orEmpty().trimEnd('/')
            "https://${uri.rawAuthority}$path"
        }.getOrNull()
        fun webOrigin(value: String) = origin(value)
        private fun origin(value: String): String? = runCatching { URI(value.trim()).let { if ((!it.scheme.equals("http", true) && !it.scheme.equals("https", true)) || it.userInfo != null || it.host.isNullOrEmpty()) null else URI(it.scheme.lowercase(), null, it.host, it.port, null, null, null).toString().removeSuffix("/") } }.getOrNull()
    }

    private class OkHttpTransport : Transport {
        private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).cache(null).build()
        override fun get(url: String, token: String, spaceId: String, callback: (Int, String?) -> Unit) {
            val request = Request.Builder().url(url).header("token", token).apply { if (spaceId.isNotEmpty()) header("X-Space-Id", spaceId) }.build()
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: Call, e: IOException) = callback(-1, null)
                override fun onResponse(call: Call, response: Response) { var code = response.code; var body: String? = null; try { response.use { val source = it.body?.byteStream() ?: return@use; val buffer = ByteArray(8192); val output = java.io.ByteArrayOutputStream(); var total = 0; while (total <= 2_000_000) { val count = source.read(buffer, 0, minOf(buffer.size, 2_000_001 - total)); if (count < 0) break; total += count; output.write(buffer, 0, count); if (total > 2_000_000) break }; if (total <= 2_000_000) body = output.toByteArray().toString(Charsets.UTF_8) else code = -1 } } catch (_: Throwable) { code = -1; body = null }; callback(code, body) }
            })
        }
    }
}
