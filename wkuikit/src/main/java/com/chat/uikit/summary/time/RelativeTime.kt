/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.summary.time

import android.content.Context
import com.chat.uikit.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 1:1 对齐 iOS [OctoSummaryDateFormat]:
 *   - [parseISO] 容错三种格式: 带分秒小数 ISO / 标准 ISO / "yyyy-MM-dd HH:mm:ss" UTC
 *   - [relativeFromISO]: <1min "刚刚", <1h "Nm前", <24h "Nh前", <7d "Nd前", 否则本地
 *   - [localFromISO]: "yyyy-MM-dd HH:mm" 本地时区
 */
object RelativeTime {

    private val isoMicroFormat: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply { isLenient = false }

    private val isoFormat: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { isLenient = false }

    private val plainUtcFormat: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }

    private val outFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    }

    fun parseISO(iso: String?): Date? {
        if (iso.isNullOrEmpty()) return null
        return runCatching { isoMicroFormat.parse(iso) }.getOrNull()
            ?: runCatching { isoFormat.parse(iso) }.getOrNull()
            ?: runCatching { plainUtcFormat.parse(iso) }.getOrNull()
    }

    fun relativeFromISO(context: Context, iso: String?): String {
        val d = parseISO(iso) ?: return ""
        val diffSec = ((System.currentTimeMillis() - d.time) / 1_000L).coerceAtLeast(0L)
        return when {
            diffSec < 60 ->
                context.getString(R.string.summary_time_just_now)
            diffSec < 60 * 60 ->
                context.getString(R.string.summary_time_minutes_ago, (diffSec / 60).toInt())
            diffSec < 60 * 60 * 24 ->
                context.getString(R.string.summary_time_hours_ago, (diffSec / 3600).toInt())
            diffSec < 60 * 60 * 24 * 7 ->
                context.getString(R.string.summary_time_days_ago, (diffSec / 86_400).toInt())
            else -> localFromISO(iso)
        }
    }

    fun localFromISO(iso: String?): String {
        val d = parseISO(iso) ?: return ""
        return outFormat.get()?.format(d).orEmpty()
    }
}
