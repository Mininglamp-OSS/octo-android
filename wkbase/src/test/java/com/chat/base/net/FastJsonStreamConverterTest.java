package com.chat.base.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.alibaba.fastjson.JSON;

import okhttp3.MediaType;
import okhttp3.ResponseBody;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * OOM 改动的回归网：{@link FastJsonResponseBodyConverter} 超过阈值会改走 JSONReader 流式解析，
 * 会话同步那个 350 万字符的响应必然走这条路。这里用同一份 JSON 分别跑「流式路径」和
 * 「改动前的 parseObject(String)」，逐字段比对——少一个元素、少一个字段都要在这里挂掉。
 */
public class FastJsonStreamConverterTest {

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    public static class Recent {
        public String message_id;
        public String content;
        public long message_seq;
    }

    public static class Conversation {
        public String channel_id;
        public byte channel_type;
        public long timestamp;
        public int unread;
        public List<Recent> recents;
    }

    public static class SyncChat {
        public List<Conversation> conversations;
        public String cursor;
    }

    /** 造一个字符数 >= targetChars 的响应，结构与 conv sync 同形。 */
    private static String buildPayload(int convCount, int recentsPerConv, int contentChars) {
        SyncChat chat = new SyncChat();
        chat.cursor = "cursor-end";
        chat.conversations = new ArrayList<>();
        StringBuilder filler = new StringBuilder(contentChars);
        for (int i = 0; i < contentChars; i++) {
            // 混入需要转义的字符和多字节字符，覆盖 lexer 的边界处理
            filler.append(i % 37 == 0 ? '"' : (i % 53 == 0 ? '\\' : (i % 11 == 0 ? '中' : 'a')));
        }
        String content = filler.toString();
        for (int c = 0; c < convCount; c++) {
            Conversation conv = new Conversation();
            conv.channel_id = "channel_" + c;
            conv.channel_type = (byte) (c % 3);
            conv.timestamp = 1_700_000_000L + c;
            conv.unread = c;
            conv.recents = new ArrayList<>();
            for (int r = 0; r < recentsPerConv; r++) {
                Recent recent = new Recent();
                recent.message_id = "msg_" + c + "_" + r;
                recent.message_seq = r;
                recent.content = content;
                conv.recents.add(recent);
            }
            chat.conversations.add(conv);
        }
        return JSON.toJSONString(chat);
    }

    private static SyncChat convert(String payload) throws Exception {
        FastJsonResponseBodyConverter<SyncChat> converter =
                new FastJsonResponseBodyConverter<>(SyncChat.class);
        return converter.convert(ResponseBody.create(payload, JSON_TYPE));
    }

    private static void assertSameAsLegacy(String payload) throws Exception {
        SyncChat expected = JSON.parseObject(payload, SyncChat.class);
        SyncChat actual = convert(payload);

        assertNotNull("converter 返回 null", actual);
        assertEquals("cursor 丢失", expected.cursor, actual.cursor);
        assertEquals("会话数量对不上", expected.conversations.size(), actual.conversations.size());
        for (int i = 0; i < expected.conversations.size(); i++) {
            Conversation e = expected.conversations.get(i);
            Conversation a = actual.conversations.get(i);
            assertEquals("channel_id[" + i + "]", e.channel_id, a.channel_id);
            assertEquals("channel_type[" + i + "]", e.channel_type, a.channel_type);
            assertEquals("timestamp[" + i + "]", e.timestamp, a.timestamp);
            assertEquals("unread[" + i + "]", e.unread, a.unread);
            assertEquals("recents 条数[" + i + "]", e.recents.size(), a.recents.size());
            for (int r = 0; r < e.recents.size(); r++) {
                assertEquals("message_id[" + i + "][" + r + "]",
                        e.recents.get(r).message_id, a.recents.get(r).message_id);
                assertEquals("content[" + i + "][" + r + "]",
                        e.recents.get(r).content, a.recents.get(r).content);
                assertEquals("message_seq[" + i + "][" + r + "]",
                        e.recents.get(r).message_seq, a.recents.get(r).message_seq);
            }
        }
    }

    /** 小于阈值：走原路径，必须与改动前逐字段一致。 */
    @Test
    public void smallPayloadMatchesLegacy() throws Exception {
        String payload = buildPayload(3, 2, 100);
        assertSameAsLegacy(payload);
    }

    /** 刚好越过 256K 字符阈值：流式路径的入口边界。 */
    @Test
    public void justOverThresholdMatchesLegacy() throws Exception {
        String payload = buildPayload(40, 4, 2_000);
        assertSameAsLegacy(payload);
    }

    /** 会话同步量级（数百会话 × 多条 recent，百万级字符）：真实场景。 */
    @Test
    public void syncChatSizedPayloadMatchesLegacy() throws Exception {
        String payload = buildPayload(300, 5, 2_000);
        assertSameAsLegacy(payload);
    }

    /** 单个超大字段跨多次 buffer 填充：lexer 跨块拼接的经典出错点。 */
    @Test
    public void hugeSingleFieldMatchesLegacy() throws Exception {
        String payload = buildPayload(2, 1, 900_000);
        assertSameAsLegacy(payload);
    }

    /** 恰好落在阈值附近的多个长度，逐个扫一遍边界。 */
    @Test
    public void lengthsAroundThresholdMatchLegacy() throws Exception {
        for (int extra = -3; extra <= 3; extra++) {
            String base = buildPayload(1, 1, 256 * 1024);
            int target = 256 * 1024 + extra;
            StringBuilder sb = new StringBuilder(base);
            while (sb.length() < target) sb.insert(sb.length() - 1, ' ');
            String payload = sb.toString();
            SyncChat actual = convert(payload);
            assertNotNull("len=" + payload.length() + " 返回 null", actual);
            assertEquals("len=" + payload.length() + " 会话数对不上",
                    1, actual.conversations.size());
            assertEquals("len=" + payload.length() + " content 长度对不上",
                    JSON.parseObject(payload, SyncChat.class)
                            .conversations.get(0).recents.get(0).content.length(),
                    actual.conversations.get(0).recents.get(0).content.length());
        }
    }

    /**
     * 空 body：{@code headLen == 0 -> null} 是这个转换器里唯一一处由我们手写、而非 fastjson
     * 决定的分支，所以必须有测试钉住。改动前 {@code JSON.parseObject("")} 也是 null。
     */
    @Test
    public void emptyBodyReturnsNull() throws Exception {
        assertNull("空 body 应返回 null", convert(""));
        assertNull("改动前的路径同样返回 null", JSON.parseObject("", SyncChat.class));
    }

    /**
     * 截断 JSON。按大小分流最容易出的洞是两条路径抛不同类型的异常 —— 上层
     * {@code ResponseExceptionHandle} 就会因为响应多大而走不同分支。这里钉住「同一类异常」，
     * 小包（原路径）和越过阈值的大包（流式路径）各测一次。
     */
    @Test
    public void malformedPayloadThrowsSameTypeAsLegacy() {
        assertSameThrowAsLegacy(truncate(buildPayload(3, 2, 100)));
        assertSameThrowAsLegacy(truncate(buildPayload(300, 5, 2_000)));
    }

    /** 砍掉尾部让结构不闭合。 */
    private static String truncate(String payload) {
        return payload.substring(0, payload.length() - 20);
    }

    private static void assertSameThrowAsLegacy(String payload) {
        Class<?> legacy = null;
        Class<?> actual = null;
        try {
            JSON.parseObject(payload, SyncChat.class);
        } catch (Throwable t) {
            legacy = t.getClass();
        }
        try {
            convert(payload);
        } catch (Throwable t) {
            actual = t.getClass();
        }
        assertNotNull("改动前的路径没有抛异常，这个用例失去意义（截断得不够狠）", legacy);
        assertEquals("两条路径抛的异常类型不一致，上层会因响应大小走不同分支",
                legacy, actual);
    }
}
