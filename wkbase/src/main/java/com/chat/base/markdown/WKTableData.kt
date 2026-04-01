package com.chat.base.markdown

import org.commonmark.ext.gfm.tables.TableCell

data class WKTableData(
    val headers: List<String>,
    val rows: List<List<String>>,
    val alignments: List<TableCell.Alignment?>
)
