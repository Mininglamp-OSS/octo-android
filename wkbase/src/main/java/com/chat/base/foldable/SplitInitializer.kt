package com.chat.base.foldable

import android.content.Context
import androidx.startup.Initializer
import androidx.window.embedding.RuleController
import com.chat.base.R

/**
 * App Startup [Initializer] that loads the Activity Embedding split rules from
 * `res/xml/main_split_config.xml` and registers them with [RuleController].
 *
 * Declared in `app/src/main/AndroidManifest.xml` via `androidx.startup.InitializationProvider`.
 * See YUJ-248 (GH #176) — 折叠屏 L3-A Activity Embedding.
 */
class SplitInitializer : Initializer<Any> {

    override fun create(context: Context): Any {
        val controller = RuleController.getInstance(context)
        controller.setRules(
            RuleController.parseRules(context, R.xml.main_split_config)
        )
        return Any()
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
