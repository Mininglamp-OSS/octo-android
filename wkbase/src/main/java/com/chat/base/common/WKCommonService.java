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

package com.chat.base.common;

import com.chat.base.emoji.EmojiManifestResp;
import com.chat.base.entity.AppModule;
import com.chat.base.entity.ChannelInfoEntity;
import com.chat.base.entity.WKAPPConfig;
import com.chat.base.entity.AppVersion;
import com.chat.base.entity.WKChannelState;

import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * 4/21/21 6:25 PM
 */
interface WKCommonService {
    @GET("common/updater/{os}/{version}")
    Observable<AppVersion> getAppNewVersion(@Path("os") String os, @Path("version") String version);

    @GET("common/appconfig")
    Observable<WKAPPConfig> getAppConfig();

    @GET("channel/state")
    Observable<WKChannelState> getChannelState(@Query("channel_id") String channelID, @Query("channel_type") byte channelType);

    @GET("channels/{channelID}/{channelType}")
    Observable<ChannelInfoEntity> getChannel(@Path("channelID") String channelID, @Path("channelType") byte channelType);

    @GET("common/appmodule")
    Observable<List<AppModule>> getAppModule();

    /** 服务端 GET /v1/common/emojis：公开无鉴权，返回内置自定义表情清单。响应体
     *  是顶层 {version, list}，无 {status, data, msg} 信封，故 T 直接是 {@link EmojiManifestResp}。 */
    @GET("common/emojis")
    Observable<EmojiManifestResp> getEmojis();
}
