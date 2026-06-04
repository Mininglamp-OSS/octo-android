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

import android.graphics.BitmapFactory;
import android.text.TextUtils;

import androidx.annotation.VisibleForTesting;

import com.chat.base.msg.IConversationContext;
import com.chat.base.net.ud.WKUploader;
import com.chat.base.utils.WKToastUtils;
import com.chat.uikit.R;
import com.chat.uikit.chat.msgmodel.WKRichTextContent;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.msgmodel.WKMessageContent;
import com.xinbida.wukongim.msgmodel.WKTextContent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 图文混排（RichText，ContentType=14）<em>发送侧</em>聚合器（Phase 1）。
 *
 * <p>与 web 发送侧 octo-web#227 对称：当输入框同时含文本 + 图片（且无非图片文件块）
 * 时，把它们聚合成 <strong>一条</strong> {@code type=14} 消息（content 为有序 block
 * 数组），而非逐张发独立消息。纯文本 / 纯图片 / 含文件块的路径保持原有逐条发送不变，
 * 零回归。
 *
 * <p>关键链路差异（对齐 octo-android 实际架构）：Android 没有 web Tiptap 那种把文本与
 * 图片交错暂存的内联编辑器——文本在输入框、图片从相册选取。因此「混排」的发生时机 =
 * 输入框已有文本时用户又选了一批静态图片；此时本聚合器接管。穿插顺序固定为
 * <em>文本块在前、图片块按选取顺序在后</em>（即用户先打字、再选图的自然顺序）。
 *
 * <p>RichTextContent extends {@link WKMessageContent}（非 Media），故最终走
 * sendTextAndWaitAck 链路，不是 media upload 链路；但图片 URL 仍需先上传得到
 * downloadUrl 再塞 block，故本类先逐张上传（顺序串行，天然保序）。
 *
 * <p>安全：每张图上传后用 {@link WKRichTextContent#isSafeImageUrl(String)} 校验
 * http/https 或 server 相对路径，阻断 {@code javascript:}/{@code data:}/{@code file:}
 * 等注入；不安全或上传失败的图片跳过并 Toast。
 *
 * <p>原子性（对齐 YUJ-2740 元教训②）：文本<strong>始终落成一条消息</strong>——只要有
 * ≥1 张有效图片就发图文混排单条；若所有图片都失败，则降级发纯文本（text 不丢失）。
 * mention（三态 humans/ais + 群成员 uids）由调用方在传入的 {@code content} 上预置，
 * 全部图片失败降级时同样迁移到纯文本消息，保证群 @ 通知不丢。
 *
 * <p><strong>文本必达（YUJ-2832 P1 修复）</strong>：图片上传是异步的，
 * {@link IConversationContext#sendMessage} 要等所有上传回调完成才落库入队。在那之前，
 * 调用方<em>绝不能</em>提前清空输入框——否则进程被杀 / Activity 销毁 / 上传始终未回调
 * 时，用户文本既不在输入框、也没作为本地 outgoing message 落库，造成相对 text/media
 * 路径（立即入队）的丢消息回归。为此 {@code send} 接收一个 {@link OnEnqueued} 回调，
 * <strong>仅在消息真正入队后</strong>（混排单条或纯文本降级）才触发；上传未完成期间文本
 * 留在输入框，由 ChatActivity 的草稿持久化兜底，可恢复。
 */
public final class WKRichTextSender {

    private WKRichTextSender() {
    }

    /**
     * 消息已持久入队的回调。<strong>仅</strong>在 {@link IConversationContext#sendMessage}
     * 真正被调用（混排单条或纯文本降级落库）后触发；上传未完成 / 全程无消息入队时
     * 永不触发，保证调用方据此清空输入框时「文本已必达」。在主线程触发（WKUploader
     * 回调已 post 回主 looper）。
     */
    public interface OnEnqueued {
        void onEnqueued();
    }

    /**
     * 发送流程<strong>终态</strong>回调（Phase 2 输入框附件托盘）。与 {@link OnEnqueued} 不同：
     * {@code onEnqueued} 只在「消息真正入队」时触发（保证文本必达语义）；本回调在发送流程
     * <em>任何终态</em>都触发一次（混排入队 / 纯文本降级入队 / <strong>全图失败且无文本→什么都没发</strong>）。
     *
     * <p>存在意义：托盘用一个 {@code richTextTraySending} in-flight 标志防重入，必须在流程结束时
     * 复位。纯图片托盘若所有图片上传失败、又没有文本可降级，{@code onEnqueued} 永不触发 →
     * 标志将永久卡住、发送键失灵。本回调保证这种「什么都没发」的终态也能复位标志。在主线程触发。
     */
    public interface OnComplete {
        void onComplete();
    }

    /**
     * 单张图片上传抽象（可注入测试替身）。生产实现走 {@link WKUploader} 两步（取凭证
     * → PUT），回调在主线程。把它抽成 seam 是为了让「上传未完成 → 文本仍可恢复 / 不被
     * 提前清空」这条 P1 回归可在纯 JVM 单测下被断言。
     */
    public interface ImageUploader {
        /**
         * @param channel   目标频道
         * @param localPath 图片本地路径
         * @param result    上传结果回调（成功给 downloadUrl；失败 / 未完成则不调用 onSuccess）
         */
        void upload(WKChannel channel, String localPath, Result result);

        interface Result {
            void onSuccess(String downloadUrl);

            void onFailure();
        }
    }

    /**
     * Snapshot-aware 清空判定（YUJ-2872 🔴 defect a，对齐 web#227 第二轮 isEditorUnchanged）。
     *
     * <p>图片上传是异步的，消息要等所有上传回调完成才入队（{@link OnEnqueued}）。在那之前
     * 输入框保留待发文本（YUJ-2832 崩溃恢复）。若用户在等待期间又打了新草稿，则<em>较早</em>
     * 那条 send 入队后<strong>绝不能</strong>无条件清空输入框——否则把用户新打的内容擦掉。
     * 仅当输入框<em>仍然恰好</em>等于本次消费的快照文本时才清；否则保留更新后的草稿。
     *
     * @param consumedSnapshot   本次混排发送消费的输入框文本快照（send 入参 text）
     * @param currentComposerText 入队回调触发时输入框的当前文本
     * @return true 表示可以安全清空（输入框未变）；false 表示用户已打新草稿，须保留
     */
    public static boolean shouldClearComposer(String consumedSnapshot, String currentComposerText) {
        if (consumedSnapshot == null) {
            return false;
        }
        return consumedSnapshot.equals(currentComposerText);
    }

    /**
     * 重复发送判定（YUJ-2872 🔴 defect b，对齐 web#227 sendingRef 防重入）。
     *
     * <p>一条混排发送 in-flight 期间，被消费的文本仍留在输入框（YUJ-2832 崩溃恢复）。此时
     * 用户手动点发送键会把<em>同一段</em>可见文本作为一条独立纯文本消息单发出去 → 重复文本
     * 消息 + 之后那条 RichText。命中（候选手动发送文本与 in-flight 快照完全相同）时调用方
     * 应吞掉这次点击。文本被改动（哪怕一个字符）即视为用户的新意图，放行。
     *
     * @param pendingSnapshot in-flight 混排发送消费的文本快照（无 in-flight 时为 null）
     * @param candidateText   手动发送键将要发出的文本
     * @return true 表示这是 in-flight 混排发送的重复，手动发送须被拦截
     */
    public static boolean isDuplicatePendingText(String pendingSnapshot, String candidateText) {
        return pendingSnapshot != null && pendingSnapshot.equals(candidateText);
    }

    private static volatile ImageUploader uploader = defaultUploader();

    @VisibleForTesting
    static void setUploaderForTest(ImageUploader testUploader) {
        uploader = testUploader != null ? testUploader : defaultUploader();
    }

    @VisibleForTesting
    static void resetUploader() {
        uploader = defaultUploader();
    }

    /**
     * 发起图文混排聚合发送。
     *
     * @param context    会话上下文（用于最终 {@link IConversationContext#sendMessage} 落库 + 注入 spaceId）
     * @param content    调用方已预置 mention 基字段（mentionInfo/mentionHumans/mentionAis）的 RichText 载体
     * @param text       输入框原始文本（保证落地，不丢字）
     * @param imagePaths 本次选取的图片本地路径（按选取顺序）
     * @param onEnqueued 消息真正入队后的回调；调用方应在此（且仅在此）清空输入框，
     *                   保证「文本必达」。可为 null。
     */
    public static void send(IConversationContext context,
                            WKRichTextContent content,
                            String text,
                            List<String> imagePaths,
                            OnEnqueued onEnqueued) {
        send(context, content, text, imagePaths, onEnqueued, null);
    }

    /**
     * 发起图文混排聚合发送（Phase 2 重载：带终态回调）。
     *
     * @param onComplete 发送流程<strong>任何终态</strong>都触发一次的回调（含「全图失败且无
     *                   文本→什么都没发」），用于复位托盘 in-flight 防重入标志。可为 null。
     *                   见 {@link OnComplete}。
     */
    public static void send(IConversationContext context,
                            WKRichTextContent content,
                            String text,
                            List<String> imagePaths,
                            OnEnqueued onEnqueued,
                            OnComplete onComplete) {
        if (context == null || content == null) {
            notifyComplete(onComplete);
            return;
        }
        WKChannel channel = context.getChatChannelInfo();
        if (channel == null) {
            sendTextFallback(context, null, content, text, onEnqueued);
            notifyComplete(onComplete);
            return;
        }
        List<String> paths = imagePaths != null ? imagePaths : new ArrayList<>();
        uploadNext(context, channel, content, text, paths, 0,
                new ArrayList<>(), new int[]{0}, onEnqueued, onComplete);
    }

    /**
     * 串行上传第 index 张图片（保序），完成后递归处理下一张；全部处理完构造并发送。
     */
    private static void uploadNext(IConversationContext context,
                                   WKChannel channel,
                                   WKRichTextContent content,
                                   String text,
                                   List<String> paths,
                                   int index,
                                   List<WKRichTextContent.RichTextBlock> imageBlocks,
                                   int[] failedCount,
                                   OnEnqueued onEnqueued,
                                   OnComplete onComplete) {
        if (index >= paths.size()) {
            finish(context, channel, content, text, imageBlocks, failedCount[0], onEnqueued, onComplete);
            return;
        }
        String localPath = paths.get(index);
        if (TextUtils.isEmpty(localPath)) {
            failedCount[0]++;
            uploadNext(context, channel, content, text, paths, index + 1, imageBlocks, failedCount, onEnqueued, onComplete);
            return;
        }
        int[] dims = decodeImageSize(localPath);
        uploader.upload(channel, localPath, new ImageUploader.Result() {
            @Override
            public void onSuccess(String downloadUrl) {
                // 安全对称：上传成功也要校验 scheme，阻断 javascript:/data:/file:。
                if (!WKRichTextContent.isSafeImageUrl(downloadUrl)) {
                    failedCount[0]++;
                } else {
                    imageBlocks.add(WKRichTextContent.makeImageBlock(downloadUrl, dims[0], dims[1]));
                }
                uploadNext(context, channel, content, text, paths, index + 1, imageBlocks, failedCount, onEnqueued, onComplete);
            }

            @Override
            public void onFailure() {
                failedCount[0]++;
                uploadNext(context, channel, content, text, paths, index + 1, imageBlocks, failedCount, onEnqueued, onComplete);
            }
        });
    }

    /**
     * 全部图片处理完毕：拼装有序 block（text 在前、image 按序在后）→ 发单条 RichText；
     * 若无任何有效图片，降级发纯文本（原子性：text 不丢）。失败有跳过时 Toast 提示。
     * 入队成功后触发 {@code onEnqueued}（调用方据此清空输入框，保证文本必达）。
     * 无论是否入队，最终都触发 {@code onComplete}（复位托盘 in-flight 标志）。
     */
    private static void finish(IConversationContext context,
                               WKChannel channel,
                               WKRichTextContent content,
                               String text,
                               List<WKRichTextContent.RichTextBlock> imageBlocks,
                               int failedCount,
                               OnEnqueued onEnqueued,
                               OnComplete onComplete) {
        if (imageBlocks.isEmpty()) {
            // 所有图片都失败/不安全 → 文本仍要落地（有文本时降级；无文本则什么都不发）。
            toastFailIfAny(context, failedCount);
            sendTextFallback(context, channel, content, text, onEnqueued);
            notifyComplete(onComplete);
            return;
        }

        List<WKRichTextContent.RichTextBlock> blocks = new ArrayList<>();
        if (!TextUtils.isEmpty(text)) {
            blocks.add(WKRichTextContent.makeTextBlock(text));
        }
        blocks.addAll(imageBlocks);

        content.blocks = blocks;
        // plain 非权威：仅填本地占位（image → 占位 wire token），server #232 Finalize 覆盖。
        content.plain = WKRichTextContent.buildPlainFromBlocks(blocks);

        toastFailIfAny(context, failedCount);
        // 按<em>捕获</em>的频道落库（YUJ-2872 🔴 跨频道路由）：上传期间 Activity 可能已切到
        // 别的频道，绝不能读 mutable 字段，否则消息错投。channel 为本次发起时捕获的目标。
        enqueueToChannel(context, content, channel);
        notifyEnqueued(onEnqueued);
        notifyComplete(onComplete);
    }

    /**
     * 降级纯文本发送：把已预置在 RichText 载体上的 mention 基字段迁移到 WKTextContent，
     * 保证群 @ 通知（含 @所有AI）不因降级而丢失。入队后触发 {@code onEnqueued}。
     */
    private static void sendTextFallback(IConversationContext context,
                                         WKChannel channel,
                                         WKRichTextContent richHolder,
                                         String text,
                                         OnEnqueued onEnqueued) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        WKTextContent textContent = new WKTextContent(text);
        textContent.mentionAll = richHolder.mentionAll;
        textContent.mentionHumans = richHolder.mentionHumans;
        textContent.mentionAis = richHolder.mentionAis;
        textContent.mentionInfo = richHolder.mentionInfo;
        // 注意：此处<em>有意</em>不迁移 mention 的 entities（@ 高亮区间）。仅全图失败的降级
        // 路径会走到这里，发出纯文本单条，与既有发送键文本路径同语义——@ 通知靠上面的
        // mentionAll/humans/ais + mentionInfo.uids 三态基字段保证不丢；entities 只影响接收侧
        // 高亮渲染，且其跨 block 的 offset 在降级为纯文本后已失去意义，故不迁移。
        enqueueToChannel(context, textContent, channel);
        notifyEnqueued(onEnqueued);
    }

    /**
     * 按<em>捕获</em>的目标频道落库（YUJ-2872 🔴 跨频道路由）。延迟入队期间 Activity 可能已
     * 被复用切到别的频道；若 {@code channel} 已捕获就走 {@link IConversationContext#sendMessageToChannel}
     * 锁定目标，避免错投。{@code channel} 为 null（无法获取频道）时回退 {@link IConversationContext#sendMessage}
     * 保持原行为。
     */
    private static void enqueueToChannel(IConversationContext context,
                                         WKMessageContent messageContent,
                                         WKChannel channel) {
        if (channel != null) {
            context.sendMessageToChannel(messageContent, channel);
        } else {
            context.sendMessage(messageContent);
        }
    }

    private static void notifyEnqueued(OnEnqueued onEnqueued) {
        if (onEnqueued != null) {
            onEnqueued.onEnqueued();
        }
    }

    private static void notifyComplete(OnComplete onComplete) {
        if (onComplete != null) {
            onComplete.onComplete();
        }
    }

    private static void toastFailIfAny(IConversationContext context, int failedCount) {
        if (failedCount > 0 && context != null && context.getChatActivity() != null) {
            WKToastUtils.getInstance().showToast(
                    context.getChatActivity().getString(R.string.upload_fail));
        }
    }

    /** inJustDecodeBounds 量本地图片尺寸（避免读 CDN 返回 0×0，对齐 web 本地量尺寸教训）。 */
    private static int[] decodeImageSize(String localPath) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(localPath, options);
            int w = Math.max(options.outWidth, 0);
            int h = Math.max(options.outHeight, 0);
            return new int[]{w, h};
        } catch (Throwable t) {
            return new int[]{0, 0};
        }
    }

    /** 生产上传实现：WKUploader 两步（取凭证 → PUT），回调已 post 回主线程。 */
    private static ImageUploader defaultUploader() {
        return (channel, localPath, result) ->
                WKUploader.getInstance().getUploadCredentials(channel.channelID, channel.channelType, localPath,
                        (uploadUrl, downloadUrl, contentType, contentDisposition) -> {
                            if (TextUtils.isEmpty(uploadUrl) || TextUtils.isEmpty(downloadUrl)) {
                                result.onFailure();
                                return;
                            }
                            WKUploader.getInstance().putUpload(uploadUrl, localPath, contentType, contentDisposition,
                                    UUID.randomUUID().toString().replaceAll("-", ""),
                                    new WKUploader.IUploadBack() {
                                        @Override
                                        public void onSuccess(String url) {
                                            result.onSuccess(downloadUrl);
                                        }

                                        @Override
                                        public void onError() {
                                            result.onFailure();
                                        }
                                    });
                        });
    }
}
