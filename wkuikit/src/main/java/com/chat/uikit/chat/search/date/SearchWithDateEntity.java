package com.chat.uikit.chat.search.date;

import com.chad.library.adapter.base.entity.MultiItemEntity;

import java.util.List;

/**
 * 3/23/21 6:02 PM
 * 通过日期搜索聊天记录
 */
public class SearchWithDateEntity implements MultiItemEntity {
    public int itemType;
    public String day;
    public boolean selected;
    public boolean isToDay;
    public String date;
    public long dayCount;
    public long orderSeq;
    public boolean isNull;
    public List<SearchWithDateEntity> list;

    @Override
    public int getItemType() {
        return itemType;
    }

    public SearchWithDateEntity(int itemType) {
        this.itemType = itemType;
    }

}
