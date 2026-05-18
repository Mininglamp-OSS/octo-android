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

package com.chat.base.entity

import com.xinbida.wukongim.entity.WKChannelType

class GlobalChannel {
    var channel_id: String = ""
    var channel_type: Byte = WKChannelType.PERSONAL
    var channel_name: String = ""
    var channel_remark: String = ""


    fun getHtmlName():String{
        return channel_name.replace("<mark>", "<font color=#7761F4>")
            .replace("</mark>", "</font>")
    }
}