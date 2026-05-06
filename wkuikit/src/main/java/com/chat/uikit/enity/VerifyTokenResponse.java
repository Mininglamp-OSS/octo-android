package com.chat.uikit.enity;

/**
 * YUJ-361 (#227) · OCTO 实名认证 —— {@code POST /v1/internal/verify-token} 响应。
 *
 * <p>客户端发起实名时走 verify-side-channel（方案 J v3）：App 先拿一次性 token，
 * 再用 {@code verify_url} 在 Custom Tabs 打开 CAS 验证页，CAS 完成后 redirect 回
 * {@code dmwork://verified}，客户端 refresh 个人资料即可看到 {@code realname_verified=true}。
 *
 * <p>所有字段均来自 dmworkim PR#1301（后端实装）。客户端不解析 token 本身。
 */
public class VerifyTokenResponse {
    public String verify_url;
    public String token;
    public long expires_at;
}
