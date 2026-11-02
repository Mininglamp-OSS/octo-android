package com.octoim.app

import android.app.Instrumentation
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chat.base.WKBaseApplication
import com.chat.base.config.WKApiConfig
import com.chat.base.config.WKConfig
import com.chat.base.msgcontent.WKFileContent
import com.chat.base.utils.TrustedLocalFileStore
import com.chat.uikit.message.MsgModel
import com.chat.uikit.chat.manager.WKSendMsgUtils
import com.chat.base.net.ud.WKUploader
import com.chat.uikit.WKUIKitApplication
import com.chat.uikit.chat.ChooseChatActivity
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.WKIMApplication
import com.xinbida.wukongim.db.WKDBHelper
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelType
import com.xinbida.wukongim.entity.WKMsg
import com.xinbida.wukongim.interfaces.ISendMsgCallBackListener
import com.xinbida.wukongim.message.WKConnection
import com.xinbida.wukongim.message.type.WKConnectStatus
import com.xinbida.wukongim.msgmodel.WKTextContent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.io.File
import java.lang.reflect.Proxy
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger

/** Covers the exported share entry, registered chooser endpoint, and the visible chooser confirmation flow. */
@RunWith(AndroidJUnit4::class)
class ShareReceiveActivityTest {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context by lazy { instrumentation.targetContext }
    private val config = WKConfig.getInstance()
    private val app = WKIMApplication.getInstance()
    private val unique = UUID.randomUUID().toString().replace("-", "")
    private val uid = "share-test-$unique"
    private val channelId = "share-target-$unique"
    private val sharedText = "shared-through-chooser-$unique"
    private val callbackKey = "share-ui-test-$unique"
    private val fileTransportField = WKSendMsgUtils::class.java.getDeclaredField("fileUploadTransport").apply { isAccessible = true }
    private val fileTransportBefore = fileTransportField.get(null)
    private val fileSendCallbacks = CopyOnWriteArrayList<WKMsg>()
    private val fileSendLatch = AtomicReference(CountDownLatch(1))
    private val expectedFileMessages = AtomicInteger()
    private val fileConfirmationStarted = java.util.concurrent.atomic.AtomicBoolean(false)
    private val fixtureFiles = CopyOnWriteArrayList<File>()
    private val requestOwnedDirectories = CopyOnWriteArrayList<File>()
    private val textWaiters = ConcurrentHashMap<String, CountDownLatch>()
    private val textCallbacks = CopyOnWriteArrayList<WKMsg>()
    private val helperField = WKIMApplication::class.java.getDeclaredField("mDbHelper").apply { isAccessible = true }
    private val helperCtor = WKDBHelper::class.java.getDeclaredConstructor(android.content.Context::class.java, String::class.java).apply { isAccessible = true }
    private val uidField = WKDBHelper::class.java.getDeclaredField("uid").apply { isAccessible = true }
    private val dbNameField = WKDBHelper::class.java.getDeclaredField("myDBName").apply { isAccessible = true }
    private val openHelperField = WKDBHelper::class.java.getDeclaredField("openHelper").apply { isAccessible = true }
    private val sessionVersionField = WKConfig::class.java.getDeclaredField("sessionVersion").apply { isAccessible = true }
    private val connectStatusField = WKConnection::class.java.getDeclaredField("connectStatus").apply { isAccessible = true }
    private val sendingMapField = WKConnection::class.java.getDeclaredField("sendingMsgHashMap").apply { isAccessible = true }
    private val connectionLock = WKConnection::class.java.getField("connectionLock").get(WKConnection.getInstance()) as java.util.concurrent.locks.ReentrantLock
    private val contentRegistryField = WKIM.getInstance().msgManager.javaClass.getDeclaredField("customContentMsgList").apply { isAccessible = true }
    private val contentRegistryBefore = (contentRegistryField.get(WKIM.getInstance().msgManager) as? List<*>)?.toList()
    private val sessionBefore = config.sessionSnapshot
    private val versionBefore = sessionVersionField.getLong(config)
    private val appUidBefore = app.uid
    private val appTokenBefore = app.token
    private val appContextField = WKIMApplication::class.java.getDeclaredField("mContext").apply { isAccessible = true }
    private val appContextBefore = appContextField.get(app)
    private val helperBefore = helperField.get(app)
    private val staticUidBefore = uidField.get(null)
    private val dbNameBefore = dbNameField.get(null)
    private val openHelperBefore = openHelperField.get(null)
    private val connectStatusBefore = connectStatusField.getInt(WKConnection.getInstance())
    private val baseUrlBefore = WKApiConfig.baseUrl
    private val baseWebUrlBefore = WKApiConfig.baseWebUrl
    private val baseApplicationBefore = WKBaseApplication.getInstance().application
    private val spaceBefore = MsgModel.getInstance().currentSpaceId
    private val spaceNameBefore = MsgModel.getInstance().currentSpaceName
    private val threadOnBefore = config.appConfig.thread_on
    private val sent = AtomicReference<WKMsg?>(null)
    private val currentChooser = AtomicReference<Activity?>(null)
    private val recreationSource = AtomicReference<Activity?>(null)
    private val recreatedChooserLatch = AtomicReference(CountDownLatch(1))
    private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) {
            if (activity.javaClass.name == "com.chat.uikit.chat.ChooseChatActivity") {
                currentChooser.set(activity)
                val old = recreationSource.get()
                if (old != null && old !== activity) recreatedChooserLatch.get().countDown()
            }
        }
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) {
            if (currentChooser.get() === activity) currentChooser.compareAndSet(activity, null)
        }
    }
    private val sendLatch = CountDownLatch(1)
    private val seedLatch = CountDownLatch(1)
    private lateinit var testHelper: WKDBHelper
    private lateinit var channel: WKChannel
    private lateinit var seed: WKMsg
    private var originalSendingEntries: Map<Int, Any> = emptyMap()

    @Before fun setUp() {
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(lifecycleCallbacks)
        connectionLock.lock()
        try {
            @Suppress("UNCHECKED_CAST")
            val sending = sendingMapField.get(WKConnection.getInstance()) as MutableMap<Int, Any>
            originalSendingEntries = HashMap(sending)
            sending.clear()
        } finally { connectionLock.unlock() }
        WKBaseApplication.getInstance().application = context.applicationContext as android.app.Application
        val initContext = WKIMApplication::class.java.getDeclaredMethod("initContext", android.content.Context::class.java).apply { isAccessible = true }
        initContext.invoke(app, context)
        config.uid = uid
        config.token = "share-test-token"
        app.uid = uid
        app.token = "share-test-token"
        testHelper = helperCtor.newInstance(context, uid)
        helperField.set(app, testHelper)
        WKIM.getInstance().msgManager.initNormalMsg()
        WKIM.getInstance().msgManager.registerContentMsg(WKTextContent::class.java)
        WKApiConfig.baseUrl = "https://share-test.invalid/"
        WKApiConfig.baseWebUrl = "https://share-test.invalid/"
        MsgModel.getInstance().setCurrentSpaceId("")
        config.appConfig.thread_on = 0
        connectStatusField.setInt(WKConnection.getInstance(), WKConnectStatus.syncMsg)
        channel = WKChannel(channelId, WKChannelType.GROUP).apply { channelName = "Share target $unique" }
        WKIM.getInstance().channelManager.saveOrUpdateChannel(channel)
        WKIM.getInstance().msgManager.addOnSendMsgCallback(callbackKey, ISendMsgCallBackListener { msg ->
            if (msg.channelID == channelId && msg.baseContentMsgModel is WKTextContent &&
                (msg.baseContentMsgModel as WKTextContent).content != null) {
                val text = (msg.baseContentMsgModel as WKTextContent).content
                if (text == sharedText) { sent.set(msg); sendLatch.countDown() }
                textWaiters[text]?.countDown()
                if (text != "seed-$unique") textCallbacks += msg
                if (text == "seed-$unique") seedLatch.countDown()
            } else if (msg.channelID == channelId && msg.baseContentMsgModel is WKFileContent) {
                fileSendCallbacks += msg
                fileSendLatch.get().countDown()
            }
        })
        val uploadTransport = Proxy.newProxyInstance(fileTransportField.type.classLoader,
            arrayOf(fileTransportField.type)) { _, method, args ->
            when (method.name) {
                "getCredentials" -> {
                    (args!![3] as WKUploader.IGetUploadCredentials).onResult(null, null, null, null)
                    null
                }
                "put" -> {
                    (args!![5] as WKUploader.IUploadBack).onError()
                    null
                }
                else -> null
            }
        }
        fileTransportField.set(null, uploadTransport)
        seed = WKMsg().apply {
            this.channelID = channelId
            channelType = WKChannelType.GROUP
            fromUID = uid
            type = com.chat.base.msgitem.WKContentType.WK_TEXT
            baseContentMsgModel = WKTextContent("seed-$unique")
        }
        instrumentation.runOnMainSync { WKIM.getInstance().msgManager.sendMessage(seed) }
        check(seedLatch.await(8, TimeUnit.SECONDS)) { "fixture conversation seed was not inserted" }
    }

    @After fun tearDown() {
        currentChooser.get()?.let { runCatching { instrumentation.runOnMainSync { it.finish() } } }
        if (fileConfirmationStarted.get() && expectedFileMessages.get() > 0) {
            check(fileSendLatch.get().await(15, TimeUnit.SECONDS)) { "confirmed file share did not complete SDK insert callbacks" }
        }
        check(awaitRequestOwnedCleanup(15, TimeUnit.SECONDS)) { "request-owned source cleanup did not finish before database teardown" }
        check(awaitFileUploadsTerminal(15, TimeUnit.SECONDS)) { "file upload callbacks did not reach terminal state before database teardown" }
        (context.applicationContext as Application).unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
        WKIM.getInstance().msgManager.removeSendMsgCallBack(callbackKey)
        sent.get()?.let { runCatching { WKIM.getInstance().msgManager.deleteWithClientMsgNO(it.clientMsgNO) } }
        fileSendCallbacks.forEach { runCatching { WKIM.getInstance().msgManager.deleteWithClientMsgNO(it.clientMsgNO) } }
        if (::seed.isInitialized && !seed.clientMsgNO.isNullOrEmpty()) runCatching { WKIM.getInstance().msgManager.deleteWithClientMsgNO(seed.clientMsgNO) }
        connectionLock.lock()
        try {
            @Suppress("UNCHECKED_CAST")
            (sendingMapField.get(WKConnection.getInstance()) as? MutableMap<Int, Any?>)?.let { map ->
                if (::seed.isInitialized) map.remove(seed.clientSeq.toInt())
                sent.get()?.let { map.remove(it.clientSeq.toInt()) }
                fileSendCallbacks.forEach { map.remove(it.clientSeq.toInt()) }
            }
        } finally { connectionLock.unlock() }
        contentRegistryField.set(WKIM.getInstance().msgManager, contentRegistryBefore?.toMutableList())
        fileTransportField.set(null, fileTransportBefore)
        fixtureFiles.forEach { it.delete() }
        requestOwnedDirectories.distinct().forEach { it.deleteRecursively() }
        runCatching { testHelper.close() }
        appContextField.set(app, appContextBefore)
        helperField.set(app, helperBefore)
        uidField.set(null, staticUidBefore)
        dbNameField.set(null, dbNameBefore)
        openHelperField.set(null, openHelperBefore)
        app.uid = appUidBefore
        app.token = appTokenBefore
        config.uid = sessionBefore.uid
        config.token = sessionBefore.token
        sessionVersionField.setLong(config, versionBefore)
        connectStatusField.setInt(WKConnection.getInstance(), connectStatusBefore)
        WKApiConfig.baseUrl = baseUrlBefore
        WKApiConfig.baseWebUrl = baseWebUrlBefore
        MsgModel.getInstance().setCurrentSpaceId(spaceBefore, spaceNameBefore)
        config.appConfig.thread_on = threadOnBefore
        WKBaseApplication.getInstance().application = baseApplicationBefore
        connectionLock.lock()
        try {
            @Suppress("UNCHECKED_CAST")
            val sending = sendingMapField.get(WKConnection.getInstance()) as MutableMap<Int, Any>
            sending.clear()
            sending.putAll(originalSendingEntries)
        } finally { connectionLock.unlock() }
        context.deleteDatabase("wk_$uid.db")
        File(context.filesDir, "trusted-local-files/${sha256(uid)}").deleteRecursively()
    }

    @Test fun actionSendTextUsesTheRegisteredChooserAndVisibleConfirmation() {
        val monitor = instrumentation.addMonitor("com.chat.uikit.chat.ChooseChatActivity", null, false)
        var chooser: Activity? = null
        try {
            val intent = Intent(context, ShareReceiveActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, sharedText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            instrumentation.startActivitySync(intent)
            chooser = monitor.waitForActivityWithTimeout(10_000)
            assertNotNull("the real endpoint should open the chooser", chooser)
            instrumentation.waitForIdleSync()

            onView(withText(com.chat.uikit.R.string.summary_choose_chat_tab_recent)).perform(click())
            onView(withText(channel.channelName)).check(matches(isDisplayed())).perform(click())
            onView(withId(com.chat.base.R.id.titleRightBtn)).check(matches(isDisplayed())).perform(click())
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            onView(withId(com.chat.base.R.id.titleRightBtn)).perform(click())
            onView(withText(com.chat.uikit.R.string.sure)).inRoot(isDialog()).check(matches(isDisplayed())).perform(click())

            assertEquals("the selected text should reach the SDK send callback once", true, sendLatch.await(10, TimeUnit.SECONDS))
            val sentMessage = sent.get()
            assertNotNull(sentMessage)
            assertEquals(channelId, sentMessage!!.channelID)
            assertEquals(sharedText, (sentMessage.baseContentMsgModel as WKTextContent).content)
            val cursor = testHelper.rawQuery("select content from message where channel_id=? and channel_type=?", arrayOf(channelId, WKChannelType.GROUP.toString()))
            try {
                var matches = 0
                while (cursor.moveToNext()) if (cursor.getString(0).contains(sharedText)) matches++
                assertEquals("the shared text must be persisted exactly once", 1, matches)
            } finally { cursor.close() }
        } finally {
            chooser?.let { instrumentation.runOnMainSync { it.finish() } }
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test fun backingOutOfTheRealChooserDoesNotSendTheSharedText() {
        val monitor = instrumentation.addMonitor("com.chat.uikit.chat.ChooseChatActivity", null, false)
        var chooser: Activity? = null
        try {
            val intent = Intent(context, ShareReceiveActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, sharedText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            instrumentation.startActivitySync(intent)
            chooser = monitor.waitForActivityWithTimeout(10_000)
            assertNotNull("the real endpoint should open the chooser", chooser)
            pressBack()
            instrumentation.waitForIdleSync()
            assertEquals("canceling the chooser must not send", false, sendLatch.await(1, TimeUnit.SECONDS))
        } finally {
            chooser?.let { instrumentation.runOnMainSync { it.finish() } }
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test fun actionSendPdfPersistsOneResolvableTrustedSnapshot() {
        val bytes = "%PDF-1.4\n%share-$unique\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n".toByteArray()
        val source = createPdfSource("single", bytes)
        val monitor = instrumentation.addMonitor("com.chat.uikit.chat.ChooseChatActivity", null, false)
        var chooser: Activity? = null
        try {
            launchShare(Intent.ACTION_SEND, listOf(uriFor(source)), "application/pdf")
            chooser = monitor.waitForActivityWithTimeout(10_000)
            assertNotNull("the real endpoint should open the chooser", chooser)
            trackRequestOwnedFiles(chooser!!)
            selectTargetAndConfirm(expectedFiles = 1)
            assertEquals("PDF should be inserted once", true, fileSendLatch.get().await(10, TimeUnit.SECONDS))
            assertTrue("PDF upload should finish before inspecting its row", awaitFileUploadsTerminal(10, TimeUnit.SECONDS))
            val msg = fileSendCallbacks.single()
            val content = msg.baseContentMsgModel as WKFileContent
            val snapshot = TrustedLocalFileStore.resolve(context, uid, WKApiConfig.baseUrl,
                msg.channelType, msg.channelID, msg.clientMsgNO, content.size)
            assertNotNull("sent PDF must resolve from the trusted index", snapshot)
            assertEquals(bytes.toList(), snapshot!!.readBytes().toList())
            val rowCount = testHelper.rawQuery("select client_msg_no from message where client_msg_no=?", arrayOf(msg.clientMsgNO)).use { it.count }
            assertEquals(1, rowCount)
        } finally {
            chooser?.let { instrumentation.runOnMainSync { it.finish() } }
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test fun actionSendMultiplePdfUsesTheRealChooserAndCreatesOneSnapshotPerSource() {
        val firstBytes = "%PDF-1.4\n%first-$unique\n%%EOF\n".toByteArray()
        val secondBytes = "%PDF-1.4\n%second-$unique\n%%EOF\n".toByteArray()
        val sources = listOf(createPdfSource("first", firstBytes), createPdfSource("second", secondBytes))
        fileSendLatch.set(CountDownLatch(2))
        val monitor = instrumentation.addMonitor("com.chat.uikit.chat.ChooseChatActivity", null, false)
        var chooser: Activity? = null
        try {
            launchShare(Intent.ACTION_SEND_MULTIPLE, sources.map(::uriFor), "application/pdf")
            chooser = monitor.waitForActivityWithTimeout(10_000)
            assertNotNull("the real endpoint should open the chooser", chooser)
            trackRequestOwnedFiles(chooser!!)
            selectTargetAndConfirm(expectedFiles = 2)
            assertEquals("both shared PDFs should be inserted", true, fileSendLatch.get().await(10, TimeUnit.SECONDS))
            assertTrue("all PDF upload callbacks should finish", awaitFileUploadsTerminal(10, TimeUnit.SECONDS))
            assertEquals(2, fileSendCallbacks.size)
            val resolvedBytes = fileSendCallbacks.map { msg ->
                val content = msg.baseContentMsgModel as WKFileContent
                val snapshot = TrustedLocalFileStore.resolve(context, uid, WKApiConfig.baseUrl,
                    msg.channelType, msg.channelID, msg.clientMsgNO, content.size)
                assertNotNull(snapshot)
                snapshot!!.readBytes().toList()
            }
            assertEquals(setOf(firstBytes.toList(), secondBytes.toList()), resolvedBytes.toSet())
            val ids = fileSendCallbacks.map { it.clientMsgNO }
            assertEquals("each source should produce exactly one DB row", 2,
                testHelper.rawQuery("select client_msg_no from message where client_msg_no IN (?,?)", ids.toTypedArray()).use { it.count })
        } finally {
            chooser?.let { instrumentation.runOnMainSync { it.finish() } }
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test fun cancelingPdfChooserRemovesItsTemporarySource() {
        val source = createPdfSource("cancel", "%PDF-1.4\n%%EOF\n".toByteArray())
        val monitor = instrumentation.addMonitor("com.chat.uikit.chat.ChooseChatActivity", null, false)
        var chooser: Activity? = null
        try {
            launchShare(Intent.ACTION_SEND, listOf(uriFor(source)), "application/pdf")
            chooser = monitor.waitForActivityWithTimeout(10_000)
            assertNotNull(chooser)
            val ownedFiles = requestOwnedFiles(chooser!!)
            assertTrue("the request should own its copied PDF", ownedFiles.isNotEmpty() && ownedFiles.all { it.isFile })
            ownedFiles.forEach { requestOwnedDirectories += it.parentFile!! }
            pressBack()
            instrumentation.waitForIdleSync()
            assertEquals(0, fileSendCallbacks.size)
            assertTrue("canceling should release this request's temporary copy", ownedFiles.none { it.exists() })
        } finally {
            chooser?.let { instrumentation.runOnMainSync { it.finish() } }
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test fun switchingAccountsBeforeConfirmationRejectsSharedAndAppendedText() {
        val monitor = instrumentation.addMonitor("com.chat.uikit.chat.ChooseChatActivity", null, false)
        var chooser: Activity? = null
        try {
            launchTextShare(sharedText)
            chooser = monitor.waitForActivityWithTimeout(10_000)
            assertNotNull(chooser)
            instrumentation.waitForIdleSync()
            onView(withText(com.chat.uikit.R.string.summary_choose_chat_tab_recent)).perform(click())
            onView(withText(channel.channelName)).perform(click())
            onView(withId(com.chat.base.R.id.titleRightBtn)).perform(click())
            onView(withId(com.chat.uikit.R.id.messageEt)).perform(androidx.test.espresso.action.ViewActions.replaceText("attached note"))

            config.uid = "share-switched-$unique"
            config.token = "share-switched-token"
            app.uid = "share-switched-$unique"
            app.token = "share-switched-token"
            onView(withText(com.chat.uikit.R.string.sure)).inRoot(isDialog()).perform(click())
            instrumentation.waitForIdleSync()
            assertEquals("expired request must not send the shared message", false, sendLatch.await(1, TimeUnit.SECONDS))
            assertEquals("expired request must not send the appended text", false,
                textCallbacks.any { (it.baseContentMsgModel as? WKTextContent)?.content == "attached note" })
            val cursor = testHelper.rawQuery("select content from message where channel_id=?", arrayOf(channelId))
            try { while (cursor.moveToNext()) assertEquals(false, cursor.getString(0).contains(sharedText)) }
            finally { cursor.close() }
        } finally {
            config.uid = uid; config.token = "share-test-token"
            app.uid = uid; app.token = "share-test-token"
            chooser?.let { instrumentation.runOnMainSync { it.finish() } }
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test fun chooserRecreationKeepsItsRequestUntilTheVisibleConfirmation() {
        val monitor = instrumentation.addMonitor("com.chat.uikit.chat.ChooseChatActivity", null, false)
        var chooser: Activity? = null
        try {
            launchTextShare(sharedText)
            chooser = monitor.waitForActivityWithTimeout(10_000)
            assertNotNull(chooser)
            recreationSource.set(chooser)
            recreatedChooserLatch.set(CountDownLatch(1))
            instrumentation.runOnMainSync { chooser!!.recreate() }
            assertTrue("new chooser instance should resume", recreatedChooserLatch.get().await(10, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            val recreated = currentChooser.get()
            assertNotNull("the chooser should be recreated", recreated)
            assertNotSame("the request must survive a new activity instance", chooser, recreated)
            selectTargetAndConfirm()
            assertEquals(true, sendLatch.await(10, TimeUnit.SECONDS))
            assertEquals(1, textCallbacks.count { (it.baseContentMsgModel as WKTextContent).content == sharedText })
        } finally {
            chooser?.let { instrumentation.runOnMainSync { it.finish() } }
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test fun interleavedShareRequestsKeepTheirContentsBoundToTheirChoosers() {
        val firstMonitor = instrumentation.addMonitor("com.chat.uikit.chat.ChooseChatActivity", null, false)
        var secondMonitor: Instrumentation.ActivityMonitor? = null
        var firstChooser: Activity? = null
        var secondChooser: Activity? = null
        val secondText = "second-share-$unique"
        val firstLatch = CountDownLatch(1)
        val secondLatch = CountDownLatch(1)
        textWaiters[sharedText] = firstLatch
        textWaiters[secondText] = secondLatch
        try {
            launchTextShare(sharedText)
            firstChooser = firstMonitor.waitForActivityWithTimeout(10_000)
            assertNotNull(firstChooser)
            secondMonitor = instrumentation.addMonitor("com.chat.uikit.chat.ChooseChatActivity", null, false)
            launchTextShare(secondText)
            secondChooser = secondMonitor!!.waitForActivityWithTimeout(10_000)
            assertNotNull(secondChooser)

            selectTargetAndConfirm()
            assertEquals("top request should send its own content", true, secondLatch.await(10, TimeUnit.SECONDS))
            selectTargetAndConfirm()
            assertEquals("underlying request should retain its own content", true, firstLatch.await(10, TimeUnit.SECONDS))
            assertEquals(1, textCallbacks.count { (it.baseContentMsgModel as WKTextContent).content == sharedText })
            assertEquals(1, textCallbacks.count { (it.baseContentMsgModel as WKTextContent).content == secondText })
        } finally {
            firstChooser?.let { instrumentation.runOnMainSync { it.finish() } }
            secondChooser?.let { instrumentation.runOnMainSync { it.finish() } }
            textWaiters.remove(sharedText)
            textWaiters.remove(secondText)
            instrumentation.removeMonitor(firstMonitor)
            secondMonitor?.let { instrumentation.removeMonitor(it) }
        }
    }

    private fun createPdfSource(label: String, bytes: ByteArray): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "$unique-$label.pdf").apply { writeBytes(bytes) }
        fixtureFiles += file
        return file
    }

    private fun requestOwnedFiles(chooser: Activity): List<File> {
        val requestId = chooser.intent.getStringExtra(ChooseChatActivity.EXTRA_CHOOSE_REQUEST_ID)
        val request = WKUIKitApplication.getInstance().getChooseChatRequest(requestId)
        return request?.list.orEmpty().filterIsInstance<WKFileContent>().mapNotNull { content ->
            content.localPath?.takeIf { it.isNotEmpty() }?.let(::File)
        }
    }

    private fun trackRequestOwnedFiles(chooser: Activity) {
        requestOwnedFiles(chooser).forEach { file -> file.parentFile?.let { requestOwnedDirectories += it } }
    }

    private fun awaitRequestOwnedCleanup(timeout: Long, unit: TimeUnit): Boolean {
        val deadline = System.nanoTime() + unit.toNanos(timeout)
        while (System.nanoTime() < deadline) {
            if (requestOwnedDirectories.all { !it.exists() }) return true
            Thread.sleep(10)
        }
        return requestOwnedDirectories.all { !it.exists() }
    }

    private fun awaitFileUploadsTerminal(timeout: Long, unit: TimeUnit): Boolean {
        val deadline = System.nanoTime() + unit.toNanos(timeout)
        val requiredCallbacks = if (fileConfirmationStarted.get()) expectedFileMessages.get() else 0
        fun allFailedTerminal(): Boolean = fileSendCallbacks.size >= requiredCallbacks && fileSendCallbacks.all { msg ->
            val cursor = testHelper.rawQuery("select status from message where client_msg_no=?", arrayOf(msg.clientMsgNO))
            try { cursor.moveToFirst() && cursor.getInt(0) == com.xinbida.wukongim.message.type.WKSendMsgResult.send_fail }
            finally { cursor.close() }
        }
        while (System.nanoTime() < deadline && !allFailedTerminal()) Thread.sleep(10)
        instrumentation.waitForIdleSync()
        return allFailedTerminal()
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun uriFor(file: File): Uri = FileProvider.getUriForFile(context,
        "${context.packageName}.fileProvider", file)

    private fun launchShare(action: String, uris: List<Uri>, mimeType: String) {
        val intent = Intent(context, ShareReceiveActivity::class.java).apply {
            this.action = action
            type = mimeType
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (action == Intent.ACTION_SEND_MULTIPLE) putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            else putExtra(Intent.EXTRA_STREAM, uris.single())
        }
        instrumentation.startActivitySync(intent)
    }

    private fun launchTextShare(text: String) {
        val intent = Intent(context, ShareReceiveActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        instrumentation.startActivitySync(intent)
    }

    private fun selectTargetAndConfirm(expectedFiles: Int = 0) {
        if (expectedFiles > 0) {
            expectedFileMessages.set(expectedFiles)
            fileSendLatch.set(CountDownLatch(expectedFiles))
            fileConfirmationStarted.set(false)
        }
        instrumentation.waitForIdleSync()
        onView(withText(com.chat.uikit.R.string.summary_choose_chat_tab_recent)).perform(click())
        onView(withText(channel.channelName)).check(matches(isDisplayed())).perform(click())
        onView(withId(com.chat.base.R.id.titleRightBtn)).check(matches(isDisplayed())).perform(click())
        onView(withText(com.chat.uikit.R.string.sure)).inRoot(isDialog()).check(matches(isDisplayed())).perform(click())
        if (expectedFiles > 0) fileConfirmationStarted.set(true)
    }
}
