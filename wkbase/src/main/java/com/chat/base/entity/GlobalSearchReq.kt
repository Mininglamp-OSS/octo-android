package com.chat.base.entity

class GlobalSearchReq(
    val onlyMessage: Int = 0,
    val keyword: String,
    val channelId: String,
    val channelType: Byte,
    val fromUID: String,
    val topic: String,
    val contentType: List<Int>,
    val page: Int = 1,
    val limit: Int = 20,
    val startTime: Long,
    val endTime: Long
)