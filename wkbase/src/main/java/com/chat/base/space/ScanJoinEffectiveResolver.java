package com.chat.base.space;

/**
 * Pure-Java helper for resolving the "effective" scan-join parameters used by
 * {@code ScanJoinGroupActivity} when deciding whether to surface the cross-Space
 * Toast (YUJ-200 Path B / YUJ-212).
 *
 * <p>Round-2 (Jerry-Xin review): distinguish {@code null} (field missing from
 * scanjoin response → fall back to pre-scan QR payload) from {@code ""}
 * (backend explicitly signals "public group", must be preserved as-is).
 * Previously {@code TextUtils.isEmpty} collapsed both into the same branch,
 * which caused a mis-fired cross-Space Toast when a group was moved to
 * "public" but the QR was not regenerated.
 *
 * <p>Kept under {@code wkbase/space} next to {@link JoinSuccessHelper} /
 * {@link SpaceFilter} so it can be unit-tested on the JVM without any
 * Android dependency (no {@code TextUtils}).
 */
public final class ScanJoinEffectiveResolver {

    private ScanJoinEffectiveResolver() {}

    /**
     * Resolve an effective field value from the scanjoin response vs. the
     * pre-scan QR payload.
     *
     * @param response backend field (may be {@code null} when missing,
     *                 {@code ""} when backend explicitly signals "public group")
     * @param fallback pre-scan QR payload value (Intent extra on Activity)
     * @return the effective value:
     *         <ul>
     *           <li>{@code response} when non-{@code null} (including {@code ""})</li>
     *           <li>{@code fallback} when {@code response} is {@code null}</li>
     *         </ul>
     */
    public static String resolve(String response, String fallback) {
        return response != null ? response : fallback;
    }

    /**
     * True iff the effective target Space id and the viewer's current Space id
     * are both non-empty AND differ — i.e. this join is cross-Space and should
     * persist a cross-Space notice.
     *
     * <p>Empty {@code effectiveSpaceId} → the group is public (or response
     * carried no Space context) → not cross-Space. Empty {@code viewerSpaceId}
     * → viewer is not in any Space → not cross-Space.
     */
    public static boolean isCrossSpace(String effectiveSpaceId, String viewerSpaceId) {
        return !isNullOrEmpty(effectiveSpaceId)
                && !isNullOrEmpty(viewerSpaceId)
                && !effectiveSpaceId.equals(viewerSpaceId);
    }

    private static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
