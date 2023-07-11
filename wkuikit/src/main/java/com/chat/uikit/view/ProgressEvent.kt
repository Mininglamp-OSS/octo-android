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
