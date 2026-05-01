package com.chat.uikit.group.service.entity;

/**
 * 2020-07-20 21:54
 * 群二维码信息
 */
public class GroupQr {
    public int day;
    public String qrcode;
    public String expire;
    /**
     * 跨 Space 用户扫码入群的邀请链接。
     * 后端 ChannelQrcodeResp.invite_url（dmworkim PR #1201）
     */
    public String invite_url;
}
