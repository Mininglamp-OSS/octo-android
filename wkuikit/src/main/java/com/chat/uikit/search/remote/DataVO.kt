package com.chat.uikit.search.remote

import com.chad.library.adapter.base.entity.MultiItemEntity
import com.chat.base.entity.GlobalChannel
import com.chat.base.entity.GlobalMessage

class DataVO(
    val type: Int,
    val channel: GlobalChannel?,
    val message: GlobalMessage?,
    val text: String,
    val keyword: String = "",
    val messageCount: Int = 0,
    val orderSeq: Long = 0
) :
    MultiItemEntity {

    override val itemType: Int
        get() = type

    companion object {
        const val SPAN: Int = -1
        const val TEXT: Int = 0
        const val CHANNEL: Int = 1
        const val MESSAGE: Int = 2
        const val SEARCH: Int = 3
        const val LOCAL_MSG: Int = 4
    }
}