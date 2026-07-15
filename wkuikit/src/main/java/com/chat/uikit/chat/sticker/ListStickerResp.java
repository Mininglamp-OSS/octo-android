/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.chat.sticker;

import java.util.List;

/**
 * GET /sticker/user 响应包装。
 *
 * 服务端刻意让空集合返回 {"list":[]} 而非 404（issue #26），
 * 因此外层必须 wrap 一层 list 字段，不能直接反序列化成 List<WKSticker>。
 */
public class ListStickerResp {
    public List<WKSticker> list;
}
