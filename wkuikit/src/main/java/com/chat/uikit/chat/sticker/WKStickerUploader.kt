/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.uikit.chat.sticker

import android.net.Uri
import android.text.TextUtils
import com.alibaba.fastjson.JSONObject
import com.chat.base.R as BaseR
import com.chat.base.base.WKBaseModel
import com.chat.base.config.WKApiConfig
import com.chat.base.config.WKConfig
import com.chat.base.net.ApiService
import com.chat.base.net.IRequestResultListener
import com.chat.base.net.entity.UploadFileUrl
import com.chat.base.net.ud.WKUploader
import com.chat.base.utils.WKToastUtils
import java.io.File
import java.util.UUID

/**
 * 上传自己贴纸的三步链路，对齐 iOS `WKStickerUploadService`：
 *
 * 1. [StickerUploadValidator.validate] —— 客户端校验（magic bytes / size / dim），
 *    失败直接吐 toast 不打网络
 * 2. GET /v1/file/upload/credentials?path=/sticker/{uid}/{uuid}.{ext}&type=sticker
 *    → 拿 uploadUrl / downloadUrl
 * 3. PUT uploadUrl 文件二进制 → 存 CDN
 * 4. POST /v1/sticker/user body {path, width, height, format} → 注册元数据
 * 5. 成功 → [WKStickerManager.onStickerAdded] 更新缓存 + LiveData
 *
 * ⚠️ handle 字段：iOS 上传 STEP 3 响应会带 `sticker_handle`（HMAC 签名），
 * POST /sticker/user 时带 `handle` 字段。Android 现有 `/file/upload/credentials`
 * 响应结构无 handle 字段。本实现先不带 handle 试探：
 * - 服务端 200 → 完成
 * - 服务端 400 `handle_required` → 服务端配置了 `sticker.handle_required=true`，
 *   需要与后端对齐把 handle 加入 credentials 响应，或改走 iOS server-proxied 上传
 *
 * 呼叫方：面板 UI 层拿到用户选中的图 → new File → uploader.upload(file, callback)。
 * 选图（PickVisualMedia）由 Activity/Fragment 层负责，本类只做上传编排。
 */
object WKStickerUploader : WKBaseModel() {

    private val apiService by lazy { createService(ApiService::class.java) }
    private val stickerService by lazy { createService(StickerService::class.java) }

    /** UI 层回调：上传状态。progress 0..100（Retrofit 层未提供细粒度进度时可能一直是 0/100）。 */
    interface Callback {
        fun onProgress(progress: Int) {}
        fun onSuccess(sticker: WKSticker)
        fun onError(messageResId: Int)
    }

    /**
     * 触发上传。所有阶段的错误都通过 [callback.onError] 回调，UI 层直接 toast 对应
     * 字符串即可。本方法必须在主线程调（内部会切 IO / 主线程）。
     */
    fun upload(file: File, callback: Callback) {
        // 1. 校验
        val meta = StickerUploadValidator.validate(file).getOrElse { throwable ->
            val failure = (throwable as? StickerUploadValidator.FailureException)?.failure
                ?: StickerUploadValidator.Failure.IoError
            callback.onError(failure.stringResId)
            return
        }

        val uid = WKConfig.getInstance().uid
        if (uid.isNullOrEmpty()) {
            callback.onError(BaseR.string.str_sticker_upload_failed)
            return
        }

        // 2. 服务端签发上传凭证
        val ext = ".${meta.format.ext}"
        val contentType = mimeFor(meta.format)
        val fileSize = file.length()
        // path 前缀必须以 sticker/{uid}/ 开头（服务端 /sticker/user 上传校验）
        val remotePath = "/sticker/$uid/${UUID.randomUUID().toString().replace("-", "")}$ext"

        val url = Uri.parse(WKApiConfig.baseUrl + "file/upload/credentials").buildUpon().apply {
            appendQueryParameter("path", remotePath)
            appendQueryParameter("type", "sticker")
            appendQueryParameter("filename", file.name)
            appendQueryParameter("contentType", contentType)
            appendQueryParameter("fileSize", fileSize.toString())
        }.build().toString()

        request(apiService.getUploadCredentials(url), object : IRequestResultListener<UploadFileUrl> {
            override fun onSuccess(result: UploadFileUrl?) {
                val uploadUrl = result?.uploadUrl
                val downloadUrl = result?.downloadUrl
                if (uploadUrl.isNullOrEmpty() || downloadUrl.isNullOrEmpty()) {
                    callback.onError(BaseR.string.str_sticker_upload_failed)
                    return
                }
                val ct = if (!TextUtils.isEmpty(result.contentType)) result.contentType else contentType
                // 3. PUT 文件到 CDN
                callback.onProgress(10)
                WKUploader.getInstance().putUpload(
                    uploadUrl,
                    file.absolutePath,
                    ct,
                    result.contentDisposition,
                    file.absolutePath, // tag: 用 path 作为唯一标记
                    object : WKUploader.IUploadBack {
                        override fun onSuccess(unusedUrl: String?) {
                            callback.onProgress(90)
                            registerSticker(remotePath, meta, callback)
                        }

                        override fun onError() {
                            callback.onError(BaseR.string.str_sticker_upload_failed)
                        }
                    }
                )
            }

            override fun onFail(code: Int, msg: String?) {
                callback.onError(BaseR.string.str_sticker_upload_failed)
            }
        })
    }

    // 4. POST /sticker/user 注册元数据
    private fun registerSticker(path: String, meta: StickerUploadValidator.Meta, callback: Callback) {
        val body = JSONObject()
        body["path"] = path
        if (meta.width > 0) body["width"] = meta.width
        if (meta.height > 0) body["height"] = meta.height
        body["format"] = meta.format.ext
        // handle 字段：先不带，若服务端拒绝再考虑加

        request(stickerService.uploadSticker(body), object : IRequestResultListener<WKSticker> {
            override fun onSuccess(result: WKSticker?) {
                if (result == null) {
                    callback.onError(BaseR.string.str_sticker_upload_failed)
                    return
                }
                callback.onProgress(100)
                WKStickerManager.onStickerAdded(result)
                WKToastUtils.getInstance().showToastNormal(
                    com.chat.base.WKBaseApplication.getInstance().context
                        .getString(BaseR.string.str_sticker_upload_success)
                )
                callback.onSuccess(result)
            }

            override fun onFail(code: Int, msg: String?) {
                // 服务端 message 优先（可能是 "配额已达上限"），退回默认 "上传失败"
                if (!msg.isNullOrEmpty()) {
                    WKToastUtils.getInstance().showToastNormal(msg)
                    callback.onError(0) // 已经吐过 toast，UI 层不用再吐
                } else {
                    callback.onError(BaseR.string.str_sticker_upload_failed)
                }
            }
        })
    }

    private fun mimeFor(format: StickerUploadValidator.Format): String = when (format) {
        StickerUploadValidator.Format.GIF -> "image/gif"
        StickerUploadValidator.Format.PNG -> "image/png"
        StickerUploadValidator.Format.JPEG -> "image/jpeg"
        StickerUploadValidator.Format.WEBP -> "image/webp"
    }
}
