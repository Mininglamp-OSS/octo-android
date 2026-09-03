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

package com.chat.base.msgitem;


import com.xinbida.wukongim.message.type.WKMsgContentType;

/**
 * 2019-11-15 17:01
 * 消息正文类型
 */
public class WKContentType extends WKMsgContentType {
    //系统消息
    public final static int systemMsg = 0;
    //以下是新消息提示分割线
    public final static int msgPromptNewMsg = -1;
    //消息时间
    public final static int msgPromptTime = -2;
    //未知消息
    public final static int unknown_msg = -3;
    //正在输入
    public final static int typing = -4;
    //撤回消息
    public final static int revoke = -5;
    //加载中
    public final static int loading = -6;
    //本地显示的群会议音视频
    public final static int videoCallGroup = -7;
    // 非好友
    public final static int noRelation = -9;
    /**
     * 敏感词提醒。
     *
     * @deprecated 功能已整套移除：该提示由客户端本地 contains() 匹配凭空造出，
     * 以一条真实消息落库并顶掉会话列表的最后一条消息，服务端并无对应约束。
     * 历史 DB 行由迁移 wk_sql/202608191100.sql 清理：先把 last_client_msg_no 还指着
     * 这类行的会话重新指向该频道最新一条真实消息，再删行 —— 否则会话列表预览会因为
     * 指针悬空而渲染成空白。
     * <p>常量刻意保留：-10 是已被占用过的协议号，删掉后若被复用表示别的消息类型，
     * 尚未升级的老客户端上的历史数据会串味。请勿复用此值。
     */
    @Deprecated
    public final static int sensitiveWordsTips = -10;
    public final static int emptyView = -12;
    public final static int spanEmptyView = -13;

    // 富文本
    public final static int richText = 14;
    // 交互式卡片（Microsoft AdaptiveCards / InteractiveCard，与 iOS/web 对齐 type=17）
    public final static int interactiveCard = 17;
    //群聊加人
    public final static int addGroupMembersMsg = 1002;
    //群聊减人
    public final static int removeGroupMembersMsg = 1003;
    //群系统消息
    public final static int groupSystemInfo = 1005;
    //撤回消息
    public final static int withdrawSystemInfo = 1006;
    //设置新的管理员
    public final static int setNewGroupAdmin = 1008;
    //审核群成员
    public final static int approveGroupMember = 1009;
    //截屏消息
    public final static int screenshot = 20;
    /**
     * 群总结完成提示（对齐 octo-web MessageContentTypeConst.summaryNotify）。
     * <p>content 形如 {@code {from_uid, from_name}}，不在 1000-2000 系统号段内，
     * 需要像 {@link #screenshot} 一样单独注册 provider 才能当系统提示渲染。
     * <p>Web 自 octo-web d31a10c3 起改用 WK_TIP(2000) 发新消息（2000 走
     * {@code isSystemMsg()} 通用路径，无需适配），此类型仅用于渲染存量历史消息。
     */
    public final static int summaryNotify = 21;
    //子区创建通知
    public final static int threadCreated = 1100;
    /**
     * 群总结完成提示（WK_TIP 号段，对齐 octo-web MessageContentTypeConst.summaryTip）。
     * <p>content 形如 {@code {content:"{0}总结了群聊内容", extra:[{uid,name}]}}，落在
     * 1000-2000 系统号段内，收端由 WKSystemProvider + StringUtils.getShowContent()
     * 通用路径渲染，无需单独注册 provider；此常量供发送侧构造消息体使用。
     * @see #summaryNotify 已废弃的自定义 type-21 旧协议
     */
    public final static int summaryTip = 2000;

    public static boolean isSystemMsg(int type) {
        return type >= 1000 && type <= 2000;
    }

    public static boolean isLocalMsg(int type) {
        return type <= 0;
    }

    public static boolean isSupportNotification(int type) {
        return type >= WK_TEXT && type <= richText;
    }
}
