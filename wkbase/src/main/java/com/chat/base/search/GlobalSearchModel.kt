package com.chat.base.search

import com.alibaba.fastjson.JSONObject
import com.chat.base.base.WKBaseModel
import com.chat.base.net.HttpResponseCode
import com.chat.base.net.IRequestResultListener
import com.chat.base.entity.GlobalSearch
import com.chat.base.entity.GlobalSearchReq
import com.chat.base.config.WKSharedPreferencesUtil

object GlobalSearchModel : WKBaseModel() {

    fun search(
        req: GlobalSearchReq,
        back: (code: Short, msg: String?, result: GlobalSearch?) -> Unit
    ) {
        val json = JSONObject()
        json["channel_id"] = req.channelId
        json["channel_type"] = req.channelType
        json["only_message"] = req.onlyMessage
        json["keyword"] = req.keyword
        json["from_uid"] = req.fromUID
        json["topic"] = req.topic
        json["limit"] = req.limit
        json["page"] = req.page
        json["start_time"] = req.startTime
        json["end_time"] = req.endTime
        json["content_type"] = req.contentType
        // Space 过滤：将 space_id 作为 URL query parameter 传递（与 iOS/Web 一致）
        val spaceId = WKSharedPreferencesUtil.getInstance().getSPWithUID("current_space_id")?.ifEmpty { null }
        request(createService(IService::class.java).search(json, spaceId),
            object : IRequestResultListener<GlobalSearch> {
                override fun onSuccess(result: GlobalSearch) {
                    back(HttpResponseCode.success, "", result)
                }

                override fun onFail(code: Int, msg: String) {
                    back(code.toShort(), msg, null)
                }
            })
    }

}