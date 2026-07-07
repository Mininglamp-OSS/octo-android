/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.uikit.chat.search.image;

import com.chad.library.adapter.base.entity.MultiItemEntity;
import com.chat.base.entity.GlobalMessage;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

/**
 * 3/23/21 10:31 AM
 * 搜索聊天图片
 */
public class SearchImgEntity implements MultiItemEntity {
    public int itemType;
    public GlobalMessage message;
    public WKMessageContent originalContent;
    public String url;
    public String date;
    /** API 切换后 sender_name 不再来自本地 channel，存一份给收藏等回调使用。 */
    public String senderName;
//    public String clientMsgNo;
//    public long oldestOrderSeq;
//    public WKMessageContent messageContent;

    @Override
    public int getItemType() {
        return itemType;
    }
}
