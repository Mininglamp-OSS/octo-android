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

package com.chat.base.net.entity;

/**
 * POST /v1/file/upload?type=sticker (multipart) 的响应。
 * 对应 octo-server modules/file/api.go uploadFile() 的 resp map。
 * sticker_handle 仅当服务端配置了签名能力 (OCTO_MASTER_KEY) 时才下发。
 */
public class StickerUploadResult {
    public String path;
    public String name;
    public long size;
    public String ext;
    public String sticker_handle;
}
