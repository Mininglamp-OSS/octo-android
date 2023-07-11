package com.chat.base.endpoint.entity;

/**
 * 2020-09-02 10:59
 * 个人中心菜单配置
 */
public class PersonalInfoMenu extends BaseEndpoint {
    public IPersonalInfoMenuClick iPersonalInfoMenuClick;
    public boolean isNewVersionIv = false;


    public PersonalInfoMenu(int imgResourceID, String text, IPersonalInfoMenuClick iPersonalInfoMenuClick) {
        this.imgResourceID = imgResourceID;
        this.text = text;
        this.iPersonalInfoMenuClick = iPersonalInfoMenuClick;
    }

    public void setIsNewVersionIv(boolean is) {
        isNewVersionIv = is;
    }

    public interface IPersonalInfoMenuClick {
        void onClick();
    }
}
