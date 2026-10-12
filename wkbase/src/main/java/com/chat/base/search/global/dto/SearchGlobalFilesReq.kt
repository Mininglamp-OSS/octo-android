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

package com.chat.base.search.global.dto

/**
 * 全局文件搜索请求。对应 `POST /v1/messages/_search_global_files`。
 *
 * 契约与 L2 [SearchGlobalMessagesReq] 对齐（服务端契约待确认，本类按
 * `FileHit` 已预留的 channel_id/channel_type 字段推断的契约实现）：
 *  - [keyword] 可空（浏览模式：仅按 filters 过滤）。
 *  - [filters] 一般带 `file_exts`（见 [GlobalSearchFilters.fileExts]）。
 *  - [pageSize] ∈ [1, 100]，默认 20。
 *  - [cursor] 服务端签发的不透明游标，翻页时原样回传；首次请求为空。
 *  - [sequence] 前端自增，仅用于本地竞态防护，服务端不解析。
 *
 * 该端点服务端尚未确认是否已上线，接入前需与后端对齐实际字段。
 */
class SearchGlobalFilesReq(
    val keyword: String? = null,
    val filters: GlobalSearchFilters,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    val cursor: String? = null,
    val sequence: Long = 0L,
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
    }
}
