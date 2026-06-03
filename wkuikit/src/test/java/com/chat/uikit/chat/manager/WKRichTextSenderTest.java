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

package com.chat.uikit.chat.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.appcompat.app.AppCompatActivity;

import com.chat.base.msg.ChatAdapter;
import com.chat.base.msg.IConversationContext;
import com.chat.uikit.chat.msgmodel.WKRichTextContent;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.msgmodel.WKMessageContent;
import com.xinbida.wukongim.msgmodel.WKTextContent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Locks the RichText (ContentType=14) <em>send-side</em> "text-must-arrive"
 * contract (YUJ-2832 P1, web #227 同类丢消息缺陷的 Android 对称面).
 *
 * <p>The defect: ChatActivity used to clear the input box <em>immediately</em>
 * after kicking off async image uploads, while {@link WKRichTextSender} only
 * calls {@code sendMessage} once every upload callback has returned. If the
 * process is killed / the Activity is destroyed / an upload never reports back,
 * the user's text was neither in the input box nor enqueued as a local outgoing
 * message — a regression versus the text/media paths (which enqueue at once).
 *
 * <p>Fix under test: the input box is cleared <em>only</em> via the
 * {@code onEnqueued} callback, which fires strictly after a message is actually
 * handed to {@code sendMessage}. These tests assert that callback never fires
 * while an upload is still pending, and that it does fire (exactly once) on both
 * the mixed-message and the all-images-failed text-fallback paths.
 *
 * <p>Runs under plain JVM (no Robolectric). Image upload is replaced with an
 * injectable {@link WKRichTextSender.ImageUploader} test double so the
 * "upload not finished" state is directly controllable.
 */
public class WKRichTextSenderTest {

    private CapturingContext context;

    @Before
    public void setUp() {
        context = new CapturingContext();
    }

    @After
    public void tearDown() {
        WKRichTextSender.resetUploader();
    }

    /**
     * 核心 P1 回归：上传未完成（uploader 永不回调）时，绝不能 sendMessage、
     * 绝不能触发 onEnqueued —— 调用方据此不清输入框，文本留存可恢复。
     */
    @Test
    public void uploadNeverCompletes_doesNotEnqueueAndDoesNotClearInput() {
        // 永不回调的 uploader：模拟上传 in-flight / 进程被杀前回调未到。
        WKRichTextSender.setUploaderForTest((channel, localPath, result) -> {
            // 故意不调用 result.onSuccess / onFailure。
        });

        boolean[] cleared = {false};
        WKRichTextContent content = new WKRichTextContent();
        WKRichTextSender.send(context, content, "上线方案", Arrays.asList("/sd/a.png"),
                () -> cleared[0] = true);

        // 没有任何消息入队。
        assertTrue(context.sent.isEmpty());
        // onEnqueued 未触发 → 输入框不会被清空 → 文本必达（留在输入框可恢复）。
        assertFalse(cleared[0]);
    }

    /**
     * 上传成功：聚合成单条 RichText 入队，onEnqueued 恰好触发一次（此时清输入框安全）。
     */
    @Test
    public void uploadSucceeds_enqueuesRichTextThenClearsInput() {
        WKRichTextSender.setUploaderForTest((channel, localPath, result) ->
                result.onSuccess("https://cdn/" + localPath.hashCode() + ".png"));

        int[] clearedCount = {0};
        WKRichTextContent content = new WKRichTextContent();
        WKRichTextSender.send(context, content, "看图", Arrays.asList("/sd/a.png", "/sd/b.png"),
                () -> clearedCount[0]++);

        assertEquals(1, context.sent.size());
        WKMessageContent sent = context.sent.get(0);
        assertTrue(sent instanceof WKRichTextContent);
        WKRichTextContent rich = (WKRichTextContent) sent;
        // text 块在前、两张 image 块按序在后。
        assertEquals(3, rich.blocks.size());
        assertTrue(rich.blocks.get(0).isText());
        assertEquals("看图", rich.blocks.get(0).text);
        assertTrue(rich.blocks.get(1).isImage());
        assertTrue(rich.blocks.get(2).isImage());
        // onEnqueued 恰一次。
        assertEquals(1, clearedCount[0]);
    }

    /**
     * 所有图片失败：降级纯文本入队（text 不丢），onEnqueued 仍触发一次（清输入框安全）。
     * mention 三态迁移到 WKTextContent，群 @ 通知不丢。
     */
    @Test
    public void allUploadsFail_fallsBackToTextThenClearsInput() {
        WKRichTextSender.setUploaderForTest((channel, localPath, result) -> result.onFailure());

        int[] clearedCount = {0};
        WKRichTextContent content = new WKRichTextContent();
        content.mentionAll = 1;
        content.mentionAis = 1;
        WKRichTextSender.send(context, content, "全员看通知", Arrays.asList("/sd/a.png"),
                () -> clearedCount[0]++);

        assertEquals(1, context.sent.size());
        WKMessageContent sent = context.sent.get(0);
        assertTrue(sent instanceof WKTextContent);
        assertEquals("全员看通知", ((WKTextContent) sent).content);
        // mention 三态迁移不丢。
        assertEquals(1, sent.mentionAll);
        assertEquals(1, sent.mentionAis);
        assertEquals(1, clearedCount[0]);
    }

    /**
     * 部分图片不安全（伪 scheme）被跳过：仍发混排单条（含安全图片），文本不丢。
     */
    @Test
    public void unsafeUrlSkipped_stillEnqueuesWithSafeImagesOnly() {
        List<String> paths = Arrays.asList("/sd/ok.png", "/sd/bad.png");
        WKRichTextSender.setUploaderForTest((channel, localPath, result) -> {
            if (localPath.contains("bad")) {
                // server 返回伪 scheme（攻击面）→ isSafeImageUrl 拒绝 → 跳过。
                result.onSuccess("mailto:evil@x");
            } else {
                result.onSuccess("https://cdn/ok.png");
            }
        });

        int[] clearedCount = {0};
        WKRichTextContent content = new WKRichTextContent();
        WKRichTextSender.send(context, content, "两张图", paths, () -> clearedCount[0]++);

        assertEquals(1, context.sent.size());
        WKRichTextContent rich = (WKRichTextContent) context.sent.get(0);
        // text + 1 张安全 image（bad 被跳过）。
        assertEquals(2, rich.blocks.size());
        assertTrue(rich.blocks.get(0).isText());
        assertTrue(rich.blocks.get(1).isImage());
        assertEquals("https://cdn/ok.png", rich.blocks.get(1).url);
        assertEquals(1, clearedCount[0]);
    }

    /**
     * 第一张上传完成但第二张悬挂（未回调）：链路未走到 finish，不入队、不清输入框。
     * 证明只要还有一张图片回调未到，文本就绝不提前丢失。
     */
    @Test
    public void oneUploadPending_blocksEnqueueUntilAllComplete() {
        WKRichTextSender.setUploaderForTest((channel, localPath, result) -> {
            if (localPath.contains("a.png")) {
                result.onSuccess("https://cdn/a.png");
            }
            // b.png 永不回调。
        });

        boolean[] cleared = {false};
        WKRichTextContent content = new WKRichTextContent();
        WKRichTextSender.send(context, content, "等第二张", Arrays.asList("/sd/a.png", "/sd/b.png"),
                () -> cleared[0] = true);

        assertTrue(context.sent.isEmpty());
        assertFalse(cleared[0]);
    }

    // ---------------------------------------------------------------------
    // YUJ-2872 🔴 composer-state race + 重复发送（对齐 web#227 第二轮 snapshot-aware）。
    // ---------------------------------------------------------------------

    /**
     * 🔴 defect a 核心回归：上传 pending 期间用户打了新草稿，较早那条 send 入队后
     * <strong>绝不能</strong>清掉新草稿。模拟 ChatActivity 的 onEnqueued snapshot-aware
     * 清空逻辑：仅当输入框仍等于被消费的快照时才清。
     */
    @Test
    public void draftTypedDuringUpload_survivesAfterEnqueue() {
        // 受控 uploader：先攒回调，待"用户打新草稿"后再放行，复刻异步上传期间的竞态。
        final ImageUploaderGate gate = new ImageUploaderGate("https://cdn/a.png");
        WKRichTextSender.setUploaderForTest(gate);

        // 模拟输入框：发送时消费的快照文本。
        final String consumedSnapshot = "上线方案";
        final StringBuilder composer = new StringBuilder(consumedSnapshot);

        WKRichTextContent content = new WKRichTextContent();
        WKRichTextSender.send(context, content, consumedSnapshot, Arrays.asList("/sd/a.png"),
                () -> {
                    // ChatActivity.onEnqueued 的 snapshot-aware 清空逻辑。
                    if (WKRichTextSender.shouldClearComposer(consumedSnapshot, composer.toString())) {
                        composer.setLength(0);
                    }
                });

        // 上传 pending 期间用户打了一条全新的草稿（覆盖了仍留存的旧文本）。
        composer.setLength(0);
        composer.append("下一条新消息");

        // 现在上传回调到达 → RichText 入队 → onEnqueued 触发。
        gate.release();

        // RichText 确实入队（旧文本必达）。
        assertEquals(1, context.sent.size());
        assertTrue(context.sent.get(0) instanceof WKRichTextContent);
        // 关键：用户在等待期间新打的草稿没有被擦掉。
        assertEquals("下一条新消息", composer.toString());
    }

    /**
     * 🔴 对照组：上传 pending 期间用户<em>没动</em>输入框，入队后正常清空（输入框未变）。
     */
    @Test
    public void composerUnchangedDuringUpload_clearedAfterEnqueue() {
        final ImageUploaderGate gate = new ImageUploaderGate("https://cdn/a.png");
        WKRichTextSender.setUploaderForTest(gate);

        final String consumedSnapshot = "看这张图";
        final StringBuilder composer = new StringBuilder(consumedSnapshot);

        WKRichTextContent content = new WKRichTextContent();
        WKRichTextSender.send(context, content, consumedSnapshot, Arrays.asList("/sd/a.png"),
                () -> {
                    if (WKRichTextSender.shouldClearComposer(consumedSnapshot, composer.toString())) {
                        composer.setLength(0);
                    }
                });

        gate.release();

        assertEquals(1, context.sent.size());
        // 输入框未变 → 清空。
        assertEquals("", composer.toString());
    }

    /**
     * 🔴 defect b：in-flight 混排发送期间，发送键拦截把同一段可见文本重复单发；
     * 文本被改动则放行。直接断言去重判定，不产生重复文本消息。
     */
    @Test
    public void duplicatePendingText_blocksManualResend_butAllowsEditedText() {
        final String pending = "同一段文本";
        // 与 in-flight 快照完全相同 → 拦截（避免重复文本消息）。
        assertTrue(WKRichTextSender.isDuplicatePendingText(pending, "同一段文本"));
        // 用户改动了文本（哪怕一字）→ 新意图，放行。
        assertFalse(WKRichTextSender.isDuplicatePendingText(pending, "同一段文本!"));
        assertFalse(WKRichTextSender.isDuplicatePendingText(pending, "别的内容"));
        // 无 in-flight（快照 null）→ 永不拦截。
        assertFalse(WKRichTextSender.isDuplicatePendingText(null, "同一段文本"));
    }

    /**
     * 🔴 shouldClearComposer 边界：null 快照 / 文本已变 → 不清；完全相同 → 清。
     */
    @Test
    public void shouldClearComposer_onlyWhenComposerStillEqualsSnapshot() {
        assertTrue(WKRichTextSender.shouldClearComposer("abc", "abc"));
        assertFalse(WKRichTextSender.shouldClearComposer("abc", "abcd"));
        assertFalse(WKRichTextSender.shouldClearComposer("abc", ""));
        assertFalse(WKRichTextSender.shouldClearComposer(null, "abc"));
        assertFalse(WKRichTextSender.shouldClearComposer("abc", null));
    }

    // ---------------------------------------------------------------------

    /** 受控 uploader：把单张图片的成功回调延迟到 {@link #release()} 调用时再触发。 */
    private static final class ImageUploaderGate implements WKRichTextSender.ImageUploader {
        private final String downloadUrl;
        private Result pending;

        ImageUploaderGate(String downloadUrl) {
            this.downloadUrl = downloadUrl;
        }

        @Override
        public void upload(WKChannel channel, String localPath, Result result) {
            this.pending = result;
        }

        void release() {
            if (pending != null) {
                pending.onSuccess(downloadUrl);
            }
        }
    }

    // ---------------------------------------------------------------------

    /** 最小 IConversationContext 测试替身：记录入队消息，其余方法 no-op。 */
    private static final class CapturingContext implements IConversationContext {
        final List<WKMessageContent> sent = new ArrayList<>();

        @Override
        public void sendMessage(WKMessageContent wkMessageContent) {
            sent.add(wkMessageContent);
        }

        @Override
        public WKChannel getChatChannelInfo() {
            return new WKChannel("c1", (byte) 2);
        }

        @Override
        public void showMultipleChoice() {
        }

        @Override
        public void setTitleRightText(String text) {
        }

        @Override
        public void showReply(WKMsg wkMsg) {
        }

        @Override
        public void showEdit(WKMsg wkMsg) {
        }

        @Override
        public void tipsMsg(String clientMsgNo) {
        }

        @Override
        public void setEditContent(String content) {
        }

        @Override
        public AppCompatActivity getChatActivity() {
            // null → toastFailIfAny 短路，避免触碰 android Toast。
            return null;
        }

        @Override
        public WKMsg getReplyMsg() {
            return null;
        }

        @Override
        public void hideSoftKeyboard() {
        }

        @Override
        public ChatAdapter getChatAdapter() {
            return null;
        }

        @Override
        public void sendCardMsg() {
        }

        @Override
        public void chatRecyclerViewScrollToEnd() {
        }

        @Override
        public void deleteOperationMsg() {
        }

        @Override
        public void onChatAvatarClick(String uid, boolean isLongClick) {
        }

        @Override
        public void onViewPicture(boolean isViewing) {
        }

        @Override
        public void onMsgViewed(WKMsg wkMsg, int position) {
        }

        @Override
        public android.view.View getRecyclerViewLayout() {
            return null;
        }

        @Override
        public boolean isShowChatActivity() {
            return true;
        }

        @Override
        public void closeActivity() {
        }

        @Override
        public void chooseFile() {
        }
    }
}
