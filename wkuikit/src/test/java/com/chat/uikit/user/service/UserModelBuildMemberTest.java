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

package com.chat.uikit.user.service;

import com.chat.uikit.group.service.entity.GroupMember;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelMemberExtras;
import com.xinbida.wukongim.entity.WKChannelType;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 *  /  问题 2 —
 * {@link UserModel#buildMemberFromUserInfo(GroupMember)} 外部群字段透传单元测试。
 *
 * <p>根因回放：UserDetailActivity 打开一个群成员时会调 /users/{uid}?group_no=，
 * 原实现漏把 {@code is_external / source_space_* / home_space_*} 写入
 * {@code member.extraMap}，随后 {@code save(member)} 走 INSERT OR REPLACE 清空 DB
 * 的 {@code extra} 列。第二次打开群成员列表从本地 DB 读数据时 {@code @SpaceName}
 * 后缀就消失了（member 列表的 adapter 只能从 extraMap 拿到这些字段）。
 *
 * <p>本测试锁死透传契约——任何一个 key 漏写都会红灯。
 */
public class UserModelBuildMemberTest {

    private static GroupMember externalMember() {
        GroupMember gm = new GroupMember();
        gm.uid = "uid_alice";
        gm.name = "Alice";
        gm.remark = "Ali";
        gm.group_no = "grp_001";
        gm.role = 2;
        gm.status = 1;
        gm.is_deleted = 0;
        gm.version = 77;
        gm.invite_uid = "uid_invite";
        gm.robot = 0;
        gm.forbidden_expir_time = 0L;
        gm.created_at = "2026-04-30 10:00:00";
        gm.updated_at = "2026-04-30 10:05:00";
        gm.vercode = "vc_abc";
        gm.is_external = 1;
        gm.source_space_id = "space_beta";
        gm.source_space_name = "Beta";
        gm.home_space_id = "space_alpha";
        gm.home_space_name = "Alpha";
        return gm;
    }

    @Test
    public void externalMember_propagatesAllKeysIntoExtraMap() {
        WKChannelMember member = UserModel.buildMemberFromUserInfo(externalMember());

        assertNotNull("extraMap 必须非 null，否则后续 save() 会清空 DB 的 extra 列", member.extraMap);
        assertEquals("vc_abc", member.extraMap.get(WKChannelMemberExtras.WKCode));
        assertEquals(1, member.extraMap.get(WKChannelMemberExtras.isExternal));
        assertEquals("space_beta", member.extraMap.get(WKChannelMemberExtras.sourceSpaceID));
        assertEquals("Beta", member.extraMap.get(WKChannelMemberExtras.sourceSpaceName));
        assertEquals("space_alpha", member.extraMap.get(WKChannelMemberExtras.homeSpaceID));
        assertEquals("Alpha", member.extraMap.get(WKChannelMemberExtras.homeSpaceName));

        // 核心字段正常透传
        assertEquals("uid_alice", member.memberUID);
        assertEquals("Alice", member.memberName);
        assertEquals("Ali", member.memberRemark);
        assertEquals("grp_001", member.channelID);
        assertEquals(WKChannelType.GROUP, member.channelType);
        assertEquals(2, member.role);
        assertEquals(1, member.status);
        assertEquals(77, member.version);
    }

    @Test
    public void internalMember_writesIsExternalZeroAndOmitsOptionalSpaceFields() {
        GroupMember gm = externalMember();
        gm.is_external = 0;
        gm.source_space_id = null;
        gm.source_space_name = null;
        gm.home_space_id = null;
        gm.home_space_name = null;

        WKChannelMember member = UserModel.buildMemberFromUserInfo(gm);

        assertNotNull(member.extraMap);
        // is_external 固定透传（int 原始字段，0 也要写进 extraMap 覆盖旧值）
        assertEquals(0, member.extraMap.get(WKChannelMemberExtras.isExternal));
        // 空字符串/null 的 space 字段不透传，避免污染下游
        assertFalse(member.extraMap.containsKey(WKChannelMemberExtras.sourceSpaceID));
        assertFalse(member.extraMap.containsKey(WKChannelMemberExtras.sourceSpaceName));
        assertFalse(member.extraMap.containsKey(WKChannelMemberExtras.homeSpaceID));
        assertFalse(member.extraMap.containsKey(WKChannelMemberExtras.homeSpaceName));
    }

    @Test
    public void emptyStringSpaceFields_areSkipped_notPersisted() {
        GroupMember gm = externalMember();
        gm.source_space_id = "";
        gm.source_space_name = "";
        gm.home_space_id = "";
        gm.home_space_name = "";

        WKChannelMember member = UserModel.buildMemberFromUserInfo(gm);

        assertNotNull(member.extraMap);
        assertFalse(member.extraMap.containsKey(WKChannelMemberExtras.sourceSpaceID));
        assertFalse(member.extraMap.containsKey(WKChannelMemberExtras.sourceSpaceName));
        assertFalse(member.extraMap.containsKey(WKChannelMemberExtras.homeSpaceID));
        assertFalse(member.extraMap.containsKey(WKChannelMemberExtras.homeSpaceName));
        // 但 is_external 仍然透传（根源字段）
        assertTrue(member.extraMap.containsKey(WKChannelMemberExtras.isExternal));
    }

    @Test
    public void robotMember_overridesNameWithUsername() {
        GroupMember gm = externalMember();
        gm.robot = 1;
        gm.username = "Alice-bot";

        WKChannelMember member = UserModel.buildMemberFromUserInfo(gm);

        // robot==1 且 username 非空 → memberName 用 username
        assertEquals("Alice-bot", member.memberName);
        assertEquals(1, member.robot);
    }

    @Test
    public void nullVercode_doesNotPutWKCodeKey() {
        GroupMember gm = externalMember();
        gm.vercode = null;

        WKChannelMember member = UserModel.buildMemberFromUserInfo(gm);

        assertNotNull(member.extraMap);
        assertFalse(member.extraMap.containsKey(WKChannelMemberExtras.WKCode));
    }

    /**
     *  · 实名徽章 Phase A：透传 realname_verified → extraMap，
     * 让 UserDetailActivity 调 /users/{uid}?group_no= 也能刷新群成员列表的蓝勾。
     */
    @Test
    public void realnameVerifiedTrue_propagatesIntoExtraMap() {
        GroupMember gm = externalMember();
        gm.realname_verified = Boolean.TRUE;

        WKChannelMember member = UserModel.buildMemberFromUserInfo(gm);

        assertNotNull(member.extraMap);
        assertEquals(Boolean.TRUE, member.extraMap.get(WKChannelMemberExtras.realnameVerified));
    }

    /**
     *  P0-2：显式 false 必须写进 extraMap，tri-state resolver 才能
     * 让「已取消实名」的显式状态覆盖 channel 侧的 stale true。
     */
    @Test
    public void realnameVerifiedExplicitFalse_isWrittenToExtraMap() {
        GroupMember gm = externalMember();
        gm.realname_verified = Boolean.FALSE;

        WKChannelMember member = UserModel.buildMemberFromUserInfo(gm);

        assertNotNull(member.extraMap);
        assertTrue(member.extraMap.containsKey(WKChannelMemberExtras.realnameVerified));
        assertEquals(Boolean.FALSE, member.extraMap.get(WKChannelMemberExtras.realnameVerified));
    }

    /**
     *  P0-2：后端未下发 realname_verified 时字段为 null，不写进 extraMap，
     * 让 resolver tri-state 读到 null，回落到 channel 侧的 profile。
     */
    @Test
    public void realnameVerifiedNull_isOmittedFromExtraMap() {
        GroupMember gm = externalMember();
        gm.realname_verified = null;

        WKChannelMember member = UserModel.buildMemberFromUserInfo(gm);

        assertNotNull(member.extraMap);
        assertFalse(member.extraMap.containsKey(WKChannelMemberExtras.realnameVerified));
    }
}
