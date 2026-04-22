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
