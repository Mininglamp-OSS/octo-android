package com.chat.base.endpoint.entity;

/**
 * 2020-09-02 10:54
 */
public class BaseEndpoint {
    public String sid;
    public int imgResourceID;
    public String text;
    public IMenuClick iMenuClick;

    public interface IMenuClick {
        void onClick();
    }
}
