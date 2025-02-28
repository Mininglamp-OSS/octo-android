package com.chat.uikit.chat.search.image;

import com.chad.library.adapter.base.entity.MultiItemEntity;
import com.chat.base.entity.GlobalMessage;

/**
 * 3/23/21 10:31 AM
 * 搜索聊天图片
 */
public class SearchImgEntity implements MultiItemEntity {
    public int itemType;
    public GlobalMessage message;
    public String url;
    public String date;
//    public String clientMsgNo;
//    public long oldestOrderSeq;
//    public WKMessageContent messageContent;

    @Override
    public int getItemType() {
        return itemType;
    }
}
