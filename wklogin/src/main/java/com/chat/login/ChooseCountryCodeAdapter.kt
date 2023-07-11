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