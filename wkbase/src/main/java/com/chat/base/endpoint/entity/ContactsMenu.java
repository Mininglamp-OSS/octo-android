package com.chat.base.endpoint.entity;

/**
 * 2020-09-02 12:14
 * 联系人模块
 */
public class ContactsMenu extends BaseEndpoint {
    public int badgeNum;
    //是否显示红点提示
    public boolean showRedDot;
    public String uid;
    public String sid;

    public ContactsMenu(String sid, int imgResourceID, String text, IMenuClick iMenuClick) {
        this.imgResourceID = imgResourceID;
        this.text = text;
        this.sid = sid;
        this.iMenuClick = iMenuClick;
    }
}
