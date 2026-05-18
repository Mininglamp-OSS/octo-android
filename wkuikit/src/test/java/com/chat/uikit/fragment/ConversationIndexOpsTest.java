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

package com.chat.uikit.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.chat.uikit.enity.ChatConversationMsg;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKUIConversationMsg;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *  · {@link ConversationIndexOps} host-side 单元测试。
 *
 * <p>覆盖 ChatFragment 把 {@code allConversations} 从裸 ArrayList 升级到
 * 「List + Map 索引」后的一致性合约：
 * <ul>
 *     <li>{@link ConversationIndexOps#upsert} 的「已存在直接 return，不重复插入」语义</li>
 *     <li>{@link ConversationIndexOps#removeByKey} / {@code removeByChannel} 同步清两处</li>
 *     <li>{@link ConversationIndexOps#clearAll} / {@link ConversationIndexOps#rebuildIndex}
 *         批量替换路径</li>
 *     <li>section header（{@code isSectionHeader}）不进索引，不影响 UI 分组</li>
 *     <li>rebuildIndex 能收敛历史污染留下的 duplicate entry</li>
 * </ul>
 */
public class ConversationIndexOpsTest {

    private static final String BOT_SYSTEM = "u_10000";
    private static final String BOTFATHER = "botfather";
    private static final String FRIEND_UID = "uid_alice";
    private static final String GROUP_NO = "g_work_001";

    private List<ChatConversationMsg> list;
    private Map<String, ChatConversationMsg> index;

    @Before
    public void setUp() {
        list = new ArrayList<>();
        index = new HashMap<>();
    }

    // ---------- upsert ----------

    @Test
    public void upsert_newEntry_addsToListAndIndex() {
        ChatConversationMsg msg = conv(BOT_SYSTEM, WKChannelType.PERSONAL);

        ChatConversationMsg returned = ConversationIndexOps.upsert(list, index, msg);

        assertSame(msg, returned);
        assertEquals(1, list.size());
        assertEquals(1, index.size());
        assertSame(msg, list.get(0));
        assertSame(msg, index.get(BOT_SYSTEM + "_" + WKChannelType.PERSONAL));
    }

    @Test
    public void upsert_sameKey_noDuplicateListEntry_returnsExisting() {
        //  核心契约：同一 (channelID, channelType) 再次 upsert 时，
        // 列表绝不多出一条；返回的是之前已经插入的 entry。
        ChatConversationMsg first = conv(BOT_SYSTEM, WKChannelType.PERSONAL);
        ConversationIndexOps.upsert(list, index, first);

        ChatConversationMsg second = conv(BOT_SYSTEM, WKChannelType.PERSONAL);
        ChatConversationMsg returned = ConversationIndexOps.upsert(list, index, second);

        assertSame("upsert 必须返回现有 entry，不能吞掉引用", first, returned);
        assertNotSame(second, returned);
        assertEquals("列表必须仍然只有 1 条", 1, list.size());
        assertEquals(1, index.size());
    }

    @Test
    public void upsert_differentChannelTypes_sameChannelID_areTreatedAsDifferentKeys() {
        // botfather 作为 PERSONAL 存在；群号恰好同名时不应被当做同一会话
        ChatConversationMsg personalMsg = conv(BOTFATHER, WKChannelType.PERSONAL);
        ChatConversationMsg groupMsg = conv(BOTFATHER, WKChannelType.GROUP);

        ConversationIndexOps.upsert(list, index, personalMsg);
        ConversationIndexOps.upsert(list, index, groupMsg);

        assertEquals(2, list.size());
        assertEquals(2, index.size());
        assertSame(personalMsg, index.get(BOTFATHER + "_" + WKChannelType.PERSONAL));
        assertSame(groupMsg, index.get(BOTFATHER + "_" + WKChannelType.GROUP));
    }

    @Test
    public void upsert_sectionHeader_appendedButNotIndexed() {
        ChatConversationMsg header = new ChatConversationMsg("cat_work", "工作");
        ChatConversationMsg returned = ConversationIndexOps.upsert(list, index, header);

        assertSame(header, returned);
        assertEquals("section header 仍需进列表以供 UI 分组渲染", 1, list.size());
        assertTrue("section header 不进索引（没有 channelID）", index.isEmpty());
    }

    @Test
    public void upsert_nullOrEmptyChannelId_appendedButNotIndexed() {
        ChatConversationMsg msgNull = conv(null, WKChannelType.PERSONAL);
        ChatConversationMsg msgEmpty = conv("", WKChannelType.PERSONAL);

        ConversationIndexOps.upsert(list, index, msgNull);
        ConversationIndexOps.upsert(list, index, msgEmpty);

        assertEquals(2, list.size());
        assertTrue(index.isEmpty());
    }

    @Test
    public void upsert_nullMsg_returnsNull_noMutation() {
        assertNull(ConversationIndexOps.upsert(list, index, null));
        assertTrue(list.isEmpty());
        assertTrue(index.isEmpty());
    }

    // ---------- removeByKey / removeByChannel ----------

    @Test
    public void removeByKey_removesFromListAndIndex() {
        ChatConversationMsg msg = conv(FRIEND_UID, WKChannelType.PERSONAL);
        ConversationIndexOps.upsert(list, index, msg);

        boolean removed = ConversationIndexOps.removeByKey(list, index,
                FRIEND_UID + "_" + WKChannelType.PERSONAL);

        assertTrue(removed);
        assertTrue(list.isEmpty());
        assertTrue(index.isEmpty());
    }

    @Test
    public void removeByChannel_convenienceFormEquivalentToRemoveByKey() {
        ChatConversationMsg msg = conv(FRIEND_UID, WKChannelType.PERSONAL);
        ConversationIndexOps.upsert(list, index, msg);

        boolean removed = ConversationIndexOps.removeByChannel(list, index,
                FRIEND_UID, WKChannelType.PERSONAL);

        assertTrue(removed);
        assertTrue(list.isEmpty());
        assertTrue(index.isEmpty());
    }

    @Test
    public void removeByKey_missingKey_returnsFalse_noMutation() {
        ChatConversationMsg msg = conv(FRIEND_UID, WKChannelType.PERSONAL);
        ConversationIndexOps.upsert(list, index, msg);

        boolean removed = ConversationIndexOps.removeByKey(list, index, "does_not_exist_1");

        assertFalse(removed);
        assertEquals(1, list.size());
        assertEquals(1, index.size());
    }

    @Test
    public void removeByKey_removesHistoricDuplicateResidueFromList() {
        // 历史污染场景：列表里有多条同 key 的残留（pre- bug 状态）。
        // removeByKey 必须清掉所有同 key 残留，不留漏网之鱼。
        ChatConversationMsg a = conv(BOT_SYSTEM, WKChannelType.PERSONAL);
        ChatConversationMsg b = conv(BOT_SYSTEM, WKChannelType.PERSONAL);
        ChatConversationMsg c = conv(BOT_SYSTEM, WKChannelType.PERSONAL);
        list.add(a);
        list.add(b);
        list.add(c);
        // index 只有一条（现实：旧代码从未写 index；新路径 upsert 才写入）
        index.put(BOT_SYSTEM + "_" + WKChannelType.PERSONAL, a);

        boolean removed = ConversationIndexOps.removeByKey(list, index,
                BOT_SYSTEM + "_" + WKChannelType.PERSONAL);

        assertTrue(removed);
        assertTrue("列表里所有同 key 残留都要清掉", list.isEmpty());
        assertTrue(index.isEmpty());
    }

    @Test
    public void removeByKey_nullOrEmpty_returnsFalse() {
        assertFalse(ConversationIndexOps.removeByKey(list, index, null));
        assertFalse(ConversationIndexOps.removeByKey(list, index, ""));
    }

    // ---------- clearAll ----------

    @Test
    public void clearAll_emptiesBothStructuresInLockstep() {
        ConversationIndexOps.upsert(list, index, conv(BOT_SYSTEM, WKChannelType.PERSONAL));
        ConversationIndexOps.upsert(list, index, conv(FRIEND_UID, WKChannelType.PERSONAL));
        ConversationIndexOps.upsert(list, index, conv(GROUP_NO, WKChannelType.GROUP));
        assertEquals(3, list.size());
        assertEquals(3, index.size());

        ConversationIndexOps.clearAll(list, index);

        assertTrue(list.isEmpty());
        assertTrue(index.isEmpty());
    }

    // ---------- rebuildIndex ----------

    @Test
    public void rebuildIndex_mapsListEntriesByKey_whenListIsCleanAfterBulkAdd() {
        // sortMsg 的 clear + addAll 批量替换 path：模拟「直接写 list 不走 upsert」
        list.add(conv(BOT_SYSTEM, WKChannelType.PERSONAL));
        list.add(conv(FRIEND_UID, WKChannelType.PERSONAL));
        list.add(conv(GROUP_NO, WKChannelType.GROUP));
        index.clear();

        int dropped = ConversationIndexOps.rebuildIndex(list, index);

        assertEquals(0, dropped);
        assertEquals(3, list.size());
        assertEquals(3, index.size());
        assertNotNull(index.get(BOT_SYSTEM + "_" + WKChannelType.PERSONAL));
        assertNotNull(index.get(FRIEND_UID + "_" + WKChannelType.PERSONAL));
        assertNotNull(index.get(GROUP_NO + "_" + WKChannelType.GROUP));
    }

    @Test
    public void rebuildIndex_dropsDuplicateListEntries_keepsFirstOccurrence() {
        // 历史污染场景：列表里已经塞进了 3 条 u_10000（未经过 upsert）。
        // rebuildIndex 必须在重建索引时把重复项 drop 掉，收敛到每 key 只剩 1 条。
        ChatConversationMsg first = conv(BOT_SYSTEM, WKChannelType.PERSONAL);
        ChatConversationMsg second = conv(BOT_SYSTEM, WKChannelType.PERSONAL);
        ChatConversationMsg third = conv(BOT_SYSTEM, WKChannelType.PERSONAL);
        list.add(first);
        list.add(second);
        list.add(third);

        int dropped = ConversationIndexOps.rebuildIndex(list, index);

        assertEquals(2, dropped);
        assertEquals("rebuildIndex 应收敛到每 key 单条", 1, list.size());
        assertEquals(1, index.size());
        assertSame("保留最先出现的 entry（对齐 upsert 已存在直接 return 语义）",
                first, list.get(0));
        assertSame(first, index.get(BOT_SYSTEM + "_" + WKChannelType.PERSONAL));
    }

    @Test
    public void rebuildIndex_afterClear_indexEmpty() {
        list.add(conv(BOT_SYSTEM, WKChannelType.PERSONAL));
        index.put(BOT_SYSTEM + "_" + WKChannelType.PERSONAL, list.get(0));

        ConversationIndexOps.clearAll(list, index);
        int dropped = ConversationIndexOps.rebuildIndex(list, index);

        assertEquals(0, dropped);
        assertTrue(list.isEmpty());
        assertTrue("clear 后 rebuildIndex 不应凭空造出 entry", index.isEmpty());
    }

    @Test
    public void rebuildIndex_skipsSectionHeaders() {
        list.add(new ChatConversationMsg("cat_1", "工作"));
        list.add(conv(GROUP_NO, WKChannelType.GROUP));
        list.add(new ChatConversationMsg("cat_ungrouped", "未分组"));

        int dropped = ConversationIndexOps.rebuildIndex(list, index);

        assertEquals(0, dropped);
        assertEquals(3, list.size());
        assertEquals("只有有 channelID 的 entry 进索引", 1, index.size());
        assertNotNull(index.get(GROUP_NO + "_" + WKChannelType.GROUP));
    }

    // ---------- full-cycle consistency ----------

    @Test
    public void fullCycle_upsertAddUpdateRemoveClear_listAndIndexStayConsistent() {
        // 模拟 ChatFragment 真实工作流程：冷启动 sync → 追加单 msg → 删除 → 批量替换 → 清空
        ConversationIndexOps.upsert(list, index, conv(BOT_SYSTEM, WKChannelType.PERSONAL));
        ConversationIndexOps.upsert(list, index, conv(FRIEND_UID, WKChannelType.PERSONAL));
        assertIndexMatchesList();

        // 重复 upsert 不造重复
        ConversationIndexOps.upsert(list, index, conv(BOT_SYSTEM, WKChannelType.PERSONAL));
        assertEquals(2, list.size());
        assertIndexMatchesList();

        // 删除
        ConversationIndexOps.removeByChannel(list, index, FRIEND_UID, WKChannelType.PERSONAL);
        assertEquals(1, list.size());
        assertIndexMatchesList();

        // 批量替换（sortMsg 路径）
        ConversationIndexOps.clearAll(list, index);
        list.add(conv(BOT_SYSTEM, WKChannelType.PERSONAL));
        list.add(conv(GROUP_NO, WKChannelType.GROUP));
        ConversationIndexOps.rebuildIndex(list, index);
        assertIndexMatchesList();

        // 清空
        ConversationIndexOps.clearAll(list, index);
        assertTrue(list.isEmpty());
        assertTrue(index.isEmpty());
    }

    @Test
    public void upsert_indexHitButListMissing_preservesIndexRef_doesNotAppend() {
        // 防御分支：index 有 key，但列表已经没有该 entry（索引和列表曾经走失）。
        // upsert 当下应该仍然 return 索引里的 ref 不插入重复——即便列表看起来没这条。
        // ChatFragment#resetData 里的 `if (inserted != newMsg) filterAndDisplay()` 路径
        // 依赖这个契约做防御恢复。
        ChatConversationMsg ghost = conv(BOT_SYSTEM, WKChannelType.PERSONAL);
        index.put(BOT_SYSTEM + "_" + WKChannelType.PERSONAL, ghost);
        // list 是空的（模拟 index-ahead-of-list 走失）

        ChatConversationMsg candidate = conv(BOT_SYSTEM, WKChannelType.PERSONAL);
        ChatConversationMsg returned = ConversationIndexOps.upsert(list, index, candidate);

        assertSame("upsert 应返回索引里已有的 ghost，不 push candidate", ghost, returned);
        assertNotSame(candidate, returned);
        // 注意：此时列表依然是空（upsert 只做 key 级保护，不补列表）。修复由
        // ChatFragment 侧 filterAndDisplay / rebuildConversationIndex 兜底。
        assertTrue(list.isEmpty());
        assertEquals(1, index.size());
    }

    @Test
    public void removeByKey_multipleDuplicatesInList_removesAllAndDropsIndex() {
        // codex review P1：historic pollution 场景——同 key 在列表里有 3 条 duplicate，
        // removeByKey 必须把所有 3 条都清掉，不留漏网。
        ChatConversationMsg a = conv(BOT_SYSTEM, WKChannelType.PERSONAL);
        ChatConversationMsg b = conv(BOT_SYSTEM, WKChannelType.PERSONAL);
        ChatConversationMsg c = conv(BOT_SYSTEM, WKChannelType.PERSONAL);
        ChatConversationMsg other = conv(FRIEND_UID, WKChannelType.PERSONAL);
        list.add(a);
        list.add(b);
        list.add(other);
        list.add(c);
        index.put(BOT_SYSTEM + "_" + WKChannelType.PERSONAL, a);
        index.put(FRIEND_UID + "_" + WKChannelType.PERSONAL, other);

        boolean removed = ConversationIndexOps.removeByKey(list, index,
                BOT_SYSTEM + "_" + WKChannelType.PERSONAL);

        assertTrue(removed);
        assertEquals("其它 key 的 entry 不受影响", 1, list.size());
        assertSame(other, list.get(0));
        assertEquals(1, index.size());
        assertNull(index.get(BOT_SYSTEM + "_" + WKChannelType.PERSONAL));
        assertNotNull(index.get(FRIEND_UID + "_" + WKChannelType.PERSONAL));
    }

    private void assertIndexMatchesList() {
        int withKey = 0;
        for (ChatConversationMsg m : list) {
            String key = ConversationIndexOps.keyOf(m);
            if (key == null) continue;
            assertSame("list entry 必须在 index 里且引用相等", m, index.get(key));
            withKey++;
        }
        assertEquals("index 不应有幽灵 entry", withKey, index.size());
    }

    // ---------- helpers ----------

    private static ChatConversationMsg conv(String channelID, byte channelType) {
        // 绕过 ChatConversationMsg(WKUIConversationMsg) 构造器——它会触碰
        // WKConfig.getInstance() / WKIMUtils.getInstance() 单例（底层走 SharedPreferences，
        // JVM 单测路径下 returnDefaultValues 也没法喂出真实实例）。
        // 用 section-header 构造器占位后回填字段，得到一个纯数据载体。
        ChatConversationMsg msg = new ChatConversationMsg("__test_placeholder_sid__", "__test_placeholder__");
        msg.isSectionHeader = false;
        msg.sectionId = null;
        msg.sectionTitle = null;
        WKUIConversationMsg ui = new WKUIConversationMsg();
        ui.channelID = channelID;
        ui.channelType = channelType;
        msg.uiConversationMsg = ui;
        return msg;
    }
}
