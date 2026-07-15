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

package com.chat.base.emoji;

/**
 * 服务端 {@code GET /v1/common/emojis} 响应中的单个自定义表情条目。
 *
 * <p>字段与服务端 {@code modules/common/emoji.go} 的 {@code emojiItem} 严格对齐：
 * <ul>
 *   <li>{@code key}：消息正文里的字面 token，如 {@code "[使命必达]"}——wire 契约，各端逐字节一致，
 *       服务端保证形如 {@code [xxx]}（{@code [} 开头 {@code ]} 结尾、中间非空且不含 {@code ]}）</li>
 *   <li>{@code name}：人类可读标签（如 {@code "使命必达"}），供选择器 title / 无障碍文本用；服务端保证非空</li>
 *   <li>{@code url}：图片地址——内置表情留空（{@code ""}）由客户端复用打包 asset；未来后台新增的表情
 *       会带非空 URL，客户端据此渲染</li>
 * </ul>
 *
 * <p>用 public 字段满足 FastJson 反序列化——release 包必须 proguard keep（见
 * {@code app/proguard-rules.pro}），否则 R8 混淆字段名会让 JSON 解析出空对象。
 */
public class EmojiManifestItem {
    public String key;
    public String name;
    public String url;
}
