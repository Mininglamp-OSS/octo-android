package com.xinbida.wukongim.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

/**
 * 慢查询日志节流 key 的归一化。
 *
 * <p>这组用例的存在理由很具体：上一版把 key 写成 {@code abbreviate(sql).replaceAll("\\d+","?")}，
 * 看着在按语句形状归一，实际是空操作 —— 会变的部分（分页偏移）在 300 字符截断之外，而
 * {@code IN (...)} 用的是绑定占位符、压根没有数字。第一条用例就会让那种写法挂掉。
 */
public class WKDBHelperSlowLogShapeTest {

    /** 与 ChannelMembersDbManager.queryWithPage 同形（全长 463，limit 在 451，远超截断阈值 300）。 */
    private static String pagingSql(int offset, int size) {
        String cm = "channel_members";
        String ch = "channel";
        String cols = ch + ".channel_remark," + ch + ".channel_name," + ch + ".avatar,"
                + ch + ".avatar_cache_key";
        return "select " + cm + ".*," + cols + " from " + cm + " LEFT JOIN " + ch + " on " + cm
                + ".member_uid=" + ch + ".channel_id and " + ch + ".channel_type=1 where " + cm
                + ".channel_id=? and " + cm + ".channel_type=? and " + cm + ".is_deleted=0 and "
                + cm + ".status=1 order by " + cm + ".role=1 desc," + cm + ".role=2 desc,"
                + cm + ".created_at asc limit " + offset + "," + size;
    }

    /** 翻页只改 limit 偏移，必须落在同一个节流槽位。 */
    @Test
    public void pagingOffsetsShareOneShape() {
        assertEquals(WKDBHelper.slowLogShape(pagingSql(0, 20)),
                WKDBHelper.slowLogShape(pagingSql(180, 20)));
    }

    /** 归一必须发生在截断之前，否则尾部的 limit 根本没被处理过。 */
    @Test
    public void pagingSqlIsLongerThanTheLogCap() {
        String sql = pagingSql(20, 20);
        // 实测 463 字符、limit 在 451 —— 这里不钉死字面值（加个列就会误报），
        // 钉住的是「会变的部分落在 300 字符截断之外」这个性质本身。
        org.junit.Assert.assertTrue("SQL 应长于日志截断阈值", sql.length() > 300);
        org.junit.Assert.assertTrue("limit 应落在截断之外", sql.lastIndexOf(" limit ") > 300);
    }

    /** IN 列表按元素个数裂开才是 key 空间的主要来源：不同 arity 必须收敛成同一形状。 */
    @Test
    public void inListAritiesShareOneShape() {
        String two = "select from message where message_id in (?, ?)";
        String seven = "select from message where message_id in (?, ?, ?, ?, ?, ?, ?)";
        assertEquals(WKDBHelper.slowLogShape(two), WKDBHelper.slowLogShape(seven));
    }

    /** 单个占位符不该被改写成别的东西。 */
    @Test
    public void singlePlaceholderIsUntouched() {
        String sql = "select from channel where channel_id=? and channel_type=?";
        assertEquals(sql, WKDBHelper.slowLogShape(sql));
    }

    /** 不同语句仍然要落在不同槽位，否则节流会把别的语句一起吞掉。 */
    @Test
    public void differentStatementsKeepDifferentShapes() {
        assertNotEquals(WKDBHelper.slowLogShape("select from message where channel_id=?"),
                WKDBHelper.slowLogShape("select from conversation where channel_id=?"));
    }

    @Test
    public void nullIsTolerated() {
        assertEquals("null", WKDBHelper.slowLogShape(null));
    }
}
