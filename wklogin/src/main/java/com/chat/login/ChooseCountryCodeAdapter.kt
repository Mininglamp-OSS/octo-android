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

package com.chat.login

import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.login.entity.CountryCodeEntity

class ChooseCountryCodeAdapter :
    BaseQuickAdapter<CountryCodeEntity, BaseViewHolder>(R.layout.item_choose_country_code_layout) {
    override fun convert(holder: BaseViewHolder, item: CountryCodeEntity) {
        val codeName: String = item.code.substring(2)
        holder.setText(R.id.nameTv, item.icon + " " + item.name + "（+" + codeName + "）")
        val index: Int = holder.bindingAdapterPosition
        val index1: Int = getPositionForSection(item.pying.substring(0, 1))
        holder.setText(R.id.pyTv, item.pying.substring(0, 1))
        holder.setGone(R.id.pyTv, index != index1)
    }


    private fun getPositionForSection(catalog: String): Int {
        var i = 0
        val size = data.size
        while (i < size) {
            val sortStr = data[i].pying.substring(0, 1)
            if (catalog.equals(sortStr, ignoreCase = true)) {
                return i
            }
            i++
        }
        return -1
    }
}