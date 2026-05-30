package com.chat.uikit.chat.search.file;

import com.chad.library.adapter.base.entity.MultiItemEntity;
import com.chat.base.entity.GlobalMessage;

public class SearchFileEntity implements MultiItemEntity {
    public static final int TYPE_FILE = 0;
    public static final int TYPE_DATE_HEADER = 1;

    public int itemType;
    public GlobalMessage message;
    public String date;
    public String fileName;
    public String extension;
    public long fileSize;

    @Override
    public int getItemType() {
        return itemType;
    }
}
