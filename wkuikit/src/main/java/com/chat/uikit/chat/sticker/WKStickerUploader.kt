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
import com.alibaba.fastjson.JSONObject
import com.chat.base.R as BaseR
import com.chat.base.base.WKBaseModel
import com.chat.base.config.WKApiConfig
import com.chat.base.config.WKConfig
import com.chat.base.net.ApiService
import com.chat.base.net.IRequestResultListener
import com.chat.base.net.entity.StickerUploadResult
import com.chat.base.utils.WKLogUtils
import com.chat.base.utils.WKToastUtils
import java.io.File
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

/**
 * 上传自己贴纸的两步链路，对齐服务端 modules/sticker/api.go 的强制约定：
 *
 * 贴纸不支持预签名直传 —— 上传句柄只能在 modules/file 同时掌握认证上传者与
 * 已过内容校验(魔数/尺寸/格式)字节的地方签发，因此必须走 multipart 端点，见
 * octo-server modules/file/api.go:852-857 的服务端强制拒绝（预签名会绕过内容
 * 校验，允许伪造超额/非图对象注册为贴纸 URL）。
 *
 * 1. [StickerUploadValidator.validate] —— 客户端预校验（magic bytes / size / dim），
 *    失败直接吐 toast 不打网络（服务端还会再校验一遍，客户端校验只是快速失败）
 * 2. POST /v1/file/upload?type=sticker&path=/{uid}/{uuid}.{ext} (multipart)
 *    → 服务端校验 + 落盘（objectKey = type+path = sticker/{uid}/{uuid}.{ext}），
 *    响应 {path（含 sticker/ 前缀的完整 key/URL）, sticker_handle?}
 * 3. POST /v1/sticker/user body {path, width, height, format, handle?} → 注册元数据
 * 4. 成功 → [WKStickerManager.onStickerAdded] 更新缓存 + LiveData
 *
 * handle 字段：服务端配置了签名能力(OCTO_MASTER_KEY)时，步骤2响应会带
 * sticker_handle，步骤3原样回传作为 handle。未配置时 sticker_handle 为空，
 * 步骤3不带 handle（服务端回退到路径形状校验）。
 *
 * 呼叫方：面板 UI 层拿到用户选中的图 → new File → uploader.upload(file, callback)。
 * 选图（PickVisualMedia）由 Activity/Fragment 层负责，本类只做上传编排。
 */
object WKStickerUploader : WKBaseModel() {

    private const val TAG = "StickerUpload"

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
        WKLogUtils.d(TAG, "upload start file=${file.absolutePath} exists=${file.exists()} size=${file.length()}")
        // 1. 校验
        val meta = StickerUploadValidator.validate(file).getOrElse { throwable ->
            val failure = (throwable as? StickerUploadValidator.FailureException)?.failure
                ?: StickerUploadValidator.Failure.IoError
            WKLogUtils.e(TAG, "validate failed: failure=$failure file=${file.absolutePath} size=${file.length()}")
            callback.onError(failure.stringResId)
            return
        }
        WKLogUtils.d(TAG, "validate passed meta: format=${meta.format} width=${meta.width} height=${meta.height}")

        val uid = WKConfig.getInstance().uid
        if (uid.isNullOrEmpty()) {
            WKLogUtils.e(TAG, "upload abort: uid is empty")
            callback.onError(BaseR.string.str_sticker_upload_failed)
            return
        }

        // 2. multipart 上传：服务端在这一步做内容校验（魔数/尺寸/格式/1MB上限）并落盘
        val ext = ".${meta.format.ext}"
        val contentType = mimeFor(meta.format)
        // path 不带 type 前缀（服务端会自己拼 fileType+path，见 getFilePath 对
        // type=sticker 的参考实现），只需 /{uid}/{uuid}.ext；服务端校验 uid 段与登录用户一致
        val remotePath = "/$uid/${UUID.randomUUID().toString().replace("-", "")}$ext"

        val url = Uri.parse(WKApiConfig.baseUrl + "file/upload").buildUpon().apply {
            appendQueryParameter("type", "sticker")
            appendQueryParameter("path", remotePath)
            appendQueryParameter("contenttype", contentType)
        }.build().toString()
        WKLogUtils.d(TAG, "multipart upload start remotePath=$remotePath contentType=$contentType url=$url")

        val mediaType = contentType.toMediaType()
        val fileBody = file.asRequestBody(mediaType)
        val part = MultipartBody.Part.createFormData("file", file.name, fileBody)

        callback.onProgress(10)
        request(apiService.uploadMultipart(url, part), object : IRequestResultListener<StickerUploadResult> {
            override fun onSuccess(result: StickerUploadResult?) {
                if (result == null || result.path.isNullOrEmpty()) {
                    WKLogUtils.e(TAG, "multipart upload onSuccess but path empty, remotePath=$remotePath")
                    callback.onError(BaseR.string.str_sticker_upload_failed)
                    return
                }
                WKLogUtils.d(TAG, "multipart upload success path=${result.path} handle=${result.sticker_handle != null}")
                callback.onProgress(90)
                registerSticker(result.path, result.sticker_handle, meta, callback)
            }

            override fun onFail(code: Int, msg: String?) {
                WKLogUtils.e(TAG, "multipart upload onFail code=$code msg=$msg remotePath=$remotePath")
                if (!msg.isNullOrEmpty()) {
                    WKToastUtils.getInstance().showToastNormal(msg)
                    callback.onError(0)
                } else {
                    callback.onError(BaseR.string.str_sticker_upload_failed)
                }
            }
        })
    }

    // 3. POST /sticker/user 注册元数据
    private fun registerSticker(path: String, handle: String?, meta: StickerUploadValidator.Meta, callback: Callback) {
        val body = JSONObject()
        body["path"] = path
        if (meta.width > 0) body["width"] = meta.width
        if (meta.height > 0) body["height"] = meta.height
        body["format"] = meta.format.ext
        if (!handle.isNullOrEmpty()) body["handle"] = handle
        WKLogUtils.d(TAG, "POST /sticker/user body=$body")

        request(stickerService.uploadSticker(body), object : IRequestResultListener<WKSticker> {
            override fun onSuccess(result: WKSticker?) {
                if (result == null) {
                    WKLogUtils.e(TAG, "registerSticker onSuccess but result=null, path=$path")
                    callback.onError(BaseR.string.str_sticker_upload_failed)
                    return
                }
                WKLogUtils.d(TAG, "registerSticker success sticker_id=${result.sticker_id} path=${result.path}")
                callback.onProgress(100)
                WKStickerManager.onStickerAdded(result)
                WKToastUtils.getInstance().showToastNormal(
                    com.chat.base.WKBaseApplication.getInstance().context
                        .getString(BaseR.string.str_sticker_upload_success)
                )
                callback.onSuccess(result)
            }

            override fun onFail(code: Int, msg: String?) {
                WKLogUtils.e(TAG, "registerSticker onFail code=$code msg=$msg path=$path")
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
