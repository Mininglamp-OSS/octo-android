package com.xinbida.wukongim.message;


import com.xinbida.wukongim.entity.WKMsg;

/**
 * 4/22/21 4:26 PM
 * 需要保存的消息
 */
class SavedMsg {
    public WKMsg wkMsg;
    public int redDot;

    public SavedMsg(WKMsg msg, int redDot) {
        this.redDot = redDot;
        this.wkMsg = msg;
    }
}
