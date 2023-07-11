package com.chat.uikit.enity;

import com.chad.library.adapter.base.entity.MultiItemEntity;

public class MailListEntity implements MultiItemEntity {
    public String name;
    public String uid;
    public String phone;
    public String zone;
    public String vercode;
    public int is_friend;
    public String pying;
    public int itemType = 0;

    @Override
    public int getItemType() {
        return itemType;
    }
}
