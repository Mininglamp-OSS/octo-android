package com.chat.base.external;

/**
 * Msg-level external-source extras keys (YUJ-89 bubble + mergeforward).
 *
 * <p>These keys are copied into {@link com.xinbida.wukongim.entity.WKMsg#localExtraMap}
 * when the backend sync response carries the field (see {@link com.chat.uikit.message.SyncMsg}
 * and {@code MsgModel.getWKSyncMsg}). Downstream UI reads them via
 * {@link ExternalSourceResolver} to render "@SpaceName" after the sender nickname.
 *
 * <p>Wire format aligned with web PR #981 / #982 / #997 / #1013 — <strong>no {@code from_}
 * prefix</strong>. Matches {@link com.xinbida.wukongim.entity.WKChannelMemberExtras}
 * so local and remote extras share the same key space.
 *
 * <p>Web PR #982 added the equivalent passthrough on web and the silent-failure
 * YUJ-53 bug was caused by missing this transport. Unit tests for field passthrough
 * live under {@code wkuikit/src/test/java/com/chat/uikit/external/}.
 */
public final class ExternalMsgExtras {

    private ExternalMsgExtras() {
    }

    /** 1 when the sender's home Space differs from channel's effective Space. */
    public static final String IS_EXTERNAL = "is_external";

    /** Sender's source Space id (absolute view — sender's home Space). */
    public static final String SOURCE_SPACE_ID = "source_space_id";

    /** Sender's source Space display name (absolute view). */
    public static final String SOURCE_SPACE_NAME = "source_space_name";

    /**
     * Sender's home Space id (YUJ-63 viewer-relative upgrade).
     *
     * <p>Having this lets the client render the suffix relative to the current viewer's
     * Space, instead of the server's fixed "channel effective Space" perspective.
     */
    public static final String HOME_SPACE_ID = "home_space_id";

    /** Sender's home Space display name (YUJ-63 viewer-relative upgrade). */
    public static final String HOME_SPACE_NAME = "home_space_name";
}
