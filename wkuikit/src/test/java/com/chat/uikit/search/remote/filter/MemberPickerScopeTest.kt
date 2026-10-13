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

package com.chat.uikit.search.remote.filter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 覆盖 [MemberPickerScope]：两个维度 × (自己 / bot / 普通成员) 的候选集口径。 */
class MemberPickerScopeTest {

    // ---- 发送者：全都可选（「只看我发的」必须可达，bot 是一等消息来源） ----

    @Test
    fun sender_includes_self() {
        assertTrue(MemberPickerScope.shouldInclude(MemberPickerScope.SENDER, isSelf = true, isRobot = false))
    }

    @Test
    fun sender_includes_robot() {
        assertTrue(MemberPickerScope.shouldInclude(MemberPickerScope.SENDER, isSelf = false, isRobot = true))
    }

    @Test
    fun sender_includes_normal_member() {
        assertTrue(MemberPickerScope.shouldInclude(MemberPickerScope.SENDER, isSelf = false, isRobot = false))
    }

    // ---- 包含成员：排除自己与 bot（刻意保留 ForwardDirectoryActivity 口径） ----

    @Test
    fun member_excludes_self() {
        assertFalse(MemberPickerScope.shouldInclude(MemberPickerScope.MEMBER, isSelf = true, isRobot = false))
    }

    @Test
    fun member_excludes_robot() {
        assertFalse(MemberPickerScope.shouldInclude(MemberPickerScope.MEMBER, isSelf = false, isRobot = true))
    }

    @Test
    fun member_includes_normal_member() {
        assertTrue(MemberPickerScope.shouldInclude(MemberPickerScope.MEMBER, isSelf = false, isRobot = false))
    }

    /** 未知 scope 走保守口径（等同 MEMBER），避免越权把自己/bot 放进候选。 */
    @Test
    fun unknown_scope_falls_back_to_member_semantics() {
        assertFalse(MemberPickerScope.shouldInclude(99, isSelf = true, isRobot = false))
        assertFalse(MemberPickerScope.shouldInclude(99, isSelf = false, isRobot = true))
        assertTrue(MemberPickerScope.shouldInclude(99, isSelf = false, isRobot = false))
    }
}
