package com.dmwork.im

import android.app.Application
import com.didichuxing.doraemonkit.DoKit

object DebugTools {
    fun init(app: Application) {
        DoKit.Builder(app)
            .alwaysShowMainIcon(false)
            .productId("")
            .build()
    }
}
