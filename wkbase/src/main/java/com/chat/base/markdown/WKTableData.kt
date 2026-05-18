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

package com.chat.base.markdown

import org.commonmark.ext.gfm.tables.TableCell

data class WKTableCellLink(
    val start: Int,
    val end: Int,
    val url: String
)

data class WKTableCell(
    val text: String,
    val links: List<WKTableCellLink> = emptyList()
)

data class WKTableData(
    val headers: List<WKTableCell>,
    val rows: List<List<WKTableCell>>,
    val alignments: List<TableCell.Alignment?>
)
