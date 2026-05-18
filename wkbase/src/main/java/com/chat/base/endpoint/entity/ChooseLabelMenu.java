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

import java.util.List;

/**
 * 2020-11-19 11:54
 * 选择标签
 */
public class ChooseLabelMenu {
    public IChooseLabel iChooseLabel;

    public ChooseLabelMenu(IChooseLabel iChooseLabel) {
        this.iChooseLabel = iChooseLabel;
    }

    public interface IChooseLabel {
        void onResult(List<ChooseLabelEntity> list);
    }
}
