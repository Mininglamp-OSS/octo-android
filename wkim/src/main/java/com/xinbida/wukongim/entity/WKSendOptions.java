package com.xinbida.wukongim.entity;

public class WKSendOptions {
    public int expire = 0;
    public String topicID;
    public int flame;
    public int flameSecond;
    public String robotID;
    public WKMsgSetting setting = new WKMsgSetting();
    public WKMsgHeader header = new WKMsgHeader();
}
