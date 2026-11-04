package com.chat.base.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import okhttp3.HttpUrl;
import org.junit.Test;

public class ReportUrlBuilderTest {
    private static final String REPORT = "https://im-test.deepminer.com.cn/api/v1/report/html";

    @Test
    public void buildIncludesPersonalAndGroupParameters() {
        assertQuery(ReportUrlBuilder.build(REPORT, "zh-Hans", "u1", "t1", "dark", "person", (byte) 1),
                "zh-Hans", "u1", "t1", "dark", "person", "1");
        assertQuery(ReportUrlBuilder.build(REPORT, "en", "u2", "t2", "light", "group", (byte) 2),
                "en", "u2", "t2", "light", "group", "2");
    }

    @Test
    public void buildEncodesSpecialCharactersAndReplacesExistingQuery() {
        String url = ReportUrlBuilder.build(REPORT + "?uid=old&extra=kept", "zh-Hant", "用戶+& #", "t+& #令牌", "light", "群組&+", (byte) 2);
        HttpUrl parsed = HttpUrl.get(url);
        assertEquals("zh-Hant", parsed.queryParameter("lang"));
        assertEquals("用戶+& #", parsed.queryParameter("uid"));
        assertEquals("t+& #令牌", parsed.queryParameter("token"));
        assertEquals("群組&+", parsed.queryParameter("channel_id"));
        assertEquals("2", parsed.queryParameter("channel_type"));
        assertEquals(1, parsed.queryParameterValues("uid").size());
        assertEquals("kept", parsed.queryParameter("extra"));
    }

    @Test
    public void configuredRecognitionIgnoresQueryButMatchesExactOriginAndPath() {
        assertTrue(ReportUrlBuilder.isConfiguredReportUrl(REPORT + "?uid=u&token=t", REPORT));
        assertFalse(ReportUrlBuilder.isConfiguredReportUrl("https://im-test.deepminer.com.cn/api/report/html", REPORT));
        assertFalse(ReportUrlBuilder.isConfiguredReportUrl("https://other.example/api/v1/report/html", REPORT));
    }

    private static void assertQuery(String url, String lang, String uid, String token,
                                    String mode, String channelId, String channelType) {
        HttpUrl parsed = HttpUrl.get(url);
        assertEquals(lang, parsed.queryParameter("lang"));
        assertEquals(uid, parsed.queryParameter("uid"));
        assertEquals(token, parsed.queryParameter("token"));
        assertEquals(mode, parsed.queryParameter("mode"));
        assertEquals(channelId, parsed.queryParameter("channel_id"));
        assertEquals(channelType, parsed.queryParameter("channel_type"));
    }
}
