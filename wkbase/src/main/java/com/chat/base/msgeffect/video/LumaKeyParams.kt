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
 * 单个 luma-key 特效的抠像参数，对齐 iOS `WKLumaKeyVideoView` 的实例属性。
 *
 * iOS 侧每个特效（Action / Classy / Shangfang）各自设置这些属性；Android 早期移植
 * 把它们写死成常量，导致新素材无法单独调参。这里提成值对象，默认值 = 移植时的原常量，
 * 所以不传参的调用方（action / classy）行为与改造前完全一致。
 *
 * 坐标约定：归一化 (0,0)~(1,1)，**原点左上**，与 iOS 头文件注释一致。
 * 注意 iOS 内部要把 y 翻转成 CoreImage 的左下原点（`1.0 - center.y`），
 * Android 的 Bitmap 本身就是左上原点、y 向下，**不需要翻转**。
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
     * iOS 注释记录了这个门控的来由：素材前段主体尚未铺满画面时就摆上完整盘，
     * 盘子会罩在还是纯黑的背景上，浅色模式下呈现为"浮在画面中间的黑圈"。
     */
    val centerProtectStartTimeMs: Long = 0L,
    val centerProtectRampDurationMs: Long = 0L,

    /**
     * CPU 抠像缓冲的长边上限（像素）。缓冲的**宽高比始终跟随素材**，这里只限制分辨率。
     *
     * iOS 走 Metal/CoreImage 在 GPU 上按设备原生分辨率抠像，没有这个限制；Android 是
     * 逐像素 Kotlin 循环 + TextureView.getBitmap 回读，分辨率直接决定每帧耗时，
     * 必须设上限。数值越大越清晰、越吃 CPU（耗时约与像素数成正比）：
     *   1280 → 589×1280 ≈ 0.75M px（默认，兼顾清晰度与流畅）
     *   1600 → 736×1600 ≈ 1.18M px
     *   1920 → 884×1920 ≈ 1.70M px（等于素材原生，最清晰，低端机可能掉帧）
     * 掉帧不会卡 UI：processFrame 有 `processing` 门闩，来不及处理的帧直接跳过。
     */
    val processMaxLongSide: Int = 1280,
) {
    companion object {
        /** action / classy 沿用的默认参数（等价于参数化改造前的写死常量）。 */
        @JvmField
        val DEFAULT = LumaKeyParams()

        /**
         * [尚方宝剑] 专用参数，逐项对齐 iOS `WKShangfangVideoEffect.m:48-67`。
         *
         * 其中中心盘圆心 (0.54, 0.41)、眼部圈 (0.60, 0.42) 是 iOS 侧按帧实测得到的，
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
        )
    }
}
