package com.xinbida.wukongim.db;

/**
 * YUJ-326 · 从 Space channel_id 前缀反解 space_id 的纯 Java 工具。
 *
 * <p>Server 规则（dmworkim {@code pkg/space/channel.go:35 BuildChannelID}）：
 * <pre>
 *   channel_id = "s" + 32-char lowercase-hex spaceID + "_" + peerID
 *   length = 1 + 32 + 1 + len(peer) = &gt;= 34
 * </pre>
 *
 * <p>非 Space 频道（直接 peer_id / 早期格式）不带 {@code s} 前缀，返回 {@code ""}。
 * 异常输入（null / 过短 / 缺分隔符 / 非 hex）一律静默返回 {@code ""}，不抛异常 ——
 * backfill 路径必须对脏数据免疫，以免一行坏数据阻塞整张表升级。
 *
 * <p>与 migration SQL {@code substr(channel_id, 2, 32)} 的行为差异：SQL 版本不做 hex
 * 校验（SQLite 没有 regex extension），极端情况下非 hex 字节会被写入 space_id 列。
 * 这个 Java 版本作为"严格参照实现"供单测锁定契约，也供 runtime 可选用它逐行校验
 * （{@link WKDBSpaceIdBackfill#BACKFILL_MODE} 选择 SQL 快速批 vs Java 严格批）。
 */
public final class SpaceChannelIdParser {

    /** Space channel_id 固定长度 1 ('s') + 32 (hex) + 1 ('_') = 34 前缀。 */
    public static final int PREFIX_LENGTH = 34;
    /** space_id 段长度（32 hex chars）。 */
    public static final int SPACE_ID_LENGTH = 32;

    private SpaceChannelIdParser() {}

    /**
     * 从 channel_id 解出 space_id。
     *
     * @param channelId 输入 channel_id
     * @return 32 位 hex space_id，或 {@code ""}（非 Space 频道 / 解析失败）
     */
    public static String extractSpaceId(String channelId) {
        if (channelId == null) return "";
        if (channelId.length() < PREFIX_LENGTH) return "";
        if (channelId.charAt(0) != 's') return "";
        if (channelId.charAt(1 + SPACE_ID_LENGTH) != '_') return "";
        String candidate = channelId.substring(1, 1 + SPACE_ID_LENGTH);
        if (!isAllHex(candidate)) return "";
        return candidate;
    }

    /** 是否全部为 hex 字符（0-9 / a-f / A-F，SQL substr 提取的串已是 UTF-8 单字节）。 */
    public static boolean isAllHex(String s) {
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!ok) return false;
        }
        return true;
    }
}
