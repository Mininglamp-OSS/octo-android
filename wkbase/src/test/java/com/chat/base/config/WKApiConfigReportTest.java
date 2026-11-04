package com.chat.base.config;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.After;

public class WKApiConfigReportTest {
    private final String originalBaseUrl = WKApiConfig.baseUrl;
    private final String originalBaseWebUrl = WKApiConfig.baseWebUrl;

    @After
    public void restoreApiConfiguration() {
        WKApiConfig.baseUrl = originalBaseUrl;
        WKApiConfig.baseWebUrl = originalBaseWebUrl;
    }

    @Test
    public void reportEndpointTracksEachProductionBaseInitializer() {
        WKApiConfig.initBaseURL("https://api-a.example/api");
        assertEquals("https://api-a.example/api/v1/report/html", WKApiConfig.getReportEndpoint());

        WKApiConfig.initBaseURLIncludeIP("https://api-b.example/custom/v3");
        assertEquals("https://api-b.example/custom/v3/v1/report/html", WKApiConfig.getReportEndpoint());
    }
}
