package com.chat.uikit.message

import com.chat.base.base.WKBaseModel
import com.chat.base.config.WKConstants
import com.chat.base.net.IRequestResultListener
import com.chat.uikit.db.ProhibitWordDB
import com.chat.uikit.enity.ProhibitWord

class ProhibitWordModel private constructor() : WKBaseModel() {
    companion object {
        val instance = SingletonHolder.holder
    }

    private object SingletonHolder {
        val holder = ProhibitWordModel()
    }

    private var words: List<ProhibitWord> = ArrayList()
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
                    ProhibitWordDB.instance.save(result)
                    getAll()
                }

                override fun onFail(code: Int, msg: String?) {
                }
            })
    }
}