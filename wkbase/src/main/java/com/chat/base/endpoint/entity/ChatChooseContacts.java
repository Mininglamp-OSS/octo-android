package com.chat.base.endpoint.entity;


import com.xinbida.wukongim.entity.WKChannel;

import java.util.List;

/**
 * 2020-09-03 11:13
 * 选择联系人
 */
public class ChatChooseContacts {
    public IChoose iChoose;

    public ChatChooseContacts(IChoose iChoose) {
        this.iChoose = iChoose;
    }

    public interface IChoose {
        void onResult(List<WKChannel> list);
    }
}
