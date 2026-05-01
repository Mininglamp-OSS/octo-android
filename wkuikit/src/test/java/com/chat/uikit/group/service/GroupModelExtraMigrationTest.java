package com.chat.uikit.group.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.xinbida.wukongim.entity.WKChannelMemberExtras;

import org.junit.Test;

import java.util.HashMap;

/**
 * YUJ-183 · Fix B Step 1 · {@code GroupModel.isExtraMissingExternalFields} 判定矩阵。
 *
 * <p>根因：后端 membersync 是 version-based 增量接口，老用户本地 maxVersion 已经越过
 * 外部群字段首次下发的 version 点，之后如果没有 bump，客户端永远拉不到
 * {@code is_external / source_space_* / home_space_*}。
 * 该判定函数决定 {@code GroupModel.groupMembersSync} 是否需要用 version=0 强制全量重拉。
 *
 * <p>判定契约：
 * <ul>
 *     <li>null / 空 map → true（缺字段，需要强制全量）</li>
 *     <li>只要 {@code home_space_id} 或 {@code is_external} 任一在 → false（已下发过，走正常增量）</li>
 *     <li>其他 key 存在但这两个 key 都不在 → true（历史脏 row，需要强制全量）</li>
 * </ul>
 */
public class GroupModelExtraMigrationTest {

    @Test
    public void nullExtras_treatedAsMissing_forceFull() {
        assertTrue(GroupModel.isExtraMissingExternalFields(null));
    }

    @Test
    public void emptyExtras_treatedAsMissing_forceFull() {
        assertTrue(GroupModel.isExtraMissingExternalFields(new HashMap<>()));
    }

    @Test
    public void extrasWithOnlyHomeSpaceId_notMissing_doesNotForceFull() {
        HashMap<String, Object> m = new HashMap<>();
        m.put(WKChannelMemberExtras.homeSpaceID, "spaceA");
        assertFalse(GroupModel.isExtraMissingExternalFields(m));
    }

    @Test
    public void extrasWithOnlyIsExternal_notMissing_doesNotForceFull() {
        HashMap<String, Object> m = new HashMap<>();
        m.put(WKChannelMemberExtras.isExternal, 1);
        assertFalse(GroupModel.isExtraMissingExternalFields(m));
    }

    @Test
    public void extrasWithIsExternalZero_stillNotMissing_doesNotForceFull() {
        // 内部成员的正确状态：is_external=0 已经写进 extraMap 了，说明 sync 已下发过。
        HashMap<String, Object> m = new HashMap<>();
        m.put(WKChannelMemberExtras.isExternal, 0);
        assertFalse(GroupModel.isExtraMissingExternalFields(m));
    }

    @Test
    public void extrasWithBothKeys_notMissing_doesNotForceFull() {
        HashMap<String, Object> m = new HashMap<>();
        m.put(WKChannelMemberExtras.homeSpaceID, "spaceA");
        m.put(WKChannelMemberExtras.isExternal, 1);
        m.put(WKChannelMemberExtras.sourceSpaceID, "spaceB");
        m.put(WKChannelMemberExtras.sourceSpaceName, "外部");
        assertFalse(GroupModel.isExtraMissingExternalFields(m));
    }

    @Test
    public void extrasWithUnrelatedKeys_butNoExternalMarkers_missing_forceFull() {
        // 老数据：只有 vercode 之类的老字段，没有外部群标记 → 强制全量
        HashMap<String, Object> m = new HashMap<>();
        m.put(WKChannelMemberExtras.WKCode, "vercode123");
        assertTrue(GroupModel.isExtraMissingExternalFields(m));
    }

    @Test
    public void extrasWithSourceSpaceButNoHomeOrIsExternal_stillMissing_forceFull() {
        // 理论上不该出现（GroupModel.serialize 写 source_space_* 时也会写 is_external）。
        // 但防御性保底：只要两个核心 key 都不在，就判定为缺字段。
        // 原因：旧版 SDK/server 如果只下发 source_space_id 而漏了 is_external/home_space_id，
        // ExternalSourceResolver / UserDetailActivity.isExternalUser 的判定链仍然不完整。
        HashMap<String, Object> m = new HashMap<>();
        m.put(WKChannelMemberExtras.sourceSpaceID, "spaceB");
        assertTrue(GroupModel.isExtraMissingExternalFields(m));
    }
}
