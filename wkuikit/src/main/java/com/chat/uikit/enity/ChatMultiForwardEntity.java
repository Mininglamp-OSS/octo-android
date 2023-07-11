package com.chat.uikit.enity;

import com.chad.library.adapter.base.entity.MultiItemEntity;
import com.xinbida.wukongim.entity.WKMsg;

/**
 * 2020-09-22 12:10
 * 合并转发
 */
public class ChatMultiForwardEntity implements MultiItemEntity {

    public int itemType;
    public String title;
    public WKMsg msg;

    @Override
    public int getItemType() {
        return itemType;
    }
}
