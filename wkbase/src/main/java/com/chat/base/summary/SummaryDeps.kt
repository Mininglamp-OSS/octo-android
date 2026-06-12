/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.base.summary

import com.chat.base.summary.repository.SummaryRepository
import com.chat.base.summary.repository.SummaryRepositoryImpl

/**
 * Summary 模块的轻量 Service Locator。
 *
 * 项目目前未引入 Hilt/Koin —— 不为新模块单独引一个 DI 框架,只暴露一个可替换的
 * Repository 单例,测试时可调用 [overrideRepository] 注入 Fake。
 */
object SummaryDeps {

    @Volatile
    private var repositoryOverride: SummaryRepository? = null

    @Volatile
    private var defaultRepository: SummaryRepository? = null

    val repository: SummaryRepository
        get() = repositoryOverride ?: defaultRepository ?: synchronized(this) {
            defaultRepository ?: SummaryRepositoryImpl().also { defaultRepository = it }
        }

    /** 仅供测试. */
    fun overrideRepository(repo: SummaryRepository?) {
        repositoryOverride = repo
    }
}
