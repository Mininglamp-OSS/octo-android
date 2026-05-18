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

package com.chat.uikit.chat.face

import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.content.ContextCompat
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.uikit.R

class DropAdapter : BaseQuickAdapter<Drop, BaseViewHolder>(R.layout.item_chat_function_drop) {
    override fun convert(holder: BaseViewHolder, item: Drop) {
        val lineView = holder.getView<View>(R.id.lineView)
        val myShapeDrawable = lineView.background as GradientDrawable
        if (item.isSelect) {
            myShapeDrawable.setColor(ContextCompat.getColor(context, R.color.colorAccent));
        } else {
            myShapeDrawable.setColor(ContextCompat.getColor(context, R.color.transparent));
        }
    }
}