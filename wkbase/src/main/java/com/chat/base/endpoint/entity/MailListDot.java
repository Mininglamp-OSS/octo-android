package com.chat.base.endpoint.entity;

/**
 * 2020-11-26 17:28
 * 通讯录红点
 */
public class MailListDot {
    public int numCount;
    public boolean showDot;

    public MailListDot(int numCount, boolean showDot) {
        this.showDot = showDot;
        this.numCount = numCount;
    }
}
