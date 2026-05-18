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

package com.chat.uikit.chat.msgmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.chat.base.external.ExternalMsgExtras;
import com.xinbida.wukongim.entity.WKMsg;

import org.json.JSONObject;
import org.junit.Test;

/**
 * Locks the per-msg field-passthrough contract from merge-forward payload JSON
 * into {@code WKMsg.localExtraMap}.
 *
 * <p> on web was caused by the model layer dropping these fields on the
 * floor, causing the DOM to render with no external suffix even though the
 * server was sending the data. This test suite is the Android-side equivalent
 * guard — if any field is ever lost during decode, one of these tests fires.
 *
 * <p>Wire format aligned with web PR #981 — no {@code from_} prefix. Per-user
 * (users[]) passthrough is covered by {@link WKMultiForwardContentExternalFieldsTest}
 * ( EP1) which exercises the inline decode/encode paths.
 */
public class WKMultiForwardContentPassthroughTest {

    @Test
    public void msgLevelFieldsAreCopiedIntoLocalExtraMap() throws Exception {
        WKMsg msg = new WKMsg();
        JSONObject json = new JSONObject();
        json.put(ExternalMsgExtras.IS_EXTERNAL, 1);
        json.put(ExternalMsgExtras.SOURCE_SPACE_ID, "space_B_id");
        json.put(ExternalMsgExtras.SOURCE_SPACE_NAME, "Space B");
        json.put(ExternalMsgExtras.HOME_SPACE_ID, "home_B_id");
        json.put(ExternalMsgExtras.HOME_SPACE_NAME, "Home B");

        WKMultiForwardContent.copyExternalFieldsToLocalExtra(msg, json);

        assertNotNull(msg.localExtraMap);
        assertEquals(1, msg.localExtraMap.get(ExternalMsgExtras.IS_EXTERNAL));
        assertEquals("space_B_id", msg.localExtraMap.get(ExternalMsgExtras.SOURCE_SPACE_ID));
        assertEquals("Space B", msg.localExtraMap.get(ExternalMsgExtras.SOURCE_SPACE_NAME));
        assertEquals("home_B_id", msg.localExtraMap.get(ExternalMsgExtras.HOME_SPACE_ID));
        assertEquals("Home B", msg.localExtraMap.get(ExternalMsgExtras.HOME_SPACE_NAME));
    }

    @Test
    public void msgLevelEmptyJsonLeavesLocalExtraUntouched() {
        WKMsg msg = new WKMsg();
        WKMultiForwardContent.copyExternalFieldsToLocalExtra(msg, new JSONObject());
        assertTrue(msg.localExtraMap == null || msg.localExtraMap.isEmpty());
    }

    @Test
    public void msgLevelPreservesExistingLocalExtra() throws Exception {
        WKMsg msg = new WKMsg();
        msg.localExtraMap = new java.util.HashMap();
        msg.localExtraMap.put("unrelated_key", "keep_me");
        JSONObject json = new JSONObject();
        json.put(ExternalMsgExtras.HOME_SPACE_NAME, "Home B");

        WKMultiForwardContent.copyExternalFieldsToLocalExtra(msg, json);

        assertEquals("keep_me", msg.localExtraMap.get("unrelated_key"));
        assertEquals("Home B", msg.localExtraMap.get(ExternalMsgExtras.HOME_SPACE_NAME));
    }

    @Test
    public void nullSafety() {
        // Should not throw
        WKMultiForwardContent.copyExternalFieldsToLocalExtra(null, new JSONObject());
        WKMultiForwardContent.copyExternalFieldsToLocalExtra(new WKMsg(), null);
    }

    @Test
    public void nullFieldInJsonIsSkipped() throws Exception {
        WKMsg msg = new WKMsg();
        JSONObject json = new JSONObject();
        json.put(ExternalMsgExtras.SOURCE_SPACE_NAME, JSONObject.NULL);
        WKMultiForwardContent.copyExternalFieldsToLocalExtra(msg, json);
        assertFalse(msg.localExtraMap != null
                && msg.localExtraMap.containsKey(ExternalMsgExtras.SOURCE_SPACE_NAME));
    }

    @Test
    public void decodeInheritsChannelTypeOntoEachForwardedMsg() throws Exception {
        // Regression for /codex review finding on : forwarded msgs used
        // to keep channelType == 0, which made ExternalSourceResolver's
        // "group only" guard drop all msg-level external-source extras.
        JSONObject payload = new JSONObject();
        payload.put("channel_type", com.xinbida.wukongim.entity.WKChannelType.GROUP);
        org.json.JSONArray msgs = new org.json.JSONArray();
        JSONObject m = new JSONObject();
        m.put("message_id", "mid1");
        m.put("timestamp", 1700_000_000L);
        m.put("from_uid", "sender");
        m.put(ExternalMsgExtras.HOME_SPACE_ID, "home_B");
        m.put(ExternalMsgExtras.HOME_SPACE_NAME, "Home B");
        msgs.put(m);
        payload.put("msgs", msgs);

        WKMultiForwardContent c = new WKMultiForwardContent();
        c.decodeMsg(payload);

        assertEquals(1, c.msgList.size());
        WKMsg decoded = c.msgList.get(0);
        assertEquals(com.xinbida.wukongim.entity.WKChannelType.GROUP, decoded.channelType);
        // And the extras survive the hop, so downstream resolver sees them.
        assertEquals("Home B",
                decoded.localExtraMap.get(ExternalMsgExtras.HOME_SPACE_NAME));
    }
}
