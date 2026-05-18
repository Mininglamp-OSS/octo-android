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

package com.chat.base.external;

import com.chat.base.entity.WKChannelCustomerExtras;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the viewer-relative "source Space" label to show after a sender nickname
 * in external-group message bubbles and in mergeforward user lists.
 *
 * <p>Priority chain (per  spec — aligned with web PR #981/#982/#997/#1013):
 * <ol>
 *   <li>msg-level {@code home_space_id} + {@code home_space_name}
 *       ( viewer-relative — only show when sender's home Space ≠ viewer's
 *       current Space).</li>
 *   <li>msg-level {@code is_external} + {@code source_space_name}
 *       (absolute flag from backend).</li>
 *   <li>channel-level {@code is_external_group} + {@code source_space_name}
 *       in {@link WKChannel#remoteExtraMap} (compat for old data before
 *       msg-level fields were added).</li>
 * </ol>
 *
 * <p>The resolver is deliberately scope-limited: it never returns a suffix for
 * private chats, topic sub-threads, or system messages — matching the UI-spec
 * guard rails in the issue description.
 */
public final class ExternalSourceResolver {

    /** Channel-level key (ChannelInfo.remoteExtraMap) carrying the source Space display name. */
    public static final String CHANNEL_SOURCE_SPACE_NAME = "source_space_name";

    private ExternalSourceResolver() {
    }

    /**
     * Compute the "@SpaceName" suffix text for a message bubble nickname.
     *
     * @param msg                the message to render
     * @param viewerHomeSpaceId  the viewer's current Space id (may be {@code null}/empty);
     *                           when null the viewer-relative check degrades to "always
     *                           show if home_space_name is present".
     * @return source Space display name to append after the nickname, or
     *         {@code null} when no suffix should be rendered.
     */
    public static String resolveSourceSpaceName(WKMsg msg, String viewerHomeSpaceId) {
        if (msg == null) {
            return null;
        }
        // v2 UI rule: only show in group chats. Topic sub-threads still use
        // channelType == GROUP but are distinguished by topicID being non-empty.
        if (msg.channelType != WKChannelType.GROUP) {
            return null;
        }
        if (!isNullOrEmpty(msg.topicID)) {
            return null;
        }

        // Priority 1: viewer-relative msg-level home_space_id/name ()
        String homeSpaceId = getExtraString(msg.localExtraMap, ExternalMsgExtras.HOME_SPACE_ID);
        String homeSpaceName = getExtraString(msg.localExtraMap, ExternalMsgExtras.HOME_SPACE_NAME);
        if (!isNullOrEmpty(homeSpaceId)) {
            // If sender's home Space matches the viewer's, do not flag as external.
            if (!isNullOrEmpty(viewerHomeSpaceId) && homeSpaceId.equals(viewerHomeSpaceId)) {
                return null;
            }
            if (!isNullOrEmpty(homeSpaceName)) {
                return homeSpaceName;
            }
        }

        // Priority 2: absolute msg-level is_external + source_space_name
        String isExternal = getExtraString(msg.localExtraMap, ExternalMsgExtras.IS_EXTERNAL);
        String sourceSpaceName = getExtraString(msg.localExtraMap, ExternalMsgExtras.SOURCE_SPACE_NAME);
        if (isTruthy(isExternal) && !isNullOrEmpty(sourceSpaceName)) {
            return sourceSpaceName;
        }

        // Priority 3: channel-level fallback via remoteExtraMap
        // (channel carries is_external_group + source_space_name when the
        // per-message fields haven't been populated yet — compat for old data).
        // Wrapped in try/catch so unit tests that don't bootstrap the singleton
        // WKIM/ChannelManager never explode here.
        try {
            WKChannel channel = msg.getChannelInfo();
            if (channel != null && channel.remoteExtraMap != null) {
                Object externalFlag = channel.remoteExtraMap.get(WKChannelCustomerExtras.isExternalGroup);
                Object channelSourceName = channel.remoteExtraMap.get(CHANNEL_SOURCE_SPACE_NAME);
                if (isTruthy(externalFlag) && channelSourceName != null) {
                    String name = String.valueOf(channelSourceName);
                    if (!isNullOrEmpty(name)) {
                        return name;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Singleton not initialised (unit test) or other access failure —
            // priority 3 is a best-effort legacy fallback, safe to skip.
        }

        return null;
    }

    /**
     * Extract the source Space name for a merge-forward user entry. The server
     * packs per-user is_external / source_space_name into the {@code users[]}
     * array of the multi-forward payload (see {@code WKMultiForwardContent}).
     * Users whose home Space matches the viewer get no suffix.
     *
     * @param user               the channel entry decoded from the {@code users[]} array
     * @param viewerHomeSpaceId  the viewer's current Space id
     * @return source Space display name to show, or {@code null} when the user is not external
     */
    public static String resolveMergeForwardUserSpaceName(WKChannel user, String viewerHomeSpaceId) {
        if (user == null || user.remoteExtraMap == null) {
            return null;
        }
        HashMap<?, ?> extras = user.remoteExtraMap;
        String homeSpaceId = getExtraString(extras, ExternalMsgExtras.HOME_SPACE_ID);
        String homeSpaceName = getExtraString(extras, ExternalMsgExtras.HOME_SPACE_NAME);
        if (!isNullOrEmpty(homeSpaceId)) {
            if (!isNullOrEmpty(viewerHomeSpaceId) && homeSpaceId.equals(viewerHomeSpaceId)) {
                return null;
            }
            if (!isNullOrEmpty(homeSpaceName)) {
                return homeSpaceName;
            }
        }
        String isExternal = getExtraString(extras, ExternalMsgExtras.IS_EXTERNAL);
        String sourceSpaceName = getExtraString(extras, ExternalMsgExtras.SOURCE_SPACE_NAME);
        if (isTruthy(isExternal) && !isNullOrEmpty(sourceSpaceName)) {
            return sourceSpaceName;
        }
        return null;
    }

    /**
     * Reply-preview variant ( · aligned with web PR #1073 / iOS).
     *
     * <p>The reply object (carried inside a message's payload) packs the replied-to
     * sender's home/source Space directly on the JSON object, not on a {@code WKMsg}.
     * This overload accepts the four optional fields as primitives so callers don't
     * have to synthesise a {@code WKMsg} just to reuse the priority chain.
     *
     * <p>Priority chain mirrors {@link #resolveSourceSpaceName(WKMsg, String)}:
     * <ol>
     *   <li>{@code fromHomeSpaceId} + {@code fromHomeSpaceName} → viewer-relative;
     *       returns {@code null} when the replied-to user's home Space matches the
     *       viewer's current Space.</li>
     *   <li>{@code fromIsExternal != 0} + {@code fromSourceSpaceName} → absolute fallback.</li>
     * </ol>
     *
     * <p>No group/topic guard here — the reply preview is always rendered as part of a
     * message bubble that already passed its own scope check.
     *
     * @param fromHomeSpaceId     replied-to user's home Space id ({@code null} when absent)
     * @param fromHomeSpaceName   replied-to user's home Space display name ({@code null} when absent)
     * @param fromIsExternal      1 when the replied-to user is external, 0 otherwise
     * @param fromSourceSpaceName replied-to user's source Space display name ({@code null} when absent)
     * @param viewerHomeSpaceId   viewer's current Space id ({@code null}/empty degrades to
     *                            "always render when home_space_name present")
     * @return display name to append after the nickname, or {@code null} when no suffix
     */
    public static String resolveSourceSpaceName(
            String fromHomeSpaceId,
            String fromHomeSpaceName,
            int fromIsExternal,
            String fromSourceSpaceName,
            String viewerHomeSpaceId) {
        // Priority 1: viewer-relative home_space_id/name
        if (!isNullOrEmpty(fromHomeSpaceId)) {
            if (!isNullOrEmpty(viewerHomeSpaceId) && fromHomeSpaceId.equals(viewerHomeSpaceId)) {
                return null;
            }
            if (!isNullOrEmpty(fromHomeSpaceName)) {
                return fromHomeSpaceName;
            }
        }
        // Priority 2: absolute is_external + source_space_name
        if (fromIsExternal != 0 && !isNullOrEmpty(fromSourceSpaceName)) {
            return fromSourceSpaceName;
        }
        return null;
    }

    private static String getExtraString(Map<?, ?> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) {
            return false;
        }
        // JSON numeric 1, boolean "true", string "1"/"true" are all acceptable.
        if ("1".equals(s) || "true".equalsIgnoreCase(s)) {
            return true;
        }
        return false;
    }

    /**
     * Local replacement for {@code android.text.TextUtils.isEmpty} so the
     * resolver can run under plain JVM unit tests (without Robolectric).
     */
    private static boolean isNullOrEmpty(CharSequence s) {
        return s == null || s.length() == 0;
    }
}
