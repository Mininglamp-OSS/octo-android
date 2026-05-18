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

package com.chat.uikit.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.chat.base.external.ExternalMsgExtras;
import com.xinbida.wukongim.entity.WKMsg;

import org.junit.Test;

/**
 * Locks the SyncMsg → WKMsg passthrough for external-source fields.
 *
 * <p> was a "data in the wire, not in the view-tree" regression caused
 * by the model layer silently dropping new fields. We mirror that concern
 * here: if any {@link ExternalMsgExtras} key ever stops flowing from
 * {@link SyncMsg} into {@link WKMsg#localExtraMap}, this test fires.
 *
 * <p>Wire format aligned with web PR #981 — no {@code from_} prefix.
 */
public class MsgModelExternalPassthroughTest {

    private SyncMsg baseSyncMsg() {
        SyncMsg s = new SyncMsg();
        s.header = new SyncMsgHeader();
        s.message_id = "mid";
        s.from_uid = "sender";
        s.channel_id = "ch";
        return s;
    }

    @Test
    public void allExternalFieldsFlowIntoLocalExtraMap() {
        SyncMsg syncMsg = baseSyncMsg();
        syncMsg.is_external = 1;
        syncMsg.source_space_id = "vendor_space";
        syncMsg.source_space_name = "Vendor Space";
        syncMsg.home_space_id = "home_B";
        syncMsg.home_space_name = "Home B";

        WKMsg msg = new WKMsg();
        MsgModel.copyExternalSourceExtras(msg, syncMsg);

        assertNotNull(msg.localExtraMap);
        assertEquals(1, msg.localExtraMap.get(ExternalMsgExtras.IS_EXTERNAL));
        assertEquals("vendor_space", msg.localExtraMap.get(ExternalMsgExtras.SOURCE_SPACE_ID));
        assertEquals("Vendor Space", msg.localExtraMap.get(ExternalMsgExtras.SOURCE_SPACE_NAME));
        assertEquals("home_B", msg.localExtraMap.get(ExternalMsgExtras.HOME_SPACE_ID));
        assertEquals("Home B", msg.localExtraMap.get(ExternalMsgExtras.HOME_SPACE_NAME));
    }

    @Test
    public void noExternalFieldsLeavesMapUntouched() {
        SyncMsg syncMsg = baseSyncMsg();
        WKMsg msg = new WKMsg();
        MsgModel.copyExternalSourceExtras(msg, syncMsg);
        // No writes occurred — either still null or no external keys present.
        if (msg.localExtraMap != null) {
            assertNull(msg.localExtraMap.get(ExternalMsgExtras.IS_EXTERNAL));
            assertNull(msg.localExtraMap.get(ExternalMsgExtras.HOME_SPACE_ID));
        }
    }

    @Test
    public void partialFieldsOnlyCopyPresentKeys() {
        SyncMsg syncMsg = baseSyncMsg();
        syncMsg.home_space_id = "home_B";
        syncMsg.home_space_name = "Home B";
        // is_external / source_space_* intentionally absent
        WKMsg msg = new WKMsg();
        MsgModel.copyExternalSourceExtras(msg, syncMsg);
        assertNotNull(msg.localExtraMap);
        assertEquals("home_B", msg.localExtraMap.get(ExternalMsgExtras.HOME_SPACE_ID));
        assertEquals("Home B", msg.localExtraMap.get(ExternalMsgExtras.HOME_SPACE_NAME));
        assertNull(msg.localExtraMap.get(ExternalMsgExtras.IS_EXTERNAL));
        assertNull(msg.localExtraMap.get(ExternalMsgExtras.SOURCE_SPACE_ID));
    }

    @Test
    public void nullSafety() {
        MsgModel.copyExternalSourceExtras(null, baseSyncMsg());
        MsgModel.copyExternalSourceExtras(new WKMsg(), null);
    }
}
