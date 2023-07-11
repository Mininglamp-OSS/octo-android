package com.chat.base.endpoint.entity;

/**
 * 2020-09-02 11:59
 * 钱包菜单
 */
public class WalletMenu extends BaseEndpoint {
    public WalletMenu(int imgResourceID, String text, IMenuClick iMenuClick) {
        this.imgResourceID = imgResourceID;
        this.text = text;
        this.iMenuClick = iMenuClick;
    }
}
