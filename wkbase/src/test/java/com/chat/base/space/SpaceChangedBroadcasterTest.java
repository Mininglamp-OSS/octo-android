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

package com.chat.base.space;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 *  · {@link SpaceChangedBroadcaster} host-side 单元测试。
 *
 * <p>覆盖：
 * <ul>
 *     <li>add/remove listener 基本契约</li>
 *     <li>重复 add 只生效一次（addIfAbsent 语义）</li>
 *     <li>null listener 容忍</li>
 *     <li>old == new 时静默吞掉（不广播）</li>
 *     <li>null 被归一化为空串</li>
 *     <li>单 listener 抛异常不影响其它 listener</li>
 * </ul>
 */
public class SpaceChangedBroadcasterTest {

    private static final String SPACE_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String SPACE_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Before
    public void setUp() {
        SpaceChangedBroadcaster.clearListenersForTest();
    }

    @After
    public void tearDown() {
        SpaceChangedBroadcaster.clearListenersForTest();
    }

    @Test
    public void listener_receives_change_event() {
        List<String[]> received = new ArrayList<>();
        SpaceChangedBroadcaster.Listener l =
                (oldId, newId) -> received.add(new String[]{oldId, newId});

        SpaceChangedBroadcaster.addListener(l);
        SpaceChangedBroadcaster.notifyChanged(SPACE_A, SPACE_B);

        assertEquals(1, received.size());
        assertEquals(SPACE_A, received.get(0)[0]);
        assertEquals(SPACE_B, received.get(0)[1]);
    }

    @Test
    public void same_space_id_is_silently_swallowed() {
        List<String> received = new ArrayList<>();
        SpaceChangedBroadcaster.addListener((oldId, newId) -> received.add(newId));

        SpaceChangedBroadcaster.notifyChanged(SPACE_A, SPACE_A);
        SpaceChangedBroadcaster.notifyChanged("", "");
        SpaceChangedBroadcaster.notifyChanged(null, null);
        SpaceChangedBroadcaster.notifyChanged(null, "");
        SpaceChangedBroadcaster.notifyChanged("", null);

        assertTrue("same/empty/null on both sides must not fire", received.isEmpty());
    }

    @Test
    public void null_is_normalized_to_empty_string() {
        List<String[]> received = new ArrayList<>();
        SpaceChangedBroadcaster.addListener((oldId, newId) -> received.add(new String[]{oldId, newId}));

        SpaceChangedBroadcaster.notifyChanged(null, SPACE_B);
        SpaceChangedBroadcaster.notifyChanged(SPACE_A, null);

        assertEquals(2, received.size());
        assertEquals("", received.get(0)[0]);
        assertEquals(SPACE_B, received.get(0)[1]);
        assertEquals(SPACE_A, received.get(1)[0]);
        assertEquals("", received.get(1)[1]);
    }

    @Test
    public void removed_listener_does_not_receive_events() {
        List<String> received = new ArrayList<>();
        SpaceChangedBroadcaster.Listener l = (oldId, newId) -> received.add(newId);

        SpaceChangedBroadcaster.addListener(l);
        SpaceChangedBroadcaster.notifyChanged(SPACE_A, SPACE_B);
        assertEquals(1, received.size());

        SpaceChangedBroadcaster.removeListener(l);
        SpaceChangedBroadcaster.notifyChanged(SPACE_B, SPACE_A);
        assertEquals("removed listener must not receive", 1, received.size());
    }

    @Test
    public void duplicate_add_is_deduplicated() {
        List<String> received = new ArrayList<>();
        SpaceChangedBroadcaster.Listener l = (oldId, newId) -> received.add(newId);

        SpaceChangedBroadcaster.addListener(l);
        SpaceChangedBroadcaster.addListener(l);
        SpaceChangedBroadcaster.addListener(l);
        assertEquals(1, SpaceChangedBroadcaster.listenerCountForTest());

        SpaceChangedBroadcaster.notifyChanged(SPACE_A, SPACE_B);
        assertEquals("duplicate add must only fire once", 1, received.size());
    }

    @Test
    public void null_listener_add_remove_is_noop() {
        SpaceChangedBroadcaster.addListener(null);
        SpaceChangedBroadcaster.removeListener(null);
        assertEquals(0, SpaceChangedBroadcaster.listenerCountForTest());

        // adding null should not break subsequent real notifications
        List<String> received = new ArrayList<>();
        SpaceChangedBroadcaster.addListener((oldId, newId) -> received.add(newId));
        SpaceChangedBroadcaster.notifyChanged(SPACE_A, SPACE_B);
        assertEquals(1, received.size());
    }

    @Test
    public void listener_exception_does_not_break_other_listeners() {
        List<String> received = new ArrayList<>();
        SpaceChangedBroadcaster.addListener((oldId, newId) -> {
            throw new RuntimeException("boom");
        });
        SpaceChangedBroadcaster.addListener((oldId, newId) -> received.add(newId));

        SpaceChangedBroadcaster.notifyChanged(SPACE_A, SPACE_B);

        assertEquals("exception in one listener must not suppress others", 1, received.size());
        assertEquals(SPACE_B, received.get(0));
    }

    @Test
    public void space_enter_and_leave_both_fire() {
        List<String[]> received = new ArrayList<>();
        SpaceChangedBroadcaster.addListener((oldId, newId) -> received.add(new String[]{oldId, newId}));

        // 非 Space 模式 → 进 Space A
        SpaceChangedBroadcaster.notifyChanged("", SPACE_A);
        // Space A → Space B
        SpaceChangedBroadcaster.notifyChanged(SPACE_A, SPACE_B);
        // Space B → 退出到非 Space 模式
        SpaceChangedBroadcaster.notifyChanged(SPACE_B, "");

        assertEquals(3, received.size());
        assertEquals("", received.get(0)[0]);
        assertEquals(SPACE_A, received.get(0)[1]);
        assertEquals(SPACE_A, received.get(1)[0]);
        assertEquals(SPACE_B, received.get(1)[1]);
        assertEquals(SPACE_B, received.get(2)[0]);
        assertEquals("", received.get(2)[1]);
    }

    @Test
    public void clear_listeners_empties_registry() {
        SpaceChangedBroadcaster.addListener((oldId, newId) -> {});
        SpaceChangedBroadcaster.addListener((oldId, newId) -> {});
        assertEquals(2, SpaceChangedBroadcaster.listenerCountForTest());

        SpaceChangedBroadcaster.clearListenersForTest();
        assertEquals(0, SpaceChangedBroadcaster.listenerCountForTest());

        // after clear, no listener should receive
        final boolean[] fired = {false};
        List<String> captured = new ArrayList<>();
        SpaceChangedBroadcaster.notifyChanged(SPACE_A, SPACE_B);
        assertFalse(fired[0]);
        assertTrue(captured.isEmpty());
    }
}
