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

package com.chat.base.ui.components

import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import androidx.annotation.NonNull


class NormalClickableSpan(
    private val isShowUnderLine: Boolean,
    private val color: Int,
    val clickableContent: NormalClickableContent,
    @NonNull val iClick: IClick
) :
    ClickableSpan() {
    override fun onClick(p0: View) {
        iClick.onClick(p0)
    }

    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        ds.isUnderlineText = isShowUnderLine
        ds.color = color
        ds.clearShadowLayer()
    }

    interface IClick {
        fun onClick(view: View)
    }

}