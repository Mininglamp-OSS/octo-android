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
 * 用户收藏贴纸 —— 对齐服务端 stickerResp (modules/sticker/model.go)。
 *
 * 字段严格与服务端返回一致：sticker_id / path / category / placeholder /
 * format / sort / shortcode / keywords。width/height 服务端不返回（表结构里
 * 也没这两列），Android 侧渲染用固定 160dp。
 */
public class WKSticker {
    public String sticker_id;
    public String path;          // 可渲染 URL（服务端已规范化 CDN URL）
    public String category;      // 固定 "user"
    public String placeholder;   // 摘要占位文案，默认 "[表情]"
    public String format;        // gif/png/jpg/jpeg/webp
    public int sort;
    public String shortcode;     // 客户端联想 shortcode
    public List<String> keywords;
}
