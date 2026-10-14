package com.chat.uikit.chat.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.chat.uikit.chat.msgmodel.WKMultiForwardContent;
import com.chat.uikit.enity.ChatMultiForwardEntity;
import com.xinbida.wukongim.entity.WKMsg;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 合并转发文件缓存键的 bundle 作用域回归测试。
 *
 * 覆盖两点：
 * 1) 同一 payload 两次独立 decode 得到同一作用域摘要（P1-1：原实现用
 *    list.hashCode() —— ChatMultiForwardEntity 未重写 hashCode，是身份哈希，
 *    每次进详情页都变，缓存永不命中且 forward/ 无界增长）。
 * 2) payload 不同必然得到不同摘要。
 *
 * 注：不直接构造 ChatMultiForwardDetailAdapter —— BaseMultiItemQuickAdapter 的
 * 构造依赖 Android framework，在 returnDefaultValues 的 JVM 单测里不可用。这里
 * 测的是 adapter 内 payloadDigest() 的等价算法，两边必须同步修改。
 */
public class ChatMultiForwardDetailKeyTest {

    private static final String PAYLOAD_A =
            "{\"channel_type\":2,\"msgs\":[" +
            "{\"message_id\":\"m1\",\"timestamp\":1700000000,\"from_uid\":\"u1\"," +
            "\"payload\":{\"type\":6,\"name\":\"report.pdf\",\"url\":\"/a.pdf\",\"size\":10}}," +
            "{\"message_id\":\"m2\",\"timestamp\":1700000001,\"from_uid\":\"u2\"," +
            "\"payload\":{\"type\":1,\"content\":\"hi\"}}]}";

    private static final String PAYLOAD_B =
            "{\"channel_type\":2,\"msgs\":[" +
            "{\"message_id\":\"m9\",\"timestamp\":1700000009,\"from_uid\":\"u9\"," +
            "\"payload\":{\"type\":6,\"name\":\"other.pdf\",\"url\":\"/b.pdf\",\"size\":20}}]}";

    @Test
    public void samePayloadDecodedTwiceYieldsSameScope() throws Exception {
        assertEquals(digest(buildList(PAYLOAD_A)), digest(buildList(PAYLOAD_A)));
    }

    @Test
    public void differentPayloadYieldsDifferentScope() throws Exception {
        assertNotEquals(digest(buildList(PAYLOAD_A)), digest(buildList(PAYLOAD_B)));
    }

    /** 等价于 ChatMultiForwardDetailAdapter.payloadDigest()，两边必须同步。 */
    private static String digest(List<ChatMultiForwardEntity> list) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (ChatMultiForwardEntity item : list) {
            WKMsg msg = item.msg;
            if (msg == null) continue;
            sb.append(msg.messageID == null ? "" : msg.messageID).append('')
              .append(msg.timestamp).append('')
              .append(msg.fromUID == null ? "" : msg.fromUID).append('')
              .append(msg.content == null ? "" : msg.content).append('');
        }
        byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(sb.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 8; i++) hex.append(String.format("%02x", bytes[i]));
        return hex.toString();
    }

    private static List<ChatMultiForwardEntity> buildList(String json) throws Exception {
        WKMultiForwardContent content = new WKMultiForwardContent();
        content.decodeMsg(new JSONObject(json));
        List<ChatMultiForwardEntity> list = new ArrayList<>();
        ChatMultiForwardEntity header = new ChatMultiForwardEntity();
        header.itemType = 1;
        header.title = "t";
        list.add(header);
        for (int i = 0; i < content.msgList.size(); i++) {
            ChatMultiForwardEntity e = new ChatMultiForwardEntity();
            e.msg = content.msgList.get(i);
            list.add(e);
        }
        ChatMultiForwardEntity tail = new ChatMultiForwardEntity();
        tail.itemType = 2;
        list.add(tail);
        return list;
    }
}
