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
 * http/https，阻断 {@code javascript:}/{@code data:}/{@code file:} 注入；不安全或上传
 * 失败的图片跳过并 Toast。
 *
 * <p>原子性（对齐 YUJ-2740 元教训②）：文本<strong>始终落成一条消息</strong>——只要有
 * ≥1 张有效图片就发图文混排单条；若所有图片都失败，则降级发纯文本（text 不丢失）。
 * mention（三态 humans/ais + 群成员 uids）由调用方在传入的 {@code content} 上预置，
 * 全部图片失败降级时同样迁移到纯文本消息，保证群 @ 通知不丢。
 */
public final class WKRichTextSender {

    private WKRichTextSender() {
    }

    /**
     * 发起图文混排聚合发送。
     *
     * @param context    会话上下文（用于最终 {@link IConversationContext#sendMessage} 落库 + 注入 spaceId）
     * @param content    调用方已预置 mention 基字段（mentionInfo/mentionHumans/mentionAis）的 RichText 载体
     * @param text       输入框原始文本（保证落地，不丢字）
     * @param imagePaths 本次选取的图片本地路径（按选取顺序）
     */
    public static void send(IConversationContext context,
                            WKRichTextContent content,
                            String text,
                            List<String> imagePaths) {
        if (context == null || content == null) {
            return;
        }
        WKChannel channel = context.getChatChannelInfo();
        if (channel == null) {
            sendTextFallback(context, content, text);
            return;
        }
        List<String> paths = imagePaths != null ? imagePaths : new ArrayList<>();
        uploadNext(context, channel, content, text, paths, 0,
                new ArrayList<>(), new int[]{0});
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
                                   int[] failedCount) {
        if (index >= paths.size()) {
            finish(context, content, text, imageBlocks, failedCount[0]);
            return;
        }
        String localPath = paths.get(index);
        if (TextUtils.isEmpty(localPath)) {
            failedCount[0]++;
            uploadNext(context, channel, content, text, paths, index + 1, imageBlocks, failedCount);
            return;
        }
        int[] dims = decodeImageSize(localPath);
        WKUploader.getInstance().getUploadCredentials(channel.channelID, channel.channelType, localPath,
                (uploadUrl, downloadUrl, contentType, contentDisposition) -> {
                    if (TextUtils.isEmpty(uploadUrl) || TextUtils.isEmpty(downloadUrl)) {
                        failedCount[0]++;
                        uploadNext(context, channel, content, text, paths, index + 1, imageBlocks, failedCount);
                        return;
                    }
                    WKUploader.getInstance().putUpload(uploadUrl, localPath, contentType, contentDisposition,
                            UUID.randomUUID().toString().replaceAll("-", ""),
                            new WKUploader.IUploadBack() {
                                @Override
                                public void onSuccess(String url) {
                                    // 安全对称：上传成功也要校验 scheme，阻断 javascript:/data:/file:。
                                    if (!WKRichTextContent.isSafeImageUrl(downloadUrl)) {
                                        failedCount[0]++;
                                    } else {
                                        imageBlocks.add(WKRichTextContent.makeImageBlock(downloadUrl, dims[0], dims[1]));
                                    }
                                    uploadNext(context, channel, content, text, paths, index + 1, imageBlocks, failedCount);
                                }

                                @Override
                                public void onError() {
                                    failedCount[0]++;
                                    uploadNext(context, channel, content, text, paths, index + 1, imageBlocks, failedCount);
                                }
                            });
                });
    }

    /**
     * 全部图片处理完毕：拼装有序 block（text 在前、image 按序在后）→ 发单条 RichText；
     * 若无任何有效图片，降级发纯文本（原子性：text 不丢）。失败有跳过时 Toast 提示。
     */
    private static void finish(IConversationContext context,
                               WKRichTextContent content,
                               String text,
                               List<WKRichTextContent.RichTextBlock> imageBlocks,
                               int failedCount) {
        if (imageBlocks.isEmpty()) {
            // 所有图片都失败/不安全 → 文本仍要落地。
            toastFailIfAny(context, failedCount);
            sendTextFallback(context, content, text);
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
        context.sendMessage(content);
    }

    /**
     * 降级纯文本发送：把已预置在 RichText 载体上的 mention 基字段迁移到 WKTextContent，
     * 保证群 @ 通知（含 @所有AI）不因降级而丢失。
     */
    private static void sendTextFallback(IConversationContext context,
                                         WKRichTextContent richHolder,
                                         String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        WKTextContent textContent = new WKTextContent(text);
        textContent.mentionAll = richHolder.mentionAll;
        textContent.mentionHumans = richHolder.mentionHumans;
        textContent.mentionAis = richHolder.mentionAis;
        textContent.mentionInfo = richHolder.mentionInfo;
        context.sendMessage(textContent);
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
}
