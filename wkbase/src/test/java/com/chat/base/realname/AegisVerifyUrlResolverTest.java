package com.chat.base.realname;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.chat.base.entity.OidcProviderConfig;
import com.chat.base.entity.WKAPPConfig;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * YUJ-396 / GH dmwork-web#1174 — Android 端「去认证」URL 解析器单测。
 *
 * <p>锁以下行为合约（与 Web 端 resolveRealnameVerifyUrl 纯函数 /
 * iOS 端 WKRealnameVerifyURLBuilderTests 对齐）:
 * <ol>
 *     <li>appconfig 的 oidc_providers[0].account_url=prod → 拼 prod verify URL</li>
 *     <li>appconfig 的 account_url=test → 拼 test verify URL（im-test 场景主验证）</li>
 *     <li>末尾斜杠剥离, 防 //profile/... 协议相对 URL 泄漏</li>
 *     <li>第一条 provider 的 account_url 非法时回退到第二条</li>
 *     <li>oidc_providers=null / [] → NO_ACCOUNT_URL（调用侧 toast 不跳）</li>
 *     <li>appConfig=null（冷启动未到）→ NO_ACCOUNT_URL</li>
 *     <li>scheme=http / javascript: → NO_ACCOUNT_URL（https-only 安全守卫）</li>
 *     <li>空串 / `https://` 无 host → NO_ACCOUNT_URL</li>
 * </ol>
 *
 * <p>纯 JVM 单测 —— {@link AegisVerifyUrlResolver} 用 {@code java.net.URI} 做
 * scheme + host 校验, 不依赖 Android framework, 避免需要 Robolectric。
 */
public class AegisVerifyUrlResolverTest {

    private static OidcProviderConfig provider(String id, String accountUrl) {
        OidcProviderConfig p = new OidcProviderConfig();
        p.id = id;
        p.name = id;
        p.authorize_path = "/auth/oidc/" + id + "/authorize";
        p.account_url = accountUrl;
        return p;
    }

    private static WKAPPConfig configWithProviders(OidcProviderConfig... providers) {
        WKAPPConfig c = new WKAPPConfig();
        c.oidc_providers = new ArrayList<>(Arrays.asList(providers));
        return c;
    }

    // ---- happy path ----

    @Test
    public void prodAccountUrl_buildsProdVerifyURL() {
        WKAPPConfig config = configWithProviders(provider("xming", "https://accounts.example.com"));

        AegisVerifyUrlResolver.Result r = AegisVerifyUrlResolver.resolve(config);

        assertEquals(AegisVerifyUrlResolver.Result.Reason.OK, r.reason);
        assertEquals("https://accounts.example.com/profile/info?anchor=verification", r.url);
        assertEquals("accounts.example.com", r.host);
    }

    @Test
    public void testAccountUrl_buildsTestVerifyURL_imTestScenario() {
        // 本测试就是 YUJ-396 修复的核心目标: im-test 环境不能再跳 prod Aegis。
        WKAPPConfig config = configWithProviders(
                provider("xming", "https://accounts-test.imocto.cn"));

        AegisVerifyUrlResolver.Result r = AegisVerifyUrlResolver.resolve(config);

        assertEquals(AegisVerifyUrlResolver.Result.Reason.OK, r.reason);
        assertEquals("https://accounts-test.imocto.cn/profile/info?anchor=verification", r.url);
        assertEquals("accounts-test.imocto.cn", r.host);
    }

    @Test
    public void trailingSlashOnAccountUrl_isStripped() {
        WKAPPConfig config = configWithProviders(
                provider("xming", "https://accounts-test.imocto.cn/"));

        AegisVerifyUrlResolver.Result r = AegisVerifyUrlResolver.resolve(config);

        assertEquals(AegisVerifyUrlResolver.Result.Reason.OK, r.reason);
        assertEquals("https://accounts-test.imocto.cn/profile/info?anchor=verification", r.url);
    }

    @Test
    public void multipleTrailingSlashes_allStripped() {
        WKAPPConfig config = configWithProviders(provider("xming", "https://accounts.example.com///"));

        AegisVerifyUrlResolver.Result r = AegisVerifyUrlResolver.resolve(config);

        assertEquals(AegisVerifyUrlResolver.Result.Reason.OK, r.reason);
        assertEquals("https://accounts.example.com/profile/info?anchor=verification", r.url);
    }

    @Test
    public void firstProviderInvalid_fallsBackToSecondValidProvider() {
        WKAPPConfig config = configWithProviders(
                provider("broken", "http://insecure.example"),     // http: 会被拒
                provider("xming", "https://accounts-test.imocto.cn"));

        AegisVerifyUrlResolver.Result r = AegisVerifyUrlResolver.resolve(config);

        assertEquals(AegisVerifyUrlResolver.Result.Reason.OK, r.reason);
        assertEquals("https://accounts-test.imocto.cn/profile/info?anchor=verification", r.url);
    }

    // ---- no_account_url 分支 ----

    @Test
    public void nullAppConfig_returnsNoAccountUrl() {
        AegisVerifyUrlResolver.Result r = AegisVerifyUrlResolver.resolve(null);
        assertEquals(AegisVerifyUrlResolver.Result.Reason.NO_ACCOUNT_URL, r.reason);
        assertNull(r.url);
        assertNull(r.host);
    }

    @Test
    public void nullOidcProviders_returnsNoAccountUrl() {
        WKAPPConfig config = new WKAPPConfig();
        config.oidc_providers = null;

        AegisVerifyUrlResolver.Result r = AegisVerifyUrlResolver.resolve(config);
        assertEquals(AegisVerifyUrlResolver.Result.Reason.NO_ACCOUNT_URL, r.reason);
    }

    @Test
    public void emptyOidcProviders_returnsNoAccountUrl() {
        WKAPPConfig config = new WKAPPConfig();
        config.oidc_providers = Collections.emptyList();

        AegisVerifyUrlResolver.Result r = AegisVerifyUrlResolver.resolve(config);
        assertEquals(AegisVerifyUrlResolver.Result.Reason.NO_ACCOUNT_URL, r.reason);
    }

    @Test
    public void providerWithNullAccountUrl_returnsNoAccountUrl() {
        WKAPPConfig config = configWithProviders(provider("xming", null));

        AegisVerifyUrlResolver.Result r = AegisVerifyUrlResolver.resolve(config);
        assertEquals(AegisVerifyUrlResolver.Result.Reason.NO_ACCOUNT_URL, r.reason);
    }

    @Test
    public void providerWithEmptyAccountUrl_returnsNoAccountUrl() {
        WKAPPConfig config = configWithProviders(provider("xming", ""));

        AegisVerifyUrlResolver.Result r = AegisVerifyUrlResolver.resolve(config);
        assertEquals(AegisVerifyUrlResolver.Result.Reason.NO_ACCOUNT_URL, r.reason);
    }

    // ---- 安全守卫 ----

    @Test
    public void httpAccountUrl_returnsNoAccountUrl_httpsOnly() {
        // Aegis 涉及密码 / OIDC token, 客户端必须 TLS。
        WKAPPConfig config = configWithProviders(provider("xming", "http://accounts-test.imocto.cn"));

        AegisVerifyUrlResolver.Result r = AegisVerifyUrlResolver.resolve(config);
        assertEquals(AegisVerifyUrlResolver.Result.Reason.NO_ACCOUNT_URL, r.reason);
    }

    @Test
    public void javascriptProtocolAccountUrl_returnsNoAccountUrl_noScriptInjection() {
        WKAPPConfig config = configWithProviders(provider("xming", "javascript:alert(1)"));

        AegisVerifyUrlResolver.Result r = AegisVerifyUrlResolver.resolve(config);
        assertEquals(AegisVerifyUrlResolver.Result.Reason.NO_ACCOUNT_URL, r.reason);
    }

    @Test
    public void httpsWithoutHost_returnsNoAccountUrl() {
        WKAPPConfig config = configWithProviders(provider("xming", "https://"));

        AegisVerifyUrlResolver.Result r = AegisVerifyUrlResolver.resolve(config);
        assertEquals(AegisVerifyUrlResolver.Result.Reason.NO_ACCOUNT_URL, r.reason);
    }

    @Test
    public void onlySlashes_returnsNoAccountUrl() {
        // 全是斜杠的 accountUrl → 剥完以后是空串 → NO_ACCOUNT_URL
        WKAPPConfig config = configWithProviders(provider("xming", "/////"));

        AegisVerifyUrlResolver.Result r = AegisVerifyUrlResolver.resolve(config);
        assertEquals(AegisVerifyUrlResolver.Result.Reason.NO_ACCOUNT_URL, r.reason);
    }
}
