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

package com.chat.base.endpoint.entity;

/**
 * 2020-11-06 15:13
 * 视频录制
 */
public class VideoReadingMenu extends BaseEndpoint {
    public IRedingResult iRedingResult;

    public VideoReadingMenu(IRedingResult iRedingResult) {
        this.iRedingResult = iRedingResult;
    }

    public interface IRedingResult {
        void onResult(long second, String path, String videoPath, long size);
    }
}
