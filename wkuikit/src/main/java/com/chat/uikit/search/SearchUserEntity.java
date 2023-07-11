package com.chat.uikit.search;

import com.chad.library.adapter.base.entity.MultiItemEntity;
import com.chat.uikit.enity.UserInfo;

/**
 * 2019-11-20 11:42
 * 搜索结果
 */
public class SearchUserEntity implements MultiItemEntity {
    public int itemType;
    public int status = 0;
    public int exist;
    //是否显示申请按钮
    public boolean showApply = true;
    public UserInfo data;

    @Override
    public int getItemType() {
        return itemType;
    }
}
