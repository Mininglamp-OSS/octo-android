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

package com.chat.base.search.channel.dto

/** `/_search_around` 返回锚点上下文。before/after 均按时间正序（oldest-first）。 */
class AroundResult {
    var before: List<MessageHit> = emptyList()
    lateinit var anchor: MessageHit
    var after: List<MessageHit> = emptyList()
    var has_more_before: Boolean = false
    var has_more_after: Boolean = false
}
