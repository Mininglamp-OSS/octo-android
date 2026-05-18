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