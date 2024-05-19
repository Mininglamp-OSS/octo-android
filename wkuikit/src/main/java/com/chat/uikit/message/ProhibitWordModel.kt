package com.chat.uikit.message

import com.chat.base.base.WKBaseModel
import com.chat.base.config.WKConstants
import com.chat.base.endpoint.EndpointCategory
import com.chat.base.endpoint.EndpointManager
import com.chat.base.net.IRequestResultListener
import com.chat.base.utils.WKReader
import com.chat.uikit.db.ProhibitWordDB
import com.chat.uikit.enity.ProhibitWord

class ProhibitWordModel private constructor() : WKBaseModel() {
    companion object {
        val instance = SingletonHolder.holder
    }

    private object SingletonHolder {
        val holder = ProhibitWordModel()
    }

    private var words: ArrayList<ProhibitWord> = ArrayList()
    fun getAll(): List<ProhibitWord> {
        if (words.isEmpty()) {
            words = ProhibitWordDB.instance.getAll()
        }
        return words
    }

    fun sync() {
        if (!WKConstants.isLogin()) return
        val version = ProhibitWordDB.instance.getMaxVersion()
        request(createService(MsgService::class.java).syncProhibitWord(version),
            object : IRequestResultListener<List<ProhibitWord>> {
                override fun onSuccess(result: List<ProhibitWord>) {
                    if (WKReader.isNotEmpty(result)) {
                        ProhibitWordDB.instance.save(result)
                        words.clear()
                        getAll()
                        val list: List<Any>? = EndpointManager.getInstance()
                            .invokes(EndpointCategory.refreshProhibitWord, 1)
                    }
                }

                override fun onFail(code: Int, msg: String?) {
                }
            })
    }
}