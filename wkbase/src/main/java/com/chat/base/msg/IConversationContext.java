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
}
