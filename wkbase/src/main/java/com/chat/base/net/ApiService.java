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

package com.chat.base.net;

import com.chat.base.net.entity.UploadFileUrl;
import com.chat.base.net.entity.StickerUploadResult;

import io.reactivex.rxjava3.core.Observable;
import okhttp3.MultipartBody;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Url;

/**
 * 2020-07-21 11:53
 */
public interface ApiService {

    @GET
    Observable<UploadFileUrl> getUploadFileUrl(@Url String url);

    @GET
    Observable<UploadFileUrl> getUploadCredentials(@Url String url);

    // 贴纸专用：服务端要求贴纸内容必须经 multipart 上传校验（魔数/尺寸/格式），
    // 不支持预签名直传。url 需带 query 参数 type=sticker&path=...&contenttype=...
    @Multipart
    @POST
    Observable<StickerUploadResult> uploadMultipart(@Url String url, @Part MultipartBody.Part file);
}
