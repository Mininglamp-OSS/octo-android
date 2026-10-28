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
import com.chat.uikit.R
import com.chat.uikit.chat.provider.DocumentSharePreviewTestActivity
import com.chat.uikit.chat.provider.WKFileProvider
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.WKIMApplication
import com.xinbida.wukongim.db.WKDBHelper
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelType
import com.xinbida.wukongim.entity.WKMsg
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okio.Buffer

/** Exercises the SDK's real send/attachment callback and database path; only the network edges are faked. */
class FileUploadPipelineTest {
    internal class FakeTransport : WKSendMsgUtils.FileUploadTransport {
        @Volatile var credentials: WKUploader.IGetUploadCredentials? = null
        @Volatile var put: WKUploader.IUploadBack? = null
        val credentialsReady = CountDownLatch(1)
        override fun getCredentials(channelID: String, channelType: Byte, path: String, callback: WKUploader.IGetUploadCredentials) { credentials = callback; credentialsReady.countDown() }
        override fun put(uploadUrl: String, path: String, contentType: String, contentDisposition: String?, tag: Any, callback: WKUploader.IUploadBack) { put = callback }
        fun returnCredentials(ok: Boolean = true) {
            requireNotNull(credentials).onResult(if (ok) "https://upload.test/object" else null, if (ok) "https://files.test/object" else null, "application/octet-stream", null)
        }
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
            assertTrue(row.findViewById<android.widget.TextView>(R.id.fileUploadProgressPercent).text.toString().contains("100"))
            assertFalse(row.findViewById<View>(R.id.contentLayout).isEnabled)
            assertFalse(row.findViewById<View>(R.id.fileActionButton).isEnabled)
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
            WKProgressManager.instance.unregisterProgress(progressKey())
            @Suppress("UNCHECKED_CAST") (sendingMapField.get(WKConnection.getInstance()) as? MutableMap<Int, Any?>)?.remove(message.clientSeq.toInt())
            WKSendMsgUtils.setFileUploadTransportForTests(null)
            WKIM.getInstance().msgManager.removeSendMsgCallBack(sendCallbackKey)
            beforeCapturedWriteHook.set(null, null); afterCapturedWriteHook.set(null, null)
            capturedUiHook.set(null, null); queueIdentityHook.set(null, null); sdkTransportField.set(null, oldSdkTransport)
            afterFailureReadbackHook.set(null, null)
            failureWriteHook.set(null, null)
            failureAfterWriteHook.set(null, null)
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
                assertFalse(view.findViewById<View>(R.id.contentLayout).isEnabled)
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
            scenario.assertUploadSessionListenerRemoved()
        } finally { scenario.close() }
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
                assertEquals(scenario.context.getString(R.string.doc_share_upload_failed), scenario.row.findViewById<android.widget.TextView>(R.id.fileSizeTv).text.toString())
                assertFalse(scenario.row.findViewById<View>(R.id.contentLayout).isEnabled)
                assertFalse(scenario.row.findViewById<View>(R.id.fileActionButton).isEnabled)
                assertEquals(View.GONE, scenario.row.findViewById<View>(R.id.fileUploadProgressLayout).visibility)
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
            assertEquals(scenario.context.getString(R.string.doc_share_upload_failed), scenario.row.findViewById<android.widget.TextView>(R.id.fileSizeTv).text.toString())
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
            assertEquals(scenario.context.getString(R.string.doc_share_upload_failed), scenario.row.findViewById<android.widget.TextView>(R.id.fileSizeTv).text.toString())
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
            assertEquals(scenario.context.getString(R.string.doc_share_upload_failed), scenario.row.findViewById<android.widget.TextView>(R.id.fileSizeTv).text.toString())
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
}
