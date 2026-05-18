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