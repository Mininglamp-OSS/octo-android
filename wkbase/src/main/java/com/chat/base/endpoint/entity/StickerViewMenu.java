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

package com.chat.base.endpoint.entity;

import androidx.annotation.NonNull;

import com.chat.base.msg.IConversationContext;

/**
 * 12/31/20 10:06 AM
 * 表情view
 */
public class StickerViewMenu {
    public IConversationContext conversationContext;
    public IStickerStatusListener iStickerStatusListener;

    public StickerViewMenu(@NonNull IConversationContext conversationContext, IStickerStatusListener iStickerStatusListener) {
        this.conversationContext = conversationContext;
        this.iStickerStatusListener = iStickerStatusListener;
    }

    public interface IStickerStatusListener {
        void onSearchViewShow(boolean isShow);
    }
}
