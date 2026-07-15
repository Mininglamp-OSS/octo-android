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

import java.util.List;

/**
 * 服务端 {@code GET /v1/common/emojis} 的顶层响应体。
 *
 * <p>wire 契约（来自 {@code octo-server modules/common/emoji.go} {@code emojiManifestResp}）：
 * 顶层直接是 {@code {version, list}}，<b>无</b> {@code {status, data, msg}} 信封（服务端测试专门
 * 断言了这一点）。故 FastJson 直接反序列化到本类，不走通用 {@code CommonResponse<T>} 包装。
 *
 * <ul>
 *   <li>{@code version}：清单版本号，manifest 任一改动服务端会自增；供本地缓存判断是否需要刷新</li>
 *   <li>{@code list}：条目按服务端真源顺序排列；服务端保证非空且每条目 key 唯一</li>
 * </ul>
 *
 * <p>用 public 字段满足 FastJson 反序列化——release 包必须 proguard keep（见
 * {@code app/proguard-rules.pro}）。
 */
public class EmojiManifestResp {
    public int version;
    public List<EmojiManifestItem> list;
}
