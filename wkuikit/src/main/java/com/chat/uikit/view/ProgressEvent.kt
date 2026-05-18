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

package com.chat.uikit.view


data class ProgressEvent(val id: String, var progress: Float, val status: Int) {
    init {
        if (progress.isNaN()) {
            progress = 0f
        }
    }
    companion object {
        fun loadingEvent(id: String, progress: Float) = ProgressEvent(
            id,
            progress,
            CircleProgress.STATUS_LOADING
        )

        fun playEvent(id: String, progress: Float = 0f) =
            ProgressEvent(id, progress, CircleProgress.STATUS_PLAY)

        fun pauseEvent(id: String, progress: Float = 0f) =
            ProgressEvent(id, progress, CircleProgress.STATUS_PAUSE)

        fun errorEvent(id: String): ProgressEvent =
            ProgressEvent(id, 0f, CircleProgress.STATUS_ERROR)
    }
}
