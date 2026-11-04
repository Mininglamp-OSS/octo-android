package com.chat.base.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import okhttp3.HttpUrl;
import org.junit.Test;

public class ReportUrlBuilderTest {
    private static final String API_BASE = "https://im-test.deepminer.com.cn/api/v1/";

    @Test
    public void buildIncludesPersonalAndGroupParameters() {
        assertQuery(ReportUrlBuilder.build(API_BASE, "zh-Hans", "u1", "t1", "dark", "person", (byte) 1),
                "zh-Hans", "u1", "t1", "dark", "person", "1");
        assertQuery(ReportUrlBuilder.build(API_BASE, "en", "u2", "t2", "light", "group", (byte) 2),
                "en", "u2", "t2", "light", "group", "2");
    }

    @Test
    public void buildEncodesSpecialCharactersAndReplacesExistingQuery() {
        String url = ReportUrlBuilder.build(API_BASE + "?old=1#fragment", "zh-Hant", "用戶+& #", "t+& #令牌", "light", "群組&+", (byte) 2);
        HttpUrl parsed = HttpUrl.get(url);
        assertEquals("zh-Hant", parsed.queryParameter("lang"));
        assertEquals("用戶+& #", parsed.queryParameter("uid"));
        assertEquals("t+& #令牌", parsed.queryParameter("token"));
        assertEquals("群組&+", parsed.queryParameter("channel_id"));
        assertEquals("2", parsed.queryParameter("channel_type"));
        assertEquals(1, parsed.queryParameterValues("uid").size());
        assertEquals("https://im-test.deepminer.com.cn/api/v1/report/html", parsed.newBuilder().query(null).build().toString());
    }

    @Test
    public void configuredRecognitionIgnoresQueryButMatchesExactOriginAndPath() {
        assertTrue(ReportUrlBuilder.isConfiguredReportUrl("https://im-test.deepminer.com.cn/api/v1/report/html?uid=u&token=t", API_BASE));
        assertFalse(ReportUrlBuilder.isConfiguredReportUrl("https://im-test.deepminer.com.cn/api/report/html", API_BASE));
        assertFalse(ReportUrlBuilder.isConfiguredReportUrl("https://other.example/api/v1/report/html", API_BASE));
        assertFalse(ReportUrlBuilder.isConfiguredReportUrl("http://im-test.deepminer.com.cn/api/v1/report/html", API_BASE));
    }

    @Test
    public void endpointPreservesVersionPrefixAndRejectsUnsafeBases() {
        assertEquals("https://host.example/api/v1/report/html", ReportUrlBuilder.getEndpoint("https://host.example/api/v1/"));
        assertEquals("https://host.example/report/html", ReportUrlBuilder.getEndpoint("https://host.example/"));
        assertEquals("", ReportUrlBuilder.getEndpoint("http://host.example/api/v1/"));
        assertEquals("", ReportUrlBuilder.getEndpoint(" "));
        assertEquals("", ReportUrlBuilder.getEndpoint("not a URL"));
        assertEquals("", ReportUrlBuilder.getEndpoint("https://user:pass@host.example/api/v1/"));
        assertEquals("", ReportUrlBuilder.build("", "en", "u", "t", "light", "c", (byte) 1));
        assertEquals("", ReportUrlBuilder.build("http://host.example/api/v1/", "en", "u", "t", "light", "c", (byte) 1));
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
