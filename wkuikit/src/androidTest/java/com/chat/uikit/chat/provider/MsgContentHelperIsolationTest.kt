package com.chat.uikit.chat.provider

import android.content.ContentValues
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.xinbida.wukongim.WKIMApplication
import com.xinbida.wukongim.db.MsgDbManager
import com.xinbida.wukongim.db.WKDBHelper
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.HashMap
import java.util.UUID

class MsgContentHelperIsolationTest {
    @Test fun explicitHelperUpdatesOnlyItsOwnAccountDatabaseAndPreservesUnknownFields() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = WKIMApplication.getInstance()
        val oldUid = app.uid
        val appContextField = WKIMApplication::class.java.getDeclaredField("mContext").apply { isAccessible = true }
        val oldContextRef = appContextField.get(app)
        val staticUidField = WKDBHelper::class.java.getDeclaredField("uid").apply { isAccessible = true }
        val staticNameField = WKDBHelper::class.java.getDeclaredField("myDBName").apply { isAccessible = true }
        val staticOpenHelperField = WKDBHelper::class.java.getDeclaredField("openHelper").apply { isAccessible = true }
        val oldStaticUid = staticUidField.get(null)
        val oldStaticName = staticNameField.get(null)
        val oldOpenHelper = staticOpenHelperField.get(null)
        val initContext = WKIMApplication::class.java.getDeclaredMethod("initContext", Context::class.java).apply { isAccessible = true }
        initContext.invoke(app, context)
        val constructor = WKDBHelper::class.java.getDeclaredConstructor(Context::class.java, String::class.java).apply { isAccessible = true }
        val suffix = UUID.randomUUID().toString().replace("-", "")
        val uidA = "codex-helper-isolation-a-$suffix"
        val uidB = "codex-helper-isolation-b-$suffix"
        val dbAName = "wk_$uidA.db"
        val dbBName = "wk_$uidB.db"
        context.deleteDatabase(dbAName); context.deleteDatabase(dbBName)
        var helperA: WKDBHelper? = null
        var helperB: WKDBHelper? = null
        try {
            app.uid = uidA
            helperA = constructor.newInstance(context, uidA)
            app.uid = uidB
            helperB = constructor.newInstance(context, uidB)
            val accountAHelper = helperA!!
            val accountBHelper = helperB!!

            insertContent(accountAHelper, "shared-client-msg", "{\"space_id\":\"spaceA\",\"unknown\":\"keepA\"}")
            insertContent(accountBHelper, "shared-client-msg", "{\"space_id\":\"spaceB\",\"unknown\":\"keepB\"}")
            val fields = HashMap<String, Any>().apply { put("localPath", "/account-a/cache.bin") }
            // The process is now on account B. Passing helper A must still modify only A's DB.
            app.uid = uidB
            assertTrue(MsgDbManager.getInstance().mergeContentFieldsWithClientMsgNo(accountAHelper, "shared-client-msg", fields))

            val valueA = JSONObject(readContent(accountAHelper, "shared-client-msg"))
            val valueB = JSONObject(readContent(accountBHelper, "shared-client-msg"))
            assertEquals("/account-a/cache.bin", valueA.getString("localPath"))
            assertEquals("spaceA", valueA.getString("space_id")); assertEquals("keepA", valueA.getString("unknown"))
            assertFalse(valueB.has("localPath"))
            assertEquals("spaceB", valueB.getString("space_id")); assertEquals("keepB", valueB.getString("unknown"))
        } finally {
            helperB?.close(); helperA?.close()
            context.deleteDatabase(dbAName); context.deleteDatabase(dbBName)
            app.uid = oldUid
            appContextField.set(app, oldContextRef)
            staticUidField.set(null, oldStaticUid)
            staticNameField.set(null, oldStaticName)
            staticOpenHelperField.set(null, oldOpenHelper)
        }
    }

    private fun insertContent(helper: WKDBHelper, clientMsgNo: String, content: String) {
        val values = ContentValues().apply { put("client_msg_no", clientMsgNo); put("content", content) }
        assertTrue(helper.insert("message", values) > 0)
    }

    private fun readContent(helper: WKDBHelper, clientMsgNo: String): String {
        val cursor = helper.rawQuery("select content from message where client_msg_no=?", arrayOf(clientMsgNo))
        try { assertTrue(cursor.moveToFirst()); return cursor.getString(0) } finally { cursor.close() }
    }
}
