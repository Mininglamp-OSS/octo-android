package com.xinbida.wukongim.interfaces;


import com.xinbida.wukongim.entity.WKMsg;

import java.util.List;

/**
 * 2019-11-18 11:44
 * 新消息监听
 */
public interface INewMsgListener {
    void newMsg(List<WKMsg> msgs);
}
