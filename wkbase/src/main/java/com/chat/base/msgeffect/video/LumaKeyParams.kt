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

package com.chat.base.msgeffect.video

/**
 * 单个 luma-key 特效的抠像参数。
 *
 * 每个特效（Action / Classy / Shangfang）的素材构图不同，需要各自调参；早期实现
 * 把这些值写死成常量，导致新素材无法单独调。这里提成值对象，默认值 = 改造前的原常量，
 * 所以不传参的调用方（action / classy）行为与改造前完全一致。
 *
 * 坐标约定：归一化 (0,0)~(1,1)，**原点左上、y 向下**，与 Bitmap 的像素坐标一致。
 */
data class LumaKeyParams(
    /** 亮度抠像阈值：luma < threshold 视为背景。 */
    val lumaThreshold: Float = 0.10f,
    /** 过渡软度：luma 在 [threshold, threshold+tolerance] 间平滑到不透明。 */
    val lumaTolerance: Float = 0.12f,
    /** 背景"半透明纱"的 alpha 下限（越暗越接近它）。 */
    val backgroundAlphaFloor: Float = 0.05f,
    /** 背景"半透明纱"的 alpha 上限（越亮越接近它，留住光晕）。 */
    val backgroundAlphaCeil: Float = 0.45f,

    /** 中心保护盘半径（相对画面短边比例）。0 关闭。 */
    val centerProtectRadius: Float = 0.30f,
    /** 中心保护盘边缘过渡软度（相对短边比例）。 */
    val centerProtectSoftness: Float = 0.14f,
    /** 中心保护盘圆心 X（归一化，原点左上）。 */
    val centerProtectCenterX: Float = 0.5f,
    /** 中心保护盘圆心 Y（归一化，原点左上）。 */
    val centerProtectCenterY: Float = 0.5f,

    /**
     * 眼部小保护圈 —— 补住主体内部与背景同为暗色、luma 无法区分的细节（深色眼睛/眉毛）。
     * 半径 > 0 时**全程常驻**，不受中心盘的时间门控影响；<= 0 关闭（默认关闭）。
     */
    val eyeProtectCenterX: Float = 0.5f,
    val eyeProtectCenterY: Float = 0.5f,
    val eyeProtectRadius: Float = 0f,
    val eyeProtectSoftness: Float = 0f,

    /**
     * 中心保护盘"延时出现"门控，为"主体甩入、定格后才该出现完整盘"的素材设计。
     *
     * - `<= 0`（默认）：中心盘从头常驻全强度，即 action / classy 的原始行为。
     * - `> 0`：播放位置 < startTime 时强度 0；随后在 rampDuration 内线性淡入到全强度。
     *
     * 门控的来由：素材前段主体尚未铺满画面时就摆上完整盘，盘子会罩在还是纯黑的
     * 背景上，浅色模式下呈现为"浮在画面中间的黑圈"。
     */
    val centerProtectStartTimeMs: Long = 0L,
    val centerProtectRampDurationMs: Long = 0L,

    /**
     * CPU 抠像缓冲的长边上限（像素）。缓冲的**宽高比始终跟随素材**，这里只限制分辨率。
     *
     * 抠像是逐像素 Kotlin 循环 + TextureView.getBitmap 回读，每帧耗时约与像素数
     * 成正比，必须设上限。
     *
     * **默认 960 是为了不动已有特效的性能画像**：改造前缓冲写死 540×960 = 518k px/帧，
     * 按 960 长边跟随素材比例算下来，action(1080×2004) → 517×960 = 496k px、
     * classy(1080×2352) → 440×960 = 422k px，都不超过改前，所以低端机上的流畅度不会退。
     * 新素材若需要更清晰，在自己的参数里单独调高（见 [SHANGFANG]），不要动这个默认值。
     *
     * 掉帧不会卡 UI：processFrame 有 `processing` 门闩，来不及处理的帧直接跳过。
     */
    val processMaxLongSide: Int = 960,
) {
    companion object {
        /** action / classy 沿用的默认参数（等价于参数化改造前的写死常量）。 */
        @JvmField
        val DEFAULT = LumaKeyParams()

        /**
         * [尚方宝剑] 专用参数。
         *
         * 其中中心盘圆心 (0.54, 0.41)、眼部圈 (0.60, 0.42) 是逐帧实测量出来的，
         * 换素材时需要重新量。
         */
        @JvmField
        val SHANGFANG = LumaKeyParams(
            lumaThreshold = 0.10f,
            lumaTolerance = 0.12f,
            backgroundAlphaFloor = 0.05f,
            backgroundAlphaCeil = 0.45f,
            centerProtectRadius = 0.30f,
            centerProtectSoftness = 0.12f,
            centerProtectCenterX = 0.54f,
            centerProtectCenterY = 0.41f,
            eyeProtectCenterX = 0.60f,
            eyeProtectCenterY = 0.42f,
            eyeProtectRadius = 0.13f,
            eyeProtectSoftness = 0.05f,
            centerProtectStartTimeMs = 600L,
            centerProtectRampDurationMs = 650L,
            // 素材 884×1920，主体是有细节的人物+剑刃，960 长边下边缘发糊，
            // 单独提到 1280（589×1280 ≈ 754k px/帧）。只影响本特效。
            processMaxLongSide = 1280,
        )
    }
}
