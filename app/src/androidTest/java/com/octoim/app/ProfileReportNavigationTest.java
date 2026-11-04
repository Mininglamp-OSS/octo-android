package com.octoim.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.chat.base.act.WKWebViewActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.uikit.utils.ReportNavigation;
import com.xinbida.wukongim.entity.WKChannelType;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

@RunWith(AndroidJUnit4.class)
public class ProfileReportNavigationTest {
    @Test
    public void persistedServerSelectionInitializesProductionApiAndReportEntrypoints() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WKSharedPreferencesUtil preferences = WKSharedPreferencesUtil.getInstance();
        String oldApi = preferences.getSP("api_base_url");
        String oldUid = WKConfig.getInstance().getUid();
        String oldToken = WKConfig.getInstance().getToken();
        String oldBaseUrl = WKApiConfig.baseUrl;
        String oldBaseWebUrl = WKApiConfig.baseWebUrl;
        String oldTermsUrl = WKApiConfig.termsUrl;
        String oldPrivacyUrl = WKApiConfig.privacyUrl;
        Method initApi = TSApplication.class.getDeclaredMethod("initApi");
        initApi.setAccessible(true);
        try {
            preferences.putSPSync("api_base_url", "");
            initApi.invoke((TSApplication) context.getApplicationContext());
            assertEquals(com.octoim.app.BuildConfig.API_BASE_URL + "/v1/", WKApiConfig.baseUrl);

            preferences.putSPSync("api_base_url", "https://selected.example/custom");
            initApi.invoke((TSApplication) context.getApplicationContext());
            assertEquals("https://selected.example/custom/v1/", WKApiConfig.baseUrl);
            assertEquals("https://selected.example/custom/v1/report/html", WKApiConfig.getReportEndpoint());
            // The user signs in after the server switch and process restart.
            WKConfig.getInstance().setUid("simulated-user");
            WKConfig.getInstance().setToken("simulated-token");

            CapturingContext capture = new CapturingContext(context);
            openOnMainThread(capture, "profile-user-42", WKChannelType.PERSONAL);
            assertTargetAndUrl(capture.intent, "profile-user-42", WKChannelType.PERSONAL);
            assertTrue(capture.intent.getStringExtra("url").contains("uid=simulated-user"));
            assertTrue(capture.intent.getStringExtra("url").contains("token=simulated-token"));

            openOnMainThread(capture, "chat-user-42", WKChannelType.PERSONAL);
            assertTargetAndUrl(capture.intent, "chat-user-42", WKChannelType.PERSONAL);
            openOnMainThread(capture, "group-42", WKChannelType.GROUP);
            assertTargetAndUrl(capture.intent, "group-42", WKChannelType.GROUP);

            preferences.putSPSync("api_base_url", "http://unsafe.example/api");
            initApi.invoke((TSApplication) context.getApplicationContext());
            capture.intent = null;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    assertFalse(ReportNavigation.open(capture, "profile-user-42", WKChannelType.PERSONAL)));
            assertNull(capture.intent);
        } finally {
            preferences.putSPSync("api_base_url", oldApi);
            WKConfig.getInstance().setUid(oldUid);
            WKConfig.getInstance().setToken(oldToken);
            WKApiConfig.baseUrl = oldBaseUrl;
            WKApiConfig.baseWebUrl = oldBaseWebUrl;
            WKApiConfig.termsUrl = oldTermsUrl;
            WKApiConfig.privacyUrl = oldPrivacyUrl;
        }
    }

    private static void openOnMainThread(CapturingContext context, String channelId, byte channelType) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                assertTrue(ReportNavigation.open(context, channelId, channelType)));
    }

    private static void assertTargetAndUrl(Intent intent, String channelId, byte channelType) {
        assertNotNull(intent);
        assertEquals(WKWebViewActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals(channelId, intent.getStringExtra("channelID"));
        assertEquals(channelType, intent.getByteExtra("channelType", (byte) 0));
        String url = intent.getStringExtra("url");
        assertTrue(url.startsWith("https://selected.example/custom/v1/report/html?"));
        okhttp3.HttpUrl parsed = okhttp3.HttpUrl.get(url);
        assertEquals(channelId, parsed.queryParameter("channel_id"));
        assertEquals(Byte.toString(channelType), parsed.queryParameter("channel_type"));
    }

    private static final class CapturingContext extends ContextWrapper {
        Intent intent;

        CapturingContext(Context base) { super(base); }

        @Override public void startActivity(Intent value) {
            intent = value;
        }
    }
}
