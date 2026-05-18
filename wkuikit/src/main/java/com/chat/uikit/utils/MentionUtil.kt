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

package com.chat.uikit.utils

import android.graphics.Color
import android.widget.EditText
import java.util.*
import java.util.regex.Pattern

fun mentionDisplay(string: CharSequence): Boolean {
    val matcher = mentionEndPattern.matcher(string)
    return matcher.find()
}

fun mentionEnd(string: String): String? {
    val matcher = mentionEndPattern.matcher(string)
    return if (matcher.find()) {
        matcher.group().replace(" ", "").replace("@", "")
    } else {
        null
    }
}

fun deleteMentionEnd(editText: EditText) {
    val text = editText.text
    val matcher = mentionEndPattern.matcher(text)
    if (matcher.find()) {
        editText.setText(text.removeRange(matcher.start(), matcher.end()))
        editText.setSelection(editText.text.length)
    }
}

fun rendMentionContent(
    text: String?,
    userMap: Map<String, String>?
): String? {
    if (text == null || userMap == null) return text
    val matcher = mentionNumberPattern.matcher(text)
    val textStack = Stack<ReplaceData>()
    while (matcher.find()) {
        val number = matcher.group().substring(1)
        val name = userMap[number] ?: continue
        textStack.push(ReplaceData(matcher.start(), matcher.end(), "@$name"))
    }
    var result = text
    while (!textStack.empty()) {
        val replaceData = textStack.pop()
        result = result?.replaceRange(replaceData.start, replaceData.end, replaceData.replace)
    }
    return result
}

class ReplaceData(val start: Int, val end: Int, val replace: String)

private val mentionEndPattern by lazy {
    Pattern.compile("(?:\\s|^)@\\S*\$")
}

val mentionNumberPattern: Pattern by lazy {
    Pattern.compile("@[\\d]{4,}")
}

val MENTION_PRESS_COLOR by lazy { Color.parseColor("#665FA7E4") }
val MENTION_COLOR by lazy { Color.parseColor("#5FA7E4") }
