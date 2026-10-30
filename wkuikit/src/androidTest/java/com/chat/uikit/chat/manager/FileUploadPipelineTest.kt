package com.chat.uikit.chat.manager

import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import androidx.test.platform.app.InstrumentationRegistry
import com.chat.base.WKBaseApplication
import com.chat.base.config.WKConfig
import com.chat.base.msgcontent.WKFileContent
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.net.ud.WKProgressManager
import com.chat.base.net.ud.FileRequestBody
import com.chat.base.utils.AppExecutors
import com.chat.uikit.R
import com.chat.uikit.chat.provider.DocumentSharePreviewTestActivity
import com.chat.uikit.chat.provider.WKFileProvider
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.WKIMApplication
import com.xinbida.wukongim.db.WKDBHelper
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelType
import com.xinbida.wukongim.entity.WKMsg
import com.xinbida.wukongim.entity.WKSendOptions
import com.xinbida.wukongim.interfaces.IUploadAttachmentListener
import com.xinbida.wukongim.message.WKConnection
import com.xinbida.wukongim.message.type.WKConnectStatus
import com.xinbida.wukongim.message.type.WKSendingMsg
import com.xinbida.wukongim.protocol.WKSendMsg
import com.chat.base.net.ud.WKUploader
import com.xinbida.wukongim.msgmodel.WKMessageContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okio.Buffer

/** Exercises the SDK's real send/attachment callback and database path; only the network edges are faked. */
class FileUploadPipelineTest {
    private class CallbackObservedFileContent(
        private val delegate: WKFileContent,
        private val completed: CountDownLatch,
        private val failure: AtomicReference<Throwable?>
    ) : WKFileContent() {
        init {
            name = delegate.name; extension = delegate.extension; size = delegate.size
            localPath = delegate.localPath; url = delegate.url
        }
        override fun hasTrustedLocalSource(): Boolean = delegate.hasTrustedLocalSource()
        override fun getTrustedLocalAttachmentSize(): Long = delegate.getTrustedLocalAttachmentSize()
        override fun prepareTrustedLocalAttachment(uid: String, channelId: String, channelType: Byte,
                                                   clientMsgNo: String, expectedSize: Long,
                                                   callback: com.xinbida.wukongim.msgmodel.WKLocalAttachmentContent.PrepareCallback) {
            delegate.prepareTrustedLocalAttachment(uid, channelId, channelType, clientMsgNo, expectedSize) { path, success ->
                try { callback.onPrepared(path, success) }
                catch (error: Throwable) { failure.set(error); throw error }
                finally { completed.countDown() }
            }
        }
    }

    internal class FakeTransport : WKSendMsgUtils.FileUploadTransport {
        data class CredentialRequest(val channelID: String, val channelType: Byte, val path: String, val callback: WKUploader.IGetUploadCredentials)
        data class PutRequest(val path: String, val tag: Any, val callback: WKUploader.IUploadBack)
        @Volatile var credentials: WKUploader.IGetUploadCredentials? = null
        @Volatile var put: WKUploader.IUploadBack? = null
        val credentialsReady = CountDownLatch(1)
        val credentialRequests = CopyOnWriteArrayList<CredentialRequest>()
        val putRequests = CopyOnWriteArrayList<PutRequest>()
        override fun getCredentials(channelID: String, channelType: Byte, path: String, callback: WKUploader.IGetUploadCredentials) {
            credentials = callback
            credentialRequests += CredentialRequest(channelID, channelType, path, callback)
            credentialsReady.countDown()
        }
        override fun put(uploadUrl: String, path: String, contentType: String, contentDisposition: String?, tag: Any, callback: WKUploader.IUploadBack) {
            put = callback
            putRequests += PutRequest(path, tag, callback)
        }
        fun returnCredentials(ok: Boolean = true) {
            requireNotNull(credentials).onResult(if (ok) "https://upload.test/object" else null, if (ok) "https://files.test/object" else null, "application/octet-stream", null)
        }
        fun credentialsFor(channelID: String): CredentialRequest? = credentialRequests.lastOrNull { it.channelID == channelID }
        fun putForPath(path: String): PutRequest? = putRequests.lastOrNull { it.path == path }
    }

    internal class Scenario(private val trustedSource: Boolean = false,
                            private val preparedContentFactory: ((WKConfig.SessionSnapshot, String) -> WKFileContent)? = null,
                            private val forceSnapshotFailure: Boolean = false) : AutoCloseable {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = WKIMApplication.getInstance()
        val config = WKConfig.getInstance()
        val oldUid = app.uid
        val oldToken = app.token
        val oldSession = config.getSessionSnapshot()
        val oldApiBaseUrl = com.chat.base.config.WKApiConfig.baseUrl
        val oldApiWebBaseUrl = com.chat.base.config.WKApiConfig.baseWebUrl
        lateinit var uploadSession: WKConfig.SessionSnapshot
        val appContextField = WKIMApplication::class.java.getDeclaredField("mContext").apply { isAccessible = true }
        val dbHelperField = WKIMApplication::class.java.getDeclaredField("mDbHelper").apply { isAccessible = true }
        val oldAppContext = appContextField.get(app)
        val oldDbHelper = dbHelperField.get(app)
        val dbHelperConstructor = WKDBHelper::class.java.getDeclaredConstructor(android.content.Context::class.java, String::class.java).apply { isAccessible = true }
        val uidField = WKDBHelper::class.java.getDeclaredField("uid").apply { isAccessible = true }
        val dbNameField = WKDBHelper::class.java.getDeclaredField("myDBName").apply { isAccessible = true }
        val openHelperField = WKDBHelper::class.java.getDeclaredField("openHelper").apply { isAccessible = true }
        val oldStaticUid = uidField.get(null)
        val oldDbName = dbNameField.get(null)
        val oldOpenHelper = openHelperField.get(null)
        val sessionVersionField = WKConfig::class.java.getDeclaredField("sessionVersion").apply { isAccessible = true }
        val oldSessionVersion = sessionVersionField.getLong(config)
        val sessionListenersField = WKConfig::class.java.getDeclaredField("sessionListeners").apply { isAccessible = true }
        val initialSessionListenerCount = (sessionListenersField.get(config) as java.util.concurrent.CopyOnWriteArrayList<*>).size
        val progressListField = WKProgressManager::class.java.getDeclaredField("progressList").apply { isAccessible = true }
        val uploadListenerField = WKIM.getInstance().msgManager.javaClass.getDeclaredField("iUploadAttachmentListener").apply { isAccessible = true }
        val oldUploadListener = uploadListenerField.get(WKIM.getInstance().msgManager)
        val contentRegistryField = WKIM.getInstance().msgManager.javaClass.getDeclaredField("customContentMsgList").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val oldContentRegistry = (contentRegistryField.get(WKIM.getInstance().msgManager) as? List<Class<out WKMessageContent>>)?.toList()
        val connectStatusField = WKConnection::class.java.getDeclaredField("connectStatus").apply { isAccessible = true }
        val oldConnectStatus = connectStatusField.getInt(WKConnection.getInstance())
        val sendingMapField = WKConnection::class.java.getDeclaredField("sendingMsgHashMap").apply { isAccessible = true }
        val beforeCapturedWriteHook = WKConnection::class.java.getDeclaredField("fileUploadBeforeCapturedWriteHookForTests").apply { isAccessible = true }
        val afterCapturedWriteHook = WKConnection::class.java.getDeclaredField("fileUploadAfterCapturedWriteHookForTests").apply { isAccessible = true }
        val afterSnapshotSessionCheckHook = WKConnection::class.java.getDeclaredField("fileUploadAfterSnapshotSessionCheckHookForTests").apply { isAccessible = true }
        val beforeFinishSessionCheckHook = WKConnection::class.java.getDeclaredField("fileUploadBeforeFinishSendSessionCheckHookForTests").apply { isAccessible = true }
        val afterSenderLookupHook = WKConnection::class.java.getDeclaredField("fileUploadAfterSenderLookupHookForTests").apply { isAccessible = true }
        val connectionLockField = WKConnection::class.java.getField("connectionLock")
        val capturedUiHook = WKIM.getInstance().msgManager.javaClass.getDeclaredField("beforeCapturedUiDeliveryHookForTests").apply { isAccessible = true }
        val afterFailureReadbackHook = WKConnection::class.java.getDeclaredField("afterCapturedFailureReadbackHookForTests").apply { isAccessible = true }
        val queueIdentityHook = WKConnection::class.java.getDeclaredField("beforeSendingQueueIdentityCheckHookForTests").apply { isAccessible = true }
        val failureWriteHook = WKConnection::class.java.getDeclaredField("beforeCapturedFailureWriteHookForTests").apply { isAccessible = true }
        val failureAfterWriteHook = WKConnection::class.java.getDeclaredField("afterCapturedFailureWriteHookForTests").apply { isAccessible = true }
        val sdkTransportField = WKConnection::class.java.getDeclaredField("capturedFileSendTransportForTests").apply { isAccessible = true }
        val oldSdkTransport = sdkTransportField.get(null)
        val testUid = "codex-upload-${UUID.randomUUID().toString().replace("-", "")}"
        val testToken = "codex-test-token"
        val dbName = "wk_$testUid.db"
        lateinit var testDbHelper: WKDBHelper
        val switchedHelpers = mutableListOf<Pair<WKDBHelper, String>>()
        val extraMessageSeqs = mutableListOf<Long>()
        val tempFile = File.createTempFile("octo-upload-", ".txt", context.cacheDir).apply { writeText("upload fixture") }
        val transport = FakeTransport()
        val sendCallbackReceived = CountDownLatch(1)
        val callbackMessage = AtomicReference<WKMsg?>(null)
        val sendCallbackKey = "codex-file-send-${UUID.randomUUID()}"
        val previousApplication = WKBaseApplication.getInstance().application
        val activity: DocumentSharePreviewTestActivity
        val root: android.view.ViewGroup
        val provider = WKFileProvider()
        val message = WKMsg()
        val item: WKUIChatMsgItemEntity
        val row: View
        val originalBinding: Any?

        init {
            context.deleteDatabase(dbName)
            WKBaseApplication.getInstance().application = context.applicationContext as android.app.Application
            com.chat.base.config.WKApiConfig.baseUrl = "https://api.test/v1/"
            com.chat.base.config.WKApiConfig.baseWebUrl = "https://api.test/web/"
            val initContext = WKIMApplication::class.java.getDeclaredMethod("initContext", android.content.Context::class.java).apply { isAccessible = true }
            initContext.invoke(app, context)
            config.uid = testUid
            config.token = testToken
            uploadSession = config.getSessionSnapshot()
            app.uid = testUid
            app.token = testToken
            testDbHelper = dbHelperConstructor.newInstance(context, testUid)
            dbHelperField.set(app, testDbHelper)
            WKIM.getInstance().msgManager.initNormalMsg()
            WKIM.getInstance().msgManager.registerContentMsg(WKFileContent::class.java)
            WKIM.getInstance().channelManager.saveOrUpdateChannel(WKChannel(testUid, WKChannelType.PERSONAL))
            uploadListenerField.set(WKIM.getInstance().msgManager, IUploadAttachmentListener { msg, callback -> WKSendMsgUtils.getInstance().uploadChatAttachment(msg, callback) })
            WKIM.getInstance().msgManager.addOnSendMsgCallback(sendCallbackKey) { sent -> callbackMessage.set(sent); sendCallbackReceived.countDown() }
            connectStatusField.setInt(WKConnection.getInstance(), WKConnectStatus.syncMsg)
            WKSendMsgUtils.setFileUploadTransportForTests(transport)

            activity = instrumentation.startActivitySync(Intent(context, DocumentSharePreviewTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as DocumentSharePreviewTestActivity
            root = activity.findViewById(android.R.id.content)
            message.channelID = "upload-test-channel"
            message.channelType = WKChannelType.GROUP
            message.fromUID = testUid
            message.type = WKContentType.WK_FILE
            message.baseContentMsgModel = preparedContentFactory?.invoke(config.getSessionSnapshot(), com.chat.base.config.WKApiConfig.baseUrl) ?: if (trustedSource) {
                requireNotNull(WKFileContent.fromTrustedLocalFile(tempFile.absolutePath, tempFile.name, "txt",
                    config.getSessionSnapshot(), com.chat.base.config.WKApiConfig.baseUrl))
            } else WKFileContent().apply { name = tempFile.name; extension = "txt"; size = tempFile.length(); localPath = tempFile.absolutePath }
            if (forceSnapshotFailure) (message.baseContentMsgModel as WKFileContent).size += 1
            item = WKUIChatMsgItemEntity(null, message, null)
            provider.context = activity
            instrumentation.runOnMainSync { WKConnection.getInstance().sendMessage(message) }
            if (forceSnapshotFailure) check(sendCallbackReceived.await(5, TimeUnit.SECONDS)) { "snapshot failure did not notify current chat" }
            else check(transport.credentialsReady.await(5, TimeUnit.SECONDS)) { "SDK did not reach file credential transport" }
            row = android.view.LayoutInflater.from(activity).inflate(R.layout.chat_item_file, root, false)
            instrumentation.runOnMainSync {
                root.addView(row)
                provider.refreshData(0, row, item, WKChatIteMsgFromType.SEND)
            }
            originalBinding = row.findViewById<LinearLayout>(R.id.fileView).tag
            if (!forceSnapshotFailure) requireNotNull(transport.credentials) { "SDK attachment callback did not reach file credential transport" }
        }

        fun progressAt100() {
            val path = (message.baseContentMsgModel as WKFileContent).localPath
            FileRequestBody(File(path).asRequestBody("application/octet-stream".toMediaType()), progressKey()).writeTo(Buffer())
            instrumentation.waitForIdleSync()
            assertNotNull(WKProgressManager.instance.getFileUploadTask(progressKey()))
            assertTrue(row.findViewById<android.widget.TextView>(R.id.fileUploadProgressPercent).text.toString().contains("100"))
            assertTrue(row.findViewById<View>(R.id.fileSizeTv).visibility == View.VISIBLE)
            assertTrue(row.findViewById<View>(R.id.contentLayout).isEnabled)
            assertTrue(row.findViewById<View>(R.id.fileActionButton).isEnabled)
        }

        fun persisted() = WKIM.getInstance().msgManager.getWithClientMsgNO(message.clientMsgNO)
        fun progressKey() = WKProgressManager.FileUploadProgressKey(uploadSession.uid, uploadSession.version, message.clientMsgNO, message.clientSeq)
        fun isProgressRegistered(key: Any) = (progressListField.get(WKProgressManager.instance) as Map<*, *>).containsKey(key)
        fun assertUploadSessionListenerRemoved() {
            assertEquals(initialSessionListenerCount, (sessionListenersField.get(config) as java.util.concurrent.CopyOnWriteArrayList<*>).size)
        }
        fun capturedStatusAndContent(): Pair<Int, String> {
            val cursor = testDbHelper.rawQuery("select status,content from message where client_msg_no=?", arrayOf(message.clientMsgNO))
            try { check(cursor.moveToFirst()); return cursor.getInt(0) to cursor.getString(1) } finally { cursor.close() }
        }

        fun capturedStatusAndContent(msg: WKMsg): Pair<Int, String> {
            val cursor = testDbHelper.rawQuery("select status,content from message where client_msg_no=? and client_seq=?", arrayOf(msg.clientMsgNO, msg.clientSeq.toString()))
            try { check(cursor.moveToFirst()); return cursor.getInt(0) to cursor.getString(1) } finally { cursor.close() }
        }

        private fun finishFixtureUpload() {
            val request = transport.credentialsFor("upload-test-channel") ?: return
            val put = transport.putForPath(request.path)
            if (put == null) request.callback.onResult(null, null, null, null)
            else put.callback.onError()
        }

        fun sendNewFileMessage(channelID: String, content: WKFileContent): WKMsg {
            val sent = WKMsg().apply {
                this.channelID = channelID
                channelType = WKChannelType.GROUP
                fromUID = uploadSession.uid
                type = WKContentType.WK_FILE
                baseContentMsgModel = content
            }
            instrumentation.runOnMainSync { WKConnection.getInstance().sendMessage(sent) }
            extraMessageSeqs += sent.clientSeq
            return sent
        }

        fun trackMessage(msg: WKMsg) { extraMessageSeqs += msg.clientSeq }

        fun latestMessageForChannel(channelID: String): Pair<String, Int>? {
            val cursor = testDbHelper.rawQuery("select client_msg_no,status from message where channel_id=? and channel_type=? order by client_seq desc limit 1", arrayOf(channelID, WKChannelType.GROUP.toString()))
            try { return if (cursor.moveToFirst()) cursor.getString(0) to cursor.getInt(1) else null } finally { cursor.close() }
        }

        fun switchToAnotherSession(): WKDBHelper {
            val nextUid = "codex-upload-next-${UUID.randomUUID().toString().replace("-", "")}"
            config.uid = nextUid; config.token = "codex-next-token"
            app.uid = nextUid; app.token = "codex-next-token"
            val helper = dbHelperConstructor.newInstance(context, nextUid)
            dbHelperField.set(app, helper)
            switchedHelpers += helper to "wk_$nextUid.db"
            return helper
        }

        override fun close() {
            runCatching { instrumentation.runOnMainSync { root.removeView(row); activity.finish() } }
            runCatching { finishFixtureUpload() }
            WKProgressManager.instance.finishFileUpload(progressKey())
            WKProgressManager.instance.unregisterProgress(progressKey())
            @Suppress("UNCHECKED_CAST") (sendingMapField.get(WKConnection.getInstance()) as? MutableMap<Int, Any?>)?.remove(message.clientSeq.toInt())
            extraMessageSeqs.distinct().forEach { seq -> runCatching {
                val cursor = testDbHelper.rawQuery("select client_msg_no,client_seq from message where client_seq=? limit 1", arrayOf(seq.toString()))
                try {
                    if (cursor.moveToFirst()) {
                        val key = WKProgressManager.FileUploadProgressKey(uploadSession.uid, uploadSession.version, cursor.getString(0), cursor.getLong(1))
                        WKProgressManager.instance.finishFileUpload(key)
                        WKProgressManager.instance.unregisterProgress(key)
                    }
                } finally { cursor.close() }
            } }
            WKSendMsgUtils.setFileUploadTransportForTests(null)
            WKIM.getInstance().msgManager.removeSendMsgCallBack(sendCallbackKey)
            beforeCapturedWriteHook.set(null, null); afterCapturedWriteHook.set(null, null)
            afterSnapshotSessionCheckHook.set(null, null); beforeFinishSessionCheckHook.set(null, null); afterSenderLookupHook.set(null, null)
            capturedUiHook.set(null, null); queueIdentityHook.set(null, null); sdkTransportField.set(null, oldSdkTransport)
            afterFailureReadbackHook.set(null, null)
            failureWriteHook.set(null, null)
            failureAfterWriteHook.set(null, null)
            @Suppress("UNCHECKED_CAST") (sendingMapField.get(WKConnection.getInstance()) as? MutableMap<Int, Any?>)?.let { map -> extraMessageSeqs.forEach { map.remove(it.toInt()) } }
            uploadListenerField.set(WKIM.getInstance().msgManager, oldUploadListener)
            contentRegistryField.set(WKIM.getInstance().msgManager, oldContentRegistry?.toMutableList())
            connectStatusField.setInt(WKConnection.getInstance(), oldConnectStatus)
            switchedHelpers.forEach { (helper, name) -> helper.close(); context.deleteDatabase(name) }
            testDbHelper.close()
            appContextField.set(app, oldAppContext)
            dbHelperField.set(app, oldDbHelper)
            uidField.set(null, oldStaticUid); dbNameField.set(null, oldDbName); openHelperField.set(null, oldOpenHelper)
            app.uid = oldUid; app.token = oldToken
            config.uid = oldSession.uid; config.token = oldSession.token
            com.chat.base.config.WKApiConfig.baseUrl = oldApiBaseUrl
            com.chat.base.config.WKApiConfig.baseWebUrl = oldApiWebBaseUrl
            sessionVersionField.setLong(config, oldSessionVersion)
            WKBaseApplication.getInstance().application = previousApplication
            context.deleteDatabase(dbName)
            tempFile.delete()
        }
    }

    private data class IoBlock(val release: CountDownLatch, val entered: CountDownLatch, val timedOut: AtomicReference<Boolean>)

    private fun blockIoPool(): IoBlock {
        val executor = AppExecutors.io() as ThreadPoolExecutor
        val release = CountDownLatch(1)
        val entered = CountDownLatch(executor.corePoolSize)
        val timedOut = AtomicReference(false)
        try {
            repeat(executor.corePoolSize) {
                executor.execute {
                    entered.countDown()
                    if (!release.await(10, TimeUnit.SECONDS)) timedOut.set(true)
                }
            }
            check(entered.await(5, TimeUnit.SECONDS)) { "IO workers did not reach the test barrier" }
            return IoBlock(release, entered, timedOut)
        } catch (error: Throwable) {
            release.countDown()
            throw error
        }
    }

    private fun createOwnedShareSource(scenario: Scenario, name: String): Pair<WKFileContent, File> {
        val directory = File(scenario.context.cacheDir, "share_file/test-${UUID.randomUUID()}")
        check(directory.mkdirs())
        val sourceFile = File(directory, name).apply { scenario.tempFile.copyTo(this, overwrite = true) }
        val content = requireNotNull(WKFileContent.fromTrustedLocalFile(sourceFile.absolutePath, name, "txt",
            scenario.config.getSessionSnapshot(), com.chat.base.config.WKApiConfig.baseUrl))
        content.setOwnedTemporaryDirectory(directory)
        return content to directory
    }

    private fun sendAndCleanupTwice(scenario: Scenario, content: WKFileContent, channelID: String) {
        scenario.instrumentation.runOnMainSync {
            WKIM.getInstance().msgManager.sendWithOptions(content, WKChannel(channelID, WKChannelType.GROUP), WKSendOptions())
            content.cleanupOwnedTemporarySource()
            content.cleanupOwnedTemporarySource()
        }
    }

    private fun awaitCredential(scenario: Scenario, channelID: String): FakeTransport.CredentialRequest {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            scenario.transport.credentialsFor(channelID)?.let { return it }
            Thread.sleep(10)
        }
        throw AssertionError("no credential request for $channelID")
    }

    private fun awaitPut(scenario: Scenario, path: String): FakeTransport.PutRequest {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            scenario.transport.putRequests.lastOrNull { it.path == path }?.let { return it }
            Thread.sleep(10)
        }
        throw AssertionError("no upload request for snapshot $path")
    }

    private fun finishUnfinishedSends(scenario: Scenario, channelIDs: List<String>, completed: Set<String>) {
        channelIDs.filterNot { it in completed }.forEach { channelID ->
            val request = runCatching { awaitCredential(scenario, channelID) }.getOrNull()
            if (request == null) {
                runCatching { awaitFailure(scenario, channelID) }
            } else {
                if (scenario.transport.putForPath(request.path) == null) {
                    request.callback.onResult("https://upload.test/object", "https://files.test/object", "application/octet-stream", null)
                }
                runCatching { awaitPut(scenario, request.path).callback.onError() }
            }
        }
        runCatching { scenario.instrumentation.waitForIdleSync() }
    }

    private fun cleanupSentRows(scenario: Scenario, channelIDs: List<String>) {
        channelIDs.forEach { channelID ->
            val cursor = scenario.testDbHelper.rawQuery("select client_msg_no,client_seq from message where channel_id=? and channel_type=? order by client_seq desc limit 1", arrayOf(channelID, WKChannelType.GROUP.toString()))
            try {
                if (cursor.moveToFirst()) {
                    val clientMsgNo = cursor.getString(0)
                    val clientSeq = cursor.getLong(1)
                    @Suppress("UNCHECKED_CAST")
                    (scenario.sendingMapField.get(WKConnection.getInstance()) as? MutableMap<Int, Any?>)?.remove(clientSeq.toInt())
                    val key = WKProgressManager.FileUploadProgressKey(scenario.uploadSession.uid, scenario.uploadSession.version, clientMsgNo, clientSeq)
                    WKProgressManager.instance.finishFileUpload(key)
                    WKProgressManager.instance.unregisterProgress(key)
                }
            } finally { cursor.close() }
        }
    }

    private fun awaitFailure(scenario: Scenario, channelID: String): Pair<String, Int> {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            scenario.latestMessageForChannel(channelID)?.let { (clientMsgNo, status) ->
                if (status == com.xinbida.wukongim.message.type.WKSendMsgResult.send_fail) return clientMsgNo to status
            }
            Thread.sleep(10)
        }
        throw AssertionError("captured message for $channelID did not reach send_fail")
    }

    private fun awaitMessageStatus(scenario: Scenario, msg: WKMsg, expected: Int): String {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        while (System.nanoTime() < deadline) {
            val (status, content) = scenario.capturedStatusAndContent(msg)
            if (status == expected) return content
            Thread.sleep(10)
        }
        throw AssertionError("captured message ${msg.clientMsgNO} did not reach status $expected")
    }

    private data class LockHold(
        val lock: ReentrantLock,
        val release: CountDownLatch,
        val acquired: CountDownLatch,
        val thread: Thread,
        val failure: AtomicReference<Throwable?>
    )

    private fun holdConnectionLock(scenario: Scenario): LockHold {
        val lock = scenario.connectionLockField.get(WKConnection.getInstance()) as ReentrantLock
        val release = CountDownLatch(1)
        val acquired = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val thread = Thread {
            try {
                lock.lock()
                try {
                    acquired.countDown()
                    if (!release.await(12, TimeUnit.SECONDS)) throw AssertionError("connection lock holder timed out")
                } finally { lock.unlock() }
            } catch (error: Throwable) { failure.set(error) }
        }
        thread.start()
        check(acquired.await(5, TimeUnit.SECONDS)) { "connection lock was not acquired" }
        return LockHold(lock, release, acquired, thread, failure)
    }

    private fun releaseLock(held: LockHold) {
        held.release.countDown()
        held.thread.join(5000)
        assertFalse("connection lock holder did not exit", held.thread.isAlive)
        assertEquals(null, held.failure.get())
    }

    private fun assertNoCapturedQueueEntry(scenario: Scenario, msg: WKMsg) {
        @Suppress("UNCHECKED_CAST")
        val sendingMap = scenario.sendingMapField.get(WKConnection.getInstance()) as MutableMap<Int, Any>
        assertEquals(null, sendingMap[msg.clientSeq.toInt()])
    }

    private fun insertSessionSentinel(scenario: Scenario, msg: WKMsg): Pair<WKDBHelper, WKSendingMsg> {
        val helper = scenario.switchToAnotherSession()
        val sentinel = WKSendingMsg(1, WKSendMsg().apply { clientSeq = msg.clientSeq.toInt() }, true)
        helper.insert("message", android.content.ContentValues().apply {
            put("client_seq", msg.clientSeq); put("client_msg_no", msg.clientMsgNO)
            put("status", com.xinbida.wukongim.message.type.WKSendMsgResult.send_loading)
            put("channel_id", msg.channelID); put("channel_type", msg.channelType.toInt())
            put("from_uid", WKConfig.getInstance().uid); put("type", WKContentType.WK_FILE); put("content", "session-sentinel")
        })
        @Suppress("UNCHECKED_CAST")
        (scenario.sendingMapField.get(WKConnection.getInstance()) as MutableMap<Int, Any>)[msg.clientSeq.toInt()] = sentinel
        return helper to sentinel
    }

    private fun trustedTestContent(scenario: Scenario, name: String): WKFileContent = requireNotNull(
        WKFileContent.fromTrustedLocalFile(scenario.tempFile.absolutePath, name, "txt",
            scenario.uploadSession, com.chat.base.config.WKApiConfig.baseUrl))

    @Test fun sendWithOptionsTransfersSingleShareSourceBeforeCallerCleanup() {
        val scenario = Scenario()
        var block: IoBlock? = null
        val contents = mutableListOf<WKFileContent>()
        val channels = mutableListOf<String>()
        val completed = mutableSetOf<String>()
        try {
            val external = scenario.tempFile
            val (content, ownedDirectory) = createOwnedShareSource(scenario, "single-share.txt")
            contents += content
            val sourcePath = content.localPath
            val channelID = "share-single-${UUID.randomUUID()}"
            channels += channelID
            val ioBlock = blockIoPool()
            block = ioBlock
            sendAndCleanupTwice(scenario, content, channelID)
            assertFalse("IO barrier must remain held until explicitly released", ioBlock.timedOut.get())
            assertTrue("source must remain available while snapshot IO is queued", File(sourcePath).isFile)
            assertEquals("upload fixture", File(sourcePath).readText())
            assertTrue(ownedDirectory.isDirectory)
            ioBlock.release.countDown()
            val request = awaitCredential(scenario, channelID)
            assertNotEquals(sourcePath, request.path)
            assertEquals("upload fixture", File(request.path).readText())
            assertFalse(ownedDirectory.exists())
            assertTrue(external.isFile)
            request.callback.onResult("https://upload.test/object", "https://files.test/object", "application/octet-stream", null)
            awaitPut(scenario, request.path).callback.onSuccess("ignored")
            completed += channelID
        } finally {
            block?.release?.countDown()
            if (channels.isNotEmpty()) finishUnfinishedSends(scenario, channels, completed)
            cleanupSentRows(scenario, channels)
            contents.forEach { runCatching { it.cleanupOwnedTemporarySource() } }
            scenario.close()
        }
    }

    @Test fun sendWithOptionsKeepsOriginalAndIndependentDestinationSourcesUntilEachSnapshotStarts() {
        val scenario = Scenario()
        var block: IoBlock? = null
        val channels = mutableListOf<String>()
        val contents = mutableListOf<WKFileContent>()
        val completed = mutableSetOf<String>()
        try {
            val external = scenario.tempFile
            val (original, originalDirectory) = createOwnedShareSource(scenario, "multi-share.txt")
            contents += original
            val firstCopy = original.duplicateOwnedSource(scenario.context, scenario.config.getSessionSnapshot(), com.chat.base.config.WKApiConfig.baseUrl)!!
            val secondCopy = original.duplicateOwnedSource(scenario.context, scenario.config.getSessionSnapshot(), com.chat.base.config.WKApiConfig.baseUrl)!!
            contents += firstCopy
            contents += secondCopy
            val directories = listOf(originalDirectory, File(firstCopy.localPath).parentFile!!, File(secondCopy.localPath).parentFile!!)
            val sourcePaths = contents.map { it.localPath }
            channels += listOf("share-multi-a-${UUID.randomUUID()}", "share-multi-b-${UUID.randomUUID()}", "share-multi-c-${UUID.randomUUID()}")
            val ioBlock = blockIoPool()
            block = ioBlock
            scenario.instrumentation.runOnMainSync {
                contents.zip(channels).forEach { (content, channelID) ->
                    WKIM.getInstance().msgManager.sendWithOptions(content, WKChannel(channelID, WKChannelType.GROUP), WKSendOptions())
                }
                contents.forEach { it.cleanupOwnedTemporarySource(); it.cleanupOwnedTemporarySource() }
            }
            assertFalse("IO barrier must remain held until explicitly released", ioBlock.timedOut.get())
            assertTrue(directories.all { it.isDirectory })
            assertTrue(contents.all { File(it.localPath).readText() == "upload fixture" })
            ioBlock.release.countDown()
            val requests = channels.map { awaitCredential(scenario, it) }
            assertEquals(3, requests.map { it.channelID }.toSet().size)
            assertEquals(3, requests.map { it.path }.toSet().size)
            requests.forEach { request ->
                assertEquals("upload fixture", File(request.path).readText())
                assertTrue("credential path must point to a distinct snapshot", request.path !in sourcePaths)
            }
            assertTrue(directories.none { it.exists() })
            assertTrue(external.isFile)
            requests.forEach { request ->
                request.callback.onResult("https://upload.test/object", "https://files.test/object", "application/octet-stream", null)
                awaitPut(scenario, request.path).callback.onSuccess("ignored")
                completed += request.channelID
            }
        } finally {
            block?.release?.countDown()
            if (channels.isNotEmpty()) finishUnfinishedSends(scenario, channels, completed)
            cleanupSentRows(scenario, channels)
            contents.forEach { runCatching { it.cleanupOwnedTemporarySource() } }
            scenario.close()
        }
    }

    @Test fun sendWithOptionsSnapshotFailureCleansOnlyItsOwnedSourceAndPersistsFailure() {
        val scenario = Scenario()
        var block: IoBlock? = null
        val channels = mutableListOf<String>()
        val contents = mutableListOf<WKFileContent>()
        val completed = mutableSetOf<String>()
        try {
            val external = scenario.tempFile
            val (badContent, badDirectory) = createOwnedShareSource(scenario, "bad-size-share.txt")
            contents += badContent
            badContent.size += 1
            val (goodContent, goodDirectory) = createOwnedShareSource(scenario, "good-size-share.txt")
            contents += goodContent
            val badChannel = "share-bad-${UUID.randomUUID()}"
            val goodChannel = "share-good-${UUID.randomUUID()}"
            channels += listOf(badChannel, goodChannel)
            val ioBlock = blockIoPool()
            block = ioBlock
            sendAndCleanupTwice(scenario, badContent, badChannel)
            sendAndCleanupTwice(scenario, goodContent, goodChannel)
            assertFalse("IO barrier must remain held until explicitly released", ioBlock.timedOut.get())
            assertTrue(badDirectory.isDirectory)
            assertTrue(goodDirectory.isDirectory)
            ioBlock.release.countDown()
            awaitFailure(scenario, badChannel)
            val goodRequest = awaitCredential(scenario, goodChannel)
            assertEquals("upload fixture", File(goodRequest.path).readText())
            assertFalse(badDirectory.exists())
            assertFalse(goodDirectory.exists())
            assertTrue(external.isFile)
            assertTrue("failed destination must not upload", scenario.transport.credentialsFor(badChannel) == null)
            goodRequest.callback.onResult("https://upload.test/object", "https://files.test/object", "application/octet-stream", null)
            awaitPut(scenario, goodRequest.path).callback.onSuccess("ignored")
            completed += goodChannel
        } finally {
            block?.release?.countDown()
            if (channels.isNotEmpty()) finishUnfinishedSends(scenario, channels, completed)
            cleanupSentRows(scenario, channels)
            contents.forEach { runCatching { it.cleanupOwnedTemporarySource() } }
            scenario.close()
        }
    }

    @Test fun sendWithOptionsSessionInvalidationFailsCapturedMessageAfterQueuedPreparation() {
        val scenario = Scenario()
        var block: IoBlock? = null
        val contents = mutableListOf<WKFileContent>()
        val channels = mutableListOf<String>()
        try {
            val external = scenario.tempFile
            val (content, ownedDirectory) = createOwnedShareSource(scenario, "session-share.txt")
            contents += content
            val channelID = "share-session-${UUID.randomUUID()}"
            channels += channelID
            val ioBlock = blockIoPool()
            block = ioBlock
            sendAndCleanupTwice(scenario, content, channelID)
            assertFalse("IO barrier must remain held until explicitly released", ioBlock.timedOut.get())
            assertTrue(ownedDirectory.isDirectory)
            scenario.switchToAnotherSession()
            ioBlock.release.countDown()
            val (clientMsgNo, status) = awaitFailure(scenario, channelID)
            assertEquals(com.xinbida.wukongim.message.type.WKSendMsgResult.send_fail, status)
            assertEquals(clientMsgNo, scenario.latestMessageForChannel(channelID)?.first)
            assertFalse(ownedDirectory.exists())
            assertTrue(external.isFile)
            assertTrue("invalidated session must not upload", scenario.transport.credentialsFor(channelID) == null)
        } finally {
            block?.release?.countDown()
            if (channels.isNotEmpty()) finishUnfinishedSends(scenario, channels, emptySet())
            cleanupSentRows(scenario, channels)
            contents.forEach { runCatching { it.cleanupOwnedTemporarySource() } }
            scenario.close()
        }
    }

    @Test fun successfulSdkCallbackPersistsUrlBeforeUpdatingTheSameVisibleRow() {
        val scenario = Scenario()
        try {
            val view = scenario.row
            val binding = scenario.originalBinding
            scenario.progressAt100()
            scenario.instrumentation.runOnMainSync { scenario.transport.returnCredentials() }
            assertNotNull(scenario.transport.put)
            scenario.instrumentation.runOnMainSync {
                scenario.transport.put!!.onSuccess("ignored-cos-response")
                val savedBeforePostedUiEvent = scenario.persisted()
                assertEquals("https://files.test/object", (savedBeforePostedUiEvent!!.baseContentMsgModel as WKFileContent).url)
                assertTrue(view.findViewById<View>(R.id.contentLayout).isEnabled)
            }
            scenario.instrumentation.runOnMainSync { }
            val saved = scenario.persisted()
            assertNotNull(saved)
            assertEquals("https://files.test/object", (saved!!.baseContentMsgModel as WKFileContent).url)
            assertSame(view, scenario.row)
            assertSame(binding, view.findViewById<LinearLayout>(R.id.fileView).tag)
            assertTrue(view.findViewById<View>(R.id.contentLayout).isEnabled)
            assertTrue(view.findViewById<View>(R.id.fileActionButton).isEnabled)
            assertEquals(View.GONE, view.findViewById<View>(R.id.fileUploadProgressLayout).visibility)
            assertEquals(null, WKProgressManager.instance.getFileUploadTask(scenario.progressKey()))
            scenario.assertUploadSessionListenerRemoved()
        } finally { scenario.close() }
    }

    @Test fun queuedUploadTerminalStillHidesProgressAfterImmediateLocalOpen() {
        val scenario = Scenario(trustedSource = true)
        val openCount = AtomicInteger()
        try {
            scenario.progressAt100()
            scenario.instrumentation.runOnMainSync { scenario.transport.returnCredentials() }
            WKFileProvider.openFileForTests = { _, _ -> openCount.incrementAndGet() }
            scenario.instrumentation.runOnMainSync {
                scenario.transport.put!!.onSuccess("ignored")
                assertTrue(scenario.row.findViewById<View>(R.id.contentLayout).performClick())
            }
            scenario.instrumentation.waitForIdleSync()
            assertEquals(1, openCount.get())
            assertEquals(View.GONE, scenario.row.findViewById<View>(R.id.fileUploadProgressLayout).visibility)
            assertTrue(scenario.row.findViewById<View>(R.id.contentLayout).isEnabled)
        } finally {
            WKFileProvider.openFileForTests = null
            scenario.close()
        }
    }

    @Test fun credentialAndPutFailuresPersistSendFailAndTerminateTheSameRow() {
        listOf(true, false).forEach { credentialFails ->
            val scenario = Scenario()
            try {
                scenario.progressAt100()
                scenario.instrumentation.runOnMainSync {
                    scenario.transport.returnCredentials(ok = !credentialFails)
                    if (!credentialFails) scenario.transport.put!!.onError()
                }
                scenario.instrumentation.runOnMainSync { }
                scenario.assertUploadSessionListenerRemoved()
                assertEquals(com.xinbida.wukongim.message.type.WKSendMsgResult.send_fail, scenario.persisted()!!.status)
                assertEquals(WKFileProvider.formatFileSize((scenario.message.baseContentMsgModel as WKFileContent).size), scenario.row.findViewById<android.widget.TextView>(R.id.fileSizeTv).text.toString())
                assertTrue(scenario.row.findViewById<View>(R.id.contentLayout).isEnabled)
                assertTrue(scenario.row.findViewById<View>(R.id.fileActionButton).isEnabled)
                assertEquals(View.GONE, scenario.row.findViewById<View>(R.id.fileUploadProgressLayout).visibility)
                assertEquals(null, WKProgressManager.instance.getFileUploadTask(scenario.progressKey()))
                scenario.assertUploadSessionListenerRemoved()
            } finally { scenario.close() }
        }
    }

    @Test fun completionWhileDetachedIsRecoveredFromDatabaseOnReattach() {
        listOf(true, false).forEach { succeed ->
            val scenario = Scenario()
            try {
                scenario.instrumentation.runOnMainSync { scenario.root.removeView(scenario.row) }
                scenario.instrumentation.runOnMainSync {
                    scenario.transport.returnCredentials(ok = succeed)
                    if (succeed) scenario.transport.put!!.onSuccess("ignored")
                }
                scenario.assertUploadSessionListenerRemoved()
                scenario.instrumentation.runOnMainSync { scenario.root.addView(scenario.row) }
                scenario.instrumentation.runOnMainSync { }
                assertSame(scenario.originalBinding, scenario.row.findViewById<LinearLayout>(R.id.fileView).tag)
                assertEquals(succeed, scenario.row.findViewById<View>(R.id.contentLayout).isEnabled)
                assertEquals(succeed, scenario.row.findViewById<View>(R.id.fileActionButton).isEnabled)
                assertEquals(View.GONE, scenario.row.findViewById<View>(R.id.fileUploadProgressLayout).visibility)
            } finally { scenario.close() }
        }
    }

    @Test fun queuedTerminalViewWorkCannotUpdateAReboundMessage() {
        val scenario = Scenario()
        try {
            scenario.instrumentation.runOnMainSync {
                scenario.transport.returnCredentials()
                scenario.transport.put!!.onSuccess("ignored")
                val replacement = WKMsg().apply {
                    channelID = scenario.message.channelID; channelType = scenario.message.channelType
                    fromUID = WKConfig.getInstance().uid; clientSeq = scenario.message.clientSeq + 1
                    clientMsgNO = "replacement-${UUID.randomUUID()}"; type = WKContentType.WK_FILE
                    baseContentMsgModel = WKFileContent().apply { name = "replacement.txt"; extension = "txt"; size = 10; localPath = scenario.tempFile.absolutePath }
                }
                scenario.provider.refreshData(0, scenario.row, WKUIChatMsgItemEntity(null, replacement, null), WKChatIteMsgFromType.SEND)
            }
            scenario.instrumentation.runOnMainSync { }
            assertFalse(scenario.row.findViewById<View>(R.id.contentLayout).isEnabled)
            assertFalse(scenario.row.findViewById<View>(R.id.fileActionButton).isEnabled)
            assertEquals(View.VISIBLE, scenario.row.findViewById<View>(R.id.fileUploadProgressLayout).visibility)
        } finally { scenario.close() }
    }

    @Test fun terminalPostQueuedBeforeDetachIsRecoveredWhenTheSameRowReattaches() {
        val scenario = Scenario()
        try {
            scenario.instrumentation.runOnMainSync {
                scenario.transport.returnCredentials()
                scenario.transport.put!!.onSuccess("ignored")
                scenario.root.removeView(scenario.row)
                scenario.root.addView(scenario.row)
            }
            scenario.instrumentation.runOnMainSync { }
            assertSame(scenario.originalBinding, scenario.row.findViewById<LinearLayout>(R.id.fileView).tag)
            assertTrue(scenario.row.findViewById<View>(R.id.contentLayout).isEnabled)
            assertTrue(scenario.row.findViewById<View>(R.id.fileActionButton).isEnabled)
            assertEquals(View.GONE, scenario.row.findViewById<View>(R.id.fileUploadProgressLayout).visibility)
        } finally { scenario.close() }
    }

    @Test fun staleSessionPutCannotWriteOrReplaceTheNewSessionProgressListener() {
        val scenario = Scenario()
        val secondRow = android.view.LayoutInflater.from(scenario.activity).inflate(R.layout.chat_item_file, scenario.root, false)
        try {
            scenario.instrumentation.runOnMainSync { scenario.transport.returnCredentials() }
            assertNotNull(scenario.transport.put)
            val switched = arrayOfNulls<WKDBHelper>(1)
            scenario.instrumentation.runOnMainSync { switched[0] = scenario.switchToAnotherSession() }
            scenario.assertUploadSessionListenerRemoved()
            val newHelper = requireNotNull(switched[0])
            val replacement = WKMsg().apply {
                channelID = scenario.message.channelID; channelType = scenario.message.channelType
                fromUID = WKConfig.getInstance().uid; clientSeq = scenario.message.clientSeq
                clientMsgNO = "new-session-${UUID.randomUUID()}"; type = WKContentType.WK_FILE
                baseContentMsgModel = WKFileContent().apply { name = "new-session.txt"; extension = "txt"; size = 10; localPath = scenario.tempFile.absolutePath }
            }
            replacement.status = com.xinbida.wukongim.message.type.WKSendMsgResult.send_loading
            newHelper.insert("message", android.content.ContentValues().apply {
                put("client_seq", replacement.clientSeq); put("client_msg_no", replacement.clientMsgNO)
                put("status", replacement.status); put("channel_id", replacement.channelID)
                put("channel_type", replacement.channelType.toInt()); put("from_uid", replacement.fromUID)
                put("type", replacement.type); put("content", replacement.baseContentMsgModel!!.encodeMsg().toString())
            })
            scenario.instrumentation.runOnMainSync {
                scenario.root.addView(secondRow)
                scenario.provider.refreshData(0, secondRow, WKUIChatMsgItemEntity(null, replacement, null), WKChatIteMsgFromType.SEND)
                scenario.transport.put!!.onSuccess("stale-result")
            }
            val oldCursor = scenario.testDbHelper.rawQuery("select content,status from message where client_msg_no=?", arrayOf(scenario.message.clientMsgNO))
            try {
                assertTrue(oldCursor.moveToFirst())
                assertEquals("", org.json.JSONObject(oldCursor.getString(0)).optString("url"))
                assertEquals(com.xinbida.wukongim.message.type.WKSendMsgResult.send_fail, oldCursor.getInt(1))
            } finally { oldCursor.close() }
            val newCursor = newHelper.rawQuery("select client_msg_no,status from message where client_msg_no=?", arrayOf(replacement.clientMsgNO))
            try { assertTrue(newCursor.moveToFirst()); assertEquals(com.xinbida.wukongim.message.type.WKSendMsgResult.send_loading, newCursor.getInt(1)) } finally { newCursor.close() }

            scenario.instrumentation.runOnMainSync {
                scenario.root.removeView(scenario.row)
                scenario.root.addView(scenario.row)
            }
            val path = (replacement.baseContentMsgModel as WKFileContent).localPath
            FileRequestBody(File(path).asRequestBody("application/octet-stream".toMediaType()), WKProgressManager.FileUploadProgressKey(WKConfig.getInstance().uid, WKConfig.getInstance().getSessionSnapshot().version, replacement.clientMsgNO, replacement.clientSeq)).writeTo(Buffer())
            scenario.instrumentation.waitForIdleSync()
            assertFalse(secondRow.findViewById<View>(R.id.contentLayout).isEnabled)
            assertTrue(secondRow.findViewById<android.widget.TextView>(R.id.fileUploadProgressPercent).text.toString().contains("100"))
            assertFalse(scenario.row.findViewById<View>(R.id.contentLayout).isEnabled)
        } finally {
            scenario.instrumentation.runOnMainSync { scenario.root.removeView(secondRow) }
            scenario.close()
        }
    }

    @Test fun sessionSwitchBeforeCredentialsCallbackCancelsAndPersistsOnlyCapturedAccountFailure() {
        val scenario = Scenario()
        try {
            val switched = arrayOfNulls<WKDBHelper>(1)
            scenario.instrumentation.runOnMainSync { switched[0] = scenario.switchToAnotherSession() }
            assertEquals(com.xinbida.wukongim.message.type.WKSendMsgResult.send_fail, scenario.capturedStatusAndContent().first)
            scenario.assertUploadSessionListenerRemoved()
            scenario.instrumentation.runOnMainSync { scenario.transport.returnCredentials() }
            assertEquals(null, scenario.transport.put)
            val currentHelper = requireNotNull(switched[0])
            val cursor = currentHelper.rawQuery("select client_msg_no from message where client_seq=?", arrayOf(scenario.message.clientSeq.toString()))
            try { assertEquals(0, cursor.count) } finally { cursor.close() }
        } finally { scenario.close() }
    }

    @Test fun sameUidTokenChangeMakesLateRepeatedPutCallbacksInert() {
        val scenario = Scenario()
        try {
            scenario.instrumentation.runOnMainSync { scenario.transport.returnCredentials() }
            val putCallback = requireNotNull(scenario.transport.put)
            scenario.instrumentation.runOnMainSync {
                WKConfig.getInstance().token = "rotated-${UUID.randomUUID()}"
                scenario.root.removeView(scenario.row)
                scenario.root.addView(scenario.row)
            }
            assertEquals(com.xinbida.wukongim.message.type.WKSendMsgResult.send_fail, scenario.capturedStatusAndContent().first)
            scenario.assertUploadSessionListenerRemoved()
            scenario.instrumentation.waitForIdleSync()
            assertSame(scenario.originalBinding, scenario.row.findViewById<LinearLayout>(R.id.fileView).tag)
            assertEquals(WKFileProvider.formatFileSize((scenario.message.baseContentMsgModel as WKFileContent).size), scenario.row.findViewById<android.widget.TextView>(R.id.fileSizeTv).text.toString())
            assertEquals(View.GONE, scenario.row.findViewById<View>(R.id.fileUploadProgressLayout).visibility)
            assertFalse(scenario.row.findViewById<View>(R.id.contentLayout).isEnabled)
            assertFalse(scenario.row.findViewById<View>(R.id.fileActionButton).isEnabled)
            scenario.instrumentation.runOnMainSync {
                putCallback.onSuccess("late-success")
                putCallback.onError()
            }
            val captured = scenario.capturedStatusAndContent()
            assertEquals(com.xinbida.wukongim.message.type.WKSendMsgResult.send_fail, captured.first)
            assertEquals("", org.json.JSONObject(captured.second).optString("url"))
            scenario.instrumentation.waitForIdleSync()
            assertSame(scenario.originalBinding, scenario.row.findViewById<LinearLayout>(R.id.fileView).tag)
            assertEquals(WKFileProvider.formatFileSize((scenario.message.baseContentMsgModel as WKFileContent).size), scenario.row.findViewById<android.widget.TextView>(R.id.fileSizeTv).text.toString())
            assertEquals(View.GONE, scenario.row.findViewById<View>(R.id.fileUploadProgressLayout).visibility)
            assertFalse(scenario.row.findViewById<View>(R.id.contentLayout).isEnabled)
            assertFalse(scenario.row.findViewById<View>(R.id.fileActionButton).isEnabled)
        } finally { scenario.close() }
    }

    @Test fun accountSwitchAwayAndBackStillInvalidatesTheCapturedUpload() {
        val scenario = Scenario()
        try {
            scenario.instrumentation.runOnMainSync { scenario.transport.returnCredentials() }
            val callback = requireNotNull(scenario.transport.put)
            scenario.instrumentation.runOnMainSync {
                scenario.switchToAnotherSession()
                WKConfig.getInstance().uid = scenario.testUid
                WKConfig.getInstance().token = scenario.testToken
                scenario.app.uid = scenario.testUid; scenario.app.token = scenario.testToken
                scenario.dbHelperField.set(scenario.app, scenario.testDbHelper)
            }
            scenario.instrumentation.runOnMainSync { callback.onSuccess("must-not-apply") }
            val saved = scenario.capturedStatusAndContent()
            assertEquals(com.xinbida.wukongim.message.type.WKSendMsgResult.send_fail, saved.first)
            assertEquals("", org.json.JSONObject(saved.second).optString("url"))
        } finally { scenario.close() }
    }

    @Test fun sdkNoOpUidAndTokenSettersDoNotInvalidateCapturedSession() {
        val scenario = Scenario()
        try {
            val captured = scenario.app.captureSessionContext("", 0)
            scenario.app.setUid(scenario.app.uid)
            scenario.app.setToken(scenario.app.token)
            assertTrue(scenario.app.isSessionCurrent(captured))
        } finally { scenario.close() }
    }

    @Test fun sessionCaptureWaitsForCapturedMutationGateToRelease() {
        val scenario = Scenario()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val captureDone = CountDownLatch(1)
        var mutationThread: Thread? = null
        var captureThread: Thread? = null
        try {
            val captured = scenario.app.captureSessionContext("", 0)
            mutationThread = Thread {
                scenario.app.withCurrentSession(captured) {
                    entered.countDown()
                    if (!release.await(5, TimeUnit.SECONDS)) throw AssertionError("gate barrier timed out")
                }
            }.apply { start() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            captureThread = Thread { scenario.app.captureSessionContext("", 0); captureDone.countDown() }.apply { start() }
            assertFalse("session capture must wait while captured mutation holds the gate", captureDone.await(100, TimeUnit.MILLISECONDS))
            release.countDown()
            mutationThread!!.join(5000); captureThread!!.join(5000)
            assertFalse(mutationThread!!.isAlive); assertFalse(captureThread!!.isAlive)
            assertEquals(0L, captureDone.count)
        } finally { release.countDown(); mutationThread?.join(5000); captureThread?.join(5000); scenario.close() }
    }

    @Test fun capturedResendReactivatesPreviouslyDeletedConversation() {
        val scenario = Scenario()
        try {
            val channel = "deleted-conversation-${UUID.randomUUID()}"
            scenario.testDbHelper.insert("conversation", android.content.ContentValues().apply {
                put("channel_id", channel); put("channel_type", WKChannelType.GROUP)
                put("last_client_msg_no", "old"); put("last_msg_timestamp", 1L); put("last_msg_seq", 1L)
                put("unread_count", 0); put("is_deleted", 1); put("extra", "{}")
            })
            val msg = WKMsg().apply {
                channelID = channel; channelType = WKChannelType.GROUP; fromUID = scenario.testUid
                clientMsgNO = "resend-${UUID.randomUUID()}"; timestamp = System.currentTimeMillis(); messageSeq = 2
            }
            com.xinbida.wukongim.db.ConversationDbManager.getInstance()
                .insertOrUpdateWithMsg(msg, 0, scenario.testDbHelper)
            val cursor = scenario.testDbHelper.rawQuery("select is_deleted from conversation where channel_id=?", arrayOf(channel))
            try { assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0)) } finally { cursor.close() }
        } finally { scenario.close() }
    }

    @Test fun queuedCapturedUiCallbackIsDroppedIfSessionChangesBeforeMainDelivery() {
        val scenario = Scenario()
        val delivered = java.util.concurrent.atomic.AtomicInteger()
        val key = "captured-ui-${UUID.randomUUID()}"
        try {
            WKIM.getInstance().msgManager.addOnSendMsgCallback(key) { delivered.incrementAndGet() }
            scenario.capturedUiHook.set(null, Runnable { scenario.switchToAnotherSession() })
            scenario.instrumentation.runOnMainSync {
                WKIM.getInstance().msgManager.setSendMsgCallback(scenario.message, scenario.message.uploadSessionContext)
            }
            scenario.instrumentation.runOnMainSync { }
            assertEquals(0, delivered.get())
        } finally {
            scenario.capturedUiHook.set(null, null)
            WKIM.getInstance().msgManager.removeSendMsgCallBack(key)
            scenario.close()
        }
    }

    @Test fun capturedSdkSendReconnectsForMissingSocketRefusalAndDisconnectedStateButWaitsDuringSync() {
        val scenario = Scenario(trustedSource = true)
        val sends = java.util.concurrent.atomic.AtomicInteger()
        val reconnects = java.util.concurrent.atomic.AtomicInteger()
        val hasSocket = java.util.concurrent.atomic.AtomicBoolean(false)
        val result = java.util.concurrent.atomic.AtomicInteger(0)
        val fake = object : WKConnection.CapturedFileSendTransportForTests {
            override fun hasSocket() = hasSocket.get()
            override fun send(msg: WKSendMsg): Int { sends.incrementAndGet(); return result.get() }
            override fun reconnect() { reconnects.incrementAndGet() }
        }
        try {
            WKConnection.setCapturedFileSendTransportForTests(fake)
            scenario.connectStatusField.setInt(WKConnection.getInstance(), WKConnectStatus.success)
            scenario.instrumentation.runOnMainSync { scenario.transport.returnCredentials() }
            val put = requireNotNull(scenario.transport.put)
            put.onSuccess("url")
            assertEquals(1, reconnects.get()) // no socket
            hasSocket.set(true); result.set(0)
            val queued = (scenario.sendingMapField.get(WKConnection.getInstance()) as Map<*, *>)[scenario.message.clientSeq.toInt()]
            val sendQueued = WKConnection::class.java.getDeclaredMethod("sendQueuedMessage", WKSendingMsg::class.java).apply { isAccessible = true }
            sendQueued.invoke(WKConnection.getInstance(), queued)
            assertEquals(1, sends.get()); assertEquals(2, reconnects.get()) // send returned 0
            scenario.connectStatusField.setInt(WKConnection.getInstance(), WKConnectStatus.noNetwork)
            result.set(1)
            sendQueued.invoke(WKConnection.getInstance(), queued)
            assertEquals(3, reconnects.get())
            scenario.connectStatusField.setInt(WKConnection.getInstance(), WKConnectStatus.syncMsg)
            sendQueued.invoke(WKConnection.getInstance(), queued)
            assertEquals("syncMsg must defer without reconnecting", 3, reconnects.get())
        } finally { scenario.close() }
    }

    @Test fun timeoutPersistsFailureInCapturedHelperAndQueueReplacementIsNeverOverwritten() {
        val scenario = Scenario()
        val refreshedStatus = AtomicReference<Int?>(null)
        val refreshArrived = CountDownLatch(1)
        val refreshKey = "captured-timeout-${UUID.randomUUID()}"
        try {
            WKIM.getInstance().msgManager.addOnRefreshMsgListener(refreshKey) { msg, _ -> refreshedStatus.set(msg.status); refreshArrived.countDown() }
            scenario.instrumentation.runOnMainSync {
                scenario.transport.returnCredentials()
                scenario.transport.put!!.onSuccess("queued-for-timeout")
            }
            @Suppress("UNCHECKED_CAST") val queue = scenario.sendingMapField.get(WKConnection.getInstance()) as MutableMap<Int, WKSendingMsg>
            val current = requireNotNull(queue[scenario.message.clientSeq.toInt()])
            current.sendCount = 5
            current.isCanResend = true
            WKConnection.getInstance().checkSendingMsg()
            assertEquals(com.xinbida.wukongim.message.type.WKSendMsgResult.send_fail, scenario.capturedStatusAndContent().first)
            assertTrue("persisted timeout should refresh the failed captured row", refreshArrived.await(5, TimeUnit.SECONDS))
            assertEquals(com.xinbida.wukongim.message.type.WKSendMsgResult.send_fail, refreshedStatus.get())

            val sentinel = WKSendingMsg(1, WKSendMsg().apply { clientSeq = scenario.message.clientSeq.toInt() }, true)
            current.isCanResend = true
            queue[scenario.message.clientSeq.toInt()] = current
            scenario.queueIdentityHook.set(null, Runnable { queue[scenario.message.clientSeq.toInt()] = sentinel })
            WKConnection.getInstance().checkSendingMsg()
            assertSame(sentinel, queue[scenario.message.clientSeq.toInt()])
        } finally { WKIM.getInstance().msgManager.removeRefreshMsgListener(refreshKey); scenario.close() }
    }

    @Test fun acknowledgementThatWinsBeforeCapturedFailureCannotBePublishedAsFailed() {
        listOf(false, true).forEach { ackAfterFailureWrite ->
            val scenario = Scenario(trustedSource = true)
            val refreshStatuses = java.util.concurrent.CopyOnWriteArrayList<Int>()
            val refreshKey = "ack-wins-${UUID.randomUUID()}"
            try {
                WKIM.getInstance().msgManager.addOnRefreshMsgListener(refreshKey) { msg, _ -> refreshStatuses.add(msg.status) }
                val acknowledge = Runnable {
                    scenario.testDbHelper.update("message",
                        android.content.ContentValues().apply { put("status", com.xinbida.wukongim.message.type.WKSendMsgResult.send_success) },
                        "client_msg_no=? AND client_seq=?", arrayOf(scenario.message.clientMsgNO, scenario.message.clientSeq.toString()))
                }
                (if (ackAfterFailureWrite) scenario.failureAfterWriteHook else scenario.failureWriteHook).set(null, acknowledge)
                val context = requireNotNull(scenario.message.uploadSessionContext)
                val fail = WKConnection::class.java.getDeclaredMethod("failCapturedFileMessage",
                    WKIMApplication.SessionContext::class.java, WKMsg::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
                fail.invoke(WKConnection.getInstance(), context, scenario.message, true)
                assertEquals(com.xinbida.wukongim.message.type.WKSendMsgResult.send_success, scenario.capturedStatusAndContent().first)
                scenario.instrumentation.waitForIdleSync()
                assertFalse("an ACKed row must never be delivered as send_fail", refreshStatuses.contains(com.xinbida.wukongim.message.type.WKSendMsgResult.send_fail))
                assertEquals(com.xinbida.wukongim.message.type.WKSendMsgResult.send_loading, scenario.callbackMessage.get()!!.status)
            } finally {
                scenario.failureWriteHook.set(null, null); scenario.failureAfterWriteHook.set(null, null)
                WKIM.getInstance().msgManager.removeRefreshMsgListener(refreshKey); scenario.close()
            }
        }
    }

    @Test fun capturedFailureRefreshReReadsAckedRowAfterItsFirstReadback() {
        val scenario = Scenario(trustedSource = true)
        val statuses = java.util.concurrent.CopyOnWriteArrayList<Int>()
        val successDelivered = CountDownLatch(1)
        val key = "ack-after-read-${UUID.randomUUID()}"
        try {
            WKIM.getInstance().msgManager.addOnRefreshMsgListener(key) { msg, _ ->
                statuses.add(msg.status)
                if (msg.status == com.xinbida.wukongim.message.type.WKSendMsgResult.send_success) successDelivered.countDown()
            }
            scenario.afterFailureReadbackHook.set(null, Runnable {
                scenario.testDbHelper.update("message",
                    android.content.ContentValues().apply { put("status", com.xinbida.wukongim.message.type.WKSendMsgResult.send_success) },
                    "client_msg_no=? AND client_seq=?", arrayOf(scenario.message.clientMsgNO, scenario.message.clientSeq.toString()))
                WKIM.getInstance().msgManager.setRefreshMsg(requireNotNull(scenario.persisted()), true)
            })
            val context = requireNotNull(scenario.message.uploadSessionContext)
            val fail = WKConnection::class.java.getDeclaredMethod("failCapturedFileMessage",
                WKIMApplication.SessionContext::class.java, WKMsg::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
            fail.invoke(WKConnection.getInstance(), context, scenario.message, false)
            assertTrue("ACK state should be delivered", successDelivered.await(5, TimeUnit.SECONDS))
            scenario.instrumentation.waitForIdleSync()
            assertEquals(com.xinbida.wukongim.message.type.WKSendMsgResult.send_success, statuses.last())
            assertFalse("stale failure must not be delivered after ACK readback", statuses.contains(com.xinbida.wukongim.message.type.WKSendMsgResult.send_fail))
            assertEquals(com.xinbida.wukongim.message.type.WKSendMsgResult.send_success, scenario.capturedStatusAndContent().first)
        } finally {
            scenario.afterFailureReadbackHook.set(null, null)
            WKIM.getInstance().msgManager.removeRefreshMsgListener(key)
            scenario.close()
        }
    }

    @Test fun tokenChangeWhileDetachedRecoversPersistedFailureWithoutRegisteringStaleProgress() {
        val scenario = Scenario()
        try {
            scenario.instrumentation.runOnMainSync { scenario.transport.returnCredentials() }
            val putCallback = requireNotNull(scenario.transport.put)
            scenario.instrumentation.runOnMainSync { scenario.root.removeView(scenario.row) }
            assertFalse(scenario.isProgressRegistered(scenario.progressKey()))
            scenario.instrumentation.runOnMainSync { WKConfig.getInstance().token = "rotated-${UUID.randomUUID()}" }
            assertEquals(com.xinbida.wukongim.message.type.WKSendMsgResult.send_fail, scenario.capturedStatusAndContent().first)
            scenario.assertUploadSessionListenerRemoved()
            scenario.instrumentation.runOnMainSync { scenario.root.addView(scenario.row) }
            scenario.instrumentation.waitForIdleSync()
            assertFalse(scenario.isProgressRegistered(scenario.progressKey()))
            assertSame(scenario.originalBinding, scenario.row.findViewById<LinearLayout>(R.id.fileView).tag)
            assertEquals(WKFileProvider.formatFileSize((scenario.message.baseContentMsgModel as WKFileContent).size), scenario.row.findViewById<android.widget.TextView>(R.id.fileSizeTv).text.toString())
            assertEquals(View.GONE, scenario.row.findViewById<View>(R.id.fileUploadProgressLayout).visibility)
            assertFalse(scenario.row.findViewById<View>(R.id.contentLayout).isEnabled)
            assertFalse(scenario.row.findViewById<View>(R.id.fileActionButton).isEnabled)
            scenario.instrumentation.runOnMainSync { putCallback.onSuccess("late-success") }
            assertEquals("", org.json.JSONObject(scenario.capturedStatusAndContent().second).optString("url"))
        } finally { scenario.close() }
    }

    @Test fun closedCapturedHelperDefersPersistenceAndLateCallbacksDoNotTouchNewAccount() {
        val scenario = Scenario()
        try {
            scenario.instrumentation.runOnMainSync { scenario.transport.returnCredentials() }
            val putCallback = requireNotNull(scenario.transport.put)
            scenario.testDbHelper.close()
            val switched = arrayOfNulls<WKDBHelper>(1)
            scenario.instrumentation.runOnMainSync { switched[0] = scenario.switchToAnotherSession() }
            scenario.assertUploadSessionListenerRemoved()
            scenario.instrumentation.runOnMainSync {
                putCallback.onSuccess("late-success")
                putCallback.onError()
            }
            val cursor = requireNotNull(switched[0]).rawQuery("select client_msg_no from message where client_seq=?", arrayOf(scenario.message.clientSeq.toString()))
            try { assertEquals(0, cursor.count) } finally { cursor.close() }
        } finally { scenario.close() }
    }

    @Test fun sessionSwitchAtBothCapturedWriteBoundariesCannotMutateNewAccountOrReplaceItsQueueEntry() {
        listOf(false, true).forEach { pauseAfterCapturedWrite ->
          listOf(false, true).forEach { callbackSucceeds ->
            val scenario = Scenario()
            val resume = CountDownLatch(1)
            var callbackThread: Thread? = null
            try {
                scenario.instrumentation.runOnMainSync { scenario.transport.returnCredentials() }
                val putCallback = requireNotNull(scenario.transport.put)
                val reached = CountDownLatch(1)
                val callbackFailure = AtomicReference<Throwable?>(null)
                val hook = Runnable {
                    reached.countDown()
                    if (!resume.await(5, TimeUnit.SECONDS)) throw AssertionError("session switch barrier timed out")
                }
                (if (pauseAfterCapturedWrite) scenario.afterCapturedWriteHook else scenario.beforeCapturedWriteHook).set(null, hook)
                callbackThread = Thread {
                    try {
                        if (callbackSucceeds) putCallback.onSuccess("barrier-success") else putCallback.onError()
                    } catch (error: Throwable) { callbackFailure.set(error) }
                }
                callbackThread!!.start()
                assertTrue("upload callback did not reach barrier", reached.await(5, TimeUnit.SECONDS))

                val switched = arrayOfNulls<WKDBHelper>(1)
                val sentinelEntry = WKSendingMsg(1, WKSendMsg().apply { clientSeq = scenario.message.clientSeq.toInt() }, true)
                @Suppress("UNCHECKED_CAST")
                val sendingMap = scenario.sendingMapField.get(WKConnection.getInstance()) as MutableMap<Int, Any>
                scenario.instrumentation.runOnMainSync {
                    switched[0] = scenario.switchToAnotherSession()
                    val helper = requireNotNull(switched[0])
                    helper.insert("message", android.content.ContentValues().apply {
                        put("client_seq", scenario.message.clientSeq); put("client_msg_no", scenario.message.clientMsgNO)
                        put("status", com.xinbida.wukongim.message.type.WKSendMsgResult.send_loading)
                        put("channel_id", scenario.message.channelID); put("channel_type", scenario.message.channelType.toInt())
                        put("from_uid", WKConfig.getInstance().uid); put("type", WKContentType.WK_FILE); put("content", "sentinel")
                    })
                    sendingMap[scenario.message.clientSeq.toInt()] = sentinelEntry
                }
                resume.countDown()
                callbackThread!!.join(5000)
                assertFalse("upload callback did not finish", callbackThread!!.isAlive)
                assertEquals(null, callbackFailure.get())

                val helper = requireNotNull(switched[0])
                val cursor = helper.rawQuery("select client_msg_no,status,content from message where client_seq=?", arrayOf(scenario.message.clientSeq.toString()))
                try {
                    assertTrue(cursor.moveToFirst())
                    assertEquals(scenario.message.clientMsgNO, cursor.getString(0))
                    assertEquals(com.xinbida.wukongim.message.type.WKSendMsgResult.send_loading, cursor.getInt(1))
                    assertEquals("sentinel", cursor.getString(2))
                } finally { cursor.close() }
                assertSame(sentinelEntry, sendingMap[scenario.message.clientSeq.toInt()])
            } finally { resume.countDown(); callbackThread?.join(5000); scenario.close() }
          }
        }
    }

    @Test fun sessionInvalidationAtEveryPreSendCheckFailsOnlyTheCapturedMessage() {
        val hookFields = listOf(
            "fileUploadAfterSnapshotSessionCheckHookForTests",
            "fileUploadBeforeFinishSendSessionCheckHookForTests",
            "fileUploadAfterSenderLookupHookForTests"
        )
        hookFields.forEach { hookName ->
            val scenario = Scenario()
            val reached = CountDownLatch(1)
            val resume = CountDownLatch(1)
            val callbackDone = CountDownLatch(1)
            val callbackFailure = AtomicReference<Throwable?>(null)
            val hook = WKConnection::class.java.getDeclaredField(hookName).apply { isAccessible = true }
            val refreshStatuses = java.util.concurrent.CopyOnWriteArrayList<Int>()
            val newAccountInsertCount = AtomicInteger()
            val switched = java.util.concurrent.atomic.AtomicBoolean(false)
            val refreshKey = "session-failure-ui-${UUID.randomUUID()}"
            val insertKey = "session-failure-insert-${UUID.randomUUID()}"
            var sent: WKMsg? = null
            try {
                WKIM.getInstance().msgManager.addOnRefreshMsgListener(refreshKey) { msg, _ ->
                    if (msg.clientMsgNO == sent?.clientMsgNO) refreshStatuses += msg.status
                }
                WKIM.getInstance().msgManager.addOnSendMsgCallback(insertKey) { msg ->
                    if (switched.get() && msg.clientMsgNO == sent?.clientMsgNO) newAccountInsertCount.incrementAndGet()
                }
                hook.set(null, Runnable {
                    reached.countDown()
                    if (!resume.await(8, TimeUnit.SECONDS)) throw AssertionError("session invalidation barrier timed out at $hookName")
                })
                val channelID = "session-race-$hookName-${UUID.randomUUID()}"
                val observed = CallbackObservedFileContent(trustedTestContent(scenario, "$hookName.txt"), callbackDone, callbackFailure)
                sent = scenario.sendNewFileMessage(channelID, observed)
                assertTrue("send did not reach $hookName", reached.await(5, TimeUnit.SECONDS))
                val (newHelper, sentinel) = insertSessionSentinel(scenario, requireNotNull(sent))
                switched.set(true)
                resume.countDown()
                val failedContent = awaitMessageStatus(scenario, requireNotNull(sent), com.xinbida.wukongim.message.type.WKSendMsgResult.send_fail)
                assertTrue("trusted attachment callback did not exit", callbackDone.await(5, TimeUnit.SECONDS))
                assertEquals(null, callbackFailure.get())
                scenario.instrumentation.waitForIdleSync()
                assertEquals(com.xinbida.wukongim.message.type.WKSendMsgResult.send_fail, scenario.capturedStatusAndContent(requireNotNull(sent)).first)
                assertTrue("failed captured row should not be replaced by a new-account row", failedContent.isNotBlank())
                assertEquals(null, scenario.transport.credentialsFor(channelID))
                @Suppress("UNCHECKED_CAST")
                val sendingMap = scenario.sendingMapField.get(WKConnection.getInstance()) as MutableMap<Int, Any>
                assertSame(sentinel, sendingMap[sent!!.clientSeq.toInt()])
                val cursor = newHelper.rawQuery("select status,content from message where client_msg_no=? and client_seq=?", arrayOf(sent!!.clientMsgNO, sent!!.clientSeq.toString()))
                try {
                    assertTrue(cursor.moveToFirst())
                    assertEquals(com.xinbida.wukongim.message.type.WKSendMsgResult.send_loading, cursor.getInt(0))
                    assertEquals("session-sentinel", cursor.getString(1))
                } finally { cursor.close() }
                assertFalse("stale callback must not notify the current account", refreshStatuses.contains(com.xinbida.wukongim.message.type.WKSendMsgResult.send_fail))
                assertEquals("stale callback must not insert into the new account UI", 0, newAccountInsertCount.get())
            } finally {
                resume.countDown()
                hook.set(null, null)
                WKIM.getInstance().msgManager.removeRefreshMsgListener(refreshKey)
                WKIM.getInstance().msgManager.removeSendMsgCallBack(insertKey)
                sent?.let {
                    assertTrue("trusted attachment callback must exit before closing the captured database", callbackDone.await(5, TimeUnit.SECONDS))
                }
                scenario.close()
            }
        }
    }

    private fun runUploadLockTimeoutAcknowledgementRace(ackWins: Boolean) {
        val scenario = Scenario()
        val hookReached = CountDownLatch(1)
        val callbackDone = CountDownLatch(1)
        val callbackFailure = AtomicReference<Throwable?>(null)
        val statuses = java.util.concurrent.CopyOnWriteArrayList<Int>()
        val sentRef = AtomicReference<WKMsg?>(null)
        val refreshKey = "lock-timeout-refresh-${UUID.randomUUID()}"
        var held: LockHold? = null
        var callbackThread: Thread? = null
        val afterWriteHook = Runnable { hookReached.countDown() }
        val channelID = "upload-lock-timeout-${UUID.randomUUID()}"
        try {
            WKIM.getInstance().msgManager.addOnRefreshMsgListener(refreshKey) { msg, _ ->
                if (msg.clientMsgNO == sentRef.get()?.clientMsgNO) statuses += msg.status
            }
            scenario.afterCapturedWriteHook.set(null, afterWriteHook)
            val sent = scenario.sendNewFileMessage(channelID, trustedTestContent(scenario, "lock-timeout.txt"))
            sentRef.set(sent)
            val credentials = awaitCredential(scenario, channelID)
            credentials.callback.onResult("https://upload.test/object", "https://files.test/object", "application/octet-stream", null)
            val put = awaitPut(scenario, credentials.path)
            held = holdConnectionLock(scenario)
            callbackThread = Thread {
                try { put.callback.onSuccess("ignored") } catch (error: Throwable) { callbackFailure.set(error) }
                finally { callbackDone.countDown() }
            }
            callbackThread!!.start()
            assertTrue("upload callback did not persist URL before lock acquisition", hookReached.await(5, TimeUnit.SECONDS))
            val contentBeforeLockOutcome = scenario.capturedStatusAndContent(sent).second
            assertEquals("https://files.test/object", org.json.JSONObject(contentBeforeLockOutcome).optString("url"))
            if (ackWins) {
                scenario.testDbHelper.update("message", android.content.ContentValues().apply {
                    put("status", com.xinbida.wukongim.message.type.WKSendMsgResult.send_success)
                }, "client_msg_no=? AND client_seq=?", arrayOf(sent.clientMsgNO, sent.clientSeq.toString()))
                WKIM.getInstance().msgManager.setRefreshMsg(requireNotNull(WKIM.getInstance().msgManager.getWithClientMsgNO(sent.clientMsgNO)), true)
            }
            assertTrue("upload callback did not finish its real timed lock attempt", callbackDone.await(7, TimeUnit.SECONDS))
            releaseLock(requireNotNull(held))
            callbackThread!!.join(5000)
            assertFalse("upload callback worker must exit before the test database closes", callbackThread!!.isAlive)
            assertEquals(null, callbackFailure.get())
            val expected = if (ackWins) com.xinbida.wukongim.message.type.WKSendMsgResult.send_success else com.xinbida.wukongim.message.type.WKSendMsgResult.send_fail
            awaitMessageStatus(scenario, sent, expected)
            scenario.instrumentation.waitForIdleSync()
            val persisted = scenario.capturedStatusAndContent(sent)
            assertEquals(expected, persisted.first)
            assertEquals("https://files.test/object", org.json.JSONObject(persisted.second).optString("url"))
            assertNoCapturedQueueEntry(scenario, sent)
            val key = WKProgressManager.FileUploadProgressKey(scenario.uploadSession.uid, scenario.uploadSession.version, sent.clientMsgNO, sent.clientSeq)
            assertEquals(null, WKProgressManager.instance.getFileUploadTask(key))
            if (ackWins) {
                assertTrue(statuses.contains(com.xinbida.wukongim.message.type.WKSendMsgResult.send_success))
                assertFalse("ACKed message must not publish a failure refresh", statuses.contains(com.xinbida.wukongim.message.type.WKSendMsgResult.send_fail))
            } else {
                assertTrue("captured failure status should refresh the current row", statuses.contains(com.xinbida.wukongim.message.type.WKSendMsgResult.send_fail))
                val original = requireNotNull(WKIM.getInstance().msgManager.getWithClientMsgNO(sent.clientMsgNO))
                val retry = requireNotNull(WKFileContent.forResend(scenario.context, original,
                    scenario.config.getSessionSnapshot(), com.chat.base.config.WKApiConfig.baseUrl))
                assertEquals("https://files.test/object", retry.url)
                val credentialCount = scenario.transport.credentialRequests.size
                val retryMsg = WKMsg().apply {
                    this.channelID = original.channelID; channelType = original.channelType
                    setting = original.setting; header = original.header; type = original.type
                    content = original.content; baseContentMsgModel = retry; fromUID = scenario.uploadSession.uid
                }
                scenario.instrumentation.runOnMainSync {
                    WKIM.getInstance().msgManager.deleteWithClientMsgNO(original.clientMsgNO)
                    WKIM.getInstance().msgManager.sendMessage(retryMsg)
                }
                scenario.trackMessage(retryMsg)
                val queueDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                @Suppress("UNCHECKED_CAST")
                val sendingMap = scenario.sendingMapField.get(WKConnection.getInstance()) as MutableMap<Int, Any>
                while (System.nanoTime() < queueDeadline && sendingMap[retryMsg.clientSeq.toInt()] == null) Thread.sleep(10)
                assertNotNull("resend should enter the captured session queue", sendingMap[retryMsg.clientSeq.toInt()])
                assertEquals(com.xinbida.wukongim.message.type.WKSendMsgResult.send_loading,
                    scenario.capturedStatusAndContent(retryMsg).first)
                assertEquals(retryMsg.clientSeq.toInt(),
                    (sendingMap[retryMsg.clientSeq.toInt()] as WKSendingMsg).wkSendMsg.clientSeq)
                assertEquals("https://files.test/object", org.json.JSONObject(scenario.capturedStatusAndContent(retryMsg).second).optString("url"))
                assertEquals("known URL retry must bypass credentials", credentialCount, scenario.transport.credentialRequests.size)
            }
        } finally {
            held?.release?.countDown()
            callbackThread?.join(5000)
            held?.let { if (it.thread.isAlive) releaseLock(it) }
            callbackThread?.join(5000)
            callbackThread?.let { assertFalse("upload callback worker must exit before the test database closes", it.isAlive) }
            scenario.afterCapturedWriteHook.set(null, null)
            WKIM.getInstance().msgManager.removeRefreshMsgListener(refreshKey)
            scenario.close()
        }
    }

    @Test fun uploadCompletionLockTimeoutFailsPersistedMessageAndAllowsUrlRetry() = runUploadLockTimeoutAcknowledgementRace(false)

    @Test fun ackDuringUploadCompletionLockTimeoutPreservesSuccessAndSuppressesFailureUi() = runUploadLockTimeoutAcknowledgementRace(true)

    @Test fun directRemoteFileQueueLockTimeoutFailsCapturedMessageWithoutUploading() {
        val scenario = Scenario()
        val statuses = java.util.concurrent.CopyOnWriteArrayList<Int>()
        val msgRef = AtomicReference<WKMsg?>(null)
        val listenerKey = "direct-lock-timeout-${UUID.randomUUID()}"
        var held: LockHold? = null
        var sent: WKMsg? = null
        try {
            WKIM.getInstance().msgManager.addOnRefreshMsgListener(listenerKey) { msg, _ ->
                if (msg.clientMsgNO == msgRef.get()?.clientMsgNO) statuses += msg.status
            }
            held = holdConnectionLock(scenario)
            val content = WKFileContent().apply { name = "remote.txt"; extension = "txt"; size = 10; url = "https://files.test/existing" }
            sent = WKMsg().apply {
                channelID = "direct-lock-timeout-${UUID.randomUUID()}"; channelType = WKChannelType.GROUP
                fromUID = scenario.uploadSession.uid; type = WKContentType.WK_FILE; baseContentMsgModel = content
            }
            msgRef.set(sent)
            WKConnection.getInstance().sendMessage(sent!!)
            scenario.trackMessage(sent!!)
            releaseLock(requireNotNull(held))
            val persisted = scenario.capturedStatusAndContent(sent!!)
            assertEquals(com.xinbida.wukongim.message.type.WKSendMsgResult.send_fail, persisted.first)
            assertEquals("https://files.test/existing", org.json.JSONObject(persisted.second).optString("url"))
            assertNoCapturedQueueEntry(scenario, sent!!)
            assertEquals(null, scenario.transport.credentialsFor(sent!!.channelID))
            scenario.instrumentation.waitForIdleSync()
            assertTrue(statuses.contains(com.xinbida.wukongim.message.type.WKSendMsgResult.send_fail))
        } finally {
            held?.let { if (it.thread.isAlive) releaseLock(it) }
            WKIM.getInstance().msgManager.removeRefreshMsgListener(listenerKey)
            scenario.close()
        }
    }
}
