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

package com.chat.base.msg;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

/**
 * 2020-08-13 14:22
 */
public interface IConversationContext {
    //发送消息到当前会话
    void sendMessage(WKMessageContent wkMessageContent);

    /**
     * 发送消息到<strong>指定</strong>频道（YUJ-2872 🔴 跨频道路由）。用于<em>延迟入队</em>
     * 场景：图文混排发送在等图片异步上传期间，承载它的 Activity 可能已被复用切到别的频道
     * （{@code ChatActivity.onNewIntent} 改写了 mutable 的 channelId/channelType）。若此时
     * 回调仍走 {@link #sendMessage} 读 mutable 字段，消息会被错投到当前频道（用 A 频道凭证
     * 上传的图片落到 B 频道 = 隐私/路由 bug）。调用方应在发起时<strong>捕获</strong>目标频道
     * （{@link #getChatChannelInfo()}），入队时传回这里按捕获的频道落库，不受字段变更影响。
     *
     * <p>默认实现委派回 {@link #sendMessage}（无跨频道语义的实现保持原行为）。
     */
    default void sendMessageToChannel(WKMessageContent wkMessageContent,
                                      com.xinbida.wukongim.entity.WKChannel channel) {
        sendMessage(wkMessageContent);
    }

    //获取当前会话到频道信息
    WKChannel getChatChannelInfo();

    //显示多选状态
    void showMultipleChoice();

    //显示标题栏右边内容
    void setTitleRightText(String text);

    //显示回复效果
    void showReply(WKMsg wkMsg);

    //显示编辑效果
    void showEdit(WKMsg wkMsg);

    //提醒某条消息
    void tipsMsg(String clientMsgNo);

    //设置输入框内容
    void setEditContent(String content);

    // 当前聊天页面
    AppCompatActivity getChatActivity();

    // 获取回复消息
    WKMsg getReplyMsg();

    // 隐藏软键盘
    void hideSoftKeyboard();

    ChatAdapter getChatAdapter();

    // 发送名片
    void sendCardMsg();

    // 消息列表滚动到底部
    void chatRecyclerViewScrollToEnd();

    void deleteOperationMsg();

    // 头像点击事件
    void onChatAvatarClick(String uid, boolean isLongClick);

    // 正在查看大图
    void onViewPicture(boolean isViewing);

    // 消息已查看
    void onMsgViewed(WKMsg wkMsg, int position);

    View getRecyclerViewLayout();
    boolean isShowChatActivity();
    void closeActivity();

    // 选择文件发送
    void chooseFile();

    /**
     * 图文混排（RichText=14）发送侧聚合（Phase 1）。
     *
     * <p>当输入框含待发文本时，把传入的图片本地路径与文本聚合成<strong>一条</strong>
     * type=14 消息（text 块在前、image 块按选取顺序在后），上传图片得 URL 后落库发送，
     * 并清空输入框；返回 true 表示本次发送已被接管。若输入框无文本则返回 false，调用方
     * 继续走原有逐条图片发送（纯图片零回归）。
     *
     * @param imageLocalPaths 本次选取的静态图片本地路径（按选取顺序，调用方已过滤 video/gif）
     * @return true 已聚合发送；false 未接管
     */
    default boolean trySendRichTextMixed(java.util.List<String> imageLocalPaths) {
        return false;
    }

    /**
     * 图文混排（RichText=14）发送 in-flight 期间的重复发送拦截（YUJ-2872 🔴）。
     *
     * <p>一条混排发送在等图片异步上传完成期间，被消费的文本仍留在输入框（YUJ-2832 崩溃
     * 恢复语义）。若用户此时手动点发送键，会把<strong>同一段</strong>可见文本作为一条独立
     * 纯文本消息单发出去 → 重复文本消息 + 之后那条 RichText。本方法返回 true 表示候选发送
     * 文本与 in-flight 混排消费的文本完全相同，发送键路径应吞掉这次点击；文本被改动（即
     * 用户的新意图）或无 in-flight 时返回 false（放行）。
     *
     * @param candidateText 发送键将要发出的文本
     * @return true 表示是 in-flight 混排发送的重复，应拦截；false 表示放行
     */
    default boolean isPendingRichTextDuplicate(String candidateText) {
        return false;
    }

    /**
     * 图文混排（RichText=14）<strong>输入框附件托盘</strong>入口（Phase 2，对齐 web#237）。
     *
     * <p>把本次选取的静态图片<strong>加入输入框上方的缩略图托盘</strong>而非立即逐张发送：
     * 用户可继续打字、可多批追加、可调序、可移除，点发送时按托盘真实顺序整体打成单条
     * type=14（真·穿插）。返回 true 表示已加入托盘（本次相册选择被托盘接管）。
     *
     * <p>与 Phase 1 的 {@link #trySendRichTextMixed(List)} 的区别：后者是「选图即聚合发送」
     * （顺序固定、不可调），Phase 2 改为「选图先入托盘、发送时才聚合」。Phase 2 入口启用后，
     * 静态图片的相册选择统一走本方法（不再走 trySendRichTextMixed 的即时聚合，也不再逐张发）。
     *
     * @param imageLocalPaths 本次选取的静态图片本地路径（按选取顺序，调用方已过滤 video/gif）
     * @return true 表示已加入托盘；false 表示未接管（调用方继续原有逐条发送，零回归）
     */
    default boolean addImagesToRichTextTray(java.util.List<String> imageLocalPaths) {
        return false;
    }

    /**
     * 发送输入框附件托盘（Phase 2）：把 {@code text} 与 {@code orderedImagePaths}（托盘当前
     * 真实顺序）整体打成单条 type=14（真·穿插）。复用 Phase 1
     * {@code WKRichTextSender} 的原子性 / 跨频道路由 / 文本必达 / snapshot 清空能力——
     * 只动入口/UX，不碰 wire schema（硬约束 #3）。
     *
     * <p>原子性：消息真正入队后才触发 {@code onEnqueued}（主线程），调用方应在其中且仅在其中
     * 清空托盘 + 输入框，保证「文本 / 图片必达」（承接 Phase 1 YUJ-2832/2872 教训）。
     *
     * @param text             输入框原始文本（可空白；空白时发纯图片混排或退化逐张）
     * @param orderedImagePaths 托盘当前顺序的图片本地路径（非空）
     * @param onEnqueued       入队后回调（主线程触发），用于清空托盘 / 输入框；可为 null
     * @param onComplete       发送流程任何终态都触发的回调（含「全图失败且无文本→什么都没发」），
     *                         用于复位托盘 in-flight 防重入标志；可为 null
     * @return true 表示已接管发送；false 表示未接管（调用方保留托盘 / 输入框）
     */
    default boolean sendRichTextTray(String text,
                                     java.util.List<String> orderedImagePaths,
                                     Runnable onEnqueued,
                                     Runnable onComplete) {
        return false;
    }
}
