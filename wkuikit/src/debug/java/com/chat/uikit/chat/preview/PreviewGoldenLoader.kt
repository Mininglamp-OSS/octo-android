/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.chat.uikit.chat.preview

import android.content.Context
import android.util.Log

/**
 * 从 assets 里扫出所有 AdaptiveCard golden 供预览页展示。
 *
 * 约定的目录结构（新增卡片样式只加目录，不改代码）：
 * ```
 * assets/
 *   interactive_cards/
 *     <templateId>/                    e.g. docs.access-request
 *       manifest.json                  可选：模板元数据（版本/renderProfile/…）
 *       goldens/
 *         <state>.card.json            e.g. pending.card.json / approved.card.json
 * ```
 *
 * 加载失败（IO 异常 / JSON 非法）**不 throw**，只 log warn 后跳过。让预览页尽量可用。
 */
object PreviewGoldenLoader {

    private const val TAG = "CardPreview"
    private const val ROOT = "interactive_cards"
    private const val GOLDENS_SUBDIR = "goldens"
    private const val CARD_SUFFIX = ".card.json"

    /** 一条 golden = 预览页一行。cardVersion 目前从模板 manifest 里读；缺省 1.5。 */
    data class Golden(
        val templateId: String,
        val state: String,
        val cardJson: String,
        val cardVersion: String = "1.5",
    )

    /** 扫 assets 下所有 golden。排序：优先展示带 action 的状态（pending），再是终态。 */
    fun loadAll(ctx: Context): List<Golden> {
        val assets = ctx.assets
        val templateDirs = try {
            assets.list(ROOT).orEmpty()
        } catch (t: Throwable) {
            Log.w(TAG, "读 $ROOT 目录失败", t)
            return emptyList()
        }
        return templateDirs.sorted().flatMap { tid ->
            val goldensPath = "$ROOT/$tid/$GOLDENS_SUBDIR"
            val files = try {
                assets.list(goldensPath).orEmpty()
            } catch (t: Throwable) {
                Log.w(TAG, "读 $goldensPath 失败", t)
                emptyArray()
            }
            files
                .filter { it.endsWith(CARD_SUFFIX) }
                .sortedBy { stateOrder(it.removeSuffix(CARD_SUFFIX)) }
                .mapNotNull { file -> readGolden(ctx, tid, goldensPath, file) }
        }
    }

    /**
     * 状态展示顺序：pending 有交互按钮（destructive/positive 关键路径）最先展示；
     * approved / rejected 是终态卡（一个 button），排在后面。未知状态排最后。
     */
    private fun stateOrder(state: String): Int = when (state) {
        "pending" -> 0
        "approved" -> 1
        "rejected" -> 2
        else -> 100
    }

    private fun readGolden(
        ctx: Context,
        templateId: String,
        goldensPath: String,
        fileName: String,
    ): Golden? {
        val path = "$goldensPath/$fileName"
        return try {
            val json = ctx.assets.open(path).bufferedReader().use { it.readText() }
            if (json.isBlank()) {
                Log.w(TAG, "golden 为空 $path")
                return null
            }
            Golden(
                templateId = templateId,
                state = fileName.removeSuffix(CARD_SUFFIX),
                cardJson = json,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "读 golden 失败 $path", t)
            null
        }
    }
}
