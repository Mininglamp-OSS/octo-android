package com.chat.uikit.group.service.entity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;

import org.junit.Test;

/**
 * YUJ-91 · 外部群 EP8：群二维码「复制邀请链接」按钮
 * <p>
 * 风险防范（对齐 YUJ-53）：model 层必须透传新字段 invite_url，
 * 否则 UI 会静默失败（按钮永远不显示）。本测试确保 Gson 能把
 * 后端 ChannelQrcodeResp.invite_url 字段正确反序列化到 GroupQr.invite_url。
 */
public class GroupQrTest {

    @Test
    public void testInviteUrlFieldPassthrough() {
        String json = "{\"day\":7," +
                "\"qrcode\":\"https://example.com/qrcode/g1.png\"," +
                "\"expire\":\"2026-05-06 10:00\"," +
                "\"invite_url\":\"https://example.com/invite/abc123\"}";

        GroupQr qr = new Gson().fromJson(json, GroupQr.class);

        assertNotNull(qr);
        assertEquals(7, qr.day);
        assertEquals("https://example.com/qrcode/g1.png", qr.qrcode);
        assertEquals("2026-05-06 10:00", qr.expire);
        // 核心断言：invite_url 字段必须透传，否则「复制邀请链接」按钮永远不显示。
        assertEquals("https://example.com/invite/abc123", qr.invite_url);
    }

    @Test
    public void testInviteUrlMissingIsNull() {
        // 旧版本后端可能没有 invite_url 字段；客户端必须兼容（按钮隐藏，不崩溃）。
        String json = "{\"day\":7," +
                "\"qrcode\":\"https://example.com/qrcode/g1.png\"," +
                "\"expire\":\"2026-05-06 10:00\"}";

        GroupQr qr = new Gson().fromJson(json, GroupQr.class);

        assertNotNull(qr);
        assertNull(qr.invite_url);
    }

    @Test
    public void testInviteUrlEmptyString() {
        String json = "{\"day\":7,\"qrcode\":\"q\",\"expire\":\"e\",\"invite_url\":\"\"}";

        GroupQr qr = new Gson().fromJson(json, GroupQr.class);

        assertNotNull(qr);
        assertEquals("", qr.invite_url);
    }
}
